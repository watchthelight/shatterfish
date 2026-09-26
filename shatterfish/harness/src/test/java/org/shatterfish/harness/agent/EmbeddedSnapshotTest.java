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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EmbeddedRun#snapshot()} (story 5.3, FR-38): what the render thread reads to draw the Mode
 * strip and the Decision card -- the last served wait's Decision, turn and floor, and the Run's live
 * state, which is {@link EmbeddedRun.State#THINKING} exactly while the next Decision is pending, with
 * the previous one still shown.
 */
class EmbeddedSnapshotTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    /** A Deliberator whose Decision names the call it was made on, so a test can tell which one shows. */
    private static final class Counting implements Deliberator {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch release;
        final int blockOnCall;
        volatile RunLog.Decision decision;

        Counting(CountDownLatch release, int blockOnCall) {
            this.release = release;
            this.blockOnCall = blockOnCall;
        }

        @Override
        public Action decide(Observation observation) {
            int n = calls.incrementAndGet();
            decision = new RunLog.Decision("call " + n, new RunLog.Choice(new Action.Search(), 10_000, "only option"),
                    List.of(), List.of(), "counting");
            if (n == blockOnCall) {
                try {
                    release.await(2, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new Action.Search();
        }

        @Override
        public RunLog.Decision lastDecision() {
            return decision;
        }

        @Override
        public Belief belief() {
            return null;
        }
    }

    @Test
    @DisplayName("before the first wait is served, the snapshot has no Decision, turn 0 and floor 0")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void nothing_served_yet() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Counting(new CountDownLatch(1), -1), null, 5_000);
            EmbeddedRun.Snapshot snapshot = run.snapshot();
            assertNull(snapshot.decision());
            assertEquals(0, snapshot.turn());
            assertEquals(0, snapshot.floor());
            assertEquals(EmbeddedRun.State.PLAYING, snapshot.state());
        }
    }

    @Test
    @DisplayName("a served wait's Decision, turn and floor reach the snapshot; PLAYING once it is served")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_served_wait() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Counting brain = new Counting(new CountDownLatch(1), -1);
            EmbeddedRun run = host.attach(brain, null, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            EmbeddedRun.Snapshot snapshot = run.snapshot();
            assertEquals("call 1", snapshot.decision().goal());
            assertEquals(1, snapshot.floor());
            assertEquals(RunLoop.turns(), snapshot.turn(), "the turn the wait was confirmed at");
            assertEquals(EmbeddedRun.State.PLAYING, snapshot.state());

            // A second wait, after the Brain's Search has spent a turn: the snapshot's turn moves with
            // the Run's own, rather than staying at whatever the first wait happened to read.
            EmbeddedAttachTest.playUntilWaits(host, 2);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            EmbeddedRun.Snapshot second = run.snapshot();
            assertEquals("call 2", second.decision().goal());
            assertTrue(second.turn() > snapshot.turn(), "a turn passed between the two waits: "
                    + snapshot.turn() + " then " + second.turn());
            assertEquals(RunLoop.turns(), second.turn());
        }
    }

    @Test
    @DisplayName("while the next Decision is pending the snapshot shows THINKING with the previous Decision still on it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void thinking_keeps_the_previous_decision() {
        CountDownLatch release = new CountDownLatch(1);
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Counting brain = new Counting(release, 2);
            EmbeddedRun run = host.attach(brain, null, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            assertEquals("call 1", run.snapshot().decision().goal());

            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.THINKING);
            EmbeddedRun.Snapshot thinking = run.snapshot();
            assertEquals(EmbeddedRun.State.THINKING, thinking.state());
            assertEquals("call 1", thinking.decision().goal(), "wait 2's answer has not landed yet");

            release.countDown();
            EmbeddedAttachTest.playUntilWaits(host, 2);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            EmbeddedRun.Snapshot served = run.snapshot();
            assertEquals(EmbeddedRun.State.PLAYING, served.state());
            assertEquals("call 2", served.decision().goal());
        }
    }

    @Test
    @DisplayName("snapshot() is a port: it refuses a thread that is not the UI-role thread (ADR-0013)")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void snapshot_is_thread_confined() throws InterruptedException {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Counting(new CountDownLatch(1), -1), null, 5_000);
            Throwable[] caught = new Throwable[1];
            Thread stranger = new Thread(() -> {
                try {
                    run.snapshot();
                } catch (Throwable t) {
                    caught[0] = t;
                }
            }, "a-stranger-thread");
            stranger.start();
            stranger.join();
            assertTrue(caught[0] instanceof IllegalStateException, String.valueOf(caught[0]));
            assertTrue(caught[0].getMessage().contains("EmbeddedRun.snapshot()"), caught[0].getMessage());
            // The UI-role thread (this test's) still works: a stranger's refused call changed nothing.
            assertEquals(0, run.snapshot().turn());
        }
    }
}
