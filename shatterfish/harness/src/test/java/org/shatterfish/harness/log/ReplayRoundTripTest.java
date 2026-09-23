package org.shatterfish.harness.log;

import org.shatterfish.harness.LogText;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Run, replayed from its own log (story 3.4, FR-24).
 *
 * <p>This is the story's whole claim in one test: play a Run, replay it from what it wrote, and get
 * the same chain. A chain covers everything about a Run except how long the decider took and which
 * machine it ran on, so the two chains agreeing means every Observation, every Action, every section
 * hash, the turn counts and the ending were reproduced.
 */
class ReplayRoundTripTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final int CAP = 150;

    private static final String COMMIT = "0".repeat(40);

    private static RunLoop.Logging logging(Path folder) {
        return new RunLoop.Logging(folder, COMMIT,
                new RunLog.Brain("random", COMMIT, "0".repeat(64)), "", "test");
    }

    private static Path log(Path folder) {
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SeedSet.code(SEED), SALT, "random")));
    }

    /** A Run, played and logged. */
    private static RunOutcome play(Path folder, int cap) {
        return new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), cap,
                logging(folder));
    }

    @Test
    @DisplayName("a Run replayed from its own log reaches the same chain, wait for wait")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void a_run_replays_to_the_same_chain(@TempDir Path first, @TempDir Path again) {
        RunOutcome outcome = play(first, CAP);

        Replay.Result result = Replay.of(log(first), again, "test");

        assertTrue(result.ok(), result.why());
        assertTrue(result.waits() > 10, "the Run served waits: " + result.waits());
        assertEquals(result.waits(), result.verified(),
                "every wait's Observation was the one the log recorded");
        assertEquals(result.originalChain(), result.chain(),
                "the Replay's own chain is the log's, so nothing about the Run differed");
        assertTrue(result.chain().matches("[0-9a-f]{64}"), result.chain());

        // And the Replay is a Run: it ended the way the original did, through the same loop.
        RunLogReader.Log replayed = RunLogReader.of(log(again));
        assertEquals(outcome.cause().name(), replayed.end().outcome().cause(),
                "the Replay ended as the Run did");
        assertEquals(outcome.depth(), replayed.end().outcome().depth());
    }

    @Test
    @DisplayName("the Replay's log is the original's, byte for byte, except where the chain says it may differ")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_two_logs_differ_only_where_they_may(@TempDir Path first, @TempDir Path again) throws IOException {
        play(first, CAP);
        Replay.of(log(first), again, "elsewhere");

        List<String> original = lines(log(first));
        List<String> replay = lines(log(again));

        assertEquals(original.size(), replay.size(), "the same records, in the same number");
        for (int i = 0; i < original.size(); i++) {
            // Named, not derived. The first draft of this compared the two lines by putting both
            // through `RunLogVerifier.chained` -- the function this test exists to hold -- so a
            // mutant that stripped every key from every line passed, and the chain covered nothing
            // while every assertion here stayed green. What a reproduction has to get right is a
            // list, and a list is what this states.
            Set<String> differing = differingKeys(original.get(i), replay.get(i));
            Set<String> allowed = i == 0 ? Set.of("machine", "started", "chain")
                    : Set.of("think_ms", "prev", "chain");
            assertTrue(allowed.containsAll(differing), "line " + (i + 1) + " differs in "
                    + differing + ", and only " + allowed + " may differ between a Run and its"
                    + " reproduction");
        }
        // And again through the reader that shares no code with the one under test. Two routes to
        // the same answer is the arrangement this project keeps for exactly this reason.
        for (int i = 0; i < original.size(); i++) {
            assertEquals(LogText.chained(original.get(i)), LogText.chained(replay.get(i)),
                    "line " + (i + 1) + ", read by the independent reader, differs in something"
                            + " the chain covers");
        }
        // And the header did differ where it is allowed to: this Replay said it ran elsewhere.
        assertNotEquals(original.get(0), replay.get(0),
                "the two headers are not identical -- the machine and the hour are not the same");
    }

    /**
     * Which top-level keys two lines disagree about, by name.
     *
     * <p>Written here out of the raw text rather than taken from any reader, because a test that
     * asks the code under test which fields differ is a test that agrees with whatever that code
     * currently thinks.
     */
    private static Set<String> differingKeys(String left, String right) {
        Set<String> differing = new TreeSet<>();
        for (String key : LogText.keys(left)) {
            if (!LogText.value(left, key).equals(LogText.value(right, key))) {
                differing.add(key);
            }
        }
        for (String key : LogText.keys(right)) {
            if (!LogText.keys(left).contains(key)) {
                differing.add(key);
            }
        }
        return differing;
    }

    private static List<String> lines(Path file) throws IOException {
        String text = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        return List.of(text.strip().split("\n"));
    }
}
