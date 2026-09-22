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
final class LogText {

    /** The keys the chain does not cover, written out here rather than imported (see above). */
    static final List<String> UNCHAINED = List.of("prev", "chain", "think_ms", "machine", "started");

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

    /**
     * Where the chain first disagrees with the file, counting lines from one, or zero when every
     * chain in the file is the one the bytes give.
     *
     * <p>A partial last line is not checked: it is not a record, and a reader that repaired one
     * would be inventing the half of it that never reached the disk.
     */
    static int firstBrokenLine(Lines lines) {
        String previous = "";
        for (int i = 0; i < lines.whole().size(); i++) {
            String line = lines.whole().get(i);
            String stated = string(line, "chain");
            if (stated == null) {
                return i + 1;
            }
            String said = string(line, "prev");
            if (!previous.isEmpty() && !previous.equals(said)) {
                return i + 1;
            }
            if (previous.isEmpty() && said != null) {
                return i + 1;
            }
            String computed = hex(sha256(concat(unhex(previous), utf8(chained(line)))));
            if (!computed.equals(stated)) {
                return i + 1;
            }
            previous = stated;
        }
        return 0;
    }

    /** The same line with the unchained keys removed: what the chain is taken over. */
    static String chained(String line) {
        StringBuilder out = new StringBuilder("{");
        for (Member member : members(line)) {
            if (UNCHAINED.contains(member.key)) {
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
    static List<String> keys(String line) {
        return members(line).stream().map(m -> m.key).toList();
    }

    /** The text of a key's value, quotes and braces included, or null when the line lacks the key. */
    static String value(String line, String key) {
        for (Member member : members(line)) {
            if (member.key.equals(key)) {
                return line.substring(member.valueFrom, member.to);
            }
        }
        return null;
    }

    /** A string value with its quotes taken off, or null when the line lacks the key. */
    static String string(String line, String key) {
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
            if (line.charAt(keyEnd) != ':') {
                throw new IllegalArgumentException("a key is followed by a colon, at " + keyEnd + ": " + line);
            }
            int valueFrom = keyEnd + 1;
            int to = endOfValue(line, valueFrom);
            members.add(new Member(key, from, valueFrom, to));
            at = to;
            if (at < line.length() - 1) {
                if (line.charAt(at) != ',') {
                    throw new IllegalArgumentException("members are comma separated, at " + at + ": " + line);
                }
                at++;
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
