package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readers of one grammar, kept honest by each other (story 3.4).
 *
 * <p>`LogText` was written for story 3.2 as the skeptic's reader: the file's own text, the JDK's
 * digest, no code shared with the writer. `RunLogVerifier` and `Json` are that idea moved into
 * production, where the Rig and the Replay can use it. Two implementations agreeing about every
 * line of a real log is evidence; one implementation agreeing with itself is not — which is the
 * defect this project found in four consecutive stories.
 *
 * <p>`LogText` is the one that may not be changed to suit the other. It predates the production
 * reader and was written against a writer that did not yet exist in its final form, so when the two
 * disagree the question is what the format says, not which is more convenient.
 */
class LogReaderAgreementTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final String COMMIT = "0".repeat(40);

    /** A backslash, which cannot be written next to a `u` in Java source. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    @Test
    @DisplayName("the production reader and the skeptic's reader agree about every line of a real log")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_two_readers_agree(@TempDir Path folder) throws IOException {
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), 120,
                new RunLoop.Logging(folder, COMMIT, new RunLog.Brain("random", COMMIT, "0".repeat(64)),
                        "", "test"));
        Path file = folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SeedSet.code(SEED), SALT, "random")));
        List<String> lines = LogText.lines(file).whole();
        assertTrue(lines.size() > 10, "a real log: " + lines.size());

        for (String line : lines) {
            // The text the chain is taken over: the property every stranger's checker depends on.
            assertEquals(LogText.chained(line), RunLogVerifier.chained(line),
                    "the two readers strip a line differently: " + line);

            // And every member, key by key, raw text against raw text.
            Map<String, String> read = Json.object(line);
            assertEquals(LogText.keys(line).size(), read.size(), "the same members: " + line);
            for (String key : LogText.keys(line)) {
                assertEquals(LogText.value(line, key), read.get(key),
                        "the two readers read " + key + " differently in: " + line);
            }
        }
    }

    /**
     * The lines a hand editor or a different writer produces, on which the two readers must agree.
     *
     * <p>Not one of these can come out of {@code RunLogJson}, and that is the point: agreement
     * about canonical text is agreement about the easy half. The first of them is the one that
     * mattered — a key spelled {@code \u0070rev} decodes to {@code prev}, so a reader that strips
     * by decoded name removes it from the chained text and a reader that strips by written name
     * keeps it. One file, two verdicts, and the chain is supposed to be the thing a stranger can
     * check for themselves.
     */
    private static final List<String> MADE_BY_HAND = List.of(
            "{\"" + BACKSLASH + "u0070rev\":\"" + "0".repeat(64) + "\",\"t\":\"mode\"}",
            "{\"a\":[1}",
            "{\"b\":1,\"a\":2}",
            "{\"a\":1,}",
            "{\"a\":1,\"a\":2}",
            "{\"a\":,\"b\":1}",
            "{\"k\":1,\"machine\":\"free bytes\",\"t\":\"mode\"}");

    @Test
    @DisplayName("the two readers reach the same verdict on lines the writer would never produce")
    void the_two_readers_agree_about_refusing() {
        for (String line : MADE_BY_HAND) {
            boolean production = refuses(() -> RunLogVerifier.chained(line));
            boolean skeptic = refuses(() -> LogText.chained(line));
            assertEquals(production, skeptic, "the two readers disagree about whether this line is"
                    + " one the writer could have written, which is the one thing they may not"
                    + " disagree about: " + line);
            assertTrue(production, "this line is not one the writer could have written: " + line);
        }
    }

    @Test
    @DisplayName("the three copies of the unchained set are the same set")
    void the_exclusion_sets_agree() {
        // Three hand-written copies: the writer's, the production checker's and this reader's. The
        // ArchUnit rule keeps the *production* checker from importing the writer, and it says
        // nothing about a test doing so -- so this is where a fourth name added to one of them
        // stops being a one-line change. Adding a key to the checker's copy alone removes that
        // field from what the chain protects, and until this existed nothing noticed.
        assertEquals(RunLogJson.UNCHAINED, RunLogVerifier.UNCHAINED,
                "the writer and the checker exclude the same keys");
        assertEquals(RunLogJson.UNCHAINED, Set.copyOf(LogText.UNCHAINED),
                "and so does the skeptic's reader");

        // And where each of them is allowed to appear, which is the half a name-only list misses.
        assertEquals(Set.of("prev", "chain", "machine", "started"),
                Set.of(LogText.allowed("header").split(" ")));
        assertEquals(Set.of("prev", "chain", "think_ms"),
                Set.of(LogText.allowed("wait").split(" ")));
        assertEquals(Set.of("prev", "chain"), Set.of(LogText.allowed("end").split(" ")));
    }

    private static boolean refuses(Runnable read) {
        try {
            read.run();
            return false;
        } catch (RuntimeException refused) {
            return true;
        }
    }
}
