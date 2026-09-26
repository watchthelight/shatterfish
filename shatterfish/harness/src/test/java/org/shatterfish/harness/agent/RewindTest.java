package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Rewindable;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The real deciders rewind completely (story 5.1's fairness review): asked a question, put back, and
 * asked another, a decider answers the second exactly as one never asked the first would, in its
 * Action, its Belief and its Decision. The embedded Run's tests use a stand-in decider; this holds the
 * two the Overlay attaches, the Brain and the random agent.
 */
class RewindTest {

    /** Three first screens of three dungeons: one to have seen before, one to drop, one to answer. */
    private static final List<Observation> SCREENS = new ArrayList<>();

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void screens() {
        for (long seed : new long[] {31_415_926L, 27_182_818L, 16_180_339L}) {
            try (HeadlessDriver driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, 0x5A17_5A17L)) {
                driver.stepToInputWait();
                SCREENS.add(new Observer().observe());
            }
        }
        assertNotEquals(SCREENS.get(1).hash(), SCREENS.get(2).hash(), "the dropped screen is another screen");
    }

    /** Seen, then dropped and rewound, then answered; against seen, then answered. */
    private static void rewindsCompletely(java.util.function.Supplier<? extends Rewindable> fresh) {
        Observation seen = SCREENS.get(0);
        Observation dropped = SCREENS.get(1);
        Observation answered = SCREENS.get(2);

        Rewindable rewound = fresh.get();
        org.shatterfish.api.Decider asked = (org.shatterfish.api.Decider) rewound;
        asked.decide(seen);
        Object mark = rewound.mark();
        asked.decide(dropped);
        rewound.rewind(mark);
        Action rewoundAction = asked.decide(answered);

        org.shatterfish.api.Decider never = (org.shatterfish.api.Decider) fresh.get();
        never.decide(seen);
        Action neverAction = never.decide(answered);

        assertEquals(neverAction, rewoundAction, "the Action");
        if (asked instanceof org.shatterfish.api.Deliberator rewoundBrain
                && never instanceof org.shatterfish.api.Deliberator neverBrain) {
            assertEquals(neverBrain.beliefHash(), rewoundBrain.beliefHash(), "the Belief");
            assertEquals(neverBrain.lastDecision(), rewoundBrain.lastDecision(), "the Decision");
            assertEquals(neverBrain.lastHighlights(), rewoundBrain.lastHighlights(), "the highlights");
        }
    }

    @Test
    @DisplayName("the Brain, put back, answers as a Brain that never saw the dropped screen")
    void the_brain_rewinds() {
        rewindsCompletely(() -> new BrainDecider(EmbeddedDeterminismTest.brain()));
    }

    @Test
    @DisplayName("the random agent, put back, draws as one that never drew for the dropped screen")
    void the_random_agent_rewinds() {
        // Many answers, so a stream one draw ahead would show.
        for (long seed = 1; seed <= 50; seed++) {
            long agentSeed = seed;
            rewindsCompletely(() -> new RandomAgent(agentSeed));
        }
    }
}
