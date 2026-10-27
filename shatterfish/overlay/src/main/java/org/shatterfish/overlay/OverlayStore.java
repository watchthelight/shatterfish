package org.shatterfish.overlay;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3FileHandle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Where the Overlay's settings are kept between games (story 8.4, FR-45): a libGDX {@link Preferences}, the
 * kind of store the game's own settings use ({@code SPDSettings} reads and writes through one), under keys
 * that all begin {@value #PREFIX} so they cannot meet a key of the game's.
 *
 * <p><b>Which file.</b> The Run's own preferences are in memory and thrown away (FR-37, ADR-0007), and the
 * launcher never reads or writes the player's {@code settings.xml}, so the person's choices can live in
 * neither. They live in a file of the same kind of its own, {@value #FILE_NAME} in {@code ~/.shatterfish}
 * (or where the system property {@value #PATH_PROPERTY} says), which a Run's Profile never touches and is
 * never deleted with. Nothing in it enters a Run's definition: the settings only choose when the Overlay
 * stops, and {@code PauseOnRunTest} holds that stopping does not change play.
 *
 * <p><b>Bad data.</b> A key that is absent, unparseable or out of range reads as the default of
 * {@link OverlaySettings}, one key at a time, and never throws: a settings file edited by hand or cut short
 * must not stop the screen or the launch.
 */
final class OverlayStore {

    /** Every key this class writes begins with this. */
    static final String PREFIX = "shatterfish_overlay_";
    /** One boolean per condition: this, then the condition's name in lower case. */
    static final String PAUSE_KEY = PREFIX + "pause_";
    /** The HP threshold, in percent of maximum hit points. */
    static final String HP_KEY = PREFIX + "pause_hp_percent";

    static final String FILE_NAME = "overlay-settings.xml";
    static final String PATH_PROPERTY = "shatterfish.overlay.settings";

    private final Preferences preferences;

    OverlayStore(Preferences preferences) {
        this.preferences = preferences;
    }

    /** The player's store: the file named by {@value #PATH_PROPERTY}, else {@value #FILE_NAME} in {@code ~/.shatterfish}. */
    static OverlayStore ofPlayer() {
        String asked = System.getProperty(PATH_PROPERTY);
        Path file = asked != null ? Path.of(asked)
                : Path.of(System.getProperty("user.home"), ".shatterfish", FILE_NAME);
        return at(file);
    }

    /** A store in {@code file}, which is created at the first save. */
    static OverlayStore at(Path file) {
        return new OverlayStore(new Lwjgl3Preferences(
                new Lwjgl3FileHandle(file.toAbsolutePath().toString(), Files.FileType.Absolute)));
    }

    static String key(PauseOn.Condition condition) {
        return PAUSE_KEY + condition.name().toLowerCase(Locale.ROOT);
    }

    /** What is stored, with the default for every key that is missing or bad. */
    OverlaySettings load() {
        Set<PauseOn.Condition> enabled = EnumSet.noneOf(PauseOn.Condition.class);
        for (PauseOn.Condition condition : PauseOn.Condition.values()) {
            if (readBoolean(key(condition))) {
                enabled.add(condition);
            }
        }
        return new OverlaySettings(enabled, readThreshold());
    }

    private boolean readBoolean(String key) {
        try {
            return preferences.getBoolean(key, false);
        } catch (RuntimeException corrupt) {
            return false;
        }
    }

    private int readThreshold() {
        try {
            int percent = preferences.getInteger(HP_KEY, OverlaySettings.HP_DEFAULT);
            return percent < OverlaySettings.HP_MIN || percent > OverlaySettings.HP_MAX
                    ? OverlaySettings.HP_DEFAULT : percent;
        } catch (RuntimeException corrupt) {
            return OverlaySettings.HP_DEFAULT;
        }
    }

    /** Stores {@code settings} and writes them out. A store that cannot be written is logged, not a crash. */
    void save(OverlaySettings settings) {
        for (PauseOn.Condition condition : PauseOn.Condition.values()) {
            preferences.putBoolean(key(condition), settings.pauseOn().contains(condition));
        }
        preferences.putInteger(HP_KEY, settings.hpThresholdPercent());
        try {
            preferences.flush();
        } catch (RuntimeException unwritable) {
            System.err.println("shatterfish: could not write the Overlay's settings: " + unwritable);
        }
    }
}
