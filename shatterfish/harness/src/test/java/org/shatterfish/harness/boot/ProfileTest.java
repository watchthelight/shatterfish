package org.shatterfish.harness.boot;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import org.shatterfish.harness.rng.Mix;
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

    /** The five classes a player earns, which the Profile grants (story 3.1). */
    private static final List<Badges.Badge> UNLOCKS = List.of(Badges.Badge.UNLOCK_MAGE,
            Badges.Badge.UNLOCK_ROGUE, Badges.Badge.UNLOCK_HUNTRESS, Badges.Badge.UNLOCK_DUELIST,
            Badges.Badge.UNLOCK_CLERIC);

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

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
    @DisplayName("a preference the game flipped in one Run does not reach the next")
    void the_preferences_are_fresh() throws IOException {
        // The game turns this off by itself when a player drags the waterskin out of a quickslot
        // (core/.../ui/QuickSlotButton.java:390), and a Run that inherited it starts with a
        // different hero screen: the two-JVM determinism test found this on its first full build,
        // after the thousand random Runs had dragged the waterskin about.
        SPDSettings.quickslotWaterskin(false);
        SPDSettings.vaultInjureWarns(3);

        Profile.prepare(HeadlessBoot.ensure(), Files.createTempDirectory("shatterfish-profile-test"));

        assertTrue(SPDSettings.quickslotWaterskin(), "the waterskin is quickslotted, as for a new player");
        assertEquals(0, SPDSettings.vaultInjureWarns(), "and the vault has warned nobody yet");
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

        // The journal is the half that actually changes the dungeon: the guide pages a floor
        // scatters are the ones the player has not found (core/.../levels/RegularLevel.java:561-589),
        // so a page found in one Run and remembered into the next is a different floor from the
        // same seed. The game's loader will not re-read it, so the Profile restores it directly.
        Document.ADVENTURERS_GUIDE.findPage(Document.GUIDE_SEARCHING);
        assertTrue(Document.ADVENTURERS_GUIDE.isPageFound(Document.GUIDE_SEARCHING),
                "a Run that read a page");

        Profile.prepare(HeadlessBoot.ensure(), Files.createTempDirectory("shatterfish-profile-test"));

        assertEquals(0, Badges.totalUnlocked(false), "and the next Run starts with nothing");
        assertTrue(!Document.ADVENTURERS_GUIDE.isPageFound(Document.GUIDE_SEARCHING),
                "and with none of the pages the last Run found");
    }

    @Test
    @DisplayName("every hero class a seed set names can be selected, because the Profile grants the badges")
    void the_six_classes_can_be_started() throws IOException {
        // The empty history the Profile installs is a profile that has played nothing, and five of
        // the six classes are earned (core/.../actors/hero/HeroClass.java:330-347). The seed sets
        // name all six, so a Profile that did not grant the badges would commit hundreds of triples
        // for heroes no Run could start as -- a set that lies rather than a set that is held out.
        // Badges.reset leaves the global badges alone (issue #117), so the five are taken away
        // here: without this the test would pass on whatever an earlier Run in this process left
        // behind rather than on what the Profile does.
        for (Badges.Badge held : UNLOCKS) {
            Badges.disown(held);
        }
        assertTrue(!Badges.isUnlocked(Badges.Badge.UNLOCK_MAGE),
                "a profile that has played nothing has not earned the Mage");
        // The state a Run leaves behind: the game sets this in initSeed and never clears it, and
        // Badges.unlock refuses a LOCAL badge while it stands (core/.../Badges.java:1209-1214), so
        // without the Profile clearing it the grant below is a silent no-op.
        Dungeon.customSeedText = "ZZZ-ZZZ-ZZZ";

        Profile.prepare(HeadlessBoot.ensure(), Files.createTempDirectory("shatterfish-profile-classes"));

        assertEquals("", Dungeon.customSeedText, "the Profile clears the seed text the last Run left");

        for (Badges.Badge badge : UNLOCKS) {
            assertTrue(Badges.isUnlocked(badge), badge + " is not granted, and a seed set names its class");
        }
        for (HeroClass heroClass : HeroClass.values()) {
            assertTrue(heroClass.isUnlocked(),
                    heroClass + " cannot be selected at the class screen, and the seed sets name it");
        }
    }

    @Test
    @DisplayName("the stack is the Run's from the moment the floor is built, not from the first wait")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_stack_is_owned_from_the_start() {
        // The game's own initialisation ends by seeding the base generator from the system
        // (core/.../Dungeon.java:254), so anything drawn while the floor is built and before the
        // first wait would be the moment's and not the tuple's. Two Runs of one tuple are asked for
        // numbers before either has had a wait: if the Run owns the stack from init onward, both
        // are at the same place in the same stream and answer alike.
        //
        // The first Run in a process is not one of the two: it consumes exactly one draw more than
        // every Run after it, whatever the tuple, which is a warm-up somewhere in the game rather
        // than anything drawn from entropy — an entropy-seeded floor would differ in every number,
        // not by one position in the same stream. That single draw is part of what issue #70 has
        // left, and story 1.16 named it: the guidebook was placed from an unseeded generator, and its
        // placement loop retried a different number of times in every process.
        streamAtTheStart(4242L, 0x5A17L);
        assertEquals(streamAtTheStart(4242L, 0x5A17L), streamAtTheStart(4242L, 0x5A17L),
                "two Runs of one tuple, before either has been asked for an Action");
        assertTrue(!streamAtTheStart(4242L, 0x5A17L).equals(streamAtTheStart(4242L, 0x5A18L)),
                "and another salt draws otherwise");
    }

    @Test
    @DisplayName("each wait of a real Run draws from the mix of its salt and that wait")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void every_wait_of_a_run_draws_from_its_own_seed() {
        // MixTestVectorTest holds RngControl to this with no game running; this holds the Run to it,
        // which is what the published methodology promises a skeptic they can check.
        HeadlessDriver driver = HeadlessDriver.start(4242L, HeroClass.WARRIOR, RUN_SALT);
        try {
            for (int wait = 0; wait < 4; wait++) {
                long k = driver.stepToInputWait().waitIndex();
                List<Integer> drawn = new ArrayList<>();
                for (int draw = 0; draw < 8; draw++) {
                    drawn.add(com.watabou.utils.Random.Int(1_000_000));
                }
                assertEquals(fromSeed(Mix.mix(RUN_SALT, k), 8), drawn,
                        "the draws at wait " + k + " are the mix's, not the last wait's");
                com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.rest(false);
                HeadlessDriver.actionHandedOver();
            }
        } finally {
            driver.close();
        }
    }

    /** The numbers on top of the stack the moment a Run has been started and not yet played. */
    private static List<Integer> streamAtTheStart(long seed, long salt) {
        HeadlessDriver driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, salt);
        try {
            List<Integer> drawn = new ArrayList<>();
            for (int draw = 0; draw < 8; draw++) {
                drawn.add(com.watabou.utils.Random.Int(1_000_000));
            }
            return drawn;
        } finally {
            driver.close();
        }
    }

    /** What a generator seeded {@code seed} gives, through the game's own push. */
    private static List<Integer> fromSeed(long seed, int take) {
        com.watabou.utils.Random.pushGenerator(seed);
        try {
            List<Integer> drawn = new ArrayList<>();
            for (int draw = 0; draw < take; draw++) {
                drawn.add(com.watabou.utils.Random.Int(1_000_000));
            }
            return drawn;
        } finally {
            com.watabou.utils.Random.popGenerator();
        }
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

    // There was a test here that asserted the divergence: two Runs of one tuple seeing different
    // screens, so that story 1.16 would meet a failing test the day it succeeded. It is gone,
    // because the divergence is intermittent — the mutation battery caught it agreeing with itself
    // on a run where the two Runs happened to match, which would have failed the build for a reason
    // that had nothing to do with the change. A test that asserts a defect and flakes is worse than
    // a defect written down: the divergence is in issue #70, in ADR-0007's amendment and on the
    // methodology page, with the evidence and what is known about the cause.

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

}
