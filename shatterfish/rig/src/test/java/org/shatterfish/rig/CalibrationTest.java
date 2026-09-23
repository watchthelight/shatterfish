package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.shatterfish.rig.SequentialTest.Statistic.EPROCESS;
import static org.shatterfish.rig.SequentialTest.Statistic.GSPRT;

/**
 * The calibration of the sequential test (story 3.7).
 *
 * <p>The committed page is the calibration's claim, so the test that matters most is that a fresh
 * render of the committed table is that page, byte for byte: a change to the simulation, the grid,
 * the rule that chooses, or the test being calibrated moves a number on it. The rest holds the
 * pieces a fresh render would agree with whatever they did: the tilt, the rule, the refusals, how a
 * missing Run enters the stream, and the extraction from a folder the Rig wrote -- which the page
 * test never runs, since it reads the committed table.
 */
class CalibrationTest {

    private static final String PROVENANCE = "{\"tag\":\"v4.0.0\"}";

    /** The committed calibration, simulated once for the tests that read it. */
    private static Calibration.Result committed;

    private static synchronized Calibration.Result committed() {
        if (committed == null) {
            committed = Calibration.simulate(Calibration.read(
                            SeedSetsTest.ROOT.resolve(Calibration.FOLDER).resolve(Calibration.TABLE)),
                    Calibration.GRID, Calibration.SIMULATIONS, Calibration.SEED);
        }
        return committed;
    }

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
        String page = Files.readString(SeedSetsTest.ROOT.resolve(Calibration.PAGE),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals(page, Calibration.page(committed()), "regenerate with " + Calibration.COMMAND);
    }

    @Test
    @DisplayName("the rule chooses the published bounds, and they hold within the margin on fresh sequences")
    void the_chosen_bounds_are_calibrated() {
        Calibration.Result result = committed();

        // The expectation is written down, not computed: these are the numbers the methodology page
        // states, and a change that moves them has to move the page with it.
        assertEquals(new Calibration.Cell(600, 20, 250), Calibration.CHOSEN);
        assertNotNull(result.chosen(), "some cell is within margin and powerful");
        assertEquals(Calibration.CHOSEN, result.chosen().cell());
        // Chosen on one set of sequences, quoted from another.
        Calibration.Row fresh = result.validation();
        assertEquals(Calibration.CHOSEN, fresh.cell());
        assertTrue(fresh.null0().accept() * 1000L <= 60L * fresh.null0().total(),
                "false-accept within 0.050 + 0.010 on fresh sequences: " + fresh.null0());
        assertTrue(fresh.alternative().reject() * 1000L <= 60L * fresh.alternative().total(),
                "false-reject within 0.050 + 0.010 on fresh sequences: " + fresh.alternative());
        assertTrue(fresh.powerful(), fresh.alternative().toString());
        assertNotEquals(result.chosen().null0(), fresh.null0(), "the validation is other sequences");
        assertEquals(10, Calibration.MARGIN_PER_MIL, "the margin story 3.8 tests against");
    }

    @Test
    @DisplayName("both designs run on the same fresh sequences, and the rule's gate is the gate the Rig runs")
    void the_gate() {
        Calibration.Result result = committed();

        // Written down: the GSPRT stays the gate, because its validated rates are within the margin.
        assertEquals(GSPRT, result.gate());
        assertEquals(SequentialTest.GATE, result.gate(),
                "SequentialTest.GATE must follow the calibration; change it with the page");
        List<Calibration.Row> duel = result.duel();
        List<Integer> p1s = Calibration.duelAt(Calibration.GRID);
        assertEquals(List.of(550, 600, 650), p1s, "every p1 of the grid, the chosen one among them");
        assertEquals(2 * p1s.size(), duel.size());
        for (int i = 0; i < duel.size(); i += 2) {
            Calibration.Row gsprt = duel.get(i);
            Calibration.Row eprocess = duel.get(i + 1);
            assertEquals(GSPRT, gsprt.statistic());
            assertEquals(EPROCESS, eprocess.statistic());
            assertEquals(gsprt.cell(), eprocess.cell(), "the same bounds");
            assertEquals(p1s.get(i / 2), gsprt.cell().p1PerMil());
            // Same sequences, so the same simulated H1 stream.
            assertEquals(gsprt.alternative().mean(), eprocess.alternative().mean());
            // Ville's inequality bounds the probability, not the rate on 10,000 draws; with the
            // realized rates near 2% against 5%, sampling noise could not carry a sound e-process
            // past alpha, so a rate above it would mean a bug. On the page's own numbers, then: at
            // most alpha, with no margin needed.
            assertTrue(eprocess.null0().accept() * 1000L
                            <= (long) Calibration.ALPHA_PER_MIL * eprocess.null0().total(),
                    eprocess.null0().toString());
        }
        assertEquals(result.validation(), duel.stream()
                        .filter(row -> row.statistic() == GSPRT && row.cell().equals(result.chosen().cell()))
                        .findFirst().orElseThrow(),
                "the chosen cell's GSPRT row in the duel is the validation: same sequences, same test");
    }

    @Test
    @DisplayName("a GSPRT outside the margin hands the gate to an e-process that is within it and powerful, and to nothing else")
    void the_rule_can_go_the_other_way() {
        Calibration.Tally within = new Calibration.Tally(60, 940, 0, 0, 1000, 10, 0.5);
        Calibration.Tally over = new Calibration.Tally(61, 939, 0, 0, 1000, 10, 0.5);
        Calibration.Tally h1 = new Calibration.Tally(950, 50, 0, 0, 1000, 10, 0.6);
        Calibration.Tally h1over = new Calibration.Tally(900, 61, 39, 0, 1000, 10, 0.6);
        Calibration.Tally h1weak = new Calibration.Tally(800, 20, 180, 0, 1000, 10, 0.6);
        Calibration.Cell cell = new Calibration.Cell(600, 20, 250);
        Calibration.Row able = new Calibration.Row(cell, EPROCESS, within, h1);
        Calibration.Row feeble = new Calibration.Row(cell, EPROCESS, within, h1weak);
        Calibration.Row loose = new Calibration.Row(cell, EPROCESS, over, h1);

        assertEquals(GSPRT, Calibration.gate(new Calibration.Row(cell, GSPRT, within, h1), feeble),
                "a GSPRT within the margin stays, whatever the e-process did");
        assertEquals(EPROCESS, Calibration.gate(new Calibration.Row(cell, GSPRT, over, h1), able));
        assertEquals(EPROCESS, Calibration.gate(new Calibration.Row(cell, GSPRT, within, h1over), able));
        assertNull(Calibration.gate(new Calibration.Row(cell, GSPRT, over, h1), feeble),
                "an e-process without the power is no replacement");
        assertNull(Calibration.gate(new Calibration.Row(cell, GSPRT, over, h1), loose),
                "nor one outside the margin itself");
    }

    @Test
    @DisplayName("the tilted stream's mean is p1 in every cell, with its missing pairs left missing")
    void the_tilt_reaches_p1() {
        for (Calibration.Row row : committed().rows()) {
            assertEquals(row.cell().p1PerMil() / 1000.0, row.alternative().mean(), 0.002,
                    row.cell().toString());
        }
    }

    // ------------------------------------------------------------------------------ the pieces

    @Test
    @DisplayName("the tilt's share, and the p1 it refuses because no share of the reached pairs reaches it")
    void the_tilt() {
        for (int p1 : new int[] {550, 600, 650}) {
            for (double m : new double[] {0, 0.16, 0.5}) {
                assertEquals(p1 / 1000.0, 0.5 + (1 - m) * Calibration.tiltFor(p1, m) / 2, 1e-12);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(500, 0));
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(600, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Calibration.tiltFor(800, 0.5),
                "half the pairs missing caps the mean at three quarters");
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
    @DisplayName("a Run with no ending makes its pair a tie counted missing, it stays missing under H1, and past the cap the result is void")
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
        assertEquals(200, result.rows().get(0).alternative().voided(),
                "a better Brain meets the same unknown windows, so H1 voids as often");
    }

    @Test
    @DisplayName("the rule: within margin and powerful, then the smallest p1, the smallest cap, the fewest pairs")
    void the_rule() {
        Calibration.Tally good0 = new Calibration.Tally(50, 950, 0, 0, 1000, 10, 0.5);
        Calibration.Tally bad0 = new Calibration.Tally(70, 930, 0, 0, 1000, 10, 0.5);
        Calibration.Tally strong = new Calibration.Tally(950, 50, 0, 0, 1000, 10, 0.6);
        Calibration.Tally faster = new Calibration.Tally(950, 50, 0, 0, 900, 9, 0.6);
        Calibration.Tally weak = new Calibration.Tally(800, 50, 150, 0, 1000, 10, 0.6);

        Calibration.Row loose = new Calibration.Row(new Calibration.Cell(550, 10, 250), GSPRT, bad0, strong);
        Calibration.Row feeble = new Calibration.Row(new Calibration.Cell(550, 20, 250), GSPRT, good0, weak);
        Calibration.Row wideCap = new Calibration.Row(new Calibration.Cell(600, 20, 250), GSPRT, good0, faster);
        Calibration.Row narrowCap = new Calibration.Row(new Calibration.Cell(600, 20, 200), GSPRT, good0, strong);
        Calibration.Row slow = new Calibration.Row(new Calibration.Cell(600, 40, 200), GSPRT, good0, strong);
        Calibration.Row quick = new Calibration.Row(new Calibration.Cell(600, 10, 200), GSPRT, good0, faster);
        Calibration.Row coarse = new Calibration.Row(new Calibration.Cell(650, 10, 100), GSPRT, good0, faster);

        assertNull(Calibration.choose(List.of(loose, feeble)), "out of margin, and underpowered");
        assertSame(narrowCap, Calibration.choose(List.of(coarse, wideCap, narrowCap)),
                "the smallest p1, then the smallest cap, even over fewer pairs");
        assertSame(quick, Calibration.choose(List.of(slow, quick, coarse)), "then the fewest pairs");
    }

    @Test
    @DisplayName("no qualifying cell renders as no choice, not as the best of the bad ones")
    void no_choice() {
        Calibration.Table table = new Calibration.Table(PROVENANCE, List.of(died(1), lost()));
        Calibration.Result result = Calibration.simulate(table,
                List.of(new Calibration.Cell(600, 10, 100)), 50, 3);

        assertNull(result.chosen());
        assertNull(result.validation());
        assertTrue(result.duel().isEmpty());
        assertNull(result.gate(), "nothing measured the e-process, so the rule picks neither");
        assertTrue(Calibration.page(result).contains("the rule picks no gate"));
    }

    @Test
    @DisplayName("the same table, seed and count render the same page; another seed does not")
    void determinism() {
        Calibration.Table table = new Calibration.Table(PROVENANCE,
                List.of(died(1), died(2), died(3), lost()));
        List<Calibration.Cell> grid = List.of(new Calibration.Cell(600, 10, 500));

        String once = Calibration.page(Calibration.simulate(table, grid, 300, 5));
        String twice = Calibration.page(Calibration.simulate(table, grid, 300, 5));
        String other = Calibration.page(Calibration.simulate(table, grid, 300, 6));

        assertEquals(once, twice);
        assertNotEquals(once, other);
    }

    @Test
    @DisplayName("no Run the game ended, a table short of its own count, a missing key, and a count no array holds are refused")
    void refusals(@TempDir Path folder) throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> new Calibration.Table(PROVENANCE, List.of(lost(), lost())));
        assertThrows(IllegalArgumentException.class,
                () -> new Calibration.Table(PROVENANCE, List.of()));
        Path empty = folder.resolve("empty.jsonl");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Calibration.read(empty));

        List<String> lines = Files.readAllLines(
                SeedSetsTest.ROOT.resolve(Calibration.FOLDER).resolve(Calibration.TABLE));
        Path shortened = folder.resolve("short.jsonl");
        Files.write(shortened, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);
        IllegalArgumentException truncated = assertThrows(IllegalArgumentException.class,
                () -> Calibration.read(shortened));
        assertTrue(truncated.getMessage().contains("states 500 Runs and holds 499"),
                truncated.getMessage());
        Path keyless = folder.resolve("keyless.jsonl");
        List<String> edited = new ArrayList<>(lines);
        edited.set(1, edited.get(1).replace("\"win\":false", "\"won\":false"));
        Files.write(keyless, edited, StandardCharsets.UTF_8);
        IllegalArgumentException noWin = assertThrows(IllegalArgumentException.class,
                () -> Calibration.read(keyless));
        assertTrue(noWin.getMessage().contains("without \"win\""), noWin.getMessage());
        Path headless = folder.resolve("headless.jsonl");
        Files.write(headless, lines.subList(1, lines.size()), StandardCharsets.UTF_8);
        // Refused for what it is, not for the number its missing "runs" fails to parse into: a
        // NumberFormatException is an IllegalArgumentException too, and passed for this refusal.
        IllegalArgumentException noProvenance = assertThrows(IllegalArgumentException.class,
                () -> Calibration.read(headless));
        assertTrue(noProvenance.getMessage().contains("without \"brain\""), noProvenance.getMessage());
        Path numeric = folder.resolve("numeric.jsonl");
        List<String> numbered = new ArrayList<>(lines);
        numbered.set(1, numbered.get(1).replace("\"win\":false", "\"win\":0"));
        Files.write(numeric, numbered, StandardCharsets.UTF_8);
        IllegalArgumentException notBoolean = assertThrows(IllegalArgumentException.class,
                () -> Calibration.read(numeric), "a win that is not a boolean is not read as a loss");
        assertTrue(notBoolean.getMessage().contains("not a boolean"), notBoolean.getMessage());

        Calibration.Table table = new Calibration.Table(PROVENANCE, List.of(died(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Calibration.simulate(table, Calibration.GRID, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Calibration.simulate(table, Calibration.GRID, 5_000_000, 1));
    }

    @Test
    @DisplayName("the commands the methodology page publishes are the task and the shape its main accepts")
    void the_published_commands() throws IOException {
        String page = Files.readString(SeedSetsTest.ROOT.resolve("docs/methodology.md"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
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

    /** A folder of logs for the whole smoke set, as the Rig writes one; the index lines are returned. */
    private static List<String> folder(Path runs, String brain, int incomplete, int won)
            throws IOException {
        SeedSet set = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set();
        List<String> index = new ArrayList<>();
        for (int i = 0; i < set.entries().size(); i++) {
            SeedSet.Entry triple = set.entries().get(i);
            RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234",
                    triple.heroClass(), triple.challengeFlags(), triple.seed(), triple.seedCode(),
                    100L + i, 20_000, 3, 2, 8,
                    new RunLog.Brain(i == 3 ? brain : "random", "abc1234", "0".repeat(64)),
                    "", false, "a laptop", "2026-09-23T00:00:00Z");
            StringBuilder text = new StringBuilder(RunLogJson.line("", header)).append('\n');
            if (i != incomplete) {
                RunLog.End end = new RunLog.End(0, new RunLog.Outcome(i == won, i == won, 10 * i,
                        1 + i % 3, 1000 + i, i == won ? "WIN" : "DEATH", i % 2), true);
                text.append(RunLogJson.line(RunLogJson.chain("", header), end)).append('\n');
            }
            String file = RunLog.fileName(header.runId());
            Files.writeString(runs.resolve(file), text.toString(), StandardCharsets.UTF_8);
            index.add("{\"chain\":\"" + LogHeader.of(runs.resolve(file)).chain() + "\",\"log\":\""
                    + file + "\",\"runId\":\"" + header.runId() + "\"}");
        }
        Files.write(runs.resolve(RunIndex.RUNS), index, StandardCharsets.UTF_8);
        Files.writeString(runs.resolve(RunIndex.SUMMARY), "{\"seedSet\":\"smoke\"}\n",
                StandardCharsets.UTF_8);
        return index;
    }

    @Test
    @DisplayName("the table is what each Run's own log says, under a provenance every row agrees with")
    void extraction(@TempDir Path runs) throws IOException, NoSuchAlgorithmException {
        folder(runs, "random", 2, 1);

        String text = Calibration.extract(runs, SeedSetsTest.ROOT);
        Path file = runs.resolve("table.jsonl");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        Calibration.Table table = Calibration.read(file);

        assertEquals(new RunLog.Outcome(false, false, 0, 1, 1000, "DEATH", 0), table.outcomes().get(0));
        assertEquals(new RunLog.Outcome(true, true, 10, 2, 1001, "WIN", 1), table.outcomes().get(1),
                "a win reads back as a win");
        assertEquals(new RunLog.Outcome(false, false, 0, 0, 0, "INCOMPLETE", 0), table.outcomes().get(2));
        String p = table.provenance();
        assertEquals("v4.0.0", LogHeader.string(p, "tag"));
        assertEquals("random", LogHeader.string(p, "brain"));
        assertEquals("abc1234", LogHeader.string(p, "brain_commit"));
        assertEquals("0".repeat(64), LogHeader.string(p, "brain_config"));
        assertEquals("20000", LogHeader.value(p, "cap"));
        assertEquals("25", LogHeader.value(p, "runs"));
        assertEquals("smoke", LogHeader.string(p, "seed_set"));
        assertEquals(String.valueOf(SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().version()),
                LogHeader.value(p, "seed_version"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(runs.resolve(RunIndex.RUNS)))),
                LogHeader.string(p, "index_sha256"), "the index's own bytes, not the summary's");
        assertEquals("./gradlew :rig:run --args=\"--brain random --seeds smoke --cap 20000 --out <dir>\"",
                LogHeader.string(p, "command"), "the command that made this folder, not a standard one");
        assertEquals("v4.0.0-random-smoke.jsonl", Calibration.tableName(text));
    }

    @Test
    @DisplayName("a folder that mixes Brains, an index whose chain is not the log's, and an index short of the set are refused")
    void extraction_refusals(@TempDir Path root) throws IOException {
        Path mixed = Files.createDirectories(root.resolve("mixed"));
        folder(mixed, "greedy", -1, -1);
        IllegalArgumentException twoBrains = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(mixed, SeedSetsTest.ROOT));
        assertTrue(twoBrains.getMessage().contains("mixes Runs"), twoBrains.getMessage());

        Path forged = Files.createDirectories(root.resolve("forged"));
        List<String> index = folder(forged, "random", -1, -1);
        index.set(4, index.get(4).replaceFirst("\"chain\":\"[0-9a-f]{64}\"", "\"chain\":\"" + "f".repeat(64) + "\""));
        Files.write(forged.resolve(RunIndex.RUNS), index, StandardCharsets.UTF_8);
        IllegalArgumentException chain = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(forged, SeedSetsTest.ROOT));
        assertTrue(chain.getMessage().contains("its log ends on"), chain.getMessage());

        Path partial = Files.createDirectories(root.resolve("partial"));
        List<String> some = folder(partial, "random", -1, -1);
        Files.write(partial.resolve(RunIndex.RUNS), some.subList(0, 20), StandardCharsets.UTF_8);
        IllegalArgumentException shortIndex = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(partial, SeedSetsTest.ROOT));
        assertTrue(shortIndex.getMessage().contains("lists 20 Runs and the Seed set smoke holds 25"),
                shortIndex.getMessage());

        Path unnamed = Files.createDirectories(root.resolve("unnamed"));
        folder(unnamed, "random", -1, -1);
        Files.writeString(unnamed.resolve(RunIndex.SUMMARY), "{\"brain\":\"random\"}\n",
                StandardCharsets.UTF_8);
        IllegalArgumentException noSet = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(unnamed, SeedSetsTest.ROOT));
        assertTrue(noSet.getMessage().contains("names no Seed set"), noSet.getMessage());

        Path blank = Files.createDirectories(root.resolve("blank"));
        List<String> blanked = folder(blank, "random", -1, -1);
        blanked.set(0, blanked.get(0).replaceFirst("\"chain\":\"[0-9a-f]{64}\"", "\"chain\":\"\""));
        Files.write(blank.resolve(RunIndex.RUNS), blanked, StandardCharsets.UTF_8);
        IllegalArgumentException noChain = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(blank, SeedSetsTest.ROOT));
        assertTrue(noChain.getMessage().contains("without \"chain\""), noChain.getMessage());

        Path escaping = Files.createDirectories(root.resolve("escaping"));
        List<String> out = folder(escaping, "random", -1, -1);
        out.set(0, out.get(0).replaceFirst("\"log\":\"", "\"log\":\"../"));
        Files.write(escaping.resolve(RunIndex.RUNS), out, StandardCharsets.UTF_8);
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
                () -> Calibration.extract(escaping, SeedSetsTest.ROOT));
        assertTrue(outside.getMessage().contains("outside"), outside.getMessage());
    }
}
