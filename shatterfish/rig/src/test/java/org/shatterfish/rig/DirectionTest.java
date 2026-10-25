package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The direction check: which triples it plays, what it refuses, and the numbers it reads from logs. */
class DirectionTest {

    private static final Path ROOT = SeedSetsTest.ROOT;

    private static final String ZERO = "0".repeat(64);

    // ------------------------------------------------------------------ selection

    @Test
    @DisplayName("the tuning set: the first 40 Warriors of standard in file order, salts 1000 + index")
    void the_tuning_set() {
        List<DirectionSelect.Pick> picks = DirectionSelect.select(ROOT, "standard", HeroClass.WARRIOR, 40, 1000);
        List<SeedSet.Entry> warriors = SeedSets.set("standard").entries().stream()
                .filter(e -> e.heroClass() == HeroClass.WARRIOR).limit(40).toList();

        assertEquals(40, picks.size());
        for (int i = 0; i < 40; i++) {
            assertEquals(warriors.get(i), picks.get(i).entry(), "file order, after the filter");
            assertEquals(1000 + i, picks.get(i).salt(), "the salt counts the selection, not the file");
        }
    }

    @Test
    @DisplayName("without a class every entry is a candidate, first N cuts in file order, and the salt base moves")
    void unfiltered_and_cut() {
        List<DirectionSelect.Pick> picks = DirectionSelect.select(ROOT, "smoke", null, 7, 5);
        List<SeedSet.Entry> file = SeedSets.set("smoke").entries();

        assertEquals(7, picks.size());
        assertEquals(file.subList(0, 7), picks.stream().map(DirectionSelect.Pick::entry).toList());
        assertEquals(5, picks.get(0).salt());
        assertEquals(11, picks.get(6).salt());
    }

    @Test
    @DisplayName("no cut is every entry after the filter, and a cut longer than the set is the set")
    void no_cut() {
        assertEquals(25, DirectionSelect.select(ROOT, "smoke", null, -1, 0).size());
        assertEquals(25, DirectionSelect.select(ROOT, "smoke", null, 9999, 0).size());
        List<DirectionSelect.Pick> clerics = DirectionSelect.select(ROOT, "smoke", HeroClass.CLERIC, -1, 0);
        assertTrue(clerics.size() >= 4 && clerics.size() < 25);
        assertTrue(clerics.stream().allMatch(p -> p.entry().heroClass() == HeroClass.CLERIC));
        assertEquals(clerics.size() - 1, clerics.get(clerics.size() - 1).salt());
    }

    @Test
    @DisplayName("a class is named in any case, and a class the game lacks is refused")
    void class_names() {
        assertEquals(HeroClass.WARRIOR, DirectionSelect.heroClass("warrior"));
        assertThrows(IllegalArgumentException.class, () -> DirectionSelect.heroClass("bard"));
    }

    @Test
    @DisplayName("goo is refused as registered-use only, holdout as never for development, unknown sets by name")
    void refusals() {
        IllegalArgumentException goo = assertThrows(IllegalArgumentException.class,
                () -> DirectionSelect.select(ROOT, "goo", null, 5, 0));
        assertTrue(goo.getMessage().contains("Registration"), goo.getMessage());
        IllegalArgumentException holdout = assertThrows(IllegalArgumentException.class,
                () -> DirectionSelect.select(ROOT, "holdout", null, 5, 0));
        assertTrue(holdout.getMessage().contains("development read"), holdout.getMessage());
        assertThrows(IllegalArgumentException.class, () -> DirectionSelect.select(ROOT, "nonesuch", null, 5, 0));
    }

    @Test
    @DisplayName("the command line refuses a flag it does not know, a flag without a value, and a flag given twice")
    void command_line() {
        assertEquals(Map.of("--set", "smoke", "--first", "3"),
                Direction.arguments(new String[] {"--set", "smoke", "--first", "3"}));
        assertThrows(IllegalArgumentException.class, () -> Direction.arguments(new String[] {"--seeds", "smoke"}));
        // The debug record flag takes no value, in any position (issue #219).
        assertEquals("true", Direction.arguments(new String[] {"--debug-record", "--set", "smoke"}).get("--debug-record"));
        assertEquals("smoke", Direction.arguments(new String[] {"--set", "smoke", "--debug-record"}).get("--set"));
        assertThrows(IllegalArgumentException.class,
                () -> Direction.arguments(new String[] {"--debug-record", "--debug-record"}));
        assertThrows(IllegalArgumentException.class, () -> Direction.arguments(new String[] {"--set"}));
        assertThrows(IllegalArgumentException.class,
                () -> Direction.arguments(new String[] {"--set", "smoke", "--set", "bosses"}));
        // Nothing is played for a refused set: the refusal comes before any folder is made.
        assertThrows(IllegalArgumentException.class,
                () -> Direction.run(Map.of("--set", "goo", "--root", ROOT.toString())));
        assertThrows(IllegalArgumentException.class,
                () -> Direction.run(Map.of("--set", "holdout", "--root", ROOT.toString())));
    }

    // ------------------------------------------------------------------ baseline plumbing

    @Test
    @DisplayName("the baseline's Brain replaces the classpath's one Brain, wherever it sits, and only that")
    void swap() {
        String sep = File.pathSeparator;
        String cp = String.join(sep, "/a/api-4.0.0.jar", "/r/shatterfish/brain/build/libs/brain-4.0.0.jar", "/h.jar");
        assertEquals(String.join(sep, "/a/api-4.0.0.jar", Path.of("/x/brain-4.0.0.jar").toAbsolutePath().toString(),
                "/h.jar"), Direction.swapBrain(cp, Path.of("/x/brain-4.0.0.jar")));
        String classes = String.join(sep, "/a/api.jar", "/r/shatterfish/brain/build/classes/java/main",
                "/r/shatterfish/brain/build/resources/main", "/h.jar");
        assertEquals(String.join(sep, "/a/api.jar", Path.of("/x/b.jar").toAbsolutePath().toString(), "/h.jar"),
                Direction.swapBrain(classes, Path.of("/x/b.jar")));
        assertThrows(IllegalStateException.class, () -> Direction.swapBrain("/a/api.jar" + sep + "/h.jar",
                Path.of("/x/b.jar")));
    }

    @Test
    @DisplayName("a baseline folder is the folder, or the current/ folder of an earlier direction check")
    void baseline_folder(@TempDir Path root) throws IOException {
        assertEquals(null, Direction.folderOf("v4.0.0-not-a-folder-" + System.nanoTime()));
        assertEquals(root, Direction.folderOf(root.toString()));
        Path current = Files.createDirectories(root.resolve("current"));
        assertEquals(current, Direction.folderOf(root.toString()));
    }

    // ------------------------------------------------------------------ metrics on fixture logs

    private static RunLog.Wait wait(long k, int depth, Action action, String why, String policy) {
        RunLog.Decision decision = policy == null ? null : new RunLog.Decision("act",
                new RunLog.Choice(action, 1, why), List.of(), List.of(), policy);
        return new RunLog.Wait(k, k * 1000, depth, 0, ZERO, Map.of("map", ZERO), action, true, RunLog.BOT,
                decision, decision == null ? "" : ZERO, List.of(), 1);
    }

    private static void log(Path folder, long seed, long salt, List<RunLog.Wait> waits, RunLog.Outcome outcome)
            throws IOException {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0,
                seed, SeedSet.code(seed), salt, 20_000, 3, 2, 8, new RunLog.Brain("shatterfish", "abc1234", ZERO),
                "", false, "a laptop", "2026-09-25T00:00:00Z");
        List<RunLog> records = new ArrayList<>();
        records.add(header);
        records.addAll(waits);
        if (outcome != null) {
            records.add(new RunLog.End(waits.size(), outcome, true));
        }
        StringBuilder text = new StringBuilder();
        String chain = "";
        for (RunLog record : records) {
            text.append(RunLogJson.line(chain, record)).append('\n');
            chain = RunLogJson.chain(chain, record);
        }
        Files.writeString(folder.resolve(RunLog.fileName(header.runId())), text.toString(), StandardCharsets.UTF_8);
    }

    /** Two Runs: one reaches depth 5 and kills Goo and bounces between two cells; one dies on 3. */
    private static Path fixture(Path root) throws IOException {
        Path folder = Files.createDirectories(root.resolve("current"));
        List<RunLog.Wait> a = new ArrayList<>();
        long k = 0;
        for (int i = 0; i < 8; i++) {
            a.add(wait(k++, 1, new Action.Step(i % 2 == 0 ? 10 : 11), "walk", "explore"));
        }
        a.add(wait(k++, 2, new Action.Search(), "search frontier", "explore"));
        a.add(wait(k++, 2, new Action.Search(), "search-spot 4", "explore"));
        a.add(wait(k++, 5, new Action.Wait(), "rest", "rest"));
        log(folder, 1000, 1, a, new RunLog.Outcome(false, false, 0, 5, 1_500_000, "DEATH", 1));
        List<RunLog.Wait> b = new ArrayList<>();
        b.add(wait(0, 1, new Action.Step(5), "walk", "explore"));
        b.add(wait(1, 3, new Action.Wait(), "rest", null));
        log(folder, 2000, 2, b, new RunLog.Outcome(false, false, 0, 3, 500_000, "DEATH", 0));
        Files.writeString(folder.resolve("WARRIOR-X-0000000000000001.heaps.tsv"),
                "depth\tbranch\tcell\titem\tseen\ttaken\n"
                        + "1\t0\t5\tpotion\t1\t3\n"
                        + "1\t0\t6\tscroll\t2\t200\n"
                        + "2\t0\t7\tring\t5\t-1\n", StandardCharsets.UTF_8);
        // The run index is not a Run log.
        Files.writeString(folder.resolve(RunIndex.RUNS), "{}\n", StandardCharsets.UTF_8);
        return folder;
    }

    @Test
    @DisplayName("metrics read from fixture logs: reach, Goo, turns, depth, deaths, searches, oscillation, heaps")
    void metrics(@TempDir Path root) throws IOException {
        DirectionMetrics.Side side = DirectionMetrics.read(fixture(root));

        assertEquals(2, side.runs());
        assertEquals(1, side.reached5());
        assertEquals(1, side.gooKills());
        assertEquals(1000.0, side.medianTurns(), 1e-9, "turns are thousandths in the log");
        assertEquals(4.0, side.deepestMean(), 1e-9);
        assertEquals(5, side.deepestMax());
        assertEquals(1, side.deathsAt(3));
        assertEquals(1, side.deathsAt(5));
        assertEquals(0, side.deathsAt(4));
        // Floors: depths 1, 2, 5 in the first Run and 1, 3 in the second.
        assertEquals(5, side.floors());
        assertEquals(1, side.searches(), "a search spot is not a search");
        assertEquals(1, side.searchSpots());
        assertEquals(0.2, side.searchesPerFloor(), 1e-9);
        assertEquals(0.4, side.searchWaitsPerFloor(), 1e-9);
        assertEquals(8, side.oscillation(), "eight Steps between two cells");
        assertEquals(3, side.heapsSeen());
        assertEquals(2, side.heapsTaken());
        assertEquals(1, side.slowHeaps(), "198 waits from first sight is slow, 2 is not, untaken is not counted");
        assertEquals(100.0, side.seenToTakenMedian(), 1e-9);
    }

    @Test
    @DisplayName("a ranked or oracle Run in a baseline folder is refused")
    void ranked_baseline(@TempDir Path root) throws IOException {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0,
                9, SeedSet.code(9), 1, 20_000, 3, 2, 8, new RunLog.Brain("shatterfish", "abc1234", ZERO),
                "H-0001@0123456789abcdef", false, "a laptop", "2026-09-25T00:00:00Z");
        Files.writeString(root.resolve(RunLog.fileName(header.runId())),
                RunLogJson.line("", header) + '\n', StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> DirectionMetrics.read(root));
    }

    @Test
    @DisplayName("oscillation needs six consecutive Steps between exactly two cells")
    void oscillation() {
        List<Action> five = steps(1, 2, 1, 2, 1, 9, 9, 9);
        assertEquals(0, DirectionMetrics.oscillation(five), "five then a different cell");
        assertEquals(6, DirectionMetrics.oscillation(steps(1, 2, 1, 2, 1, 2, 7, 8, 9, 3)));
        assertEquals(0, DirectionMetrics.oscillation(steps(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), "a walk is not a bounce");
        List<Action> broken = new ArrayList<>(steps(1, 2, 1, 2, 1, 2));
        broken.add(2, new Action.Wait());
        broken.addAll(steps(5, 6, 7, 8));
        assertEquals(0, DirectionMetrics.oscillation(broken), "a wait between breaks the run of Steps");
    }

    private static List<Action> steps(int... cells) {
        List<Action> actions = new ArrayList<>();
        for (int cell : cells) {
            actions.add(new Action.Step(cell));
        }
        return actions;
    }

    @Test
    @DisplayName("the table: two columns without a baseline, four with one and the deltas signed")
    void table(@TempDir Path root) throws IOException {
        DirectionMetrics.Side side = DirectionMetrics.read(fixture(root));

        String alone = DirectionMetrics.table("this", side, null, null);
        assertTrue(alone.startsWith("| | this |\n|---|---|\n"), alone);
        assertTrue(alone.contains("| reach depth 5 | 1/2 |"), alone);
        assertTrue(alone.contains("| Goo kills | 1/2 |"), alone);
        assertTrue(alone.contains("| median turns | 1000.0 |"), alone);
        assertTrue(alone.contains("| deepest mean / max | 4.000 / 5 |"), alone);
        assertTrue(alone.contains("| deaths by depth 1..5 | 0/0/1/0/1 |"), alone);
        assertTrue(alone.contains("| searches per floor | 0.20 |"), alone);
        assertTrue(alone.contains("| oscillation Steps | 8 |"), alone);
        assertTrue(alone.contains("| heaps taking over 100 waits | 1 of 3 |"), alone);

        Path other = Files.createDirectories(root.resolve("other"));
        log(other, 3000, 3, List.of(wait(0, 1, new Action.Step(5), "walk", "explore")),
                new RunLog.Outcome(false, false, 0, 2, 800_000, "DEATH", 0));
        String both = DirectionMetrics.table("new", side, "old", DirectionMetrics.read(other));
        assertTrue(both.startsWith("| | old | new | delta |\n|---|---|---|---|\n"), both);
        assertTrue(both.contains("| Runs | 1 | 2 | +1 |"), both);
        assertTrue(both.contains("| reach depth 5 | 0/1 | 1/2 | +1 |"), both);
        assertTrue(both.contains("| deaths by depth 1..5 | 0/1/0/0/0 | 0/0/1/0/1 |  |"), both);
        assertTrue(both.contains("| oscillation Steps | 0 | 8 | +8 |"), both);
        assertTrue(both.contains("| deepest mean / max | 2.000 / 2 | 4.000 / 5 | +2 |"), both);
    }
}
