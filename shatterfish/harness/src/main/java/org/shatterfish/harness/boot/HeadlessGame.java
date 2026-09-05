package org.shatterfish.harness.boot;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.input.KeyEvent;
import com.watabou.input.PointerEvent;
import com.watabou.input.ScrollEvent;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import com.watabou.utils.PlatformSupport;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.harness.scene.SceneStepper;

/**
 * The {@code Game} instance a headless Run has instead of {@code ShatteredPixelDungeon}.
 *
 * <p>Game code dereferences {@code Game.instance} without a null check ({@code Game.scene()},
 * {@code Game.switchingScene()}, {@code SPD-classes/.../noosa/Game.java:222-228}), and the
 * constructor is what sets it (`:79-84`). The real game class is never used headlessly because it
 * drives scene switching and settings for a player (ADR-0015, option 4); this one exists to be
 * the static, to own scene changes on the driver's terms, and to never look like a scene switch
 * is pending.
 *
 * <p>Two things about it are load-bearing:
 *
 * <ul>
 * <li>{@code requestedReset} starts true in every {@code Game} ({@code Game.java:64}) and is
 * cleared only by {@code step()} when the first scene is created ({@code :230-236}). The actor
 * thread picks no actor while it is true ({@code core/.../actors/Actor.java:252}), so a driver
 * that never calls {@code step()} would get an actor loop that parks forever with no error.
 * Story 1.1 found this; the constructor clears it.</li>
 * <li>{@link #switchTo(Scene)} is the game's own {@code switchScene()} ({@code Game.java:249-267})
 * run on a scene the driver constructed, rather than one {@code step()} instantiates by class
 * name. That is how the harness substitutes {@link HeadlessScene} for {@code GameScene} without
 * an upstream edit.</li>
 * </ul>
 *
 * <p>One more thing is load-bearing, found by story 1.4: the backend's runnable queue is
 * process-wide and outlives a scene. What a Run's last act posted to the render thread (the
 * chasm prompt, the game-over banner, {@code core/.../actors/hero/Hero.java:2256}) would run at
 * the first frame of whatever scene exists next, which is the next Run's. So a scene is never
 * destroyed or replaced here with anything still queued: the queue is drained first, against the
 * scene that is about to go, which is where the game would have run it. The input event queues
 * are process statics in the same way ({@code SPD-classes/.../input/PointerEvent.java:131-132}),
 * with no way to clear them but delivery, and are delivered at the same moment.
 *
 * <p>It is never registered with libGDX as the application listener, so {@code create()} and
 * {@code render()} are never called and are made to say so if they are.
 */
public final class HeadlessGame extends Game {

    HeadlessGame(PlatformSupport platform) {
        super(HeadlessScene.class, platform);
        requestedReset = false;
    }

    /**
     * Destroys the current scene, if any, and creates {@code next} in its place, exactly as the
     * render thread does between two scenes: cameras reset, vertex buffers cleared, frame time
     * zeroed.
     */
    public void switchTo(Scene next) {
        if (next == null) {
            throw new IllegalArgumentException("switchTo needs a scene; use destroy() to end the last one");
        }
        refuseWhileTheActorThreadRuns("switchTo");
        drainWhatTheLastSceneWasPosted();
        requestedScene = next;
        requestedReset = false;
        switchScene();
    }

    /** Ends the current scene, as the game does when it shuts down; the actor thread must be ended first. */
    @Override
    public void destroy() {
        refuseWhileTheActorThreadRuns("destroy");
        drainWhatTheLastSceneWasPosted();
        super.destroy();
    }

    /**
     * Runs what game code posted to the render thread and no frame has run yet, while the scene
     * it was posted for still exists. {@code Game.runOnRenderThread} is {@code Gdx.app.postRunnable}
     * ({@code Game.java:306-313}); the headless backend queues it and runs nothing on its own.
     */
    private static void drainWhatTheLastSceneWasPosted() {
        ((HeadlessApplication) Gdx.app).executeRunnables();
        PointerEvent.processPointerEvents();
        KeyEvent.processKeyEvents();
        ScrollEvent.processScrollEvents();
    }

    /**
     * {@code GameScene.destroy()} interrupts a live actor thread and waits for it
     * ({@code GameScene.java:768-777}, {@code :796-806}) but never asks it to finish, so a scene
     * ended with its thread alive leaves an orphan that the next scene's {@code update()} finds
     * alive and notifies. The stepper's {@code endActorThread()} is the way to end a Run.
     */
    private void refuseWhileTheActorThreadRuns(String what) {
        if (scene instanceof GameScene && SceneStepper.theSceneHasALiveActorThread()) {
            throw new IllegalStateException(what + "() with the actor thread alive; call"
                    + " SceneStepper.endActorThread() first, or the next scene inherits a thread that was"
                    + " interrupted mid-turn");
        }
    }

    /** The scene created by the last {@link #switchTo(Scene)}, or null before it and after {@link #destroy()}. */
    public Scene currentScene() {
        return scene;
    }

    /**
     * Whether game code has asked for a scene change since the last one was served. The game does
     * this through {@code Game.switchScene(Class)} (`Game.java:212-220`) when the hero takes the
     * stairs, dies, or wins; the driver reads it here and decides what to do.
     */
    public boolean sceneSwitchRequested() {
        return requestedReset;
    }

    /** The scene class game code asked for, meaningful only while {@link #sceneSwitchRequested()}. */
    public Class<? extends Scene> requestedSceneClass() {
        return sceneClass;
    }

    /** Marks a requested switch as served, so the actor thread resumes picking actors. */
    public void clearSceneSwitchRequest() {
        requestedReset = false;
    }

    @Override
    public void create() {
        throw new IllegalStateException("HeadlessGame is never the application listener; HeadlessBoot"
                + " installs the backend statics and the driver owns the loop");
    }

    @Override
    public void render() {
        throw new IllegalStateException("HeadlessGame never renders; the driver steps the scene itself");
    }
}
