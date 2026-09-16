package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.watabou.utils.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex is seed-free (FR-14, story 2.1): generating under two seeds and two Profiles gives
 * the same bytes, the committed folder is what a fresh generation writes, and the folder's name
 * is the tag the ledger pins. Together these are the guarantee every later table inherits by
 * being generated the same way.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class CodexSeedFreeTest {

    /** The repository root: the test's working directory is the module's. */
    static final Path ROOT = Path.of("").toAbsolutePath().getParent().getParent();

    @Test
    @DisplayName("two generations under different seeds and different Profiles are byte-identical")
    void two_generations_are_identical() throws IOException {
        HeadlessBoot boot = HeadlessBoot.ensure();
        Profile.prepare(boot, Files.createTempDirectory("shatterfish-codex-a"));
        Dungeon.seed = 1_234L;
        Random.pushGenerator(1_234L);
        Map<String, String> first;
        try {
            first = Generate.generate(ROOT);
        } finally {
            Random.popGenerator();
        }
        Profile.prepare(boot, Files.createTempDirectory("shatterfish-codex-b"));
        Dungeon.seed = 987_654_321L;
        Random.pushGenerator(987_654_321L);
        Map<String, String> second;
        try {
            second = Generate.generate(ROOT);
        } finally {
            Random.popGenerator();
        }
        assertEquals(first.keySet(), second.keySet());
        for (String file : first.keySet()) {
            assertEquals(first.get(file), second.get(file), file + " differs between two seeds");
        }
    }

    @Test
    @DisplayName("the committed codex/<tag>/ is a fresh generation, byte for byte, and no file is extra")
    void the_committed_folder_is_a_fresh_generation() throws IOException {
        Path folder = ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag());
        assertTrue(Files.isDirectory(folder), folder + " is committed");
        Map<String, String> fresh = Generate.generate(ROOT);
        TreeSet<String> committed = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(f -> committed.add(f.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(fresh.keySet()), committed, "the files under " + folder);
        for (String file : new TreeSet<>(fresh.keySet())) {
            byte[] onDisk = Files.readAllBytes(folder.resolve(file));
            byte[] generated = fresh.get(file).getBytes(StandardCharsets.UTF_8);
            assertEquals(new String(generated, StandardCharsets.UTF_8), new String(onDisk, StandardCharsets.UTF_8),
                    "the first differing file is " + file + "; run ./gradlew :codex:generate and commit");
        }
    }

    @Test
    @DisplayName("the folder is named by the tag docs/UPSTREAM.md pins")
    void the_tag_is_the_pinned_one() throws IOException {
        String ledger = Files.readString(ROOT.resolve("docs/UPSTREAM.md"), StandardCharsets.UTF_8);
        Matcher pinned = Pattern.compile("\\|\\s*Tag\\s*\\|\\s*`([^`]+)`\\s*\\|").matcher(ledger);
        assertTrue(pinned.find(), "docs/UPSTREAM.md pins a tag in its table");
        assertEquals(pinned.group(1), Upstream.tag(), "the generator's tag and the ledger's pin");
    }
}
