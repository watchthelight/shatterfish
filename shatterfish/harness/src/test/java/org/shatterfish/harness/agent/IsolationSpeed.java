package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.ScrubbedStart;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.isolate.IsolatedHost;
import org.shatterfish.harness.isolate.IsolationLoader;
import org.shatterfish.harness.isolate.RolloutWorld;
import org.shatterfish.harness.isolate.WorldFactory;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The isolation spike's measurement (E10 W4, ADR-0024): what a rollout costs when the game is a second copy in its own class
 * loader and nothing is swapped, beside what the same rollout costs under the swap-in-place host, on the same snapshots,
 * the same scrubbed samples and the same lines of play, in one process.
 *
 * <p>Method of story 6.4 ({@link SimulatorSpeed}): the snapshots are the mid-fight waits of the Runs the real Brain plays
 * over a Seed set; at each, several samples are scrubbed, and each sample is rolled out at horizons of 2, 3 and 4 Actions
 * by the swap host, the pooled isolated host and, for a few, the fresh isolated host (a world per rollout). Every pair of
 * results is compared, so the correctness check runs at scale: it is the same scrubbed copy and the same Actions through two
 * hosts. It also times lines of 1 to 16 waits in the pooled world, for the cost of a simulated turn, and measures the memory
 * a world holds.
 *
 * <p>It reports durations, counts and sizes, and reads nothing from the game that the scrub does not already hand a search.
 * Run it with {@code ./gradlew :harness:isolationSpeed}; {@code -Pisolation.args="--seeds 8 --per-seed 2 --samples 3"}
 * overrides the defaults.
 */
final class IsolationSpeed {

    static final int[] HORIZONS = SimulatorSpeed.HORIZONS;
    static final int[] LINES = {1, 2, 4, 8, 16};

    record Config(String set, int seeds, int perSeed, int samples, int gap, int waitLimit, int warmup, int fresh) {
        Config {
            if (!set.equals("smoke") && !set.equals("standard")) {
                throw new IllegalArgumentException("the measurement runs on the smoke or standard set, not \"" + set + "\"");
            }
            if (seeds < 1 || perSeed < 1 || samples < 1 || gap < 0 || waitLimit < 1 || warmup < 0 || fresh < 0) {
                throw new IllegalArgumentException("a measurement needs at least one seed, snapshot and sample: " + this);
            }
        }

        static Config defaults() {
            return new Config("smoke", 12, 2, 3, 40, 3_000, 3, 6);
        }

        String command() {
            return "--set " + set + " --seeds " + seeds + " --per-seed " + perSeed + " --samples " + samples + " --gap " + gap
                    + " --wait-limit " + waitLimit + " --warmup " + warmup + " --fresh " + fresh;
        }
    }

    /** One swap-host rollout: its four phases. */
    record Swap(int horizon, long snapshot, long load, long rollout, long restore) {
        long total() {
            return snapshot + load + rollout + restore;
        }
    }

    /** One isolated rollout: its four phases, and (for a fresh world) what opening the world cost. */
    record Iso(int horizon, long hold, long load, long play, long restore, long open, int applied) {
        long total() {
            return open + hold + load + play + restore;
        }
    }

    /** A pair of results that did not agree, described without their content. */
    record Mismatch(String where, int horizon, String how) {
    }

    /** Rollouts a pooled world plays before its costs count as steady: its classes are loaded and its code has run. */
    static final int WORLD_WARMUP = 6;

    private final Config config;
    private final BrainDecider planner = RealBrain.decider();
    private final SnapshotStore store = new SnapshotStore();
    private final IsolatedHost pool = new IsolatedHost(store, IsolatedHost.Mode.POOLED);
    private final IsoClock isoClock = new IsoClock();
    private final IsolatedHost fresher = new IsolatedHost(store, IsolatedHost.Mode.FRESH).timedBy(isoClock);
    private final Map<String, Integer> uses = new LinkedHashMap<>();
    private final List<Iso> cold = new ArrayList<>();
    private final List<long[]> partsList = new ArrayList<>();
    private final List<Swap> swaps = new ArrayList<>();
    private final List<Iso> pooled = new ArrayList<>();
    private final List<Iso> fresh = new ArrayList<>();
    private final List<Long> scrubs = new ArrayList<>();
    private final List<Long> firstRollouts = new ArrayList<>();
    private final Map<Integer, List<Long>> perLine = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> perLineApplied = new LinkedHashMap<>();
    private final List<Long> opens = new ArrayList<>();
    private final List<Mismatch> mismatches = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();
    private final List<Double> loads = new ArrayList<>();
    private final List<String> memory = new ArrayList<>();
    private long classesDefined;
    private int compared;
    private int snapshots;
    private int warmed;
    private int freshLeft;
    private boolean memoryDone;

    private IsolationSpeed(Config config) {
        this.config = config;
        this.freshLeft = config.fresh();
        for (int line : LINES) {
            perLine.put(line, new ArrayList<>());
            perLineApplied.put(line, new ArrayList<>());
        }
    }

    record Report(Config config, int snapshots, int compared, List<Mismatch> mismatches, List<Swap> swaps, List<Iso> pooled,
                  List<Iso> cold, List<long[]> parts, List<Iso> fresh, List<Long> scrubs, List<Long> opens, List<Long> firstRollouts, Map<Integer, List<Long>> perLine,
                  Map<Integer, List<Integer>> perLineApplied, List<String> memory, long classesDefined, List<String> skipped,
                  double loadMin, double loadMean, double loadMax) {
    }

    static Report run(Config config) throws IOException {
        return new IsolationSpeed(config).measure();
    }

    private static BeliefSample sample(long n) {
        return ScrubSample.of(0x1234_5678_9ABCL * n + 17, 0xABCDEF01L * n + 3, 0x9E37_79B9L * n + 5).toBelief();
    }

    private Report measure() throws IOException {
        List<SimulatorSpeed.Triple> seeds = SimulatorSpeed.triples(config.set(), config.seeds());
        for (int index = 0; index < seeds.size(); index++) {
            playSeed(seeds.get(index), 7_000L + index);
        }
        double[] seen = loads.stream().filter(d -> !d.isNaN()).mapToDouble(Double::doubleValue).toArray();
        double min = seen.length == 0 ? Double.NaN : Arrays.stream(seen).min().getAsDouble();
        double max = seen.length == 0 ? Double.NaN : Arrays.stream(seen).max().getAsDouble();
        double mean = seen.length == 0 ? Double.NaN : Arrays.stream(seen).average().getAsDouble();
        pool.close();
        fresher.close();
        return new Report(config, snapshots, compared, List.copyOf(mismatches), List.copyOf(swaps), List.copyOf(pooled),
                List.copyOf(cold), List.copyOf(partsList), List.copyOf(fresh), List.copyOf(scrubs), List.copyOf(opens), List.copyOf(firstRollouts), perLine, perLineApplied,
                List.copyOf(memory), classesDefined, List.copyOf(skipped), min, mean, max);
    }

    private void playSeed(SimulatorSpeed.Triple triple, long salt) {
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
                if (SimulatorSpeed.fightable(observation) && halt.waitIndex() - lastWait >= config.gap()) {
                    if (measureHere(driver, observation, triple.seed() + "/" + triple.heroClass() + "@" + halt.waitIndex())) {
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

    private static final class SwapClock implements RolloutHost.Phases {
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

    private static final class IsoClock implements IsolatedHost.Phases {
        long opened;
        long hold;
        long load;
        long play;
        long restore;
        boolean rolled;
        long[] parts = new long[5];

        void reset() {
            opened = hold = load = play = restore = 0;
            rolled = false;
            parts = new long[5];
        }

        @Override
        public void loadParts(long endScene, long files, long game, long scene, long step) {
            parts = new long[] {endScene, files, game, scene, step};
        }

        @Override
        public void opened(long nanos) {
            opened = nanos;
        }

        @Override
        public void rolled(long hold, long load, long play, long restore) {
            this.hold = hold;
            this.load = load;
            this.play = play;
            this.restore = restore;
            rolled = true;
        }
    }

    private boolean measureHere(HeadlessDriver driver, Observation observation, String where) {
        SnapshotScrubber scrubber = new SnapshotScrubber(store);
        RolloutHost swap = new RolloutHost(store, driver);
        SnapshotHandle probe;
        try {
            probe = scrubber.scrub(store.take(driver), sample(1));
        } catch (IllegalStateException | Scrub.Refused refused) {
            return false;
        }
        store.drop(probe);
        loads.add(cpuLoad());
        snapshots++;
        SwapClock swapClock = new SwapClock();
        String heroClass = driverClass(driver);
        int total = config.samples() + (warmed < config.warmup() ? 1 : 0);
        for (int n = 1; n <= total; n++) {
            boolean warm = warmed < config.warmup();
            SnapshotHandle live = store.take(driver);
            long start = System.nanoTime();
            SnapshotHandle copy = scrubber.scrub(live, sample(100L * snapshots + n));
            long scrubNanos = System.nanoTime() - start;
            store.drop(live);
            List<Action> plan = plan(swap, copy, observation);
            for (int horizon : HORIZONS) {
                List<Action> line = plan.subList(0, Math.min(horizon, plan.size()));
                swap.timedBy(swapClock);
                RolloutResult viaSwap = swap.simulate(copy, line);
                swap.timedBy(null);

                isoClock.reset();
                boolean firstOfWorld = pool.openWorlds() == 0;
                pool.timedBy(isoClock);
                RolloutResult viaPool = pool.simulate(copy, line);
                pool.timedBy(null);
                long poolOpen = isoClock.opened;
                if (firstOfWorld) {
                    opens.add(poolOpen);
                    firstRollouts.add(isoClock.hold + isoClock.load + isoClock.play + isoClock.restore);
                }
                compare(where, horizon, viaSwap, viaPool);

                int used = uses.merge(heroClass, 1, Integer::sum);
                if (!warm && !firstOfWorld) {
                    Iso iso = new Iso(horizon, isoClock.hold, isoClock.load, isoClock.play, isoClock.restore, 0, viaPool.applied());
                    if (used <= WORLD_WARMUP) {
                        cold.add(iso);
                    } else {
                        pooled.add(iso);
                        partsList.add(isoClock.parts);
                        swaps.add(new Swap(horizon, swapClock.snapshot, swapClock.load, swapClock.rollout, swapClock.restore));
                        if (horizon == HORIZONS[0]) {
                            scrubs.add(scrubNanos);
                        }
                    }
                }
                if (!warm && freshLeft > 0 && horizon == 3) {
                    isoClock.reset();
                    RolloutResult viaFresh = fresher.timedBy(isoClock).simulate(copy, line);
                    compare(where + " (fresh)", horizon, viaSwap, viaFresh);
                    fresh.add(new Iso(horizon, isoClock.hold, isoClock.load, isoClock.play, isoClock.restore, isoClock.opened,
                            viaFresh.applied()));
                    freshLeft--;
                }
            }
            if (!warm) {
                if (uses.getOrDefault(heroClass, 0) > WORLD_WARMUP) {
                    timeLines(pool, copy);
                }
                if (!memoryDone) {
                    memoryDone = true;
                    measureMemory(store, copy);
                }
            }
            store.drop(copy);
            if (warm) {
                warmed++;
            }
        }
        classesDefined = Math.max(classesDefined, pool.definedClasses(heroClass));
        return true;
    }

    private static String driverClass(HeadlessDriver driver) {
        return com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.heroClass.name();
    }

    private void compare(String where, int horizon, RolloutResult a, RolloutResult b) {
        compared++;
        if (a.equals(b)) {
            return;
        }
        String how;
        if (a.end() != b.end() || a.applied() != b.applied()) {
            how = "ended " + a.end() + "/" + a.applied() + " (swap) against " + b.end() + "/" + b.applied() + " (isolated)";
        } else if (a.observations().size() != b.observations().size()) {
            how = a.observations().size() + " against " + b.observations().size() + " Observations";
        } else {
            how = "same end and length; Observation";
            for (int i = 0; i < a.observations().size(); i++) {
                if (!a.observations().get(i).equals(b.observations().get(i))) {
                    List<String> sections = new ArrayList<>();
                    Map<String, String> x = a.observations().get(i).sectionHashes();
                    Map<String, String> y = b.observations().get(i).sectionHashes();
                    for (String key : x.keySet()) {
                        if (!x.get(key).equals(y.get(key))) {
                            sections.add(key);
                        }
                    }
                    how += " " + i + " differs in " + sections;
                    if (sections.contains("log")) {
                        List<String> x1 = a.observations().get(i).log().lines().stream().map(Object::toString).toList();
                        List<String> y1 = b.observations().get(i).log().lines().stream().map(Object::toString).toList();
                        List<String> onlySwap = new ArrayList<>(x1);
                        onlySwap.removeAll(y1);
                        List<String> onlyIso = new ArrayList<>(y1);
                        onlyIso.removeAll(x1);
                        how += " (only in swap: " + onlySwap + "; only in isolated: " + onlyIso + "; lines " + x1.size() + " against " + y1.size() + ")";
                    }
                    break;
                }
            }
        }
        mismatches.add(new Mismatch(where, horizon, how));
    }

    private List<Action> plan(RolloutHost swap, SnapshotHandle copy, Observation first) {
        List<Action> plan = new ArrayList<>();
        Observation at = first;
        int most = HORIZONS[HORIZONS.length - 1];
        for (int h = 1; h <= most; h++) {
            plan.add(planner.decide(at));
            if (h == most) {
                break;
            }
            RolloutResult tried = swap.simulate(copy, plan);
            if (tried.observations().size() <= h || tried.applied() < h) {
                break;
            }
            at = tried.observations().get(h);
        }
        return plan;
    }

    /** Lines of plain waits, for what a simulated turn costs in a warm world. */
    private void timeLines(IsolatedHost pool, SnapshotHandle copy) {
        IsoClock clock = new IsoClock();
        pool.timedBy(clock);
        for (int length : LINES) {
            List<Action> waits = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                waits.add(new Action.Wait());
            }
            clock.reset();
            RolloutResult result = pool.simulate(copy, waits);
            perLine.get(length).add(clock.play);
            perLineApplied.get(length).add(result.applied());
        }
        pool.timedBy(null);
    }

    /** What worlds hold: heap and metaspace after a collection, with 0 to 3 worlds open, each having played a rollout. */
    private void measureMemory(SnapshotStore store, SnapshotHandle copy) {
        ScrubbedStart start = ScrubbedStart.from(store, copy);
        String heroClass = start.heroClass();
        List<RolloutWorld> worlds = new ArrayList<>();
        List<Action> line = List.of(new Action.Wait(), new Action.Wait());
        try {
            memory.add(row(0));
            for (int i = 1; i <= 3; i++) {
                IsolationLoader loader = new IsolationLoader(IsolationSpeed.class.getClassLoader());
                RolloutWorld world = ServiceLoader.load(WorldFactory.class, loader).findFirst().orElseThrow().open(heroClass);
                worlds.add(world);
                world.rollout(start, line);
                memory.add(row(i) + " | " + loader.definedClasses());
            }
        } finally {
            for (RolloutWorld world : worlds) {
                world.close();
            }
        }
    }

    private static String row(int worlds) {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Runtime rt = Runtime.getRuntime();
        double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        double meta = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().equals("Metaspace")) {
                meta = pool.getUsage().getUsed() / 1048576.0;
            }
        }
        int classes = ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
        double committed = Double.NaN;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            committed = os.getCommittedVirtualMemorySize() / 1048576.0;
        }
        return String.format(Locale.ROOT, "%d | %.1f | %.1f | %d | %.0f", worlds, heap, meta, classes, committed);
    }

    private static double cpuLoad() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getCpuLoad();
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // context for the reader, not a result
        }
        return Double.NaN;
    }

    // --- the page

    private static SimulatorSpeed.Stat stat(String name, int horizon, long[] nanos) {
        return SimulatorSpeed.stat(name, horizon, nanos);
    }

    private static long[] of(List<Swap> list, int horizon, java.util.function.ToLongFunction<Swap> f) {
        return list.stream().filter(x -> x.horizon() == horizon).mapToLong(f).toArray();
    }

    private static long[] ofIso(List<Iso> list, int horizon, java.util.function.ToLongFunction<Iso> f) {
        return list.stream().filter(x -> horizon == 0 || x.horizon() == horizon).mapToLong(f).toArray();
    }

    private static double med(long[] nanos) {
        return SimulatorSpeed.stat("x", 0, nanos).medianMs();
    }

    private static double p95(long[] nanos) {
        return SimulatorSpeed.stat("x", 0, nanos).p95Ms();
    }

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
                        + "horizons 2, 3 and 4 Actions, %d warm-up samples discarded, %d fresh-world rollouts\n",
                r.config().set(), r.config().seeds(), r.config().perSeed(), r.config().samples(), r.config().warmup(), r.config().fresh()));
        out.append(String.format(en, "- Measured: %d snapshots, %d warm swap rollouts, %d warm pooled isolated rollouts, %d fresh-world rollouts\n",
                r.snapshots(), r.swaps().size(), r.pooled().size(), r.fresh().size()));
        out.append(String.format(en, "- System CPU load at the snapshots: min %.0f%%, mean %.0f%%, max %.0f%%\n",
                100 * r.loadMin(), 100 * r.loadMean(), 100 * r.loadMax()));
        for (String s : r.skipped()) {
            out.append("- Note: seed ").append(s).append('\n');
        }

        out.append("\n### Correctness: the same scrubbed copy and Actions through both hosts\n\n");
        out.append(String.format(en, "%d pairs of results compared (swap host against pooled isolated, and against fresh isolated); "
                + "**%d differ**.\n", r.compared(), r.mismatches().size()));
        for (Mismatch m : r.mismatches()) {
            out.append(String.format(en, "- %s, %d Actions: %s\n", m.where(), m.horizon(), m.how()));
        }

        out.append("\n### The cost of one rollout (median / 95th percentile, ms)\n\n");
        out.append("| Horizon | swap: snapshot | load | rollout | restore | **swap total** | isolated pooled: hold | load | play | restore | **pooled total** | speed-up |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int h : HORIZONS) {
            long[] swapTotal = of(r.swaps(), h, Swap::total);
            long[] poolTotal = ofIso(r.pooled(), h, Iso::total);
            out.append(String.format(en, "| %d | %.3f | %.3f | %.3f | %.3f | **%.3f / %.3f** | %.3f | %.3f | %.3f | %.3f | **%.3f / %.3f** | %.1fx |\n", h,
                    med(of(r.swaps(), h, Swap::snapshot)), med(of(r.swaps(), h, Swap::load)), med(of(r.swaps(), h, Swap::rollout)),
                    med(of(r.swaps(), h, Swap::restore)), med(swapTotal), p95(swapTotal),
                    med(ofIso(r.pooled(), h, Iso::hold)), med(ofIso(r.pooled(), h, Iso::load)), med(ofIso(r.pooled(), h, Iso::play)),
                    med(ofIso(r.pooled(), h, Iso::restore)), med(poolTotal), p95(poolTotal), med(swapTotal) / med(poolTotal)));
        }
        out.append("\n### Where the isolated load goes (pooled, median / 95th percentile, ms)\n\n");
        out.append("| End the old scene | write the files | load the game and floor | build the new scene | step to the first wait | the whole load |\n|---|---|---|---|---|---|\n");
        for (int k = 0; k < 5; k++) {
            final int part = k;
            long[] v = r.parts().stream().mapToLong(a -> a[part]).toArray();
            out.append(String.format(en, "| %.2f / %.2f ", med(v), p95(v)));
        }
        long[] wholeLoad = ofIso(r.pooled(), 0, Iso::load);
        out.append(String.format(en, "| %.2f / %.2f |\n", med(wholeLoad), p95(wholeLoad)));
        long[] scrub = r.scrubs().stream().mapToLong(Long::longValue).toArray();
        out.append(String.format(en, "\nThe scrub (the same for both hosts, in the live world): median %.3f ms, 95th percentile %.3f ms.\n",
                med(scrub), p95(scrub)));

        out.append("\n### Setup: opening a world\n\n");
        long[] opens = r.opens().stream().mapToLong(Long::longValue).toArray();
        long[] firsts = r.firstRollouts().stream().mapToLong(Long::longValue).toArray();
        out.append("| What | n | median (ms) | 95th percentile (ms) |\n|---|---|---|---|\n");
        out.append(String.format(en, "| open a world (loader, boot, Run start, first floor), first of a pooled host, JVM already warm for the live game | %d | %.1f | %.1f |\n",
                opens.length, med(opens), p95(opens)));
        out.append(String.format(en, "| the first rollout in a freshly opened world (classes still loading, code cold) | %d | %.1f | %.1f |\n",
                firsts.length, med(firsts), p95(firsts)));
        long[] freshOpen = r.fresh().stream().mapToLong(Iso::open).toArray();
        long[] freshAll = r.fresh().stream().mapToLong(Iso::total).toArray();
        long[] coldTotal = r.cold().stream().mapToLong(Iso::total).toArray();
        out.append(String.format(en, "| the first %d rollouts of a world after its first (cold code), per rollout | %d | %.1f | %.1f |\n",
                WORLD_WARMUP - 1, coldTotal.length, med(coldTotal), p95(coldTotal)));
        out.append(String.format(en, "| a fresh world per rollout: open | %d | %.1f | %.1f |\n", freshOpen.length, med(freshOpen), p95(freshOpen)));
        out.append(String.format(en, "| a fresh world per rollout: open plus the 3-Action rollout | %d | %.1f | %.1f |\n", freshAll.length, med(freshAll), p95(freshAll)));
        out.append(String.format(en, "\nClasses defined by one pooled world after its rollouts: %d.\n", r.classesDefined()));

        out.append("\n### The cost of a simulated turn (pooled world, lines of waits, play phase only)\n\n");
        out.append("| Line | rollouts | played in full | median play (ms) | per turn, full lines (ms) |\n|---|---|---|---|---|\n");
        for (int line : LINES) {
            List<Long> plays = r.perLine().get(line);
            List<Integer> applied = r.perLineApplied().get(line);
            List<Long> full = new ArrayList<>();
            for (int i = 0; i < plays.size(); i++) {
                if (applied.get(i) == line) {
                    full.add(plays.get(i));
                }
            }
            long[] all = plays.stream().mapToLong(Long::longValue).toArray();
            long[] fl = full.stream().mapToLong(Long::longValue).toArray();
            out.append(String.format(en, "| %d waits | %d | %d | %.3f | %s |\n", line, all.length, fl.length, med(all),
                    fl.length == 0 ? "n/a" : String.format(en, "%.3f", med(fl) / line)));
        }
        out.append("\nThe play phase of a line includes the observation of each wait reached (the Observer), which the swap host's rollout phase includes too.\n");

        out.append("\n### Memory: worlds held open at once (after three collections)\n\n");
        out.append("| Worlds | heap used (MiB) | metaspace used (MiB) | loaded classes | committed virtual memory (MiB) | classes defined by the newest world |\n|---|---|---|---|---|---|\n");
        for (String row : r.memory()) {
            out.append("| ").append(row).append(row.chars().filter(c -> c == '|').count() < 5 ? " | - " : "").append(" |\n");
        }

        out.append("\n### Rollouts per second per process (median costs, one scrub per rollout, horizon 3)\n\n");
        double scrubMs = med(scrub);
        double swapMs = med(of(r.swaps(), 3, Swap::total)) + scrubMs;
        double poolMs = med(ofIso(r.pooled(), 3, Iso::total)) + scrubMs;
        out.append("| Host | ms per rollout | rollouts per second |\n|---|---|---|\n");
        out.append(String.format(en, "| swap in place | %.1f | %.1f |\n", swapMs, 1000 / swapMs));
        out.append(String.format(en, "| isolated, pooled | %.1f | %.1f |\n", poolMs, 1000 / poolMs));
        out.append(String.format(en, "| isolated, pooled, one scrub shared by 8 rollouts | %.1f | %.1f |\n",
                med(ofIso(r.pooled(), 3, Iso::total)) + scrubMs / 8, 1000 / (med(ofIso(r.pooled(), 3, Iso::total)) + scrubMs / 8)));
        double freshMs = med(freshAll) + scrubMs;
        out.append(String.format(en, "| isolated, a fresh world per rollout | %.1f | %.2f |\n", freshMs, 1000 / freshMs));

        out.append("\n### What the least searches of story 6.6 cost with each host (median costs)\n\n");
        out.append("The shapes are story 6.6's: option 3 is 3 candidate Actions, one rollout each, in 2 worlds (6 rollouts, 2 scrubs); option 5 is "
                + "9 leaves in 2 worlds (18 rollouts, 2 scrubs); option 4 is 100 iterations (100 scrubs and 100 rollouts).\n\n");
        out.append("| Option | rollouts | swap host (ms) | isolated pooled (ms) |\n|---|---|---|---|\n");
        double swapRoll = med(of(r.swaps(), 3, Swap::total));
        double poolRoll = med(ofIso(r.pooled(), 3, Iso::total));
        for (int[] shape : new int[][] {{3, 6, 2}, {5, 18, 2}, {4, 100, 100}}) {
            out.append(String.format(en, "| %d | %d | %.0f | %.0f |\n", shape[0], shape[1], shape[1] * swapRoll + shape[2] * scrubMs,
                    shape[1] * poolRoll + shape[2] * scrubMs));
        }
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        Config config = Config.defaults();
        for (int i = 0; i < args.length; i += 2) {
            String value = i + 1 < args.length ? args[i + 1] : "";
            config = switch (args[i]) {
                case "--set" -> new Config(value, config.seeds(), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), config.warmup(), config.fresh());
                case "--seeds" -> new Config(config.set(), Integer.parseInt(value), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), config.warmup(), config.fresh());
                case "--per-seed" -> new Config(config.set(), config.seeds(), Integer.parseInt(value), config.samples(), config.gap(), config.waitLimit(), config.warmup(), config.fresh());
                case "--samples" -> new Config(config.set(), config.seeds(), config.perSeed(), Integer.parseInt(value), config.gap(), config.waitLimit(), config.warmup(), config.fresh());
                case "--gap" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), Integer.parseInt(value), config.waitLimit(), config.warmup(), config.fresh());
                case "--wait-limit" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), config.gap(), Integer.parseInt(value), config.warmup(), config.fresh());
                case "--warmup" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), Integer.parseInt(value), config.fresh());
                case "--fresh" -> new Config(config.set(), config.seeds(), config.perSeed(), config.samples(), config.gap(), config.waitLimit(), config.warmup(), Integer.parseInt(value));
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            };
        }
        Report report = run(config);
        System.out.println(markdown(report, commit(), "./gradlew :harness:isolationSpeed -Pisolation.args=\"" + config.command() + "\""));
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
