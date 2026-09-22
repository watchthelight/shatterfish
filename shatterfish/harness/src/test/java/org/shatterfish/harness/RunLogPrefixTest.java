package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a killed Run leaves behind (story 3.2, ADR-0011).
 *
 * <p>A comparison runs thousands of Runs across processes, and some of them will be killed: a
 * timeout, a crash, a machine going away. The Rig counts those rather than losing them, and scores
 * their pairs as ties (ADR-0012), which it can only do if their logs are still readable. So the
 * writer flushes a whole line before the next record exists, and this is the test of that: cut the
 * file anywhere and what is left still reads, and still verifies as far as it goes.
 */
class RunLogPrefixTest {

    private static final long SEED = 0xBEEFL;

    private static final long SALT = 0x5A17_5A17L;

    /** Enough turns for a header, a run of waits and an end, and no more (see ChainRecomputeTest). */
    private static final int CAP = 120;

    private static byte[] log(Path folder) throws IOException {
        RunLog.Brain who = new RunLog.Brain("random", "0".repeat(40), "0".repeat(64));
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(11L), CAP,
                new RunLoop.Logging(folder, "0".repeat(40), who, "", "test"));
        return Files.readAllBytes(folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SeedSet.code(SEED), SALT, "random"))));
    }

    @Test
    @DisplayName("cut at any line boundary, the prefix reads and every chain in it verifies")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void every_whole_prefix_verifies(@TempDir Path folder) throws IOException {
        byte[] bytes = log(folder);
        LogText.Lines whole = LogText.lines(bytes);
        assertTrue(whole.complete(), "the Run finished, so it wrote whole lines");
        assertTrue(whole.whole().size() >= 3, "a header, waits and an end: " + whole.whole().size());

        int at = 0;
        int cuts = 0;
        for (String line : whole.whole()) {
            at += line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
            LogText.Lines prefix = LogText.lines(Arrays.copyOf(bytes, at));
            assertTrue(prefix.complete(), "a cut at a line boundary leaves whole lines");
            assertEquals(0, LogText.firstBrokenLine(prefix),
                    "the prefix through line " + prefix.whole().size() + " verifies as far as it goes");
            cuts++;
        }
        assertEquals(whole.whole().size(), cuts, "every boundary was cut at");
        // The offsets were computed by re-encoding each line, so this is the check that they are
        // the file's own: if they were not, every cut above was at the wrong place and the test
        // was verifying prefixes of its own arithmetic.
        assertEquals(bytes.length, at, "the cuts walked the whole file, byte for byte");
    }

    @Test
    @DisplayName("cut mid-line, the whole lines still verify and the partial one is left as it is")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_partial_last_line_is_not_repaired(@TempDir Path folder) throws IOException {
        byte[] bytes = log(folder);
        LogText.Lines whole = LogText.lines(bytes);
        int first = whole.whole().get(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
        int second = whole.whole().get(1).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        // Halfway through the second record: the header is whole, the wait is not.
        LogText.Lines cut = LogText.lines(Arrays.copyOf(bytes, first + second / 2));

        assertFalse(cut.complete(), "the last line never reached the disk whole");
        assertFalse(cut.partial().isEmpty(), "and what did reach it is still there to look at");
        assertEquals(1, cut.whole().size(), "the header is whole");
        assertEquals(0, LogText.firstBrokenLine(cut), "and it verifies");
        // The partial line is not a record. A reader that parsed it would be inventing the half
        // that never arrived, which is worse than reporting the Run as incomplete. It is refused
        // naming what is wrong with it, rather than by an exception with a character offset.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LogText.keys(cut.partial()));
        assertTrue(refused.getMessage().contains("one JSON object"), refused.getMessage());
        assertFalse(LogText.whole(cut), "and the log is not a whole Run's");
    }

    @Test
    @DisplayName("a log with no end record is incomplete, and says so by not saying otherwise")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_killed_run_has_no_ending(@TempDir Path folder) throws IOException {
        List<String> lines = LogText.lines(log(folder)).whole();
        assertEquals("end", LogText.string(lines.get(lines.size() - 1), "t"), "this one finished");

        List<String> killed = lines.subList(0, lines.size() - 1);
        assertEquals(0, LogText.firstBrokenLine(new LogText.Lines(List.copyOf(killed), "")),
                "a Run killed before its ending still verifies");
        for (String line : killed) {
            assertFalse("end".equals(LogText.string(line, "t")),
                    "and nothing in it claims the Run ended");
        }
    }

    @Test
    @DisplayName("the writer refuses a file that already exists rather than overwriting the evidence")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_second_run_of_one_id_is_refused(@TempDir Path folder) throws IOException {
        log(folder);
        IllegalStateException taken = assertThrows(IllegalStateException.class, () -> log(folder));
        assertTrue(taken.getMessage().contains("already written"), taken.getMessage());
        assertTrue(taken.getMessage().contains("overwrite the other's evidence"), taken.getMessage());
    }
}
