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
import java.util.TreeMap;

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
 * differing Decision and correlation only shrinks the variance. Its mean is one half by symmetry
 * (a missing pair scores one half too). <b>H1</b> is the same stream with a share of its
 * <em>reached</em> pairs made wins; a missing pair stays missing, because a better Brain does not
 * stop meeting the windows the Harness cannot handle. With {@code m} the stream's missing share,
 * the share {@code q = 2(p1 - 1/2) / (1 - m)} puts the mean at exactly {@code p1}.
 *
 * <p>The cell is chosen by a fixed rule on one set of sequences and then <b>validated on a fresh
 * set</b>, drawn from another seed: choosing the best of twenty-seven cells and quoting its rates from
 * the sequences it was chosen on would report the luckiest cell's luck.
 *
 * <p>The simulation is a pure function of the table, the grid, the number of simulations and the
 * seed, so the page it writes is a generated file that CI regenerates and compares. Its randomness
 * is a {@link SplittableRandom} seeded from a constant: nothing here touches the game, a salt, or a
 * Run.
 *
 * <p>Two modes, both through {@code ./gradlew :rig:calibrate}: with the root alone it reads the
 * committed table and writes the page; with {@code extract <runs folder>} it writes a table from a
 * folder the Rig wrote, named for the tag, Brain and Seed set its Runs carry.
 */
public final class Calibration {

    /** Where the committed tables live, under the repository root. */
    public static final String FOLDER = "calibration";

    /** The table this calibration reads: the random Brain on {@code standard} at the pinned tag. */
    public static final String TABLE = "v4.0.0-random-standard.jsonl";

    /** The page it writes, under the repository root. */
    public static final String PAGE = "docs/results/calibration-v4.0.0.md";

    public static final String COMMAND = "./gradlew :rig:calibrate";

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

    /** Simulated sequences per hypothesis per cell, and again for the validation. */
    public static final int SIMULATIONS = 10_000;

    /** The simulation's seed: the date the calibration was first run. Any constant would do. */
    public static final long SEED = 20_260_923L;

    /** Added to the seed for the validation's sequences, so that none of them is a choosing one. */
    public static final long VALIDATION_OFFSET = 1_000_000_007L;

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

        /** The e-process on the same hypotheses and cap; it has no burn-in. */
        public EProcess eprocess() {
            return new EProcess(P0_PER_MIL / 1000.0, p1PerMil / 1000.0, ALPHA_PER_MIL / 1000.0,
                    BETA_PER_MIL / 1000.0, MAXIMUM).allowingMissing(missingPerMil);
        }

        /** The test of {@code statistic} this cell describes. */
        public SequentialTest test(SequentialTest.Statistic statistic) {
            return statistic == SequentialTest.Statistic.GSPRT ? test() : eprocess();
        }
    }

    /**
     * The bounds this calibration chose from the committed table, written down. {@code
     * CalibrationTest} holds the rule's choice to it, so a change that moves the choice has to move
     * this, the methodology page and the Registrations that cite it together.
     */
    public static final Cell CHOSEN = new Cell(600, 20, 250);

    /** The grid the calibration searches, in the order the page lists it. */
    public static final List<Cell> GRID = grid();

    private static List<Cell> grid() {
        List<Cell> cells = new ArrayList<>();
        // The missing caps bracket the stream's own missing share (16% of pairs for the random
        // Brain): below it nearly everything is void, which the page shows rather than assumes.
        for (int missing : new int[] {100, 200, 250}) {
            for (int p1 : new int[] {550, 600, 650}) {
                for (int burnIn : new int[] {10, 20, 40}) {
                    cells.add(new Cell(p1, burnIn, missing));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** What one hypothesis's simulations concluded: counts of each verdict, and the stops. */
    public record Tally(int accept, int reject, int undecided, int voided, long pairs, int median,
                        double mean) {

        public int total() {
            return accept + reject + undecided + voided;
        }
    }

    /**
     * One cell's two tallies.
     *
     * @param statistic   which design ran
     * @param null0       the H0 sequences' verdicts
     * @param alternative the H1 sequences' verdicts; {@code mean} is the H1 stream's mean pair
     *                    score, which the tilt is meant to put at {@code p1}
     */
    public record Row(Cell cell, SequentialTest.Statistic statistic, Tally null0, Tally alternative) {

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

        public double missingShare() {
            return (double) missing / pairs;
        }
    }

    /**
     * The whole calibration.
     *
     * @param rows       every cell, on the choosing sequences
     * @param chosen     the rule's choice among {@code rows}, or null when no cell qualifies
     * @param validation the chosen cell again, on fresh sequences, or null with it
     * @param duel       on the same fresh sequences, both designs at each p1 of the grid, at the
     *                   chosen burn-in and missing cap; empty when nothing was chosen
     * @param gate       the design the rule picks (see {@link #gate}), or null when the rule picks
     *                   neither, which the build then refuses until someone decides
     */
    public record Result(Table table, int sims, long seed, List<Row> rows, Stream stream, Row chosen,
                         Row validation, List<Row> duel, SequentialTest.Statistic gate) {
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

    /** The provenance keys a table's first line must carry. */
    static final List<String> PROVENANCE = List.of("brain", "brain_commit", "brain_config", "cap",
            "command", "index_sha256", "runs", "seed_set", "seed_version", "tag");

    /** The keys each of a table's rows must carry. */
    static final List<String> ROW = List.of("ascended", "bosses", "cause", "chain", "depth", "run",
            "score", "turns", "win");

    /** A pair score by its halves, so that decoding never leans on the enum's declaration order. */
    private static final PairScore[] BY_HALVES = byHalves();

    private static PairScore[] byHalves() {
        PairScore[] scores = new PairScore[3];
        for (PairScore score : PairScore.values()) {
            scores[score.halves()] = score;
        }
        return scores;
    }

    private Calibration() {
    }

    // ------------------------------------------------------------------------------ the simulation

    /**
     * Runs every cell of {@code grid} on {@code sims} sequences per hypothesis, chooses by the rule
     * in {@link #choose}, and validates the choice on {@code sims} fresh sequences drawn from
     * {@code seed + VALIDATION_OFFSET}.
     */
    public static Result simulate(Table table, List<Cell> grid, int sims, long seed) {
        Pass pass = pass(table, entries(grid, SequentialTest.Statistic.GSPRT), sims, seed);
        Row chosen = choose(pass.rows());
        if (chosen == null) {
            // No GSPRT bound survived the rule, and nothing measured the e-process either: the rule
            // has nothing to decide with, so it decides nothing. The build then fails on the gate
            // until someone chooses, which is the point.
            return new Result(table, sims, seed, pass.rows(), pass.stream(), null, null, List.of(),
                    null);
        }
        // The fresh sequences: the chosen cell, and the duel -- both designs at each p1 at the
        // chosen burn-in and cap -- all on one draw, so the designs differ only in the test.
        List<Entry> fresh = new ArrayList<>();
        fresh.add(new Entry(chosen.cell(), SequentialTest.Statistic.GSPRT));
        for (int p1 : duelAt(grid)) {
            Cell cell = new Cell(p1, chosen.cell().burnIn(), chosen.cell().missingPerMil());
            fresh.add(new Entry(cell, SequentialTest.Statistic.GSPRT));
            fresh.add(new Entry(cell, SequentialTest.Statistic.EPROCESS));
        }
        List<Row> rows = pass(table, fresh, sims, seed + VALIDATION_OFFSET).rows();
        Row validation = rows.get(0);
        List<Row> duel = rows.subList(1, rows.size());
        Row challenger = duel.stream()
                .filter(row -> row.statistic() == SequentialTest.Statistic.EPROCESS
                        && row.cell().equals(chosen.cell()))
                .findFirst().orElseThrow();
        return new Result(table, sims, seed, pass.rows(), pass.stream(), chosen, validation, duel,
                gate(validation, challenger));
    }

    /** The p1 values the duel runs at: every p1 of the grid, which the chosen cell was one of. */
    static List<Integer> duelAt(List<Cell> grid) {
        java.util.TreeSet<Integer> p1s = new java.util.TreeSet<>();
        grid.forEach(cell -> p1s.add(cell.p1PerMil()));
        return List.copyOf(p1s);
    }

    /**
     * ADR-0012's rule: the e-process replaces the GSPRT as the gate if the GSPRT's realized error
     * rate, on the fresh sequences, exceeds its nominal rate by more than {@link #MARGIN_PER_MIL}
     * -- and only if the e-process, at the same bounds on the same sequences, is itself within the
     * margin and powerful. When neither is, the rule picks neither and returns null.
     */
    static SequentialTest.Statistic gate(Row validation, Row challenger) {
        if (validation.calibrated()) {
            return SequentialTest.Statistic.GSPRT;
        }
        return challenger.calibrated() && challenger.powerful() ? SequentialTest.Statistic.EPROCESS
                : null;
    }

    /** One test to run in a pass: a cell, in one design. */
    private record Entry(Cell cell, SequentialTest.Statistic statistic) {
    }

    private static List<Entry> entries(List<Cell> grid, SequentialTest.Statistic statistic) {
        return grid.stream().map(cell -> new Entry(cell, statistic)).toList();
    }

    private record Pass(List<Row> rows, Stream stream) {
    }

    /**
     * One pass over {@code sims} sequences. Sequence {@code k} is drawn from its own generator
     * seeded with {@code seed + k}, so every cell sees the same sequences and the cells differ only
     * in the test: the grid is a paired comparison of bounds.
     */
    private static Pass pass(Table table, List<Entry> grid, int sims, long seed) {
        if (sims < 1 || (long) sims * MAXIMUM > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("between one simulation and as many as an array of"
                    + " pairs can hold: " + sims);
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
        Stream stream = new Stream((long) sims * MAXIMUM, ties, gone, reachedTies);
        List<Row> rows = new ArrayList<>();
        for (Entry entry : grid) {
            Cell cell = entry.cell();
            SequentialTest test = cell.test(entry.statistic());
            rows.add(new Row(cell, entry.statistic(), tally(test, score, missing, tilt, sims, 0.0),
                    tally(test, score, missing, tilt, sims,
                            tiltFor(cell.p1PerMil(), stream.missingShare()))));
        }
        return new Pass(List.copyOf(rows), stream);
    }

    /**
     * The share of reached pairs made wins so that the mean is {@code p1}: with {@code m} of the
     * pairs missing and scoring one half, {@code 1/2 + (1 - m) q / 2 = p1}, so
     * {@code q = 2(p1 - 1/2) / (1 - m)}. Refused when no share of the reached pairs can get there.
     */
    static double tiltFor(int p1PerMil, double missingShare) {
        if (p1PerMil <= 500 || p1PerMil >= 1000 || !(missingShare >= 0 && missingShare < 1)) {
            throw new IllegalArgumentException("H1 is a mean above one half and below one, over a"
                    + " stream that is not all missing: " + p1PerMil + " per mil, " + missingShare
                    + " missing");
        }
        double q = 2 * (p1PerMil / 1000.0 - 0.5) / (1 - missingShare);
        if (q >= 1) {
            throw new IllegalArgumentException("with " + missingShare + " of the pairs missing, no"
                    + " share of the rest made wins reaches a mean of " + p1PerMil + " per mil");
        }
        return q;
    }

    private static Tally tally(SequentialTest test, byte[] score, boolean[] missing, double[] tilt, int sims,
                               double q) {
        int accept = 0;
        int reject = 0;
        int undecided = 0;
        int voided = 0;
        long pairs = 0;
        long halves = 0;
        int[] stops = new int[sims];
        for (int k = 0; k < sims; k++) {
            int from = k * MAXIMUM;
            List<PairScore> scores = new AbstractList<>() {
                @Override
                public PairScore get(int i) {
                    int at = from + i;
                    return !missing[at] && tilt[at] < q ? PairScore.BETTER : BY_HALVES[score[at]];
                }

                @Override
                public int size() {
                    return MAXIMUM;
                }
            };
            List<Boolean> gone = new AbstractList<>() {
                @Override
                public Boolean get(int i) {
                    return missing[from + i];
                }

                @Override
                public int size() {
                    return MAXIMUM;
                }
            };
            for (PairScore pair : scores) {
                halves += pair.halves();
            }
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
        return new Tally(accept, reject, undecided, voided, pairs, stops[sims / 2],
                halves / 2.0 / ((long) sims * MAXIMUM));
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

    /**
     * Reads a committed table: the provenance line, then one outcome per line. Every key is
     * required, and the number of rows must be the number the provenance states, so a truncated or
     * hand-trimmed table is refused rather than calibrated and published under its old name.
     */
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
        String provenance = lines.get(0);
        for (String key : PROVENANCE) {
            required(file, provenance, key);
        }
        List<RunLog.Outcome> outcomes = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            for (String key : ROW) {
                required(file, line, key);
            }
            outcomes.add(new RunLog.Outcome(bool(file, line, "win"), bool(file, line, "ascended"),
                    Long.parseLong(LogHeader.value(line, "score")),
                    Integer.parseInt(LogHeader.value(line, "depth")),
                    Long.parseLong(LogHeader.value(line, "turns")),
                    LogHeader.string(line, "cause"),
                    Integer.parseInt(LogHeader.value(line, "bosses"))));
        }
        long stated = Long.parseLong(LogHeader.value(provenance, "runs"));
        if (stated != outcomes.size()) {
            throw new IllegalArgumentException("the calibration table " + file + " states " + stated
                    + " Runs and holds " + outcomes.size());
        }
        return new Table(provenance, outcomes);
    }

    private static void required(Path file, String line, String key) {
        if (LogHeader.value(line, key) == null) {
            throw new IllegalArgumentException("the calibration table " + file + " has a line"
                    + " without \"" + key + "\": " + line);
        }
    }

    private static boolean bool(Path file, String line, String key) {
        String raw = LogHeader.value(line, key);
        if (!raw.equals("true") && !raw.equals("false")) {
            throw new IllegalArgumentException("the calibration table " + file + " has \"" + key
                    + "\" that is not a boolean: " + line);
        }
        return raw.equals("true");
    }

    /**
     * Writes the table for the Runs {@code runs} holds, in the order its index lists them.
     *
     * <p>Every finished Run must agree on the tag, the Brain (name, commit and configuration) and
     * the cap, the index must list exactly one Run per triple of the Seed set its summary names,
     * and each Run's chain in the index must be the chain its own log ends on: the provenance line
     * speaks for every row, so every row is held to it. A Run with no whole, finished log is
     * written with the cause {@code INCOMPLETE}, which no pair score counts as an ending, so it
     * enters the simulation as the missing Run it was.
     */
    public static String extract(Path runs, Path root) {
        Path folder = runs.toAbsolutePath().normalize();
        List<String> index;
        String summary;
        try {
            index = Files.readAllLines(folder.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
            summary = Files.readString(folder.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the run index or summary in " + folder
                    + " could not be read", e);
        }
        String set = LogHeader.string(summary, "seedSet");
        if (set == null) {
            throw new IllegalArgumentException("the summary in " + folder + " names no Seed set");
        }
        SeedSet seeds = SeedSets.load(root, set).set();
        List<String> rows = new ArrayList<>();
        RunLog.Header first = null;
        for (String line : index) {
            if (line.isBlank()) {
                continue;
            }
            String log = named(line, "log", folder);
            String run = named(line, "runId", folder);
            String chain = named(line, "chain", folder);
            Path file = folder.resolve(log).normalize();
            if (!file.getParent().equals(folder)) {
                throw new IllegalArgumentException("the index in " + folder + " names a log outside"
                        + " it: " + log);
            }
            RunLog.Outcome outcome = Comparison.outcome(file);
            if (outcome != null) {
                RunLog.Header header = RunLogReader.of(file).header();
                if (first == null) {
                    first = header;
                } else if (!header.tag().equals(first.tag()) || !header.brain().equals(first.brain())
                        || header.cap() != first.cap()) {
                    throw new IllegalArgumentException(folder + " mixes Runs: " + run + " is "
                            + header.tag() + ", " + header.brain() + ", cap " + header.cap()
                            + ", and the first was " + first.tag() + ", " + first.brain() + ", cap "
                            + first.cap());
                }
                String ends = LogHeader.of(file).chain();
                if (!chain.equals(ends)) {
                    throw new IllegalArgumentException("the index gives " + run + " the chain " + chain
                            + " and its log ends on " + ends);
                }
            }
            JsonWriter row = new JsonWriter().beginObject();
            row.key("ascended").value(outcome != null && outcome.ascended());
            row.key("bosses").value(outcome == null ? 0 : outcome.bosses());
            row.key("cause").value(outcome == null ? "INCOMPLETE" : outcome.cause());
            row.key("chain").value(chain);
            row.key("depth").value(outcome == null ? 0 : outcome.depth());
            row.key("run").value(run);
            row.key("score").value(outcome == null ? 0 : outcome.score());
            row.key("turns").value(outcome == null ? 0 : outcome.turns());
            row.key("win").value(outcome != null && outcome.win());
            rows.add(row.endObject().toJson());
        }
        if (first == null) {
            throw new IllegalArgumentException(folder + " holds no finished Run to calibrate on");
        }
        if (rows.size() != seeds.entries().size()) {
            throw new IllegalArgumentException("the index in " + folder + " lists " + rows.size()
                    + " Runs and the Seed set " + set + " holds " + seeds.entries().size());
        }
        JsonWriter head = new JsonWriter().beginObject();
        head.key("brain").value(first.brain().name());
        head.key("brain_commit").value(first.brain().commit());
        head.key("brain_config").value(first.brain().configHash());
        head.key("cap").value(first.cap());
        head.key("command").value("./gradlew :rig:run --args=\"--brain " + first.brain().name()
                + " --seeds " + set + " --cap " + first.cap() + " --out <dir>\"");
        head.key("index_sha256").value(sha256(folder.resolve(RunIndex.RUNS)));
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

    private static String named(String line, String key, Path folder) {
        String value = LogHeader.string(line, key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("the index in " + folder + " has a line without \""
                    + key + "\": " + line);
        }
        return value;
    }

    /** The file a table is written to: its tag, Brain and Seed set, from its own first line. */
    static String tableName(String table) {
        String head = table.substring(0, table.indexOf('\n'));
        return LogHeader.string(head, "tag") + "-" + LogHeader.string(head, "brain") + "-"
                + LogHeader.string(head, "seed_set") + ".jsonl";
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
    public static String page(Result result) {
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
        out.append("| Brain | `").append(LogHeader.string(p, "brain")).append("`, invoked at `")
                .append(LogHeader.string(p, "brain_commit")).append("`, configuration `")
                .append(LogHeader.string(p, "brain_config")).append("` |\n");
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
        out.append("Two independent draws per pair, ")
                .append(String.format(Locale.ROOT, "%,d", stream.pairs())).append(" pairs in all.")
                .append(" A pair is scored by the Composite order, whose last step is turns survived,")
                .append(" counted in thousandths of a turn.\n\n");
        out.append("| | share |\n|---|---|\n");
        out.append("| Tied (scored ½) | ").append(share(stream.ties(), stream.pairs())).append(" |\n");
        out.append("| Missing (a Run with no ending; scored ½) | ")
                .append(share(stream.missing(), stream.pairs())).append(" |\n");
        out.append("| Tied with both Runs reached | ").append(share(stream.reachedTies(), stream.pairs()))
                .append(" |\n\n");
        out.append("## The grid\n\n");
        out.append(String.format(Locale.ROOT, "Every cell: p0 = 0.500, α = β = %.3f, maximum %d pairs;"
                        + " %,d sequences per hypothesis, seed %d, the same sequences for every cell."
                        + " H1 makes a share of the reached pairs wins; missing pairs stay missing."
                        + " *Errors within margin* means realized false-accept (H0 accept) and"
                        + " false-reject (H1 reject) are each at most nominal + %.3f; a void result"
                        + " is neither, so a cell that voids most results can be within margin and"
                        + " still useless, which *power* (at least %.1f%% of H1 sequences accept)"
                        + " catches. Pair counts are over all sequences, whatever the verdict; the"
                        + " median is the upper median.\n\n",
                ALPHA_PER_MIL / 1000.0, MAXIMUM, result.sims(), result.seed(),
                MARGIN_PER_MIL / 1000.0, POWER_PER_MIL / 10.0));
        out.append("| p1 | n0 | missing cap | H0 accept | H0 reject | H0 undecided | H0 void"
                + " | H0 mean pairs | H1 mean score | H1 accept (power) | H1 reject | H1 undecided"
                + " | H1 void | H1 mean pairs | H1 median pairs | errors within margin | power |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row row : result.rows()) {
            out.append(row(row));
        }
        out.append("\n## The chosen bounds\n\n");
        Row chosen = result.chosen();
        if (chosen == null) {
            out.append("No cell is both within margin and powerful, so no bounds are chosen from"
                    + " this table, and with no GSPRT bound to measure the rule picks no gate.\n");
            return out.toString();
        }
        Cell c = chosen.cell();
        out.append("The rule, fixed in code before the grid was run: of the cells within margin and"
                + " powerful, the smallest p1, then the smallest missing cap, then the fewest H1"
                + " pairs on average.\n\n");
        out.append(String.format(Locale.ROOT, "p0 = 0.500, p1 = %.3f, α = %.3f, β = %.3f, n0 = %d,"
                        + " nmax = %d, missing cap = %.3f.\n\n",
                c.p1PerMil() / 1000.0, ALPHA_PER_MIL / 1000.0, BETA_PER_MIL / 1000.0, c.burnIn(),
                MAXIMUM, c.missingPerMil() / 1000.0));
        out.append(String.format(Locale.ROOT, "Its rates on the choosing sequences flatter it, because"
                + " it was chosen on them. On %,d fresh sequences (seed %d), the rates to quote:\n\n",
                result.sims(), result.seed() + VALIDATION_OFFSET));
        out.append("| p1 | n0 | missing cap | H0 accept | H0 reject | H0 undecided | H0 void"
                + " | H0 mean pairs | H1 mean score | H1 accept (power) | H1 reject | H1 undecided"
                + " | H1 void | H1 mean pairs | H1 median pairs | errors within margin | power |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        out.append(row(result.validation()));
        out.append("\n## The e-process beside it\n\n");
        out.append(String.format(Locale.ROOT, "On the same %,d fresh sequences, both designs at each"
                        + " p1 of the grid, at the chosen burn-in and missing cap. The e-process"
                        + " accepts when its betting wealth against H0 reaches 1/α and needs no"
                        + " burn-in and no alternative to do so; it rejects when a second wealth,"
                        + " against a mean of p1 or more, reaches 1/β. n0 applies to the GSPRT"
                        + " alone. *Errors within margin* holds both designs to nominal + the margin;"
                        + " the e-process's own promise is stricter, a false-accept probability of"
                        + " at most α at any stopping time.\n\n", result.sims()));
        out.append("| statistic | p1 | n0 | missing cap | H0 accept | H0 reject | H0 undecided"
                + " | H0 void | H0 mean pairs | H1 mean score | H1 accept (power) | H1 reject"
                + " | H1 undecided | H1 void | H1 mean pairs | H1 median pairs | errors within margin"
                + " | power |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row row : result.duel()) {
            out.append("| ").append(row.statistic() == SequentialTest.Statistic.GSPRT ? "GSPRT"
                    : "e-process").append(' ').append(row(row));
        }
        out.append("\n## The gate\n\n");
        out.append(String.format(Locale.ROOT, "The rule (ADR-0012): the e-process replaces the GSPRT"
                        + " as the gate if the GSPRT's realized false-accept or false-reject rate on"
                        + " the fresh sequences exceeds its nominal rate by more than %.3f. The"
                        + " GSPRT's are %s and %s against %.3f and %.3f, so the gate is **%s**.\n",
                MARGIN_PER_MIL / 1000.0,
                share(result.validation().null0().accept(), result.validation().null0().total()),
                share(result.validation().alternative().reject(),
                        result.validation().alternative().total()),
                ALPHA_PER_MIL / 1000.0, BETA_PER_MIL / 1000.0,
                result.gate() == SequentialTest.Statistic.GSPRT ? "the GSPRT"
                        : result.gate() == SequentialTest.Statistic.EPROCESS ? "the e-process"
                        : "neither: the e-process is not within the margin and powerful either"));
        return out.toString();
    }

    private static String row(Row row) {
        Cell c = row.cell();
        Tally h0 = row.null0();
        Tally h1 = row.alternative();
        return String.format(Locale.ROOT, "| %.3f | %s | %.3f | %s | %s | %s | %s | %.1f | %.4f | %s"
                        + " | %s | %s | %s | %.1f | %d | %s | %s |\n",
                c.p1PerMil() / 1000.0,
                row.statistic() == SequentialTest.Statistic.GSPRT ? String.valueOf(c.burnIn()) : "—",
                c.missingPerMil() / 1000.0,
                share(h0.accept(), h0.total()), share(h0.reject(), h0.total()),
                share(h0.undecided(), h0.total()), share(h0.voided(), h0.total()),
                (double) h0.pairs() / h0.total(), h1.mean(),
                share(h1.accept(), h1.total()), share(h1.reject(), h1.total()),
                share(h1.undecided(), h1.total()), share(h1.voided(), h1.total()),
                (double) h1.pairs() / h1.total(), h1.median(),
                row.calibrated() ? "yes" : "no", row.powerful() ? "yes" : "no");
    }

    private static String causes(Table table) {
        TreeMap<String, Integer> causes = new TreeMap<>();
        TreeMap<Integer, Integer> depths = new TreeMap<>();
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
     * writes a table from a folder the Rig wrote, named for its tag, Brain and Seed set. Prints what
     * it wrote and how long it took.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1 && !(args.length == 3 && args[1].equals("extract"))) {
            throw new IllegalArgumentException("usage: Calibration <root> [extract <runs folder>], not "
                    + Arrays.toString(args));
        }
        Path root = Seeds.checkout(args[0]);
        long began = System.nanoTime();
        if (args.length == 3) {
            String table = extract(Path.of(args[2]), root);
            Path file = root.resolve(FOLDER).resolve(tableName(table));
            Files.createDirectories(file.getParent());
            Files.writeString(file, table, StandardCharsets.UTF_8);
            System.out.println("the calibration wrote " + file + " in "
                    + (System.nanoTime() - began) / 1_000_000L + " ms");
            return;
        }
        Result result = simulate(read(root.resolve(FOLDER).resolve(TABLE)), GRID, SIMULATIONS, SEED);
        Path page = root.resolve(PAGE);
        Files.writeString(page, page(result), StandardCharsets.UTF_8);
        System.out.println("the calibration wrote " + page + " in "
                + (System.nanoTime() - began) / 1_000_000L + " ms");
    }
}
