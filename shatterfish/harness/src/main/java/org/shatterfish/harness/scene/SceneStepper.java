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

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Advances a {@link GameScene} one frame at a time on the calling thread, and returns from each
 * frame only when the actor thread has parked again.
 *
 * <p>A frame here is what {@code Game.update()} does on the render thread
 * ({@code SPD-classes/.../noosa/Game.java:269-283}), minus the input handler a headless process
 * has no events for: the frame time is capped at 0.2 s as the game caps it, the posted runnables
 * are drained first as the backends drain them before rendering, then music, the scene, and the
 * cameras update. Nothing is drawn.
 *
 * <p>The part that is not in the game is the waiting. The scene starts the actor thread and
 * notifies it from its own {@code update()} ({@code core/.../scenes/GameScene.java:865-888}),
 * after which the actor thread runs concurrently with the render thread until it parks again. At
 * the pinned tag it parks in three places: on its own monitor between turns
 * ({@code core/.../actors/Actor.java:318}); on the sprite of the character it is about to process,
 * while that sprite's movement animation runs ({@code Actor.java:274-282}); and, while the
 * gravity-chaos curse is active, on each moving sprite in {@code Actor.chars()} order until none
 * moves ({@code core/.../actors/buffs/GravityChaosTracker.java:76-86}). {@code grep '\.wait('}
 * under {@code core} and {@code SPD-classes} finds those three and one more,
 * {@code GameScene.waitForActorThread} ({@code GameScene.java:796-806}), which is the render
 * thread waiting on the actor thread from {@code destroy()} and {@code onPause()}; it must never
 * be reached on this thread inside a frame, because {@code Object.wait} releases a reentrant
 * hold in full. In the real game the two threads overlap, and any random draw made on the render
 * thread lands at a wall-clock-dependent point in the actor thread's sequence. Headlessly that
 * would make the same seed and the same actions replay differently, so every frame is fenced:
 *
 * <ol>
 * <li>Before the frame the thread must be parked, and the object the JVM reports it waiting on
 * must be its own monitor or a moving sprite this class is about to hold. A wait site this class
 * does not know fails the step by name rather than letting the thread run unfenced.</li>
 * <li>The actor thread's monitor is held across the whole frame. The scene's own {@code notify}
 * still happens, but the thread cannot run until the frame ends.</li>
 * <li>The monitor of every sprite that is moving is held too. A movement ending inside the frame
 * still notifies ({@code core/.../sprites/CharSprite.java:826-834}), but whichever sprite the
 * thread is waiting on, it cannot proceed until the frame ends.</li>
 * <li>At the end of the frame, with the monitors still held, the thread's state says whether
 * anything woke it: a thread that was notified, on its own monitor or on the sprite it waits on,
 * is blocked on the monitor this class holds, and reads as {@code BLOCKED}; one that was not is
 * still {@code WAITING}. That is the scene's own wake rule and the game's own movement release,
 * read rather than repeated, throttle and all. If it was woken, the stepper releases the
 * monitors and polls the JVM's count of the thread's waits until the thread has entered its next
 * wait, which is the one signal that cannot confuse "still parked" with "notified and not yet
 * running".</li>
 * <li>The monitors the thread released when it parked, its own and every sprite's, are then
 * acquired and released once, so that everything the thread wrote before parking is visible to
 * whoever reads game state between frames, by the language's rules and not by the hardware's.
 * The thread is also started by the stepper before the first frame, where the scene would
 * otherwise start it mid-update.</li>
 * </ol>
 *
 * <p>The result is that the actor thread only ever runs between frames, never during one, and a
 * frame that reaches the hero's Input wait returns with the actor thread parked and the game's
 * state quiescent. Two checks keep the promise honest on a JVM that does not report states the
 * way HotSpot does: a thread that parks between two frames without the stepper having waited for
 * it fails the next step, and so does a thread that is anything but parked or blocked at the end
 * of a frame. {@code FenceInvariantTest} observes the same things from the scene's side, with and
 * without the gravity-chaos curse. That is the shape story 1.4's loop and story 1.5's wait
 * detection build on.
 *
 * <p>Two private statics of the game are reached through reflection, and {@code HarnessReflectionTest}
 * confines reflection in {@code harness} to this class: {@code GameScene.actorThread}, which the
 * stepper sets once so that the scene finds its thread already running, and {@code Actor.current},
 * read before each frame to predict which sprite the thread waits on for the check in step 1.
 * Neither changes what the game computes; if an upgrade renames either the failure is immediate
 * and says so, and row 4 of the ledger is where they would move.
 */
public final class SceneStepper {

    /**
     * The frame time each step advances by: the most the game itself accepts in one frame
     * ({@code Game.java:271}), so nothing is fast-forwarded further than a slow real frame would.
     */
    public static final float FRAME = 0.2f;

    private static final Field ACTOR_THREAD = privateStatic(GameScene.class, "actorThread");
    private static final Field ACTOR_CURRENT = privateStatic(Actor.class, "current");
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final GameScene scene;
    private long frames;
    private Thread actorThread;
    private volatile Throwable actorThreadFailure;
    private long parkedAfterLastStep;
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

    /** The scene's actor thread, or null before the first frame started it. */
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
        Thread thread = actorThread;
        if (!thread.isAlive()) {
            throw new IllegalStateException("the actor thread has ended; a Run does not step past endActorThread()");
        }

        long waitedBefore = waitedCount(thread);
        if (waitedBefore != parkedAfterLastStep) {
            throw new IllegalStateException("the actor thread parked " + (waitedBefore - parkedAfterLastStep)
                    + " time(s) since the last frame ended without the stepper waiting for it, so it ran"
                    + " between frames unfenced; the JVM did not report it blocked at the end of that frame: "
                    + describe(thread));
        }
        List<CharSprite> moving = movingSprites();
        checkTheWaitSite(thread, moving);

        Thread.State[] atFrameEnd = new Thread.State[1];
        synchronized (thread) {
            holdingAll(moving, 0, () -> {
                frame(frameTime);
                atFrameEnd[0] = thread.getState();
            });
            if (sceneActorThread() != thread) {
                throw new IllegalStateException("the scene replaced its actor thread during frame " + frames
                        + ", which it does only when the thread it had was dead");
            }
        }

        if (atFrameEnd[0] == Thread.State.BLOCKED) {
            awaitPark(thread, waitedBefore);
        } else if (atFrameEnd[0] != Thread.State.WAITING) {
            throw new IllegalStateException("the actor thread was " + atFrameEnd[0] + " at the end of frame "
                    + frames + ", so it ran during it: " + describe(thread));
        }
        publish(thread);
        parkedAfterLastStep = waitedCount(thread);
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

    /** Holds the monitors of {@code sprites[from..]} while {@code body} runs. */
    private static void holdingAll(List<CharSprite> sprites, int from, Runnable body) {
        if (from == sprites.size()) {
            body.run();
            return;
        }
        synchronized (sprites.get(from)) {
            holdingAll(sprites, from + 1, body);
        }
    }

    /**
     * Every sprite that is moving, in {@code Actor.chars()} order ({@code Actor.java:395}), which is
     * the order the gravity-chaos buff waits on them in. {@code isMoving} is read under the
     * sprite's monitor, which is what the actor thread released when it started waiting on it.
     */
    private static List<CharSprite> movingSprites() {
        List<CharSprite> moving = new ArrayList<>();
        for (Char ch : Actor.chars()) {
            CharSprite sprite = ch.sprite;
            if (sprite == null) {
                continue;
            }
            boolean isMoving;
            synchronized (sprite) {
                isMoving = sprite.isMoving;
            }
            if (isMoving) {
                moving.add(sprite);
            }
        }
        return moving;
    }

    /**
     * The thread must be parked, on its own monitor or on a moving sprite this class is about to
     * hold. Which sprite is predicted from the wait sites in the class comment: the character being
     * processed waits on its own sprite ({@code Actor.java:274-282}); the gravity-chaos buff, which
     * is not a character, waits on the first moving sprite in {@code Actor.chars()} order and then
     * the next ({@code GravityChaosTracker.java:76-86}). The prediction is compared with the class
     * and identity of the object the JVM reports the thread waiting on. Under the test JVM's
     * identity-hash pin every identity hash is the same value, so there the class is the only
     * discriminator; in a Rig process the comparison is exact.
     */
    private static void checkTheWaitSite(Thread thread, List<CharSprite> moving) {
        ThreadInfo info = THREADS.getThreadInfo(thread.threadId());
        if (info == null || info.getThreadState() != Thread.State.WAITING) {
            throw new IllegalStateException("the actor thread is not parked at the start of a frame: "
                    + describe(thread));
        }
        LockInfo lock = info.getLockInfo();
        if (lock == null) {
            throw new IllegalStateException("the actor thread waits on nothing this class knows: " + describe(thread));
        }
        if (lock.getClassName().equals(Thread.class.getName())
                && lock.getIdentityHashCode() == System.identityHashCode(thread)) {
            return; // parked between turns, on its own monitor (Actor.java:318)
        }

        Object current = read(ACTOR_CURRENT);
        CharSprite predicted;
        if (current instanceof Char ch) {
            predicted = ch.sprite;
        } else {
            predicted = moving.isEmpty() ? null : moving.get(0);
        }
        boolean known = predicted != null
                && moving.contains(predicted)
                && predicted.getClass().getName().equals(lock.getClassName())
                && lock.getIdentityHashCode() == System.identityHashCode(predicted);
        if (!known) {
            throw new IllegalStateException("the actor thread waits on " + lock + " but the stepper predicted "
                    + (predicted == null ? "no sprite" : predicted.getClass().getName())
                    + " (Actor.current=" + (current == null ? "null" : current.getClass().getSimpleName())
                    + ", moving sprites=" + moving.size()
                    + "): a wait site the stepper does not know, see SceneStepper's class comment. "
                    + describe(thread));
        }
    }

    private void startTheActorThread() {
        // GameScene.java:866-882, the scene's own construction of the thread.
        Thread thread = new Thread(Actor::process);
        if (Runtime.getRuntime().availableProcessors() == 1) {
            thread.setPriority(Thread.NORM_PRIORITY - 1);
        }
        thread.setName("SHPD Actor Thread");
        thread.setUncaughtExceptionHandler((t, failure) -> actorThreadFailure = failure);
        actorThread = thread;
        try {
            ACTOR_THREAD.set(null, thread);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        Actor.keepActorThreadAlive = true;
        thread.start();
        awaitPark(thread, 0);
        publish(thread);
        parkedAfterLastStep = waitedCount(thread);
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

    /**
     * Acquires and releases the monitors the actor thread released when it parked: its own, and
     * every character's sprite, one of which it may be waiting on. Whatever it wrote before parking
     * happens-before whatever the caller reads after this returns.
     */
    private static void publish(Thread thread) {
        synchronized (thread) {
            // nothing: the acquisition is the point
        }
        for (Char ch : Actor.chars()) {
            CharSprite sprite = ch.sprite;
            if (sprite != null) {
                synchronized (sprite) {
                    // likewise
                }
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
        return (Thread) read(ACTOR_THREAD);
    }

    private static Object read(Field field) {
        try {
            return field.get(null);
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
