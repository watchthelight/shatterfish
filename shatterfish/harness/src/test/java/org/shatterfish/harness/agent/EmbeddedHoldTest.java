package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam story 5.6's Pause and Next Step stand on: {@link EmbeddedRun#hold}, {@link EmbeddedRun#advance}.
 * A held Run asks for no Input wait without a credit, spends one credit per wait, is not ended by its own
 * idleness, and is the ordinary Run again when it is let go.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class EmbeddedHoldTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;
    /** Well past the Run's frame budget in game time (20,000 sixtieths at a fifth of a second a frame is about 1,700 frames). */
    private static final long IDLE_FRAMES = 3_000;

    private static final class Searching implements Deliberator {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Action decide(Observation observation) {
            calls.incrementAndGet();
            return new Action.Search();
        }

        @Override
        public RunLog.Decision lastDecision() {
            return null;
        }

        @Override
        public Belief belief() {
            return null;
        }
    }

    @Test
    @DisplayName("a held Run confirms no wait however long it idles, and the frame budget does not end it")
    void held_run_asks_for_nothing() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            assertTrue(run.held());
            assertEquals(EmbeddedRun.State.PLAYING, host.play(IDLE_FRAMES));
            assertEquals(0, run.waitIndex(), "no wait was confirmed");
            assertEquals(0, brain.calls.get(), "the Brain was never asked");
            assertNull(run.outcome(), "an idle Run is not a stuck one");
        }
    }

    @Test
    @DisplayName("advance(n) lets exactly n waits through and the Run holds again")
    void advance_spends_one_credit_per_wait() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            run.advance(1);
            assertEquals(1, run.credits());
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() == 1 && run.state() == EmbeddedRun.State.PLAYING);
            host.play(IDLE_FRAMES / 10);
            assertEquals(1, run.waitIndex(), "one credit, one wait");
            assertEquals(0, run.credits());
            assertEquals(1, brain.calls.get());

            run.advance(3);
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() == 4 && run.state() == EmbeddedRun.State.PLAYING);
            host.play(IDLE_FRAMES / 10);
            assertEquals(4, run.waitIndex(), "three more credits, three more waits, and no fourth");
            assertEquals(4, brain.calls.get());
            assertEquals(4, run.snapshot().history().size(), "each of them served and recorded");
        }
    }

    @Test
    @DisplayName("holding clears the credits; letting go makes the Run an ordinary one again")
    void hold_clears_and_release_frees() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            run.advance(5);
            run.hold(true);
            assertEquals(0, run.credits(), "a Pause cancels a Run N still owed");
            host.play(IDLE_FRAMES / 10);
            assertEquals(0, run.waitIndex());

            run.hold(false);
            assertFalse(run.held());
            assertEquals(0, run.credits(), "a Run that is not held has no credits to count");
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() >= 10 && run.state() == EmbeddedRun.State.PLAYING);
            assertTrue(brain.calls.get() >= 10);
        }
    }

    @Test
    @DisplayName("a negative count is refused, and credit given to a Run that is not held is not kept")
    void bad_credit() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Searching(), null, 5_000);
            assertThrows(IllegalArgumentException.class, () -> run.advance(-1));
            run.advance(7);
            run.hold(true);
            assertEquals(0, run.credits(), "advance on a free Run was dropped, not banked");
        }
    }
}
