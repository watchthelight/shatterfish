package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The render thread is the UI-role thread, the Brain runs on its own worker, and the game's frames
 * go on while the Brain thinks (story 5.1, NFR-4, ADR-0013).
 *
 * <p>A Brain that will not answer until the test lets it is the worst case for a frame rate: if the
 * render thread waited on it anywhere, frames would stop. So the test holds the Brain, keeps giving
 * the Run frames, and holds that every frame returned at once and that the scene went on being
 * stepped, then lets the Brain go and holds that its answer is played at a later frame.
 */
class EmbeddedThreadingTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    /** Frames the test keeps giving while the Brain is held. */
    private static final int HELD_FRAMES = 300;

    /**
     * The longest a single frame of the Run may take while its Brain is held. The Run does no more
     * than ask whether the answer is in; a frame of the game at 60 fps is 16 ms, and this bound is
     * generous for a busy CI machine while still orders of magnitude below a held Brain.
     */
    private static final long FRAME_BOUND_MS = 50;

    @Test
    @DisplayName("the Brain decides on its own worker, and the render thread's frames never wait for it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_frames_go_on_while_the_brain_thinks() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger decisions = new AtomicInteger();
        Thread[] decidedOn = new Thread[1];
        Decider held = new Decider() {
            @Override
            public Action decide(Observation observation) {
                decidedOn[0] = Thread.currentThread();
                if (decisions.getAndIncrement() == 0) {
                    try {
                        // The first decision is held until the test has watched the frames go on.
                        if (!release.await(2, TimeUnit.MINUTES)) {
                            throw new IllegalStateException("never released");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return new Action.Search();
            }
        };
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.stepWhileThinking = true;
            EmbeddedRun run = host.attach(held, null, 5_000);
            Thread render = Thread.currentThread();
            assertEquals(render, run.uiThread(), "the thread that attached is the UI-role thread");

            // Up to the first wait, whose decision is held.
            while (run.state() != EmbeddedRun.State.THINKING) {
                host.frame();
            }
            long framesAtTheWait = host.driver.frames();
            long slowest = 0;
            for (int frame = 0; frame < HELD_FRAMES; frame++) {
                long before = System.nanoTime();
                EmbeddedRun.State state = host.frame();
                long took = (System.nanoTime() - before) / 1_000_000L;
                slowest = Math.max(slowest, took);
                assertEquals(EmbeddedRun.State.THINKING, state, "the Brain is still held at frame " + frame);
            }
            assertTrue(slowest < FRAME_BOUND_MS, "no frame waited for the Brain; the slowest took " + slowest + " ms");
            assertEquals(framesAtTheWait + HELD_FRAMES, host.driver.frames(),
                    "the game went on drawing a frame each time while the Brain thought");
            assertEquals(HELD_FRAMES, host.framesWhileThinking);
            assertEquals(1, run.waitIndex(), "no second wait is confirmed while the first is being decided");

            release.countDown();
            // The answer arrives on the worker; the next frames take it and play it.
            long k = run.waitIndex();
            for (int frame = 0; frame < 100_000 && run.waitIndex() == k; frame++) {
                host.frame();
            }
            assertTrue(run.waitIndex() > k, "the held decision was played and the Run went on");
            assertNotNull(decidedOn[0]);
            assertNotEquals(render, decidedOn[0], "the Brain never runs on the render thread");
            assertEquals("shatterfish-brain", decidedOn[0].getName());
        }
    }

    @Test
    @DisplayName("a frame on any thread but the UI-role thread is refused by name")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void only_the_ui_role_thread_gives_frames() throws InterruptedException {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(observation -> new Action.Search(), null, 5_000);
            Throwable[] failure = new Throwable[1];
            Thread stranger = new Thread(() -> {
                try {
                    run.frame();
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }, "not-the-render-thread");
            stranger.start();
            stranger.join();
            assertNotNull(failure[0], "a frame from another thread was refused");
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> {
                throw (IllegalStateException) failure[0];
            });
            assertTrue(refused.getMessage().contains("EmbeddedRun.frame()"), refused.getMessage());
        }
    }
}
