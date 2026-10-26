package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.watabou.utils.Bundle;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubRow;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The differential test of the scrubber (story 6.2, FR-13): a scrubbed copy of the Run at a mid-fight wait shows the
 * Brain exactly the Observation the live Run does, section by section, and differs from the live Run in the hidden
 * state of every row of the scrub table that the snapshot holds. Two different samples give two different copies that
 * look alike on screen, and a scrubber missing a row fails the second half of this test by naming that row.
 *
 * <p>The snapshots are those of Warriors of the {@code smoke} set (never {@code goo} or {@code holdout}) at the first
 * wait where an enemy is in view, played by the real Brain. Per fixture the test restores the live snapshot, observes,
 * restores each scrubbed copy, steps to its wait, observes, and compares the sections' hashes.
 */
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class RedeterminationDifferentialTest {

    private static final long SALT = 0x5A17_5A17L;
    /** How many Warriors of the smoke set to play to a fight. */
    private static final int SEEDS = 2;
    private static final int WAIT_LIMIT = 1_500;
    /** Waits between two fixtures of one Run. */
    private static final long GAP = 30;
    /** Fixtures per Run, and the Runs: the rows a snapshot holds differ with the floor. */
    private static final int PER_RUN = 2;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    // --- the fixture

    /** A Run stopped at a wait with an enemy in view, and its live snapshot. */
    private record Fixture(long seed, SnapshotStore store, SnapshotHandle live, Observation shown) {
        /** The cells of the actors the screen shows: the mobs the AI and health row re-samples. */
        Set<Integer> viewed() {
            Set<Integer> cells = new java.util.HashSet<>();
            shown.actors().actors().forEach(a -> cells.add(a.cell()));
            return cells;
        }
    }

    private Fixture fixture(long seed) {
        return fixture(seed, 0);
    }

    /** The {@code index}th fight of the Run (counting from 0, at least {@link #GAP} waits apart) that the scrub accepts. */
    private Fixture fixture(long seed, int index) {
        if (driver != null) {
            driver.close();
        }
        driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, SALT);
        int accepted = 0;
        long lastWait = -GAP;
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        for (int i = 0; i < WAIT_LIMIT; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                continue;
            }
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "the Run reaches a fight before it ends: " + halt);
            Observation observation = new Observer().observe();
            if (fightable(observation) && halt.waitIndex() - lastWait >= GAP) {
                SnapshotStore store = new SnapshotStore();
                try {
                    SnapshotHandle live = store.take(driver);
                    new SnapshotScrubber(store).scrub(live, sample(1));
                    if (accepted++ == index) {
                        return new Fixture(seed, store, live, observation);
                    }
                    lastWait = halt.waitIndex();
                } catch (IllegalStateException | Scrub.Refused refused) {
                    // a window in front, or a snapshot the scrub declines (a chest in view): the next wait
                }
            }
            Action action = brain.decide(observation);
            executor.execute(observation, action);
        }
        throw new AssertionError("seed " + seed + " showed no enemy in " + WAIT_LIMIT + " waits");
    }

    private static boolean fightable(Observation observation) {
        return observation.actors().actors().stream().anyMatch(a -> a.alignment() == Alignment.ENEMY);
    }

    private static List<Long> smokeWarriors() throws IOException {
        String text = Files.readString(RealBrain.root().resolve("seeds").resolve("smoke.json"));
        Matcher entry = Pattern.compile("\"heroClass\":\"WARRIOR\",\"seed\":(\\d+)").matcher(text);
        List<Long> seeds = new ArrayList<>();
        while (seeds.size() < SEEDS && entry.find()) {
            seeds.add(Long.parseLong(entry.group(1)));
        }
        assertEquals(SEEDS, seeds.size(), "enough Warriors in the smoke set");
        return seeds;
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    /** Back to the live Run's wait and what it shows. */
    private Observation liveAgain(Fixture f) {
        f.store.restore(f.live, driver);
        HeadlessDriver.Halt halt = driver.stepToInputWait();
        assertEquals(f.live.k(), halt.waitIndex(), "the live snapshot restores to its own wait");
        return new Observer().observe();
    }

    private Observation copyOf(Fixture f, SnapshotHandle copy) {
        f.store.restoreScrubbed(copy, driver);
        HeadlessDriver.Halt halt = driver.stepToInputWait();
        assertEquals(copy.k(), halt.waitIndex(), "the scrubbed copy stands at the live wait");
        return new Observer().observe();
    }

    private static void assertSameScreen(String what, Observation live, Observation copy) {
        Map<String, String> a = live.sectionHashes();
        Map<String, String> b = copy.sectionHashes();
        for (Map.Entry<String, String> section : a.entrySet()) {
            assertEquals(section.getValue(), b.get(section.getKey()), what + ": section " + section.getKey()
                    + " of the scrubbed copy differs from the live Run's\nlive:\n" + live.json() + "\ncopy:\n" + copy.json());
        }
        assertEquals(live.hash(), copy.hash(), what + ": the whole Observation");
    }

    // --- the hidden state a row replaces, read from the loaded game

    /** The hidden facts of the world the driver stands in, by the row that replaces them. */
    private static Map<ScrubRow, String> world(HeadlessDriver driver, long wait, Fixture fixture) {
        Set<Integer> inView = fixture.viewed();
        Map<ScrubRow, String> out = new java.util.EnumMap<>(ScrubRow.class);
        StringBuilder images = new StringBuilder();
        for (Generator.Category category : List.of(Generator.Category.POTION, Generator.Category.SCROLL, Generator.Category.RING)) {
            for (Class<?> type : category.classes) {
                Object made = Reflection.newInstance(type);
                images.append(type.getSimpleName()).append('=').append(((Item) made).image()).append(';');
            }
        }
        out.put(ScrubRow.POTION_SCROLL_RING_CLASSES, images.toString());
        // Keyed by place, class-blind, so that the rows that move or rename items do not show as a changed level.
        StringBuilder levels = new StringBuilder();
        int slot = 0;
        for (Item item : Dungeon.hero.belongings.backpack.items) {
            levels.append("pack").append(slot++).append('=').append(item.level()).append(item.cursed ? "c" : "").append('\n');
        }
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            int index = 0;
            for (Item item : heap.items) {
                levels.append("heap").append(heap.pos).append('.').append(index++).append('=').append(item.level())
                        .append(item.cursed ? "c" : "").append('\n');
            }
        }
        out.put(ScrubRow.ITEM_LEVEL_AND_CURSE, levels.toString());
        List<String> mobs = new ArrayList<>();
        List<String> ai = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            mobs.add(mob.getClass().getSimpleName() + "@" + mob.pos);
            if (inView.contains(mob.pos)) {
                Bundle stored = new Bundle();
                mob.storeInBundle(stored);
                org.json.JSONObject all = new org.json.JSONObject(stored.toString());
                StringBuilder picked = new StringBuilder(mob.getClass().getSimpleName() + "@" + mob.pos);
                for (String key : List.of("HP", "state", "target", "enemy_id", "seen")) {
                    picked.append(' ').append(key).append('=').append(all.opt(key));
                }
                ai.add(picked.toString());
            }
        }
        java.util.Collections.sort(mobs);
        java.util.Collections.sort(ai);
        out.put(ScrubRow.MOBS_OUT_OF_VIEW, mobs.toString());
        out.put(ScrubRow.MOB_AI_AND_HP, ai.toString());
        // Only the cells the screen leaves unknown: the other rows also change the map of cells the player knows.
        StringBuilder unknown = new StringBuilder();
        List<org.shatterfish.api.Fog> fog = fixture.shown.map().fog();
        for (int cell = 0; cell < fog.size(); cell++) {
            if (fog.get(cell) == org.shatterfish.api.Fog.UNKNOWN) {
                unknown.append(Dungeon.level.map[cell]).append(Dungeon.level.visited[cell] ? 'v' : '-').append(',');
            }
        }
        out.put(ScrubRow.UNKNOWN_CELLS, unknown.toString());
        // CONTAINER_CONTENTS: what lies in the heaps the player has seen. UNSEEN_ITEMS: the heaps on known cells it has not.
        StringBuilder heaps = new StringBuilder();
        StringBuilder unseen = new StringBuilder();
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            boolean known = Dungeon.level.visited[heap.pos] || Dungeon.level.mapped[heap.pos];
            if (heap.seen) {
                heaps.append(heap.pos).append(':').append(heap.type).append(':').append(heap.items.size());
                if (heap.type != Heap.Type.HEAP) {
                    for (Item item : heap.items) {
                        heaps.append(',').append(item.getClass().getSimpleName());
                    }
                }
                heaps.append(';');
            } else if (known) {
                unseen.append(heap.pos).append(';');
            }
        }
        out.put(ScrubRow.CONTAINER_CONTENTS, heaps.toString());
        out.put(ScrubRow.UNSEEN_ITEMS, unseen.toString());
        int secrets = 0;
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            boolean known = Dungeon.level.visited[cell] || Dungeon.level.mapped[cell];
            int terrain = Dungeon.level.map[cell];
            if (known && (terrain == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.SECRET_DOOR
                    || terrain == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.SECRET_TRAP)) {
                secrets++;
            }
            com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap trap = Dungeon.level.traps.get(cell);
            if (known && trap != null && !trap.visible) {
                secrets++;
            }
        }
        out.put(ScrubRow.SECRET_TRAPS_AND_DOORS, Integer.toString(secrets));
        StringBuilder buffs = new StringBuilder();
        for (Buff buff : Dungeon.hero.buffs()) {
            Bundle stored = new Bundle();
            buff.storeInBundle(stored);
            buffs.append(buff.getClass().getName()).append(stored).append(';');
        }
        out.put(ScrubRow.HERO_HIDDEN_BUFFS, buffs.toString());
        out.put(ScrubRow.DUNGEON_SEED, Long.toString(Dungeon.seed));
        Bundle generator = new Bundle();
        Generator.storeInBundle(generator);
        out.put(ScrubRow.GENERATOR_AND_QUEUES, generator.toString());
        out.put(ScrubRow.ROLLOUT_SALT, Long.toString(driver.seedFor(wait)));
        return out;
    }

    /** Whether the two worlds differ in what {@code row} replaces; for levels, in the places both worlds hold an item. */
    private static boolean differs(ScrubRow row, Map<ScrubRow, String> a, Map<ScrubRow, String> b) {
        if (row != ScrubRow.ITEM_LEVEL_AND_CURSE) {
            return !a.get(row).equals(b.get(row));
        }
        Map<String, String> left = lines(a.get(row));
        Map<String, String> right = lines(b.get(row));
        for (Map.Entry<String, String> line : left.entrySet()) {
            if (right.containsKey(line.getKey()) && !right.get(line.getKey()).equals(line.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> lines(String text) {
        Map<String, String> out = new java.util.HashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return out;
    }

    private static List<ScrubRow> equalRows(Map<ScrubRow, String> a, Map<ScrubRow, String> b) {
        List<ScrubRow> equal = new ArrayList<>();
        for (ScrubRow row : READABLE) {
            if (!differs(row, a, b)) {
                equal.add(row);
            }
        }
        return equal;
    }

    /** The rows that change something in this snapshot under at least one of three samples: the ones it holds. */
    private static Set<ScrubRow> holds(Fixture f) {
        Set<ScrubRow> rows = EnumSet.noneOf(ScrubRow.class);
        SnapshotScrubber survey = new SnapshotScrubber(f.store);
        for (long n = 1; n <= 3; n++) {
            survey.scrub(f.live, sample(n));
            for (ScrubRow row : READABLE) {
                if (survey.lastReport().changed(row) > 0) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** The rows whose hidden state the snapshot holds to be replaced and which the test can read from the game. */
    private static final Set<ScrubRow> READABLE = EnumSet.complementOf(EnumSet.of(ScrubRow.STEALTHY_MIMIC_KEPT));

    // --- tests

    private interface Check {
        void on(Fixture f) throws IOException;
    }

    /** Runs {@code check} on each fight of each Run, one Run at a time. */
    private void forEachFixture(Check check) throws IOException {
        for (long seed : smokeWarriors()) {
            for (int index = 0; index < PER_RUN; index++) {
                check.on(fixture(seed, index));
            }
        }
    }

    @Test
    @DisplayName("every row on: the scrubbed copy shows the live Run's Observation, section by section")
    void all_rows_show_the_same_screen() throws IOException {
        forEachFixture(f -> {
            Observation live = liveAgain(f);
            assertEquals(f.shown.hash(), live.hash(), "the live restore reads as the snapshot's wait");
            SnapshotScrubber scrubber = new SnapshotScrubber(f.store);
            for (long n = 1; n <= 3; n++) {
                SnapshotHandle copy = scrubber.scrub(f.live, sample(n));
                assertTrue(copy.scrubbed());
                assertTrue(f.store.madeScrubbed(copy));
                assertSameScreen("seed " + f.seed + " wait " + f.live.k() + " sample " + n, live, copyOf(f, copy));
                assertEquals(live.hash(), liveAgain(f).hash(), "the live Run is untouched by what the copy did");
            }
        });
    }

    @Test
    @DisplayName("each row alone: the scrubbed copy shows the live Run's Observation")
    void each_row_alone_shows_the_same_screen() throws IOException {
        forEachFixture(f -> {
            Observation live = liveAgain(f);
            for (ScrubRow row : ScrubRow.values()) {
                SnapshotScrubber scrubber = new SnapshotScrubber(f.store, EnumSet.of(row));
                SnapshotHandle copy = scrubber.scrub(f.live, sample(1));
                assertSameScreen("seed " + f.seed + " wait " + f.live.k() + " row " + row, live, copyOf(f, copy));
                liveAgain(f);
            }
        });
    }

    @Test
    @DisplayName("the copy differs from the live Run in the hidden state of every row the snapshot holds")
    void the_copy_differs_in_every_row_the_snapshot_holds() throws IOException {
        Set<ScrubRow> covered = EnumSet.noneOf(ScrubRow.class);
        forEachFixture(f -> {
            liveAgain(f);
            Map<ScrubRow, String> liveWorld = world(driver, f.live.k(), f);
            Set<ScrubRow> holds = holds(f);
            System.out.println("REDETERMINE seed " + f.seed + " wait " + f.live.k() + " holds " + holds);
            for (ScrubRow row : READABLE) {
                if (!holds.contains(row)) {
                    continue;
                }
                boolean differs = false;
                for (long n = 1; n <= 3 && !differs; n++) {
                    SnapshotHandle copy = new SnapshotScrubber(f.store, EnumSet.of(row)).scrub(f.live, sample(n));
                    copyOf(f, copy);
                    differs = differs(row, liveWorld, world(driver, f.live.k(), f));
                    liveAgain(f);
                }
                assertTrue(differs, "row " + row + " (seed " + f.seed + ", wait " + f.live.k() + "): the snapshot holds it and the"
                        + " scrubbed copy differs from the live Run in none of what the row replaces");
                covered.add(row);
            }
        });
        System.out.println("REDETERMINE covered " + covered);
        for (ScrubRow row : List.of(ScrubRow.POTION_SCROLL_RING_CLASSES, ScrubRow.MOBS_OUT_OF_VIEW, ScrubRow.MOB_AI_AND_HP,
                ScrubRow.UNKNOWN_CELLS, ScrubRow.DUNGEON_SEED, ScrubRow.GENERATOR_AND_QUEUES, ScrubRow.ROLLOUT_SALT,
                ScrubRow.HERO_HIDDEN_BUFFS)) {
            assertTrue(covered.contains(row), "the fixtures must hold " + row + ", and held only " + covered);
        }
    }

    @Test
    @DisplayName("two samples give two different copies with equal Observations")
    void two_samples_two_copies_one_screen() throws IOException {
        forEachFixture(f -> {
            Observation live = liveAgain(f);
            SnapshotScrubber scrubber = new SnapshotScrubber(f.store);
            SnapshotHandle a = scrubber.scrub(f.live, sample(1));
            SnapshotHandle b = scrubber.scrub(f.live, sample(2));
            assertNotEquals(a.id(), b.id());
            Observation shownA = copyOf(f, a);
            Map<ScrubRow, String> worldA = world(driver, f.live.k(), f);
            Observation shownB = copyOf(f, b);
            Map<ScrubRow, String> worldB = world(driver, f.live.k(), f);
            assertSameScreen("sample 1", live, shownA);
            assertSameScreen("sample 2", live, shownB);
            assertSameScreen("the two copies", shownA, shownB);
            int differing = 0;
            for (ScrubRow row : READABLE) {
                if (differs(row, worldA, worldB)) {
                    differing++;
                }
            }
            assertTrue(differing >= 3, "two samples must give two different worlds, and only " + differing + " row(s) differ");
            assertNotEquals(worldA.get(ScrubRow.DUNGEON_SEED), worldB.get(ScrubRow.DUNGEON_SEED));
        });
    }

    @Test
    @DisplayName("a scrubber with one row removed fails the check, naming that row")
    void an_incomplete_scrubber_names_the_missing_row() throws IOException {
        Set<ScrubRow> everChecked = EnumSet.noneOf(ScrubRow.class);
        forEachFixture(f -> {
            liveAgain(f);
            Map<ScrubRow, String> liveWorld = world(driver, f.live.k(), f);
            // The rows the complete scrubber visibly changes under this sample: the check can name only these.
            copyOf(f, new SnapshotScrubber(f.store).scrub(f.live, sample(1)));
            Map<ScrubRow, String> completeWorld = world(driver, f.live.k(), f);
            liveAgain(f);
            Set<ScrubRow> shown = EnumSet.noneOf(ScrubRow.class);
            for (ScrubRow row : READABLE) {
                if (differs(row, liveWorld, completeWorld)) {
                    shown.add(row);
                }
            }
            for (ScrubRow missing : shown) {
                EnumSet<ScrubRow> rows = EnumSet.allOf(ScrubRow.class);
                rows.remove(missing);
                copyOf(f, new SnapshotScrubber(f.store, rows).scrub(f.live, sample(1)));
                Map<ScrubRow, String> copyWorld = world(driver, f.live.k(), f);
                liveAgain(f);
                AssertionError caught = assertThrows(AssertionError.class, () -> {
                    for (ScrubRow row : shown) {
                        if (!differs(row, liveWorld, copyWorld)) {
                            fail("row " + row + " is held by the snapshot and the scrubbed copy equals the live Run in it");
                        }
                    }
                }, "a scrubber without " + missing + " must fail");
                assertTrue(caught.getMessage().contains("row " + missing + " "), "the failure names " + missing + ": " + caught.getMessage());
                everChecked.add(missing);
            }
        });
        System.out.println("REDETERMINE incomplete scrubbers named " + everChecked);
        assertTrue(everChecked.size() >= 6, "the fixtures showed " + everChecked + "; a missing row must be named for several rows");
    }

    /** Plays the Run to wait {@code k}, optionally scrubs there without restoring, and returns the hashes of the next waits. */
    private List<String> after(long seed, long k, boolean scrub, int waits) {
        if (driver != null) {
            driver.close();
        }
        driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, SALT);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        List<String> hashes = new ArrayList<>();
        boolean reached = false;
        for (int i = 0; i < WAIT_LIMIT && hashes.size() < waits; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                continue;
            }
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                // The Run ended: the twin ends the same way, and that is part of what is compared.
                hashes.add("end:" + halt.reason());
                break;
            }
            Observation observation = new Observer().observe();
            if (halt.waitIndex() == k) {
                SnapshotStore store = new SnapshotStore();
                SnapshotHandle live = store.take(driver);
                if (scrub) {
                    SnapshotScrubber scrubber = new SnapshotScrubber(store);
                    scrubber.scrub(live, sample(1));
                    scrubber.scrub(live, sample(2));
                }
                reached = true;
            }
            if (reached) {
                hashes.add(halt.waitIndex() + ":" + observation.hash());
            }
            executor.execute(observation, brain.decide(observation));
        }
        assertTrue(reached, "the Run reaches wait " + k);
        return hashes;
    }

    @Test
    @DisplayName("scrubbing a snapshot leaves the live Run's statics alone: it plays on as its unscrubbed twin does")
    void scrubbing_leaves_the_live_run_alone() throws IOException {
        long seed = smokeWarriors().get(1);
        Fixture f = fixture(seed, 0);
        long k = f.live.k();
        List<String> scrubbed = after(seed, k, true, 120);
        List<String> twin = after(seed, k, false, 120);
        assertEquals(twin, scrubbed, "the Run after a scrub reads wait for wait as the twin that was never scrubbed");
        assertTrue(scrubbed.size() >= 60, "the comparison covers a stretch of play: " + scrubbed.size());
    }

    @Test
    @DisplayName("heaps the player has seen or not seen: the rows for container contents and unseen items hold and differ")
    void container_and_unseen_rows_differ() throws IOException {
        long seed = smokeWarriors().get(0);
        driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, SALT);
        driver.stepToInputWait();
        com.shatteredpixel.shatteredpixeldungeon.levels.Level level = Dungeon.level;
        List<Integer> near = new ArrayList<>();
        for (int step : new int[] {-1, 1, -level.width(), level.width()}) {
            int cell = Dungeon.hero.pos + step;
            if (level.passable[cell] && level.heaps.get(cell) == null && level.heroFOV[cell]) {
                near.add(cell);
            }
        }
        assertTrue(near.size() >= 2, "two free cells beside the hero");
        // A stack of two seen on the floor, and a skeleton with its remains.
        Heap stack = new Heap();
        stack.pos = near.get(0);
        stack.type = Heap.Type.HEAP;
        stack.seen = true;
        stack.items.add(new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger());
        stack.items.add(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing());
        level.heaps.put(stack.pos, stack);
        Heap bones = new Heap();
        bones.pos = near.get(1);
        bones.type = Heap.Type.SKELETON;
        bones.seen = true;
        bones.items.add(new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger());
        bones.items.add(new com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor());
        level.heaps.put(bones.pos, bones);
        // A heap on a cell the player knows by map and has never seen.
        int hidden = -1;
        for (int cell = 0; cell < level.length() && hidden < 0; cell++) {
            if (level.passable[cell] && !level.heroFOV[cell] && !level.visited[cell] && level.heaps.get(cell) == null) {
                hidden = cell;
            }
        }
        assertTrue(hidden >= 0, "a cell out of view");
        level.mapped[hidden] = true;
        Heap lost = new Heap();
        lost.pos = hidden;
        lost.type = Heap.Type.HEAP;
        lost.seen = false;
        lost.items.add(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength());
        level.heaps.put(lost.pos, lost);

        SnapshotStore store = new SnapshotStore();
        Observation shown = new Observer().observe();
        SnapshotHandle live = store.take(driver);
        Fixture f = new Fixture(seed, store, live, shown);
        Observation again = liveAgain(f);
        assertEquals(shown.hash(), again.hash());
        Map<ScrubRow, String> liveWorld = world(driver, live.k(), f);
        Set<ScrubRow> holds = holds(f);
        assertTrue(holds.contains(ScrubRow.CONTAINER_CONTENTS), "the skeleton and the stack are held: " + holds);
        assertTrue(holds.contains(ScrubRow.UNSEEN_ITEMS), "the unseen heap is held: " + holds);
        for (ScrubRow row : List.of(ScrubRow.CONTAINER_CONTENTS, ScrubRow.UNSEEN_ITEMS)) {
            SnapshotHandle copy = new SnapshotScrubber(store, EnumSet.of(row)).scrub(live, sample(1));
            Observation shownCopy = copyOf(f, copy);
            assertSameScreen("row " + row, again, shownCopy);
            assertTrue(differs(row, liveWorld, world(driver, live.k(), f)), "row " + row + " changes what lies there");
            liveAgain(f);
        }
        SnapshotHandle all = new SnapshotScrubber(store).scrub(live, sample(2));
        assertSameScreen("every row", again, copyOf(f, all));
    }

    @Test
    @DisplayName("the store refuses a handle that claims to be scrubbed and is not one it made")
    void the_store_refuses_a_claimed_handle() throws IOException {
        Fixture f = fixture(smokeWarriors().get(0));
        SnapshotHandle claimed = new SnapshotHandle(f.live.id(), f.live.k(), true);
        assertThrows(IllegalArgumentException.class, () -> f.store.restoreScrubbed(claimed, driver));
        assertThrows(IllegalArgumentException.class, () -> f.store.restore(claimed, driver));
        assertFalse(f.store.madeScrubbed(claimed));
        assertFalse(f.store.madeScrubbed(new SnapshotHandle("nothing", 3, true)));
        SnapshotHandle copy = new SnapshotScrubber(f.store).scrub(f.live, sample(1));
        assertThrows(IllegalArgumentException.class, () -> f.store.restore(new SnapshotHandle(copy.id(), copy.k(), false), driver));
        assertThrows(IllegalArgumentException.class, () -> f.store.restore(copy, driver));
        assertThrows(IllegalArgumentException.class,
                () -> f.store.restoreScrubbed(new SnapshotHandle(copy.id(), copy.k(), false), driver));
        assertNotNull(copy);
    }
}
