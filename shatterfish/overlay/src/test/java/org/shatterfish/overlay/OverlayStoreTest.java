package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.boot.MemoryPreferences;
import org.shatterfish.overlay.PauseOn.Condition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Overlay's settings are kept between games and a bad store never fails the screen (story 8.4, FR-45): the
 * values go to a libGDX store under Overlay-prefixed keys, a new store over the same file reads them back, and
 * a key that is missing, unparseable or out of range is the default of {@link OverlaySettings} for that key
 * alone.
 */
class OverlayStoreTest {

    private static OverlaySettings mixed() {
        return new OverlaySettings(EnumSet.of(Condition.STAIRS, Condition.NEW_ENEMY), 45);
    }

    @Test
    @DisplayName("an empty store reads as the defaults")
    void an_empty_store_is_the_defaults() {
        assertEquals(OverlaySettings.DEFAULTS, new OverlayStore(new MemoryPreferences()).load());
    }

    @Test
    @DisplayName("what is saved is read back, and every key is the Overlay's own")
    void saved_values_come_back_under_prefixed_keys() {
        MemoryPreferences preferences = new MemoryPreferences();
        new OverlayStore(preferences).save(mixed());

        assertEquals(mixed(), new OverlayStore(preferences).load());
        Map<String, ?> keys = preferences.get();
        assertEquals(Condition.values().length + 1, keys.size(), "one key per condition and the threshold: " + keys);
        for (String key : keys.keySet()) {
            assertTrue(key.startsWith(OverlayStore.PREFIX), key + " is not Overlay-prefixed");
        }
    }

    @Test
    @DisplayName("a restart reads the values back: a new store over the same file")
    void a_restart_reads_the_values_back(@TempDir Path folder) {
        Path file = folder.resolve("nested").resolve(OverlayStore.FILE_NAME);
        OverlayStore.at(file).save(mixed());
        assertTrue(Files.exists(file), "the first save created the file");

        assertEquals(mixed(), OverlayStore.at(file).load(), "read by a store that never saw the first");
        OverlayStore.at(file).save(OverlaySettings.all().withHpThreshold(99));
        assertEquals(OverlaySettings.all().withHpThreshold(99), OverlayStore.at(file).load());
    }

    @Test
    @DisplayName("a missing file reads as the defaults")
    void a_missing_file_is_the_defaults(@TempDir Path folder) {
        assertEquals(OverlaySettings.DEFAULTS, OverlayStore.at(folder.resolve("none.xml")).load());
    }

    @Test
    @DisplayName("a corrupt file reads as the defaults and does not throw")
    void a_corrupt_file_is_the_defaults(@TempDir Path folder) throws IOException {
        Path file = folder.resolve(OverlayStore.FILE_NAME);
        Files.writeString(file, "this is <not> a properties file \u0000 \\u12 ===");
        assertEquals(OverlaySettings.DEFAULTS, OverlayStore.at(file).load());
    }

    @Test
    @DisplayName("a bad key is the default for that key alone")
    void a_bad_key_falls_back_alone() {
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.putBoolean(OverlayStore.key(Condition.STAIRS), true);
        preferences.putString(OverlayStore.key(Condition.ITEM_USE), "banana");
        preferences.putString(OverlayStore.HP_KEY, "lots");
        OverlaySettings read = new OverlayStore(preferences).load();
        assertEquals(EnumSet.of(Condition.STAIRS), EnumSet.copyOf(read.pauseOn()), "the good key stands, the bad one is off");
        assertEquals(OverlaySettings.HP_DEFAULT, read.hpThresholdPercent());
    }

    @Test
    @DisplayName("a threshold outside its range is the default")
    void an_out_of_range_threshold_is_the_default() {
        for (int bad : new int[] {0, -5, 100, 5000}) {
            MemoryPreferences preferences = new MemoryPreferences();
            preferences.putInteger(OverlayStore.HP_KEY, bad);
            assertEquals(OverlaySettings.HP_DEFAULT, new OverlayStore(preferences).load().hpThresholdPercent(),
                    "stored " + bad);
        }
        MemoryPreferences edge = new MemoryPreferences();
        edge.putInteger(OverlayStore.HP_KEY, OverlaySettings.HP_MAX);
        assertEquals(OverlaySettings.HP_MAX, new OverlayStore(edge).load().hpThresholdPercent());
    }

    @Test
    @DisplayName("a store that cannot be written does not throw")
    void an_unwritable_store_does_not_throw(@TempDir Path folder) throws IOException {
        // A directory where the file should be: the flush fails and the screen carries on.
        Path file = Files.createDirectory(folder.resolve(OverlayStore.FILE_NAME));
        OverlayStore.at(file).save(mixed());
    }
}
