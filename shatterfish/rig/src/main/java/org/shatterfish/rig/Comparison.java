package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Two Brains' Runs of one Seed set, paired and tested (story 3.6, ADR-0012).
 *
 * <p><b>A pair is two Runs of one triple under one salt.</b> The Runner draws the salt once per
 * triple and hands it to both children, which is what makes the pair a common-random-numbers
 * comparison: whatever the dungeon does at a given moment it does to both Brains. A pair is found
 * here by the run id both Runs' own headers name, and a Run whose file is absent, unreadable,
 * without an ending, or ended by something other than the game is scored a tie and counted as
 * missing.
 *
 * <p><b>Pairs are consumed in the Seed set's order.</b> Never in the order the Runs finished:
 * completion order depends on which Runs were slow, which depends on how they went, and a sequential
 * test fed in that order would be stopping on information it was not supposed to have yet.
 *
 * <p><b>The report can be recomputed from itself.</b> {@code comparison.json} carries every pair
 * (triple, salt, both run ids, score), the test's parameters, the counts over the pairs the test
 * actually consumed, and a hash of each side's run index -- so a reader can redo the arithmetic,
 * find every log it rests on, and see if either index was changed after the verdict was written.
 */
public final class Comparison {

    /** The file beside the two sides' folders. */
    public static final String FILE = "comparison.json";

    /** The folders the two sides write into, so that a Brain compared with itself has two. */
    public static final String CANDIDATE = "candidate";

    public static final String BASELINE = "baseline";

    /**
     * One pair: which triple, under which salt, both Runs, what the pair scored, and the two
     * outcomes it was scored from (null for a Run with no whole, finished log).
     */
    public record Pair(SeedSet.Entry triple, long salt, String candidateRun, String baselineRun,
                       PairScore score, boolean missing, RunLog.Outcome candidateOutcome,
                       RunLog.Outcome baselineOutcome) {

        /**
         * Whether both Runs ended the same way in every part of the Composite outcome -- not that
         * they were the same Run, which the logs' chains cannot say across two Brains' headers.
         */
        public boolean identical() {
            return !missing && candidateOutcome.equals(baselineOutcome);
        }
    }

    /**
     * The within-pair correlation of turns survived over the pairs where both Runs reached an
     * ending, with the number of those pairs: what pairing buys (ADR-0012's open question). Pearson's
     * r; absent when fewer than three pairs qualify or either side's turns do not vary.
     */
    public record Correlation(int pairs, Double r) {
    }

    static Correlation correlation(List<Pair> pairs) {
        List<Pair> reached = pairs.stream().filter(pair -> !pair.missing()).toList();
        int n = reached.size();
        if (n < 3) {
            return new Correlation(n, null);
        }
        double meanA = reached.stream().mapToDouble(p -> p.candidateOutcome().turns()).average().orElse(0);
        double meanB = reached.stream().mapToDouble(p -> p.baselineOutcome().turns()).average().orElse(0);
        double cov = 0;
        double varA = 0;
        double varB = 0;
        for (Pair pair : reached) {
            double a = pair.candidateOutcome().turns() - meanA;
            double b = pair.baselineOutcome().turns() - meanB;
            cov += a * b;
            varA += a * a;
            varB += b * b;
        }
        if (varA == 0 || varB == 0) {
            return new Correlation(n, null);
        }
        // Clamped: rounding can carry a perfect correlation a hair past one, and a file that says
        // 1000001 millionths is claiming something no correlation can be.
        return new Correlation(n, Math.max(-1.0, Math.min(1.0, cov / Math.sqrt(varA * varB))));
    }

    /**
     * What the pairs said.
     *
     * @param pairs  one per triple, in the Seed set's order
     * @param test   the sequential test that ran, or null when no Registration stated its bounds
     * @param result what it concluded, or null with it
     */
    public record Report(String candidate, String baseline, List<Pair> pairs, SequentialTest test,
                         Gsprt.Result result) {

        public Report {
            pairs = List.copyOf(pairs);
        }

        public List<PairScore> scores() {
            return pairs.stream().map(Pair::score).toList();
        }

        public int missing() {
            return (int) pairs.stream().filter(Pair::missing).count();
        }
    }

    private Comparison() {
    }

    /**
     * Pairs the two sides' logs, one per triple in {@code triples}' order under {@code salts}, and
     * runs {@code test} on them when there is one.
     */
    public static Report of(SeedSet triples, List<Long> salts, String tag, Path out,
                            String candidate, String baseline, SequentialTest test) {
        if (salts.size() != triples.entries().size()) {
            throw new IllegalArgumentException("a salt for every triple: " + salts.size() + " for "
                    + triples.entries().size());
        }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < salts.size(); i++) {
            SeedSet.Entry triple = triples.entries().get(i);
            long salt = salts.get(i);
            String mineId = runId(tag, triple, salt, candidate);
            String theirsId = runId(tag, triple, salt, baseline);
            RunLog.Outcome mine = outcome(out.resolve(CANDIDATE).resolve(RunLog.fileName(mineId)));
            RunLog.Outcome theirs = outcome(out.resolve(BASELINE).resolve(RunLog.fileName(theirsId)));
            boolean missing = !PairScore.reached(mine) || !PairScore.reached(theirs);
            pairs.add(new Pair(triple, salt, mineId, theirsId, PairScore.of(mine, theirs), missing,
                    mine, theirs));
        }
        Gsprt.Result result = test == null ? null
                : test.run(pairs.stream().map(Pair::score).toList(),
                        pairs.stream().map(Pair::missing).toList());
        return new Report(candidate, baseline, pairs, test, result);
    }

    private static String runId(String tag, SeedSet.Entry triple, long salt, String brain) {
        return RunLog.runId(tag, triple.heroClass(), triple.challengeFlags(), triple.seedCode(), salt,
                brain);
    }

    /**
     * The outcome a Run's log records, or null when there is no whole, finished log to read.
     *
     * <p>Only a log that reads to its end, ends in a record the writer finished, and holds exactly
     * one header is believed. Anything less is a Run the Rig cannot vouch for, and it is counted as
     * missing rather than scored -- which is how the index counted it too.
     */
    static RunLog.Outcome outcome(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        RunLogReader.Log log = RunLogReader.of(file);
        OverlayLogs.refuse(log, file);
        if (!log.readable() || !log.complete() || log.end() == null
                || log.records().stream().filter(RunLog.Header.class::isInstance).count() != 1) {
            return null;
        }
        return log.end().outcome();
    }

    /**
     * Writes {@code report} as {@code comparison.json} in {@code out}.
     *
     * @param directionCheck whether the Seed set is one that may not accept (ADR-0012: only
     *                       {@code standard} and {@code bosses} can), so an ACCEPT here is a
     *                       direction and not a result
     */
    public static void write(Path out, Report report, String registration, boolean directionCheck) {
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.key("baseline").value(report.baseline());
        json.key("baseline_index_sha256").value(sha256(out.resolve(BASELINE).resolve(RunIndex.RUNS)));
        json.key("candidate").value(report.candidate());
        json.key("candidate_index_sha256").value(sha256(out.resolve(CANDIDATE).resolve(RunIndex.RUNS)));
        // What pairing bought: the within-pair correlation of turns survived over the pairs both
        // Runs of which reached an ending, and how many pairs were the same Run to the last turn.
        Correlation correlation = correlation(report.pairs());
        json.key("correlated_pairs").value(correlation.pairs());
        json.key("direction_check").value(directionCheck);
        json.key("identical_pairs").value((int) report.pairs().stream().filter(Pair::identical).count());
        json.key("missing").value(report.missing());
        json.key("pairs").beginArray();
        for (Pair pair : report.pairs()) {
            json.beginObject();
            json.key("baseline_run").value(pair.baselineRun());
            json.key("candidate_run").value(pair.candidateRun());
            json.key("missing").value(pair.missing());
            json.key("salt").value(RunLog.salt(pair.salt()));
            json.key("score_halves").value(pair.score().halves());
            json.key("seed").value(pair.triple().seed());
            json.endObject();
        }
        json.endArray();
        json.key("registration").value(registration);
        json.key("tested").value(report.result() != null);
        if (correlation.r() != null) {
            json.key("turns_correlation_micros").value(micros(correlation.r()));
        }
        if (report.result() != null) {
            SequentialTest test = report.test();
            Gsprt.Result result = report.result();
            int[] counts = result.counts();
            json.key("burn_in").value(test.burnIn());
            json.key("clamped").value(result.clamped());
            // The counts over the pairs the test consumed, which are the counts the LLR is a
            // function of. Counting every pair played -- including those after the stop -- gave a
            // file whose counts could not reproduce its own LLR.
            json.key("consumed_better").value(counts[2]);
            json.key("consumed_equal").value(counts[1]);
            json.key("consumed_worse").value(counts[0]);
            json.key("llr_micros").value(micros(result.llr()));
            json.key("lower_micros").value(micros(result.lower()));
            json.key("maximum").value(test.maximum());
            json.key("missing_per_mil").value(test.missingPerMil());
            json.key("p0_micros").value(micros(test.p0()));
            json.key("p1_micros").value(micros(test.p1()));
            // Which design produced the verdict: the bounds and the trace mean different things in
            // the two (an LLR against Wald bounds, or a log wealth against log(1/alpha)).
            json.key("statistic").value(test.statistic().name());
            json.key("stopped_at").value(result.pairs());
            json.key("trace_micros").beginArray();
            for (double step : result.trace()) {
                json.value(micros(step));
            }
            json.endArray();
            json.key("upper_micros").value(micros(result.upper()));
            json.key("verdict").value(result.verdict().name());
        }
        json.endObject();
        try {
            Files.writeString(out.resolve(FILE), json.toJson() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the comparison could not be written to " + out, e);
        }
    }

    /**
     * Millionths, refusing a value no long can hold rather than saturating to one silently.
     *
     * <p>The raw LLR grows with the square of a lopsided run's length, and {@code Math.round} of a
     * double past {@code Long.MAX_VALUE} returns {@code Long.MAX_VALUE} without a word.
     */
    static long micros(double value) {
        double scaled = value * 1_000_000;
        if (!(Math.abs(scaled) < 9.0e18)) {
            throw new IllegalArgumentException("a value too large to write in millionths: " + value);
        }
        return Math.round(scaled);
    }

    private static String sha256(Path file) {
        try {
            byte[] bytes = Files.isRegularFile(file) ? Files.readAllBytes(file) : new byte[0];
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
    }
}
