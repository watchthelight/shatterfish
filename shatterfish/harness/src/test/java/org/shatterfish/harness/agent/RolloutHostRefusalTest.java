package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.Simulator;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.ScrubRow;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rollout host's refusals, asserted on their own (story 6.3, ADR-0009's rule that no search may run on a handle whose
 * scrubbed flag is false; FR-6, FR-13): {@link Simulator#simulate} refuses a handle that does not say scrubbed before any
 * host is asked, and {@link RolloutHost} refuses a handle that says scrubbed and is not a complete scrubbed copy its own
 * store made, whatever the flag claims. A refusal touches nothing: the live Run reads, afterwards, as the same screen, and
 * no refusal message carries the Run's salt (the salt predicts every roll of the Run, ADR-0007).
 *
 * <p>The search leak test relies on these refusals and does not repeat them: its forged-handle control takes the refusal
 * away on purpose, to show the comparison would see what the forged handle lets a search read.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class RolloutHostRefusalTest {

    private static final long SEED = 14_142_135L;
    private static final long SALT = 0x5A17_5A17L;
    private static final List<Action> ACTIONS = List.of(new Action.Rest(false));

    private HeadlessDriver driver;
    private SnapshotStore store;
    private SnapshotHandle live;
    private Observation shown;
    private RolloutHost host;

    @BeforeEach
    void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        driver.stepToInputWait();
        store = new SnapshotStore();
        live = store.take(driver);
        shown = new Observer().observe();
        host = new RolloutHost(store, driver);
    }

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    /** The live Run is still at its wait and shows what it showed. */
    private void assertUntouched(String what) {
        assertEquals(shown.hash(), new Observer().observe().hash(), what + ": the live Run reads as it did");
    }

    private void assertRefused(SnapshotHandle handle, String why) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> host.simulate(handle, ACTIONS), why);
        String message = String.valueOf(refusal.getMessage());
        assertFalse(message.contains(Long.toString(SALT)) || message.toLowerCase().contains(Long.toHexString(SALT)),
                why + ": the refusal names no salt: " + message);
        assertUntouched(why);
    }

    @Test
    @DisplayName("an unscrubbed handle is refused by Simulator.simulate, before any host runs")
    void an_unscrubbed_handle_is_refused_before_the_host() {
        AtomicInteger asked = new AtomicInteger();
        Simulator counting = new Simulator() {
            @Override
            protected RolloutResult rollout(SnapshotHandle handle, List<Action> actions) {
                asked.incrementAndGet();
                return host.simulate(handle, actions);
            }
        };
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> counting.simulate(live, ACTIONS));
        assertTrue(refusal.getMessage().contains("not scrubbed"), refusal.getMessage());
        assertEquals(0, asked.get(), "the host was never asked");
        assertThrows(IllegalArgumentException.class, () -> host.simulate(live, ACTIONS), "the real host's own entry is the same one");
        assertUntouched("an unscrubbed handle");
    }

    @Test
    @DisplayName("the live snapshot's handle with the scrubbed flag forged is refused by the host")
    void a_forged_flag_on_the_live_handle_is_refused() {
        assertRefused(new SnapshotHandle(live.id(), live.k(), true), "the live snapshot's id, flag forged");
    }

    @Test
    @DisplayName("a handle for an id the store never made is refused")
    void an_unknown_id_is_refused() {
        assertRefused(new SnapshotHandle("scrubbed-snapshot-99-" + "0".repeat(64) + "-r1fff", live.k(), true), "an id nobody made");
    }

    @Test
    @DisplayName("a scrubbed copy another store made is refused: a handle is good for the store that holds the bytes")
    void a_foreign_copy_is_refused() {
        SnapshotStore other = new SnapshotStore();
        SnapshotHandle otherLive = other.take(driver);
        SnapshotHandle foreign = new SnapshotScrubber(other).scrub(otherLive, sample(1));
        assertTrue(foreign.scrubbed() && other.madeScrubbed(foreign), "the other store made it");
        assertFalse(store.madeScrubbed(foreign), "this store did not");
        assertRefused(foreign, "another store's scrubbed copy");
    }

    @Test
    @DisplayName("a copy made with fewer rows than the table is refused as incomplete")
    void a_copy_with_a_row_missing_is_refused() {
        EnumSet<ScrubRow> rows = EnumSet.allOf(ScrubRow.class);
        rows.remove(ScrubRow.UNKNOWN_CELLS);
        SnapshotHandle partial = new SnapshotScrubber(store, rows).scrub(live, sample(1));
        assertTrue(partial.scrubbed(), "it says scrubbed");
        assertFalse(store.madeScrubbed(partial), "and the store knows it is not the whole table");
        assertRefused(partial, "a scrub without a row");
    }

    @Test
    @DisplayName("a scrubbed handle at another wait than the copy was made at is refused")
    void a_handle_at_the_wrong_wait_is_refused() {
        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(1));
        assertRefused(new SnapshotHandle(copy.id(), copy.k() + 1, true), "the right copy at the wrong wait");
    }

    @Test
    @DisplayName("a complete copy of this store's own is rolled out, and the live Run is back at its wait afterwards")
    void the_genuine_copy_is_accepted() {
        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(1));
        RolloutResult result = host.simulate(copy, ACTIONS);
        assertFalse(result.observations().isEmpty(), "the rollout saw at least the wait it started at");
        assertUntouched("a rollout that was accepted");
    }

    @Test
    @DisplayName("missing arguments are refused")
    void nothing_is_refused_by_name() {
        assertThrows(IllegalArgumentException.class, () -> host.simulate(null, ACTIONS));
        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(1));
        assertThrows(IllegalArgumentException.class, () -> host.simulate(copy, null));
        assertUntouched("missing arguments");
    }
}
