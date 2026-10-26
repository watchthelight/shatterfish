package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubRow;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The findings of the fairness re-review of story 6.2, each held by a test that fails on the code that had the finding:
 * items hidden outside the pack and the heaps, secret doors and walls told apart by the solver, the refusals that were
 * missing tests, and the wand whose charges the screen does not show.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RedeterminationReReviewTest {

    private static final long SEED = 3_343_871_708_117L;
    private static final long SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;

    @AfterEach
    void end() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    private Level start() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        driver.stepToInputWait();
        return Dungeon.level;
    }

    private List<Integer> free(Level level) {
        List<Integer> near = new ArrayList<>();
        for (int step : new int[] {-1, 1, -level.width(), level.width()}) {
            int cell = Dungeon.hero.pos + step;
            if (level.passable[cell] && level.heaps.get(cell) == null && level.heroFOV[cell] && Actor.findChar(cell) == null) {
                near.add(cell);
            }
        }
        assertTrue(near.size() >= 2, "two free cells beside the hero");
        return near;
    }

    private static Wand cursedWand() {
        Wand wand = new WandOfMagicMissile();
        wand.cursed = true;
        wand.cursedKnown = false;
        wand.levelKnown = false;
        return wand;
    }

    /** Scrubs {@code live} with the item-level row alone under samples 1.. and collects what {@code read} finds in each copy. */
    private Set<Boolean> cursesAcrossSamples(SnapshotStore store, SnapshotHandle live, Supplier<Boolean> read) {
        Set<Boolean> seen = new HashSet<>();
        for (long n = 1; n <= 16 && seen.size() < 2; n++) {
            SnapshotHandle copy = new SnapshotScrubber(store, EnumSet.of(ScrubRow.ITEM_LEVEL_AND_CURSE)).scrub(live, sample(n));
            store.restoreScrubbed(copy, driver);
            driver.stepToInputWait();
            seen.add(read.get());
        }
        return seen;
    }

    @Test
    @DisplayName("an unidentified item dropped down a chasm is rolled afresh, as the pack's are")
    void a_dropped_item_is_rolled_afresh() {
        Level level = start();
        ArrayList<Item> dropped = new ArrayList<>();
        dropped.add(cursedWand());
        Dungeon.droppedItems.put(Dungeon.depth + 1, dropped);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        int below = Dungeon.depth + 1;
        Set<Boolean> curses = cursesAcrossSamples(store, live, () -> {
            ArrayList<Item> items = Dungeon.droppedItems.get(below);
            assertNotNull(items, "the copy still holds what fell");
            return ((Wand) items.get(0)).cursed;
        });
        assertEquals(Set.of(true, false), curses, "across samples the hidden curse of the dropped wand varies");
    }

    @Test
    @DisplayName("an unidentified item a thief in view carries is rolled afresh")
    void a_thiefs_loot_is_rolled_afresh() {
        Level level = start();
        List<Integer> near = free(level);
        Thief thief = new Thief();
        thief.pos = near.get(0);
        thief.HT = 20;
        thief.HP = 20;
        thief.item = cursedWand();
        level.mobs.add(thief);
        GameScene.add(thief);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        Set<Boolean> curses = cursesAcrossSamples(store, live, () -> {
            for (Mob mob : Dungeon.level.mobs) {
                if (mob instanceof Thief t && t.item instanceof Wand wand) {
                    return wand.cursed;
                }
            }
            throw new AssertionError("the thief is still in view in the copy");
        });
        assertEquals(Set.of(true, false), curses, "across samples the hidden curse of the stolen wand varies");
    }

    @Test
    @DisplayName("a wand whose charges the screen does not show gets fresh charges in the copy")
    void a_wands_hidden_charges_are_rolled_afresh() {
        start();
        Wand wand = new WandOfMagicMissile();
        wand.levelKnown = true;
        wand.cursedKnown = true;
        wand.cursed = false;
        wand.curChargeKnown = false;
        wand.curCharges = 0;
        Dungeon.hero.belongings.backpack.items.add(wand);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        SnapshotHandle copy = new SnapshotScrubber(store, EnumSet.of(ScrubRow.ITEM_LEVEL_AND_CURSE)).scrub(live, sample(1));
        store.restoreScrubbed(copy, driver);
        driver.stepToInputWait();
        boolean found = false;
        for (Item item : Dungeon.hero.belongings.backpack.items) {
            if (item instanceof WandOfMagicMissile copied) {
                found = true;
                assertTrue(copied.curCharges > 0, "the used-up wand is not used up in the copy: " + copied.curCharges);
            }
        }
        assertTrue(found, "the wand is in the copy");
    }

    @Test
    @DisplayName("an item no fresh roll can match is refused, not kept with its true curse")
    void an_item_no_roll_fits_is_refused() {
        start();
        Dagger odd = new Dagger();
        odd.levelKnown = true;
        odd.cursedKnown = false;
        odd.level(50);
        Dungeon.hero.belongings.backpack.items.add(odd);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        Scrub.Refused refused = assertThrows(Scrub.Refused.class, () -> new SnapshotScrubber(store).scrub(live, sample(1)));
        assertTrue(refused.getMessage().contains("roll"), refused.getMessage());
        assertTrue(!refused.getMessage().contains("Dagger") && !refused.getMessage().contains("dagger"),
                "the refusal does not name the item: " + refused.getMessage());
    }

    @Test
    @DisplayName("remains in view are refused")
    void remains_are_refused() {
        Level level = start();
        Heap remains = new Heap();
        remains.pos = free(level).get(0);
        remains.type = Heap.Type.REMAINS;
        remains.seen = true;
        remains.items.add(new Dagger());
        level.heaps.put(remains.pos, remains);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        Scrub.Refused refused = assertThrows(Scrub.Refused.class, () -> new SnapshotScrubber(store).scrub(live, sample(1)));
        assertTrue(refused.getMessage().contains("REMAINS"), refused.getMessage());
    }

    @Test
    @DisplayName("a secret door and a wall that the screen draws alike scrub to the same terrain")
    void a_secret_door_scrubs_as_a_wall_does() {
        Level level = start();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle asWall = store.take(driver);
        Observation wallScreen = new Observer().observe();
        int cell = -1;
        for (int c = 0; c < level.length(); c++) {
            if (level.map[c] != Terrain.WALL || !(level.visited[c] || level.mapped[c])) {
                continue;
            }
            Level.set(c, Terrain.SECRET_DOOR);
            if (new Observer().observe().hash().equals(wallScreen.hash())) {
                cell = c;
            } else {
                Level.set(c, Terrain.WALL);
            }
        }
        assertTrue(cell >= 0, "a known wall that can hold a secret door without the screen changing");
        SnapshotHandle asDoor = store.take(driver);

        int[] fromWall = mapOfCopy(store, asWall);
        int[] fromDoor = mapOfCopy(store, asDoor);
        assertArrayEquals(fromWall, fromDoor, "the copy's terrain does not tell the secret door from the wall");
    }

    @Test
    @DisplayName("no handle id and no refusal message carries the salt")
    void nothing_the_brain_sees_carries_the_salt() {
        start();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(1));
        String salt = Long.toString(SALT);
        String hex = Long.toHexString(SALT);
        for (SnapshotHandle handle : List.of(live, copy)) {
            assertTrue(!handle.id().contains(salt) && !handle.id().toLowerCase().contains(hex), "the id " + handle.id());
        }
        RolloutHost host = new RolloutHost(store, driver);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> host.simulate(new SnapshotHandle(live.id(), live.k(), true), List.of()));
        assertTrue(!refused.getMessage().contains(salt), refused.getMessage());
        // a snapshot of a Run with another salt is refused without printing either salt
        driver.close();
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT + 1);
        driver.stepToInputWait();
        IllegalArgumentException foreign = assertThrows(IllegalArgumentException.class, () -> store.restore(live, driver));
        assertTrue(!foreign.getMessage().contains(salt) && !foreign.getMessage().contains(Long.toString(SALT + 1)),
                foreign.getMessage());
    }

    @Test
    @DisplayName("a quest that has spawned on a floor the player has not seen does not decide whether the scrub is refused")
    void a_spawned_quest_is_no_signal() {
        start();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle plain = store.take(driver);
        new SnapshotScrubber(store).scrub(plain, sample(1));

        com.watabou.utils.Bundle node = new com.watabou.utils.Bundle();
        node.put("spawned", true);
        node.put("type", 1);
        node.put("given", false);
        node.put("depth", Dungeon.depth + 1);
        node.put("processed", false);
        node.put("weapon", new Dagger());
        node.put("armor", new com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor());
        com.watabou.utils.Bundle outer = new com.watabou.utils.Bundle();
        outer.put("sadGhost", node);
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost.Quest.restoreFromBundle(outer);
        SnapshotHandle spawned = store.take(driver);
        try {
            SnapshotHandle copy = new SnapshotScrubber(store).scrub(spawned, sample(1));
            assertNotNull(copy, "a spawned, unmet quest is scrubbed as one that has not spawned");
        } finally {
            com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost.Quest.reset();
        }
    }

    @Test
    @DisplayName("a copy made with only some rows is not rolled out by the host")
    void a_partial_copy_is_not_rolled_out() {
        start();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        SnapshotHandle partial = new SnapshotScrubber(store, EnumSet.of(ScrubRow.ROLLOUT_SALT)).scrub(live, sample(1));
        SnapshotHandle full = new SnapshotScrubber(store).scrub(live, sample(1));
        RolloutHost host = new RolloutHost(store, driver);
        assertThrows(IllegalArgumentException.class, () -> host.simulate(partial, List.of()));
        assertTrue(store.madeScrubbed(full), "the full copy is still accepted");
        assertTrue(!store.madeScrubbed(partial), "the store does not call a partial copy scrubbed");
    }

    private int[] mapOfCopy(SnapshotStore store, SnapshotHandle live) {
        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(3));
        store.restoreScrubbed(copy, driver);
        driver.stepToInputWait();
        return Dungeon.level.map.clone();
    }
}
