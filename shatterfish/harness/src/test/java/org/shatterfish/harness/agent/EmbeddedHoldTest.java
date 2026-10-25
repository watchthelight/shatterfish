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

import org.shatterfish.api.Decider;
import org.shatterfish.harness.driver.WaitGate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam stories 5.6 and 5.7's Pause, Next Step and speed modes stand on: {@link EmbeddedRun#hold},
 * {@link EmbeddedRun#advance}. A held Run still confirms its wait and asks the Brain, but executes the
 * Decision that lands only against a credit, one per wait; the Decision waits, shown in the snapshot, and
 * a Run that waits is not ended by its own idleness. A credit is spent when its Decision executes, so a
 * stale answer that is asked again spends none. A Run that is let go is the ordinary Run again.
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
            return new RunLog.Decision("call " + calls.get(), new RunLog.Choice(new Action.Search(), 10_000, "only option"),
                    java.util.List.of(), java.util.List.of(), "searching");
        }

        @Override
        public Belief belief() {
            return null;
        }
    }

    private static void idle(EmbeddedHost host) {
        host.play(IDLE_FRAMES / 10);
    }

    @Test
    @DisplayName("a held Run decides the wait in front and waits, unexecuted, however long it idles; the frame budget does not end it")
    void held_run_decides_and_waits() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            assertTrue(run.held());
            EmbeddedAttachTest.playUntil(host, run::withheld);
            assertEquals(EmbeddedRun.State.PLAYING, host.play(IDLE_FRAMES));
            assertEquals(1, run.waitIndex(), "the wait in front was confirmed");
            assertEquals(1, brain.calls.get(), "and the Brain asked once, not again while it waits");
            assertTrue(run.withheld());
            assertNull(run.outcome(), "an idle Run is not a stuck one");
            EmbeddedRun.Snapshot snapshot = run.snapshot();
            assertTrue(snapshot.awaiting(), "the card shows the Decision the next press executes");
            assertEquals("searching", snapshot.decision().policy());
            assertEquals(0, snapshot.history().size(), "nothing was executed or recorded");
            assertEquals(EmbeddedRun.State.PLAYING, snapshot.state(), "waiting for a press is not THINKING");
        }
    }

    @Test
    @DisplayName("advance(n) lets exactly n Decisions execute and the Run waits again")
    void advance_spends_one_credit_per_wait() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            EmbeddedAttachTest.playUntil(host, run::withheld);
            run.advance(1);
            assertEquals(1, run.credits());
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1 && run.withheld());
            idle(host);
            assertEquals(1, run.snapshot().history().size(), "one credit, one executed wait");
            assertEquals(2, run.waitIndex(), "and the next wait decided and waiting");
            assertEquals(0, run.credits());
            assertEquals(2, brain.calls.get());

            run.advance(3);
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 4 && run.withheld());
            idle(host);
            assertEquals(4, run.snapshot().history().size(), "three more credits, three more waits, and no fifth");
            assertEquals(5, run.waitIndex());
            assertEquals(5, brain.calls.get());
        }
    }

    @Test
    @DisplayName("a credit given while the Brain thinks is queued and spent once, when its Decision lands")
    void a_queued_credit_fires_once() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            assertEquals(EmbeddedRun.State.THINKING, run.state());
            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1);
            assertEquals(0, run.credits(), "spent when the Decision executed");
            EmbeddedAttachTest.playUntil(host, run::withheld);
            idle(host);
            assertEquals(1, run.snapshot().history().size(), "exactly once");
        }
    }

    @Test
    @DisplayName("holding clears the credits; letting go makes the Run an ordinary one again, and its waiting Decision executes")
    void hold_clears_and_release_frees() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Searching brain = new Searching();
            EmbeddedRun run = host.attach(brain, null, 5_000);
            run.hold(true);
            run.advance(5);
            run.hold(true);
            assertEquals(0, run.credits(), "a Pause cancels a Run N still owed");
            EmbeddedAttachTest.playUntil(host, run::withheld);
            idle(host);
            assertEquals(0, run.snapshot().history().size());

            run.hold(false);
            assertFalse(run.held());
            assertEquals(0, run.credits(), "a Run that is not held has no credits to count");
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() >= 10 && run.state() == EmbeddedRun.State.PLAYING);
            assertTrue(brain.calls.get() >= 10);
            assertFalse(run.snapshot().history().isEmpty(), "the Decision that waited was executed");
        }
    }

    @Test
    @DisplayName("a stale answer that is dropped and asked again spends no credit; the one fresh answer spends it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_stale_answer_spends_no_credit() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger decisions = new AtomicInteger();
        Decider held = observation -> {
            if (decisions.getAndIncrement() == 0) {
                try {
                    release.await(2, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new Action.Search();
        };
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(held, null, 5_000);
            run.hold(true);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            run.advance(1);
            WaitGate.live().handedOver();
            release.countDown();
            EmbeddedAttachTest.playUntil(host, () -> run.staleAnswers() == 1);
            assertEquals(1, run.credits(), "the dropped answer was not paid for");
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1 && run.withheld());
            idle(host);
            assertEquals(0, run.credits(), "the fresh answer was");
            assertEquals(1, run.snapshot().history().size(), "one credit, one executed wait, not two");
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
