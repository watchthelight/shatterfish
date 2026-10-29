package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
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
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.isolate.IsolatedHost;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #390: a rollout in the isolated world is the swap host's rollout, log included.
 *
 * <p>The isolated world begins every rollout with the profile a fresh Run has: an empty journal. The live Run the swap
 * host borrows has played on, and its journal holds the guide pages it found and read, which the game reads when it
 * decides whether to hint at one ({@code Hero.java:1758}, {@code Snake.java:65}, {@code Hunger.java:109}, {@code
 * EquipableItem.java:59}). So the same rollout showed an extra "Your guidebook has advice for you!" line in one host and
 * not in the other, and the log window slid by one. The two cases are the snapshots that showed it in
 * {@code :harness:isolationSpeed} on the standard set (seed index 4, DUELIST at wait 252, and index 5, CLERIC at wait
 * 140), played by the same Brain from the same seed and salt (the tool's salt is 7000 plus the seed's index).
 *
 * <p>The determinism cases are what ADR-0025 named as a precondition of any Rig use of the host: the same rollout twice
 * gives the same result, and a rollout between them changes nothing.
 */
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class IsolatedHostLogTest {

    private static final int SAMPLES = 3;
    private static final int[] HORIZONS = SimulatorSpeed.HORIZONS;

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

    /** Plays {@code seed} with the real Brain to the fightable wait {@code at}, and stands there. */
    private Observation playTo(long seed, HeroClass heroClass, long salt, long at) {
        driver = HeadlessDriver.start(seed, heroClass, salt);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        for (int i = 0; i < 3_000; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                continue;
            }
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                throw new AssertionError("the Run ended (" + halt.reason() + ") before wait " + at);
            }
            Observation observation = new Observer().observe();
            if (halt.waitIndex() >= at) {
                assertEquals(at, halt.waitIndex(), "the Run has a wait at " + at);
                assertTrue(SimulatorSpeed.fightable(observation), "wait " + at + " is mid-fight, as the measurement had it");
                return observation;
            }
            executor.execute(observation, brain.decide(observation));
        }
        throw new AssertionError("wait " + at + " not reached");
    }

    private static List<Action> plan(RolloutHost swap, SnapshotHandle copy, Observation first, BrainDecider planner) {
        List<Action> plan = new ArrayList<>();
        Observation at = first;
        int most = HORIZONS[HORIZONS.length - 1];
        for (int h = 1; h <= most; h++) {
            plan.add(planner.decide(at));
            if (h == most) {
                break;
            }
            RolloutResult tried = swap.simulate(copy, plan);
            if (tried.observations().size() <= h || tried.applied() < h) {
                break;
            }
            at = tried.observations().get(h);
        }
        return plan;
    }

    /** The first Observation at which two results differ, described by its log lines only, or null. */
    private static String firstDifference(RolloutResult a, RolloutResult b) {
        if (a.equals(b)) {
            return null;
        }
        int n = Math.min(a.observations().size(), b.observations().size());
        for (int i = 0; i < n; i++) {
            Observation x0 = a.observations().get(i);
            Observation y0 = b.observations().get(i);
            if (!x0.equals(y0)) {
                List<String> x = x0.log().lines().stream().map(Object::toString).toList();
                List<String> y = y0.log().lines().stream().map(Object::toString).toList();
                return "Observation " + i + " differs, sections "
                        + x0.sectionHashes().entrySet().stream()
                        .filter(e -> !e.getValue().equals(y0.sectionHashes().get(e.getKey())))
                        .map(java.util.Map.Entry::getKey).toList()
                        + "; log lines " + x.size() + " against " + y.size()
                        + (x.size() == y.size() ? "" : ", last of the longer: " + (x.size() > y.size() ? x : y).get(Math.max(x.size(), y.size()) - 1));
            }
        }
        return "same Observations, other end or length: " + a.end() + "/" + a.applied() + " against " + b.end() + "/" + b.applied();
    }

    private void theHostsAgree(long seed, HeroClass heroClass, long salt, long at) {
        Observation first = playTo(seed, heroClass, salt, at);
        SnapshotStore store = new SnapshotStore();
        SnapshotScrubber scrubber = new SnapshotScrubber(store);
        RolloutHost swap = new RolloutHost(store, driver);
        pool = new IsolatedHost(store, IsolatedHost.Mode.POOLED);
        BrainDecider planner = RealBrain.decider();
        List<String> differences = new ArrayList<>();
        for (int n = 1; n <= SAMPLES; n++) {
            SnapshotHandle live = store.take(driver);
            SnapshotHandle copy = scrubber.scrub(live, sample(n));
            store.drop(live);
            List<Action> plan = plan(swap, copy, first, planner);
            for (int horizon : HORIZONS) {
                List<Action> line = plan.subList(0, Math.min(horizon, plan.size()));
                RolloutResult viaSwap = swap.simulate(copy, line);
                RolloutResult viaPool = pool.simulate(copy, line);
                String difference = firstDifference(viaSwap, viaPool);
                if (difference != null) {
                    differences.add("sample " + n + ", " + horizon + " Actions: " + difference);
                }
                // The same rollout again: a world that carried something from the last one would answer differently.
                String again = firstDifference(viaPool, pool.simulate(copy, line));
                if (again != null) {
                    differences.add("sample " + n + ", " + horizon + " Actions, the pooled world twice: " + again);
                }
            }
            store.drop(copy);
        }
        assertEquals(List.of(), differences, "the isolated world's results are the swap host's, wait " + at + " of " + seed);
    }

    @Test
    @DisplayName("DUELIST at wait 252 of seed index 4: both hosts show the same log")
    void duelist_hint_lines() {
        theHostsAgree(5_300_346_757_557L, HeroClass.DUELIST, 7_004L, 252);
    }

    @Test
    @DisplayName("CLERIC at wait 140 of seed index 5: both hosts show the same log")
    void cleric_hint_lines() {
        theHostsAgree(5_157_722_490_902L, HeroClass.CLERIC, 7_005L, 140);
    }
}
