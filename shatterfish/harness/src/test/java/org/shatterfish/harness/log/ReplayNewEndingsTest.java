package org.shatterfish.harness.log;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two endings story 4.11 added are reproducible like any other (non-negotiable 5): a Run that
 * stalled and a Run the Brain could not decide in are logged verifiable, and a Replay reaches the
 * same `end` record, detail included, with the same chain.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ReplayNewEndingsTest {

    private static final long SEED = 24_012_345L;

    private static final long SALT = 0x5A17_5A17L;

    private static final String COMMIT = "0".repeat(40);

    private static Path play(Path folder, String brain, Decider decider) {
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, decider, 1_000,
                new RunLoop.Logging(folder, COMMIT, new RunLog.Brain(brain, COMMIT, "0".repeat(64)), "", "test"));
        assertTrue(outcome.cause() == RunOutcome.Cause.STALLED || outcome.cause() == RunOutcome.Cause.BRAIN_ERROR,
                outcome.toString());
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0", org.shatterfish.api.HeroClass.WARRIOR, 0,
                SeedSet.code(SEED), SALT, brain)));
    }

    private static void replays(Path file, Path out, String cause) throws Exception {
        RunLog.End end = RunLogReader.of(file).end();
        assertEquals(cause, end.outcome().cause());
        assertTrue(end.verifiable(), "logged verifiable");
        assertTrue(!end.detail().isEmpty(), "with its detail");
        Files.createDirectories(out);
        Replay.Result result = Replay.of(file, out, "test");
        assertTrue(result.reproduced(), result.why());
        assertEquals(result.originalChain(), result.chain(), "the same chain, the end record's detail included");
    }

    @Test
    @DisplayName("a Run that stalls on Actions that take no time replays to the same ending")
    void a_stall_replays(@TempDir Path folder) throws Exception {
        // The Warrior's broken seal comes off its armour and goes back on, and neither takes a turn
        // (Armor.java:190-197; BrokenSeal.java:117-179): a loop of real, logged Actions.
        Decider seal = observation -> {
            for (Action action : observation.actions().actions()) {
                if (action instanceof Action.UseItem use && use.action().equals("DETACH")) {
                    return action;
                }
            }
            for (Action action : observation.actions().actions()) {
                if (action instanceof Action.UseItemOn use && use.action().equals("AFFIX")
                        && observation.inventory().items().get(use.target().index()).slot()
                        == org.shatterfish.api.EquipSlot.ARMOR) {
                    return action;
                }
            }
            return new Action.Search();
        };
        replays(play(folder, "seal", seal), folder.resolve("replay"), "STALLED");
    }

    @Test
    @DisplayName("a Run the Brain could not decide in replays to the same ending, message and all")
    void a_brain_error_replays(@TempDir Path folder) throws Exception {
        int[] waits = {0};
        Decider gives_up = observation -> {
            if (waits[0]++ < 3) {
                return new Action.Search();
            }
            throw new Decider.CannotDecide("no rule for this screen");
        };
        replays(play(folder, "givesup", gives_up), folder.resolve("replay"), "BRAIN_ERROR");
    }
}
