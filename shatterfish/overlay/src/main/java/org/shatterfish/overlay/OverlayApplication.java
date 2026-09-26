package org.shatterfish.overlay;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The desktop backend the Overlay runs in: libGDX's own, with one thing counted.
 *
 * <p>An embedded Run confirms an Input wait only when nothing the game posted is queued for the render
 * thread (ADR-0015; {@code WaitGate.frame}), because what the hero's act posts there, a window, runs at
 * the start of the next frame before anyone could observe or click. The headless backend exposes its
 * queue; this one keeps it private, so the game's posts are counted here instead, at the one door the
 * game posts through ({@code Game.runOnRenderThread} is {@code Gdx.app.postRunnable},
 * {@code SPD-classes/…/noosa/Game.java:306-313}): one more when a runnable is posted, one fewer when
 * it runs.
 *
 * <p>Everything but libGDX's own runnables is counted ({@code com.badlogic}): the game's, and anything
 * of Shatterfish's that ever posts to the render thread. The desktop backend queues its own too: the
 * controller library's monitor re-posts itself on every frame
 * ({@code com.badlogic.gdx.controllers.desktop.support.JamepadControllerMonitor}), so the queue is
 * never empty at the end of a frame and a count of everything would confirm no wait at all, which the
 * first launch of story 5.1 showed. The headless backend has no controllers and so queues only what
 * the game posts; counting only that here is the same rule on both backends.
 *
 * <p>The count is static because libGDX runs its whole loop inside this class's constructor
 * ({@code Lwjgl3Application} loops in its own constructor until the window closes), so no instance
 * field of a subclass is ever initialised while the game plays. One process hosts one application.
 */
public final class OverlayApplication extends Lwjgl3Application {

    private static final AtomicInteger PENDING = new AtomicInteger();

    public OverlayApplication(ApplicationListener listener, Lwjgl3ApplicationConfiguration config) {
        super(listener, config);
    }

    @Override
    public void postRunnable(Runnable runnable) {
        if (!postedByTheGame(runnable)) {
            super.postRunnable(runnable);
            return;
        }
        PENDING.incrementAndGet();
        super.postRunnable(() -> {
            PENDING.decrementAndGet();
            runnable.run();
        });
    }

    /**
     * Whether a runnable counts, which is everything but libGDX's own: the game's, and anything of
     * Shatterfish's that ever posts to the render thread; see the class comment.
     */
    static boolean postedByTheGame(Runnable runnable) {
        return counts(runnable.getClass().getName());
    }

    /** The rule by the runnable's class name. */
    static boolean counts(String className) {
        return !className.startsWith("com.badlogic.");
    }

    /** Runnables the game posted to the render thread and not yet run. */
    public static int pending() {
        return PENDING.get();
    }
}
