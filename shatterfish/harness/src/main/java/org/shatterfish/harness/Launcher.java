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
 * {@code OracleGateTest} holds, so the flag is a property of a launch and never of a Run, a Brain
 * or the measured loop; it is also why the benchmark's tactics half, which counts the oracle's
 * hidden facts, is nested here.
 */
public final class Launcher {

    /** What a command line asked for. */
    public record Launch(long seed, boolean oracle, boolean benchmark, int runs, int samples, int siblings, int horizon) {

        /** The seed a launch plays when none is given. */
        public static final long DEFAULT_SEED = 31_415_926L;

        /** A plain launch of {@code seed}. */
        public Launch(long seed, boolean oracle) {
            this(seed, oracle, false, 200, 24, 4, 20);
        }

        /**
         * Parses one seed, {@code --oracle}, {@code --benchmark} and its {@code --runs N},
         * {@code --samples N}, {@code --siblings N} and {@code --horizon N}, in any order. A second
         * seed, a seed outside the game's range ({@code …/utils/DungeonSeed.java},
         * {@code TOTAL_SEEDS}), a count that is not a positive number and anything else are
         * refused by name.
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
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--oracle")) {
                    oracle = true;
                } else if (arg.equals("--benchmark")) {
                    benchmark = true;
                } else if (arg.equals("--runs") || arg.equals("--samples") || arg.equals("--siblings") || arg.equals("--horizon")) {
                    if (i + 1 >= args.length || !args[i + 1].matches("[0-9]+") || Integer.parseInt(args[i + 1]) < 1) {
                        throw new IllegalArgumentException(arg + " takes a positive count");
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
            return new Launch(seed, oracle, benchmark, runs, samples, siblings, horizon);
        }
    }

    private Launcher() {
    }

    public static void main(String[] args) {
        Launch launch = Launch.parse(args);
        HeadlessDriver.Boot boot = HeadlessDriver.boot();
        System.out.println("Launcher: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        try {
            if (launch.benchmark()) {
                System.out.println(Benchmark.run(launch, System.out).markdown());
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
     */
    public static final class Benchmark {

        /** The numbers, and the Markdown a Results page carries them in. */
        public record Report(Launch launch, long tag, int runs, double seconds, long waits, long turns, double waitsPerSecond,
                             double runsPerMinute, long medianTurns, long medianWaits, Map<String, Integer> causes,
                             Map<Integer, Integer> depths, int costWaits, double observeMicros, double codecMicros,
                             double writerMicros, double codecBytes, double writerBytes, int samplesAsked, int samplesTaken,
                             int samplesSkipped, double leafCorrelation, double survival, double disambiguation,
                             double hiddenAtSample, double mobsFirstSeenPerWait, List<String> samples) {

            /** The report as a person and a Results page read it. */
            public String markdown() {
                StringBuilder md = new StringBuilder();
                md.append("## Throughput\n\n");
                md.append(String.format(Locale.ROOT, "- Runs: %d, in %.1f s of wall time, one process.%n", runs, seconds));
                md.append(String.format(Locale.ROOT, "- Input waits: %d (%.1f per second per process).%n", waits, waitsPerSecond));
                md.append(String.format(Locale.ROOT, "- Runs per minute: %.1f.%n", runsPerMinute));
                md.append(String.format(Locale.ROOT, "- Median Run length: %d turns, %d Input waits; %d turns in all.%n", medianTurns,
                        medianWaits, turns));
                md.append("- Causes: ").append(causes).append(".\n");
                md.append("- Deepest floor reached, by Runs: ").append(depths).append(".\n\n");
                md.append("## Costs per Observation\n\n");
                md.append(String.format(Locale.ROOT, "Over %d waits of one Run:%n%n", costWaits));
                md.append(String.format(Locale.ROOT, "| What | Microseconds | Bytes |%n|---|---|---|%n"));
                md.append(String.format(Locale.ROOT, "| `Observer.observe()` | %.1f | |%n", observeMicros));
                md.append(String.format(Locale.ROOT, "| codec: `ObservationCodec.encode` and `hash()` | %.1f | %.0f |%n", codecMicros, codecBytes));
                md.append(String.format(Locale.ROOT, "| JSON writer: `Observation.json()` | %.1f | %.0f |%n%n", writerMicros, writerBytes));
                md.append("## Tactics\n\n");
                md.append(String.format(Locale.ROOT, "- Samples: %d asked, %d taken, %d skipped under a window; %d siblings, horizon %d waits.%n",
                        samplesAsked, samplesTaken, samplesSkipped, launch.siblings(), launch.horizon()));
                md.append(String.format(Locale.ROOT, "- Leaf correlation: %.3f (mean pairwise agreement of siblings' payoffs).%n", leafCorrelation));
                md.append(String.format(Locale.ROOT, "- Survival at the horizon: %.3f of playouts.%n", survival));
                md.append(String.format(Locale.ROOT, "- Disambiguation factor: %.3f (mean share of a sample's hidden facts revealed by the horizon; %.1f hidden facts per sample).%n",
                        disambiguation, hiddenAtSample));
                md.append(String.format(Locale.ROOT, "- Mobs first drawn per wait during the playouts: %.3f.%n", mobsFirstSeenPerWait));
                md.append("- Bias: not measured; a two-player quantity.\n\n");
                md.append("## Samples\n\n");
                for (String sample : samples) {
                    md.append("- ").append(sample).append('\n');
                }
                return md.toString();
            }
        }

        private Benchmark() {
        }

        /** Runs the whole benchmark for {@code launch}, printing progress to {@code out}. */
        public static Report run(Launch launch, PrintStream out) {
            // --- throughput: the random agent to the end of each Run, one process
            RunLoop loop = new RunLoop();
            List<Long> turnsPerRun = new ArrayList<>();
            List<Long> waitsPerRun = new ArrayList<>();
            Map<String, Integer> causes = new TreeMap<>();
            Map<Integer, Integer> depths = new TreeMap<>();
            long start = System.nanoTime();
            for (int i = 0; i < launch.runs(); i++) {
                long seed = launch.seed() + i;
                RunOutcome outcome = loop.play(seed, HeroClass.WARRIOR, saltFor(seed), new RandomAgent(seed), RunLoop.TURN_CAP);
                turnsPerRun.add((long) outcome.turns());
                waitsPerRun.add(outcome.waits());
                causes.merge(outcome.cause().name(), 1, Integer::sum);
                depths.merge(outcome.depth(), 1, Integer::sum);
                if ((i + 1) % 50 == 0) {
                    out.println("Benchmark: " + (i + 1) + " of " + launch.runs() + " Runs");
                }
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            long waits = waitsPerRun.stream().mapToLong(Long::longValue).sum();
            long turns = turnsPerRun.stream().mapToLong(Long::longValue).sum();

            // --- costs: one Run, each read timed three ways
            long seed = launch.seed() + launch.runs();
            int costWaits = 0;
            long observeNanos = 0;
            long codecNanos = 0;
            long writerNanos = 0;
            long codecBytes = 0;
            long writerBytes = 0;
            try (HeadlessDriver driver = HeadlessDriver.start(seed, HeroClass.WARRIOR, saltFor(seed))) {
                RandomAgent agent = new RandomAgent(seed);
                ActionExecutor executor = new ActionExecutor();
                for (int i = 0; i < 400; i++) {
                    HeadlessDriver.Halt halt = driver.stepToInputWait();
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
                    if (chosen == null) {
                        break;
                    }
                    executor.execute(observation, chosen);
                }
            }

            // --- tactics: snapshots along random Runs, siblings played out to a horizon
            int taken = 0;
            int skipped = 0;
            double correlationSum = 0;
            int correlated = 0;
            long payoffs = 0;
            long playouts = 0;
            double disambiguationSum = 0;
            int disambiguated = 0;
            long hiddenSum = 0;
            long firstSeen = 0;
            long playoutWaits = 0;
            List<String> samples = new ArrayList<>();
            for (int s = 0; s < launch.samples(); s++) {
                long sampleSeed = launch.seed() + launch.runs() + 1 + s;
                int approach = 6 + (s * 7) % 30;
                try (HeadlessDriver driver = HeadlessDriver.start(sampleSeed, HeroClass.WARRIOR, saltFor(sampleSeed))) {
                    RandomAgent agent = new RandomAgent(sampleSeed);
                    ActionExecutor executor = new ActionExecutor();
                    HeadlessDriver.Halt halt = null;
                    Observation at = null;
                    for (int i = 0; i < approach; i++) {
                        halt = driver.stepToInputWait();
                        if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                            break;
                        }
                        at = new Observer().observe();
                        if (i + 1 < approach) {
                            Action chosen = staying(agent, at);
                            if (chosen == null) {
                                halt = null;
                                break;
                            }
                            executor.execute(at, chosen);
                        }
                    }
                    if (halt == null || halt.reason() != HeadlessDriver.Reason.INPUT_WAIT || halt.window() != null || at == null) {
                        skipped++;
                        samples.add("seed " + sampleSeed + " wait " + approach + ": skipped (" + (halt == null ? "no wait" : halt.window() != null ? "a window in front" : halt.reason()) + ")");
                        continue;
                    }
                    SnapshotStore store = new SnapshotStore();
                    SnapshotHandle snapshot = store.take(driver);
                    Set<String> hidden = hiddenFacts(new OracleObserver().observe().view());
                    hiddenSum += hidden.size();
                    taken++;
                    List<Action> siblings = siblings(at, launch.siblings(), new Random(sampleSeed));
                    List<Integer> results = new ArrayList<>();
                    List<Double> revealed = new ArrayList<>();
                    for (Action sibling : siblings) {
                        store.restore(snapshot, driver);
                        HeadlessDriver.Halt back = driver.stepToInputWait();
                        if (back.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                            continue;
                        }
                        Observation restored = new Observer().observe();
                        if (!(executor.execute(restored, sibling) instanceof Outcome.Applied)) {
                            continue;
                        }
                        Set<Integer> drawn = drawnMobs(restored);
                        int alive = 1;
                        int stepped = 0;
                        for (int h = 0; h < launch.horizon(); h++) {
                            HeadlessDriver.Halt step = driver.stepToInputWait();
                            if (step.reason() == HeadlessDriver.Reason.HERO_DEAD) {
                                alive = 0;
                                break;
                            }
                            if (step.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                                break;
                            }
                            stepped++;
                            Observation now = new Observer().observe();
                            for (int cell : drawnMobs(now)) {
                                if (drawn.add(cell)) {
                                    firstSeen++;
                                }
                            }
                            if (h + 1 < launch.horizon()) {
                                Action chosen = staying(agent, now);
                                if (chosen == null) {
                                    break;
                                }
                                executor.execute(now, chosen);
                            }
                        }
                        playoutWaits += stepped;
                        results.add(alive);
                        payoffs += alive;
                        playouts++;
                        if (alive == 1 && !hidden.isEmpty()) {
                            Set<String> still = hiddenFacts(new OracleObserver().observe().view());
                            still.retainAll(hidden);
                            revealed.add(1.0 - (double) still.size() / hidden.size());
                        }
                    }
                    if (results.size() >= 2) {
                        int agree = 0;
                        int pairs = 0;
                        for (int a = 0; a < results.size(); a++) {
                            for (int b = a + 1; b < results.size(); b++) {
                                pairs++;
                                agree += results.get(a).equals(results.get(b)) ? 1 : 0;
                            }
                        }
                        correlationSum += (double) agree / pairs;
                        correlated++;
                    }
                    if (!revealed.isEmpty()) {
                        disambiguationSum += revealed.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                        disambiguated++;
                    }
                    samples.add("seed " + sampleSeed + " wait " + approach + ": " + results.size() + " siblings, payoffs " + results
                            + ", hidden facts " + hidden.size() + ", revealed " + (revealed.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.2f", revealed.stream().mapToDouble(Double::doubleValue).average().orElse(0))));
                }
                out.println("Benchmark: sample " + (s + 1) + " of " + launch.samples());
            }
            return new Report(launch, 0, launch.runs(), seconds, waits, turns, waits / seconds, launch.runs() / (seconds / 60.0),
                    median(turnsPerRun), median(waitsPerRun), causes, depths, costWaits,
                    costWaits == 0 ? 0 : observeNanos / 1e3 / costWaits, costWaits == 0 ? 0 : codecNanos / 1e3 / costWaits,
                    costWaits == 0 ? 0 : writerNanos / 1e3 / costWaits, costWaits == 0 ? 0 : (double) codecBytes / costWaits,
                    costWaits == 0 ? 0 : (double) writerBytes / costWaits, launch.samples(), taken, skipped,
                    correlated == 0 ? Double.NaN : correlationSum / correlated, playouts == 0 ? Double.NaN : (double) payoffs / playouts,
                    disambiguated == 0 ? Double.NaN : disambiguationSum / disambiguated, taken == 0 ? 0 : (double) hiddenSum / taken,
                    playoutWaits == 0 ? 0 : (double) firstSeen / playoutWaits, samples);
        }

        /** The salt a benchmark Run of {@code seed} declares: a function of the seed, so the tuple is published by the command. */
        static long saltFor(long seed) {
            return seed * 31L + 7L;
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
        private static List<Action> siblings(Observation observation, int count, Random random) {
            List<Action> offered = new ArrayList<>(observation.actions().actions());
            offered.removeIf(a -> a instanceof Action.Descend);
            Collections.shuffle(offered, random);
            return offered.subList(0, Math.min(count, offered.size()));
        }

        /**
         * The static hidden facts of a sidecar, each as one key: an unknown appearance, a secret
         * door's cell, a hidden trap's cell, a hidden mimic's cell. Mobs are not among them, since a
         * mob's position is hidden and shown again as it moves; they are counted as first drawn.
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

        private static Set<Integer> drawnMobs(Observation observation) {
            Set<Integer> cells = new HashSet<>();
            observation.actors().actors().forEach(a -> cells.add(a.cell()));
            return cells;
        }

        private static long median(List<Long> values) {
            if (values.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return sorted.get(sorted.size() / 2);
        }
    }
}
