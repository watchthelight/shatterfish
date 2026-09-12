package org.shatterfish.harness.boot;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Profile: what a Run inherits, and the stamp that says which version prepared it.
 *
 * <p>A Run's tuple means nothing if the world around it drifts, which is what issue #70 is: story
 * 1.14 found the same seed and chooser giving different Runs. This holds the part of that ADR-0007
 * assigns here — the settings a Run declares, the history it starts with, and the refusal to play
 * against a directory some other version prepared.
 */
class ProfileTest {

    @Test
    @DisplayName("a Run declares its settings, whatever the process did before it")
    void the_settings_are_the_run_s() throws IOException {
        // A process that played under other settings must not leave them to the next Run.
        SPDSettings.interfaceSize(2);
        SPDSettings.intro(true);

        Path directory = Files.createTempDirectory("shatterfish-profile-test");
        Profile.prepare(HeadlessBoot.ensure(), directory);

        assertEquals(0, SPDSettings.interfaceSize(), "the compact interface, which a Run can play on");
        assertTrue(!SPDSettings.intro(), "and no intro");
    }

    @Test
    @DisplayName("a Profile stamps the directory with the version that prepared it")
    void the_stamp_is_written() throws IOException {
        Path directory = Files.createTempDirectory("shatterfish-profile-test");
        Profile profile = Profile.prepare(HeadlessBoot.ensure(), directory);

        Path stamp = directory.resolve(Profile.VERSION_FILE);
        assertTrue(Files.exists(stamp), "the directory says who prepared it");
        assertTrue(Files.readString(stamp, StandardCharsets.UTF_8)
                .contains("shatterfish-profile-version=" + Profile.VERSION), "and which version");
        assertEquals(directory, profile.directory());
    }

    @Test
    @DisplayName("a directory another version prepared is refused, naming both versions")
    void a_profile_of_another_version_is_refused() throws IOException {
        Path directory = Files.createTempDirectory("shatterfish-profile-test");
        Files.writeString(directory.resolve(Profile.VERSION_FILE),
                "shatterfish-profile-version=" + (Profile.VERSION + 1) + System.lineSeparator(),
                StandardCharsets.UTF_8);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> Profile.prepare(HeadlessBoot.ensure(), directory));
        assertTrue(refused.getMessage().contains(String.valueOf(Profile.VERSION + 1))
                        && refused.getMessage().contains(String.valueOf(Profile.VERSION)),
                "a Run against it would not be the Run that was recorded: " + refused.getMessage());
    }

    @Test
    @DisplayName("a stamp that names no version is refused rather than guessed at")
    void a_stamp_without_a_version() throws IOException {
        Path directory = Files.createTempDirectory("shatterfish-profile-test");
        Files.writeString(directory.resolve(Profile.VERSION_FILE), "written by something else",
                StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> Profile.prepare(HeadlessBoot.ensure(), directory));
    }

    @Test
    @DisplayName("one tuple, one Run: the same seed and salt draw the same numbers twice")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void one_tuple_one_run() {
        // What issue #70 is about. Two Runs of one tuple in one process, each reading the game's
        // own draws at each wait rather than playing: what the game draws is what a Brain's choices
        // would be made against, so if these agree the tuple determines the Run's randomness.
        List<Integer> first = drawsOfARun(4242L, 0x5A17L);
        List<Integer> second = drawsOfARun(4242L, 0x5A17L);
        assertEquals(first, second, "two Runs of one tuple");

        List<Integer> salted = drawsOfARun(4242L, 0x5A18L);
        assertTrue(!first.equals(salted), "and a different salt is a different stream");
    }

    /** The numbers the game draws at each of a Run's first waits, with nothing played. */
    private static List<Integer> drawsOfARun(long seed, long salt) {
        org.shatterfish.harness.driver.HeadlessDriver driver =
                org.shatterfish.harness.driver.HeadlessDriver.start(seed, HeroClass.WARRIOR, salt);
        try {
            List<Integer> drawn = new ArrayList<>();
            for (int wait = 0; wait < 4; wait++) {
                driver.stepToInputWait();
                for (int draw = 0; draw < 8; draw++) {
                    drawn.add(com.watabou.utils.Random.Int(1_000_000));
                }
                // Something has to happen or the next wait never comes; resting is the cheapest
                // input there is, and it is the same input in both Runs.
                com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.rest(false);
                org.shatterfish.harness.driver.HeadlessDriver.actionHandedOver();
            }
            return drawn;
        } finally {
            driver.close();
        }
    }
}
