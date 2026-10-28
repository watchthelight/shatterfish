package org.shatterfish.harness.boot;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * The headless backend, subclassed for two things libGDX keeps protected: how many runnables are queued, which a driver
 * must know before it declares an Input wait, and whether the loop thread is alive, which it must not be.
 *
 * <p>It is a top-level class, and not the private nested class it was, for the isolated worlds of the E10 spike
 * (ADR-0024): libGDX and its natives live once in the parent class loader, so {@code Gdx.app} is one object for every
 * world in the process, and a world booted in an isolated loader adopts the backend the process already has. For that the
 * class must be one class for all of them, and {@code IsolationLoader} lists it among the few it always delegates to the
 * parent. In a process with no isolated world nothing changes: the one boot makes the one backend, as before.
 */
public final class SharedBackend extends HeadlessApplication {

    SharedBackend(ApplicationListener listener, HeadlessApplicationConfiguration config) {
        super(listener, config);
    }

    /** Runnables posted and not yet run. */
    public int pendingRunnables() {
        synchronized (runnables) {
            return runnables.size;
        }
    }

    /**
     * Takes the queued runnables out, leaving the queue empty, so that a world entered from the live Run runs none of the
     * live Run's and the live Run is put back with them ({@link #putBack}). The queue is the one libGDX static the two
     * sides cannot each have a copy of; this is how they take turns at it (ADR-0024).
     */
    public List<Runnable> takeQueued() {
        synchronized (runnables) {
            List<Runnable> held = new ArrayList<>(runnables.size);
            for (int i = 0; i < runnables.size; i++) {
                held.add(runnables.get(i));
            }
            runnables.clear();
            return held;
        }
    }

    /** Puts runnables taken by {@link #takeQueued} back at the head of the queue, in their order. */
    public void putBack(List<Runnable> held) {
        synchronized (runnables) {
            for (int i = held.size() - 1; i >= 0; i--) {
                runnables.insert(0, held.get(i));
            }
        }
    }

    public boolean loopThreadAlive() {
        return mainLoopThread != null && mainLoopThread.isAlive();
    }

    void joinLoopThread() {
        try {
            mainLoopThread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the backend's loop thread ended", e);
        }
        if (mainLoopThread.isAlive()) {
            throw new IllegalStateException("the backend's loop thread is still running 10 s after create();"
                    + " with updatesPerSecond = -1 it must end, or the driver is not the only loop");
        }
    }
}
