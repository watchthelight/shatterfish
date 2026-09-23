package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * What an invocation of the Rig leaves beside the Run logs (story 3.3, ADR-0011): {@code runs.jsonl},
 * one line per Run, and {@code summary.json}.
 *
 * <p><b>Every Run the Rig starts gets a line, before it starts.</b> That is the point of the file
 * and the reason it is written the way it is. ADR-0012 scores an incomplete Run's pair as a tie — a
 * Run that crashed is a measurement that failed, and it counts. A Run that is *lost*, with no log
 * and no line, is not counted at all: it silently leaves the set, and whatever is left is a biased
 * sample of the set that was supposed to have been measured. So a Run is written down when it is
 * dispatched and its line is amended when it ends, and the two counts a reader compares are how
 * many were started and how many finished.
 *
 * <p>It is also the only thing outside a log that carries a log's final chain. A chain proves a
 * file is internally consistent; it does not prove the file was not written from scratch, and every
 * prefix of a valid log is a valid log. A chain becomes evidence when it is recorded somewhere its
 * author does not control, and for a development invocation this index is the first such place —
 * the Registration (story 3.5) is the one that matters for a published number.
 */
public final class RunIndex {

    /** One line per Run, the way a Seed set is one line per triple. */
    public static final String RUNS = "runs.jsonl";

    public static final String SUMMARY = "summary.json";

    /** How a Run ended, as the index says it. */
    public enum State {

        /** Dispatched, and nothing has been heard back yet. */
        STARTED,

        /** The child played the Run to an ending and its log says so. */
        FINISHED,

        /**
         * The child crashed, was killed at its deadline, or left a log with no end record. The Run
         * is counted and its log is kept; ADR-0012 scores its pair as a tie.
         */
        INCOMPLETE
    }

    /**
     * One Run, as the index knows it.
     *
     * @param runId the id the log is named for
     * @param log   the log's path, relative to the invocation's folder
     * @param chain the final chain, or empty while the Run is unfinished
     * @param why   what went wrong, for an incomplete Run; empty otherwise
     */
    public record Entry(String runId, String log, State state, String chain, long seed,
                        String heroClass, int challenges, long salt, String cause, long millis,
                        String why) {

        public Entry {
            if (runId == null || log == null || state == null || chain == null || cause == null || why == null) {
                throw new IllegalArgumentException("an index entry states every field it has");
            }
        }

        /** The same Run, now that it has ended. */
        public Entry ended(State ended, String chain, String cause, long millis, String why) {
            return new Entry(runId, log, ended, chain, seed, heroClass, challenges, salt, cause, millis, why);
        }

        String line() {
            JsonWriter out = new JsonWriter();
            out.beginObject();
            out.key("runId").value(runId);
            out.key("log").value(log);
            out.key("state").value(state.name());
            out.key("chain").value(chain);
            out.key("seed").value(seed);
            out.key("class").value(heroClass);
            out.key("challenges").value(challenges);
            out.key("salt").value(org.shatterfish.api.RunLog.salt(salt));
            out.key("cause").value(cause);
            out.key("ms").value(millis);
            out.key("why").value(why);
            out.endObject();
            return out.toJson();
        }
    }

    private final Path folder;
    private final List<Entry> entries = new ArrayList<>();

    public RunIndex(Path folder) {
        this.folder = folder;
    }

    /** Writes a Run down before it is dispatched, so a Run that never comes back is still counted. */
    public synchronized void started(Entry entry) {
        entries.add(entry);
        write();
    }

    /** Amends the line of a Run that has ended. The line is never created here, only changed. */
    public synchronized void ended(String runId, State state, String chain, String cause, long millis,
                                   String why) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).runId().equals(runId)) {
                entries.set(i, entries.get(i).ended(state, chain, cause, millis, why));
                write();
                return;
            }
        }
        throw new IllegalStateException("no Run named " + runId + " was ever started, so nothing of"
                + " it can have ended; the index is written before a Run is dispatched");
    }

    /** Every Run this invocation started, in the order it started them. */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** How many of them ended in the state named. */
    public synchronized long count(State state) {
        return entries.stream().filter(e -> e.state() == state).count();
    }

    private void write() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) {
            text.append(entry.line()).append('\n');
        }
        write(folder.resolve(RUNS), text.toString());
    }

    /**
     * Writes the summary: what was run, how it went, and how fast. The throughput is measured and
     * not assumed, which is the PRD's own rule about this number.
     */
    public synchronized void summary(String brain, String set, int parallel, long millis, long waits) {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("brain").value(brain);
        out.key("seedSet").value(set);
        out.key("processes").value(parallel);
        out.key("runsStarted").value(entries.size());
        out.key("runsFinished").value(count(State.FINISHED));
        out.key("runsIncomplete").value(count(State.INCOMPLETE));
        out.key("waits").value(waits);
        out.key("ms").value(millis);
        // Thousandths of a Run per second and of a wait per second: a rate is a fraction, and
        // nothing published by this project writes a float (ADR-0011).
        out.key("runsPerSecondThousandths").value(rate(entries.size(), millis));
        out.key("waitsPerSecondThousandths").value(rate(waits, millis));
        out.endObject();
        write(folder.resolve(SUMMARY), out.toJson() + "\n");
    }

    static long rate(long count, long millis) {
        return millis <= 0 ? 0 : Math.round(count * 1_000_000.0 / millis);
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("the Rig's index could not be written to " + file, e);
        }
    }
}
