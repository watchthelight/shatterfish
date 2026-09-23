package org.shatterfish.rig;

import org.shatterfish.api.JsonWriter;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The calibration of the sequential test's bounds on this project's own outcomes (story 3.7,
 * FR-21, ADR-0012).
 *
 * <p>{@link Gsprt} states nominal error rates, but its guarantees are asymptotic, and the pair score
 * it runs on is three-valued, often tied and sometimes missing. So the rates are measured before a
 * bound is trusted: a Monte-Carlo bootstraps pair sequences from the Composite outcomes of a real
 * random-Brain run of {@code standard}, runs the test over each, and counts what it concluded.
 *
 * <p><b>H0</b> is two independent draws from the table, scored by {@link PairScore}: two Brains of
 * equal strength, and the pessimistic pairing, since two different Brains diverge at their first
 * differing Decision and correlation only shrinks the variance. Its mean is one half by symmetry.
 * <b>H1</b> is the same stream with a share {@code q} of its pairs made wins, where
 * {@code q = 2(p1 - 1/2)} puts the mean at exactly {@code p1}; every other property of the real
 * stream -- its ties, its missing Runs and their rate -- is kept, scaled by {@code 1 - q}.
 *
 * <p>The simulation is a pure function of the table, the grid, the number of simulations and the
 * seed, so the page it writes is a generated file that CI regenerates and compares. Its randomness
 * is a {@link SplittableRandom} seeded from a constant: nothing here touches the game, a salt, or a
 * Run.
 *
 * <p>Two modes, both through {@code ./gradlew :rig:calibrate}: with the root alone it reads the
 * committed table and writes the page; with {@code extract <runs folder>} it writes the table from
 * a folder the Rig wrote.
 */
public final class Calibration {

    /** Where the committed tables live, under the repository root. */
    public static final String FOLDER = "calibration";

    /** The table this calibration reads: the random Brain on {@code standard} at the pinned tag. */
    public static final String TABLE = "v4.0.0-random-standard.jsonl";

    /** The page it writes, under the repository root. */
    public static final String PAGE = "docs/results/calibration-v4.0.0.md";

    public static final String COMMAND = "./gradlew :rig:calibrate";

    /** How the table was made, from the repository root, recorded in its first line. */
    public static final String RUN_COMMAND = "./gradlew :rig:run --args=\"--brain random --seeds"
            + " standard --parallel 24 --out <dir>\"";

    /**
     * The acceptable margin between a realized error rate and its nominal rate, in thousandths:
     * a realized rate up to nominal + 0.010 is within calibration. Declared here, before the
     * e-process of story 3.8 is written, so that story has a criterion it did not choose.
     *
     * <p>Ten thousand simulations put the Monte-Carlo standard error of a rate near 0.05 at about
     * 0.0022, so the margin is four and a half standard errors: a test within it is not one whose
     * excess is noise, and a test outside it is not one unlucky draw.
     */
    public static final int MARGIN_PER_MIL = 10;

    /** Simulated sequences per hypothesis per cell. */
    public static final int SIMULATIONS = 10_000;

    /** The simulation's seed: the date the calibration was first run. Any constant would do. */
    public static final long SEED = 20_260_923L;

    /** What every cell shares: H0, the error rates and the maximum (the size of {@code standard}). */
    public static final int P0_PER_MIL = 500;

    public static final int ALPHA_PER_MIL = 50;

    public static final int BETA_PER_MIL = 50;

    public static final int MAXIMUM = 500;

    /** The share of H1 sequences that must accept for a cell to be chosen: the test's power. */
    public static final int POWER_PER_MIL = 900;

    /** One candidate set of bounds: H1, the burn-in and the missing cap. */
    public record Cell(int p1PerMil, int burnIn, int missingPerMil) {

        public Gsprt test() {
            return new Gsprt(P0_PER_MIL / 1000.0, p1PerMil / 1000.0, ALPHA_PER_MIL / 1000.0,
                    BETA_PER_MIL / 1000.0, burnIn, MAXIMUM).allowingMissing(missingPerMil);
        }
    }

    /** The grid the calibration searches, in the order the page lists it. */
    public static final List<Cell> GRID = grid();

    private static List<Cell> grid() {
        List<Cell> cells = new ArrayList<>();
        for (int missing : new int[] {100, 250}) {
            for (int p1 : new int[] {550, 600, 650}) {
                for (int burnIn : new int[] {10, 20, 40}) {
                    cells.add(new Cell(p1, burnIn, missing));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** What one hypothesis's simulations concluded: counts of each verdict, and the stops. */
    public record Tally(int accept, int reject, int undecided, int voided, long pairs, int median) {

        public int total() {
            return accept + reject + undecided + voided;
        }
    }

    /** One cell's two tallies. */
    public record Row(Cell cell, Tally null0, Tally alternative) {

        /** Whether both realized error rates are within nominal + margin. */
        public boolean calibrated() {
            return null0.accept() * 1000L <= (long) (ALPHA_PER_MIL + MARGIN_PER_MIL) * null0.total()
                    && alternative.reject() * 1000L
                    <= (long) (BETA_PER_MIL + MARGIN_PER_MIL) * alternative.total();
        }

        /** Whether H1 is accepted often enough for the cell to be worth choosing. */
        public boolean powerful() {
            return alternative.accept() * 1000L >= (long) POWER_PER_MIL * alternative.total();
        }
    }

    /** The shares of the H0 pair stream that tie, that are missing, and that tie with both Runs reached. */
    public record Stream(long pairs, long ties, long missing, long reachedTies) {
    }

    /** The whole calibration: the table's provenance, every row, the stream, and the chosen row. */
    public record Result(Table table, List<Row> rows, Stream stream, Row chosen) {
    }

    /** The committed table: its first line, and one outcome per Run in the order it lists them. */
    public record Table(String provenance, List<RunLog.Outcome> outcomes) {

        public Table {
            outcomes = List.copyOf(outcomes);
            if (outcomes.stream().noneMatch(PairScore::reached)) {
                throw new IllegalArgumentException("a calibration table needs at least one Run the"
                        + " game ended; this one has " + outcomes.size() + " and none reached an ending");
            }
        }

        public long missing() {
            return outcomes.stream().filter(o -> !PairScore.reached(o)).count();
        }
    }

    private Calibration() {
    }

    // ------------------------------------------------------------------------------ the simulation

    /**
     * Runs every cell of {@code grid} on {@code sims} sequences per hypothesis. Sequence {@code k}
     * is drawn from its own generator seeded with {@code seed + k}, so every cell sees the same
     * sequences and the cells differ only in the test: the grid is a paired comparison of bounds.
     */
    public static Result simulate(Table table, List<Cell> grid, int sims, long seed) {
        if (sims < 1) {
            throw new IllegalArgumentException("at least one simulation: " + sims);
        }
        List<RunLog.Outcome> outcomes = table.outcomes();
        int n = outcomes.size();
        // The pair stream, drawn once: a score, whether it is missing, and the uniform the tilt
        // compares against, for every pair of every sequence.
        byte[] score = new byte[sims * MAXIMUM];
        boolean[] missing = new boolean[sims * MAXIMUM];
        double[] tilt = new double[sims * MAXIMUM];
        long ties = 0;
        long gone = 0;
        long reachedTies = 0;
        for (int k = 0; k < sims; k++) {
            SplittableRandom random = new SplittableRandom(seed + k);
            for (int i = 0; i < MAXIMUM; i++) {
                RunLog.Outcome candidate = outcomes.get(random.nextInt(n));
                RunLog.Outcome baseline = outcomes.get(random.nextInt(n));
                int at = k * MAXIMUM + i;
                PairScore pair = PairScore.of(candidate, baseline);
                score[at] = (byte) pair.halves();
                missing[at] = !PairScore.reached(candidate) || !PairScore.reached(baseline);
                tilt[at] = random.nextDouble();
                ties += pair == PairScore.EQUAL ? 1 : 0;
                gone += missing[at] ? 1 : 0;
                reachedTies += pair == PairScore.EQUAL && !missing[at] ? 1 : 0;
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Cell cell : grid) {
            Gsprt test = cell.test();
            rows.add(new Row(cell, tally(test, score, missing, tilt, sims, 0.0),
                    tally(test, score, missing, tilt, sims, tiltFor(cell.p1PerMil()))));
        }
        return new Result(table, List.copyOf(rows),
                new Stream((long) sims * MAXIMUM, ties, gone, reachedTies), choose(rows));
    }

    /** The share of pairs made wins so that the mean is {@code p1}: {@code q = 2(p1 - 1/2)}. */
    static double tiltFor(int p1PerMil) {
        if (p1PerMil <= 500 || p1PerMil >= 1000) {
            throw new IllegalArgumentException("H1 is a mean above one half and below one, which a"
                    + " tilt toward wins can reach: " + p1PerMil + " per mil");
        }
        return 2 * (p1PerMil / 1000.0 - 0.5);
    }

    private static Tally tally(Gsprt test, byte[] score, boolean[] missing, double[] tilt, int sims,
                               double q) {
        int accept = 0;
        int reject = 0;
        int undecided = 0;
        int voided = 0;
        long pairs = 0;
        int[] stops = new int[sims];
        for (int k = 0; k < sims; k++) {
            int from = k * MAXIMUM;
            List<PairScore> scores = new AbstractList<>() {
                @Override
                public PairScore get(int i) {
                    return tilt[from + i] < q ? PairScore.BETTER : PairScore.values()[score[from + i]];
                }

                @Override
                public int size() {
                    return MAXIMUM;
                }
            };
            List<Boolean> gone = new AbstractList<>() {
                @Override
                public Boolean get(int i) {
                    return tilt[from + i] >= q && missing[from + i];
                }

                @Override
                public int size() {
                    return MAXIMUM;
                }
            };
            Gsprt.Result result = test.run(scores, gone);
            switch (result.verdict()) {
                case ACCEPT -> accept++;
                case REJECT -> reject++;
                case UNDECIDED -> undecided++;
                case VOID -> voided++;
            }
            pairs += result.pairs();
            stops[k] = result.pairs();
        }
        Arrays.sort(stops);
        return new Tally(accept, reject, undecided, voided, pairs, stops[sims / 2]);
    }

    /**
     * The chosen row, by a rule fixed before the numbers: of the rows within calibration and with
     * the power {@link #POWER_PER_MIL} asks for, the one with the smallest {@code p1} (the finest
     * difference the test can resolve), then the smallest missing cap (the least a crash can buy),
     * then the fewest pairs under H1 on average. Null when no row qualifies.
     */
    static Row choose(List<Row> rows) {
        Row best = null;
        for (Row row : rows) {
            if (!row.calibrated() || !row.powerful()) {
                continue;
            }
            if (best == null || better(row, best)) {
                best = row;
            }
        }
        return best;
    }

    private static boolean better(Row row, Row than) {
        if (row.cell().p1PerMil() != than.cell().p1PerMil()) {
            return row.cell().p1PerMil() < than.cell().p1PerMil();
        }
        if (row.cell().missingPerMil() != than.cell().missingPerMil()) {
            return row.cell().missingPerMil() < than.cell().missingPerMil();
        }
        return row.alternative().pairs() < than.alternative().pairs();
    }

    // ------------------------------------------------------------------------------- the table

    /** Reads a committed table: the provenance line, then one outcome per line. */
    public static Table read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the calibration table " + file + " could not be read", e);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("the calibration table " + file + " is empty");
        }
        List<RunLog.Outcome> outcomes = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            outcomes.add(new RunLog.Outcome(
                    Boolean.parseBoolean(LogHeader.value(line, "win")), false,
                    Long.parseLong(LogHeader.value(line, "score")),
                    Integer.parseInt(LogHeader.value(line, "depth")),
                    Long.parseLong(LogHeader.value(line, "turns")),
                    LogHeader.string(line, "cause"),
                    Integer.parseInt(LogHeader.value(line, "bosses"))));
        }
        return new Table(lines.get(0), outcomes);
    }

    /**
     * Writes the table for the Runs {@code runs} holds, in the order its index lists them. A Run
     * with no whole, finished log is written with the cause {@code INCOMPLETE}, which no pair score
     * counts as an ending, so it enters the simulation as the missing Run it was.
     */
    public static String extract(Path runs, Path root) {
        List<String> index;
        try {
            index = Files.readAllLines(runs.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the run index in " + runs + " could not be read", e);
        }
        List<String> rows = new ArrayList<>();
        RunLog.Header first = null;
        for (String line : index) {
            if (line.isBlank()) {
                continue;
            }
            String log = LogHeader.string(line, "log");
            Path file = runs.resolve(log);
            RunLog.Outcome outcome = Comparison.outcome(file);
            if (first == null && outcome != null) {
                first = RunLogReader.of(file).header();
            }
            JsonWriter row = new JsonWriter().beginObject();
            row.key("bosses").value(outcome == null ? 0 : outcome.bosses());
            row.key("cause").value(outcome == null ? "INCOMPLETE" : outcome.cause());
            row.key("chain").value(LogHeader.string(line, "chain"));
            row.key("depth").value(outcome == null ? 0 : outcome.depth());
            row.key("run").value(LogHeader.string(line, "runId"));
            row.key("score").value(outcome == null ? 0 : outcome.score());
            row.key("turns").value(outcome == null ? 0 : outcome.turns());
            row.key("win").value(outcome != null && outcome.win());
            rows.add(row.endObject().toJson());
        }
        if (first == null) {
            throw new IllegalArgumentException(runs + " holds no finished Run to calibrate on");
        }
        String summary;
        try {
            summary = Files.readString(runs.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the summary in " + runs + " could not be read", e);
        }
        String set = LogHeader.string(summary, "seedSet");
        SeedSet seeds = SeedSets.load(root, set).set();
        JsonWriter head = new JsonWriter().beginObject();
        head.key("brain").value(first.brain().name());
        head.key("brain_commit").value(first.brain().commit());
        head.key("cap").value(first.cap());
        head.key("command").value(RUN_COMMAND);
        head.key("index_sha256").value(sha256(runs.resolve(RunIndex.RUNS)));
        head.key("runs").value(rows.size());
        head.key("seed_set").value(set);
        head.key("seed_version").value(seeds.version());
        head.key("tag").value(first.tag());
        StringBuilder text = new StringBuilder(head.endObject().toJson()).append('\n');
        for (String row : rows) {
            text.append(row).append('\n');
        }
        return text.toString();
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
    }

    // -------------------------------------------------------------------------------- the page

    /** The results page for {@code result}: Markdown, generated, the same bytes for the same inputs. */
    public static String page(Result result, int sims, long seed) {
        Table table = result.table();
        String p = table.provenance();
        StringBuilder out = new StringBuilder();
        out.append("<!-- Generated by ").append(COMMAND).append(" from ").append(FOLDER).append('/')
                .append(TABLE).append(". Do not edit by hand: CalibrationTest regenerates it. -->\n\n");
        out.append("# Calibrating the bounds: the sequential test on the random Brain's outcomes\n\n");
        out.append("Story 3.7, [#96](https://github.com/watchthelight/shatterfish/issues/96). ")
                .append("The method is on the [methodology page](../methodology.md#calibrating-the-bounds).\n\n");
        out.append("## The outcomes it was bootstrapped from\n\n");
        out.append("| | |\n|---|---|\n");
        out.append("| Upstream tag | `").append(LogHeader.string(p, "tag")).append("` |\n");
        out.append("| Brain | `").append(LogHeader.string(p, "brain")).append("` at `")
                .append(LogHeader.string(p, "brain_commit")).append("` |\n");
        out.append("| Seed set | `").append(LogHeader.string(p, "seed_set")).append("` version ")
                .append(LogHeader.value(p, "seed_version")).append(" |\n");
        out.append("| Turn cap | ").append(LogHeader.value(p, "cap")).append(" |\n");
        out.append("| Runs | ").append(table.outcomes().size()).append(", of which ")
                .append(table.missing()).append(" did not reach an ending the game decided |\n");
        out.append("| Run index SHA-256 | `").append(LogHeader.string(p, "index_sha256")).append("` |\n");
        out.append("| Command | `").append(LogHeader.string(p, "command")).append("` |\n\n");
        out.append(causes(table));
        Stream stream = result.stream();
        out.append("\n## The pair statistic under H0\n\n");
        out.append("Two independent draws per pair, ").append(String.format(Locale.ROOT, "%,d", stream.pairs()))
                .append(" pairs in all.\n\n");
        out.append("| | share |\n|---|---|\n");
        out.append("| Tied (scored ½) | ").append(share(stream.ties(), stream.pairs())).append(" |\n");
        out.append("| Missing (a Run with no ending; scored ½) | ").append(share(stream.missing(), stream.pairs()))
                .append(" |\n");
        out.append("| Tied with both Runs reached | ").append(share(stream.reachedTies(), stream.pairs()))
                .append(" |\n\n");
        out.append("## The grid\n\n");
        out.append(String.format(Locale.ROOT, "Every cell: p0 = 0.500, α = β = %.3f, maximum %d pairs;"
                        + " %,d sequences per hypothesis, seed %d. A realized rate is calibrated when"
                        + " it is at most nominal + %.3f; a cell is powerful when at least %.1f%% of"
                        + " H1 sequences accept.\n\n",
                ALPHA_PER_MIL / 1000.0, MAXIMUM, sims, seed, MARGIN_PER_MIL / 1000.0,
                POWER_PER_MIL / 10.0));
        out.append("| p1 | n0 | missing cap | H0 accept | H0 reject | H0 undecided | H0 void"
                + " | H1 accept | H1 reject | H1 undecided | H1 void | H1 mean pairs | H1 median pairs"
                + " | calibrated | powerful |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row row : result.rows()) {
            Cell c = row.cell();
            Tally h0 = row.null0();
            Tally h1 = row.alternative();
            out.append(String.format(Locale.ROOT, "| %.3f | %d | %.3f | %s | %s | %s | %s | %s | %s"
                            + " | %s | %s | %.1f | %d | %s | %s |\n",
                    c.p1PerMil() / 1000.0, c.burnIn(), c.missingPerMil() / 1000.0,
                    share(h0.accept(), h0.total()), share(h0.reject(), h0.total()),
                    share(h0.undecided(), h0.total()), share(h0.voided(), h0.total()),
                    share(h1.accept(), h1.total()), share(h1.reject(), h1.total()),
                    share(h1.undecided(), h1.total()), share(h1.voided(), h1.total()),
                    (double) h1.pairs() / h1.total(), h1.median(),
                    row.calibrated() ? "yes" : "no", row.powerful() ? "yes" : "no"));
        }
        out.append("\n## The chosen bounds\n\n");
        Row chosen = result.chosen();
        if (chosen == null) {
            out.append("No cell is both calibrated and powerful, so no bounds are chosen from this"
                    + " table.\n");
        } else {
            Cell c = chosen.cell();
            out.append(String.format(Locale.ROOT, "p0 = 0.500, p1 = %.3f, α = %.3f, β = %.3f,"
                            + " n0 = %d, nmax = %d, missing cap = %.3f. Realized false-accept %s,"
                            + " realized false-reject %s, H1 accepted in %s after %.1f pairs on"
                            + " average.\n",
                    c.p1PerMil() / 1000.0, ALPHA_PER_MIL / 1000.0, BETA_PER_MIL / 1000.0,
                    c.burnIn(), MAXIMUM, c.missingPerMil() / 1000.0,
                    share(chosen.null0().accept(), chosen.null0().total()),
                    share(chosen.alternative().reject(), chosen.alternative().total()),
                    share(chosen.alternative().accept(), chosen.alternative().total()),
                    (double) chosen.alternative().pairs() / chosen.alternative().total()));
        }
        return out.toString();
    }

    private static String causes(Table table) {
        java.util.TreeMap<String, Integer> causes = new java.util.TreeMap<>();
        java.util.TreeMap<Integer, Integer> depths = new java.util.TreeMap<>();
        for (RunLog.Outcome o : table.outcomes()) {
            causes.merge(o.cause(), 1, Integer::sum);
            depths.merge(o.depth(), 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder("| Ending | Runs |\n|---|---|\n");
        causes.forEach((cause, count) -> out.append("| `").append(cause).append("` | ").append(count)
                .append(" |\n"));
        out.append("\n| Depth | Runs |\n|---|---|\n");
        depths.forEach((depth, count) -> out.append("| ").append(depth).append(" | ").append(count)
                .append(" |\n"));
        return out.toString();
    }

    private static String share(long part, long whole) {
        return String.format(Locale.ROOT, "%.2f%%", 100.0 * part / whole);
    }

    // ---------------------------------------------------------------------------------- the task

    /**
     * {@code <root>} writes the page from the committed table; {@code <root> extract <runs folder>}
     * writes the table from a folder the Rig wrote. Prints what it wrote and how long it took.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1 && !(args.length == 3 && args[1].equals("extract"))) {
            throw new IllegalArgumentException("usage: Calibration <root> [extract <runs folder>], not "
                    + java.util.Arrays.toString(args));
        }
        Path root = Seeds.checkout(args[0]);
        long began = System.nanoTime();
        if (args.length == 3) {
            Path table = root.resolve(FOLDER).resolve(TABLE);
            Files.createDirectories(table.getParent());
            Files.writeString(table, extract(Path.of(args[2]).toAbsolutePath(), root),
                    StandardCharsets.UTF_8);
            System.out.println("the calibration wrote " + table + " in "
                    + (System.nanoTime() - began) / 1_000_000L + " ms");
            return;
        }
        Result result = simulate(read(root.resolve(FOLDER).resolve(TABLE)), GRID, SIMULATIONS, SEED);
        Path page = root.resolve(PAGE);
        Files.writeString(page, page(result, SIMULATIONS, SEED), StandardCharsets.UTF_8);
        System.out.println("the calibration wrote " + page + " in "
                + (System.nanoTime() - began) / 1_000_000L + " ms");
    }
}
