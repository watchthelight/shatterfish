package org.shatterfish.harness.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.utils.TimeUtils;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Advances a {@link GameScene} one frame at a time on the calling thread, and returns from each
 * frame only when the actor thread has parked again.
 *
 * <p>A frame here is what {@code Game.update()} does on the render thread
 * ({@code SPD-classes/.../noosa/Game.java:261-274}), minus the input handler a headless process
 * has no events for: the frame time is capped at 0.2 s as the game caps it, the posted runnables
 * are drained first as the backends drain them before rendering, then music, the scene, and the
 * cameras update. Nothing is drawn.
 *
 * <p>The part that is not in the game is the waiting. The scene starts the actor thread and
 * notifies it from its own {@code update()} ({@code core/.../scenes/GameScene.java:864-887}),
 * after which the actor thread runs concurrently with the render thread until it parks again:
 * on its own monitor when an actor's {@code act()} returns false or the hero waits for input
 * ({@code core/.../actors/Actor.java:304-322}), or on a sprite's monitor while that sprite is
 * moving ({@code :277-286}). In the real game the two threads overlap, and any random draw made
 * on the render thread lands at a wall-clock-dependent point in the actor thread's sequence.
 * Headlessly that would make the same seed and the same actions replay differently, so every
 * frame is fenced:
 *
 * <ol>
 * <li>The actor thread's monitor is held across the whole frame. The scene's own {@code notify}
 * still happens, but the thread cannot run until the frame ends.</li>
 * <li>The monitor of the sprite the actor thread is waiting on, if any, is held too. A movement
 * ending inside the frame still notifies ({@code core/.../sprites/CharSprite.java:824-834}), but
 * the thread cannot proceed until the frame ends, and whether that sprite stopped moving is then
 * a plain read.</li>
 * <li>If the frame woke the thread by either route, or created it, the stepper polls until the
 * thread has entered its next wait, using the JVM's count of the thread's waits, which is the
 * one signal that cannot confuse "still parked" with "notified and not yet running".</li>
 * </ol>
 *
 * <p>The result is that the actor thread only ever runs between two frames, never during one, and
 * a frame that reaches the hero's Input wait returns with the actor thread parked and the game's
 * state quiescent. That is the shape story 1.4's loop and story 1.5's wait detection build on.
 *
 * <p>Two private statics of the game are reached through reflection: {@code GameScene.actorThread},
 * which the stepper sets once so that the scene finds its thread already running, and
 * {@code Actor.current}, read to learn which sprite the thread is waiting on. Both are couplings
 * to upstream field names, chosen over hooks because neither changes what the game does; if an
 * upgrade renames either the failure is immediate and says so, and row 4 of the ledger
 * (read-only accessors) is where they would move.
 */
public final class SceneStepper {

    /**
     * The frame time each step advances by: the most the game itself accepts in one frame
     * ({@code Game.java:263}), so nothing is fast-forwarded further than a slow real frame would.
     */
    public static final float FRAME = 0.2f;

    private static final Field ACTOR_THREAD = privateStatic(GameScene.class, "actorThread");
    private static final Field ACTOR_CURRENT = privateStatic(Actor.class, "current");
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final GameScene scene;
    private long frames;
    private Thread actorThread;
    private volatile Throwable actorThreadFailure;
    private long parkTimeoutNanos = Duration.ofSeconds(10).toNanos();

    public SceneStepper(GameScene scene) {
        this.scene = scene;
    }

    /** How long a frame may wait for the actor thread to park before it is declared stuck. */
    public void parkTimeout(Duration timeout) {
        parkTimeoutNanos = timeout.toNanos();
    }

    /** Frames stepped so far. */
    public long frames() {
        return frames;
    }

    /** The scene's actor thread, or null before the first frame created it. */
    public Thread actorThread() {
        return actorThread;
    }

    /** What killed the actor thread, if anything did; every step checks it first. */
    public Throwable actorThreadFailure() {
        return actorThreadFailure;
    }

    /** One frame of {@link #FRAME} seconds. */
    public void step() {
        step(FRAME);
    }

    /** One frame of {@code frameTime} seconds, capped as the game caps it. */
    public void step(float frameTime) {
        failIfTheActorThreadDied();
        if (actorThread == null) {
            startTheActorThread();
        }

        Thread before = actorThread;
        long waitedBefore = before == null ? 0 : waitedCount(before);
        CharSprite gate = gate();
        Object loopMonitor = before != null ? before : new Object();
        Object gateMonitor = gate != null ? gate : new Object();

        boolean wake;
        boolean released;
        Thread after;
        synchronized (loopMonitor) {
            synchronized (gateMonitor) {
                frame(frameTime);
                released = gate != null && !gate.isMoving;
                // The scene's own rule for waking the actor thread (GameScene.java:864, :881-885),
                // repeated here because its notify is invisible from outside, and decided here,
                // while the thread still cannot run, because a moment later it may be running and
                // the reading would be of a thread mid-turn. A second notify with one waiter is a
                // no-op, so nothing is woken twice.
                wake = !Actor.processing() && Dungeon.hero != null && Dungeon.hero.isAlive();
                after = sceneActorThread();
            }
            if (after != before) {
                adopt(after);
            }
            if (wake && after != null && after == before) {
                synchronized (after) {
                    after.notify();
                }
            }
        }

        boolean created = after != null && after != before;
        if (after != null && (wake || created || released)) {
            awaitPark(after, created ? 0 : waitedBefore);
        }
    }

    /**
     * Starts the actor thread the way the scene starts it ({@code GameScene.java:866-880}) and
     * waits for its first park, so that the first frame is fenced like every other. Left to the
     * scene, the thread is created in the middle of the first {@code update()} and its first
     * turn runs concurrently with the rest of that frame; the scene then finds this thread alive
     * and only ever notifies it. The one visible difference is that the hero's first turn, which
     * observes the level and waits, runs before the first frame instead of during it.
     */
    private void startTheActorThread() {
        Thread thread = new Thread(Actor::process);
        if (Runtime.getRuntime().availableProcessors() == 1) {
            thread.setPriority(Thread.NORM_PRIORITY - 1);
        }
        thread.setName("SHPD Actor Thread");
        adopt(thread);
        try {
            ACTOR_THREAD.set(null, thread);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        Actor.keepActorThreadAlive = true;
        thread.start();
        awaitPark(thread, 0);
    }

    /**
     * Asks the actor thread to finish and waits for it, for the end of a Run. The scene's own
     * teardown then finds it dead and does not wait ({@code GameScene.java:768-777}).
     */
    public void endActorThread() {
        Thread thread = actorThread;
        if (thread == null) {
            return;
        }
        GameScene.endActorThread();
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            throw new IllegalStateException("the actor thread did not finish within 5s: " + describe(thread));
        }
        actorThread = null;
    }

    private void frame(float frameTime) {
        Game.elapsed = Game.timeScale * Math.min(0.2f, frameTime);
        Game.timeTotal += Game.elapsed;
        Game.realTime = TimeUtils.millis();

        ((HeadlessApplication) Gdx.app).executeRunnables();

        Music.INSTANCE.update();
        Sample.INSTANCE.update();
        scene.update();
        Camera.updateAll();
        frames++;
    }

    /**
     * The sprite the actor thread is waiting on, if it is waiting on one: the sprite of the actor
     * it is processing, while that sprite's movement animation runs ({@code Actor.java:271-285}).
     * Any other sprite finishing a move notifies nobody, so only this one is fenced and only its
     * release means the thread will run.
     */
    private static CharSprite gate() {
        Object current;
        try {
            current = ACTOR_CURRENT.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        if (current instanceof Char ch && ch.sprite != null && ch.sprite.isMoving) {
            return ch.sprite;
        }
        return null;
    }

    private void adopt(Thread thread) {
        actorThread = thread;
        if (thread != null) {
            thread.setUncaughtExceptionHandler((t, failure) -> actorThreadFailure = failure);
        }
    }

    private void awaitPark(Thread thread, long waitedBefore) {
        long deadline = System.nanoTime() + parkTimeoutNanos;
        int spins = 0;
        while (true) {
            failIfTheActorThreadDied();
            if (!thread.isAlive()) {
                return;
            }
            if (waitedCount(thread) > waitedBefore && thread.getState() == Thread.State.WAITING) {
                return;
            }
            if (System.nanoTime() - deadline > 0) {
                throw new IllegalStateException("the actor thread did not park within "
                        + Duration.ofNanos(parkTimeoutNanos).toMillis() + " ms after frame " + frames
                        + ": " + describe(thread));
            }
            if (++spins % 64 == 0) {
                Thread.yield();
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private void failIfTheActorThreadDied() {
        Throwable failure = actorThreadFailure;
        if (failure != null) {
            throw new IllegalStateException("the actor thread died after frame " + frames, failure);
        }
    }

    private static long waitedCount(Thread thread) {
        ThreadInfo info = THREADS.getThreadInfo(thread.threadId());
        return info == null ? Long.MAX_VALUE : info.getWaitedCount();
    }

    private static String describe(Thread thread) {
        StringBuilder out = new StringBuilder();
        out.append("state=").append(thread.getState())
                .append(", Actor.processing=").append(Actor.processing())
                .append(", Actor.now=").append(Actor.now());
        if (Dungeon.hero != null) {
            out.append(", hero.ready=").append(Dungeon.hero.ready)
                    .append(", hero.curAction=").append(Dungeon.hero.curAction)
                    .append(", hero.resting=").append(Dungeon.hero.resting);
        }
        out.append(", actor thread stack:");
        for (StackTraceElement element : thread.getStackTrace()) {
            out.append("\n    at ").append(element);
        }
        return out.toString();
    }

    private static Thread sceneActorThread() {
        try {
            return (Thread) ACTOR_THREAD.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field privateStatic(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name + " is not where the pinned"
                    + " upstream had it; the stepper reads it to fence the actor thread (see docs/UPSTREAM.md)", e);
        }
    }
}
