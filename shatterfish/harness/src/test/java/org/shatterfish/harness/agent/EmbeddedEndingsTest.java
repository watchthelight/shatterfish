package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.harness.boot.HeadlessGame;
import org.shatterfish.harness.driver.WaitGate;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.scene.SceneStepper;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the embedded Run makes of the scene in front, of a Run that reaches no wait, of an oracle it was
 * not told of, of an answer that went stale while the Brain thought, and of an actor thread not yet
 * parked (story 5.1's review: F1 to F5 and the fairness review's oracle guard).
 *
 * <p>The desktop game serves its own scene changes, usually in the same frame's step() that the actor
 * thread asked in, so the Run decides by the scene actually in front; these tests put each kind of
 * scene in front of it directly.
 */
class EmbeddedEndingsTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;
    private static final Decider SEARCHES = observation -> new Action.Search();

    @AfterEach
    void theLoadingSceneModeBack() {
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
    }

    /** Puts {@code scene} in front of the Run, with the play scene's actor thread ended first. */
    private static void inFront(EmbeddedHost host, Scene scene) {
        host.driver.stepper().endActorThread();
        HeadlessGame game = host.driver.headlessBoot().game();
        game.destroy();
        game.switchTo(scene);
    }

    @Test
    @DisplayName("the surface in front is the win, whoever served it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_surface_is_the_win() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, null, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 2);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            inFront(host, new SurfaceScene() {
                @Override
                public void create() {
                }
            });
            assertEquals(EmbeddedRun.State.ENDED, run.frame());
            assertEquals(RunOutcome.Cause.WIN, run.outcome().cause());
        }
    }

    @Test
    @DisplayName("a loading scene in a mode the headless loop does not serve ends the Run as unserved")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_resurrection_is_unserved() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, null, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            InterlevelScene.mode = InterlevelScene.Mode.RESURRECT;
            inFront(host, new InterlevelScene() {
                @Override
                public void create() {
                }
            });
            assertEquals(EmbeddedRun.State.ENDED, run.frame());
            assertEquals(RunOutcome.Cause.UNSERVED_SCENE, run.outcome().cause());
            assertTrue(run.outcome().detail().contains("RESURRECT"), run.outcome().detail());
        }
    }

    @Test
    @DisplayName("a loading scene of a descent is a floor being served; any other scene is unserved")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_descent_plays_on_and_a_stranger_does_not() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, null, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
            inFront(host, new InterlevelScene() {
                @Override
                public void create() {
                }
            });
            assertEquals(EmbeddedRun.State.PLAYING, run.frame(), "the loading scene of a descent");
            // The play scene the loading scene asks for, asked from inside its frame, is one being built.
            Game.switchScene(GameScene.class);
            assertEquals(EmbeddedRun.State.PLAYING, run.frame(), "a play scene asked for is one being built");
            host.driver.headlessBoot().game().clearSceneSwitchRequest();
            host.driver.headlessBoot().game().switchTo(new Scene());
            assertEquals(EmbeddedRun.State.ENDED, run.frame());
            assertEquals(RunOutcome.Cause.UNSERVED_SCENE, run.outcome().cause());
        }
    }

    @Test
    @DisplayName("a Run that reaches no wait within the frame budget ends as an unknown window")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void no_wait_within_the_budget() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, null, 5_000);
            host.heldQueue = 1;
            assertEquals(EmbeddedRun.State.ENDED, host.play(EmbeddedRun.FRAME_BUDGET + 10L));
            assertEquals(RunOutcome.Cause.UNKNOWN_WINDOW, run.outcome().cause());
            assertTrue(run.outcome().detail().startsWith("no wait within " + EmbeddedRun.FRAME_BUDGET),
                    run.outcome().detail());
        }
    }

    @Test
    @DisplayName("an oracle Observation reaches no Run whose log does not say it is an oracle Run")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void an_unannounced_oracle_is_refused() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.run = EmbeddedRun.attach(host, SEED, HeroClass.WARRIOR, host.driver.rngControl(), SEARCHES,
                    () -> new OracleObserver().observe().observation(), null, 5_000);
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> host.play(100_000));
            assertTrue(refused.getMessage().contains("oracle"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("an answer that went stale while the Brain thought is dropped, and the same wait is confirmed again")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_stale_answer_is_dropped() throws InterruptedException {
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
            EmbeddedAttachTest.playUntilWaits(host, 1);
            // Something the Run did not do, while the Brain thinks: an Action handed to the game.
            WaitGate.live().handedOver();
            release.countDown();
            EmbeddedAttachTest.playUntil(host, () -> run.staleAnswers() == 1);
            assertTrue(run.lastStale().startsWith("wait 1"), run.lastStale());
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.THINKING);
            assertEquals(1, run.waitIndex(), "the same wait, confirmed again from what is in front now");
            EmbeddedAttachTest.playUntilWaits(host, 2);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            assertEquals(3, decisions.get(), "wait 1 twice, the stale answer and the fresh one, then wait 2");
            assertEquals(1, run.staleAnswers());
        }
    }

    @Test
    @DisplayName("no wait is confirmed while the actor thread is still running")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void no_wait_before_the_actor_thread_parks() throws Exception {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, null, 5_000);
            host.heldQueue = 1;
            EmbeddedAttachTest.playUntil(host, () -> run.hookNotifications() > 0 && Dungeon.hero.ready);
            host.heldQueue = 0;
            assertTrue(SceneStepper.actorThreadParked(), "the stepper fences every frame on the park");

            Field field = GameScene.class.getDeclaredField("actorThread");
            field.setAccessible(true);
            Object parked = field.get(null);
            CountDownLatch stop = new CountDownLatch(1);
            Thread busy = new Thread(() -> {
                while (stop.getCount() > 0) {
                    Thread.onSpinWait();
                }
            }, "busy-actor");
            busy.start();
            field.set(null, busy);
            try {
                assertFalse(SceneStepper.actorThreadParked());
                assertEquals(EmbeddedRun.State.PLAYING, run.frame());
                assertEquals(0, run.waitIndex(), "a running actor thread is not a wait");
            } finally {
                field.set(null, parked);
                stop.countDown();
                busy.join();
            }
            assertEquals(EmbeddedRun.State.THINKING, run.frame());
            assertEquals(1, run.waitIndex(), "confirmed once the actor thread is parked");
        }
    }
}
