package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.DocumentPage;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rollout host puts the live Run back exactly, including the guide page lying on its floor (issue #271, E6.2's
 * promise). The case is the {@code standard} seed 648322377831 (a Warrior), which the 6.5 measurement found at its Input
 * wait 164: a torn guidebook page lies in view, a playout that picks it up finds the page in the journal, and the game
 * drops a page the journal has found from a floor as it loads the floor ({@code core/.../items/Heap.java:439-449}). The
 * journal is the process's and not the snapshot's, so a restore that ran before the profile was put back loaded the live
 * floor without the page, and the next snapshot of the live Run saved it that way.
 *
 * <p>The Runs are {@code standard} seeds, played by the real Brain with the 6.5 measurement's salt (never {@code goo} or
 * {@code holdout}).
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class RolloutRestoresTheFloorTest {

    private static final long SEED = 648_322_377_831L;
    /** The salt the 6.5 measurement gave the first seed of the set. */
    private static final long SALT = 7_000L;
    private static final int WAIT_LIMIT = 600;

    private HeadlessDriver driver;

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

    /** A guide page the journal has not found, lying on the floor the live Run stands on, or null. */
    private static Heap unfoundPage() {
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            Item top = heap.peek();
            if (top instanceof DocumentPage page && !page.document().isPageFound(page.page())) {
                return heap;
            }
        }
        return null;
    }

    /** The live Run, played by the real Brain to the first wait a page lies in view at and the scrub accepts. */
    private SnapshotHandle atThePage(SnapshotStore store) {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        for (int i = 0; i < WAIT_LIMIT; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                continue;
            }
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "the Run goes on to a page");
            Observation observation = new Observer().observe();
            Heap page = halt.window() == null ? unfoundPage() : null;
            if (page != null && observation.map().heaps().stream().anyMatch(h -> h.cell() == page.pos)) {
                try {
                    store.drop(new SnapshotScrubber(store).scrub(store.take(driver), sample(1)));
                    return store.take(driver);
                } catch (IllegalStateException | Scrub.Refused refused) {
                    // a wait the scrub declines: the next
                }
            }
            executor.execute(observation, brain.decide(observation));
        }
        throw new AssertionError("no guide page in view within " + WAIT_LIMIT + " waits");
    }

    /** One Action that stays on the floor: a search, a wait or a step. */
    private static List<Action> line(Observation at) {
        for (Action action : at.actions().actions()) {
            if (action instanceof Action.Search || action instanceof Action.Wait || action instanceof Action.Step) {
                return List.of(action);
            }
        }
        throw new AssertionError("the wait offers no Action that stays on the floor");
    }

    @Test
    @DisplayName("a rollout that finds the page lying on the live floor leaves the page there, and the screen as it was")
    void a_rollout_that_finds_the_page_leaves_it_on_the_floor() {
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = atThePage(store);
        Heap page = unfoundPage();
        assertNotNull(page, "the live floor holds the page");
        int cell = page.pos;
        DocumentPage held = (DocumentPage) page.peek();
        Document document = held.document();
        String name = held.page();
        Observation before = new Observer().observe();
        System.out.println("RolloutRestoresTheFloorTest: the page " + name + " lies at cell " + cell + ", wait " + live.k());

        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(2));
        AtomicBoolean found = new AtomicBoolean();
        RolloutHost host = new RolloutHost(store, driver).timedBy(new RolloutHost.Phases() {
            @Override
            public void snapshot(long nanos) {
            }

            @Override
            public void load(long nanos) {
            }

            @Override
            public void rollout(long nanos) {
                // The rollout has played and the live Run is not yet back: the hero of the copy has picked the page up, as
                // a line that walks to it would (DocumentPage.java:46-62).
                document.findPage(name);
                found.set(document.isPageFound(name));
            }

            @Override
            public void restore(long nanos) {
            }
        });
        RolloutResult result = host.simulate(copy, line(before));

        assertTrue(found.get(), "the rollout found the page");
        assertEquals(1, result.applied(), "the line was played: " + result.end());
        assertFalse(document.isPageFound(name), "the journal is as the rollout found it: the page is not found");
        Heap now = Dungeon.level.heaps.get(cell);
        assertNotNull(now, "the heap is on the live floor");
        assertTrue(now.peek() instanceof DocumentPage, "and the page is in it");
        assertEquals(before.hash(), new Observer().observe().hash(), "the live Run reads as it did");
        // The next snapshot of the live Run, which a search takes for its next rollout, is the one that carried the page.
        store.verifyRestored(live, driver);
    }

    @Test
    @DisplayName("a snapshot taken after the rollout keeps the page, so the next rollout starts from the same floor")
    void the_next_snapshot_keeps_the_page() {
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = atThePage(store);
        Heap page = unfoundPage();
        int cell = page.pos;
        DocumentPage held = (DocumentPage) page.peek();
        Document document = held.document();
        String name = held.page();

        SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(3));
        RolloutHost host = new RolloutHost(store, driver).timedBy(new RolloutHost.Phases() {
            @Override
            public void snapshot(long nanos) {
            }

            @Override
            public void load(long nanos) {
            }

            @Override
            public void rollout(long nanos) {
                document.findPage(name);
            }

            @Override
            public void restore(long nanos) {
            }
        });
        host.simulate(copy, line(new Observer().observe()));

        SnapshotStore later = new SnapshotStore();
        SnapshotHandle next = later.take(driver);
        later.restore(next, driver);
        driver.stepToInputWait();
        assertNotNull(Dungeon.level.heaps.get(cell), "a restore of a snapshot taken after the rollout still has the heap");
        assertEquals(1, Dungeon.level.heaps.valueList().stream().filter(h -> h.pos == cell).count());
    }

    @Test
    @DisplayName("verifyRestored fails loudly, naming the file, when the Run is not the snapshot's")
    void a_run_that_is_not_the_snapshots_fails_the_check() {
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = atThePage(store);
        store.verifyRestored(live, driver);
        Dungeon.gold += 7;
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> store.verifyRestored(live, driver));
        assertTrue(refusal.getMessage().contains("game.dat"), refusal.getMessage());
        Dungeon.gold -= 7;
        store.verifyRestored(live, driver);
    }

    @Test
    @DisplayName("verifyRestored names the floor's file when the page heap is gone: the set-order normalisation does not hide it")
    void a_floor_without_the_page_fails_the_check() {
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = atThePage(store);
        Heap page = unfoundPage();
        assertNotNull(page, "the live floor holds the page");
        // Issue #271's symptom, made by hand: the floor as the old restore left it.
        Dungeon.level.heaps.remove(page.pos);
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> store.verifyRestored(live, driver));
        assertTrue(refusal.getMessage().contains("depth"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("game.dat"), refusal.getMessage());
        Dungeon.level.heaps.put(page.pos, page);
        store.verifyRestored(live, driver);
    }

    @Test
    @DisplayName("verifyRestored names the screen when only the screen differs")
    void a_screen_that_differs_fails_the_check() {
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = atThePage(store);
        store.verifyRestored(live, driver);
        // A log line is drawn and never saved.
        GLog.i("a line the snapshot did not see");
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> store.verifyRestored(live, driver));
        assertTrue(refusal.getMessage().contains("[(the screen)]"), refusal.getMessage());
    }

    @Test
    @DisplayName("the 6.5 playouts at wait 164 of the standard seed come back to the screen they left")
    void the_properties_playouts_restore_exactly() throws IOException {
        PropertiesMeasurement.Config config = new PropertiesMeasurement.Config("standard", 1, 4, 4, 40, 400, 20);
        PropertiesMeasurement.Report report = PropertiesMeasurement.run(config, new Launcher.Launch(1L, true),
                new PrintStream(new ByteArrayOutputStream()));

        assertEquals(List.of(), report.skipped(), "no Run ended, and none ended on a restore that differs");
        assertEquals(4, report.snapshots(), "the fourth snapshot is the one at wait 164");
        for (Properties.Row row : report.rows()) {
            assertEquals(0, row.droppedWhy().getOrDefault("restore differs", 0), row.driver() + ": no playout was dropped for its restore");
        }
    }
}
