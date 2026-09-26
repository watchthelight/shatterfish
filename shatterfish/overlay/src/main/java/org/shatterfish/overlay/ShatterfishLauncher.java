package org.shatterfish.overlay;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.desktop.DesktopPlatformSupport;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;
import com.watabou.utils.Point;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.rng.Salt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Starts the desktop game with a Run attached (story 5.1, FR-37): the Overlay's entry point.
 *
 * <p>It is upstream's desktop launcher ({@code desktop/…/DesktopLauncher.java}) with the player's
 * directories replaced by the Run's: the Run gets a Profile of its own (ADR-0007), in a directory this
 * launcher claims, with settings in memory, so starting the Overlay never reads or writes the player's
 * saves, badges or preferences, and a Run started twice from one tuple starts from one state. The
 * game itself is upstream's, played by {@link OverlayGame}; the backend is libGDX's own with the
 * game's posts to the render thread counted ({@link OverlayApplication}).
 *
 * <p><b>The oracle.</b> {@code --oracle} is accepted here and nowhere else a Run can be started from:
 * the Rig refuses the flag by name. An oracle Run sees through the launcher's own
 * {@code OracleObserver}, its log header says so, its window's title says so, and its Mode strip
 * carries an ORACLE label (story 5.2). An oracle Run always opens windowed, so the title bar is never
 * hidden by fullscreen; the border around the game view is story 5.12's.
 *
 * <p><b>Not a Rig Run.</b> An Overlay Run's log says {@code driver: embedded}: it is not reproducible
 * from its tuple or its Action list until story 5.13 (ADR-0013), and the Rig refuses it.
 */
public final class ShatterfishLauncher {

    /** The file that claims a Profile directory for one Run, created only if absent (FR-37). */
    static final String OWNER_FILE = ".shatterfish-owner";

    private ShatterfishLauncher() {
    }

    public static void main(String[] args) {
        LaunchOptions options = LaunchOptions.parse(args);
        long salt = options.salt() != null ? options.salt() : Salt.draw();
        Path profile = freshProfile(options.profile());
        boolean madeForThisRun = deletesProfile(options);
        if (madeForThisRun) {
            // The game deletes it at its close; this is for a game that dies without closing (an
            // exception out of a frame skips dispose). The Run's log is elsewhere and is left as a killed
            // Run's is, without an end record, which the reader reports as unfinished (ADR-0012).
            Runtime.getRuntime().addShutdownHook(new Thread(() -> OverlayGame.deleteQuietly(profile),
                    "shatterfish-profile-cleanup"));
        }
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

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(title(options));
        config.setPreferencesConfig(profile.toAbsolutePath() + "/", Files.FileType.Absolute);
        config.setWindowSizeLimits(720, 400, -1, -1);
        Point size = options.windowWidth() > 0 ? new Point(options.windowWidth(), options.windowHeight())
                : SPDSettings.windowResolution();
        config.setWindowedMode(size.x, size.y);
        config.setWindowIcon("icons/icon_16.png", "icons/icon_32.png", "icons/icon_48.png",
                "icons/icon_64.png", "icons/icon_128.png", "icons/icon_256.png");

        new OverlayApplication(new OverlayGame(new DesktopPlatformSupport(), options, preferences, profile,
                madeForThisRun, salt, OverlayAgents.of(options), observer(options.oracle()),
                logging(options, machine())), config);
    }

    /** The window's title, which carries the oracle marker, as the Mode strip does (story 5.2) and the border will (story 5.12). */
    static String title(LaunchOptions options) {
        return "Shattered Pixel Dungeon + Shatterfish Overlay" + (options.oracle() ? " [ORACLE]" : "");
    }

    /** Where the Run's log goes and who played it; the oracle flag is the launcher's, and nobody else's. */
    static RunLoop.Logging logging(LaunchOptions options, String machine) {
        return new RunLoop.Logging(options.out().toAbsolutePath(), "0".repeat(40),
                new RunLog.Brain(OverlayAgents.name(options), "0".repeat(40), "0".repeat(64)), "", machine,
                options.oracle());
    }

    /**
     * Whether the Run's Profile is the launcher's to delete: only one it made, never one the player
     * named with {@code --profile}. A named directory keeps its claim file afterwards, so it is not
     * reused by a later Run (FR-37); give each Run a new one.
     */
    static boolean deletesProfile(LaunchOptions options) {
        return options.profile() == null;
    }

    /**
     * The directory this Run's Profile is prepared in, claimed for this Run and no other (FR-37): a new
     * one, or the one asked for if it holds nothing. The claim is a file created only if it does not
     * exist, which the file system decides for one process at a time, so two launchers given one
     * directory cannot both take it; a directory that already holds anything else, a stamp, a save or
     * a player's files, is refused rather than prepared over (ADR-0007).
     */
    static Path freshProfile(Path asked) {
        try {
            Path directory = asked == null ? java.nio.file.Files.createTempDirectory("shatterfish-overlay-run")
                    : java.nio.file.Files.createDirectories(asked);
            try {
                java.nio.file.Files.createFile(directory.resolve(OWNER_FILE));
            } catch (FileAlreadyExistsException taken) {
                throw new IllegalArgumentException("the Profile directory " + directory + " is claimed by another"
                        + " Overlay Run; two Overlay Runs never share a Profile (FR-37)");
            }
            try (Stream<Path> inside = java.nio.file.Files.list(directory)) {
                if (inside.anyMatch(path -> !path.getFileName().toString().equals(OWNER_FILE))) {
                    java.nio.file.Files.delete(directory.resolve(OWNER_FILE));
                    throw new IllegalArgumentException("the Profile directory " + directory + " is not empty; two"
                            + " Overlay Runs never share a Profile (FR-37), so give an empty or new directory");
                }
            }
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException("could not claim the Run's Profile directory", e);
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
