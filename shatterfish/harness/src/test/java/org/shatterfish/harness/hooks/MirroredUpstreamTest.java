package org.shatterfish.harness.hooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upstream method bodies Shatterfish reproduces rather than calls, held to the tag by a digest.
 *
 * <p>A hook is an edit to an upstream file, and {@code HooksLedgerTest} counts those. This is the
 * other thing the ledger does not see: a private upstream method whose body the harness has copied
 * out because it cannot be called — the scene that starts a game, and the scene between two floors.
 * Nothing marks those files, so an upgrade that rewrites them leaves the build green and the
 * harness playing a slightly different game from the one upstream ships. The review of story 1.14
 * asked for this, and it is the check the upgrade procedure was missing.
 *
 * <p>When this fails, the upstream body changed. Read it, decide what the change means for the
 * copy in Shatterfish, change the copy, and only then update the digest here — never the other way
 * round, which would be the test agreeing with itself.
 */
class MirroredUpstreamTest {

    /** Where a mirrored body lives upstream and what its source is expected to be. */
    private record Mirror(String path, String signature, String digest, String copiedBy) {
    }

    private static final List<Mirror> MIRRORS = List.of(
            new Mirror("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/InterlevelScene.java",
                    "private void descend() throws IOException {",
                    "", "org.shatterfish.harness.agent.RunLoop.crossFloor and HeadlessDriver.newGame"),
            new Mirror("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/InterlevelScene.java",
                    "private void ascend() throws IOException {",
                    "", "org.shatterfish.harness.agent.RunLoop.crossFloor"),
            new Mirror("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/InterlevelScene.java",
                    "private void fall() throws IOException {",
                    "", "org.shatterfish.harness.agent.RunLoop.crossFloor"));

    /**
     * The digest of each body at `v4.0.0`. A value here is a promise that the copy in Shatterfish
     * was read against that body: each of these three was, for story 1.14, and the copy carries the
     * guard the game carries on the city's quest area and fails where the game would rather than
     * arriving somewhere the game would not.
     */
    private static final Map<String, String> AT_THE_TAG = new LinkedHashMap<>();

    static {
        AT_THE_TAG.put("descend", "7f46035c23458334c48eabedacfeefefeb6876827f0d0ef2c22c04ae0a0cfcc3");
        AT_THE_TAG.put("ascend", "740476f007ee4654970f1405143153d440050832b50f0909878c8689b29512d1");
        AT_THE_TAG.put("fall", "1084c453ce83469d5829e9e2a8a0ea4f61687b2bdeb673ee9944a35bf84febb5");
    }

    @Test
    @DisplayName("every upstream body Shatterfish reproduces is the body it was copied from")
    void the_copies_are_of_what_is_there() {
        List<String> drifted = new ArrayList<>();
        for (Mirror mirror : MIRRORS) {
            String name = mirror.signature().split("\\s+")[2].replaceAll("\\(.*", "");
            String body = bodyOf(Path.of(root(), mirror.path()), mirror.signature());
            assertTrue(body.length() > 80, name + " was not found in " + mirror.path());
            String digest = sha256(body);
            String expected = AT_THE_TAG.get(name);
            if (!digest.equals(expected)) {
                drifted.add(name + " in " + mirror.path() + "\n      copied by " + mirror.copiedBy()
                        + "\n      expected " + expected + "\n      found    " + digest);
            }
        }
        assertEquals(List.of(), drifted, "an upstream body Shatterfish reproduces has changed. Read it,"
                + " decide what the change means for the copy, change the copy, then put the new digest"
                + " in AT_THE_TAG — in that order:\n  " + String.join("\n  ", drifted));
    }

    /** The source of one method, from its signature line to the brace that closes it. */
    private static String bodyOf(Path file, String signature) {
        String source = read(file);
        int start = source.indexOf(signature);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    private static String root() {
        // The working directory is a module's, so the walk goes up to the repository — and past a
        // module that has a settings file of its own, which is why the marker is the upstream tree
        // this test reads rather than the build file.
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("core/src/main/java/com/shatteredpixel"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("no repository root above " + Path.of("").toAbsolutePath());
        }
        return here.toString();
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
