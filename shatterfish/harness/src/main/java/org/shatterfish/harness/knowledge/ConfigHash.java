package org.shatterfish.harness.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The configuration hash a Run log's header gives a Brain that reads the Codex (story 4.5; the
 * bestiary's lever 0): SHA-256 of what the Brain says it is ({@code Brain.configuration}: its Policies,
 * the version of its memory, its weights), the seed of its stream, and the digest of the knowledge it
 * was built on ({@link CodexKnowledge#digest}). A change to any of them is a different Brain. It lives
 * here so the Rig and the Overlay compute it one way; neither can reach the other.
 */
public final class ConfigHash {

    private ConfigHash() {
    }

    /** The hash of a Brain configured as {@code configuration}, seeded {@code seed}, built on {@code knowledge}. */
    public static String of(String configuration, long seed, String knowledge) {
        if (knowledge == null || !knowledge.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("a Brain built on the Codex is configured by the knowledge it read"
                    + " too, as a SHA-256 digest: " + knowledge);
        }
        return sha256(configuration + ";seed=" + Long.toHexString(seed) + ";knowledge=" + knowledge);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("every Java platform has SHA-256", unavailable);
        }
    }
}
