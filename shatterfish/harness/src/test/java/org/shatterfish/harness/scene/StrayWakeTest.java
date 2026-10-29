package org.shatterfish.harness.scene;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wake the stepper did not cause, injected where the JVM would put one under CPU starvation.
 *
 * <p>{@code Object.wait} may return with no notify. The actor thread then runs one pass of
 * {@code Actor.process} between two frames and parks again, so the next frame finds its wait
 * count one higher than the stepper left it. A notify from the test thread is that wake exactly:
 * the thread cannot tell the two apart. On a loaded machine the check on that count failed a
 * correct Run (the starvation regression test, 1h45m into a merge gate); here the timing is
 * injected instead of waited for.
 *
 * <p>A pass that changed nothing is harmless and is counted; a pass that spent game time is a
 * real unfenced run and still fails the step.
 */
class StrayWakeTest {

    private static final long SEED = 1_618_033L;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private HeadlessBoot boot;
    private SceneStepper stepper;

    @AfterEach
    void endTheRun() {
        if (stepper != null) {
            stepper.endActorThread();
            stepper = null;
        }
        if (boot != null) {
            boot.game().destroy();
        }
    }

    @Test
    @DisplayName("a wake with no notify that changed nothing does not fail the next frame")
    void a_stray_wake_that_changed_nothing_is_counted_not_fatal() throws Exception {
        startAtAnInputWait();
        strayWake();
        stepper.step();
        stepper.step();
        assertEquals(1, stepper.strayWakes());
        assertTrue(Dungeon.hero.isAlive());
    }

    @Test
    @DisplayName("a wake with no notify that spent game time still fails the next frame")
    void a_stray_wake_that_spent_time_still_fails() throws Exception {
        startAtAnInputWait();
        // A resting hero spends a turn each pass (Hero.java:892), so a pass between frames is a turn
        // played outside the fence: the case the check exists for.
        Dungeon.hero.resting = true;
        strayWake();
        IllegalStateException failure = assertThrows(IllegalStateException.class, stepper::step);
        assertTrue(failure.getMessage().contains("unfenced"), failure.getMessage());
        assertEquals(0, stepper.strayWakes());
    }

    @Test
    @DisplayName("a wake with no notify that drew a random number and spent no time still fails the next frame")
    void a_stray_wake_that_drew_from_the_generator_still_fails() throws Exception {
        startAtAnInputWait();
        // An actor that goes before everyone, rolls, spends nothing and asks to wait: its pass moves
        // the generator and nothing else. It is added and played once inside a frame first, so the
        // actor set, the clock and the hero are the same before and after the stray pass.
        Actor roller = new Actor() {
            {
                actPriority = Integer.MAX_VALUE;
            }

            @Override
            protected boolean act() {
                Random.Int(1_000_000);
                return false;
            }
        };
        Actor.add(roller);
        stepper.step();
        strayWake();
        IllegalStateException failure = assertThrows(IllegalStateException.class, stepper::step);
        assertTrue(failure.getMessage().contains("random generator"), failure.getMessage());
        assertEquals(0, stepper.strayWakes());
    }

    @Test
    @DisplayName("a wake with no notify between frames of a turn in progress is counted, not fatal")
    void a_stray_wake_mid_sequence_is_counted_not_fatal() throws Exception {
        startAtAnInputWait();
        // Resting: each turn ends with the hero unready and the thread parked on its own monitor
        // until the next frame, which is where a pass between frames runs the next turn early.
        Dungeon.hero.rest(true);
        for (int frames = 0; frames < 3; frames++) {
            stepper.step();
        }
        assertTrue(!Dungeon.hero.ready && stepper.parkedOnItsOwnMonitor(), "a rest is in progress");
        long clock = Float.floatToIntBits(Actor.now());
        strayWake();
        stepper.step();
        assertEquals(1, stepper.strayWakes());
        assertTrue(Float.floatToIntBits(Actor.now()) != clock || Dungeon.hero.ready,
                "the pass between frames played a turn");
    }

    private void startAtAnInputWait() {
        boot = HeadlessBoot.ensure();
        FreshRun.start(SEED);
        GameScene scene = new GameScene();
        stepper = new SceneStepper(scene);
        boot.game().switchTo(scene);
        int frames = 0;
        while (!(Dungeon.hero.ready && Dungeon.hero.curAction == null) && frames++ < 500) {
            stepper.step();
        }
        assertTrue(Dungeon.hero.ready, "the hero reached an Input wait");
    }

    /** Wakes the actor thread with no notify of the scene's and waits until it has parked again. */
    private void strayWake() throws InterruptedException {
        Thread thread = stepper.actorThread();
        long before = THREADS.getThreadInfo(thread.threadId()).getWaitedCount();
        synchronized (thread) {
            thread.notify();
        }
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (THREADS.getThreadInfo(thread.threadId()).getWaitedCount() == before
                || thread.getState() != Thread.State.WAITING) {
            assertTrue(System.nanoTime() < deadline, "the actor thread parked again");
            Thread.sleep(1);
        }
    }
}
