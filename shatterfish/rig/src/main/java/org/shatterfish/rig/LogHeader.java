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
     */
    public record Read(boolean present, int lines, boolean oracle, boolean complete, String chain,
                       String runId, int waits) {

        /** A Run with no log at all: the child died before it could write its header. */
        public static final Read MISSING = new Read(false, 0, false, false, "", "", 0);
    }

    /** Reads {@code file}, treating anything it cannot parse as a log that is not complete. */
    public static Read of(Path file) {
        if (!Files.isRegularFile(file)) {
            return Read.MISSING;
        }
        List<String> lines = wholeLines(file);
        if (lines.isEmpty()) {
            // A file with no whole line is a Run that was killed between creating its log and
            // writing its header. It is present and incomplete, which is a different thing from
            // absent, and the index says which.
            return new Read(true, 0, false, false, "", "", 0);
        }
        String first = lines.get(0);
        if (!HEADER.equals(string(first, KIND))) {
            throw new IllegalStateException(file + " does not begin with a header, so it is not a Run"
                    + " log this Rig wrote: " + first);
        }
        String last = lines.get(lines.size() - 1);
        int waits = 0;
        for (String line : lines) {
            if (WAIT.equals(string(line, KIND))) {
                waits++;
            }
        }
        return new Read(true, lines.size(), "true".equals(value(first, "oracle")),
                END.equals(string(last, KIND)), orEmpty(string(last, "chain")), runId(first), waits);
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

    private static List<String> wholeLines(Path file) {
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
                return List.copyOf(whole);
            }
            whole.add(text.substring(from, feed));
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
                return object.substring(keyEnd + 1, to);
            }
            at = to;
            if (at < object.length() - 1) {
                if (object.charAt(at) != ',') {
                    throw new IllegalArgumentException("members are comma separated, at " + at);
                }
                at++;
            }
        }
        return null;
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
