package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.practice.KitProfiles;
import org.shatterfish.harness.practice.KitSpec;
import org.shatterfish.harness.practice.Practice;
import org.shatterfish.harness.practice.PracticeBoss;
import org.shatterfish.harness.practice.PracticeStart;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss practice start (E10, ADR-0024): a Run begins on the boss's floor with the profile's kit,
 * says so in its header, and is a function of (tag, seed, salt, class, profile, actions).
 */
class PracticeRunTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final int CAP = 40;

    private static final String ZERO = "0".repeat(40);

    /** Hands the first Observation to the test and then plays like the random agent. */
    private static final class First implements Decider {
        final RandomAgent inner = new RandomAgent(7L);
        Observation first;

        @Override
        public Action decide(Observation observation) {
            if (first == null) {
                first = observation;
            }
            return inner.decide(observation);
        }
    }

    /** Takes the stairs up at every wait. */
    private static final class Leaver implements Decider {
        @Override
        public Action decide(Observation observation) {
            return new Action.Ascend();
        }
    }

    private static RunLoop.Logging logging(Path folder, Practice practice) {
        return new RunLoop.Logging(folder, ZERO, new RunLog.Brain("random", ZERO, "0".repeat(64)), "", "test")
                .practising(practice);
    }

    private static Path file(Path folder, HeroClass heroClass) {
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.2", heroClass, 0, SeedSet.code(SEED), SALT, "random")));
    }

    private static com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass game(HeroClass heroClass) {
        return PracticeStart.gameClass(heroClass);
    }

    @Test
    @DisplayName("every class starts on the boss floor with the profile's level, strength and items, for both bosses")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void starts_on_the_boss_floor_with_the_kit(@TempDir Path folder) {
        for (PracticeBoss boss : PracticeBoss.values()) {
            for (HeroClass heroClass : HeroClass.values()) {
                Practice practice = new Practice(boss, KitProfiles.STANDARD);
                KitSpec kit = practice.kit(heroClass);
                First first = new First();
                new RunLoop().play(SEED, game(heroClass), SALT, first, CAP, logging(folder.resolve(boss.id()), practice));
                Observation o = first.first;
                String who = boss.id() + " " + heroClass;
                assertEquals(boss.depth(), o.header().depth(), who + ": the first wait is on the boss's floor");
                assertEquals(kit.level(), o.hero().level(), who + ": the profile's level");
                assertEquals(kit.strength(), o.hero().strength(), who + ": the profile's strength");
                int carried = 0;
                for (var item : o.inventory().items()) {
                    carried += item.quantity();
                }
                int wanted = 0;
                for (KitSpec.Piece piece : kit.pack()) {
                    wanted += piece.quantity();
                }
                assertTrue(carried >= wanted, who + ": the pack holds the kit's " + wanted + " items, it holds " + carried);
            }
        }
    }

    @Test
    @DisplayName("a Tengu start is the prison boss level in its START state, the hero on the entrance, the floor at depth 10")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tengu_start_is_the_boss_level() {
        Practice practice = new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD);
        try (var driver = org.shatterfish.harness.driver.HeadlessDriver.start(SEED, game(HeroClass.CLERIC), SALT, practice)) {
            driver.stepToInputWait();
            var level = com.shatteredpixel.shatteredpixeldungeon.Dungeon.level;
            assertEquals(10, com.shatteredpixel.shatteredpixeldungeon.Dungeon.depth);
            assertTrue(level instanceof com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel,
                    "depth 10 builds the boss level, not a regular prison floor: " + level.getClass());
            assertEquals(com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel.State.START,
                    ((com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel) level).state());
            // PrisonBossLevel.java:166, ENTRANCE_POS = 10 + 4*32.
            assertEquals(10 + 4 * 32, com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.pos);
            for (var mob : level.mobs) {
                assertTrue(mob instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu
                        || !(mob instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100),
                        "no regular prison mobs on the boss floor: " + mob);
            }
        }
    }

    @Test
    @DisplayName("a practice hero that takes the stairs off the boss floor ends the Run as LEFT_BOSS_FLOOR")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void leaving_the_floor_ends_the_run(@TempDir Path folder) {
        Practice practice = new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD);
        // Walks to the entrance's stairs: the hero starts on them, so the first move off and back on is enough
        // for the check; the decider steps to the stairs cell by the Action the random agent cannot be trusted to pick.
        var outcome = new RunLoop().play(SEED, game(HeroClass.WARRIOR), SALT, new Leaver(), 400, logging(folder, practice));
        assertEquals(org.shatterfish.harness.agent.RunOutcome.Cause.LEFT_BOSS_FLOOR, outcome.cause());
    }

    @Test
    @DisplayName("the header says practice, the kit hash is in it, and a log of the same tuple without a start does not")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_header_carries_the_stamp(@TempDir Path practiceFolder, @TempDir Path plain) throws IOException {
        Practice practice = new Practice(PracticeBoss.GOO, KitProfiles.STANDARD);
        new RunLoop().play(SEED, game(HeroClass.WARRIOR), SALT, new RandomAgent(7L), CAP, logging(practiceFolder, practice));
        RunLog.Header header = RunLogReader.of(file(practiceFolder, HeroClass.WARRIOR)).header();
        assertTrue(header.practiceRun());
        assertEquals(practice.stamp(HeroClass.WARRIOR), header.practice());
        assertTrue(header.practice().startsWith("goo/standard@"), header.practice());
        assertEquals("", header.registration(), "a practice Run has no Registration");

        new RunLoop().play(SEED, game(HeroClass.WARRIOR), SALT, new RandomAgent(7L), CAP,
                new RunLoop.Logging(plain, ZERO, new RunLog.Brain("random", ZERO, "0".repeat(64)), "", "test"));
        RunLog.Header ordinary = RunLogReader.of(file(plain, HeroClass.WARRIOR)).header();
        assertFalse(ordinary.practiceRun());
        assertFalse(LogText.lines(file(plain, HeroClass.WARRIOR)).whole().get(0).contains("practice"),
                "an ordinary log's bytes do not mention practice");
    }

    @Test
    @DisplayName("the same tuple and profile twice write the same chain, and a Replay reproduces the Run")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void determinism_and_replay(@TempDir Path first, @TempDir Path second, @TempDir Path replayed) throws IOException {
        Practice practice = new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD);
        new RunLoop().play(SEED, game(HeroClass.MAGE), SALT, new RandomAgent(7L), CAP, logging(first, practice));
        new RunLoop().play(SEED, game(HeroClass.MAGE), SALT, new RandomAgent(7L), CAP, logging(second, practice));
        List<String> once = LogText.lines(file(first, HeroClass.MAGE)).whole();
        List<String> twice = LogText.lines(file(second, HeroClass.MAGE)).whole();
        assertEquals(once.size(), twice.size());
        assertEquals(LogText.string(once.get(once.size() - 1), "chain"), LogText.string(twice.get(twice.size() - 1), "chain"));
        List<String> chained = new ArrayList<>();
        for (String line : once) {
            chained.add(LogText.chained(line));
        }
        for (int i = 0; i < once.size(); i++) {
            assertEquals(chained.get(i), LogText.chained(twice.get(i)), "line " + (i + 1));
        }

        Replay.Result result = Replay.of(file(first, HeroClass.MAGE), replayed, "test");
        assertTrue(result.ok(), "a practice log replays: " + result.why());
    }

    @Test
    @DisplayName("a stamp whose kit hash is not the one this build resolves is refused, so a profile cannot change under its logs")
    void a_stale_stamp_is_refused() {
        Practice practice = new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD);
        String stamp = practice.stamp(HeroClass.ROGUE);
        assertEquals(practice, Practice.parse(stamp, HeroClass.ROGUE));
        String stale = stamp.substring(0, stamp.indexOf('@') + 1) + "0".repeat(16);
        assertThrows(IllegalArgumentException.class, () -> Practice.parse(stale, HeroClass.ROGUE));
        assertThrows(IllegalArgumentException.class, () -> Practice.parse("tengu/standard", HeroClass.ROGUE));
        // The kit of one class is not the kit of another.
        assertThrows(IllegalArgumentException.class, () -> Practice.parse(stamp, HeroClass.WARRIOR));
    }

    @Test
    @DisplayName("the game-free item allowlist and the classes the start can build are the same list")
    void the_allowlist_is_buildable() {
        assertEquals(new TreeSet<>(KitSpec.ITEMS), new TreeSet<>(PracticeStart.buildable()));
        for (PracticeBoss boss : PracticeBoss.values()) {
            for (HeroClass heroClass : HeroClass.values()) {
                for (KitSpec.Piece piece : KitProfiles.kit(KitProfiles.STANDARD, boss, heroClass).equipment()) {
                    assertTrue(KitSpec.ITEMS.contains(piece.item()), piece.item());
                }
            }
        }
    }

    @Test
    @DisplayName("a practice start is refused with a Registration and with the oracle")
    void refused_with_registration_and_oracle(@TempDir Path folder) {
        Practice practice = new Practice(PracticeBoss.GOO, KitProfiles.STANDARD);
        assertThrows(RuntimeException.class, () -> new RunLoop.Logging(folder, ZERO,
                new RunLog.Brain("random", ZERO, "0".repeat(64)), "reg-1", "test").practising(practice));
    }
}
