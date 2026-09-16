package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex leak test (NFR-1, story 2.1): a Codex value derives from a type or a table, never
 * from a Run. Held three ways: generation at an Input wait of a live Run equals the committed
 * folder, which the task wrote in a process that never booted the game; every citation opens to
 * a line holding the entry's declaration; and, statically, no generator class can reach the
 * Run's statics, the game's RNG, the harness, the network, or hold a monitor.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class CodexLeakTest {

    private static final JavaClasses GENERATOR = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.shatterfish.codex");

    /** The Run's statics: what a live Run sets and a Codex must never read. */
    static final ArchRule NO_RUN_STATE = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .should().accessField(Dungeon.class, "seed")
            .orShould().accessField(Dungeon.class, "hero")
            .orShould().accessField(Dungeon.class, "depth")
            .orShould().accessField(Dungeon.class, "level")
            .orShould().accessField(Dungeon.class, "branch")
            .orShould().accessField(Dungeon.class, "challenges")
            .because("a Codex describes types and tables, never a Run (FR-14)");

    /** The doors a Codex value could arrive through that are not the pinned classes. */
    static final ArchRule NO_SIDE_DOORS = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .should().dependOnClassesThat().belongToAnyOf(com.watabou.utils.Random.class, java.util.Random.class)
            .orShould().dependOnClassesThat().resideInAnyPackage("org.shatterfish.harness..", "java.net..",
                    "java.util.concurrent..", "java.security..")
            .because("nothing drawn, nothing from a Run, nothing from the network reaches a Codex");

    /** The monitor rule of story 1.19, in the form this module needs: a generator holds no monitor. */
    static final ArchRule NO_MONITORS = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("org.shatterfish.codex..")
            .should().haveModifier(JavaModifier.SYNCHRONIZED)
            .because("the generator runs on one thread with no Run; a monitor here would be one the game could contend for");

    @Test
    @DisplayName("statically, the generator reaches no Run state, no RNG, no harness, no network, and holds no monitor")
    void the_gate() {
        assertTrue(GENERATOR.size() > 0, "the generator's classes were imported");
        NO_RUN_STATE.check(GENERATOR);
        NO_SIDE_DOORS.check(GENERATOR);
        NO_MONITORS.check(GENERATOR);
    }

    @Test
    @DisplayName("generation at an Input wait of a live Run is the committed folder, byte for byte")
    void a_live_run_changes_nothing() throws IOException {
        Path folder = CodexSeedFreeTest.ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag());
        Map<String, String> live;
        try (HeadlessDriver driver = HeadlessDriver.start(14_142_135L, HeroClass.WARRIOR, 7L)) {
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, driver.stepToInputWait().reason());
            assertTrue(Dungeon.seed == 14_142_135L && Dungeon.hero != null && Dungeon.depth >= 1, "a Run is in progress");
            live = Generate.generate(CodexSeedFreeTest.ROOT);
        }
        for (Map.Entry<String, String> file : live.entrySet()) {
            String committed = Files.readString(folder.resolve(file.getKey()), StandardCharsets.UTF_8);
            assertEquals(committed, file.getValue(), file.getKey() + " differs with a Run in progress");
        }
    }

    @Test
    @DisplayName("every citation opens to a line that holds the entry's declaration")
    void every_citation_resolves() throws IOException {
        Path root = CodexSeedFreeTest.ROOT;
        for (Codex.HeroClassEntry entry : Generate.heroClasses(root)) {
            String line = lineOf(root, entry.citation());
            assertTrue(line.contains(entry.heroClass().name() + "("), entry.citation().reference() + " holds " + entry.heroClass() + ": " + line);
            for (int i = 0; i < entry.subclasses().size(); i++) {
                assertTrue(line.contains(entry.subclasses().get(i).name()), entry.citation().reference() + " names " + entry.subclasses().get(i));
            }
        }
        for (Codex.ChallengeEntry entry : Generate.challenges(root)) {
            String line = lineOf(root, entry.citation());
            assertTrue(line.contains(entry.challenge().name()) && line.contains("= " + entry.mask() + ";"),
                    entry.citation().reference() + " holds " + entry.challenge() + " = " + entry.mask() + ": " + line);
        }
    }

    @Test
    @DisplayName("an anchor that matches no line, or two, fails the generation naming the file and the anchor")
    void a_bad_anchor_fails() {
        Path root = CodexSeedFreeTest.ROOT;
        IllegalStateException none = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Citations.at(root, Generate.CHALLENGES_SOURCE, "^\\s*public static final int\\s+NO_SUCH_FLAG\\s*="));
        assertTrue(none.getMessage().contains("NO_SUCH_FLAG") && none.getMessage().contains("Challenges.java") && none.getMessage().contains("no line"), none.getMessage());
        IllegalStateException many = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Citations.at(root, Generate.CHALLENGES_SOURCE, "public static final int"));
        assertTrue(many.getMessage().contains("all match"), many.getMessage());
    }

    private static String lineOf(Path root, Codex.Citation citation) throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(citation.path()), StandardCharsets.UTF_8);
        assertTrue(citation.line() <= lines.size(), citation.reference() + " is inside the file");
        return lines.get(citation.line() - 1);
    }
}
