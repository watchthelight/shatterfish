package org.shatterfish.rig;

import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The tune runner (story 10.36, ADR-0026): SPSA over the registry of tunable numbers, Fishtest style.
 *
 * <pre>
 * ./gradlew :rig:tune --args="--set standard --class WARRIOR --first 20 --salt-base 5000 --iterations 3 --jobs 4 --params fight.margin_per_mille,identify.reach,descend.rests"
 * ./gradlew :rig:tune --args="--resume build/tune/20261029-101500 --iterations 40"
 * </pre>
 *
 * <p>Each iteration perturbs the vector by plus or minus {@code c_k} steps ({@link Spsa}), plays the plus and the
 * minus vector on <em>the same</em> triples under <em>the same</em> salts, scores each Run, and moves the vector
 * along the paired difference. Everything is written to a folder, and the folder is the whole state:
 *
 * <ul>
 *   <li>{@code tune.json}: the configuration, written once;</li>
 *   <li>{@code trajectory.jsonl}: one line per finished iteration (values, both scores, the step), which is also
 *       the commit marker, so a run killed in the middle of an iteration replays that iteration and no other;</li>
 *   <li>{@code pairs/iter-NNNN.jsonl}: the score of every pair, per class and seed;</li>
 *   <li>{@code candidate.json} and {@code candidate.diff.txt}: the Playbook the final state makes, and the numbers
 *       that differ. The committed Playbook is never edited.</li>
 * </ul>
 *
 * <h2>The objective</h2>
 *
 * <p>The score of one Run is {@link #BOSS_WEIGHT} per boss killed plus the deepest depth reached
 * ({@link #score}): a Goo kill is worth the five floors that lead to it, and every floor deeper is progress
 * even where nothing was killed. It reads the Run's log and nothing else, and counts only what the log's
 * ending states, so a Run that did not finish is not scored at all.
 *
 * <p>To keep the noise down, which is the whole difficulty of tuning on Runs that depend on luck:
 * the two vectors of an iteration play the same triples under the same salts, so the dungeon and the dice are
 * common to both and only the vector differs; the gradient is taken from the <em>paired differences</em>, which
 * cancel a seed's difficulty; a class counts as one voice however many of its Runs there are (the mean of the
 * class means, so no class dominates by being easier to measure); a pair with a Run missing on either side is
 * dropped from both; and the move of one iteration is capped. Each iteration plays fresh triples (a window
 * that rotates through the class's triples) under fresh salts, so no single slice is fitted.
 *
 * <h2>Seeds and salts</h2>
 *
 * <p>The tuner plays only {@code standard} (and {@code smoke}, for the checks) and refuses {@code goo},
 * {@code holdout}, {@code tengu} and every other set by name. Its salts are {@code 5000..6999} and nothing
 * else, and a run whose salts (iterations x classes x {@code --first}, from the base) reach outside is refused:
 * 1000 is the direction tables' slice, 3000 the confirmation slice a kept candidate must win on, and 7000 and up
 * the pre-registered confirmation tiebreaks, so a tuned number is never judged under the salts it was fitted on.
 */
public final class Tune {

    public static final String SET = "--set";
    public static final String CLASS = "--class";
    public static final String FIRST = "--first";
    public static final String SALT_BASE = "--salt-base";
    public static final String ITERATIONS = "--iterations";
    public static final String JOBS = "--jobs";
    public static final String PARAMS = "--params";
    public static final String RESUME = "--resume";
    public static final String OUT = "--out";
    public static final String ROOT = "--root";
    /** The gain {@code a} of {@link Spsa.Config}. */
    public static final String GAIN = "--gain";
    /** The perturbation {@code c}, in steps, of {@link Spsa.Config}. */
    public static final String PERTURBATION = "--perturbation";
    /** The most one iteration moves a number, in steps. */
    public static final String MAX_MOVE = "--max-move";
    /** Takes no value: keep every Run log of every iteration (they are large; the scores are kept either way). */
    public static final String KEEP_LOGS = "--keep-logs";

    static final List<String> KNOWN = List.of(SET, CLASS, FIRST, SALT_BASE, ITERATIONS, JOBS, PARAMS, RESUME, OUT, ROOT,
            GAIN, PERTURBATION, MAX_MOVE, KEEP_LOGS);

    /** What a resumed run may be told again; everything else is in {@code tune.json}. */
    private static final List<String> RESUMABLE = List.of(RESUME, ITERATIONS, JOBS, ROOT, KEEP_LOGS);

    /** The least salt the tuner plays: salts below it are the direction tables' (1000) and the confirmation slice's (3000). */
    public static final long SALT_FLOOR = 5000;

    /** One past the greatest salt the tuner plays: 7000 and up are the pre-registered confirmation tiebreaks'. */
    public static final long SALT_CEILING = 7000;

    /** Floors a boss kill is worth in the score of a Run. */
    public static final int BOSS_WEIGHT = 5;

    /** The sets a tuner plays; {@code smoke} is for checks of the tuner itself. */
    static final List<String> SETS = List.of(SeedSets.STANDARD, SeedSets.SMOKE);

    /** The sets that are refused by name, with the reason each is refused for. */
    private static final Map<String, String> REFUSED = Map.of(
            SeedSets.GOO, "the goo set is the E4 gate's and is played only under a Registration",
            SeedSets.HOLDOUT, "the holdout set is never run during development",
            "tengu", "the tengu set is the E10 gate's and is played only under a Registration");

    /** Schema of {@code tune.json} and {@code trajectory.jsonl}. */
    private static final int VERSION = 1;

    static final String CONFIG = "tune.json";
    static final String TRAJECTORY = "trajectory.jsonl";
    static final String CANDIDATE = "candidate.json";
    static final String CANDIDATE_DIFF = "candidate.diff.txt";
    static final String PAIRS = "pairs";

    private static final int DEFAULT_ITERATIONS = 10;
    private static final int DEFAULT_FIRST = 20;

    private Tune() {
    }

    public static void main(String[] args) {
        try {
            System.out.println(run(arguments(args)));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            System.err.println("tune: " + refused.getMessage());
            System.exit(2);
        }
    }

    // ------------------------------------------------------------------ refusals

    /** Refuses a set the tuner may not play, saying why. */
    static void allow(String set) {
        String why = REFUSED.get(set);
        if (why != null) {
            throw new IllegalArgumentException("the tuner does not play " + set + ": " + why);
        }
        if (!SETS.contains(set)) {
            throw new IllegalArgumentException("the tuner plays " + SETS + ", and " + set + " is not one of them");
        }
    }

    /** Refuses a salt base inside the range the direction tables and the confirmation slice use. */
    static void allowSalts(long saltBase) {
        if (saltBase < SALT_FLOOR) {
            throw new IllegalArgumentException("--salt-base " + saltBase + " is below " + SALT_FLOOR + ": salts under it are"
                    + " the direction tables' (1000) and the confirmation slice's (3000), and a tuned number is judged"
                    + " under salts it was not fitted on");
        }
    }

    /**
     * Refuses a run whose salts, {@code saltBase} up to {@code saltBase + span - 1}, reach outside
     * {@code 5000..6999}: from 7000 up the salts belong to the pre-registered confirmation tiebreaks.
     */
    static void allowSaltRange(long saltBase, long span) {
        allowSalts(saltBase);
        if (saltBase + span > SALT_CEILING) {
            throw new IllegalArgumentException("the salts " + saltBase + ".." + (saltBase + span - 1) + " reach past "
                    + (SALT_CEILING - 1) + ": the tuner plays " + SALT_FLOOR + ".." + (SALT_CEILING - 1) + " only, because from "
                    + SALT_CEILING + " up they are the confirmation tiebreaks'; use fewer iterations, classes or triples"
                    + " (a run plays iterations x classes x --first salts)");
        }
    }

    // ------------------------------------------------------------------ configuration

    /**
     * What a tuning run is, fixed when its folder is made.
     *
     * @param set        the Seed set
     * @param classes    the hero classes, in the order they are played
     * @param first      the triples of each class in one iteration
     * @param saltBase   the first salt
     * @param iterations the iterations to run
     * @param params     the tuned numbers, by registry name
     * @param start      the value of each at the start, in the order of {@code params}
     * @param spsa       the constants of the method
     */
    record Config(String set, List<HeroClass> classes, int first, long saltBase, int iterations, List<String> params,
                  double[] start, Spsa.Config spsa) {

        Config {
            allow(set);
            if (classes.isEmpty() || first < 1 || iterations < 1 || params.isEmpty() || start.length != params.size()) {
                throw new IllegalArgumentException("a tuning run has classes, a triple count, iterations and numbers to tune");
            }
            allowSaltRange(saltBase, (long) iterations * classes.size() * first);
        }

        List<Spsa.Param> spsaParams() {
            List<Spsa.Param> out = new ArrayList<>();
            for (String name : params) {
                Tunables.Tunable t = Tunables.named(name);
                if (t == null) {
                    throw new IllegalArgumentException("the tunable " + name + " is not registered");
                }
                out.add(new Spsa.Param(t.name(), t.min(), t.max(), t.step()));
            }
            return out;
        }

        String json() {
            TreeMap<String, String> m = new TreeMap<>();
            m.put("classes", array(classes.stream().map(c -> quote(c.name())).toList()));
            m.put("first", String.valueOf(first));
            m.put("iterations", String.valueOf(iterations));
            m.put("params", array(params.stream().map(Tune::quote).toList()));
            m.put("saltBase", String.valueOf(saltBase));
            m.put("set", quote(set));
            TreeMap<String, String> s = new TreeMap<>();
            s.put("alpha", num(spsa.alpha()));
            s.put("gain", num(spsa.gain()));
            s.put("gamma", num(spsa.gamma()));
            s.put("maxMove", num(spsa.maxMove()));
            s.put("perturbation", num(spsa.perturbation()));
            s.put("seed", String.valueOf(spsa.seed()));
            s.put("stability", num(spsa.stability()));
            m.put("spsa", object(s));
            TreeMap<String, String> st = new TreeMap<>();
            for (int i = 0; i < params.size(); i++) {
                st.put(params.get(i), num(start[i]));
            }
            m.put("start", object(st));
            m.put("version", String.valueOf(VERSION));
            return object(m);
        }

        static Config parse(String text) {
            Map<String, String> m = Json.object(text.strip());
            if (Json.integer(Json.required(m, "version", "tune.json")) != VERSION) {
                throw new IllegalArgumentException("tune.json has another version than " + VERSION);
            }
            List<HeroClass> classes = new ArrayList<>();
            for (String c : Json.array(Json.required(m, "classes", "tune.json"))) {
                classes.add(SeedSets.heroClass(Json.string(c)));
            }
            List<String> params = new ArrayList<>();
            for (String p : Json.array(Json.required(m, "params", "tune.json"))) {
                params.add(Json.string(p));
            }
            Map<String, String> st = Json.object(Json.required(m, "start", "tune.json"));
            double[] start = new double[params.size()];
            for (int i = 0; i < start.length; i++) {
                start[i] = Double.parseDouble(Json.required(st, params.get(i), "start"));
            }
            Map<String, String> s = Json.object(Json.required(m, "spsa", "tune.json"));
            Spsa.Config spsa = new Spsa.Config(d(s, "gain"), d(s, "perturbation"), d(s, "stability"), d(s, "alpha"),
                    d(s, "gamma"), d(s, "maxMove"), Json.number(Json.required(s, "seed", "spsa")));
            return new Config(Json.string(Json.required(m, "set", "tune.json")), classes,
                    Json.integer(Json.required(m, "first", "tune.json")), Json.number(Json.required(m, "saltBase", "tune.json")),
                    Json.integer(Json.required(m, "iterations", "tune.json")), params, start, spsa);
        }

        /** This configuration asking for {@code more} iterations. */
        Config withIterations(int more) {
            return new Config(set, classes, first, saltBase, more, params, start, spsa);
        }
    }

    private static double d(Map<String, String> m, String key) {
        return Double.parseDouble(Json.required(m, key, "spsa"));
    }

    // ------------------------------------------------------------------ the triples of an iteration

    /**
     * The triples iteration {@code k} plays: for each class in turn, {@code first} triples of the class in file
     * order from a window that moves {@code first} along at every iteration (and wraps), entry {@code j} of class
     * {@code ci} under the salt {@code saltBase + (k * classes + ci) * first + j}. Every salt of a run is
     * therefore played once, and the plus and the minus vector are handed this one list.
     */
    static List<DirectionSelect.Pick> picks(Path root, Config config, int k) {
        allow(config.set());
        List<SeedSet.Entry> entries = SeedSets.load(root, config.set()).set().entries();
        List<DirectionSelect.Pick> picks = new ArrayList<>();
        for (int ci = 0; ci < config.classes().size(); ci++) {
            HeroClass heroClass = config.classes().get(ci);
            List<SeedSet.Entry> pool = entries.stream().filter(e -> e.heroClass() == heroClass).toList();
            if (pool.size() < config.first()) {
                throw new IllegalArgumentException(config.set() + " holds " + pool.size() + " " + heroClass
                        + " triples and " + FIRST + " asks for " + config.first());
            }
            int from = (int) (((long) k * config.first()) % pool.size());
            for (int j = 0; j < config.first(); j++) {
                long salt = config.saltBase() + ((long) k * config.classes().size() + ci) * config.first() + j;
                picks.add(new DirectionSelect.Pick(pool.get((from + j) % pool.size()), salt));
            }
        }
        return List.copyOf(picks);
    }

    // ------------------------------------------------------------------ scoring

    /**
     * The score of one Run: {@link #BOSS_WEIGHT} per boss killed plus the deepest depth reached. Null when the log
     * has no ending, which is a Run that did not finish and is not scored.
     */
    static Double score(RunLogReader.Log log) {
        if (!log.readable() || log.records().isEmpty() || !(log.records().get(0) instanceof RunLog.Header)) {
            return null;
        }
        RunLog.End end = log.end();
        if (end == null) {
            return null;
        }
        int deepest = end.outcome().depth();
        for (RunLog.Wait wait : log.waits()) {
            deepest = Math.max(deepest, wait.depth());
        }
        return (double) BOSS_WEIGHT * end.outcome().bosses() + deepest;
    }

    /** One triple, played on both sides. */
    record Pair(String heroClass, String seedCode, long salt, double plus, double minus) {
    }

    /**
     * The scores of one iteration.
     *
     * @param plus    the plus vector's score: the mean over classes of the class's mean Run score
     * @param minus   the minus vector's score, the same way
     * @param se      the standard error of {@code plus - minus} under that weighting
     * @param pairs   the pairs scored
     * @param dropped the triples with a Run missing on a side
     * @param rows    one JSON line per pair, for {@code pairs/}
     */
    record Evaluation(double plus, double minus, double se, int pairs, int dropped, List<String> rows) {
    }

    /** The class-balanced paired score of {@code pairs}, with {@code dropped} triples left out. */
    static Evaluation combine(List<Pair> pairs, int dropped, int iteration) {
        Map<String, List<Pair>> byClass = new TreeMap<>();
        for (Pair p : pairs) {
            byClass.computeIfAbsent(p.heroClass(), c -> new ArrayList<>()).add(p);
        }
        double plus = 0;
        double minus = 0;
        double variance = 0;
        for (List<Pair> group : byClass.values()) {
            double sp = 0;
            double sm = 0;
            for (Pair p : group) {
                sp += p.plus();
                sm += p.minus();
            }
            int n = group.size();
            plus += sp / n;
            minus += sm / n;
            double mean = (sp - sm) / n;
            double ss = 0;
            for (Pair p : group) {
                double diff = p.plus() - p.minus() - mean;
                ss += diff * diff;
            }
            variance += n > 1 ? ss / (n - 1) / n : 0;
        }
        int classes = byClass.size();
        List<String> rows = new ArrayList<>();
        for (Pair p : pairs) {
            TreeMap<String, String> row = new TreeMap<>();
            row.put("class", quote(p.heroClass()));
            row.put("iteration", String.valueOf(iteration));
            row.put("minus", num(p.minus()));
            row.put("plus", num(p.plus()));
            row.put("salt", String.valueOf(p.salt()));
            row.put("seed", quote(p.seedCode()));
            rows.add(object(row));
        }
        if (classes == 0) {
            return new Evaluation(0, 0, 0, 0, dropped, rows);
        }
        return new Evaluation(plus / classes, minus / classes, Math.sqrt(variance) / classes, pairs.size(), dropped, rows);
    }

    /** Every pair of Run logs in {@code plusFolder} and {@code minusFolder} that both sides finished. */
    static Evaluation read(Path plusFolder, Path minusFolder, int expected, int iteration) {
        Map<String, Double> plus = scores(plusFolder);
        Map<String, Double> minus = scores(minusFolder);
        List<Pair> pairs = new ArrayList<>();
        for (Map.Entry<String, Double> entry : plus.entrySet()) {
            Double other = minus.get(entry.getKey());
            if (other != null) {
                String[] key = entry.getKey().split("\\|");
                pairs.add(new Pair(key[0], key[1], Long.parseLong(key[2]), entry.getValue(), other));
            }
        }
        return combine(pairs, expected - pairs.size(), iteration);
    }

    private static Map<String, Double> scores(Path folder) {
        Map<String, Double> out = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .filter(p -> !p.getFileName().toString().equals(RunIndex.RUNS)).sorted()::iterator) {
                RunLogReader.Log log = RunLogReader.of(file);
                Double score = score(log);
                if (score != null) {
                    RunLog.Header header = log.header();
                    if (header.oracle() || !header.registration().isEmpty()) {
                        throw new IllegalStateException(file + " is an oracle or a ranked Run; the tuner reads only"
                                + " unranked development Runs");
                    }
                    out.put(header.heroClass().name() + "|" + header.seedCode() + "|" + header.salt(), score);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + folder, e);
        }
        return out;
    }

    // ------------------------------------------------------------------ the loop

    /** Plays the triples of an iteration with one vector into a folder; returns how many Runs did not finish. */
    interface Player {
        int play(List<DirectionSelect.Pick> picks, Path folder, String tunables);
    }

    /** Scores the plus and the minus vector of iteration {@code k}. */
    interface Evaluator {
        Evaluation evaluate(int k, Map<String, Long> plus, Map<String, Long> minus);
    }

    /** The evaluator that plays: both vectors on the same list of triples, one after the other. */
    static Evaluator playing(Path root, Config config, Path folder, Player player, boolean keepLogs) {
        return (k, plus, minus) -> {
            List<DirectionSelect.Pick> picks = picks(root, config, k);
            Path iteration = folder.resolve("iter-" + String.format(Locale.ROOT, "%04d", k));
            org.shatterfish.harness.boot.TempDirs.deleteTree(iteration);
            player.play(picks, iteration.resolve("plus"), vector(plus));
            player.play(picks, iteration.resolve("minus"), vector(minus));
            Evaluation evaluation = read(iteration.resolve("plus"), iteration.resolve("minus"), picks.size(), k);
            if (!keepLogs) {
                org.shatterfish.harness.boot.TempDirs.deleteTree(iteration);
            }
            return evaluation;
        };
    }

    /** The finished iterations of {@code folder} as lines, refusing a trajectory that skips or repeats one. */
    static List<Map<String, String>> trajectory(Path folder) {
        Path file = folder.resolve(TRAJECTORY);
        List<Map<String, String>> lines = new ArrayList<>();
        if (!Files.exists(file)) {
            return lines;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, String> m = Json.object(line.strip());
                if (Json.integer(Json.required(m, "iteration", TRAJECTORY)) != lines.size()) {
                    throw new IllegalStateException(file + " does not count its iterations from 0 in order at line "
                            + (lines.size() + 1));
                }
                lines.add(m);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
        return lines;
    }

    /**
     * Runs the iterations {@code config} asks for that {@code folder} has not finished, and returns the final state.
     * Everything an iteration does depends on the configuration, the state before it and the scores it was given, so
     * a folder killed at any point and resumed ends where an uninterrupted run would have.
     */
    static double[] execute(Config config, Path folder, Evaluator evaluator, java.util.function.Consumer<String> say) {
        try {
            Files.createDirectories(folder.resolve(PAIRS));
            Path configFile = folder.resolve(CONFIG);
            if (Files.exists(configFile)) {
                Config held = Config.parse(Files.readString(configFile, StandardCharsets.UTF_8));
                if (!held.withIterations(config.iterations()).json().equals(config.json())) {
                    throw new IllegalArgumentException(configFile + " holds another configuration than the one asked for;"
                            + " a folder is one tuning run, so resume it as it is or start a new folder");
                }
                if (config.iterations() < held.iterations()) {
                    throw new IllegalArgumentException("the folder was begun for " + held.iterations() + " iterations; a"
                            + " resume may ask for more, not fewer");
                }
            }
            Files.writeString(configFile, config.json() + "\n", StandardCharsets.UTF_8);
            List<Spsa.Param> params = config.spsaParams();
            List<Map<String, String>> done = trajectory(folder);
            double[] theta = config.start().clone();
            if (!done.isEmpty()) {
                Map<String, String> next = Json.object(Json.required(done.get(done.size() - 1), "next", TRAJECTORY));
                for (int i = 0; i < theta.length; i++) {
                    theta[i] = Double.parseDouble(Json.required(next, config.params().get(i), "next"));
                }
            }
            for (int k = done.size(); k < config.iterations(); k++) {
                Spsa.Probe probe = Spsa.probe(config.spsa(), params, theta, k);
                Evaluation e = evaluator.evaluate(k, vector(config.params(), probe.plus()), vector(config.params(), probe.minus()));
                if (e.pairs() == 0) {
                    throw new IllegalStateException("iteration " + k + " scored no pair: every triple lost a Run on a side;"
                            + " see the .err files beside the logs (--keep-logs)");
                }
                Spsa.Update update = Spsa.update(config.spsa(), params, theta, probe, e.plus(), e.minus());
                Files.write(folder.resolve(PAIRS).resolve("iter-" + String.format(Locale.ROOT, "%04d", k) + ".jsonl"),
                        e.rows(), StandardCharsets.UTF_8);
                String line = line(config, probe, theta, e, update);
                Files.writeString(folder.resolve(TRAJECTORY), line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                say.accept(String.format(Locale.ROOT, "iteration %d: plus %.3f, minus %.3f, diff %+.3f (se %.3f, %d pairs, %d dropped)",
                        k, e.plus(), e.minus(), e.plus() - e.minus(), e.se(), e.pairs(), e.dropped()));
                theta = update.next();
            }
            return theta;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the tuning folder " + folder, e);
        }
    }

    private static String line(Config config, Spsa.Probe probe, double[] theta, Evaluation e, Spsa.Update update) {
        TreeMap<String, String> m = new TreeMap<>();
        m.put("iteration", String.valueOf(probe.iteration()));
        m.put("ak", num(probe.ak()));
        m.put("ck", num(probe.ck()));
        m.put("delta", map(config.params(), i -> String.valueOf(probe.delta()[i])));
        m.put("diff", num(e.plus() - e.minus()));
        m.put("dropped", String.valueOf(e.dropped()));
        m.put("gradient", map(config.params(), i -> num(update.gradient()[i])));
        m.put("minus", map(config.params(), i -> String.valueOf(probe.minus()[i])));
        m.put("minusScore", num(e.minus()));
        m.put("move", map(config.params(), i -> num(update.move()[i])));
        m.put("next", map(config.params(), i -> num(update.next()[i])));
        m.put("pairs", String.valueOf(e.pairs()));
        m.put("plus", map(config.params(), i -> String.valueOf(probe.plus()[i])));
        m.put("plusScore", num(e.plus()));
        m.put("se", num(e.se()));
        m.put("theta", map(config.params(), i -> num(theta[i])));
        return object(m);
    }

    private static String map(List<String> names, java.util.function.IntFunction<String> value) {
        TreeMap<String, String> m = new TreeMap<>();
        for (int i = 0; i < names.size(); i++) {
            m.put(names.get(i), value.apply(i));
        }
        return object(m);
    }

    private static Map<String, Long> vector(List<String> names, long[] values) {
        Map<String, Long> out = new TreeMap<>();
        for (int i = 0; i < names.size(); i++) {
            out.put(names.get(i), values[i]);
        }
        return out;
    }

    /** A vector as the child's {@code --tunables} text. */
    static String vector(Map<String, Long> values) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : new TreeMap<>(values).entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ the candidate

    /**
     * Writes the candidate for the final state: the Playbook {@code base} with the numbers that moved played over
     * it ({@code candidate.json}, in the canonical spelling the committed Playbooks use) and the list of what
     * differs ({@code candidate.diff.txt}). A number whose rounded final value is its starting value is left as the
     * Playbook has it, so a class entry that states it differently is not overwritten.
     */
    static Map<String, Long> writeCandidate(Config config, Playbook base, double[] theta, Path folder) {
        List<Spsa.Param> params = config.spsaParams();
        long[] finals = Spsa.rounded(params, theta);
        long[] starts = Spsa.rounded(params, config.start());
        Map<String, Long> moved = new TreeMap<>();
        StringBuilder diff = new StringBuilder("candidate for the Playbook " + base.name() + ", from the tuning run in this folder\n"
                + "(the committed Playbook is not edited; play the numbers with --tunables, or copy them by hand)\n\n");
        for (int i = 0; i < finals.length; i++) {
            Tunables.Tunable t = Tunables.named(config.params().get(i));
            if (finals[i] != starts[i]) {
                moved.put(t.name(), finals[i]);
            }
            diff.append(String.format(Locale.ROOT, "%-34s %6d -> %-6d (bounds %d..%d, step %d)%s\n",t.name(), starts[i], finals[i],
                    t.min(), t.max(), t.step(), finals[i] == starts[i] ? "  unchanged" : ""));
        }
        diff.append("\n--tunables ").append(moved.isEmpty() ? "(nothing moved)" : vector(moved)).append('\n');
        Playbook candidate = Tunables.with(base, moved);
        try {
            Files.writeString(folder.resolve(CANDIDATE), candidate.canonical() + "\n", StandardCharsets.UTF_8);
            Files.writeString(folder.resolve(CANDIDATE_DIFF), diff.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the candidate into " + folder, e);
        }
        return moved;
    }

    // ------------------------------------------------------------------ the command

    /** The flags as a map, refusing an unknown flag, a flag with no value, or one given twice. */
    static Map<String, String> arguments(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (!KNOWN.contains(flag)) {
                throw new IllegalArgumentException("unknown flag " + flag + "; the flags are " + KNOWN);
            }
            String value;
            if (flag.equals(KEEP_LOGS)) {
                value = "true";
            } else {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(flag + " takes a value");
                }
                value = args[++i];
            }
            if (given.put(flag, value) != null) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
        }
        return given;
    }

    /** Tunes what the arguments ask for and returns the text to print. */
    static String run(Map<String, String> a) {
        Path root = Path.of(a.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        int jobs = a.containsKey(JOBS) ? integer(a, JOBS, 1) : Direction.DEFAULT_JOBS;
        boolean keepLogs = a.containsKey(KEEP_LOGS);
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        String brain = Brains.SHATTERFISH;
        Path codex = root.resolve(CodexManifest.FOLDER).resolve(tag);
        Playbook base = PlaybookFile.read(PlaybookFile.of(root, brain), brain, codex);

        Config config;
        Path folder;
        if (a.containsKey(RESUME)) {
            for (String flag : a.keySet()) {
                if (!RESUMABLE.contains(flag)) {
                    throw new IllegalArgumentException(flag + " cannot be given with " + RESUME + ": the folder holds the"
                            + " configuration, and a resume may only ask for more " + ITERATIONS);
                }
            }
            folder = Path.of(a.get(RESUME)).toAbsolutePath().normalize();
            try {
                config = Config.parse(Files.readString(folder.resolve(CONFIG), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException(folder + " holds no " + CONFIG + ": it is not a tuning folder", e);
            }
            if (a.containsKey(ITERATIONS)) {
                config = config.withIterations(integer(a, ITERATIONS, 1));
            }
        } else {
            String set = a.getOrDefault(SET, SeedSets.STANDARD);
            allow(set);
            long saltBase = a.containsKey(SALT_BASE) ? Long.parseLong(a.get(SALT_BASE)) : SALT_FLOOR;
            allowSalts(saltBase);
            String classText = a.getOrDefault(CLASS, "ALL");
            List<HeroClass> classes = classText.equalsIgnoreCase("ALL") ? List.of(HeroClass.values())
                    : List.of(DirectionSelect.heroClass(classText));
            int iterations = a.containsKey(ITERATIONS) ? integer(a, ITERATIONS, 1) : DEFAULT_ITERATIONS;
            int first = a.containsKey(FIRST) ? integer(a, FIRST, 1) : DEFAULT_FIRST;
            List<String> params = params(a.getOrDefault(PARAMS, "all"));
            double[] start = new double[params.size()];
            for (int i = 0; i < start.length; i++) {
                start[i] = Tunables.value(base, Tunables.named(params.get(i)));
            }
            Spsa.Config defaults = Spsa.Config.of(iterations);
            Spsa.Config spsa = new Spsa.Config(a.containsKey(GAIN) ? Double.parseDouble(a.get(GAIN)) : defaults.gain(),
                    a.containsKey(PERTURBATION) ? Double.parseDouble(a.get(PERTURBATION)) : defaults.perturbation(),
                    defaults.stability(), defaults.alpha(), defaults.gamma(),
                    a.containsKey(MAX_MOVE) ? Double.parseDouble(a.get(MAX_MOVE)) : defaults.maxMove(), defaults.seed());
            config = new Config(set, classes, first, saltBase, iterations, params, start, spsa);
            folder = a.containsKey(OUT) ? Path.of(a.get(OUT)).toAbsolutePath().normalize()
                    : root.resolve("build").resolve("tune")
                            .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            if (Files.exists(folder) && !isEmpty(folder)) {
                throw new IllegalArgumentException(folder + " is not empty; a tuning run writes a folder of its own (or resume it with "
                        + RESUME + ")");
            }
        }
        // Reads the triples once before anything is played, so a set that cannot be read fails here.
        picks(root, config, 0);
        System.out.println("tune: " + config.set() + " " + (config.classes().size() == 6 ? "ALL" : config.classes().get(0))
                + ", " + config.first() + " per class, salts from " + config.saltBase() + ", " + config.params().size()
                + " numbers, " + config.iterations() + " iterations, " + jobs + " at a time, into " + folder);
        String commit = Runner.commitOf(root);
        Player player = (picks, into, tunables) -> Direction.play(picks, into, Runner.classpath(), root, tag, commit, jobs, brain, tunables);
        Evaluator evaluator = playing(root, config, folder, player, keepLogs);
        double[] theta = execute(config, folder, evaluator, System.out::println);
        Map<String, Long> moved = writeCandidate(config, base, theta, folder);
        return "tune: finished " + config.iterations() + " iterations; " + moved.size() + " of " + config.params().size()
                + " numbers moved; the candidate is " + folder.resolve(CANDIDATE) + " and the trajectory "
                + folder.resolve(TRAJECTORY);
    }

    /** The registered names {@code text} lists, or all of them for {@code all}. */
    static List<String> params(String text) {
        if (text.equals("all")) {
            return Tunables.ALL.stream().map(Tunables.Tunable::name).toList();
        }
        List<String> names = new ArrayList<>();
        for (String name : text.split(",", -1)) {
            String trimmed = name.strip();
            if (Tunables.named(trimmed) == null) {
                throw new IllegalArgumentException(PARAMS + " names " + trimmed + ", which is not registered; the registry is "
                        + Tunables.ALL.stream().map(Tunables.Tunable::name).toList());
            }
            if (names.contains(trimmed)) {
                throw new IllegalArgumentException(PARAMS + " names " + trimmed + " twice");
            }
            names.add(trimmed);
        }
        return List.copyOf(names);
    }

    // ------------------------------------------------------------------ small things

    private static int integer(Map<String, String> a, String flag, int least) {
        try {
            int value = Integer.parseInt(a.get(flag));
            if (value < least) {
                throw new IllegalArgumentException(flag + " is at least " + least + ": " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " is a number: " + a.get(flag));
        }
    }

    private static boolean isEmpty(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.findAny().isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException("could not list " + folder, e);
        }
    }

    private static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalStateException("a tuning figure is not finite: " + value);
        }
        return Double.toString(value);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String array(List<String> raw) {
        return "[" + String.join(",", raw) + "]";
    }

    private static String object(TreeMap<String, String> members) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, String> e : members.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(quote(e.getKey())).append(':').append(e.getValue());
        }
        return sb.append('}').toString();
    }
}
