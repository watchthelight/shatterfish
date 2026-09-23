package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pairing and testing two folders of Runs (story 3.6).
 *
 * <p>The logs are written rather than played: a ranked comparison needs two different Brains, and
 * the second arrives with story 3.9. What is being held here is the pairing -- which file answers
 * for which triple under which salt -- the order, the missing count, and the handoff to the test.
 */
class ComparisonTest {

    private static final String TAG = "v4.0.0";

    private static final String ZERO = "0".repeat(64);

    private static SeedSet set(int size) {
        List<SeedSet.Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            long seed = 1000 + i;
            entries.add(new SeedSet.Entry(seed, HeroClass.WARRIOR, 0, SeedSet.code(seed)));
        }
        return new SeedSet("smoke", 1, entries);
    }

    /** A finished log for one side of one triple, reaching the given depth. */
    private static void log(Path folder, SeedSet.Entry triple, long salt, String brain, int depth)
            throws IOException {
        Files.createDirectories(folder);
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, TAG, "abc1234", triple.heroClass(),
                triple.challengeFlags(), triple.seed(), triple.seedCode(), salt, 20_000, 3, 2, 8,
                new RunLog.Brain(brain, "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        RunLog.End end = new RunLog.End(0, new RunLog.Outcome(false, false, 0, depth, 100, "DEATH", 0),
                true);
        String first = RunLogJson.line("", header);
        String second = RunLogJson.line(RunLogJson.chain("", header), end);
        Files.writeString(folder.resolve(RunLog.fileName(header.runId())), first + "\n" + second + "\n",
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("pairs are scored in the Seed set's order, and a missing Run is a counted tie")
    void pairs_in_order(@TempDir Path out) throws IOException {
        SeedSet set = set(4);
        List<Long> salts = List.of(11L, 22L, 33L, 44L);
        int[] mine = {5, 3, 4, 0};
        int[] theirs = {4, 3, 6, 2};
        for (int i = 0; i < 4; i++) {
            if (i != 3) {
                log(out.resolve(Comparison.CANDIDATE), set.entries().get(i), salts.get(i), "greedy", mine[i]);
            }
            log(out.resolve(Comparison.BASELINE), set.entries().get(i), salts.get(i), "random", theirs[i]);
        }

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.BETTER, PairScore.EQUAL, PairScore.WORSE, PairScore.EQUAL),
                report.scores(), "deeper, equal, shallower, and the fourth candidate Run is missing");
        assertEquals(1, report.missing());
    }

    @Test
    @DisplayName("a Run under another salt is not this pair's Run, and counts as missing")
    void the_salt_is_part_of_the_pair(@TempDir Path out) throws IOException {
        SeedSet set = set(1);
        log(out.resolve(Comparison.CANDIDATE), set.entries().get(0), 99L, "greedy", 9);
        log(out.resolve(Comparison.BASELINE), set.entries().get(0), 11L, "random", 1);

        Comparison.Report report = Comparison.of(set, List.of(11L), TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.EQUAL), report.scores());
        assertEquals(1, report.missing(), "the candidate's Run is of a different salt, so there is no pair");
    }

    @Test
    @DisplayName("under a test, the pairs are handed over in order and the verdict is written down")
    void the_test_runs_on_the_pairs(@TempDir Path out) throws IOException {
        SeedSet set = set(30);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            salts.add(100L + i);
            log(out.resolve(Comparison.CANDIDATE), set.entries().get(i), 100L + i, "greedy", 5);
            log(out.resolve(Comparison.BASELINE), set.entries().get(i), 100L + i, "random", 1);
        }
        Gsprt test = new Gsprt(0.50, 0.55, 0.05, 0.05, 10, 100);

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", test);
        Comparison.write(out, report, "H-0001-x@0123456789abcdef");

        assertNotNull(report.result());
        assertEquals(Gsprt.Verdict.ACCEPT, report.result().verdict());
        assertEquals(10, report.result().pairs(), "accepted at the burn-in");
        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("ACCEPT", LogHeader.string(json, "verdict"), json);
        assertEquals("10", LogHeader.value(json, "stopped_at"), json);
        assertEquals("30", LogHeader.value(json, "better"), json);
        assertEquals("H-0001-x@0123456789abcdef", LogHeader.string(json, "registration"), json);
    }
}
