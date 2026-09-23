package org.shatterfish.rig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the Rig reads back out of a Run's log (story 3.3): whether the Run claimed the oracle, and
 * the chain it ended on.
 *
 * <p>It reads the text. {@code RunLogJson} writes and never reads — by rule, because {@code api}
 * has no JSON reader (story 2.1), and by design, because the thing that checks a log must not be
 * the thing that wrote it. A reader built out of the writer agrees with any writer that agrees with
 * itself, which is the defect that survived four independent reviews in each of three stories. So
 * this walks the line's characters, the way story 3.2's own checker does, and the two agreeing is
 * evidence rather than tautology.
 *
 * <p>It reads as little as it can. The Rig needs two facts out of a log it did not write: the
 * oracle flag, which FR-11 makes the enforcement point, and the last chain, which is what a Results
 * page publishes. Verifying the chain is the Replay's work (story 3.4), and a reader that did it
 * here would be a second implementation of it.
 */
public final class LogHeader {

    /** The key the log's first line writes the kind under, and the kinds this reader cares about. */
    private static final String KIND = "t";

    private static final String HEADER = "header";

    private static final String END = "end";

    private static final String WAIT = "wait";

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
     * @param chain      the chain of the last whole line, or empty when there is none
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
        Split split;
        try {
            split = split(file);
        } catch (RuntimeException unreadable) {
            return unreadable(unreadable.getMessage());
        }
        if (split.whole().isEmpty()) {
            // No whole line. A killed writer can leave a header whose trailing line feed never
            // reached the disk, and that header may claim the oracle -- so the partial text is
            // asked, and a Run whose claim cannot be read is marked unreadable rather than
            // silently counted as fair.
            if (split.partial().isEmpty()) {
                return new Read(true, 0, false, false, "", "", 0, "", "");
            }
            try {
                boolean oracle = "true".equals(value(split.partial(), "oracle"));
                return oracle
                        ? new Read(true, 0, true, false, "", "", 0, "", "")
                        : unreadable("the log holds no whole line, so nothing in it can be believed");
            } catch (RuntimeException cannot) {
                return unreadable("the log holds no whole line and its partial one is not readable");
            }
        }
        try {
            return read(split.whole());
        } catch (RuntimeException unreadable) {
            return unreadable(unreadable.getMessage());
        }
    }

    private static Read unreadable(String why) {
        return new Read(true, 0, false, false, "", "", 0, "", why == null ? "unreadable" : why);
    }

    private static Read read(List<String> lines) {
        String first = lines.get(0);
        if (!HEADER.equals(string(first, KIND))) {
            throw new IllegalStateException("a Run log begins with a header, and this begins " + first);
        }
        // Every header, not the first. Two logs concatenated -- a fair Run followed by an oracle
        // one -- used to read as one fair Run, while `LogText.whole` in the harness refuses a
        // second header outright. The guard FR-11 keys on may not be weaker than a reader that
        // already exists.
        boolean oracle = false;
        int waits = 0;
        int headers = 0;
        for (String line : lines) {
            String kind = string(line, KIND);
            if (HEADER.equals(kind)) {
                headers++;
                oracle |= "true".equals(value(line, "oracle"));
            } else if (WAIT.equals(kind)) {
                waits++;
            }
        }
        if (headers != 1) {
            throw new IllegalStateException("a Run log holds one header and this holds " + headers
                    + "; two Runs in one file are not one Run");
        }
        String last = lines.get(lines.size() - 1);
        boolean complete = END.equals(string(last, KIND));
        // The end record's own cause, so the index says DEATH or TURN_CAP rather than the word
        // "ended" for every Run alike -- which is the difference between a tally and a number.
        String outcome = complete ? value(last, "outcome") : null;
        String cause = outcome == null ? "" : orEmpty(string(outcome, "cause"));
        return new Read(true, lines.size(), oracle, complete,
                orEmpty(string(last, "chain")), runId(first), waits, cause, "");
    }

    /** The id the header's own fields name, which has to be the name of the file it is in. */
    private static String runId(String header) {
        return orEmpty(string(header, "tag")) + "-" + orEmpty(string(header, "class")) + "-"
                + orEmpty(value(header, "challenges")) + "-" + orEmpty(string(header, "seedcode"))
                + "-" + orEmpty(string(header, "salt")) + "-" + brainName(header);
    }

    private static String brainName(String header) {
        String brain = value(header, "brain");
        return brain == null ? "" : orEmpty(string(brain, "name"));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** A log's whole lines and whatever a kill left after the last one. */
    private record Split(List<String> whole, String partial) {
    }

    private static Split split(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be read", e);
        }
        List<String> whole = new ArrayList<>();
        int from = 0;
        while (true) {
            int feed = text.indexOf('\n', from);
            if (feed < 0) {
                return new Split(List.copyOf(whole), text.substring(from));
            }
            String line = text.substring(from, feed);
            // A log fetched over HTTP or checked out with autocrlf carries a carriage return the
            // writer never wrote. That is a rule the format states, and it is not a reason to be
            // unable to read whether the Run claimed the oracle.
            whole.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            from = feed + 1;
        }
    }

    // ------------------------------------------------- a reader of one shape, walked as characters

    /** The text of {@code key}'s value, or null when the object does not hold it. */
    static String value(String object, String key) {
        if (object.length() < 2 || object.charAt(0) != '{' || object.charAt(object.length() - 1) != '}') {
            throw new IllegalArgumentException("a log line is one JSON object: " + object);
        }
        int at = 1;
        String found = null;
        while (at < object.length() - 1) {
            if (object.charAt(at) != '"') {
                throw new IllegalArgumentException("a key is quoted, at " + at + ": " + object);
            }
            int keyEnd = endOfString(object, at);
            String held = object.substring(at + 1, keyEnd - 1);
            if (object.charAt(keyEnd) != ':') {
                throw new IllegalArgumentException("a key is followed by a colon, at " + keyEnd);
            }
            int to = endOfValue(object, keyEnd + 1);
            if (held.equals(key)) {
                if (found != null) {
                    // `LogText` refuses a repeated key for a reason it states: every JSON reader
                    // downstream takes the last one, so a line carrying `oracle` twice means one
                    // thing to this guard and another to everything else. Returning the first was
                    // how a hand-made header defeated the refusal.
                    throw new IllegalArgumentException("the key " + key + " is written twice, and a"
                            + " reader that took the other one would read a different Run");
                }
                found = object.substring(keyEnd + 1, to);
            }
            at = to;
            if (at < object.length() - 1) {
                if (object.charAt(at) != ',') {
                    throw new IllegalArgumentException("members are comma separated, at " + at);
                }
                at++;
            }
        }
        return found;
    }

    /** A string value with its quotes taken off, or null when the object does not hold the key. */
    static String string(String object, String key) {
        String raw = value(object, key);
        if (raw == null) {
            return null;
        }
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
            throw new IllegalArgumentException("the value of " + key + " is not a string: " + raw);
        }
        return raw.substring(1, raw.length() - 1);
    }

    private static int endOfString(String line, int at) {
        for (int i = at + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("a string is not closed, from " + at + ": " + line);
    }

    private static int endOfValue(String line, int at) {
        int depth = 0;
        int i = at;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                i = endOfString(line, i);
                if (depth == 0) {
                    return i;
                }
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (depth == 0) {
                    return i;
                }
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            } else if (c == ',' && depth == 0) {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("a value is not closed, from " + at + ": " + line);
    }
}
