package org.shatterfish.harness.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Run log's chain, recomputed from the file's own bytes (story 3.4).
 *
 * <p><b>It does not use the writer.</b> Not as a matter of taste: a chain check that re-rendered
 * each record through {@code RunLogJson} and compared would agree with any writer that agreed with
 * itself, including a wrong one, and the one thing a hash chain exists to prove is that a file can
 * be checked without the tool that produced it. So this takes each line's bytes as they are, strips
 * the unchained keys <em>by name</em>, and hashes with the JDK's own {@code MessageDigest}. An
 * ArchUnit rule holds that this class depends on no renderer, because a comment saying so would not
 * survive a refactor.
 *
 * <p>This is story 3.2's checker, which lived in the harness's test sources, moved into production
 * where the Rig and the Replay can both use it. The test reader stays where it is: two
 * implementations of one grammar, kept honest by a test that they agree, is the arrangement worth
 * having — and the test one was written first, against a writer that did not yet exist in its final
 * form, which is why it is the one that is not allowed to change to suit this one.
 *
 * <p>The rules it implements are published in full on the methodology page, so a stranger can write
 * a third.
 */
public final class RunLogVerifier {

    /**
     * The keys the chain does not cover: the envelope's own two, the time a decider took, and the
     * two header fields that say where and when. Written out here rather than imported, because
     * importing them from the writer is the dependency this class exists not to have.
     */
    public static final Set<String> UNCHAINED = Set.of("prev", "chain", "think_ms", "machine", "started");

    /** The envelope, which every record carries and no record's chain covers. */
    private static final Set<String> ENVELOPE = Set.of("prev", "chain");

    /**
     * Where each unchained key is allowed to be, by record kind.
     *
     * <p>This is the half the first draft left out, and it was not a detail. {@code think_ms} is a
     * wait's field and {@code machine} and {@code started} are the header's, but a stripper that
     * worked by name alone took them off <em>any</em> line -- so every record that is not a wait had
     * room for a key called {@code think_ms} holding anything at all, removed before hashing and
     * therefore invisible to the chain. Three names of free space on every line of every log, in the
     * one function whose whole purpose is that nothing in the file is free.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "header", Set.of("prev", "chain", "machine", "started"),
            "wait", Set.of("prev", "chain", "think_ms"));

    /** What a verification found. */
    public record Verified(int lines, boolean complete, int brokenLine, String chain, String why) {

        /** Whether every chain in the file is the one the bytes give. */
        public boolean ok() {
            return brokenLine == 0 && why.isEmpty();
        }

        /** A file with nothing in it to check, which is not the same answer as one that verified. */
        public static Verified nothing() {
            return new Verified(0, false, 0, "", "the log holds no whole line, so there is nothing to verify");
        }
    }

    private RunLogVerifier() {
    }

    public static Verified of(Path file) {
        try {
            return of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be read", e);
        }
    }

    /**
     * Recomputes every chain in {@code text}.
     *
     * <p>A partial last line is not checked: it is not a record, and repairing one would be
     * inventing the half that never reached the disk. It is reported instead, because a Run whose
     * log ends mid-line was killed and the Rig counts that rather than losing it.
     */
    public static Verified of(String text) {
        List<String> lines = new ArrayList<>();
        int from = 0;
        while (true) {
            int feed = text.indexOf('\n', from);
            if (feed < 0) {
                break;
            }
            String line = text.substring(from, feed);
            lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            from = feed + 1;
        }
        boolean complete = from >= text.length();
        if (lines.isEmpty()) {
            return Verified.nothing();
        }
        String previous = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            try {
                Map<String, String> held = Json.object(line);
                String stated = held.get("chain");
                if (stated == null || !Json.string(stated).matches("[0-9a-f]{64}")) {
                    return new Verified(lines.size(), complete, i + 1, "",
                            "line " + (i + 1) + " carries no chain this reader can use");
                }
                String said = held.containsKey("prev") ? Json.string(held.get("prev")) : null;
                if (previous.isEmpty() ? said != null : !previous.equals(said)) {
                    return new Verified(lines.size(), complete, i + 1, "",
                            "line " + (i + 1) + " does not follow the line before it");
                }
                String computed = hex(sha256(concat(unhex(previous), utf8(chained(line)))));
                if (!computed.equals(Json.string(stated))) {
                    return new Verified(lines.size(), complete, i + 1, "",
                            "line " + (i + 1) + " states a chain the bytes of the file do not give");
                }
                previous = Json.string(stated);
            } catch (RuntimeException unreadable) {
                return new Verified(lines.size(), complete, i + 1, "",
                        "line " + (i + 1) + " is not a record: " + unreadable.getMessage());
            }
        }
        return new Verified(lines.size(), complete, 0, previous, "");
    }

    /**
     * The same line with the unchained keys removed: the text the chain was taken over.
     *
     * <p><b>By the key's own literal text, and only where the schema puts it.</b> Each excluded key
     * belongs to exactly one kind of record, so this reads the line's {@code t} first and then
     * strips only what that kind is allowed to carry. A {@code think_ms} on anything but a wait, or
     * a {@code machine} on anything but a header, is not a field with nothing to say — it is a
     * member the writer never wrote, and this refuses the line rather than quietly hashing the text
     * with it taken out.
     *
     * <p>The key is matched as it is written, not as it decodes. A key spelled with a unicode
     * escape decodes to a name this would strip and that a checker written from the published rules
     * would keep, which is one file with two verdicts. The guard that actually prevents that is in
     * {@link Json#object}, which refuses an escaped key outright and runs first — this method's
     * mutation survived the battery for exactly that reason. Matching the literal here is a second
     * lock on the same door, kept because this method is public and a caller that has not been
     * through {@code Json} would otherwise strip a key the writer never wrote.
     */
    public static String chained(String line) {
        StringBuilder out = new StringBuilder("{");
        int at = 1;
        if (line.length() < 2 || line.charAt(0) != '{' || line.charAt(line.length() - 1) != '}') {
            throw new IllegalArgumentException("a log line is one JSON object: " + line);
        }
        Set<String> strip = ALLOWED.getOrDefault(kind(line), ENVELOPE);
        while (at < line.length() - 1) {
            int from = at;
            if (line.charAt(at) != '"') {
                throw new IllegalArgumentException("a key is quoted, at " + at + ": " + line);
            }
            int keyEnd = Json.endOfString(line, at);
            String key = line.substring(at + 1, keyEnd - 1);
            if (line.charAt(keyEnd) != ':') {
                throw new IllegalArgumentException("a key is followed by a colon, at " + keyEnd);
            }
            int to = Json.endOfValue(line, keyEnd + 1);
            if (UNCHAINED.contains(key) && !strip.contains(key)) {
                throw new IllegalArgumentException("the key " + key + " is not one this kind of"
                        + " record carries, and the chain does not cover it: " + line);
            }
            if (!strip.contains(key)) {
                if (out.length() > 1) {
                    out.append(',');
                }
                out.append(line, from, to);
            }
            at = to;
            if (at < line.length() - 1) {
                if (line.charAt(at) != ',') {
                    throw new IllegalArgumentException("members are comma separated, at " + at);
                }
                at++;
            }
        }
        return out.append('}').toString();
    }

    /**
     * The kind of record this line is, as its own {@code t} says.
     *
     * <p>Read through {@link Json}, which refuses a line the writer would not have written. A line
     * with no {@code t} is not a record and carries no unchained key but the envelope, so it is
     * given the envelope and left to fail the chain comparison on its own merits.
     */
    private static String kind(String line) {
        String stated = Json.object(line).get("t");
        return stated == null ? "" : Json.string(stated);
    }

    // --------------------------------------------------------------------- the JDK's own digest

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
