package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubRow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The search leak test (story 6.3, FR-13, ADR-0010's choice rule 1): a search's Decision does not depend on anything the
 * player cannot know. For each mid-fight snapshot of the {@code smoke} set the test builds alternate worlds by scrubbing
 * the live snapshot with N different samples, which differ from the live Run and from each other in every row of the
 * ADR-0009 table; first checks that every alternate shows the player the Observation the live Run does, hash for
 * hash, and then runs each search in the original and in every alternate and requires the Decisions to be the same
 * bytes: the chosen Action, the alternatives in order, the scores and the reasons, compared as the canonical JSON of
 * the Decision record and not as a hash.
 *
 * <p>Two searches are covered, which are every search the program has: the one-ply model of story 6.1, which decides
 * from the Observation and the Codex alone, and the probe search ({@code ProbeSearch}), which spends its budget on
 * rollouts through the {@code Simulator} and is a test fixture so that the rollout path is exercised before any real
 * candidate exists. Each of the true seed, unseen mob positions, unidentified item classes, hidden traps and secret
 * doors is also its own case: the alternates then differ from the live Run in that element's rows alone, and a
 * failure says which element leaked.
 *
 * <p><b>The test can fail.</b> The negative controls plant a leak in each of four forms (a rollout from the live
 * snapshot's handle with the scrubbed flag forged; a read of the true seed; a probe whose scrubber leaves one row out;
 * a read of an unseen mob's position), in each element's own form, and in the form of a scrub refusal that tracks the
 * hidden state, and require the same comparison to catch each. A control that is not caught fails the test.
 *
 * <p><b>What it does not claim.</b> The test proves independence from the hidden state it can vary, which is the rows
 * of ADR-0009's table and nothing beyond. A hidden element outside the table is outside the proof until the table grows,
 * and a hidden element the smoke snapshots do not hold (a chest in view, a boss floor, a quest NPC: the scrub refuses
 * them, and a refused snapshot is not searched from) is outside it too. The probe's rollouts are three turns long, so a
 * row that matters only on a longer line (the dungeon seed matters on a descent) is held by its own case only through
 * the read of it, not through a rollout that reaches a new floor. It is a necessary condition for fairness and not a
 * sufficient one, which is why the fairness reviewer still reads every search diff. The one thing it deliberately does
 * not equate across worlds is the number of scrubs a world refuses: a refusal depends on the true state by a fraction of
 * a bit, a search must treat it as "no sample" and never as evidence, and the probe's Decision records the count, so a
 * refusal that tracks the hidden state is a failure here (see the control).
 */
@Timeout(value = 60, unit = TimeUnit.MINUTES)
class SearchLeakTest {

    private static final long SALT = 0x5A17_5A17L;
    /** The alternate worlds per snapshot for the all-rows case (the acceptance criterion says at least eight). */
    private static final int N = 8;
    /** The alternate worlds per snapshot for a single element, and for a control. */
    private static final int N_ELEMENT = 3;
    private static final int WAIT_LIMIT = 1_500;
    private static final long GAP = 30;
    /** The seed of the search's own stream, as a Brain's seed is. */
    private static final long SEARCH_SEED = 9L;
    /** How many of the seven smoke entries must arrive at a fight the scrub accepts (the Brain's own Run may end first). */
    private static final int MIN_SNAPSHOTS = 6;

    private HeadlessDriver driver;
    private Brain brain;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    // --- the fixtures: a Run stopped at a mid-fight wait

    private record Fixture(long seed, HeroClass heroClass, SnapshotStore store, SnapshotHandle live, Observation shown) {
        long waitIndex() {
            return live.k();
        }

        String name() {
            return heroClass + "/" + seed + "@" + live.k();
        }
    }

    /** The smoke set's entries to play: the first two Warriors (whose fights the differential test also uses) and the first of each other class. */
    private static List<Map.Entry<HeroClass, Long>> smokeEntries() throws IOException {
        String text = Files.readString(RealBrain.root().resolve("seeds").resolve("smoke.json"));
        Matcher entry = Pattern.compile("\"heroClass\":\"([A-Z]+)\",\"seed\":(\\d+)").matcher(text);
        List<Map.Entry<HeroClass, Long>> out = new ArrayList<>();
        Map<HeroClass, Integer> taken = new LinkedHashMap<>();
        while (entry.find()) {
            HeroClass hero = HeroClass.valueOf(entry.group(1));
            int limit = hero == HeroClass.WARRIOR ? 2 : 1;
            if (taken.merge(hero, 1, Integer::sum) <= limit) {
                out.add(Map.entry(hero, Long.parseLong(entry.group(2))));
            }
        }
        assertEquals(7, out.size(), "the smoke set holds the six classes, the Warrior twice: " + out);
        return out;
    }

    private void bootBrain() {
        HeadlessBoot.ensure();
        java.nio.file.Path root = RealBrain.root();
        String tag = HeadlessBoot.pinnedTag();
        Codex.Knowledge codex = CodexKnowledge.read(root.resolve("codex").resolve(tag), tag);
        brain = new Brain(codex, RealBrain.weights(root), SEARCH_SEED, Brain.Model.ONE_PLY);
    }

    private interface Check {
        /** Whether to stop: true once a control has what it needs, so the rest of the snapshots are not played. */
        boolean on(Fixture f) throws IOException;
    }

    /** Runs {@code check} on the first mid-fight snapshot of each smoke entry in play, one Run at a time. */
    private void forEachFixture(Check check) throws IOException {
        bootBrain();
        int arrived = 0;
        for (Map.Entry<HeroClass, Long> entry : smokeEntries()) {
            Fixture f = fixture(entry.getValue(), entry.getKey());
            if (f == null) {
                continue;
            }
            arrived++;
            if (check.on(f)) {
                return;
            }
        }
        assertTrue(arrived >= MIN_SNAPSHOTS, "the smoke entries gave " + arrived + " mid-fight snapshots, and at least "
                + MIN_SNAPSHOTS + " are the test");
    }

    private Fixture fixture(long seed, HeroClass heroClass) {
        if (driver != null) {
            driver.close();
        }
        driver = HeadlessDriver.start(seed, heroClass, SALT);
        BrainDecider player = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        for (int i = 0; i < WAIT_LIMIT; i++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                continue;
            }
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                // The Brain's Run ended (or stopped) before a fight the scrub accepts: this entry gives no snapshot.
                System.out.println("SEARCHLEAK no snapshot for " + heroClass + " seed " + seed + ": " + halt.reason() + " at wait "
                        + halt.waitIndex());
                return null;
            }
            Observation observation = new Observer().observe();
            if (fightable(observation) && halt.waitIndex() >= GAP && scrubAccepts(observation)) {
                // A trap and a door the player has not found, planted where the hero can see their cells (they read as
                // floor and wall), so that the two rows have something to replace in every snapshot.
                String planted = plant();
                Observation after = new Observer().observe();
                assertEquals(observation.hash(), after.hash(), "a hidden trap and a secret door do not change the screen");
                SnapshotStore store = new SnapshotStore();
                try {
                    SnapshotHandle live = store.take(driver);
                    new SnapshotScrubber(store).scrub(live, sampleN(1, after));
                    System.out.println("SEARCHLEAK fixture " + heroClass + " seed " + seed + " wait " + live.k() + " planted " + planted);
                    return new Fixture(seed, heroClass, store, live, after);
                } catch (IllegalStateException | Scrub.Refused refused) {
                    // planted state the scrub declines: the next wait
                }
            }
            executor.execute(observation, player.decide(observation));
        }
        System.out.println("SEARCHLEAK no snapshot for " + heroClass + " seed " + seed + ": no accepted fight in " + WAIT_LIMIT + " waits");
        return null;
    }

    /** Whether the scrub accepts the Run as it stands (a window in front or a chest in view does not). */
    private boolean scrubAccepts(Observation observation) {
        SnapshotStore store = new SnapshotStore();
        try {
            SnapshotHandle probe = store.take(driver);
            new SnapshotScrubber(store).scrub(probe, sampleN(1, observation));
            return true;
        } catch (IllegalStateException | Scrub.Refused refused) {
            return false;
        }
    }

    private static boolean fightable(Observation observation) {
        return observation.actors().actors().stream().anyMatch(a -> a.alignment() == Alignment.ENEMY);
    }

    /** Plants a hidden trap on free floor in view and a secret door in a wall in view; returns what it planted. */
    private static String plant() {
        Level level = Dungeon.level;
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
        StringBuilder planted = new StringBuilder();
        if (trapCell >= 0) {
            Trap hidden = new FrostTrap();
            level.setTrap(hidden.hide(), trapCell);
            Level.set(trapCell, Terrain.SECRET_TRAP);
            planted.append("trap");
        }
        if (wall >= 0) {
            Level.set(wall, Terrain.SECRET_DOOR);
            planted.append(planted.length() > 0 ? "+door" : "door");
        }
        return planted.toString();
    }

    /** Sample {@code n} of the alternates: the draws differ with n, and so do the remembered mobs, placed from the screen. */
    private static org.shatterfish.api.BeliefSample sampleN(long n, Observation shown) {
        return ProbeSearch.sampleFrom(new SplittableRandom(0x1234_5678_9ABCL * n + 17), shown);
    }

    // --- the worlds

    /** Alternate worlds of {@code f}: scrubs of the live snapshot by {@code rows}, under samples 1.., {@code n} accepted. */
    private List<SnapshotHandle> alternates(Fixture f, Set<ScrubRow> rows, int n) {
        SnapshotScrubber scrubber = new SnapshotScrubber(f.store(), rows);
        List<SnapshotHandle> worlds = new ArrayList<>();
        int refused = 0;
        for (long s = 1; worlds.size() < n && s <= 4L * n; s++) {
            try {
                worlds.add(scrubber.scrub(f.live(), sampleN(s, f.shown())));
            } catch (Scrub.Refused refusal) {
                refused++;
            }
        }
        assertEquals(n, worlds.size(), "enough samples scrub " + f.name() + " (" + refused + " refused)");
        return worlds;
    }

    private Observation enterOriginal(Fixture f) {
        f.store().restore(f.live(), driver);
        return arrive(f);
    }

    private Observation enter(Fixture f, SnapshotHandle alternate) {
        f.store().restoreScrubbed(alternate, driver);
        return arrive(f);
    }

    private Observation arrive(Fixture f) {
        HeadlessDriver.Halt halt = driver.stepToInputWait();
        assertEquals(HeadlessDriver.Reason.INPUT_WAIT, halt.reason(), "the world stands at a wait: " + halt);
        assertEquals(f.waitIndex(), halt.waitIndex(), "the world stands at the snapshot's own wait");
        return new Observer().observe();
    }

    // --- the searches

    /** What a search said in one world: the canonical bytes of the Decision (and the Action), and a line to read. */
    private record Said(byte[] bytes, String text, String policy, int refused, int rolled) {
    }

    private interface Search {
        String name();

        Said decide(Fixture f, Observation observation);
    }

    private static Said said(long wait, Action action, RunLog.Decision decision, String why, int refused, int rolled) {
        String text = decision == null ? "no Decision: " + why + " / " + action
                : action + " / " + RunLogJson.canonical(new RunLog.Shadow(wait, decision));
        return new Said(text.getBytes(StandardCharsets.UTF_8), text, decision == null ? "" : decision.policy(), refused, rolled);
    }

    /** The one-ply Brain on the real Codex, which decides from the Observation alone. */
    private Search onePly() {
        return new Search() {
            @Override
            public String name() {
                return "the one-ply model";
            }

            @Override
            public Said decide(Fixture f, Observation observation) {
                Brain.Decided decided = brain.decide(observation, brain.update(observation, null));
                return said(f.waitIndex(), decided.action(), decided.decision(), decided.why(), 0, 0);
            }
        };
    }

    private Search probe(ProbeSearch.Config config, String name) {
        return new Search() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Said decide(Fixture f, Observation observation) {
                ProbeSearch.Found found = ProbeSearch.decide(
                        new ProbeSearch.Context(driver, f.store(), observation, f.waitIndex(), SEARCH_SEED), config);
                return said(f.waitIndex(), found.decision().chosen().action(), found.decision(), "", found.refused(), found.rolled());
            }
        };
    }

    private Search honestProbe() {
        return probe(ProbeSearch.Config.honest(), "the probe search");
    }

    /**
     * Runs {@code search} in the live Run's world and in each alternate, and fails with "SEARCH LEAK [case]" at the
     * first alternate whose Decision is not the original's byte for byte. A world that does not show the player the
     * original's screen is a failure of a different kind (the test's own precondition), and says so differently.
     */
    private void assertNoLeak(String caseName, Fixture f, List<SnapshotHandle> alternates, Search search, Stats stats) {
        Observation shown = enterOriginal(f);
        assertEquals(f.shown().hash(), shown.hash(), caseName + ": the live snapshot restores to the screen it was taken on");
        Said original = search.decide(f, shown);
        stats.record(search, original);
        int index = 0;
        for (SnapshotHandle alternate : alternates) {
            index++;
            Observation screen = enter(f, alternate);
            if (!shown.hash().equals(screen.hash())) {
                throw new IllegalStateException("PRECONDITION: alternate world " + index + " of " + f.name() + " [" + caseName
                        + "] is not the original's screen, so no Decision need agree:\noriginal:\n" + shown.json()
                        + "\nalternate:\n" + screen.json());
            }
            Said said = search.decide(f, screen);
            stats.record(search, said);
            if (!Arrays.equals(original.bytes(), said.bytes())) {
                throw new AssertionError("SEARCH LEAK [" + caseName + "]: " + search.name() + " decided differently in alternate world "
                        + index + " of " + alternates.size() + " at " + f.name() + ", though the screen is the same:\noriginal:  "
                        + original.text() + "\nalternate: " + said.text());
            }
        }
        enterOriginal(f);
    }

    /** What the searches did, for the Results page and for the test to know it was not vacuous. */
    private static final class Stats {
        int decisions;
        int onePlyTook;
        int rollouts;
        int refusedScrubs;
        final Map<String, Integer> policies = new LinkedHashMap<>();

        void record(Search search, Said said) {
            decisions++;
            rollouts += said.rolled();
            refusedScrubs += said.refused();
            policies.merge(said.policy(), 1, Integer::sum);
            if (said.policy().equals("one-ply")) {
                onePlyTook++;
            }
        }
    }

    // --- the tests

    @Test
    @DisplayName("every row varied: the one-ply model and the probe search decide the same bytes in the original and in eight alternate worlds")
    void no_search_depends_on_the_hidden_state() throws IOException {
        Stats stats = new Stats();
        int[] worlds = {0};
        Set<HiddenPeek> held = EnumSet.noneOf(HiddenPeek.class);
        forEachFixture(f -> {
            List<SnapshotHandle> alternates = alternates(f, EnumSet.allOf(ScrubRow.class), N);
            assertTrue(alternates.size() >= 8);
            // The alternates differ from each other and from the live Run in the hidden state (the differential test
            // proves it row by row, and the reads below show it here for the elements the snapshot holds); they are
            // asked for the same screen first, and the Decisions second.
            assertEquals(N, alternates.stream().map(SnapshotHandle::id).distinct().count(), "eight different samples");
            enterOriginal(f);
            Map<HiddenPeek, Long> truth = new java.util.EnumMap<>(HiddenPeek.class);
            for (HiddenPeek element : HiddenPeek.values()) {
                truth.put(element, element.peek());
            }
            for (SnapshotHandle alternate : alternates) {
                enter(f, alternate);
                for (HiddenPeek element : HiddenPeek.values()) {
                    if (truth.get(element) != 1L) {
                        // 1 is a read of nothing there (no hidden trap, no unseen mob); a held element must have moved.
                        assertTrue(element.peek() != truth.get(element), f.name() + ": " + element.what()
                                + " is held by the snapshot and an alternate world has it as the live Run does");
                        held.add(element);
                    }
                }
            }
            for (Search search : List.of(onePly(), honestProbe())) {
                assertNoLeak("all rows", f, alternates, search, stats);
            }
            worlds[0] += alternates.size();
            return false;
        });
        System.out.println("SEARCHLEAK all-rows decisions " + stats.decisions + " worlds " + worlds[0] + " rollouts " + stats.rollouts
                + " refused scrubs " + stats.refusedScrubs + " policies " + stats.policies);
        assertEquals(EnumSet.allOf(HiddenPeek.class), held, "the snapshots held every hidden element, and every alternate replaced it");
        assertTrue(stats.rollouts > 0, "the probe rolled out: the rollout path is exercised");
        assertEquals(0, stats.refusedScrubs, "no scrub of an alternate world was refused: a refusal is no sample, and none was needed");
        assertTrue(stats.onePlyTook > 0, "the one-ply model took at least one wait, so its half of the test is not vacuous: " + stats.policies);
    }

    @Test
    @DisplayName("each hidden element alone: the true seed, unseen mob positions, unidentified item classes, hidden traps and secret doors")
    void no_hidden_element_moves_a_decision() throws IOException {
        Map<HiddenPeek, String> failures = new LinkedHashMap<>();
        Stats stats = new Stats();
        forEachFixture(f -> {
            for (HiddenPeek element : HiddenPeek.values()) {
                List<SnapshotHandle> alternates = alternates(f, element.rows(), N_ELEMENT);
                for (Search search : List.of(onePly(), honestProbe())) {
                    try {
                        assertNoLeak(element.what(), f, alternates, search, stats);
                    } catch (AssertionError leak) {
                        failures.merge(element, leak.getMessage(), (a, b) -> a);
                    }
                }
            }
            return false;
        });
        System.out.println("SEARCHLEAK elements decisions " + stats.decisions + " rollouts " + stats.rollouts);
        assertTrue(failures.isEmpty(), "hidden elements that reached a Decision: " + failures);
    }

    // --- the negative controls: a leak planted in each form must be caught by the same comparison

    /** Plays the control in one fixture: the first "SEARCH LEAK" it raises, or null when the comparison found none. */
    private String leakFound(Fixture f, Set<ScrubRow> worldRows, String caseName, Search search) {
        try {
            assertNoLeak(caseName, f, alternates(f, worldRows, N_ELEMENT), search, new Stats());
            return null;
        } catch (AssertionError leak) {
            if (!leak.getMessage().startsWith("SEARCH LEAK [" + caseName + "]")) {
                throw leak;
            }
            return leak.getMessage();
        }
    }

    /** Plays the control on each fixture in turn and returns the first leak found. */
    private String leakFound(Set<ScrubRow> worldRows, String caseName, Search search) throws IOException {
        String[] found = {null};
        forEachFixture(f -> {
            found[0] = found[0] != null ? found[0] : leakFound(f, worldRows, caseName, search);
            return found[0] != null;
        });
        return found[0];
    }

    private void assertCaught(String form, String found, String caseName) {
        System.out.println("SEARCHLEAK control caught: " + form);
        assertTrue(found != null, "the planted leak was NOT caught: " + form + ". A test that cannot fail proves nothing.");
        assertTrue(found.startsWith("SEARCH LEAK [" + caseName + "]"), "the failure names its case: " + found);
    }

    @Test
    @DisplayName("control 1: a search handed the live snapshot's handle with the scrubbed flag forged is caught")
    void a_forged_handle_is_caught() throws IOException {
        // The real host refuses the forged handle (RolloutHostRefusalTest). Here the refusal is taken away, so the
        // comparison itself is shown to see what the forged handle lets the search read: the world's own rolls.
        Search forged = probe(ProbeSearch.Config.honest().forgingTheLiveHandle()
                .withHost(c -> new LeakyHost(c.store(), c.driver())), "a probe rolling out from the live handle, flag forged");
        assertCaught("a forged scrubbed flag on the live handle", leakFound(EnumSet.allOf(ScrubRow.class), "forged live handle", forged),
                "forged live handle");
    }

    @Test
    @DisplayName("control 2: a search that reads the true seed through a back door is caught")
    void reading_the_seed_is_caught() throws IOException {
        Search peek = probe(ProbeSearch.Config.honest().peeking(c -> HiddenPeek.TRUE_SEED.peek()), "a probe that reads Dungeon.seed");
        assertCaught("a read of the true seed", leakFound(HiddenPeek.TRUE_SEED.rows(), "the true seed", peek), "the true seed");
    }

    @Test
    @DisplayName("control 3: a probe whose scrubber leaves one row out is caught, naming the row that leaks")
    void a_scrubber_missing_a_row_is_caught() throws IOException {
        // The leaky host takes an incomplete copy, which the real host refuses (RedeterminationDifferentialTest,
        // RolloutHostRefusalTest). Rows whose truth a three-turn rollout cannot reach are not caught, and are listed.
        Set<ScrubRow> caught = EnumSet.noneOf(ScrubRow.class);
        int[] played = {0};
        forEachFixture(f -> {
            for (ScrubRow missing : ScrubRow.values()) {
                EnumSet<ScrubRow> rows = EnumSet.allOf(ScrubRow.class);
                rows.remove(missing);
                Search probe = probe(ProbeSearch.Config.honest().withRows(rows).withHost(c -> new LeakyHost(c.store(), c.driver())),
                        "a probe whose scrubber lacks " + missing);
                if (!caught.contains(missing)
                        && leakFound(f, EnumSet.allOf(ScrubRow.class), "scrubber without " + missing, probe) != null) {
                    caught.add(missing);
                }
            }
            // Three snapshots say which rows a three-turn rollout reads from a fight; all six cost four minutes and add
            // rows only on layouts (a later fixture caught UNKNOWN_CELLS and SECRET_TRAPS_AND_DOORS).
            return ++played[0] == 3;
        });
        Set<ScrubRow> notReached = EnumSet.complementOf(EnumSet.copyOf(caught));
        System.out.println("SEARCHLEAK control 3 caught rows " + caught + " ; not reached by a three-turn rollout " + notReached);
        // The rows a three-turn rollout from a fight always reads: the mobs that stand there. The cells around them are read on
        // some layouts and not on others, so UNKNOWN_CELLS is listed when caught and not required. The salt is
        // the Run's and is shared by every world in one driver, so no comparison between these worlds can see it left
        // alone; RedeterminationDifferentialTest holds that row (it reads the seed the copy rolls by).
        for (ScrubRow row : List.of(ScrubRow.MOB_AI_AND_HP, ScrubRow.MOBS_OUT_OF_VIEW)) {
            assertTrue(caught.contains(row), "a scrubber that leaves " + row + " alone is caught: " + caught);
        }
    }

    @Test
    @DisplayName("control 4: a search that reads an unseen mob's position is caught")
    void reading_an_unseen_mob_is_caught() throws IOException {
        Search peek = probe(ProbeSearch.Config.honest().peeking(c -> HiddenPeek.UNSEEN_MOB_POSITIONS.peek()),
                "a probe that reads where the unseen mobs stand");
        assertCaught("a read of an unseen mob's position",
                leakFound(HiddenPeek.UNSEEN_MOB_POSITIONS.rows(), "unseen mob positions", peek), "unseen mob positions");
    }

    @Test
    @DisplayName("control 5: each remaining element's case fails with its own message when a search reads it")
    void every_element_case_can_fail() throws IOException {
        Map<HiddenPeek, String> found = new LinkedHashMap<>();
        forEachFixture(f -> {
            for (HiddenPeek element : List.of(HiddenPeek.UNIDENTIFIED_ITEM_CLASSES, HiddenPeek.HIDDEN_TRAPS, HiddenPeek.SECRET_DOORS)) {
                Search peek = probe(ProbeSearch.Config.honest().peeking(c -> element.peek()), "a probe that reads " + element.what());
                if (!found.containsKey(element)) {
                    String leak = leakFound(f, element.rows(), element.what(), peek);
                    if (leak != null) {
                        found.put(element, leak);
                    }
                }
            }
            return found.size() == 3;
        });
        for (HiddenPeek element : List.of(HiddenPeek.UNIDENTIFIED_ITEM_CLASSES, HiddenPeek.HIDDEN_TRAPS, HiddenPeek.SECRET_DOORS)) {
            assertCaught("a read of " + element.what(), found.get(element), element.what());
        }
    }

    @Test
    @DisplayName("control 6: a scrub refusal that tracks the hidden state is seen, not absorbed")
    void a_refusal_that_tracks_the_truth_is_caught() throws IOException {
        // ADR-0009's amendment: a refusal is never evidence. A refusal that happens exactly when a hidden fact holds is the
        // fact leaking through the one bit it has, and the Decision records how many scrubs refused, so it moves.
        Search refusing = probe(ProbeSearch.Config.honest().refusingWhen(c -> HiddenPeek.TRUE_SEED.peek() % 2 == 0),
                "a probe whose scrub refuses when the seed is even");
        assertCaught("a refusal that tracks the true seed", leakFound(HiddenPeek.TRUE_SEED.rows(), "refusal as evidence", refusing),
                "refusal as evidence");
    }

    @Test
    @DisplayName("control 7: the comparison sees a difference the screen does show (the one-ply model's input)")
    void a_difference_on_the_screen_moves_the_one_ply_decision() throws IOException {
        // Two worlds that differ in what the player sees must be allowed to decide differently: here the hero's hit
        // points, which the status pane shows, are cut, and the one-ply model's Decision (its scores) must move, or the
        // equality above would hold of any function whatever.
        boolean[] moved = {false};
        forEachFixture(f -> {
            Observation shown = enterOriginal(f);
            Said before = onePly().decide(f, shown);
            Dungeon.hero.HP = Math.max(1, Dungeon.hero.HP / 4);
            Observation hurt = new Observer().observe();
            assertTrue(!hurt.hash().equals(shown.hash()), "the hero's hit points are on the screen");
            Said after = onePly().decide(f, hurt);
            moved[0] |= !Arrays.equals(before.bytes(), after.bytes());
            return moved[0];
        });
        assertTrue(moved[0], "a quarter of the hero's health moved no Decision on any snapshot: the comparison could not fail");
    }
}
