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
    @DisplayName("two generations under different seeds and Profiles that differ are byte-identical")
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
        Map<String, String> b;
        try {
            assertEquals(Languages.GERMAN, Messages.lang());
            b = Generate.generate(ROOT);
        } finally {
            Random.popGenerator();
            SPDSettings.language(Languages.ENGLISH);
            Messages.setup(Languages.ENGLISH);
            SPDSettings.scale(0);
        }
        assertEquals(a.keySet(), b.keySet());
        for (String file : a.keySet()) {
            assertEquals(a.get(file), b.get(file), file + " differs between two seeds and two Profiles");
        }
    }

    @Test
    @DisplayName("the committed codex/<tag>/ is a fresh generation, byte for byte, with line feeds only and no file extra")
    void the_committed_folder_is_a_fresh_generation() throws IOException {
        Path folder = ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT));
        assertTrue(Files.isDirectory(folder), folder + " is committed");
        Map<String, String> fresh = Generate.generate(ROOT);
        TreeSet<String> committed = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(f -> committed.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), committed, "the files under " + folder + "; run ./gradlew :codex:generate and commit");
        for (String file : new TreeSet<>(fresh.keySet())) {
            byte[] onDisk = Files.readAllBytes(folder.resolve(file));
            assertFalse(new String(onDisk, StandardCharsets.UTF_8).contains("\r"),
                    file + " holds a carriage return; run git add --renormalize codex/ and commit");
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8), onDisk,
                    "the first differing file is " + file + "; run ./gradlew :codex:generate and commit");
        }
    }

    @Test
    @DisplayName("the task's main writes the same bytes, deletes a stale file, and the folder is named by the tag docs/UPSTREAM.md pins")
    void main_writes_the_folder_and_the_tag_is_the_pinned_one(@TempDir Path out) throws IOException {
        Files.writeString(out.resolve("stale.json"), "[]\n", StandardCharsets.UTF_8);
        Generate.main(new String[] {ROOT.toString(), out.toString()});
        Map<String, String> fresh = Generate.generate(ROOT);
        TreeSet<String> written = new TreeSet<>();
        try (Stream<Path> files = Files.list(out)) {
            files.forEach(f -> written.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), written, "main writes every file and deletes the stale one");
        for (String file : fresh.keySet()) {
            assertArrayEquals(fresh.get(file).getBytes(StandardCharsets.UTF_8), Files.readAllBytes(out.resolve(file)), file);
        }
        String ledger = Files.readString(ROOT.resolve("docs/UPSTREAM.md"), StandardCharsets.UTF_8);
        Matcher pinned = Pattern.compile("\\|\\s*Tag\\s*\\|\\s*`([^`]+)`\\s*\\|").matcher(ledger);
        assertTrue(pinned.find(), "docs/UPSTREAM.md pins a tag in its table");
        String tag = pinned.group(1);
        assertFalse(pinned.find(), "docs/UPSTREAM.md pins one tag");
        assertEquals(tag, Upstream.tag(ROOT), "the generator's tag and the ledger's pin");
        assertThrowsNotACheckout(out);
    }

    private static void assertThrowsNotACheckout(Path notAcheckout) {
        IllegalArgumentException refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Generate.main(new String[] {notAcheckout.toString()}));
        assertTrue(refused.getMessage().contains("not a Shatterfish checkout"), refused.getMessage());
    }
}
