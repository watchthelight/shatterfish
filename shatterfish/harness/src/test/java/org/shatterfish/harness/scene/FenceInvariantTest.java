package org.shatterfish.harness.scene;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.GravityChaosTracker;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stepper's promise, observed directly: the actor thread never runs during a frame.
 *
 * <p>A watched scene samples, around every {@code super.update()}, the JVM's count of the actor
 * thread's waits, the thread's state, and which actor is being processed. If any of them changes
 * across the frame, the thread ran inside it. The fingerprint comparison in
 * {@code SceneDrawParityTest} would eventually notice such a run through its consequences; this
 * test notices the run itself, in the frame it happens.
 *
 * <p>The second case appends the gravity-chaos curse to the hero. Its buff waits on every moving
 * sprite in turn from a wait site that is not a character's own
 * ({@code core/.../actors/buffs/GravityChaosTracker.java:76-86}), which is the case the first
 * fairness review of this story found unfenced.
 */
class FenceInvariantTest {

    private static final long SEED = 1_618_033L;
    private static final int WAITS = 60;
    private static final int FRAME_BUDGET = 6_000;

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
    @DisplayName("the actor thread never runs during a frame")
    void the_actor_thread_never_runs_during_a_frame() throws Exception {
        Watched scene = play(false);
        assertEquals(List.of(), scene.violations);
        assertTrue(scene.movesSeen > 20, "movement gates were exercised: " + scene.movesSeen + " frames with a moving sprite");
    }

    @Test
    @DisplayName("the actor thread never runs during a frame under the gravity-chaos curse")
    void the_actor_thread_never_runs_during_a_frame_under_gravity_chaos() throws Exception {
        Watched scene = play(true);
        assertEquals(List.of(), scene.violations);
        assertTrue(scene.trackerWaits > 0,
                "the curse's own wait site was reached: " + scene.trackerWaits + " frames began with the tracker current");
    }

    private Watched play(boolean gravityChaos) throws Exception {
        boot = HeadlessBoot.ensure();
        FreshRun.start(boot, SEED, () -> {
        });
        Watched scene = new Watched();
        stepper = new SceneStepper(scene);
        scene.stepper = stepper;
        boot.game().switchTo(scene);
        if (gravityChaos) {
            Buff.append(Dungeon.hero, GravityChaosTracker.class);
        }

        // The floor is whatever this JVM generated (the guidebook cell is unseeded), so the run may
        // end by the script, by death, or by the game asking to leave the floor: the curse can throw
        // the hero into a chasm, after which the actor thread rightly picks nothing until the scene
        // changes, which is story 1.6's. None of those is the invariant; the frame budget is.
        int waits = 0;
        while (stepper.frames() < FRAME_BUDGET && waits < WAITS && Dungeon.hero.isAlive()
                && !boot.game().sceneSwitchRequested()) {
            stepper.step();
            Hero hero = Dungeon.hero;
            if (hero.ready && hero.curAction == null && !hero.resting) {
                waits++;
                GameScene.handleCell(freeCellBeside(hero.pos));
            }
        }
        scene.waits = waits;
        scene.heroDied = !Dungeon.hero.isAlive();
        assertTrue(stepper.frames() < FRAME_BUDGET,
                "the run ended by the script, by death or by a floor change, not by the frame budget: "
                        + waits + " waits, hero " + (Dungeon.hero.isAlive() ? "alive" : "dead")
                        + ", floor change " + boot.game().sceneSwitchRequested());
        return scene;
    }

    /** A cell the hero can walk to next to it, not a chasm and not the stairs. */
    private static int freeCellBeside(int cell) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int candidate = cell + offset;
            if (candidate >= 0 && candidate < Dungeon.level.length()
                    && Dungeon.level.passable[candidate] && !Dungeon.level.pit[candidate]
                    && Dungeon.level.getTransition(candidate) == null
                    && Actor.findChar(candidate) == null) {
                return candidate;
            }
        }
        return cell;
    }

    /** A scene that watches the actor thread across each of its own updates. */
    static final class Watched extends GameScene {

        private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
        private static final Field CURRENT = current();

        SceneStepper stepper;
        final List<String> violations = new ArrayList<>();
        int frames;
        int waits;
        boolean heroDied;
        int movesSeen;
        int trackerWaits;

        @Override
        public synchronized void update() {
            Thread thread = stepper == null ? null : stepper.actorThread();
            if (thread == null) {
                super.update();
                return;
            }
            frames++;
            long waitedBefore = waitedCount(thread);
            Thread.State stateBefore = thread.getState();
            Object currentBefore = currentActor();
            // Between two frames the thread may be woken once: by the scene's notify or a movement
            // ending, both of which the stepper waits out. Two parks between frames means it was
            // woken twice, which is the double wake a wake rule read outside the fence produces.
            // Parks on anything but its monitor or a sprite (a console lock, say) count too, so the
            // stepper's own confirmed-park accounting is the stricter check; this one is the
            // scene's-eye view of it.
            if (lastWaitedAfter >= 0 && waitedBefore - lastWaitedAfter > 1 && !parkedAtAKnownSite(thread)) {
                violations.add("before frame " + frames + ": the actor thread parked "
                        + (waitedBefore - lastWaitedAfter) + " times since the last frame and is not at a known site");
            }
            if (currentBefore instanceof GravityChaosTracker) {
                trackerWaits++;
            }
            if (Dungeon.hero.sprite != null && Dungeon.hero.sprite.isMoving) {
                movesSeen++;
            }

            super.update();

            long waitedAfter = waitedCount(thread);
            Thread.State stateAfter = thread.getState();
            Object currentAfter = currentActor();
            // The thread must be parked when the frame starts. During the frame it may be notified
            // by the scene and then blocks on the monitor the stepper holds, which reads as BLOCKED
            // and is the fence working; anything else it could be at the end of the frame means it
            // ran. A run that parked again inside the frame shows as one more wait. A new actor
            // chosen inside the frame shows as the current actor changing to another actor, which
            // only the actor thread does; the driver thread may only clear it, through next().
            boolean ranInsideTheFrame = waitedAfter != waitedBefore
                    || (stateAfter != Thread.State.WAITING && stateAfter != Thread.State.BLOCKED)
                    || (currentAfter != currentBefore && currentAfter != null);
            if (stateBefore != Thread.State.WAITING || ranInsideTheFrame) {
                violations.add("frame " + frames + ": waits " + waitedBefore + "->" + waitedAfter
                        + ", state " + stateBefore + "->" + stateAfter
                        + ", current " + name(currentBefore) + "->" + name(currentAfter));
            }
            lastWaitedAfter = waitedAfter;
        }

        private long lastWaitedAfter = -1;

        private static long waitedCount(Thread thread) {
            ThreadInfo info = THREADS.getThreadInfo(thread.threadId());
            return info == null ? -1 : info.getWaitedCount();
        }

        /** Parked in {@code Object.wait} from one of the game's own wait sites, not on some other lock. */
        private static boolean parkedAtAKnownSite(Thread thread) {
            ThreadInfo info = THREADS.getThreadInfo(thread.threadId(), 16);
            if (info == null || info.getThreadState() != Thread.State.WAITING) {
                return false;
            }
            for (StackTraceElement frame : info.getStackTrace()) {
                if (!frame.getClassName().equals(Object.class.getName())) {
                    String site = frame.getClassName() + "." + frame.getMethodName();
                    return site.equals(Actor.class.getName() + ".process")
                            || site.equals(GravityChaosTracker.class.getName() + ".act");
                }
            }
            return false;
        }

        private static Object currentActor() {
            try {
                return CURRENT.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String name(Object actor) {
            return actor == null ? "null" : actor.getClass().getSimpleName();
        }

        private static Field current() {
            try {
                Field field = Actor.class.getDeclaredField("current");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
