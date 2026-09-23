package org.shatterfish.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Run log read the way a stranger would read one (story 3.2): as text, with the JDK's own
 * SHA-256, sharing nothing with the code that wrote it.
 *
 * <p>That is the whole point of this class. Three stories running, the defect that survived four
 * independent reviews was a test that built its expectation with the same predicate the reader
 * used, so a wrong rule agreed with itself and every test passed. A chain check that re-rendered
 * each record through {@code RunLogJson} and compared would be exactly that: it would agree with
 * any writer that agreed with itself, and the one thing a hash chain exists to prove is that the
 * file can be checked without the tool that produced it.
 *
 * <p>So this reads lines, finds the members of each line by walking its characters, strips the
 * keys the chain does not cover <em>by their names</em>, and hashes the rest with
 * {@code MessageDigest}. It is the skeptic's script, in the test sources, and the methodology page
 * publishes the same rules for anyone who would rather write their own.
 */
public final class LogText {

    /** The keys the chain does not cover, written out here rather than imported (see above). */
    static final List<String> UNCHAINED = List.of("prev", "chain", "think_ms", "machine", "started");

    /**
     * Which of those a record of each kind actually carries.
     *
     * <p>The page states the exclusion per kind, and it has to: a name struck from every record
     * alike leaves the records that do not own it with a member the chain does not cover, which is
     * free space in a file whose whole point is that it has none. Written here as its own map, from
     * the page, rather than taken from the production reader -- this class exists to follow the
     * published rules by a different route and agree, and agreement is only evidence when the two
     * routes are separate.
     */
    static String allowed(String kind) {
        return switch (kind) {
            case "header" -> "prev chain machine started";
            case "wait" -> "prev chain think_ms";
            default -> "prev chain";
        };
    }

    private LogText() {
    }

    /** The whole lines of a log: a trailing partial line is not one, and is reported separately. */
    record Lines(List<String> whole, String partial) {

        boolean complete() {
            return partial.isEmpty();
        }
    }

    /** Splits a log's bytes into whole lines and whatever a kill left behind after the last one. */
    static Lines lines(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> whole = new ArrayList<>();
        int from = 0;
        while (true) {
            int feed = text.indexOf('\n', from);
            if (feed < 0) {
                return new Lines(List.copyOf(whole), text.substring(from));
            }
            whole.add(text.substring(from, feed));
            from = feed + 1;
        }
    }

    static Lines lines(Path file) throws IOException {
        return lines(Files.readAllBytes(file));
    }

    /** What {@link #firstBrokenLine} answers when the file holds no whole line at all. */
    static final int NOTHING_THERE = -1;

    /**
     * Where the chain first disagrees with the file, counting lines from one; zero when every chain
     * in the file is the one the bytes give, and {@link #NOTHING_THERE} when there is no line to
     * check.
     *
     * <p>Empty is not the same answer as fine. A zero-length file -- which is exactly what a writer
     * that died between creating the file and writing the header leaves -- would otherwise report
     * as a complete, fully verified log with nothing wrong in it.
     *
     * <p>It reports and never throws. A malformed line is a broken line, named by its number, not
     * an exception out of the middle of a twenty-thousand-line file: the criterion is that
     * verification "fails naming the first record that disagrees", and a stack trace with a
     * character offset does not do that -- nor should one hostile log stop the Rig from checking
     * the rest.
     *
     * <p>A partial last line is not checked: it is not a record, and a reader that repaired one
     * would be inventing the half of it that never reached the disk.
     */
    static int firstBrokenLine(Lines lines) {
        if (lines.whole().isEmpty()) {
            return NOTHING_THERE;
        }
        String previous = "";
        for (int i = 0; i < lines.whole().size(); i++) {
            String line = lines.whole().get(i);
            try {
                String stated = string(line, "chain");
                if (stated == null || !stated.matches("[0-9a-f]{64}")) {
                    return i + 1;
                }
                String said = string(line, "prev");
                if (previous.isEmpty() ? said != null : !previous.equals(said)) {
                    return i + 1;
                }
                if (!hex(sha256(concat(unhex(previous), utf8(chained(line))))).equals(stated)) {
                    return i + 1;
                }
                previous = stated;
            } catch (RuntimeException malformed) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Whether a log is a whole Run's: it begins with the one header and ends with an end record.
     * A chain that verifies says nothing about this -- every prefix of a valid log is itself a
     * valid log -- so the Rig needs a second question, and this is it.
     */
    static boolean whole(Lines lines) {
        if (!lines.complete() || lines.whole().size() < 2 || firstBrokenLine(lines) != 0) {
            return false;
        }
        for (int i = 1; i < lines.whole().size(); i++) {
            if ("header".equals(string(lines.whole().get(i), "t"))) {
                return false;
            }
        }
        return "header".equals(string(lines.whole().get(0), "t"))
                && "end".equals(string(lines.whole().get(lines.whole().size() - 1), "t"));
    }

    /** The same line with the unchained keys removed: what the chain is taken over. */
    public static String chained(String line) {
        StringBuilder out = new StringBuilder("{");
        String kind = string(line, "t");
        List<String> strip = List.of(allowed(kind == null ? "" : kind).split(" "));
        for (Member member : members(line)) {
            if (UNCHAINED.contains(member.key) && !strip.contains(member.key)) {
                throw new IllegalArgumentException("a " + kind + " record does not carry "
                        + member.key + ", so nothing excuses it from the chain: " + line);
            }
            if (strip.contains(member.key)) {
                continue;
            }
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(line, member.from, member.to);
        }
        return out.append('}').toString();
    }

    /** The keys a line holds, in the order it holds them. */
    public static List<String> keys(String line) {
        return members(line).stream().map(m -> m.key).toList();
    }

    /** The text of a key's value, quotes and braces included, or null when the line lacks the key. */
    public static String value(String line, String key) {
        for (Member member : members(line)) {
            if (member.key.equals(key)) {
                return line.substring(member.valueFrom, member.to);
            }
        }
        return null;
    }

    /** A string value with its quotes taken off, or null when the line lacks the key. */
    public static String string(String line, String key) {
        String raw = value(line, key);
        if (raw == null) {
            return null;
        }
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
            throw new IllegalArgumentException("the value of " + key + " is not a string: " + raw);
        }
        return raw.substring(1, raw.length() - 1);
    }

    private record Member(String key, int from, int valueFrom, int to) {
    }

    private static List<Member> members(String line) {
        if (line.length() < 2 || line.charAt(0) != '{' || line.charAt(line.length() - 1) != '}') {
            throw new IllegalArgumentException("a log line is one JSON object: " + line);
        }
        List<Member> members = new ArrayList<>();
        int at = 1;
        while (at < line.length() - 1) {
            int from = at;
            if (line.charAt(at) != '"') {
                throw new IllegalArgumentException("a key is quoted, at " + at + ": " + line);
            }
            int keyEnd = endOfString(line, at);
            String key = line.substring(at + 1, keyEnd - 1);
            // The writer escapes nothing in a key, so a key holding an escape is a key it did not
            // write. It matters more than it looks: this reader strips the unchained keys by the
            // text they are written in, and a reader that decoded first would see `prev` as
            // `prev` and strip it. One of the two would then be hashing a line the other was not.
            // Found by reading the published rules rather than the other reader -- which is the
            // arrangement this class is for.
            if (key.indexOf((char) 92) >= 0) {
                throw new IllegalArgumentException("a key is written plainly and this one is not, at "
                        + at + ": " + line);
            }
            if (line.charAt(keyEnd) != ':') {
                throw new IllegalArgumentException("a key is followed by a colon, at " + keyEnd + ": " + line);
            }
            int valueFrom = keyEnd + 1;
            int to = endOfValue(line, valueFrom);
            for (Member seen : members) {
                if (seen.key.equals(key)) {
                    throw new IllegalArgumentException("the key " + key + " is written twice, and a"
                            + " reader that took the last one would read a different Run from the"
                            + " one the chain covers: " + line);
                }
            }
            if (!members.isEmpty() && key.compareTo(members.get(members.size() - 1).key) <= 0) {
                // The writer sorts its keys, so a line that is not sorted is a line it did not
                // write -- and a reader that accepted one would let a hand-made record chain to
                // its own text and read as intact.
                throw new IllegalArgumentException("keys are written in order and " + key
                        + " is not after " + members.get(members.size() - 1).key + ": " + line);
            }
            members.add(new Member(key, from, valueFrom, to));
            at = to;
            if (at < line.length() - 1) {
                if (line.charAt(at) != ',') {
                    throw new IllegalArgumentException("members are comma separated, at " + at + ": " + line);
                }
                at++;
                if (at >= line.length() - 1) {
                    throw new IllegalArgumentException("a comma is followed by a member, at " + at
                            + ": " + line);
                }
            }
        }
        return members;
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

    /**
     * Where the value beginning at {@code at} ends.
     *
     * <p>What opened a bracket is remembered, not merely that one did. Counting depth made
     * {@code {"a":[1}} a value in this reader and in the production one alike -- two implementations
     * agreeing because they had read the same rule the same wrong way, which is the failure mode
     * that two implementations are supposed to make unlikely rather than impossible.
     */
    private static int endOfValue(String line, int at) {
        StringBuilder opened = new StringBuilder();
        int i = at;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                i = endOfString(line, i);
                if (opened.isEmpty()) {
                    return i;
                }
                continue;
            }
            if (c == '{' || c == '[') {
                opened.append(c);
            } else if (c == '}' || c == ']') {
                if (opened.isEmpty()) {
                    // The end of whatever holds this value, so the value is whatever came before
                    // it -- and it may not be nothing.
                    if (i == at) {
                        throw new IllegalArgumentException("a value is not empty, at " + at + ": " + line);
                    }
                    return i;
                }
                char was = opened.charAt(opened.length() - 1);
                if (c == '}' ? was != '{' : was != '[') {
                    throw new IllegalArgumentException("a " + c + " closes a " + was + ", at " + i
                            + ": " + line);
                }
                opened.setLength(opened.length() - 1);
                if (opened.isEmpty()) {
                    return i + 1;
                }
            } else if (c == ',' && opened.isEmpty()) {
                if (i == at) {
                    throw new IllegalArgumentException("a value is not empty, at " + at + ": " + line);
                }
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("a value is not closed, from " + at + ": " + line);
    }

    // ------------------------------------------------------------------ the JDK's own primitives

    static byte[] sha256(byte[] message) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(message);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] concat(byte[] first, byte[] second) {
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    static byte[] unhex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
