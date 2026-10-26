package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.ProfileGuard;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rollout host plays a scrubbed copy and gives the live Run back untouched (story 6.2, ADR-0009, ADR-0010): the waits
 * from the restored wait onward read as an undisturbed twin's, hash for hash; the profile's files are as they were; a
 * handle the host's store did not make as a scrubbed copy rolls out nothing; and a descent inside a rollout builds the
 * sample's floor, not the Run's.
 *
 * <p>The Runs are Warriors of the {@code smoke} set (never {@code goo} or {@code holdout}), played by the real Brain.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class RestoreReplayAfterRolloutTest {

    private static final long SALT = 0x5A17_5A17L;
    /** A Warrior of the smoke set. */
    private static final long SEED = 3_343_871_708_117L;
    private static final int WAIT_LIMIT = 1_500;
    private static final int ROLLOUT_LENGTH = 8;
    private static final int AFTER = 60;

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

    private static boolean fightable(Observation observation) {
        return observation.actors().actors().stream().anyMatch(a -> a.alignment() == Alignment.ENEMY);
    }

    /** What the Run's profile folder holds in the four files the game writes there. */
    private Map<String, String> profileFiles() throws IOException {
        Path folder = driver.headlessBoot().profile();
        Map<String, String> held = new LinkedHashMap<>();
        for (String name : List.of("rankings.dat", "badges.dat", "journal.dat", "bones.dat")) {
            Path file = folder.resolve(name);
            held.put(name, Files.isRegularFile(file) ? java.util.HexFormat.of().formatHex(Files.readAllBytes(file)) : "absent");
        }
        return held;
    }

    private void serve() {
        InterlevelScene.Mode mode = InterlevelScene.mode;
        driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
    }

    /** Plays to the first wait with an enemy in view that the scrub accepts, and returns its wait index. */
    private long findFight() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        for (int i = 0; i < WAIT_LIMIT; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                serve();
                continue;
            }
            assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason());
            Observation observation = new Observer().observe();
            if (fightable(observation)) {
                try {
                    SnapshotStore store = new SnapshotStore();
                    new SnapshotScrubber(store).scrub(store.take(driver), sample(1));
                    return halt.waitIndex();
                } catch (IllegalStateException | Scrub.Refused refused) {
                    // a window in front, or a snapshot the scrub declines: the next wait
                }
            }
            executor.execute(observation, brain.decide(observation));
        }
        throw new AssertionError("no fight in " + WAIT_LIMIT + " waits");
    }

    /** The waits from {@code k} onward as "wait:hash", and the Actions the Brain chose at the first {@code keep} of them. */
    private record Course(List<String> hashes, List<Action> actions, Map<String, String> before, Map<String, String> after,
                          RolloutResult rolled) {
    }

    /** Plays from the start; at wait {@code k} optionally rolls {@code rollout} out; returns what the Run did from k onward. */
    private Course play(long k, boolean roll, List<Action> rollout) throws IOException {
        if (driver != null) {
            driver.close();
        }
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        List<String> hashes = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        Map<String, String> before = null;
        Map<String, String> after = null;
        RolloutResult rolled = null;
        boolean reached = false;
        for (int i = 0; i < WAIT_LIMIT && hashes.size() < AFTER; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                serve();
                continue;
            }
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                hashes.add("end:" + halt.reason());
                break;
            }
            Observation observation = new Observer().observe();
            if (halt.waitIndex() == k) {
                reached = true;
                if (roll) {
                    SnapshotStore store = new SnapshotStore();
                    SnapshotHandle live = store.take(driver);
                    SnapshotHandle copy = new SnapshotScrubber(store).scrub(live, sample(1));
                    before = profileFiles();
                    rolled = new RolloutHost(store, driver).simulate(copy, rollout);
                    after = profileFiles();
                }
            }
            if (reached) {
                hashes.add(halt.waitIndex() + ":" + observation.hash());
            }
            Action chosen = brain.decide(observation);
            if (reached && actions.size() < ROLLOUT_LENGTH) {
                actions.add(chosen);
            }
            executor.execute(observation, chosen);
        }
        assertTrue(reached, "the Run reaches wait " + k);
        return new Course(hashes, actions, before, after, rolled);
    }

    @Test
    @DisplayName("after a rollout the Run reads wait for wait as the twin that never rolled out")
    void the_run_after_a_rollout_is_the_twins() throws IOException {
        long k = findFight();
        Course twin = play(k, false, List.of());
        Course rolled = play(k, true, twin.actions());
        assertEquals(twin.hashes(), rolled.hashes(), "from wait " + k + " the disturbed Run reads as the twin");
        assertTrue(rolled.hashes().size() >= 30, "the comparison covers a stretch of play: " + rolled.hashes().size());
        RolloutResult result = rolled.rolled();
        assertTrue(result.applied() >= 1, "the rollout applied something: " + result);
        assertTrue(result.observations().size() >= 2, "and reached more than its first wait: " + result.observations().size());
        assertEquals(rolled.before(), rolled.after(), "the profile's files are as the rollout found them");
    }

    @Test
    @DisplayName("a rollout from a handle the host's store did not make as a scrubbed copy rolls out nothing")
    void a_foreign_handle_rolls_out_nothing() {
        long k = findFight();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        RolloutHost host = new RolloutHost(store, driver);
        String before = new Observer().observe().hash();
        int held = store.size();
        List<Action> any = List.of(new Action.Wait());

        // the live snapshot says scrubbed over its own id
        assertThrows(IllegalArgumentException.class, () -> host.simulate(new SnapshotHandle(live.id(), live.k(), true), any));
        // an id this store never held
        assertThrows(IllegalArgumentException.class, () -> host.simulate(new SnapshotHandle("elsewhere", k, true), any));
        // the honest flag, which the Simulator refuses before the host is reached
        assertThrows(IllegalArgumentException.class, () -> host.simulate(live, any));
        // a copy another store made
        SnapshotStore other = new SnapshotStore();
        SnapshotHandle theirs = new SnapshotScrubber(other).scrub(other.take(driver), sample(1));
        assertThrows(IllegalArgumentException.class, () -> host.simulate(theirs, any));

        assertEquals(held, store.size(), "no snapshot was taken for a refused handle");
        assertEquals(before, new Observer().observe().hash(), "the Run was not touched");
    }

    /** A hero standing on the way down, so a single Descend is a floor change. */
    private void standOnTheStairs() {
        var hero = Dungeon.hero;
        int exit = Dungeon.level.exit();
        hero.pos = exit;
        hero.sprite.place(exit);
        Dungeon.observe();
    }

    private Observation lastOf(RolloutResult result) {
        return result.observations().get(result.observations().size() - 1);
    }

    @Test
    @DisplayName("a descent inside a rollout builds the floor from the sample's seed, not the Run's")
    void a_descent_builds_the_samples_floor() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        driver.stepToInputWait();
        standOnTheStairs();
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle live = store.take(driver);
        SnapshotScrubber scrubber = new SnapshotScrubber(store);
        SnapshotHandle one = scrubber.scrub(live, sample(1));
        SnapshotHandle two = scrubber.scrub(live, sample(2));
        RolloutHost host = new RolloutHost(store, driver);
        List<Action> descend = List.of(new Action.Descend());

        RolloutResult first = host.simulate(one, descend);
        RolloutResult again = host.simulate(one, descend);
        RolloutResult other = host.simulate(two, descend);
        for (RolloutResult result : List.of(first, again, other)) {
            assertEquals(RolloutEnd.HORIZON, result.end());
            assertEquals(1, result.applied());
            assertEquals(2, lastOf(result).header().depth(), "the rollout stands on the next floor");
        }
        assertEquals(lastOf(first).map(), lastOf(again).map(), "one sample builds one floor");
        assertNotEquals(lastOf(first).map(), lastOf(other).map(), "another sample builds another");

        // The live Run, restored by the host, descends to the floor its own seed makes.
        assertEquals(1, Dungeon.depth, "the live Run is back on its floor");
        Observation here = new Observer().observe();
        assertTrue(new ActionExecutor().execute(here, new Action.Descend()) instanceof org.shatterfish.harness.executor.Outcome.Applied);
        assertEquals(HeadlessDriver.Reason.SCENE_SWITCH, driver.stepToInputWait().reason());
        serve();
        driver.stepToInputWait();
        Observation liveFloor = new Observer().observe();
        assertEquals(2, liveFloor.header().depth());
        assertNotEquals(lastOf(first).map(), liveFloor.map(), "the sample's floor is not the live seed's");
        assertNotEquals(lastOf(other).map(), liveFloor.map(), "nor is the other sample's");
    }

    @Test
    @DisplayName("the profile guard puts back the journal, the rankings and the files a rollout wrote")
    void the_profile_guard_puts_the_profile_back() throws IOException {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        driver.stepToInputWait();
        Map<String, String> files = profileFiles();
        boolean hadPage = Document.ADVENTURERS_GUIDE.isPageFound(Document.GUIDE_INTRO);
        int total = Rankings.INSTANCE.totalNumber;
        var records = Rankings.INSTANCE.records;
        boolean hadRat = com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary.isSeen(
                com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat.class);
        ProfileGuard guard = ProfileGuard.hold(driver);

        Path folder = driver.headlessBoot().profile();
        Files.writeString(folder.resolve("bones.dat"), "grave");
        Files.writeString(folder.resolve("rankings.dat"), "ranked");
        Document.ADVENTURERS_GUIDE.findPage(Document.GUIDE_INTRO);
        Rankings.INSTANCE.totalNumber += 7;
        Rankings.INSTANCE.records = new ArrayList<>();
        com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary.setSeen(com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat.class);

        guard.restore();
        assertEquals(files, profileFiles());
        assertEquals(hadPage, Document.ADVENTURERS_GUIDE.isPageFound(Document.GUIDE_INTRO));
        assertEquals(total, Rankings.INSTANCE.totalNumber);
        assertEquals(records, Rankings.INSTANCE.records);
        assertEquals(hadRat, com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary.isSeen(
                com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat.class));
    }
}
