package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Run's exact state can be saved at a wait and put back (ADR-0009; FR-6): a snapshot taken at
 * wait {@code k}, restored after the Run has gone on, stands at wait {@code k} again, reads the
 * same Observation hash, and replays the recorded Actions to the same hashes at every wait after,
 * which is what catches the draws the game's own load consumes and anything else the load resets.
 * Taking the snapshot changes nothing about the Run it is taken from: the same tuple played with
 * and without one gives the same hashes. Every handle the store hands out is the live Run's own,
 * unscrubbed, and a handle the store does not know is refused by name.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class RestoreReplayTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 14_142_135L;
    private static final int WAITS = 24;
    private static final long SNAPSHOT_AT = 9;

    /** One wait of a record: its index, the Observation's hash there, and the Action applied. */
    record Wait(long k, String hash, Action action) {
    }

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    @DisplayName("a restored Run stands at the snapshot's wait and replays the recorded Actions hash for hash")
    void a_restored_run_replays_hash_for_hash() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle[] taken = new SnapshotHandle[1];
        List<Wait> record = play(driver, WAITS, store, SNAPSHOT_AT, taken);
        SnapshotHandle handle = taken[0];
        assertNotNull(handle, "the snapshot was taken");
        assertEquals(SNAPSHOT_AT, handle.k());
        assertFalse(handle.scrubbed(), "the live Run's own snapshot is not scrubbed");
        assertEquals(1, store.size());

        store.restore(handle, driver);
        HeadlessDriver.Halt first = driver.stepToInputWait();
        assertEquals(HeadlessDriver.Reason.INPUT_WAIT, first.reason(), "the restored Run reaches a wait");
        assertEquals(SNAPSHOT_AT, first.waitIndex(), "and it is the snapshot's");
        Observation restored = new Observer().observe();
        int at = index(record, SNAPSHOT_AT);
        assertEquals(record.get(at).hash(), restored.hash(), "the restored wait reads as the original wait " + SNAPSHOT_AT);

        // The replay: the recorded Action at each wait, then the next wait, hash for hash.
        ActionExecutor executor = new ActionExecutor();
        Observation observation = restored;
        for (int i = at; i < record.size(); i++) {
            Wait original = record.get(i);
            Outcome outcome = executor.execute(observation, original.action());
            assertTrue(outcome instanceof Outcome.Applied, "wait " + original.k() + ": " + outcome);
            if (i + 1 == record.size()) {
                break;
            }
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "after wait " + original.k());
            assertEquals(record.get(i + 1).k(), halt.waitIndex());
            observation = new Observer().observe();
            assertEquals(record.get(i + 1).hash(), observation.hash(), "wait " + record.get(i + 1).k() + " replays");
        }

        // The same snapshot restored again, from the end of the replay: a rollout host restores
        // one snapshot many times.
        store.restore(handle, driver);
        assertEquals(SNAPSHOT_AT, driver.stepToInputWait().waitIndex());
        assertEquals(record.get(at).hash(), new Observer().observe().hash(), "the second restore reads the same");

        // A later snapshot taken from the restored Run, then the earlier one restored over it.
        assertTrue(executor.execute(new Observer().observe(), record.get(at).action()) instanceof Outcome.Applied);
        driver.stepToInputWait();
        SnapshotHandle later = store.take(driver);
        assertEquals(2, store.size());
        store.restore(handle, driver);
        assertEquals(SNAPSHOT_AT, driver.stepToInputWait().waitIndex());
        assertEquals(record.get(at).hash(), new Observer().observe().hash(), "the earlier snapshot over the later one");
        store.drop(later);
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("the emote a sprite showed at the wait survives the restore, though the bundle never held it")
    void an_emote_survives_a_restore() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        SnapshotStore store = new SnapshotStore();
        // A mob brought into view showing the alert icon, which lives on the sprite and not in the
        // bundle (Mob.storeInBundle, Mob.java:169-201; CharSprite.java:679-690).
        com.shatteredpixel.shatteredpixeldungeon.levels.Level level = Dungeon.level;
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob mob = null;
        for (com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob m : level.mobs) {
            if (!level.heroFOV[m.pos] && m.sprite != null && !(m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic)) {
                mob = m;
                break;
            }
        }
        assertNotNull(mob, "a mob out of view to bring in");
        int cell = -1;
        for (int c = 0; c < level.length() && cell < 0; c++) {
            if (level.heroFOV[c] && c != Dungeon.hero.pos && level.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.EMPTY
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(c) == null && level.heaps.get(c, null) == null) {
                cell = c;
            }
        }
        assertTrue(cell >= 0, "free floor in view");
        final int in = cell;
        mob.pos = in;
        mob.sprite.place(in);
        // Awake, so the Observer reads the sprite's icon rather than the sleep it draws from state.
        mob.state = mob.WANDERING;
        mob.sprite.showAlert();
        Observation alerted = new Observer().observe();
        assertTrue(alerted.actors().actors().stream().anyMatch(a -> a.cell() == in && a.emote() == org.shatterfish.api.Emote.ALERT),
                "the alert is drawn: " + alerted.actors());

        SnapshotHandle handle = store.take(driver);
        assertEquals(2, goOn(driver));
        store.restore(handle, driver);
        assertEquals(1, driver.stepToInputWait().waitIndex());
        Observation restored = new Observer().observe();
        assertTrue(restored.actors().actors().stream().anyMatch(a -> a.cell() == in && a.emote() == org.shatterfish.api.Emote.ALERT),
                "the alert is drawn again: " + restored.actors());
        assertEquals(alerted.hash(), restored.hash(), "the restored wait reads as the wait with the emote");
    }

    @Test
    @DisplayName("taking a snapshot changes nothing: the same tuple with and without one gives the same hashes")
    void taking_a_snapshot_changes_nothing() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        List<Wait> with = play(driver, WAITS, new SnapshotStore(), SNAPSHOT_AT, new SnapshotHandle[1]);
        driver.close();
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        List<Wait> without = play(driver, WAITS, null, -1, null);
        assertEquals(with.size(), without.size());
        for (int i = 0; i < with.size(); i++) {
            assertEquals(without.get(i), with.get(i), "wait " + with.get(i).k());
        }
    }

    @Test
    @DisplayName("a refused restore leaves the Run intact: an unknown handle, a scrubbed claim, a wrong wait, another salt, another seed")
    void an_unknown_handle_is_refused() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        SnapshotStore store = new SnapshotStore();
        String before = new Observer().observe().hash();
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> store.restore(new SnapshotHandle("nowhere", 1, false), driver));
        assertTrue(refused.getMessage().contains("nowhere"), refused.getMessage());
        SnapshotHandle handle = store.take(driver);
        IllegalArgumentException claimed = assertThrows(IllegalArgumentException.class,
                () -> store.restore(new SnapshotHandle(handle.id(), handle.k(), true), driver));
        assertTrue(claimed.getMessage().contains("scrubbed"), claimed.getMessage());
        IllegalArgumentException wrongWait = assertThrows(IllegalArgumentException.class,
                () -> store.restore(new SnapshotHandle(handle.id(), handle.k() + 1, false), driver));
        assertTrue(wrongWait.getMessage().contains("wait"), wrongWait.getMessage());
        assertEquals(before, new Observer().observe().hash(), "the Run is untouched by the refusals");
        assertEquals(2, goOn(driver), "and goes on");

        driver.close();
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT ^ 1);
        driver.stepToInputWait();
        HeadlessDriver salted = driver;
        IllegalArgumentException wrongSalt = assertThrows(IllegalArgumentException.class, () -> store.restore(handle, salted));
        assertTrue(wrongSalt.getMessage().contains("salt"), wrongSalt.getMessage());
        assertEquals(2, goOn(salted), "the other Run goes on too");

        driver.close();
        driver = HeadlessDriver.start(SEED + 1, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        HeadlessDriver seeded = driver;
        IllegalArgumentException wrongSeed = assertThrows(IllegalArgumentException.class, () -> store.restore(handle, seeded));
        assertTrue(wrongSeed.getMessage().contains("seed"), wrongSeed.getMessage());
        assertEquals(2, goOn(seeded));
    }

    /** Hands a Wait to the game and steps to the next wait, whose index says the Run goes on. */
    private static long goOn(HeadlessDriver driver) {
        Observation observation = new Observer().observe();
        Action wait = observation.actions().actions().stream().filter(a -> a instanceof Action.Wait).findFirst().orElseThrow();
        assertTrue(new ActionExecutor().execute(observation, wait) instanceof Outcome.Applied);
        return driver.stepToInputWait().waitIndex();
    }

    @Test
    @DisplayName("a snapshot is refused under a window and after an Action, and a broken snapshot closes the Run")
    void a_snapshot_is_refused_where_it_cannot_be_restored() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        SnapshotStore store = new SnapshotStore();
        Observation observation = new Observer().observe();

        // Under a Prompt: the game's save carries no window (Chasm.java:57-96 posts the jump
        // prompt to the render thread; the driver drains that queue in its loop).
        Chasm.heroJump(Dungeon.hero);
        HeadlessDriver.Halt prompted = driver.stepToInputWait();
        assertNotNull(prompted.window(), "the chasm prompt is in front");
        IllegalStateException windowed = assertThrows(IllegalStateException.class, () -> store.take(driver));
        assertTrue(windowed.getMessage().contains("no window in front"), windowed.getMessage());
        Action no = new Observer().observe().actions().actions().stream()
                .filter(a -> a instanceof Action.AnswerPrompt answer && answer.option() == 1).findFirst().orElseThrow();
        assertTrue(new ActionExecutor().execute(new Observer().observe(), no) instanceof Outcome.Applied);
        driver.stepToInputWait();

        // After an Action was handed to the game and before the next wait.
        observation = new Observer().observe();
        Action wait = observation.actions().actions().stream().filter(a -> a instanceof Action.Wait).findFirst().orElseThrow();
        assertTrue(new ActionExecutor().execute(observation, wait) instanceof Outcome.Applied);
        IllegalStateException acted = assertThrows(IllegalStateException.class, () -> store.take(driver));
        assertTrue(acted.getMessage().contains("nothing handed to the game"), acted.getMessage());
        driver.stepToInputWait();
        SnapshotHandle handle = store.take(driver);
        assertNotNull(handle);

        // A snapshot whose files the game cannot load closes the Run rather than leaving a driver
        // open over a destroyed scene.
        Map<String, byte[]> broken = new LinkedHashMap<>();
        broken.put("game.dat", new byte[] {0, 1, 2});
        Snapshot bad = new Snapshot("broken", handle.k(), Dungeon.seed, HeroClass.WARRIOR.name(), RUN_SALT,
                com.shatteredpixel.shatteredpixeldungeon.GamesInProgress.curSlot, broken, List.of(), Map.of());
        HeadlessDriver doomed = driver;
        assertThrows(RuntimeException.class, () -> doomed.restore(bad));
        assertTrue(doomed.closed(), "a failed restore closes the Run");
        driver = null;
    }

    /**
     * Plays {@code waits} Input waits with a seeded random agent, recording the hash and the
     * applied Action at each, and takes a snapshot into {@code store} at {@code snapshotAt}.
     */
    private static List<Wait> play(HeadlessDriver driver, int waits, SnapshotStore store, long snapshotAt, SnapshotHandle[] taken) {
        RandomAgent agent = new RandomAgent(SEED);
        ActionExecutor executor = new ActionExecutor();
        List<Wait> record = new ArrayList<>();
        for (int i = 0; i < waits; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "wait " + (i + 1) + " on floor one: " + halt);
            long k = halt.waitIndex();
            Observation observation = new Observer().observe();
            if (store != null && k == snapshotAt) {
                taken[0] = store.take(driver);
            }
            Action applied = null;
            for (int attempt = 0; attempt < 20 && applied == null; attempt++) {
                Action chosen = agent.decide(observation);
                assertNotNull(chosen, "the screen offers an Action");
                if (chosen instanceof Action.Descend) {
                    continue; // the record stays on floor one, where a restore is exact (the journal)
                }
                if (executor.execute(observation, chosen) instanceof Outcome.Applied) {
                    applied = chosen;
                }
            }
            assertNotNull(applied, "an Action the executor applies at wait " + k);
            record.add(new Wait(k, observation.hash(), applied));
        }
        return record;
    }

    private static int index(List<Wait> record, long k) {
        for (int i = 0; i < record.size(); i++) {
            if (record.get(i).k() == k) {
                return i;
            }
        }
        throw new AssertionError("no wait " + k + " in the record");
    }
}
