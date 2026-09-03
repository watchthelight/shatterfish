package org.shatterfish.harness;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder headless driver: boots the libGDX headless backend, proves it is alive, and exits.
 *
 * <p>E1 replaces the body with the real thing (boot {@code core} without a scene, seed the RNG,
 * run a hero through {@code Observer}/{@code ActionExecutor}). Keeping the boot path here now
 * means the module skeleton, the headless dependency, and CI are all exercised from day one.
 */
public final class HeadlessDriver {

    private HeadlessDriver() {
    }

    /** Result of a boot: what the backend reported about itself. */
    public record Boot(String applicationType, int updatesPerSecond) {
    }

    /**
     * Starts a headless application, captures the backend type from inside {@code create()},
     * asks it to exit, and waits for {@code dispose()}.
     *
     * @throws IllegalStateException if the backend does not come up and shut down within the timeout
     */
    public static Boot boot() {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        CountDownLatch disposed = new CountDownLatch(1);
        String[] type = new String[1];

        new HeadlessApplication(new ApplicationAdapter() {
            @Override
            public void create() {
                type[0] = Gdx.app.getType().name();
                Gdx.app.exit();
            }

            @Override
            public void dispose() {
                disposed.countDown();
            }
        }, config);

        try {
            if (!disposed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("headless backend did not shut down within 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for headless backend", e);
        }
        return new Boot(type[0], config.updatesPerSecond);
    }

    public static void main(String[] args) {
        Boot boot = boot();
        System.out.println("HeadlessDriver: booted libGDX " + boot.applicationType()
                + " backend (" + boot.updatesPerSecond() + " ups) and exited cleanly");
    }
}
