package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Decider;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An agent that throws ends its Run as a Brain error, with the error in the detail, rather than
 * stalling it or losing it to a crash (story 4.11).
 */
class BrainErrorTest {

    @Test
    @DisplayName("an agent that cannot decide ends the Run as a Brain error, saying what it said")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_throwing_agent_is_a_brain_error() {
        Decider refuses = observation -> {
            throw new IllegalStateException("no rule for this Prompt");
        };
        RunOutcome outcome = new RunLoop().play(4321L, HeroClass.WARRIOR, 0x5A17_5A17L, refuses, 40);

        assertEquals(RunOutcome.Cause.BRAIN_ERROR, outcome.cause(), outcome.toString());
        assertTrue(outcome.detail().contains("no rule for this Prompt"), outcome.detail());
        assertEquals(0, outcome.waits(), "the error came at the first wait, which was never served");
        assertFalse(outcome.ordinary(), "a Brain error is not how a Run is meant to end");
    }
}
