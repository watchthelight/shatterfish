package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
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
import java.util.List;
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
    @DisplayName("a handle the store does not know is refused by name, and a snapshot from another Run is refused by its salt")
    void an_unknown_handle_is_refused() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        SnapshotStore store = new SnapshotStore();
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> store.restore(new SnapshotHandle("nowhere", 1, false), driver));
        assertTrue(refused.getMessage().contains("nowhere"), refused.getMessage());
        SnapshotHandle handle = store.take(driver);
        driver.close();
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT ^ 1);
        driver.stepToInputWait();
        HeadlessDriver other = driver;
        IllegalArgumentException salted = assertThrows(IllegalArgumentException.class, () -> store.restore(handle, other));
        assertTrue(salted.getMessage().contains("salt"), salted.getMessage());
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
