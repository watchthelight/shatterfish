package org.shatterfish.harness;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.OracleObserver;
import org.shatterfish.harness.observer.OracleView;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.rng.Salt;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * The harness's command line: a seed, the one flag that exists only here, {@code --oracle}
 * (FR-11), and the benchmark of story 1.21, {@code --benchmark}. Without a flag a Run is read by
 * the fair {@link Observer}; with {@code --oracle}, by the {@link OracleObserver}, whose
 * Observation is marked in its header and whose sidecar is printed for the person who asked.
 * This is the only class in the harness allowed to construct an oracle observer, which
 * {@code OracleGateTest} holds by name for this class and for {@link Benchmark}, so the flag is a
 * property of a launch and never of a Run, a Brain or the measured loop. The benchmark's tactics
 * half counts the oracle's hidden facts, which is why it is nested here and why it runs only
 * when {@code --oracle} is given beside {@code --benchmark}: the same switch, the same mark.
 */
public final class Launcher {

    /** What a command line asked for. */
    public record Launch(long seed, boolean oracle, boolean benchmark, int runs, int samples, int siblings, int horizon) {

        /** The seed a launch plays when none is given. */
        public static final long DEFAULT_SEED = 31_415_926L;

        /**
         * Every count is positive, siblings are at least a pair (one sibling has no pair to agree
         * with), and every seed a benchmark derives, the Runs', the cost Run's and the samples',
         * is in the game's range ({@code …/utils/DungeonSeed.java}, {@code TOTAL_SEEDS}).
         */
        public Launch {
            if (runs < 1 || samples < 1 || horizon < 1) {
                throw new IllegalArgumentException("runs, samples and horizon are positive counts, not " + runs + ", " + samples
                        + " and " + horizon);
            }
            if (siblings < 2) {
                throw new IllegalArgumentException("siblings is at least 2, a pair to agree, not " + siblings);
            }
            long last = benchmark ? seed + runs + samples : seed;
            if (seed < 0 || last >= DungeonSeed.TOTAL_SEEDS) {
                throw new IllegalArgumentException("seed out of range: " + seed + (benchmark ? " to " + last : "")
                        + " (0 to " + (DungeonSeed.TOTAL_SEEDS - 1) + ")");
            }
        }

        /** A plain launch of {@code seed}. */
        public Launch(long seed, boolean oracle) {
            this(seed, oracle, false, 200, 24, 4, 20);
        }

        /**
         * Parses one seed, {@code --oracle}, {@code --benchmark} and its {@code --runs N},
         * {@code --samples N}, {@code --siblings N} and {@code --horizon N}, in any order. A second
         * seed, a seed outside the game's range, a count that is not a positive number, a flag
         * given twice, a count without {@code --benchmark} and anything else are refused by name.
         */
        public static Launch parse(String[] args) {
            long seed = DEFAULT_SEED;
            boolean seedGiven = false;
            boolean oracle = false;
            boolean benchmark = false;
            int runs = 200;
            int samples = 24;
            int siblings = 4;
            int horizon = 20;
            Set<String> given = new HashSet<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--") && !given.add(arg)) {
                    throw new IllegalArgumentException(arg + " given twice");
                }
                if (arg.equals("--oracle")) {
                    oracle = true;
                } else if (arg.equals("--benchmark")) {
                    benchmark = true;
                } else if (arg.equals("--runs") || arg.equals("--samples") || arg.equals("--siblings") || arg.equals("--horizon")) {
                    if (i + 1 >= args.length || !args[i + 1].matches("[0-9]{1,9}") || Integer.parseInt(args[i + 1]) < 1) {
                        throw new IllegalArgumentException(arg + " takes a positive count of at most nine digits");
                    }
                    int count = Integer.parseInt(args[++i]);
                    switch (arg) {
                        case "--runs" -> runs = count;
                        case "--samples" -> samples = count;
                        case "--siblings" -> siblings = count;
                        default -> horizon = count;
                    }
                } else if (arg.matches("-?[0-9]+")) {
                    if (seedGiven) {
                        throw new IllegalArgumentException("two seeds: " + seed + " and " + arg);
                    }
                    try {
                        seed = Long.parseLong(arg);
                    } catch (NumberFormatException tooLong) {
                        throw new IllegalArgumentException("seed out of range: " + arg);
                    }
                    if (seed < 0 || seed >= DungeonSeed.TOTAL_SEEDS) {
                        throw new IllegalArgumentException("seed out of range: " + arg + " (0 to " + (DungeonSeed.TOTAL_SEEDS - 1) + ")");
                    }
                    seedGiven = true;
                } else {
                    throw new IllegalArgumentException("unknown argument " + arg + "; a seed, --oracle, --benchmark, --runs N,"
                            + " --samples N, --siblings N and --horizon N are the arguments");
                }
            }
            if (!benchmark) {
                for (String count : new String[] {"--runs", "--samples", "--siblings", "--horizon"}) {
                    if (given.contains(count)) {
                        throw new IllegalArgumentException(count + " is a benchmark count and needs --benchmark");
                    }
                }
            }
            return new Launch(seed, oracle, benchmark, runs, samples, siblings, horizon);
        }
    }

    private Launcher() {
    }

    public static void main(String[] args) {
        Launch launch = Launch.parse(args);
        // A benchmark's report is its standard output, so that `> page.md` is the page; everything
        // else it says goes to standard error.
        PrintStream progress = launch.benchmark() ? System.err : System.out;
        HeadlessDriver.Boot boot = HeadlessDriver.boot();
        progress.println("Launcher: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        try {
            if (launch.benchmark()) {
                System.out.print(Benchmark.run(launch, progress).markdown());
                return;
            }
            // The runner draws the salt and says what it drew (ADR-0007): anyone with the seed,
            // the salt and the Actions can play the Run again.
            long salt = Salt.draw();
            try (HeadlessDriver driver = HeadlessDriver.start(launch.seed(), HeroClass.WARRIOR, salt)) {
                HeadlessDriver.Halt halt = driver.stepToInputWait();
                System.out.println("Launcher: salt " + Long.toHexString(salt));
                System.out.println("Launcher: seed " + launch.seed() + " (" + DungeonSeed.convertToCode(launch.seed()) + "), "
                        + HeroClass.WARRIOR.name() + ": " + halt.reason() + " after " + halt.framesStepped()
                        + " frame(s); hero at cell " + Dungeon.hero.pos + " on depth " + Dungeon.depth);
                read(launch, System.out);
            }
        } finally {
            Gdx.app.exit();
        }
    }

    /**
     * The read a launch asks for, at the Input wait the game is at: the oracle's, marked and with
     * the sidecar printed to {@code out}, when the flag was given; the fair Observer's otherwise.
     * Returns the Observation read, so a test can hold the branch to the flag.
     */
    public static Observation read(Launch launch, PrintStream out) {
        if (launch.oracle()) {
            OracleObserver.Read read = new OracleObserver().observe();
            out.println("Launcher: ORACLE, observation " + read.observation().hash());
            describe(read.view(), out);
            return read.observation();
        }
        Observation observation = new Observer().observe();
        out.println("Launcher: fair, observation " + observation.hash());
        return observation;
    }

    /** The sidecar, one line per thing, for a person and for nobody else. */
    private static void describe(OracleView view, PrintStream out) {
        out.println("Launcher: ORACLE seed " + view.seed());
        for (OracleView.Identity identity : view.identities()) {
            out.println("Launcher: ORACLE " + identity.family() + " " + identity.appearance() + " = " + identity.trueName()
                    + " (" + identity.type() + ")");
        }
        for (OracleView.Presence mob : view.mobs()) {
            out.println("Launcher: ORACLE mob " + mob.name() + " (" + mob.type() + ") at " + mob.cell() + " " + mob.hp() + "/"
                    + mob.ht() + " " + mob.state() + (mob.seen() ? " drawn" : " not drawn"));
        }
        out.println("Launcher: ORACLE hidden mimics " + view.hiddenMimics() + ", secret doors " + view.secretDoors()
                + ", hidden traps " + view.hiddenTraps());
    }

    /**
     * The E1 benchmark (story 1.21; NFR-3, SM-4): the random agent's throughput in one process,
     * the Observer's, the codec's and the JSON writer's cost per Observation, and Long et al.'s
     * two transferable properties for the game's tactics, measured with random playouts from
     * snapshots. Bias, the third, is a two-player quantity and is not measured. Every tuple the
     * benchmark plays is a function of the seed base and the index, so the same command on the
     * same commit plays the same Runs; the rates are the machine's.
     *
     * <p>The playouts are perfect-information rollouts on live snapshots by design and not a
     * Simulator (ADR-0010): nothing a playout chooses reads the sidecar, and the sidecar is read
     * only to count what a playout revealed. The tactics half runs only under {@code --oracle},
     * and every line it produces is marked.
     */
    public static final class Benchmark {

        /** The kinds of static hidden fact the sidecar names, in the order the report lists them. */
        static final List<String> KINDS = List.of("identity", "door", "trap", "mimic");

        /** The numbers, and the Markdown a Results page carries them in. */
        public record Report(Launch launch, String upstreamVersion, String environment, int runs, double seconds, long waits,
                             long refusals, long turns, double waitsPerSecond, double turnsPerSecond, double runsPerMinute,
                             double medianTurns, double medianWaits, Map<String, Integer> causes, Map<Integer, Integer> depths,
                             long costSeed, int costWaits, double observeMicros, double codecMicros, double writerMicros,
                             double codecBytes, double writerBytes, boolean tactics, int samplesAsked, int samplesTaken,
                             Map<String, Integer> skips, int pairedSamples, long playouts, long playoutsDropped,
                             double leafCorrelation, double chanceAgreement, double kappa, double survival,
                             int disambiguatedSamples, double disambiguation, Map<String, Double> hiddenByKind,
                             Map<String, Double> revealedByKind, List<String> samples) {

            /** The report as a person and a Results page read it. */
            public String markdown() {
                StringBuilder md = new StringBuilder();
                md.append("## Environment\n\n");
                md.append("- Shattered Pixel Dungeon ").append(upstreamVersion).append("; ").append(environment).append(".\n\n");
                md.append("## Throughput\n\n");
                md.append(String.format(Locale.ROOT, "- Runs: %d, in %.1f s of wall time, one process, each Run's start and close included.%n",
                        runs, seconds));
                md.append(String.format(Locale.ROOT, "- Input waits: %d applied Actions (%.1f per second per process); %d refusals re-served at the same wait.%n",
                        waits, waitsPerSecond, refusals));
                md.append(String.format(Locale.ROOT, "- Turns: %d (%.0f per second).%n", turns, turnsPerSecond));
                md.append(String.format(Locale.ROOT, "- Runs per minute: %.1f.%n", runsPerMinute));
                md.append(String.format(Locale.ROOT, "- Median Run length: %.1f turns, %.1f Input waits (the mean of the two middles for an even count).%n",
                        medianTurns, medianWaits));
                md.append("- Causes: ").append(causes).append(".\n");
                md.append("- Deepest floor reached, by Runs: ").append(depths).append(".\n\n");
                md.append("## Costs per Observation\n\n");
                md.append(String.format(Locale.ROOT, "Over %d waits of one Run, seed %d, each read timed alone from the Observation;"
                        + " the writer hashes on its own, so its time is not additive with the codec's:%n%n", costWaits, costSeed));
                md.append(String.format(Locale.ROOT, "| What | Microseconds | Bytes |%n|---|---|---|%n"));
                md.append(String.format(Locale.ROOT, "| `Observer.observe()` | %.1f | |%n", observeMicros));
                md.append(String.format(Locale.ROOT, "| codec: `ObservationCodec.encode` and `hash()` | %.1f | %.0f |%n", codecMicros, codecBytes));
                md.append(String.format(Locale.ROOT, "| JSON writer: `Observation.json()` | %.1f | %.0f |%n%n", writerMicros, writerBytes));
                md.append("## Tactics\n\n");
                if (!tactics) {
                    md.append("- Not measured: the tactics half reads the oracle sidecar and runs only with `--oracle`.\n\n");
                    md.append("## Samples\n\n");
                    return md.toString();
                }
                md.append("- ORACLE: the hidden facts below were read through the oracle sidecar (story 1.18) from the launcher;"
                        + " nothing a playout chose saw them.\n");
                md.append(String.format(Locale.ROOT, "- Samples: %d asked, %d taken, skipped %s; %d siblings asked, horizon %d waits;"
                        + " %d playouts reached the horizon or a death, %d cut short and dropped.%n",
                        samplesAsked, samplesTaken, skips, launch.siblings(), launch.horizon(), playouts, playoutsDropped));
                md.append(String.format(Locale.ROOT, "- Leaf correlation: %s (mean over the %d samples with a pair of the share of sibling pairs"
                        + " agreeing on alive or dead at the horizon); chance agreement at this survival %s, kappa %s.%n",
                        number(leafCorrelation, 3), pairedSamples, number(chanceAgreement, 3), number(kappa, 3)));
                md.append(String.format(Locale.ROOT, "- Survival at the horizon: %s of %d playouts.%n", number(survival, 3), playouts));
                md.append(String.format(Locale.ROOT, "- Disambiguation factor: %s (mean over the %d samples with a surviving playout of the mean"
                        + " share of the sample's hidden facts a surviving playout revealed).%n", number(disambiguation, 3), disambiguatedSamples));
                md.append("- Hidden facts per sample, by kind: ");
                for (String kind : KINDS) {
                    md.append(kind).append(' ').append(number(hiddenByKind.getOrDefault(kind, 0.0), 1)).append(", ");
                }
                md.setLength(md.length() - 2);
                md.append("; revealed share by kind: ");
                for (String kind : KINDS) {
                    md.append(kind).append(' ').append(number(revealedByKind.getOrDefault(kind, Double.NaN), 3)).append(", ");
                }
                md.setLength(md.length() - 2);
                md.append(".\n");
                md.append("- Bias: not measured; a two-player quantity.\n\n");
                md.append("## Samples\n\n");
                for (String sample : samples) {
                    md.append("- ").append(sample).append('\n');
                }
                return md.toString();
            }

            private static String number(double value, int places) {
                return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%." + places + "f", value);
            }
        }

        private Benchmark() {
        }

        /** Runs the whole benchmark for {@code launch}, printing progress to {@code out}. */
        public static Report run(Launch launch, PrintStream out) {
            String upstream = HeadlessDriver.boot().upstreamVersion();
            String environment = String.format(Locale.ROOT, "Java %s (%s), %s %s %s, %d processors, max heap %d MB",
                    System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                    System.getProperty("os.version"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory() / (1024 * 1024));

            // --- throughput: the random agent to the end of each Run, one process
            RunLoop loop = new RunLoop();
            List<Long> turnsPerRun = new ArrayList<>();
            List<Long> waitsPerRun = new ArrayList<>();
            Map<String, Integer> causes = new TreeMap<>();
            Map<Integer, Integer> depths = new TreeMap<>();
            long refusals = 0;
            long start = System.nanoTime();
            for (int i = 0; i < launch.runs(); i++) {
                long seed = launch.seed() + i;
                RunOutcome outcome;
                try {
                    outcome = loop.play(seed, HeroClass.WARRIOR, saltFor(seed), new RandomAgent(seed), RunLoop.TURN_CAP);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("benchmark Run " + (i + 1) + " of " + launch.runs() + ", seed " + seed
                            + ", salt " + Long.toHexString(saltFor(seed)) + ", failed", e);
                }
                turnsPerRun.add((long) outcome.turns());
                waitsPerRun.add(outcome.applied());
                refusals += outcome.refused();
                causes.merge(outcome.cause().name(), 1, Integer::sum);
                depths.merge(outcome.depth(), 1, Integer::sum);
                if ((i + 1) % 50 == 0) {
                    out.println("Benchmark: " + (i + 1) + " of " + launch.runs() + " Runs");
                }
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            long waits = waitsPerRun.stream().mapToLong(Long::longValue).sum();
            long turns = turnsPerRun.stream().mapToLong(Long::longValue).sum();

            // --- costs: one Run, each read timed alone
            long costSeed = launch.seed() + launch.runs();
            int costWaits = 0;
            long observeNanos = 0;
            long codecNanos = 0;
            long writerNanos = 0;
            long codecBytes = 0;
            long writerBytes = 0;
            try (HeadlessDriver driver = HeadlessDriver.start(costSeed, HeroClass.WARRIOR, saltFor(costSeed))) {
                RandomAgent agent = new RandomAgent(costSeed);
                ActionExecutor executor = new ActionExecutor();
                for (int i = 0; i < 400; i++) {
                    HeadlessDriver.Halt halt;
                    try {
                        halt = driver.stepToInputWait();
                    } catch (HeadlessDriver.Stalled stalled) {
                        out.println("Benchmark: the cost Run stalled after " + costWaits + " waits: " + stalled.getMessage());
                        break;
                    }
                    if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                        break;
                    }
                    long t0 = System.nanoTime();
                    Observation observation = new Observer().observe();
                    long t1 = System.nanoTime();
                    byte[] bytes = ObservationCodec.encode(observation);
                    String hash = observation.hash();
                    long t2 = System.nanoTime();
                    String json = observation.json();
                    long t3 = System.nanoTime();
                    observeNanos += t1 - t0;
                    codecNanos += t2 - t1;
                    writerNanos += t3 - t2;
                    codecBytes += bytes.length + hash.length();
                    writerBytes += json.length();
                    costWaits++;
                    Action chosen = agent.decide(observation);
                    if (chosen == null || !(executor.execute(observation, chosen) instanceof Outcome.Applied)) {
                        break;
                    }
                }
            }

            // --- tactics: snapshots along random Runs, siblings played out to a horizon
            Map<String, Integer> skips = new TreeMap<>();
            int taken = 0;
            int paired = 0;
            double correlationSum = 0;
            long payoffs = 0;
            long playouts = 0;
            long dropped = 0;
            int disambiguated = 0;
            double disambiguationSum = 0;
            Map<String, Long> hiddenByKindSum = new LinkedHashMap<>();
            Map<String, Double> revealedByKindSum = new LinkedHashMap<>();
            Map<String, Integer> revealedByKindCount = new LinkedHashMap<>();
            List<String> samples = new ArrayList<>();
            for (int s = 0; launch.oracle() && s < launch.samples(); s++) {
                long sampleSeed = launch.seed() + launch.runs() + 1 + s;
                int approach = (int) (6 + (s * 7L) % 30);
                String skip = null;
                try (HeadlessDriver driver = HeadlessDriver.start(sampleSeed, HeroClass.WARRIOR, saltFor(sampleSeed))) {
                    RandomAgent agent = new RandomAgent(sampleSeed);
                    ActionExecutor executor = new ActionExecutor();
                    HeadlessDriver.Halt halt = null;
                    Observation at = null;
                    for (int i = 0; i < approach && skip == null; i++) {
                        halt = driver.stepToInputWait();
                        if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                            skip = "the approach ended: " + halt.reason();
                            break;
                        }
                        at = new Observer().observe();
                        if (i + 1 < approach && !applyOne(executor, agent, at)) {
                            skip = "no choice without a descent";
                        }
                    }
                    if (skip == null && (halt == null || halt.window() != null || at == null)) {
                        skip = "a window in front";
                    }
                    List<Action> siblings = skip == null ? siblings(at, launch.siblings(), new Random(sampleSeed)) : List.of();
                    if (skip == null && siblings.size() < 2) {
                        skip = "fewer than two siblings offered";
                    }
                    if (skip != null) {
                        skips.merge(skip, 1, Integer::sum);
                        samples.add("seed " + sampleSeed + " wait " + approach + ": skipped (" + skip + ")");
                        continue;
                    }
                    SnapshotStore store = new SnapshotStore();
                    SnapshotHandle snapshot = store.take(driver);
                    Set<String> hidden = hiddenFacts(new OracleObserver().observe().view());
                    Map<String, Integer> hiddenKinds = kinds(hidden);
                    List<Integer> results = new ArrayList<>();
                    List<Double> revealed = new ArrayList<>();
                    Map<String, List<Double>> revealedKinds = new LinkedHashMap<>();
                    int cut = 0;
                    for (Action sibling : siblings) {
                        store.restore(snapshot, driver);
                        HeadlessDriver.Halt back = driver.stepToInputWait();
                        Observation restored = back.reason() == HeadlessDriver.Reason.INPUT_WAIT ? new Observer().observe() : null;
                        if (restored == null || !restored.hash().equals(at.hash())) {
                            skip = "the restore did not replay the wait";
                            break;
                        }
                        if (!(executor.execute(restored, sibling) instanceof Outcome.Applied)) {
                            cut++;
                            continue;
                        }
                        int alive = 1;
                        boolean complete = false;
                        for (int h = 0; h < launch.horizon(); h++) {
                            HeadlessDriver.Halt step = driver.stepToInputWait();
                            if (step.reason() == HeadlessDriver.Reason.HERO_DEAD) {
                                alive = 0;
                                complete = true;
                                break;
                            }
                            if (step.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                                break;
                            }
                            if (h + 1 == launch.horizon()) {
                                complete = true;
                                break;
                            }
                            if (!applyOne(executor, agent, new Observer().observe())) {
                                break;
                            }
                        }
                        if (!complete) {
                            cut++;
                            continue;
                        }
                        results.add(alive);
                        if (alive == 1 && !hidden.isEmpty()) {
                            Set<String> still = hiddenFacts(new OracleObserver().observe().view());
                            still.retainAll(hidden);
                            revealed.add(1.0 - (double) still.size() / hidden.size());
                            Map<String, Integer> stillKinds = kinds(still);
                            for (String kind : KINDS) {
                                int was = hiddenKinds.getOrDefault(kind, 0);
                                if (was > 0) {
                                    revealedKinds.computeIfAbsent(kind, k -> new ArrayList<>())
                                            .add(1.0 - (double) stillKinds.getOrDefault(kind, 0) / was);
                                }
                            }
                        }
                    }
                    if (skip != null) {
                        skips.merge(skip, 1, Integer::sum);
                        samples.add("seed " + sampleSeed + " wait " + approach + ": skipped (" + skip + ")");
                        continue;
                    }
                    taken++;
                    playouts += results.size();
                    dropped += cut;
                    payoffs += results.stream().mapToInt(Integer::intValue).sum();
                    for (String kind : KINDS) {
                        hiddenByKindSum.merge(kind, (long) hiddenKinds.getOrDefault(kind, 0), Long::sum);
                    }
                    double agreement = agreement(results);
                    if (!Double.isNaN(agreement)) {
                        correlationSum += agreement;
                        paired++;
                    }
                    if (!revealed.isEmpty()) {
                        disambiguationSum += mean(revealed);
                        disambiguated++;
                        for (Map.Entry<String, List<Double>> kind : revealedKinds.entrySet()) {
                            revealedByKindSum.merge(kind.getKey(), mean(kind.getValue()), Double::sum);
                            revealedByKindCount.merge(kind.getKey(), 1, Integer::sum);
                        }
                    }
                    samples.add("seed " + sampleSeed + " wait " + approach + ": " + results.size() + " playouts, payoffs " + results
                            + (cut > 0 ? ", " + cut + " cut short" : "") + ", hidden facts " + hidden.size() + " " + hiddenKinds
                            + ", revealed " + (revealed.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.2f", mean(revealed))));
                } catch (HeadlessDriver.Stalled stalled) {
                    skips.merge("stalled", 1, Integer::sum);
                    samples.add("seed " + sampleSeed + " wait " + approach + ": skipped (stalled: " + stalled.getMessage() + ")");
                } finally {
                    out.println("Benchmark: sample " + (s + 1) + " of " + launch.samples());
                }
            }
            double survival = playouts == 0 ? Double.NaN : (double) payoffs / playouts;
            double chance = Double.isNaN(survival) ? Double.NaN : survival * survival + (1 - survival) * (1 - survival);
            double leaf = paired == 0 ? Double.NaN : correlationSum / paired;
            double kappa = Double.isNaN(leaf) || Double.isNaN(chance) || chance == 1.0 ? Double.NaN : (leaf - chance) / (1 - chance);
            Map<String, Double> hiddenByKind = new LinkedHashMap<>();
            Map<String, Double> revealedByKind = new LinkedHashMap<>();
            for (String kind : KINDS) {
                hiddenByKind.put(kind, taken == 0 ? 0.0 : (double) hiddenByKindSum.getOrDefault(kind, 0L) / taken);
                int count = revealedByKindCount.getOrDefault(kind, 0);
                revealedByKind.put(kind, count == 0 ? Double.NaN : revealedByKindSum.get(kind) / count);
            }
            return new Report(launch, upstream, environment, launch.runs(), seconds, waits, refusals, turns, waits / seconds,
                    turns / seconds, launch.runs() / (seconds / 60.0), median(turnsPerRun), median(waitsPerRun), causes, depths,
                    costSeed, costWaits,
                    costWaits == 0 ? 0 : observeNanos / 1e3 / costWaits, costWaits == 0 ? 0 : codecNanos / 1e3 / costWaits,
                    costWaits == 0 ? 0 : writerNanos / 1e3 / costWaits, costWaits == 0 ? 0 : (double) codecBytes / costWaits,
                    costWaits == 0 ? 0 : (double) writerBytes / costWaits, launch.oracle(), launch.oracle() ? launch.samples() : 0,
                    taken, skips, paired, playouts, dropped, leaf, chance, kappa, survival, disambiguated,
                    disambiguated == 0 ? Double.NaN : disambiguationSum / disambiguated, hiddenByKind, revealedByKind, samples);
        }

        /** The salt a benchmark Run of {@code seed} declares: a function of the seed, so the tuple is published by the command. */
        static long saltFor(long seed) {
            return seed * 31L + 7L;
        }

        /**
         * One applied Action at a wait: the agent's choices, never a descent, until one is applied;
         * a refusal re-serves the wait as it does in the Run loop, and twenty refusals or no choice
         * is false.
         */
        private static boolean applyOne(ActionExecutor executor, RandomAgent agent, Observation observation) {
            for (int attempt = 0; attempt < 20; attempt++) {
                Action chosen = staying(agent, observation);
                if (chosen == null) {
                    return false;
                }
                if (executor.execute(observation, chosen) instanceof Outcome.Applied) {
                    return true;
                }
            }
            return false;
        }

        /** The agent's choice, but never a descent: a playout stays on the floor the sample was taken on. */
        private static Action staying(RandomAgent agent, Observation observation) {
            for (int attempt = 0; attempt < 20; attempt++) {
                Action chosen = agent.decide(observation);
                if (chosen == null) {
                    return null;
                }
                if (!(chosen instanceof Action.Descend)) {
                    return chosen;
                }
            }
            return null;
        }

        /** Up to {@code count} distinct Actions of the valid set, shuffled by {@code random}, never a descent. */
        static List<Action> siblings(Observation observation, int count, Random random) {
            List<Action> offered = new ArrayList<>(observation.actions().actions());
            offered.removeIf(a -> a instanceof Action.Descend);
            Collections.shuffle(offered, random);
            return offered.subList(0, Math.min(count, offered.size()));
        }

        /**
         * The static hidden facts of a sidecar, each as one key: an unknown appearance, a secret
         * door's cell, a hidden trap's cell, a hidden mimic's cell. Mobs are not among them, since a
         * mob's position is hidden and shown again as it moves.
         */
        static Set<String> hiddenFacts(OracleView view) {
            Set<String> facts = new HashSet<>();
            for (OracleView.Identity identity : view.identities()) {
                facts.add("identity " + identity.family() + " " + identity.appearance());
            }
            for (int cell : view.secretDoors()) {
                facts.add("door " + cell);
            }
            for (OracleView.Secret trap : view.hiddenTraps()) {
                facts.add("trap " + trap.cell());
            }
            for (int cell : view.hiddenMimics()) {
                facts.add("mimic " + cell);
            }
            return facts;
        }

        /** How many facts of each kind a set holds, keyed by the word before the first space. */
        static Map<String, Integer> kinds(Set<String> facts) {
            Map<String, Integer> counts = new TreeMap<>();
            for (String fact : facts) {
                counts.merge(fact.substring(0, fact.indexOf(' ')), 1, Integer::sum);
            }
            return counts;
        }

        /** The share of pairs of {@code payoffs} that agree, or NaN with fewer than two. */
        static double agreement(List<Integer> payoffs) {
            if (payoffs.size() < 2) {
                return Double.NaN;
            }
            int agree = 0;
            int pairs = 0;
            for (int a = 0; a < payoffs.size(); a++) {
                for (int b = a + 1; b < payoffs.size(); b++) {
                    pairs++;
                    agree += payoffs.get(a).equals(payoffs.get(b)) ? 1 : 0;
                }
            }
            return (double) agree / pairs;
        }

        private static double mean(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        }

        /** The median: the middle value, or the mean of the two middles for an even count; zero for none. */
        static double median(List<Long> values) {
            if (values.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int n = sorted.size();
            return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }
    }
}
