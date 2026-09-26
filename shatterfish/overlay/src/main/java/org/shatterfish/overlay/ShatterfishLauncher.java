package org.shatterfish.overlay;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.desktop.DesktopPlatformSupport;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;
import com.watabou.utils.Point;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.rng.Salt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Starts the desktop game with a Run attached (story 5.1, FR-37): the Overlay's entry point.
 *
 * <p>It is upstream's desktop launcher ({@code desktop/…/DesktopLauncher.java}) with the player's
 * directories replaced by the Run's: the Run gets a Profile of its own (ADR-0007), in a directory this
 * launcher owns, with settings in memory, so starting the Overlay never reads or writes the player's
 * saves, badges or preferences, and a Run started twice from one tuple starts from one state. The
 * game itself is upstream's, played by {@link OverlayGame}; the backend is libGDX's own with its
 * render queue counted ({@link OverlayApplication}).
 *
 * <p><b>The oracle.</b> {@code --oracle} is accepted here and nowhere else a Run can be started from:
 * the Rig refuses the flag by name. An oracle Run sees through the launcher's own
 * {@code OracleObserver}, its log header says so, and its window's title says so, which is the one
 * place a flag can be seen before the Panel exists (story 5.2 draws it there too).
 *
 * <p>Until the Codex reader moves somewhere the Overlay can reach, the Run is played by the random
 * agent, the Rig's Baseline; the Brain arrives with the story that needs it to play the sewers
 * (5.16), and nothing here changes when it does but the supplier below.
 */
public final class ShatterfishLauncher {

    private ShatterfishLauncher() {
    }

    public static void main(String[] args) {
        LaunchOptions options = LaunchOptions.parse(args);
        long salt = options.salt() != null ? options.salt() : Salt.draw();
        Path profile = freshProfile(options.profile());
        try {
            java.nio.file.Files.createDirectories(options.out());
        } catch (IOException e) {
            throw new UncheckedIOException("could not create " + options.out() + " for the Run's log", e);
        }

        // What the desktop launcher reads from its jar manifest, read from the harness's own stamp of
        // the pinned release, so a Run's save and log header name the release a headless Run's do.
        Game.version = HeadlessBoot.pinnedVersionName();
        Game.versionCode = HeadlessBoot.pinnedVersionCode();

        // The Run's settings, in memory and empty, installed before anything reads one; the Profile
        // declares the Run's own when the game is created.
        MemoryPreferences preferences = new MemoryPreferences();
        GameSettings.set(preferences);

        Supplier<Observation> observer = observer(options.oracle());
        Supplier<Decider> brain = () -> new RandomAgent(options.agentSeed());
        RunLoop.Logging logging = new RunLoop.Logging(options.out().toAbsolutePath(), "0".repeat(40),
                new RunLog.Brain("random", "0".repeat(40), "0".repeat(64)), "", machine(), options.oracle());

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Shattered Pixel Dungeon + Shatterfish Overlay" + (options.oracle() ? " [ORACLE]" : ""));
        config.setPreferencesConfig(profile.toAbsolutePath() + "/", Files.FileType.Absolute);
        config.setWindowSizeLimits(720, 400, -1, -1);
        Point size = SPDSettings.windowResolution();
        config.setWindowedMode(size.x, size.y);
        config.setWindowIcon("icons/icon_16.png", "icons/icon_32.png", "icons/icon_48.png",
                "icons/icon_64.png", "icons/icon_128.png", "icons/icon_256.png");

        new OverlayApplication(new OverlayGame(new DesktopPlatformSupport(), options, preferences, profile, salt,
                brain, observer, logging), config);
    }

    /**
     * The directory this Run's Profile is prepared in: a new one, or the one asked for if it is empty.
     * Two Overlay Runs never share a Profile (FR-37): a directory that holds anything, a Profile
     * stamp, a save or a player's files, is refused rather than prepared over, because a Run that
     * inherited it would be a different game claiming the same tuple (ADR-0007).
     */
    static Path freshProfile(Path asked) {
        try {
            if (asked == null) {
                return java.nio.file.Files.createTempDirectory("shatterfish-overlay-run");
            }
            if (java.nio.file.Files.exists(asked)) {
                try (java.util.stream.Stream<Path> inside = java.nio.file.Files.list(asked)) {
                    if (inside.findAny().isPresent()) {
                        throw new IllegalArgumentException("the Profile directory " + asked + " is not empty; two"
                                + " Overlay Runs never share a Profile (FR-37), so give an empty or new directory");
                    }
                }
            }
            return java.nio.file.Files.createDirectories(asked);
        } catch (IOException e) {
            throw new UncheckedIOException("could not prepare the Run's Profile directory", e);
        }
    }

    /**
     * The door the Brain sees through. The oracle is constructed here and nowhere else in the
     * Overlay ({@code OverlayOracleGateTest}), and only when the launcher was told {@code --oracle}.
     */
    static Supplier<Observation> observer(boolean oracle) {
        if (oracle) {
            return () -> new OracleObserver().observe().observation();
        }
        return () -> new Observer().observe();
    }

    private static String machine() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
