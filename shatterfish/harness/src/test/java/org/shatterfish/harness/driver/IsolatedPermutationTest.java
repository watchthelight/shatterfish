package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.agent.FightFixture;
import org.shatterfish.harness.agent.HiddenPermutation;
import org.shatterfish.harness.agent.RealBrain;
import org.shatterfish.harness.isolate.IsolatedHost;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The permutation half of leak test 3 of ADR-0025, the precondition of any caller of {@link IsolatedHost} that is not a
 * test (story 10.24): two live Runs that differ only in what the hero cannot see (the unidentified items' classes, an
 * unseen mob's place and state, a hidden trap and a secret door, the dungeon seed) show the player one Observation,
 * and then the same scrubbed start, the same {@link RunHistory} and the same isolated rollout, for the same sample.
 *
 * <p>Three layers are held separately so that a failure says which one leaked: the scrubbed files (the layer
 * {@code SearchLeakTest} holds across eight alternates), {@link RunHistoryCodec#capture} (the layer the #390 fix added,
 * which reads the process, not the save), and the isolated world's results.
 *
 * <p><b>The test can fail.</b> Control A: the unscrubbed snapshots of the two Runs differ in their files, so the
 * permutation really changed the hidden state and the equality above is not over nothing. Control B: a difference the
 * screen does show (the hero's health) moves the rollout's result, so the comparison of results can see a difference.
 * Control C: a journal page read moves {@link RunHistoryCodec#capture}, so the byte equality can fail too.
 */
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class IsolatedPermutationTest {

    /** The fightable waits the log test uses: the seed, class, the Run's salt and the wait. */
    private record Spot(long seed, HeroClass heroClass, long salt, long at) {
    }

    private static final List<Spot> SPOTS = List.of(
            new Spot(5_300_346_757_557L, HeroClass.DUELIST, 7_004L, 252),
            new Spot(5_157_722_490_902L, HeroClass.CLERIC, 7_005L, 140));

    private HeadlessDriver driver;
    private IsolatedHost pool;

    @AfterEach
    void end() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    private Observation playTo(Spot spot) {
        driver = FightFixture.playTo(spot.seed(), spot.heroClass(), spot.salt(), spot.at());
        return new Observer().observe();
    }

    /** What the scrubbed copy of the live Run is, as the isolated world reads it, and what the world did with it. */
    private record World(Map<String, String> files, RunHistory history, RolloutResult result, Map<String, String> liveFiles) {
    }

    private World measure(SnapshotStore store, BeliefSample sample, List<Action> plan) {
        SnapshotScrubber scrubber = new SnapshotScrubber(store);
        SnapshotHandle live = store.take(driver);
        Map<String, String> liveFiles = normalised(store.find(live.id()).files());
        SnapshotHandle copy = scrubber.scrub(live, sample);
        store.drop(live);
        Map<String, String> files = normalised(store.find(copy.id()).files());
        RunHistory history = RunHistoryCodec.capture();
        RolloutResult result = pool.simulate(copy, plan);
        store.drop(copy);
        return new World(files, history, result, liveFiles);
    }

    private static Map<String, String> normalised(Map<String, byte[]> files) {
        Map<String, String> out = new TreeMap<>();
        files.forEach((name, bytes) -> out.put(name, name.endsWith(".dat") ? maskClasses(SaveForm.of(bytes)) : Arrays.toString(bytes)));
        return out;
    }

    /**
     * The one thing the two scrubbed starts may differ in: which class an unidentified label hides. The scrub permutes the
     * live Run's own assignment by a permutation the sample draws (ScrubRow.POTION_SCROLL_RING_CLASSES), so a fixed sample
     * gives two worlds two assignments, the permutation applied to each truth, and the row is a distribution and not a
     * function. What it hides is masked here, and the Actions of the plan use no item, so no rollout reads it; the search
     * of story 10.24 offers no use of an unidentified item for the same reason (ADR-0025).
     */
    private static String maskClasses(String save) {
        return save.replaceAll("\"(\\w+)_label\":\"[^\"]*\"", "\"$1_label\":\"_\"")
                .replaceAll("items\\.(potions|scrolls|rings)\\.[A-Za-z0-9_$]+", "items.$1.X");
    }

    /** Where two normalised saves differ, a little context each, or nothing. */
    private static List<String> differences(Map<String, String> a, Map<String, String> b) {
        List<String> out = new ArrayList<>();
        for (String name : a.keySet()) {
            String x = a.get(name);
            String y = b.get(name);
            if (x.equals(y)) {
                continue;
            }
            int at = 0;
            while (at < x.length() && at < y.length() && x.charAt(at) == y.charAt(at)) {
                at++;
            }
            out.add(name + " @" + at + ": " + x.substring(Math.max(0, at - 120), Math.min(x.length(), at + 120)) + "  <>  "
                    + y.substring(Math.max(0, at - 120), Math.min(y.length(), at + 120)));
        }
        return out;
    }

    private static void assertSameHistory(RunHistory a, RunHistory b, String what) {
        assertArrayEquals(a.journal(), b.journal(), what + ": journal");
        assertEquals(a.badges(), b.badges(), what + ": badges");
        assertArrayEquals(a.flags(), b.flags(), what + ": flags");
        assertArrayEquals(a.counters(), b.counters(), what + ": counters");
    }

    private static boolean sameHistory(RunHistory a, RunHistory b) {
        return Arrays.equals(a.journal(), b.journal()) && a.badges().equals(b.badges()) && Arrays.equals(a.flags(), b.flags())
                && Arrays.equals(a.counters(), b.counters());
    }

    /** The plan: the real Brain's Action at the wait, then two rests, which are offered at any wait. */
    private static List<Action> plan(Observation at) {
        List<Action> plan = new ArrayList<>();
        plan.add(RealBrain.decider().decide(at));
        plan.add(new Action.Rest(false));
        plan.add(new Action.Rest(false));
        return plan;
    }

    private static void hideSomething(Level level) {
        // An unseen mob, elsewhere, hurt, enraged and hasted.
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && mob.sprite != null) {
                mob.HP = Math.max(1, mob.HP / 2);
                mob.aggro(Dungeon.hero);
                Buff.affect(mob, Haste.class, 5f);
                break;
            }
        }
        // A hidden trap on floor in view, which reads as floor, and a secret door in a wall in view, which reads as wall.
        int trapCell = -1;
        int wall = -1;
        for (int cell = 0; cell < level.length(); cell++) {
            if (trapCell < 0 && level.heroFOV[cell] && cell != Dungeon.hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                trapCell = cell;
            }
            if (wall < 0 && level.heroFOV[cell] && level.discoverable[cell] && level.map[cell] == Terrain.WALL) {
                wall = cell;
            }
        }
        if (trapCell >= 0) {
            level.setTrap(new FrostTrap().hide(), trapCell);
            Level.set(trapCell, Terrain.SECRET_TRAP);
        }
        if (wall >= 0) {
            Level.set(wall, Terrain.SECRET_DOOR);
        }
        // Two unidentified classes of each family trade labels; the floor items of those classes trade places.
        HiddenPermutation.permute();
        // The canary: the seed the dungeon would generate its next floors from.
        Dungeon.seed = Dungeon.seed ^ 0x5DEECE66DL;
    }

    private void permutationHolds(Spot spot) {
        Observation shown = playTo(spot);
        SnapshotStore store = new SnapshotStore();
        pool = new IsolatedHost(store, IsolatedHost.Mode.POOLED);
        List<Action> plan = plan(shown);
        List<World> before = new ArrayList<>();
        for (long n = 1; n <= 2; n++) {
            before.add(measure(store, sample(n), plan));
        }
        assertSameHistory(before.get(0).history(), before.get(1).history(), "the history does not depend on the sample");

        // Control C first, on a Run that is then thrown away: a journal page read is a visible difference.
        hideSomething(Dungeon.level);
        assertEquals(shown.hash(), new Observer().observe().hash(), "the hidden changes do not change the screen");

        List<World> after = new ArrayList<>();
        for (long n = 1; n <= 2; n++) {
            after.add(measure(store, sample(n), plan));
        }
        for (int i = 0; i < 2; i++) {
            World a = before.get(i);
            World b = after.get(i);
            // Control A: the unscrubbed saves differ, so the permutation changed something.
            assertNotEquals(a.liveFiles(), b.liveFiles(), spot + ": the two Runs' own saves differ in the hidden state");
            assertEquals(List.of(), differences(a.files(), b.files()), spot + ": the scrubbed start is the same bytes in both Runs, sample " + (i + 1));
            assertSameHistory(a.history(), b.history(), spot + ": RunHistoryCodec.capture over the two Runs, sample " + (i + 1));
            assertEquals(a.result(), b.result(), spot + ": the isolated rollout is the same result in both Runs, sample " + (i + 1));
        }
        // And the same rollout a second time in the permuted Run: nothing carried over.
        assertEquals(after.get(0).result(), measure(store, sample(1), plan).result(), spot + ": a repeat");

        // Control B: a difference the screen shows moves the result.
        Dungeon.hero.HP = Math.max(1, Dungeon.hero.HP / 3);
        Observation hurt = new Observer().observe();
        assertNotEquals(shown.hash(), hurt.hash(), "the hero's health is on the screen");
        World visible = measure(store, sample(1), plan);
        assertNotEquals(after.get(0).result(), visible.result(), spot + ": a visible difference moves the rollout's result");

        // Control C: a page read is a difference the player has, and the capture shows it.
        Document.ADVENTURERS_GUIDE.findPage(Document.GUIDE_ALCHEMY);
        Document.ADVENTURERS_GUIDE.readPage(Document.GUIDE_ALCHEMY);
        assertTrue(!sameHistory(after.get(0).history(), RunHistoryCodec.capture()), "reading a page moves the captured history");
    }

    @Test
    @DisplayName("DUELIST in a fight: hidden state permuted, the scrubbed start, the history and the isolated result are equal")
    void duelist() {
        permutationHolds(SPOTS.get(0));
    }

    @Test
    @DisplayName("CLERIC in a fight: hidden state permuted, the scrubbed start, the history and the isolated result are equal")
    void cleric() {
        permutationHolds(SPOTS.get(1));
    }
}
