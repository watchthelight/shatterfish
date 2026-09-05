package org.shatterfish.harness.boot;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.HeadlessNativesLoader;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.GameSettings;
import org.shatterfish.harness.scene.NoOpGL;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Everything the game needs to exist before a scene can, installed once per process.
 *
 * <p>This is the boot sequence story 1.1 discovered, in the order it has to happen:
 *
 * <ol>
 * <li>A {@link HeadlessApplication} whose own loop never runs ({@code updatesPerSecond = -1}),
 * so that {@code Gdx.app}, {@code Gdx.files} and {@code Gdx.audio} exist while the driver owns
 * the loop (ADR-0015, option 2). The consequence is that posted runnables are drained by nobody
 * unless the driver does it, which {@link #drainPostedRunnables()} is for; its loop thread ends as
 * soon as it has created the listener, and the boot waits for that.</li>
 * <li>The no-op graphics binding, before anything can construct a texture. Every texture calls
 * {@code glGenTexture} in its constructor ({@code SPD-classes/.../glwrap/Texture.java:47}).</li>
 * <li>Settings in memory, with the intro off. The headless backend's own preferences would write
 * under the user's home directory; a Run's settings are the harness's to pin and nobody's to
 * keep.</li>
 * <li>The statics the desktop launcher and {@code Game.create()} set: version, density, window
 * size. {@code Game.versionCode} is written into every save by {@code Dungeon.init()}; the
 * window size decides the UI layout, which is presentation, and is pinned to the game's own
 * default ({@code core/.../SPDSettings.java:447-451}).</li>
 * <li>The {@link HeadlessGame}, which is {@code Game.instance} and clears the pending scene
 * switch that would otherwise park the actor thread forever.</li>
 * <li>A profile directory, the language, and a camera, which sprites need before any scene
 * exists ({@code CharSprite.worldToCamera} reads {@code Camera.main},
 * {@code core/.../sprites/CharSprite.java:183-184}).</li>
 * </ol>
 *
 * <p>The backend is a process-wide static, so there is exactly one boot per JVM;
 * {@link #ensure()} returns it. The profile directory is the one thing that changes between
 * Runs, through {@link #profile(Path)}; story 1.15 owns what a Profile is.
 */
public final class HeadlessBoot {

    /** The game's default window, which is what an Overlay Run most likely draws into. */
    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;

    private static HeadlessBoot instance;

    private final Backend application;
    private final MemoryPreferences preferences;
    private final HeadlessGame game;
    private final String upstreamVersionName;
    private final int upstreamVersionCode;
    private Path profile;

    /** Boots the process if it has not been booted, and returns the one boot either way. */
    public static synchronized HeadlessBoot ensure() {
        if (instance == null) {
            instance = new HeadlessBoot();
        }
        return instance;
    }

    private HeadlessBoot() {
        if (Gdx.app != null) {
            throw new IllegalStateException("a libGDX application is already installed in this process;"
                    + " the harness must be the one to boot it");
        }

        HeadlessNativesLoader.load();
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1;
        CountDownLatch created = new CountDownLatch(1);
        application = new Backend(new ApplicationAdapter() {
            @Override
            public void create() {
                created.countDown();
            }
        }, config);
        try {
            if (!created.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the headless backend did not start within 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the headless backend started", e);
        }

        // With updatesPerSecond at -1 the backend's loop is skipped and its thread ends right after
        // create() (HeadlessApplication.mainLoop, libGDX 1.14.0). The boot waits for that, so that
        // "no library-owned loop drives the scene" is checked here rather than assumed.
        application.joinLoopThread();

        // Errors only. The game echoes every log line to the console through Gdx.app.log, from the
        // actor thread; the harness reads the game log through GLog's own listener instead, and a
        // console write is a lock the actor thread can block on halfway to its park, which the
        // stepper would then have to wait out (SceneStepper's class comment).
        application.setLogLevel(Application.LOG_ERROR);

        Gdx.gl20 = NoOpGL.gl20();
        Gdx.gl30 = NoOpGL.gl30();
        Gdx.gl = Gdx.gl20;

        preferences = new MemoryPreferences();
        GameSettings.set(preferences);
        SPDSettings.intro(false);
        SPDSettings.language(Languages.ENGLISH);

        Properties upstream = upstreamProperties();
        upstreamVersionName = upstream.getProperty("version.name");
        upstreamVersionCode = Integer.parseInt(upstream.getProperty("version.code"));
        Game.version = upstreamVersionName;
        Game.versionCode = upstreamVersionCode;
        Game.density = 1f;
        Game.width = WIDTH;
        Game.height = HEIGHT;

        game = new HeadlessGame(new HeadlessPlatformSupport());

        try {
            profile(java.nio.file.Files.createTempDirectory("shatterfish-profile"));
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a profile directory", e);
        }
        Messages.setup(Languages.ENGLISH);
        Camera.reset();
    }

    public HeadlessGame game() {
        return game;
    }

    /** The settings the game reads; pin anything a Run depends on here before starting it. */
    public MemoryPreferences preferences() {
        return preferences;
    }

    /** The directory the game reads and writes its files in: saves, badges, journal, rankings. */
    public Path profile() {
        return profile;
    }

    /**
     * Points the game's file access at {@code directory}, which must exist, and forgets what the
     * game had cached about the save slots of the previous one. The game remembers, for the life
     * of the process, which slots it has seen occupied ({@code core/.../GamesInProgress.java:40-41},
     * {@code :98-136}), and every level change saves the game into the current slot
     * ({@code Dungeon.java:511-512}, {@code :707-714}); left alone, the cache would describe the
     * old directory and the seventh Run in a process would find no free slot.
     */
    public void profile(Path directory) {
        FileUtils.setDefaultFileProperties(Files.FileType.Absolute, directory.toAbsolutePath() + "/");
        profile = directory;
        for (int slot = 1; slot <= GamesInProgress.MAX_SLOTS; slot++) {
            GamesInProgress.setUnknown(slot);
        }
    }

    /** The upstream release this build of the harness runs, from the root build script. */
    public String upstreamVersionName() {
        return upstreamVersionName;
    }

    public int upstreamVersionCode() {
        return upstreamVersionCode;
    }

    /**
     * Runs everything the game has posted to the render thread since the last call, on the
     * calling thread. {@code Game.runOnRenderThread} is {@code Gdx.app.postRunnable}
     * ({@code Game.java:306-313}); the game uses it to show windows from the actor thread, and a
     * real frame drains the queue before it renders.
     */
    public void drainPostedRunnables() {
        application.executeRunnables();
    }

    /** Runnables posted since the last frame drained the queue; the next frame runs them first. */
    public int pendingRunnables() {
        return application.pendingRunnables();
    }

    /** Whether the backend's own loop thread is alive; it ends during the boot and stays ended. */
    public boolean backendLoopThreadAlive() {
        return application.loopThreadAlive();
    }

    /**
     * The backend, subclassed for two things libGDX keeps protected: how many runnables are
     * queued, which a driver must know before it declares an Input wait, and whether the loop
     * thread is alive, which it must not be.
     */
    private static final class Backend extends HeadlessApplication {

        Backend(ApplicationListener listener, HeadlessApplicationConfiguration config) {
            super(listener, config);
        }

        int pendingRunnables() {
            synchronized (runnables) {
                return runnables.size;
            }
        }

        boolean loopThreadAlive() {
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

    private static Properties upstreamProperties() {
        Properties properties = new Properties();
        try (InputStream in = HeadlessBoot.class.getResourceAsStream("upstream.properties")) {
            if (in == null) {
                throw new IllegalStateException("upstream.properties is missing from the harness resources");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
