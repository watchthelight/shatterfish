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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pairing and testing two folders of Runs (story 3.6).
 *
 * <p>The logs are written by hand so that each case can hold exactly what it is about: which file
 * answers for which triple, in which order, what counts as missing, and when missing voids a verdict.
 * The real child path -- Runs played, logs written by the harness -- is {@code RunnerComparisonTest}.
 */
class ComparisonTest {

    private static final String TAG = "v4.0.0";

    private static final String ZERO = "0".repeat(64);

    /** Seeds that are not in ascending order, so that no map keyed by seed can pass for an order. */
    private static SeedSet set(int size) {
        List<SeedSet.Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            long seed = 900_000L - 7919L * i;
            entries.add(new SeedSet.Entry(seed, HeroClass.WARRIOR, 0, SeedSet.code(seed)));
        }
        return new SeedSet("smoke", 1, entries);
    }

    /** A log for one side of one triple: a header, and an ending with this cause at this depth. */
    private static void log(Path folder, SeedSet.Entry triple, long salt, String brain, int depth,
                            String cause, boolean ended) throws IOException {
        Files.createDirectories(folder);
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, TAG, "abc1234", triple.heroClass(),
                triple.challengeFlags(), triple.seed(), triple.seedCode(), salt, 20_000, 3, 2, 8,
                new RunLog.Brain(brain, "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        StringBuilder text = new StringBuilder(RunLogJson.line("", header)).append('\n');
        if (ended) {
            RunLog.End end = new RunLog.End(0, new RunLog.Outcome(false, false, 0, depth, 100, cause, 0),
                    true);
            text.append(RunLogJson.line(RunLogJson.chain("", header), end)).append('\n');
        }
        Files.writeString(folder.resolve(RunLog.fileName(header.runId())), text.toString(),
                StandardCharsets.UTF_8);
    }

    private static void died(Path out, String side, SeedSet.Entry triple, long salt, String brain,
                             int depth) throws IOException {
        log(out.resolve(side), triple, salt, brain, depth, "DEATH", true);
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
                died(out, Comparison.CANDIDATE, set.entries().get(i), salts.get(i), "greedy", mine[i]);
            }
            died(out, Comparison.BASELINE, set.entries().get(i), salts.get(i), "random", theirs[i]);
        }

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.BETTER, PairScore.EQUAL, PairScore.WORSE, PairScore.EQUAL),
                report.scores(), "deeper, equal, shallower, and the fourth candidate Run is missing");
        assertEquals(1, report.missing());
    }

    @Test
    @DisplayName("the order the pairs are consumed in decides where the test stops")
    void the_order_is_the_seed_sets(@TempDir Path out) throws IOException {
        // Ten pairs alternating win and loss, then thirty wins, in the Seed set's order. Fed in that
        // order the test is inside its bounds at the burn-in -- a mean of one half with all the
        // variance a pair can have -- and accepts some way into the wins. Reversed, the wins come
        // first and it accepts at the burn-in. So the stop and the trace say which order was used,
        // which is the one property this class exists to keep.
        SeedSet set = set(40);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            salts.add(1000L + i);
            died(out, Comparison.CANDIDATE, set.entries().get(i), 1000L + i, "greedy",
                    i < 10 && i % 2 == 1 ? 1 : 9);
            died(out, Comparison.BASELINE, set.entries().get(i), 1000L + i, "random", 5);
        }
        Gsprt test = new Gsprt(0.50, 0.55, 0.05, 0.05, 10, 100).allowingMissing(50);

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", test);

        List<PairScore> ordered = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ordered.add(i % 2 == 1 ? PairScore.WORSE : PairScore.BETTER);
        }
        ordered.addAll(Collections.nCopies(30, PairScore.BETTER));
        List<PairScore> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);
        Gsprt.Result inOrder = new Gsprt(0.50, 0.55, 0.05, 0.05, 10, 100).run(ordered);
        Gsprt.Result backwards = new Gsprt(0.50, 0.55, 0.05, 0.05, 10, 100).run(reversed);
        assertNotEquals(inOrder.pairs(), backwards.pairs(), "the case is one where order matters");
        assertEquals(inOrder.pairs(), report.result().pairs(), "consumed in the Seed set's order");
        assertEquals(inOrder.trace(), report.result().trace());
    }

    @Test
    @DisplayName("a Run under another salt is not this pair's Run, and counts as missing")
    void the_salt_is_part_of_the_pair(@TempDir Path out) throws IOException {
        SeedSet set = set(1);
        died(out, Comparison.CANDIDATE, set.entries().get(0), 99L, "greedy", 9);
        died(out, Comparison.BASELINE, set.entries().get(0), 11L, "random", 1);

        Comparison.Report report = Comparison.of(set, List.of(11L), TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.EQUAL), report.scores());
        assertEquals(1, report.missing(), "the candidate's Run is of a different salt, so there is no pair");
    }

    @Test
    @DisplayName("a log with no ending, and a Run stopped at the turn cap, are missing, not scored")
    void what_counts_as_missing(@TempDir Path out) throws IOException {
        // The turn cap is the Rig stopping a Run, not the game ending it (ADR-0012). Scored on turns
        // survived, a Brain that stalled until the cap would beat every death at the same depth.
        SeedSet set = set(2);
        log(out.resolve(Comparison.CANDIDATE), set.entries().get(0), 1L, "greedy", 1, "DEATH", false);
        died(out, Comparison.BASELINE, set.entries().get(0), 1L, "random", 1);
        log(out.resolve(Comparison.CANDIDATE), set.entries().get(1), 2L, "greedy", 3, "TURN_CAP", true);
        died(out, Comparison.BASELINE, set.entries().get(1), 2L, "random", 3);

        Comparison.Report report = Comparison.of(set, List.of(1L, 2L), TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.EQUAL, PairScore.EQUAL), report.scores());
        assertEquals(2, report.missing());
    }

    @Test
    @DisplayName("a log with two headers is two Runs' worth of claims, and counts as missing")
    void one_header_per_log(@TempDir Path out) throws IOException {
        // Chained correctly, so the reader finds nothing wrong with it line by line: only the count
        // of headers says the file is not one Run.
        SeedSet set = set(1);
        SeedSet.Entry triple = set.entries().get(0);
        died(out, Comparison.BASELINE, triple, 7L, "random", 1);
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, TAG, "abc1234", triple.heroClass(),
                triple.challengeFlags(), triple.seed(), triple.seedCode(), 7L, 20_000, 3, 2, 8,
                new RunLog.Brain("greedy", "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        RunLog.End end = new RunLog.End(0, new RunLog.Outcome(false, false, 0, 9, 100, "DEATH", 0),
                true);
        String first = RunLogJson.chain("", header);
        String second = RunLogJson.chain(first, header);
        Path folder = out.resolve(Comparison.CANDIDATE);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(RunLog.fileName(header.runId())),
                RunLogJson.line("", header) + "\n" + RunLogJson.line(first, header) + "\n"
                        + RunLogJson.line(second, end) + "\n", StandardCharsets.UTF_8);

        Comparison.Report report = Comparison.of(set, List.of(7L), TAG, out, "greedy", "random", null);

        assertEquals(List.of(PairScore.EQUAL), report.scores());
        assertEquals(1, report.missing());
    }

    @Test
    @DisplayName("a value no long can hold in millionths is refused, not written as Long.MAX_VALUE")
    void micros_refuse_to_saturate() {
        assertEquals(-2_944_439L, Comparison.micros(-2.944439));
        assertThrows(IllegalArgumentException.class, () -> Comparison.micros(1.0e13));
        assertThrows(IllegalArgumentException.class, () -> Comparison.micros(Double.NaN));
    }

    @Test
    @DisplayName("under the e-process the crash exploit is void too, and the report names the statistic")
    void the_eprocess_in_a_comparison(@TempDir Path out) throws IOException {
        // The same forty pairs as the crash case below: wins where the candidate finished, missing
        // Runs where it crashed. The e-process shares the VOID rule, so the result is void.
        SeedSet set = set(40);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            salts.add(3000L + i);
            if (i % 2 == 0) {
                died(out, Comparison.CANDIDATE, set.entries().get(i), 3000L + i, "greedy", 9);
            }
            died(out, Comparison.BASELINE, set.entries().get(i), 3000L + i, "random", 5);
        }
        EProcess test = new EProcess(0.50, 0.60, 0.05, 0.05, 100).allowingMissing(50);

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", test);
        Comparison.write(out, report, "H-0001-x@0123456789abcdef", true);

        assertEquals(Gsprt.Verdict.VOID, report.result().verdict());
        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("EPROCESS", LogHeader.string(json, "statistic"), json);
        assertEquals(String.valueOf(Math.round(Math.log(20) * 1_000_000)),
                LogHeader.value(json, "upper_micros"), json);
        assertEquals("1", LogHeader.value(json, "burn_in"), "the e-process has none: " + json);
    }

    @Test
    @DisplayName("the within-pair correlation of turns is over reached pairs, and absent when it cannot be computed")
    void the_correlation(@TempDir Path out) throws IOException {
        SeedSet set = set(5);
        List<Long> salts = new ArrayList<>();
        long[] mine = {100, 200, 300, 400, 0};
        long[] theirs = {110, 190, 330, 390, 50};
        for (int i = 0; i < 5; i++) {
            salts.add(500L + i);
            RunLog.Outcome a = new RunLog.Outcome(false, false, 0, 1, mine[i], "DEATH", 0);
            RunLog.Outcome b = new RunLog.Outcome(false, false, 0, 1, theirs[i], "DEATH", 0);
            written(out.resolve(Comparison.CANDIDATE), set.entries().get(i), 500L + i, "greedy",
                    i == 4 ? null : a);
            written(out.resolve(Comparison.BASELINE), set.entries().get(i), 500L + i, "random", b);
        }

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", null);
        Comparison.Correlation r = Comparison.correlation(report.pairs());

        // By hand over the four reached pairs: mean 250 and 255; deviations (-150, -50, 50, 150)
        // and (-145, -65, 75, 135); covariance sum 21750 + 3250 + 3750 + 20250 = 49000; variances
        // 50000 and 21025 + 4225 + 5625 + 18225 = 49100; r = 49000 / sqrt(50000 * 49100) = 0.988941.
        assertEquals(4, r.pairs(), "the pair with a missing Run is left out");
        assertEquals(0.988941, r.r(), 1e-6);
        Comparison.write(out, report, "", true);
        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("988941", LogHeader.value(json, "turns_correlation_micros"), json);
        assertEquals("4", LogHeader.value(json, "correlated_pairs"), json);
        assertEquals("0", LogHeader.value(json, "identical_pairs"), json);

        assertEquals(null, Comparison.correlation(report.pairs().subList(0, 2)).r(), "two pairs is not enough");
    }

    @Test
    @DisplayName("a perfect correlation is written as one, never a hair past it")
    void the_correlation_is_clamped(@TempDir Path out) throws IOException {
        // Turns in exact proportion: r is one, and these values carry the unclamped quotient to
        // 1.0000000000000002 in double arithmetic.
        long[] turns = {827037, 220154, 98419, 511555, 29725, 936711};
        SeedSet set = set(turns.length);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < turns.length; i++) {
            salts.add(900L + i);
            written(out.resolve(Comparison.CANDIDATE), set.entries().get(i), 900L + i, "greedy",
                    new RunLog.Outcome(false, false, 0, 1, turns[i], "DEATH", 0));
            written(out.resolve(Comparison.BASELINE), set.entries().get(i), 900L + i, "random",
                    new RunLog.Outcome(false, false, 0, 1, 25 * turns[i], "DEATH", 0));
        }
        Comparison.Correlation r = Comparison.correlation(
                Comparison.of(set, salts, TAG, out, "greedy", "random", null).pairs());

        assertTrue(r.r() <= 1.0, "r = " + r.r());
        assertEquals(1.0, r.r(), 1e-12);
    }

    @Test
    @DisplayName("pairs that ended alike are counted identical, a missing pair never is, and no variance means no correlation")
    void identical_pairs(@TempDir Path out) throws IOException {
        List<Long> salts = List.of(1L, 2L, 3L, 4L);
        RunLog.Outcome same = new RunLog.Outcome(false, false, 0, 1, 700, "DEATH", 0);
        // Three pairs that ended alike, a fourth whose candidate Run is missing, and a fifth pair both Runs of which stopped at a window the Harness does not know, the same
        // way: equal outcomes, and still missing -- neither identical nor correlated.
        SeedSet five = set(5);
        RunLog.Outcome lost = new RunLog.Outcome(false, false, 0, 1, 700, "UNKNOWN_WINDOW", 0);
        written(out.resolve(Comparison.CANDIDATE), five.entries().get(4), 5L, "greedy", lost);
        written(out.resolve(Comparison.BASELINE), five.entries().get(4), 5L, "random", lost);
        for (int i = 0; i < 4; i++) {
            written(out.resolve(Comparison.CANDIDATE), five.entries().get(i), salts.get(i), "greedy",
                    i == 3 ? null : same);
            written(out.resolve(Comparison.BASELINE), five.entries().get(i), salts.get(i), "random", same);
        }

        Comparison.Report report = Comparison.of(five, List.of(1L, 2L, 3L, 4L, 5L), TAG, out, "greedy",
                "random", null);
        Comparison.write(out, report, "", true);
        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();

        assertEquals("3", LogHeader.value(json, "identical_pairs"),
                "neither the missing fourth nor the both-unknown fifth is identical: " + json);
        assertEquals("3", LogHeader.value(json, "correlated_pairs"), json);
        assertEquals(null, LogHeader.value(json, "turns_correlation_micros"),
                "every turn count the same, so there is no correlation to write: " + json);
    }

    /** A log for one side of one triple ending in exactly this outcome, or with no ending when null. */
    private static void written(Path folder, SeedSet.Entry triple, long salt, String brain,
                                RunLog.Outcome outcome) throws IOException {
        Files.createDirectories(folder);
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, TAG, "abc1234", triple.heroClass(),
                triple.challengeFlags(), triple.seed(), triple.seedCode(), salt, 20_000, 3, 2, 8,
                new RunLog.Brain(brain, "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        StringBuilder text = new StringBuilder(RunLogJson.line("", header)).append('\n');
        if (outcome != null) {
            text.append(RunLogJson.line(RunLogJson.chain("", header), new RunLog.End(0, outcome, true)))
                    .append('\n');
        }
        Files.writeString(folder.resolve(RunLog.fileName(header.runId())), text.toString(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a Brain that crashes on the seeds it would lose gets a void result, not an accept")
    void crashing_to_a_tie_is_void(@TempDir Path out) throws IOException {
        // The exploit the fairness review found. Twenty pairs the candidate wins, and twenty it would
        // lose -- but on those it crashed, so each is missing and scores a tie. Scored as ties, the
        // mean rises and the variance falls, and the test accepts. Under the Registration's cap of
        // five percent missing, the result is void instead.
        SeedSet set = set(40);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            salts.add(2000L + i);
            if (i % 2 == 0) {
                died(out, Comparison.CANDIDATE, set.entries().get(i), 2000L + i, "greedy", 9);
            }
            died(out, Comparison.BASELINE, set.entries().get(i), 2000L + i, "random", 5);
        }

        Gsprt uncapped = new Gsprt(0.50, 0.55, 0.05, 0.05, 20, 100);
        Gsprt capped = new Gsprt(0.50, 0.55, 0.05, 0.05, 20, 100).allowingMissing(50);

        assertEquals(Gsprt.Verdict.ACCEPT,
                Comparison.of(set, salts, TAG, out, "greedy", "random", uncapped).result().verdict(),
                "without a cap the crashes buy an accept -- which is the exploit");
        assertEquals(Gsprt.Verdict.VOID,
                Comparison.of(set, salts, TAG, out, "greedy", "random", capped).result().verdict());
    }

    @Test
    @DisplayName("the report carries every pair, the test's parameters and the counts the LLR is over")
    void the_report_recomputes(@TempDir Path out) throws IOException {
        SeedSet set = set(30);
        List<Long> salts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            salts.add(100L + i);
            died(out, Comparison.CANDIDATE, set.entries().get(i), 100L + i, "greedy", 5);
            died(out, Comparison.BASELINE, set.entries().get(i), 100L + i, "random", 1);
        }
        Gsprt test = new Gsprt(0.50, 0.55, 0.05, 0.05, 10, 100).allowingMissing(50);

        Comparison.Report report = Comparison.of(set, salts, TAG, out, "greedy", "random", test);
        Comparison.write(out, report, "H-0001-x@0123456789abcdef", true);

        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("ACCEPT", LogHeader.string(json, "verdict"), json);
        assertEquals("10", LogHeader.value(json, "stopped_at"), json);
        // Over the ten pairs consumed, not the thirty played: the counts an LLR is a function of.
        assertEquals("10", LogHeader.value(json, "consumed_better"), json);
        assertEquals("0", LogHeader.value(json, "consumed_equal"), json);
        assertEquals("500000", LogHeader.value(json, "p0_micros"), json);
        assertEquals("550000", LogHeader.value(json, "p1_micros"), json);
        assertEquals("GSPRT", LogHeader.string(json, "statistic"), "which design the bounds belong to");
        assertEquals(String.valueOf(Math.round(Math.log(0.95 / 0.05) * 1_000_000)),
                LogHeader.value(json, "upper_micros"), json);
        assertEquals("true", LogHeader.value(json, "direction_check"), json);
        assertEquals("H-0001-x@0123456789abcdef", LogHeader.string(json, "registration"), json);
        String pairs = LogHeader.value(json, "pairs");
        assertEquals(30, pairs.split("\"candidate_run\"").length - 1, "every pair, with its Runs");
        assertTrue(pairs.contains(RunLog.salt(100L)), "and its salt");
    }
}
