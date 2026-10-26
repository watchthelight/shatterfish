package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The simulator-speed measurement (story 6.4, ADR-0010's first measurement): what one redetermined rollout costs under the
 * swap-in-place host of ADR-0009, split into the five costs a search pays, each as a median and a 95th percentile.
 *
 * <p>The five costs: taking a snapshot of the live Run (with the statics and the profile's files held), scrubbing it into a
 * redetermined copy, loading the copy, rolling Actions out for a stated horizon (2, 3 and 4 Actions, the FR-34 range), and
 * restoring the live Run. The snapshots are the mid-fight waits of the Runs the real Brain plays over a Seed set (the
 * {@code smoke} set by default); at each one, several samples are scrubbed, and for each sample the same line of play is
 * rolled out at each horizon. The line is the Brain's own: the Action it would choose at the snapshot's wait, then at each
 * wait the copy reaches, so a rollout plays real fight Actions and not a fixed filler.
 *
 * <p>The measurement reports durations only and reads nothing from the game that the scrub does not already hand a
 * search. It plays in the Run's own process and thread, the host's contract, so the rate it reports is per process; the
 * Rig's pool multiplies it by the processes it runs, as E1's page says of its own rate.
 *
 * <p>Run it with {@code ./gradlew :harness:simulatorSpeed}; {@code -Psimulator.args="--seeds 25 --per-seed 2 --samples 3"}
 * overrides the defaults.
 */
final class SimulatorSpeed {

    /** The horizons, in Actions: FR-34's 2 to 4 hero turns. */
    static final int[] HORIZONS = {2, 3, 4};
    /** The names of the five costs, in the order the report prints them. */
    static final List<String> COSTS = List.of("snapshot", "scrub", "load", "rollout", "restore");

    record Config(String set, int seeds, int perSeed, int samples, int gap, int waitLimit, int warmup) {
        Config {
            if (seeds < 1 || perSeed < 1 || samples < 1 || gap < 0 || waitLimit < 1 || warmup < 0) {
                throw new IllegalArgumentException("a measurement needs at least one seed, snapshot and sample: " + this);
            }
        }

        static Config defaults() {
            return new Config("smoke", 25, 2, 3, 40, 3_000, 4);
        }

        String command() {
            return "--set " + set + " --seeds " + seeds + " --per-seed " + perSeed + " --samples " + samples
                    + " --gap " + gap + " --wait-limit " + waitLimit + " --warmup " + warmup;
        }
    }

    /** One timed rollout: the four host phases, the scrub of its sample, and how the line ended. */
    record Run(int horizon, long snapshot, long scrub, long load, long rollout, long restore, int applied, RolloutEnd end) {
        long total() {
            return snapshot + scrub + load + rollout + restore;
        }
    }

    /** A cost as the report states it. */
    record Stat(String name, int horizon, int count, double medianMs, double p95Ms) {
    }

    record Report(Config config, int snapshots, int scrubs, List<Run> runs, List<Stat> stats, List<String> skipped,
                  double loadMin, double loadMean, double loadMax) {

        /** The statistic for a cost; the rollout cost is one row per horizon, the others one row. */
        Stat stat(String name, int horizon) {
            return stats.stream().filter(s -> s.name().equals(name) && s.horizon() == horizon).findFirst().orElseThrow();
        }
    }

    private final Config config;
    private final BrainDecider planner;

    private SimulatorSpeed(Config config) {
        this.config = config;
        this.planner = RealBrain.decider();
    }

    // --- the measurement

    static Report run(Config config) throws IOException {
        return new SimulatorSpeed(config).measure();
    }

    private record Triple(long seed, HeroClass heroClass) {
    }

    private static List<Triple> triples(String set, int count) throws IOException {
        String text = Files.readString(RealBrain.root().resolve("seeds").resolve(set + ".json"), StandardCharsets.UTF_8);
        Matcher entry = Pattern.compile("\"heroClass\":\"([A-Z]+)\",\"seed\":(\\d+)").matcher(text);
        List<Triple> out = new ArrayList<>();
        while (out.size() < count && entry.find()) {
            out.add(new Triple(Long.parseLong(entry.group(2)), HeroClass.valueOf(entry.group(1))));
        }
        if (out.size() < count) {
            throw new IllegalArgumentException("the " + set + " set holds " + out.size() + " seeds, not " + count);
        }
        return out;
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    private static boolean fightable(Observation observation) {
        return observation.actors().actors().stream().anyMatch(a -> a.alignment() == Alignment.ENEMY);
    }

    /** The host's phase costs of the rollout in flight. */
    private static final class Clock implements RolloutHost.Phases {
        long snapshot;
        long load;
        long rollout;
        long restore;

        @Override
        public void snapshot(long nanos) {
            snapshot = nanos;
        }

        @Override
        public void load(long nanos) {
            load = nanos;
        }

        @Override
        public void rollout(long nanos) {
            rollout = nanos;
        }

        @Override
        public void restore(long nanos) {
            restore = nanos;
        }
    }

    private final List<Run> runs = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();
    private final List<Double> loads = new ArrayList<>();
    private int snapshots;
    private int scrubs;
    private int warmed;

    private Report measure() throws IOException {
        List<Triple> seeds = triples(config.set(), config.seeds());
        for (int index = 0; index < seeds.size(); index++) {
            playSeed(seeds.get(index), 7_000L + index);
        }
        return report();
    }

    private void playSeed(Triple triple, long salt) {
        HeadlessDriver driver = HeadlessDriver.start(triple.seed(), triple.heroClass(), salt);
        try {
            BrainDecider brain = RealBrain.decider();
            ActionExecutor executor = new ActionExecutor();
            int taken = 0;
            long lastWait = -config.gap();
            for (int i = 0; i < config.waitLimit() && taken < config.perSeed(); i++) {
                HeadlessDriver.Halt halt = driver.stepToInputWait();
                if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                    InterlevelScene.Mode mode = InterlevelScene.mode;
                    driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                    continue;
                }
                if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                    skipped.add(triple.seed() + " ended (" + halt.reason() + ") after " + taken + " snapshots");
                    return;
                }
                Observation observation = new Observer().observe();
                if (fightable(observation) && halt.waitIndex() - lastWait >= config.gap()) {
                    if (measureHere(driver, observation)) {
                        taken++;
                        lastWait = halt.waitIndex();
                    }
                }
                executor.execute(observation, brain.decide(observation));
            }
            if (taken < config.perSeed()) {
                skipped.add(triple.seed() + " gave " + taken + " of " + config.perSeed() + " snapshots in " + config.waitLimit() + " waits");
            }
        } finally {
            driver.close();
        }
    }

    /** Measures every sample and horizon at the wait the driver stands at; false when the scrub declines this wait. */
    private boolean measureHere(HeadlessDriver driver, Observation observation) {
        SnapshotStore store = new SnapshotStore();
        SnapshotScrubber scrubber = new SnapshotScrubber(store);
        Clock clock = new Clock();
        RolloutHost host = new RolloutHost(store, driver);
        // A first scrub finds out whether this wait is one the scrub accepts (a window in front, a boss floor, a chest in view).
        SnapshotHandle probe;
        try {
            probe = scrubber.scrub(store.take(driver), sample(1));
        } catch (IllegalStateException | Scrub.Refused refused) {
            return false;
        }
        store.drop(probe);
        loads.add(cpuLoad());
        snapshots++;
        int total = config.samples() + (warmed < config.warmup() ? 1 : 0);
        for (int n = 1; n <= total; n++) {
            boolean warm = warmed < config.warmup();
            // The scrub reads a stored snapshot; the take that makes it is the snapshot cost, which the host pays and times.
            SnapshotHandle live = store.take(driver);
            long start = System.nanoTime();
            SnapshotHandle copy = scrubber.scrub(live, sample(100L * snapshots + n));
            long scrubNanos = System.nanoTime() - start;
            store.drop(live);
            // The Brain's line, planned without the clock: the Action it picks here, then at each wait the copy reaches.
            List<Action> plan = plan(host, copy, observation);
            host.timedBy(clock);
            for (int horizon : HORIZONS) {
                RolloutResult result = host.simulate(copy, plan.subList(0, Math.min(horizon, plan.size())));
                if (!warm) {
                    runs.add(new Run(horizon, clock.snapshot, scrubNanos, clock.load, clock.rollout, clock.restore,
                            result.applied(), result.end()));
                }
            }
            host.timedBy(null);
            store.drop(copy);
            if (warm) {
                warmed++;
            } else {
                scrubs++;
            }
        }
        return true;
    }

    private List<Action> plan(RolloutHost host, SnapshotHandle copy, Observation first) {
        List<Action> plan = new ArrayList<>();
        Observation at = first;
        int most = HORIZONS[HORIZONS.length - 1];
        for (int h = 1; h <= most; h++) {
            plan.add(planner.decide(at));
            if (h == most) {
                break;
            }
            RolloutResult tried = host.simulate(copy, plan);
            if (tried.observations().size() <= h || tried.applied() < h) {
                break;
            }
            at = tried.observations().get(h);
        }
        return plan;
    }

    private static double cpuLoad() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getCpuLoad();
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // the load is context for the reader, not a result
        }
        return Double.NaN;
    }

    // --- the statistics

    private Report report() {
        List<Stat> stats = new ArrayList<>();
        stats.add(stat("snapshot", 0, runs.stream().mapToLong(Run::snapshot).toArray()));
        // A scrub is made once per sample and is shared by the three horizons.
        stats.add(stat("scrub", 0, runs.stream().filter(r -> r.horizon() == HORIZONS[0]).mapToLong(Run::scrub).toArray()));
        stats.add(stat("load", 0, runs.stream().mapToLong(Run::load).toArray()));
        for (int h : HORIZONS) {
            stats.add(stat("rollout", h, runs.stream().filter(r -> r.horizon() == h).mapToLong(Run::rollout).toArray()));
        }
        stats.add(stat("restore", 0, runs.stream().mapToLong(Run::restore).toArray()));
        double[] seen = loads.stream().filter(d -> !d.isNaN()).mapToDouble(Double::doubleValue).toArray();
        double min = seen.length == 0 ? Double.NaN : Arrays.stream(seen).min().getAsDouble();
        double max = seen.length == 0 ? Double.NaN : Arrays.stream(seen).max().getAsDouble();
        double mean = seen.length == 0 ? Double.NaN : Arrays.stream(seen).average().getAsDouble();
        return new Report(config, snapshots, scrubs, List.copyOf(runs), List.copyOf(stats), List.copyOf(skipped), min, mean, max);
    }

    static Stat stat(String name, int horizon, long[] nanos) {
        if (nanos.length == 0) {
            return new Stat(name, horizon, 0, Double.NaN, Double.NaN);
        }
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double median = sorted.length % 2 == 1 ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
        // Nearest rank: the smallest value with at least 95% of the sample at or below it.
        int rank = (int) Math.ceil(0.95 * sorted.length);
        return new Stat(name, horizon, sorted.length, median / 1e6, sorted[Math.max(0, rank - 1)] / 1e6);
    }

    // --- the rates, from medians

    /** Milliseconds one rollout costs at {@code horizon} when a scrub is shared by {@code perScrub} rollouts. */
    static double cycleMs(Report report, int horizon, double perScrub, boolean p95) {
        double cycle = 0;
        for (String name : List.of("snapshot", "load", "restore")) {
            Stat s = report.stat(name, 0);
            cycle += p95 ? s.p95Ms() : s.medianMs();
        }
        Stat rollout = report.stat("rollout", horizon);
        Stat scrub = report.stat("scrub", 0);
        return cycle + (p95 ? rollout.p95Ms() : rollout.medianMs()) + (p95 ? scrub.p95Ms() : scrub.medianMs()) / perScrub;
    }

    static double ratePerSecond(double cycleMs) {
        return 1000.0 / cycleMs;
    }

    /** Determinizations that fit {@code budgetMs} when each is scrubbed once and rolled out {@code rollouts} times. */
    static long determinizationsThatFit(Report report, int horizon, int rollouts, double budgetMs) {
        double scrub = report.stat("scrub", 0).medianMs();
        double each = cycleMs(report, horizon, 1, false) - scrub;
        return (long) Math.floor(budgetMs / (scrub + rollouts * each));
    }

    // --- the page

    static String markdown(Report r, String commit, String command) {
        StringBuilder out = new StringBuilder();
        Locale en = Locale.ROOT;
        out.append("### Environment\n\n");
        out.append(String.format(en, "- Machine: %s, %d logical processors, %s %s\n", System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), System.getProperty("os.name"), System.getProperty("os.version")));
        out.append(String.format(en, "- JDK: %s %s (%s)\n", System.getProperty("java.vendor"), System.getProperty("java.version"),
                System.getProperty("java.vm.name")));
        out.append("- Commit: ").append(commit).append('\n');
        out.append("- Command: `").append(command).append("`\n");
        out.append(String.format(en, "- Configuration: set `%s`, %d seeds, up to %d mid-fight snapshots each, %d samples per snapshot, "
                        + "horizons 2, 3 and 4 Actions, snapshots at least %d waits apart, %d warm-up samples discarded\n",
                r.config().set(), r.config().seeds(), r.config().perSeed(), r.config().samples(), r.config().gap(),
                r.config().warmup()));
        out.append(String.format(en, "- Measured: %d snapshots, %d scrubbed samples, %d rollouts\n", r.snapshots(), r.scrubs(), r.runs().size()));
        out.append(String.format(en, "- System CPU load at the snapshots: min %.0f%%, mean %.0f%%, max %.0f%%\n",
                100 * r.loadMin(), 100 * r.loadMean(), 100 * r.loadMax()));
        for (String s : r.skipped()) {
            out.append("- Note: seed ").append(s).append('\n');
        }
        out.append("\n### The five costs\n\n| Cost | Horizon | n | median (ms) | 95th percentile (ms) |\n|---|---|---|---|---|\n");
        for (Stat s : r.stats()) {
            out.append(String.format(en, "| %s | %s | %d | %.3f | %.3f |\n", s.name(), s.horizon() == 0 ? "-" : s.horizon() + " Actions",
                    s.count(), s.medianMs(), s.p95Ms()));
        }
        out.append("\n### Redetermined rollouts per second per process\n\n");
        out.append("| Horizon | one scrub per rollout (median) | one scrub per 8 rollouts (median) | one scrub per rollout (95th percentile) | measured end to end |\n|---|---|---|---|---|\n");
        for (int h : HORIZONS) {
            List<Run> mine = r.runs().stream().filter(x -> x.horizon() == h).toList();
            double measured = mine.isEmpty() ? Double.NaN : 1e9 * mine.size() / mine.stream().mapToLong(Run::total).sum();
            out.append(String.format(en, "| %d Actions | %.2f | %.2f | %.2f | %.2f |\n", h,
                    ratePerSecond(cycleMs(r, h, 1, false)), ratePerSecond(cycleMs(r, h, 8, false)),
                    ratePerSecond(cycleMs(r, h, 1, true)), measured));
        }
        out.append("\n### How the lines ended\n\n| Horizon | rollouts | played every Action | ended by death | ended by refusal | ended by horizon with Actions left | win |\n|---|---|---|---|---|---|---|\n");
        for (int h : HORIZONS) {
            List<Run> mine = r.runs().stream().filter(x -> x.horizon() == h).toList();
            long full = mine.stream().filter(x -> x.applied() == h).count();
            Map<RolloutEnd, Long> ends = new LinkedHashMap<>();
            for (RolloutEnd e : RolloutEnd.values()) {
                ends.put(e, mine.stream().filter(x -> x.end() == e && x.applied() < h).count());
            }
            out.append(String.format(en, "| %d | %d | %d | %d | %d | %d | %d |\n", h, mine.size(), full, ends.getOrDefault(RolloutEnd.DEATH, 0L),
                    ends.getOrDefault(RolloutEnd.REFUSED, 0L), ends.getOrDefault(RolloutEnd.HORIZON, 0L),
                    ends.getOrDefault(RolloutEnd.WIN, 0L)));
        }
        out.append("\n### Which cost dominates\n\n| Horizon | dominant cost (median, one scrub per rollout) | its share | rate as measured | rate if it were zero |\n|---|---|---|---|---|\n");
        for (int h : HORIZONS) {
            String best = null;
            double most = -1;
            for (String name : COSTS) {
                double ms = name.equals("rollout") ? r.stat(name, h).medianMs() : r.stat(name, 0).medianMs();
                if (ms > most) {
                    most = ms;
                    best = name;
                }
            }
            double cycle = cycleMs(r, h, 1, false);
            out.append(String.format(en, "| %d | %s (%.3f ms) | %.0f%% | %.2f/s | %.2f/s |\n", h, best, most, 100 * most / cycle,
                    ratePerSecond(cycle), ratePerSecond(cycle - most)));
        }
        out.append("\n### What fits a per-Decision budget (median costs, one process, one thread)\n\n");
        out.append("Rollouts that fit, one scrub each, by horizon; and determinizations times rollouts at horizon 3, as D x R = total.\n\n");
        out.append("| budgetMs | 2 Actions | 3 Actions | 4 Actions | R=1 | R=4 | R=16 |\n|---|---|---|---|---|---|---|\n");
        for (int budget : new int[] {10, 25, 50, 100, 250, 500, 1000, 2000, 5000}) {
            out.append("| ").append(budget);
            for (int h : HORIZONS) {
                out.append(" | ").append(fits((long) Math.floor(budget / cycleMs(r, h, 1, false))));
            }
            for (int rollouts : new int[] {1, 4, 16}) {
                long d = determinizationsThatFit(r, 3, rollouts, budget);
                out.append(" | ").append(d == 0 ? "none" : d + " x " + rollouts + " = " + d * rollouts);
            }
            out.append(" |\n");
        }
        return out.toString();
    }

    private static String fits(long n) {
        return n == 0 ? "none" : Long.toString(n);
    }

    // --- the command

    public static void main(String[] args) throws IOException {
        Config config = Config.defaults();
        for (int i = 0; i < args.length; i += 2) {
            String value = i + 1 < args.length ? args[i + 1] : "";
            config = switch (args[i]) {
                case "--set" -> new Config(value, config.seeds(), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), config.warmup());
                case "--seeds" -> new Config(config.set(), Integer.parseInt(value), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), config.warmup());
                case "--per-seed" -> new Config(config.set(), config.seeds(), Integer.parseInt(value), config.samples(), config.gap(), config.waitLimit(), config.warmup());
                case "--samples" -> new Config(config.set(), config.seeds(), config.perSeed(), Integer.parseInt(value), config.gap(), config.waitLimit(), config.warmup());
                case "--gap" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), Integer.parseInt(value), config.waitLimit(), config.warmup());
                case "--wait-limit" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), config.gap(), Integer.parseInt(value), config.warmup());
                case "--warmup" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), Integer.parseInt(value));
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            };
        }
        if (config.set().equals("goo") || config.set().equals("holdout")) {
            throw new IllegalArgumentException("the " + config.set() + " set is not for measurements of this kind");
        }
        Report report = run(config);
        System.out.println(markdown(report, commit(), "./gradlew :harness:simulatorSpeed -Psimulator.args=\"" + config.command() + "\""));
    }

    private static String commit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return p.waitFor() == 0 ? text : "unknown";
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
