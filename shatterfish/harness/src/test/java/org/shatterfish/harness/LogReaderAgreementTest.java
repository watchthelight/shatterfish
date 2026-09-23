package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
}
