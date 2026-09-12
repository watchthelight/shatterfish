package org.shatterfish.harness.boot;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.watabou.utils.Bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
    @DisplayName("a Run starts with none of the badges the last one earned")
    void the_history_is_empty() throws IOException {
        // The game reads its own history: a snake stops dodging after four misses only once the
        // first boss has been slain (core/.../actors/mobs/Snake.java:66). So a Run that inherited
        // the badges of the Run before it would be playing a different game under the same tuple.
        HashSet<Badges.Badge> earned = new HashSet<>();
        earned.add(Badges.Badge.LEVEL_REACHED_1);
        Bundle bundle = new Bundle();
        Badges.store(bundle, earned);
        Badges.loadLocal(bundle);
        assertTrue(Badges.totalUnlocked(false) > 0, "a Run that earned something");

        Profile.prepare(HeadlessBoot.ensure(), Files.createTempDirectory("shatterfish-profile-test"));

        assertEquals(0, Badges.totalUnlocked(false), "and the next Run starts with nothing");
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
    @DisplayName("two Runs of one tuple draw the same numbers at the same waits")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void one_tuple_one_stream() {
        // What this story owns, and no more. The numbers the game draws from each wait onwards are
        // a function of the salt and the wait index, so two Runs of one tuple draw alike and two
        // salts do not. Whether the two Runs then *see* the same screens is a different question,
        // and the answer today is no — see the test below.
        List<String> first = drawsOfARun(4242L, 0x5A17L);
        List<String> second = drawsOfARun(4242L, 0x5A17L);
        assertEquals(first, second, "two Runs of one tuple draw one stream");

        List<String> salted = drawsOfARun(4242L, 0x5A18L);
        assertTrue(!first.equals(salted), "and another salt is another stream");
    }

    @Test
    @DisplayName("two Runs of one tuple still see different screens, which story 1.16 owns")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void one_tuple_two_screens_for_now() {
        // This asserts a defect, deliberately, because the alternative is a story that claims to
        // have closed issue #70 and has closed half of it. With the salt controlling every draw
        // from Dungeon.init onward and the journal emptied per Run, two Runs of one tuple still
        // put one floor-one item on cells a step apart, and the cell creeps by one with each Run in
        // a process. That is not randomness — it is something counted rather than drawn, which is
        // the ground story 1.16 stands on (identity order and the two-JVM determinism test).
        //
        // When 1.16 lands this test fails, and that failure is the good news: replace it with the
        // equality it was hiding.
        List<String> first = screensOf(4242L, 0x5A17L);
        List<String> second = screensOf(4242L, 0x5A17L);
        assertTrue(!first.equals(second), "if these now agree, issue #70 is closed: delete this test"
                + " and assert the equality instead");
    }

    /** The numbers the game draws at the head of each of a Run's first waits. */
    private static List<String> drawsOfARun(long seed, long salt) {
        HeadlessDriver driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, salt);
        try {
            List<String> drawn = new ArrayList<>();
            for (int wait = 0; wait < 4; wait++) {
                driver.stepToInputWait();
                StringBuilder atThisWait = new StringBuilder();
                for (int draw = 0; draw < 8; draw++) {
                    atThisWait.append(com.watabou.utils.Random.Int(1_000_000)).append(' ');
                }
                drawn.add(atThisWait.toString());
                com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.rest(false);
                HeadlessDriver.actionHandedOver();
            }
            return drawn;
        } finally {
            driver.close();
        }
    }

    /**
     * The screen at each wait of a Run played by pressing the same button every time. Waiting is
     * the one input always offered and always meaning the same thing, so two Runs given it are two
     * Runs given one Action list — which is what a tuple is.
     */
    private static List<String> screensOf(long seed, long salt) {
        HeadlessDriver driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, salt);
        try {
            List<String> screens = new ArrayList<>();
            ActionExecutor executor = new ActionExecutor();
            for (int wait = 0; wait < 12; wait++) {
                if (driver.stepToInputWait().reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                    break;
                }
                Observation observation = new Observer().observe();
                screens.add(observation.hash());
                if (!(executor.execute(observation, new Action.Wait()) instanceof Outcome.Applied)) {
                    break;
                }
            }
            return screens;
        } finally {
            driver.close();
        }
    }
}
