package org.shatterfish.harness.boot;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.HeadlessNativesLoader;
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
 * unless the driver does it, which {@link #drainPostedRunnables()} is for.</li>
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

    private final HeadlessApplication application;
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
        application = new HeadlessApplication(new ApplicationAdapter() {
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

    /** Points the game's file access at {@code directory}, which must exist. */
    public void profile(Path directory) {
        FileUtils.setDefaultFileProperties(Files.FileType.Absolute, directory.toAbsolutePath() + "/");
        profile = directory;
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
