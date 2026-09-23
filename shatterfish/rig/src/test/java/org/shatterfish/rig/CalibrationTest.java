package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calibration of the sequential test (story 3.7).
 *
 * <p>The committed page is the calibration's claim, so the test that matters most is that a fresh
 * render of the committed table is that page, byte for byte: a change to the simulation, the grid,
 * the rule that chooses, or the test being calibrated moves a number on it. The rest holds the
 * pieces a fresh render would agree with whatever they did: the tilt's arithmetic, the refusals, how
 * a missing Run enters the stream, and the extraction from a folder the Rig wrote.
 */
class CalibrationTest {

    private static final String PROVENANCE = "{\"tag\":\"v4.0.0\"}";

    private static RunLog.Outcome died(int depth) {
        return new RunLog.Outcome(false, false, 0, depth, 100, "DEATH", 0);
    }

    private static RunLog.Outcome lost() {
        return new RunLog.Outcome(false, false, 0, 1, 100, "UNKNOWN_WINDOW", 0);
    }

    // ----------------------------------------------------------------------- the committed claim

    @Test
    @DisplayName("the committed page is a fresh render of the committed table")
    void the_page_is_generated() throws IOException {
        Calibration.Table table = Calibration.read(
                SeedSetsTest.ROOT.resolve(Calibration.FOLDER).resolve(Calibration.TABLE));
        Calibration.Result result = Calibration.simulate(table, Calibration.GRID,
                Calibration.SIMULATIONS, Calibration.SEED);
        String committed = Files.readString(SeedSetsTest.ROOT.resolve(Calibration.PAGE),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals(committed, Calibration.page(result, Calibration.SIMULATIONS, Calibration.SEED),
                "regenerate with " + Calibration.COMMAND);
    }

    @Test
    @DisplayName("the chosen bounds are within nominal plus the declared margin, and are the ones published")
    void the_chosen_bounds_are_calibrated() {
        Calibration.Table table = Calibration.read(
                SeedSetsTest.ROOT.resolve(Calibration.FOLDER).resolve(Calibration.TABLE));
        Calibration.Row chosen = Calibration.simulate(table, Calibration.GRID,
                Calibration.SIMULATIONS, Calibration.SEED).chosen();

        assertNotNull(chosen, "some cell is calibrated and powerful");
        // The expectation is written down, not computed: these are the numbers the methodology page
        // states, and a change that moves them has to move the page with it.
        assertEquals(new Calibration.Cell(600, 20, 250), chosen.cell());
        Calibration.Tally h0 = chosen.null0();
        Calibration.Tally h1 = chosen.alternative();
        assertTrue(h0.accept() * 1000L <= 60L * h0.total(), "false-accept within 0.050 + 0.010: " + h0);
        assertTrue(h1.reject() * 1000L <= 60L * h1.total(), "false-reject within 0.050 + 0.010: " + h1);
        assertEquals(10, Calibration.MARGIN_PER_MIL, "the margin story 3.8 tests against");
    }

    // ------------------------------------------------------------------------------ the pieces

    @Test
    @DisplayName("the tilt puts H1's mean at exactly p1, and refuses a p1 it cannot reach")
    void the_tilt() {
        for (int p1 : new int[] {550, 600, 650, 999}) {
            assertEquals(p1 / 1000.0, 0.5 + Calibration.tiltFor(p1) / 2, 1e-12);
        }
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(500));
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(1000));
    }

    @Test
    @DisplayName("a table where every pair ties: H0 rejects, and H1's wins are the tilt's alone")
    void ties_and_the_tilt() {
        // One outcome, so every H0 pair is an exact tie with both Runs reached: evidence for H0, and
        // the test rejects. Under H1 the only wins are the tilted pairs, so accepting at all is the
        // tilt's doing.
        Calibration.Table table = new Calibration.Table(PROVENANCE, List.of(died(1)));
        Calibration.Result result = Calibration.simulate(table,
                List.of(new Calibration.Cell(650, 10, 250)), 400, 7);

        assertEquals(result.stream().pairs(), result.stream().reachedTies());
        assertEquals(0, result.stream().missing());
        Calibration.Row row = result.rows().get(0);
        assertEquals(400, row.null0().reject());
        assertTrue(row.alternative().accept() > 300, row.alternative().toString());
    }

    @Test
    @DisplayName("a Run with no ending makes its pair a tie counted missing, and past the cap the result is void")
    void missing_runs() {
        // Half the Runs never ended, so three pairs in four are missing and every pair ties.
        Calibration.Table table = new Calibration.Table(PROVENANCE, List.of(died(1), lost()));
        Calibration.Result result = Calibration.simulate(table,
                List.of(new Calibration.Cell(600, 10, 100)), 200, 11);

        Calibration.Stream stream = result.stream();
        assertEquals(stream.pairs(), stream.ties(), "a missing pair scores a half");
        assertEquals(stream.pairs(), stream.missing() + stream.reachedTies());
        assertTrue(stream.missing() * 10 > stream.pairs() * 7, "about three in four: " + stream);
        assertEquals(200, result.rows().get(0).null0().voided(), "far past a cap of one in ten");
    }

    @Test
    @DisplayName("the same table, seed and count render the same page; another seed does not")
    void determinism() {
        Calibration.Table table = new Calibration.Table(PROVENANCE,
                List.of(died(1), died(2), died(3), lost()));
        List<Calibration.Cell> grid = List.of(new Calibration.Cell(600, 10, 500));

        String once = Calibration.page(Calibration.simulate(table, grid, 300, 5), 300, 5);
        String twice = Calibration.page(Calibration.simulate(table, grid, 300, 5), 300, 5);
        String other = Calibration.page(Calibration.simulate(table, grid, 300, 6), 300, 6);

        assertEquals(once, twice);
        assertNotEquals(once, other);
    }

    @Test
    @DisplayName("a table with no Run the game ended, an empty file, and no simulations are refused")
    void refusals(@TempDir Path folder) throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> new Calibration.Table(PROVENANCE, List.of(lost(), lost())));
        assertThrows(IllegalArgumentException.class,
                () -> new Calibration.Table(PROVENANCE, List.of()));
        Path empty = folder.resolve("empty.jsonl");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Calibration.read(empty));
        Calibration.Table table = new Calibration.Table(PROVENANCE, List.of(died(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Calibration.simulate(table, Calibration.GRID, 0, 1));
    }

    @Test
    @DisplayName("the commands the methodology page publishes are the task and the shape its main accepts")
    void the_published_commands() throws IOException {
        String page = Files.readString(SeedSetsTest.ROOT.resolve("docs/methodology.md"),
                StandardCharsets.UTF_8);
        assertTrue(page.contains(Calibration.COMMAND + "\n"), "the page's own block runs the task");
        assertTrue(page.contains(Calibration.COMMAND + " --args=\"<root> extract <runs folder>\""),
                "and the extraction, in the shape main reads");
        // Any other shape is refused rather than guessed at: the root alone, or the root, `extract`
        // and one folder.
        assertThrows(IllegalArgumentException.class,
                () -> Calibration.main(new String[] {SeedSetsTest.ROOT.toString(), "extract"}));
        assertThrows(IllegalArgumentException.class,
                () -> Calibration.main(new String[] {SeedSetsTest.ROOT.toString(), "write", "x"}));
    }

    // --------------------------------------------------------------------------- the extraction

    @Test
    @DisplayName("the table is extracted from what each Run's own log says, and a Run with no ending is INCOMPLETE")
    void extraction(@TempDir Path runs) throws IOException {
        SeedSet set = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set();
        StringBuilder index = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            SeedSet.Entry triple = set.entries().get(i);
            RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234",
                    triple.heroClass(), triple.challengeFlags(), triple.seed(), triple.seedCode(),
                    100L + i, 20_000, 3, 2, 8, new RunLog.Brain("random", "abc1234", "0".repeat(64)),
                    "", false, "a laptop", "2026-09-23T00:00:00Z");
            StringBuilder text = new StringBuilder(RunLogJson.line("", header)).append('\n');
            if (i != 2) {
                RunLog.End end = new RunLog.End(0,
                        new RunLog.Outcome(false, false, 10 * i, 1 + i, 1000 + i, "DEATH", i), true);
                text.append(RunLogJson.line(RunLogJson.chain("", header), end)).append('\n');
            }
            String file = RunLog.fileName(header.runId());
            Files.writeString(runs.resolve(file), text.toString(), StandardCharsets.UTF_8);
            index.append("{\"chain\":\"c").append(i).append("\",\"log\":\"").append(file)
                    .append("\",\"runId\":\"").append(header.runId()).append("\"}\n");
        }
        Files.writeString(runs.resolve(RunIndex.RUNS), index.toString(), StandardCharsets.UTF_8);
        Files.writeString(runs.resolve(RunIndex.SUMMARY), "{\"seedSet\":\"smoke\"}\n",
                StandardCharsets.UTF_8);

        String text = Calibration.extract(runs, SeedSetsTest.ROOT);
        Path file = runs.resolve("table.jsonl");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        Calibration.Table table = Calibration.read(file);

        assertEquals(List.of(new RunLog.Outcome(false, false, 0, 1, 1000, "DEATH", 0),
                        new RunLog.Outcome(false, false, 10, 2, 1001, "DEATH", 1),
                        new RunLog.Outcome(false, false, 0, 0, 0, "INCOMPLETE", 0)),
                table.outcomes());
        assertEquals("smoke", LogHeader.string(table.provenance(), "seed_set"));
        assertEquals(String.valueOf(set.version()), LogHeader.value(table.provenance(), "seed_version"));
        assertEquals("random", LogHeader.string(table.provenance(), "brain"));
        assertTrue(text.contains("\"chain\":\"c1\""), "each row carries its Run's chain: " + text);
    }
}
