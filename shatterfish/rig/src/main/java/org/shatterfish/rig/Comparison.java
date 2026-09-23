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
import java.util.ArrayList;
import java.util.List;

/**
 * Two Brains' Runs of one Seed set, paired and tested (story 3.6, ADR-0012).
 *
 * <p><b>A pair is two Runs of one triple under one salt.</b> The Runner draws the salt once per
 * triple and hands it to both children, which is what makes the pair a common-random-numbers
 * comparison: whatever the dungeon does at a given moment it does to both Brains. A pair is found
 * here by the run id both Runs' own headers name, and a Run whose file is absent, unreadable or
 * without an ending is scored a tie and counted as missing.
 *
 * <p><b>Pairs are consumed in the Seed set's order.</b> Never in the order the Runs finished:
 * completion order depends on which Runs were slow, which depends on how they went, and a sequential
 * test fed in that order would be stopping on information it was not supposed to have yet.
 */
public final class Comparison {

    /** The file beside the two sides' folders. */
    public static final String FILE = "comparison.json";

    /** The folders the two sides write into, so that a Brain compared with itself has two. */
    public static final String CANDIDATE = "candidate";

    public static final String BASELINE = "baseline";

    /**
     * What the pairs said.
     *
     * @param scores  one per triple, in the Seed set's order
     * @param missing how many of them had a Run missing, and were therefore scored a tie
     * @param result  the sequential test, or null when there was no Registration to state its bounds
     */
    public record Report(String candidate, String baseline, List<PairScore> scores, int missing,
                         Gsprt.Result result) {

        public Report {
            scores = List.copyOf(scores);
        }
    }

    private Comparison() {
    }

    /**
     * Pairs the two sides' logs, one per triple in {@code triples}' order under {@code salts}, and
     * runs {@code test} on them when there is one.
     */
    public static Report of(SeedSet triples, List<Long> salts, String tag, Path out,
                            String candidate, String baseline, Gsprt test) {
        if (salts.size() != triples.entries().size()) {
            throw new IllegalArgumentException("a salt for every triple: " + salts.size() + " for "
                    + triples.entries().size());
        }
        List<PairScore> scores = new ArrayList<>();
        int missing = 0;
        for (int i = 0; i < salts.size(); i++) {
            SeedSet.Entry triple = triples.entries().get(i);
            RunLog.Outcome mine = outcome(out.resolve(CANDIDATE), tag, triple, salts.get(i), candidate);
            RunLog.Outcome theirs = outcome(out.resolve(BASELINE), tag, triple, salts.get(i), baseline);
            if (mine == null || theirs == null) {
                missing++;
            }
            scores.add(PairScore.of(mine, theirs));
        }
        return new Report(candidate, baseline, scores, missing, test == null ? null : test.run(scores));
    }

    /** The outcome one side's Run of one triple reached, or null when there is none to read. */
    static RunLog.Outcome outcome(Path folder, String tag, SeedSet.Entry triple, long salt, String brain) {
        Path file = folder.resolve(RunLog.fileName(RunLog.runId(tag, triple.heroClass(),
                triple.challengeFlags(), triple.seedCode(), salt, brain)));
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            RunLogReader.Log log = RunLogReader.of(file);
            return log.readable() && log.end() != null ? log.end().outcome() : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Writes {@code report} as {@code comparison.json} in {@code out}.
     *
     * <p>No floats: the LLR and its trace are written in millionths, as every number this project
     * writes down is a whole one (ADR-0011).
     */
    public static void write(Path out, Report report, String registration) {
        JsonWriter json = new JsonWriter();
        json.beginObject();
        json.key("baseline").value(report.baseline());
        json.key("candidate").value(report.candidate());
        int[] counts = new int[3];
        for (PairScore score : report.scores()) {
            counts[score.halves()]++;
        }
        json.key("better").value(counts[2]);
        json.key("equal").value(counts[1]);
        json.key("missing").value(report.missing());
        json.key("pairs").value(report.scores().size());
        json.key("registration").value(registration);
        json.key("tested").value(report.result() != null);
        if (report.result() != null) {
            Gsprt.Result result = report.result();
            json.key("clamped").value(result.clamped());
            json.key("llr_micros").value(micros(result.llr()));
            json.key("lower_micros").value(micros(result.lower()));
            json.key("stopped_at").value(result.pairs());
            json.key("trace_micros").beginArray();
            for (double step : result.trace()) {
                json.value(micros(step));
            }
            json.endArray();
            json.key("upper_micros").value(micros(result.upper()));
            json.key("verdict").value(result.verdict().name());
        }
        json.key("worse").value(counts[0]);
        json.endObject();
        try {
            Files.writeString(out.resolve(FILE), json.toJson() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the comparison could not be written to " + out, e);
        }
    }

    private static long micros(double value) {
        return Math.round(value * 1_000_000);
    }
}
