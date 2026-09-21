package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.utils.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex is seed-free (FR-14, story 2.1): generating under two seeds and two Profiles that
 * differ in what they hold gives the same bytes, the committed folder is what a fresh generation
 * writes, byte for byte, and the folder's name is the tag the ledger pins. Together these are
 * the guarantee every later table inherits by being generated the same way.
 *
 * <p>Story 2.9 puts the generated pages under the same check: {@code docs/codex/} is compared with
 * a fresh rendering the way {@code codex/<tag>/} is, both folders are written by one run of
 * {@code main}, and a file neither output holds any more is deleted from its folder. So a
 * hand-edited page fails the build exactly as a hand-edited table does, naming the file and the
 * command that fixes it.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class CodexSeedFreeTest {

    /** The repository root, found from the working directory by what a checkout holds. */
    static final Path ROOT = root();

    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("docs/UPSTREAM.md")) && Files.isDirectory(p.resolve("core/src/main/java"))) {
                return p;
            }
        }
        throw new IllegalStateException("no Shatterfish checkout above " + here);
    }

    @Test
    @DisplayName("two generations under different seeds and Profiles that differ are byte-identical, tables and pages alike")
    void two_generations_are_identical(@TempDir Path first, @TempDir Path second) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        Profile.prepare(boot, first);
        Dungeon.seed = 1_234L;
        Random.pushGenerator(1_234L);
        Map<String, String> a;
        try {
            a = Generate.generate(ROOT);
        } finally {
            Random.popGenerator();
        }
        // The second Profile differs in what it holds, not only in where it is: a language that is
        // not English and a scale that is not the default, the two settings a table would read
        // first if it read a Profile at all.
        Profile.prepare(boot, second);
        SPDSettings.language(Languages.GERMAN);
        Messages.setup(Languages.GERMAN);
        SPDSettings.scale(4);
        Dungeon.seed = 987_654_321L;
        Random.pushGenerator(987_654_321L);
        // The Codex's own generator moves too, so that a value a constructor drew and a table
        // carried would differ here (story 2.2).
        GameContext.generatorSeed = 987_654_321L;
        Map<String, String> b;
        try {
            assertEquals(Languages.GERMAN, Messages.lang());
            b = Generate.generate(ROOT);
        } finally {
            GameContext.generatorSeed = GameContext.CODEX_GENERATOR_SEED;
            Random.popGenerator();
            SPDSettings.language(Languages.ENGLISH);
            Messages.setup(Languages.ENGLISH);
            SPDSettings.scale(0);
        }
        assertEquals(a.keySet(), b.keySet());
        for (String file : a.keySet()) {
            assertEquals(a.get(file), b.get(file), file + " differs between two seeds and two Profiles");
        }
        // The pages are rendered from the text above, so they are seed-free for the same reason;
        // the rendering is held here too, since a page that read a clock or a machine would be a
        // drift the table comparison above could not see (story 2.9).
        Map<String, String> pagesA = Pages.pages(ROOT, a);
        Map<String, String> pagesB = Pages.pages(ROOT, b);
        assertEquals(pagesA.keySet(), pagesB.keySet());
        for (String page : pagesA.keySet()) {
            assertEquals(pagesA.get(page), pagesB.get(page), page + " differs between two seeds and two Profiles");
        }
    }

    @Test
    @DisplayName("the committed codex/<tag>/ and docs/codex/ are a fresh generation, byte for byte, with line feeds only and no file extra")
    void the_committed_folder_is_a_fresh_generation() throws IOException {
        Map<String, String> fresh = Generate.generate(ROOT);
        assertCommitted(ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT)), fresh, Generate.FOLDER + "/");
        // The pages are held exactly as the tables are, and by the same words: a hand-edited page
        // fails the build naming the page and the one command that writes it (story 2.9).
        assertCommitted(ROOT.resolve(Pages.FOLDER), Pages.pages(ROOT, fresh), Pages.FOLDER + "/");
    }

    /** {@code folder} holds exactly {@code fresh}, byte for byte, with line feeds only. */
    private static void assertCommitted(Path folder, Map<String, String> fresh, String renormalise) throws IOException {
        assertTrue(Files.isDirectory(folder), folder + " is committed");
        TreeSet<String> committed = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).forEach(f -> committed.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), committed, "the files under " + folder + "; run ./gradlew :codex:generate and commit");
        for (String file : new TreeSet<>(fresh.keySet())) {
            byte[] onDisk = Files.readAllBytes(folder.resolve(file));
            assertFalse(new String(onDisk, StandardCharsets.UTF_8).contains("\r"),
                    file + " holds a carriage return; run git add --renormalize " + renormalise + " and commit");
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8), onDisk,
                    "the first differing file is " + file + "; run ./gradlew :codex:generate and commit");
        }
    }

    @Test
    @DisplayName("the task's main writes the tables and the pages, deletes a stale file from each, and the folder is named by the tag docs/UPSTREAM.md pins")
    void main_writes_the_folder_and_the_tag_is_the_pinned_one(@TempDir Path out, @TempDir Path site) throws IOException {
        Files.writeString(out.resolve("stale.json"), "[]\n", StandardCharsets.UTF_8);
        // A page whose table the generator no longer writes is deleted with it, so the folder is
        // exactly what was generated (story 2.9).
        Files.writeString(site.resolve("stale.md"), "# gone\n", StandardCharsets.UTF_8);
        Generate.main(new String[] {ROOT.toString(), out.toString(), site.toString()});
        Map<String, String> fresh = Generate.generate(ROOT);
        assertWritten(out, fresh);
        assertWritten(site, Pages.pages(ROOT, fresh));
        String ledger = Files.readString(ROOT.resolve("docs/UPSTREAM.md"), StandardCharsets.UTF_8);
        Matcher pinned = Pattern.compile("\\|\\s*Tag\\s*\\|\\s*`([^`]+)`\\s*\\|").matcher(ledger);
        assertTrue(pinned.find(), "docs/UPSTREAM.md pins a tag in its table");
        String tag = pinned.group(1);
        assertFalse(pinned.find(), "docs/UPSTREAM.md pins one tag");
        assertEquals(tag, Upstream.tag(ROOT), "the generator's tag and the ledger's pin");
        assertThrowsNotACheckout(out);
        // One folder named and not the other would write the repository's pages over a test's, so
        // main takes either one argument or three.
        IllegalArgumentException refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Generate.main(new String[] {ROOT.toString(), out.toString()}));
        assertTrue(refused.getMessage().contains("usage: Generate"), refused.getMessage());
    }

    /** {@code folder} holds exactly {@code fresh}, and the stale file put there is gone. */
    private static void assertWritten(Path folder, Map<String, String> fresh) throws IOException {
        TreeSet<String> written = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).forEach(f -> written.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), written, "main writes every file under " + folder + " and deletes the stale one");
        for (String file : fresh.keySet()) {
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8), Files.readAllBytes(folder.resolve(file)), file);
        }
    }

    private static void assertThrowsNotACheckout(Path notAcheckout) {
        IllegalArgumentException refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Generate.main(new String[] {notAcheckout.toString()}));
        assertTrue(refused.getMessage().contains("not a Shatterfish checkout"), refused.getMessage());
    }
}
