package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Bones;
import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
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

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex leak test (NFR-1, story 2.1): a Codex value derives from a type or a table, never
 * from a Run. Held three ways. Statically, over the module's compiled classes: every class is in
 * the package the rules cover; none depends on the game's state classes, the game's toolkit or
 * libGDX (so the generator cannot boot), the harness, the RNG, the clock, reflection, the
 * network, a hash-ordered collection, or holds a monitor; file I/O is confined to the classes
 * that read the source and write the folder; {@code GameContext} alone may name {@code Dungeon},
 * for the two fields a table is parameterised by (story 2.2), and {@code Class} is admitted for
 * the game's class-keyed tables while everything that reaches a class by name or looks inside
 * one stays banned. Dynamically: generation at an Input wait of a live Run played under
 * challenges and in another language equals a generation made before it in the same JVM, both
 * equal the committed folder, and the Run's depth and challenges are what they were. And every
 * citation opens to a line holding the entry's declaration, in order.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class CodexLeakTest {

    /** The module's compiled main classes, wherever their package. */
    private static final JavaClasses GENERATOR = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPath(Path.of("build", "classes", "java", "main").toAbsolutePath());

    /** Every class the module compiles is under the package the rules below cover. */
    static final ArchRule ANCHOR = classes()
            .should().resideInAPackage("org.shatterfish.codex..")
            .because("a class outside the package is outside every rule here");

    /** The game's state: what a Run, a save, a Profile or a badge sets, and a Codex must never read. */
    static final ArchRule NO_RUN_STATE = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .and().doNotHaveFullyQualifiedName(GameContext.class.getName())
            .should().dependOnClassesThat().belongToAnyOf(Dungeon.class, Statistics.class, Badges.class, Rankings.class,
                    SPDSettings.class, GamesInProgress.class, Bones.class)
            .orShould().dependOnClassesThat().resideInAnyPackage("com.shatteredpixel.shatteredpixeldungeon.journal..",
                    "com.watabou..", "com.badlogic..", "org.shatterfish.harness..")
            .because("a Codex describes types and tables, never a Run, a Profile or a process that booted (FR-14)");

    /** The one door to the Run statics reaches Dungeon and nothing else of the game's state. */
    static final ArchRule THE_CONTEXT_IS_NARROW = noClasses()
            .that().haveFullyQualifiedName(GameContext.class.getName())
            .should().dependOnClassesThat().belongToAnyOf(Statistics.class, Badges.class, Rankings.class,
                    SPDSettings.class, GamesInProgress.class, Bones.class)
            .orShould().dependOnClassesThat().resideInAnyPackage("com.shatteredpixel.shatteredpixeldungeon.journal..",
                    "com.watabou..", "com.badlogic..", "org.shatterfish.harness..")
            .orShould().accessField(Dungeon.class, "hero")
            .orShould().accessField(Dungeon.class, "level")
            .orShould().accessField(Dungeon.class, "seed")
            .orShould().accessField(Dungeon.class, "branch")
            .because("the context sets the depth and the challenges around a construction and reads nothing else");

    /** As the api's and the brain's denied list, less Class: the doors a value could come through that are not the pinned classes. */
    private static final Class<?>[] DENIED = {
            ClassLoader.class, Module.class, ModuleLayer.class, Package.class,
            System.class, Runtime.class, Process.class, ProcessBuilder.class, ProcessHandle.class,
            Thread.class, ThreadGroup.class, ThreadLocal.class, StackWalker.class, StackTraceElement.class,
            java.util.Random.class, java.util.SplittableRandom.class, java.util.Scanner.class,
            java.util.ServiceLoader.class, java.util.Date.class, java.util.Calendar.class,
            java.util.Timer.class, java.util.UUID.class, java.util.ResourceBundle.class,
            java.util.Locale.class, java.util.TimeZone.class, java.util.Currency.class,
            java.util.IdentityHashMap.class, java.util.WeakHashMap.class,
    };

    /**
     * No hash-ordered collection is made in the generator: an enum- or class-keyed hash order is a
     * machine's. The game hands over its own ({@code properties()}, {@code RARE_ALTS}), which the
     * generator may read and must sort before it writes.
     */
    static final ArchRule NO_HASH_ORDER = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .should().callConstructorWhere(target(owner(belongToAnyOf(java.util.HashMap.class, java.util.HashSet.class,
                    java.util.Hashtable.class, java.util.LinkedHashSet.class))))
            .because("a hash order is a machine's; what the game hands over is sorted before it is written");

    static final ArchRule NO_SIDE_DOORS = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .should().dependOnClassesThat().belongToAnyOf(DENIED)
            .orShould().dependOnClassesThat().resideInAnyPackage("java.net..", "java.lang.reflect..", "java.lang.invoke..",
                    "java.util.concurrent..", "java.security..", "java.time..", "javax..", "sun..", "jdk..")
            .orShould().callMethodWhere(target(owner(type(Class.class))).and(target(nameMatching(
                    "forName|getClassLoader|getResource|getResourceAsStream|newInstance|getConstructors?|getDeclared\\w+|getMethods?|getFields?|cast"))))
            .orShould().callMethodWhere(target(name("random")).and(target(owner(type(Math.class)))))
            .orShould().callMethodWhere(target(name("setAccessible")))
            .because("nothing drawn, nothing from a clock, a name or the network reaches a Codex; a class is a key, never a door");

    /** Reading the pinned source and writing the folder are the only file I/O, in the classes that do them. */
    static final ArchRule FILES_CONFINED = noClasses()
            .that().resideInAPackage("org.shatterfish.codex..")
            .and().doNotHaveFullyQualifiedName(Citations.class.getName())
            .and().doNotHaveFullyQualifiedName(Upstream.class.getName())
            .and().doNotHaveFullyQualifiedName(Generate.class.getName())
            .and().doNotHaveFullyQualifiedName(Sources.class.getName())
            .should().dependOnClassesThat().belongToAnyOf(java.nio.file.Files.class, java.nio.file.FileSystems.class,
                    java.nio.channels.FileChannel.class, java.io.File.class, java.io.FileInputStream.class, java.io.FileOutputStream.class,
                    java.io.FileReader.class, java.io.FileWriter.class, java.io.RandomAccessFile.class, java.io.InputStream.class,
                    java.io.OutputStream.class, java.io.Reader.class, java.io.Writer.class)
            .allowEmptyShould(true)
            .because("a save file reads as easily as a source file; the classes that may open one are named");

    /** A generator declares no synchronized method; story 1.19's monitorenter rule stays the harness's. */
    static final ArchRule NO_MONITORS = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("org.shatterfish.codex..")
            .should().haveModifier(JavaModifier.SYNCHRONIZED)
            .because("the generator runs on one thread with no Run; a monitor here would be one the game could contend for");

    @Test
    @DisplayName("statically, every class is in the package, and none reaches Run state, the toolkit, the harness, the RNG, the clock, a name, the network or a hash order")
    void the_gate() {
        assertTrue(GENERATOR.size() >= 6, "the generator's classes were imported: " + GENERATOR.size());
        ANCHOR.check(GENERATOR);
        NO_RUN_STATE.check(GENERATOR);
        THE_CONTEXT_IS_NARROW.check(GENERATOR);
        NO_SIDE_DOORS.check(GENERATOR);
        NO_HASH_ORDER.check(GENERATOR);
        FILES_CONFINED.check(GENERATOR);
        NO_MONITORS.check(GENERATOR);
    }

    @Test
    @DisplayName("generation at an Input wait of a live Run under challenges and in another language equals the generation before it and the committed folder, and leaves the Run's depth and challenges as they were")
    void a_live_run_changes_nothing() throws IOException {
        Path folder = CodexSeedFreeTest.ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(CodexSeedFreeTest.ROOT));
        Map<String, String> before = Generate.generate(CodexSeedFreeTest.ROOT);
        Map<String, String> live;
        HeadlessDriver.boot();
        SPDSettings.challenges(Challenges.NO_FOOD | Challenges.DARKNESS | Challenges.STRONGER_BOSSES);
        SPDSettings.language(Languages.GERMAN);
        Messages.setup(Languages.GERMAN);
        try (HeadlessDriver driver = HeadlessDriver.start(14_142_135L, HeroClass.WARRIOR, 7L)) {
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, driver.stepToInputWait().reason());
            assertTrue(Dungeon.seed == 14_142_135L && Dungeon.hero != null && Dungeon.depth >= 1, "a Run is in progress");
            // The driver starts every Run as the declared tuple, with no challenges; the Run is put
            // under them here, as a save under challenges would be loaded, so that a value read
            // through Dungeon.isChallenged would differ from a cold generation's.
            Dungeon.challenges = Challenges.NO_FOOD | Challenges.DARKNESS | Challenges.STRONGER_BOSSES;
            Dungeon.depth = 3;
            assertTrue(Dungeon.isChallenged(Challenges.DARKNESS) && Dungeon.challenges != 0, "the Run is under challenges: " + Dungeon.challenges);
            assertEquals(Languages.GERMAN, Messages.lang(), "the Run is in another language");
            live = Generate.generate(CodexSeedFreeTest.ROOT);
            assertEquals(3, Dungeon.depth, "the generation left the Run's depth as it was");
            assertEquals(Challenges.NO_FOOD | Challenges.DARKNESS | Challenges.STRONGER_BOSSES, Dungeon.challenges,
                    "the generation left the Run's challenges as they were");
        } finally {
            SPDSettings.challenges(0);
            SPDSettings.language(Languages.ENGLISH);
            Messages.setup(Languages.ENGLISH);
        }
        assertEquals(before.keySet(), live.keySet());
        for (Map.Entry<String, String> file : live.entrySet()) {
            assertEquals(before.get(file.getKey()), file.getValue(), file.getKey() + " differs with a Run in progress");
            String committed = Files.readString(folder.resolve(file.getKey()), StandardCharsets.UTF_8);
            assertEquals(committed, file.getValue(), file.getKey() + " differs from the committed folder; run ./gradlew :codex:generate");
        }
    }

    @Test
    @DisplayName("the depth-scaled mobs and the Stronger Bosses variants fall out of the constructors, and a random facet is named")
    void the_variants_are_the_games() {
        Path root = CodexSeedFreeTest.ROOT;
        Map<String, Codex.MobEntry> byName = new java.util.TreeMap<>();
        for (Codex.MobEntry entry : Mobs.entries(root)) {
            byName.put(entry.className(), entry);
        }
        Codex.MobEntry rat = byName.get("actors.mobs.Rat");
        assertEquals(8, rat.ht());
        assertEquals(2, rat.defenseSkill());
        assertEquals(1, rat.exp());
        assertEquals(5, rat.maxLvl());
        assertEquals(Codex.RollKind.NORMAL, rat.damage().kind());
        assertEquals(1, rat.damage().min());
        assertEquals(4, rat.damage().max());
        assertEquals(Codex.RollKind.CONSTANT, rat.attack().kind());
        assertEquals(8, rat.attack().min());
        assertEquals(Codex.RollKind.NORMAL, rat.dr().kind());
        assertEquals(0, rat.dr().min());
        assertEquals(1, rat.dr().max());
        assertTrue(rat.variants().isEmpty(), "a rat is a rat at every depth: " + rat.variants());
        for (String scaled : new String[] {"actors.mobs.Piranha", "actors.mobs.Statue", "actors.mobs.ArmoredStatue", "actors.mobs.PhantomPiranha"}) {
            Codex.MobEntry entry = byName.get(scaled);
            long byDepth = entry.variants().stream().filter(v -> v.challenge().isEmpty()).count();
            assertEquals(Mobs.MAX_DEPTH - 1, byDepth, scaled + " scales with every depth");
        }
        for (String boss : new String[] {"actors.mobs.Goo", "actors.mobs.Tengu", "actors.mobs.DM300", "actors.mobs.DwarfKing", "actors.mobs.Pylon"}) {
            Codex.MobEntry entry = byName.get(boss);
            List<String> challenges = entry.variants().stream().map(Codex.Variant::challenge).filter(c -> !c.isEmpty()).toList();
            assertEquals(List.of("STRONGER_BOSSES"), challenges, boss + " differs under Stronger Bosses and no other flag");
        }
        Codex.MobEntry goo = byName.get("actors.mobs.Goo");
        assertEquals(100, goo.ht());
        assertEquals("120", goo.variants().get(0).fields().get(0).value(), "Goo under Stronger Bosses");
        Codex.MobEntry vaultBoss = byName.get("actors.mobs.quest.vault.VaultBossElemental");
        assertTrue(vaultBoss.propertiesRandom() && vaultBoss.properties().isEmpty(), "the element is a draw");
        assertEquals(Codex.LootKind.RANDOM, byName.get("actors.mobs.Thief").loot().kind());
        assertTrue(byName.get("actors.mobs.Thief").loot().random());
        Codex.MobEntry decoy = byName.get("actors.hero.abilities.rogue.SmokeBomb.NinjaLog");
        assertEquals(List.of("ht"), decoy.runDependent(), "the decoy's hit points are the hero's talent's");
        assertEquals(0, decoy.ht());
    }

    @Test
    @DisplayName("every citation opens to a line that holds the entry's declaration, its parts in order, and the tables are complete")
    void every_citation_resolves() throws IOException {
        Path root = CodexSeedFreeTest.ROOT;
        List<Codex.HeroClassEntry> heroes = Generate.heroClasses(root);
        assertEquals(org.shatterfish.api.HeroClass.values().length, heroes.size(), "every api hero class");
        assertEquals(HeroClass.values().length, heroes.size(), "every game hero class");
        for (Codex.HeroClassEntry entry : heroes) {
            String line = lineOf(root, entry.citation());
            assertTrue(line.matches(".*\\b" + entry.heroClass().name() + "\\s*\\(.*"), entry.citation().reference() + " holds " + entry.heroClass() + ": " + line);
            int at = -1;
            for (org.shatterfish.api.HeroSubclass subclass : entry.subclasses()) {
                int next = wordAt(line, subclass.name(), at + 1);
                assertTrue(next > at, entry.citation().reference() + " names " + subclass + " after the one before: " + line);
                at = next;
            }
        }
        List<Codex.ChallengeEntry> challenges = Generate.challenges(root);
        assertEquals(Challenges.MAX_CHALS, challenges.size(), "every challenge flag");
        for (Codex.ChallengeEntry entry : challenges) {
            String line = lineOf(root, entry.citation());
            assertTrue(wordAt(line, entry.challenge().name(), 0) >= 0 && line.contains("= " + entry.mask() + ";"),
                    entry.citation().reference() + " holds " + entry.challenge() + " = " + entry.mask() + ": " + line);
        }
        for (Codex.MobEntry entry : Mobs.entries(root)) {
            String simple = entry.className().substring(entry.className().lastIndexOf('.') + 1);
            assertTrue(wordAt(lineOf(root, entry.citation()), simple, 0) >= 0, entry.citation().reference() + " declares " + simple);
            for (Codex.Roll roll : List.of(entry.damage(), entry.attack(), entry.dr())) {
                String line = lineOf(root, roll.citation());
                assertTrue(line.contains("damageRoll") || line.contains("attackSkill") || line.contains("drRoll"),
                        roll.citation().reference() + " declares a roll: " + line);
            }
            assertTrue(lineOf(root, entry.loot().citation()).contains("loot"), entry.loot().citation().reference() + " declares loot");
        }
        Codex.SpawnRotation rotation = Rotation.read(root);
        for (Codex.RotationDepth depth : rotation.depths()) {
            assertTrue(lineOf(root, depth.citation()).contains("case " + depth.depth() + ":") || depth.depth() == 1,
                    depth.citation().reference() + " is the case of depth " + depth.depth());
        }
        for (Codex.RareMob rare : rotation.rareMobs()) {
            assertTrue(lineOf(root, rare.citation()).contains(rare.className() + ".class"), rare.citation().reference());
        }
        for (Codex.RareAlt alt : rotation.alternates()) {
            assertTrue(lineOf(root, alt.citation()).contains(alt.className() + ".class"), alt.citation().reference());
        }
        assertTrue(lineOf(root, rotation.champion().citation()).contains("rollForChampion"));
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

    /** The index of {@code word} in {@code line} as a whole word, from {@code from}, or -1. */
    private static int wordAt(String line, String word, int from) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b").matcher(line);
        return m.find(from) ? m.start() : -1;
    }

    private static String lineOf(Path root, Codex.Citation citation) throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(citation.path()), StandardCharsets.UTF_8);
        assertTrue(citation.line() <= lines.size(), citation.reference() + " is inside the file");
        return lines.get(citation.line() - 1);
    }
}
