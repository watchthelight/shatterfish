package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two endings story 4.11 adds (story 4.11): a decider that cannot decide ends its Run as a Brain
 * error, with its message in the log; any other exception from a decider is not that and
 * propagates; and a Run that goes a hundred waits without a turn passing is stopped as stalled.
 */
class BrainErrorTest {

    private static final long SEED = 4321L;

    private static final long SALT = 0x5A17_5A17L;

    @Test
    @DisplayName("a decider that cannot decide ends the Run as a Brain error, and the log carries its message")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_brain_error_is_logged(@TempDir Path folder) {
        Decider refuses = observation -> {
            throw new Decider.CannotDecide("no rule for this Prompt");
        };
        RunLog.Brain who = new RunLog.Brain("refuses", "0".repeat(40), "0".repeat(64));
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, refuses, 40,
                new RunLoop.Logging(folder, "0".repeat(40), who, "", "test"));

        assertEquals(RunOutcome.Cause.BRAIN_ERROR, outcome.cause(), outcome.toString());
        assertTrue(outcome.detail().contains("no rule for this Prompt"), outcome.detail());
        assertEquals(0, outcome.waits(), "the error came at the first wait, which was never served");
        assertFalse(outcome.ordinary(), "a Brain error is not how a Run is meant to end");

        Path file = folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0", org.shatterfish.api.HeroClass.WARRIOR,
                0, SeedSet.code(SEED), SALT, "refuses")));
        assertTrue(RunLogVerifier.of(file).ok(), "the log with the detail in it chains");
        RunLog.End end = RunLogReader.of(file).end();
        assertEquals("BRAIN_ERROR", end.outcome().cause());
        assertTrue(end.detail().startsWith("at wait ") && end.detail().contains("no rule for this Prompt"), end.detail());
        assertTrue(end.verifiable(), "a Brain error replays (story 4.11)");
    }

    @Test
    @DisplayName("any other exception from a decider propagates: a Replay that diverged is not a Brain error")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void other_exceptions_propagate() {
        Decider broken = observation -> {
            throw new IllegalStateException("a bug, or a Replay that diverged");
        };
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, broken, 40));
        assertEquals("a bug, or a Replay that diverged", thrown.getMessage());
    }

    @Test
    @DisplayName("a hundred waits without a turn passing end the Run as stalled, before the turn cap could")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void no_time_passing_is_a_stall() {
        // Each wait either opens a message and hands over an Action the screen does not offer, which is
        // refused and takes no time, or dismisses the message, which takes none either and resets the
        // run of refusals. Nothing ever passes a turn.
        Decider stalls = observation -> {
            if (observation.prompt().kind() == PromptKind.MESSAGE) {
                return new Action.DismissPrompt();
            }
            GameScene.show(new WndMessage("A message nobody asked for."));
            return new Action.Step(observation.hero().cell());
        };
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, stalls, 1_000);

        assertEquals(RunOutcome.Cause.STALLED, outcome.cause(), outcome.toString());
        assertTrue(outcome.detail().startsWith(RunLoop.WAITS_WITHOUT_A_TURN + " waits without a turn passing"),
                outcome.detail());
        assertFalse(outcome.ordinary());
    }
}
