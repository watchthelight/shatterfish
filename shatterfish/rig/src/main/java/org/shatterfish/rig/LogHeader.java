package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the Rig reads back out of a Run's log (story 3.3, consolidated in story 3.4).
 *
 * <p>It reads the text, not the writer. {@code RunLogJson} writes and never reads — by rule,
 * because {@code api} has no JSON reader (story 2.1), and by design, because a reader built out of
 * the writer agrees with any writer that agrees with itself, which is the defect that survived four
 * independent reviews in each of three stories.
 *
 * <p><b>It no longer has a reader of its own.</b> Story 3.3 left this class walking the characters
 * itself, and story 3.4 added {@link RunLogReader} in the harness — which would have made three
 * implementations of one grammar, counting the harness's test reader. Three is not more honest than
 * two; it is two places for a drift to hide. So the parsing here is gone and this is a Rig-shaped
 * view over the harness's readers: {@link RunLogReader} for the records, {@link RunLogVerifier} for
 * the chain, {@link Json} for the one raw field this needs before a record exists. The arrangement
 * that keeps them honest is unchanged — one production reader, one deliberately independent test
 * reader in the harness, and a test that they agree.
 *
 * <p>It still reads as little as it can, and it still reports rather than throws: the Rig reads
 * five hundred of these on worker threads, and one corrupt byte must not end the invocation.
 */
public final class LogHeader {

    private LogHeader() {
    }

    /**
     * What a log says about itself: whether it is complete, whether its header claimed the oracle,
     * and the chain of its last whole line.
     *
     * @param present    whether a log file is there at all
     * @param lines      the whole lines it holds; a partial last line is not one
     * @param oracle     the header's own oracle flag
     * @param complete   whether the last whole line is an {@code end} record
     * @param chain      the chain of the last whole line, or empty when there is none. It is what
     *                   the file states, not what its bytes give: recomputing it is the Replay's
     *                   work, which {@code --verify} does and the run index does not
     * @param runId      the id the header's own fields give, or empty when it cannot be read
     * @param waits      how many Input waits the Run served, counted as wait records. It is the
     *                   quantity a throughput number is about, and it is not "lines minus two":
     *                   a Run that met Prompts writes a record for each of those too, and the
     *                   first draft of this counted them as waits
     * @param cause      how the Run ended, as its own end record says, or empty
     * @param unreadable what is wrong with the file, or empty when nothing is. A log this reader
     *                   cannot make sense of is reported, never thrown: one corrupt byte in one
     *                   Run of five hundred must not stop the Rig reading the other four hundred
     *                   and ninety-nine, and the reader that checks chains says the same
     */
    public record Read(boolean present, int lines, boolean oracle, boolean complete, String chain,
                       String runId, int waits, String cause, String unreadable) {

        /** A Run with no log at all: the child died before it could write its header. */
        public static final Read MISSING = new Read(false, 0, false, false, "", "", 0, "", "");

        /** Whether this log can be believed about anything, the oracle flag included. */
        public boolean readable() {
            return unreadable.isEmpty();
        }
    }

    /**
     * Reads {@code file}. A log this reader cannot make sense of comes back marked unreadable
     * rather than as an exception: the Rig reads five hundred of these on worker threads, and one
     * bad byte must not end the invocation.
     */
    public static Read of(Path file) {
        if (!Files.isRegularFile(file)) {
            return Read.MISSING;
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be read", e);
        }
        List<String> whole = whole(text);
        // The oracle flag, off the text, before anything is asked to be a record. FR-11 keys on
        // this and it may not need a well-formed header to fire: a hand-made line claiming the
        // oracle and nothing else parses as an object and says what it says, and a reader that
        // required all eighteen header fields before it would believe that line would answer
        // "unreadable" -- which the Rig counts as an incomplete Run rather than as a refusal.
        // That is exactly how this guard was weakened when the Rig's own reader was folded into
        // the harness's, and it is why the flag is read separately from the records.
        boolean claimed;
        try {
            claimed = claimsTheOracle(whole, text);
        } catch (RuntimeException cannot) {
            // A line holding `oracle` twice, or holding it as something that is not true or false.
            // The claim cannot be read, so the log cannot be vouched for -- which the Rig counts
            // as incomplete, never as fair.
            return unreadable(false, "the log's own oracle flag could not be read: "
                    + cannot.getMessage());
        }
        RunLogReader.Log log;
        try {
            log = RunLogReader.of(text);
        } catch (RuntimeException unreadable) {
            return unreadable(claimed, unreadable.getMessage());
        }
        if (whole.isEmpty()) {
            return headless(claimed, log.partial());
        }
        if (!log.readable()) {
            return unreadable(claimed, log.unreadable());
        }
        try {
            return read(claimed, log, whole);
        } catch (RuntimeException unreadable) {
            return unreadable(claimed, unreadable.getMessage());
        }
    }

    /**
     * Whether any line in this file says in its own text that the Run saw what a player could not.
     *
     * <p>Every line, not the first, and the partial one too. Two logs concatenated -- a fair Run
     * followed by an oracle one -- used to read as one fair Run, and a killed writer can leave a
     * header whose trailing line feed never reached the disk.
     */
    private static boolean claimsTheOracle(List<String> whole, String text) {
        boolean claimed = false;
        List<String> every = new ArrayList<>(whole);
        String partial = text.substring(text.lastIndexOf('\n') + 1);
        if (!partial.isEmpty()) {
            every.add(partial);
        }
        for (String line : every) {
            Map<String, String> held;
            try {
                held = Json.object(line);
            } catch (RuntimeException notAnObject) {
                // A line that is not an object claims nothing. Whether the file as a whole is
                // readable is the next question, and it is answered separately.
                continue;
            }
            String oracle = held.get("oracle");
            claimed |= oracle != null && Json.bool(oracle);
        }
        return claimed;
    }

    /**
     * A log with no whole line in it.
     *
     * <p>A killed writer can leave a header whose trailing line feed never reached the disk, and
     * that header may claim the oracle — so the partial text is asked, and a Run whose claim cannot
     * be read is marked unreadable rather than silently counted as fair.
     */
    private static Read headless(boolean claimed, String partial) {
        if (partial.isEmpty()) {
            return new Read(true, 0, false, false, "", "", 0, "", "");
        }
        if (claimed) {
            return new Read(true, 0, true, false, "", "", 0, "", "");
        }
        return unreadable(false, "the log holds no whole line, so nothing in it can be believed");
    }

    private static Read unreadable(boolean claimed, String why) {
        return new Read(true, 0, claimed, false, "", "", 0, "", why == null ? "unreadable" : why);
    }

    private static Read read(boolean claimed, RunLogReader.Log log, List<String> whole) {
        List<RunLog> records = log.records();
        if (records.isEmpty() || !(records.get(0) instanceof RunLog.Header)) {
            throw new IllegalStateException("a Run log begins with a header, and this begins "
                    + whole.get(0));
        }
        // Every header, not the first. Two logs concatenated -- a fair Run followed by an oracle
        // one -- used to read as one fair Run, while `LogText` in the harness refuses a second
        // header outright. The guard FR-11 keys on may not be weaker than a reader that already
        // exists.
        boolean oracle = false;
        int waits = 0;
        int headers = 0;
        for (RunLog record : records) {
            if (record instanceof RunLog.Header header) {
                headers++;
                oracle |= header.oracle();
            } else if (record instanceof RunLog.Wait) {
                waits++;
            }
        }
        oracle |= claimed;
        if (headers != 1) {
            throw new IllegalStateException("a Run log holds one header and this holds " + headers
                    + "; two Runs in one file are not one Run");
        }
        RunLog.End end = log.end();
        // The end record's own cause, so the index says DEATH or TURN_CAP rather than the word
        // "ended" for every Run alike -- which is the difference between a tally and a number.
        String cause = end == null ? "" : end.outcome().cause();
        return new Read(true, whole.size(), oracle, end != null, chain(whole.get(whole.size() - 1)),
                ((RunLog.Header) records.get(0)).runId(), waits, cause, "");
    }

    /** The chain the last whole line states, which is the value the run index publishes. */
    private static String chain(String line) {
        Map<String, String> held = Json.object(line);
        String chain = held.get("chain");
        return chain == null ? "" : Json.string(chain);
    }

    /** A log's whole lines. A partial last line is not one. */
    private static List<String> whole(String text) {
        List<String> lines = new ArrayList<>();
        int from = 0;
        while (true) {
            int feed = text.indexOf('\n', from);
            if (feed < 0) {
                return List.copyOf(lines);
            }
            String line = text.substring(from, feed);
            // A log fetched over HTTP or checked out with autocrlf carries a carriage return the
            // writer never wrote. That is a rule the format states, and it is not a reason to be
            // unable to read whether the Run claimed the oracle.
            lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            from = feed + 1;
        }
    }

    // ---------------------------------------------------- the Rig's own files, which are not logs

    /**
     * The text of {@code key}'s value in one canonical object, or null when it holds no such key.
     *
     * <p>The run index and the summary are written in the log's own canonical shape but are not
     * logs — they hold no records — so they are read as objects. {@link Json} refuses a key written
     * twice, which is how a hand-made header once defeated the oracle guard.
     */
    public static String value(String object, String key) {
        return Json.object(object).get(key);
    }

    /** A string value, unquoted and unescaped, or null when the object does not hold the key. */
    public static String string(String object, String key) {
        String raw = value(object, key);
        return raw == null ? null : Json.string(raw);
    }
}
