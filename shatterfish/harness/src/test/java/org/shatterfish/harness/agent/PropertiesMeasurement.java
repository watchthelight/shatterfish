package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.observer.Observer;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
 * The properties measurement (story 6.5, ADR-0010's second measurement): Long et al.'s leaf correlation and disambiguation
 * factor for SPD's tactics, by random playouts from mid-fight snapshots, driven by the random agent, by the baseline Brain
 * and by the one-ply model, so that survival is uncertain and the agreement of siblings means something.
 *
 * <p><b>The snapshots</b> are story 6.4's: the real Brain plays each seed of the {@code smoke} set from its start (salt
 * 7000 plus the seed's index there, the same here), and at every wait with an enemy drawn and at least {@code gap} waits
 * after the last snapshot, up to {@code perSeed} of them, the Run is stored. The seeds, salts, "enemy drawn" rule
 * ({@link SimulatorSpeed#fightable}) and gap are 6.4's, and its code draws them. 6.4 takes two per seed and this takes more,
 * since an interval wants snapshots. One difference: 6.4 skips a wait the scrub refuses (a boss floor, a chest in view), and
 * a perfect-information playout is not scrubbed, so this takes such a wait too; only a wait with a window in front is skipped.
 *
 * <p><b>The playouts.</b> From each snapshot, up to {@code siblings} distinct Actions of the valid set (never a descent) are
 * each played, and then the driver plays on, for {@code 20} waits. The same siblings are played for each driver, so the
 * drivers are paired. A playout is a perfect-information rollout on the live snapshot (story 1.20): the method of story 1.21
 * and not a Simulator. A Brain driver starts from the Belief the live Brain held at the snapshot, so it is not cold. A driver
 * that chooses a descent, and a scene switch (a chasm), end the playout with the hero alive on the floor's last wait;
 * a playout that cannot continue (a refusal that stays, no Decision) is dropped and counted.
 *
 * <p><b>The oracle.</b> The hidden facts are counted through {@link Launcher#hiddenFacts}, the launcher's door, which
 * refuses unless the launch carries {@code --oracle}; the measurement itself holds no sidecar type and no driver sees a fact
 * (a driver is handed an Observation and nothing else). The command line of this class requires {@code --oracle} and the
 * page prints it.
 *
 * <p>Run it with {@code ./gradlew :harness:propertiesMeasurement}; {@code -Pproperties.args="--seeds 25 --per-seed 6"} overrides the
 * defaults.
 */
final class PropertiesMeasurement {

    /** The driving agents, in the order the page lists them. */
    static final List<String> DRIVERS = List.of("random agent", "baseline Brain", "one-ply model");

    record Config(String set, int seeds, int perSeed, int siblings, int gap, int waitLimit, int resamples) {
        Config {
            // An allowlist at the door, as the Rig's: goo and holdout are never reachable, whatever the spelling.
            if (!set.equals("smoke") && !set.equals("standard")) {
                throw new IllegalArgumentException("the measurement runs on the smoke or standard set, not \"" + set + "\"");
            }
            if (seeds < 1 || perSeed < 1 || siblings < 2 || gap < 0 || waitLimit < 1 || resamples < 1) {
                throw new IllegalArgumentException("a measurement needs a seed, a snapshot, a pair of siblings and a resample: " + this);
            }
        }

        static Config defaults() {
            return new Config("smoke", 25, 6, 4, 40, 3_000, 2_000);
        }

        String command() {
            return "--oracle --set " + set + " --seeds " + seeds + " --per-seed " + perSeed + " --siblings " + siblings
                    + " --gap " + gap + " --wait-limit " + waitLimit + " --resamples " + resamples;
        }
    }

    record Report(Config config, List<Properties.Row> rows, int snapshots, List<String> skipped) {
    }

    private final Config config;
    private final Launcher.Launch launch;
    private final PrintStream progress;
    private final Codex.Knowledge codex;
    private final Weights weights;
    private final Map<String, List<Properties.Sample>> samples = new LinkedHashMap<>();
    private final Map<String, Integer> playouts = new LinkedHashMap<>();
    private final Map<String, Integer> dropped = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> droppedWhy = new LinkedHashMap<>();
    private final List<String> skipped = new ArrayList<>();
    private int snapshots;

    private PropertiesMeasurement(Config config, Launcher.Launch launch, PrintStream progress) {
        if (!launch.oracle()) {
            throw new IllegalArgumentException("the properties measurement counts hidden facts through the oracle and runs only with --oracle");
        }
        this.config = config;
        this.launch = launch;
        this.progress = progress;
        var root = RealBrain.root();
        String tag = HeadlessBoot.pinnedTag();
        this.codex = CodexKnowledge.read(root.resolve("codex").resolve(tag), tag);
        this.weights = RealBrain.weights(root);
        for (String driver : DRIVERS) {
            samples.put(driver, new ArrayList<>());
            playouts.put(driver, 0);
            dropped.put(driver, 0);
            droppedWhy.put(driver, new TreeMap<>());
        }
    }

    static Report run(Config config, Launcher.Launch launch, PrintStream progress) throws IOException {
        return new PropertiesMeasurement(config, launch, progress).measure();
    }

    // --- the snapshots, drawn as story 6.4 draws them

    private Report measure() throws IOException {
        List<SimulatorSpeed.Triple> seeds = SimulatorSpeed.triples(config.set(), config.seeds());
        for (int index = 0; index < seeds.size(); index++) {
            progress.println("Properties: seed " + (index + 1) + " of " + seeds.size() + ", " + snapshots + " snapshots so far");
            playSeed(seeds.get(index), 7_000L + index);
        }
        List<Properties.Row> rows = new ArrayList<>();
        for (String driver : DRIVERS) {
            rows.add(new Properties.Row(driver, samples.get(driver), playouts.get(driver), dropped.get(driver), droppedWhy.get(driver)));
        }
        return new Report(config, rows, snapshots, List.copyOf(skipped));
    }

    private BrainDecider baseline() {
        return new BrainDecider(new Brain(codex, weights, DeciderSeeds.brain(RealBrain.NAME)));
    }

    private BrainDecider onePly() {
        return new BrainDecider(new Brain(codex, weights, DeciderSeeds.brain(RealBrain.NAME), Brain.Model.ONE_PLY));
    }

    private void playSeed(SimulatorSpeed.Triple triple, long salt) {
        HeadlessDriver driver = HeadlessDriver.start(triple.seed(), triple.heroClass(), salt);
        try {
            BrainDecider brain = baseline();
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
                if (halt.window() == null && SimulatorSpeed.fightable(observation) && halt.waitIndex() - lastWait >= config.gap()) {
                    Object mark = brain.mark();
                    if (playFrom(driver, observation, halt.waitIndex(), triple.seed(), mark)) {
                        taken++;
                        lastWait = halt.waitIndex();
                    }
                }
                try {
                    executor.execute(observation, brain.decide(observation));
                } catch (Decider.CannotDecide stuck) {
                    // The Run loop ends a Run on a Brain error; the live Run is over for this measurement too.
                    skipped.add(triple.seed() + " ended (the Brain cannot decide) after " + taken + " snapshots");
                    return;
                }
            }
            if (taken < config.perSeed()) {
                skipped.add(triple.seed() + " gave " + taken + " of " + config.perSeed() + " snapshots in " + config.waitLimit() + " waits");
            }
        } finally {
            driver.close();
        }
    }

    // --- the playouts

    /**
     * Plays every driver's siblings from the wait the driver stands at, then puts the live Run back at that wait.
     * False when the wait offers fewer than two siblings, in which case nothing was recorded.
     */
    private boolean playFrom(HeadlessDriver driver, Observation at, long wait, long seed, Object brainMark) {
        List<Action> siblings = siblings(at, config.siblings(), new Random(seed * 1_000_003L + wait));
        if (siblings.size() < 2) {
            return false;
        }
        SnapshotStore store = new SnapshotStore();
        SnapshotHandle snapshot = store.take(driver);
        Set<String> hidden = Launcher.hiddenFacts(launch);
        snapshots++;
        try {
            for (String name : DRIVERS) {
                List<Properties.Leaf> leaves = new ArrayList<>();
                for (int s = 0; s < siblings.size(); s++) {
                    playouts.merge(name, 1, Integer::sum);
                    Decider decider = decider(name, brainMark, seed * 31 + wait * 7 + s);
                    String why = null;
                    Properties.Leaf leaf = null;
                    try {
                        leaf = playout(driver, store, snapshot, at, siblings.get(s), decider, hidden);
                    } catch (Dropped d) {
                        why = d.getMessage();
                    } catch (Decider.CannotDecide stuck) {
                        // The Run loop ends a Run on this as a Brain error; a playout that reaches one has no further waits.
                        why = "driver cannot decide";
                    } catch (HeadlessDriver.Stalled stalled) {
                        // An Action that leaves the game with no wait to serve (a bag window it opened, say): the playout
                        // cannot be read to an end. The message names cells and health, so it is never kept.
                        why = "stalled";
                    }
                    if (leaf == null) {
                        dropped.merge(name, 1, Integer::sum);
                        droppedWhy.get(name).merge(why, 1, Integer::sum);
                    } else {
                        leaves.add(leaf);
                    }
                }
                samples.get(name).add(new Properties.Sample(seed, wait, name, count(hidden, true), hidden.size(), kinds(hidden), leaves));
            }
        } finally {
            // The live Run goes back to the snapshot's wait, where the caller decides for it as if nothing had been played.
            store.restore(snapshot, driver);
            HeadlessDriver.Halt back = driver.stepToInputWait();
            Observation restored = back.reason() == HeadlessDriver.Reason.INPUT_WAIT ? new Observer().observe() : null;
            store.drop(snapshot);
            if (restored == null || !restored.hash().equals(at.hash())) {
                throw new IllegalStateException("the live Run did not replay its wait after the playouts, seed " + seed + " wait " + wait);
            }
        }
        return true;
    }

    private Decider decider(String name, Object brainMark, long seed) {
        if (name.equals(DRIVERS.get(0))) {
            return new org.shatterfish.harness.agent.RandomAgent(seed);
        }
        BrainDecider d = name.equals(DRIVERS.get(1)) ? baseline() : onePly();
        d.rewind(brainMark);
        return d;
    }

    /** A playout that cannot be read to an end; the message is the reason the page counts it under. */
    private static final class Dropped extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Dropped(String why) {
            super(why, null, false, false);
        }
    }

    private Properties.Leaf playout(HeadlessDriver driver, SnapshotStore store, SnapshotHandle snapshot, Observation at,
                                    Action sibling, Decider decider, Set<String> hidden) {
        store.restore(snapshot, driver);
        HeadlessDriver.Halt back = driver.stepToInputWait();
        Observation restored = back.reason() == HeadlessDriver.Reason.INPUT_WAIT ? new Observer().observe() : null;
        if (restored == null || !restored.hash().equals(at.hash())) {
            throw new IllegalStateException("the restore did not replay the wait");
        }
        ActionExecutor executor = new ActionExecutor();
        if (!(executor.execute(restored, sibling) instanceof Outcome.Applied)) {
            throw new Dropped("sibling refused");
        }
        int[] horizons = Properties.HORIZONS;
        int last = horizons[horizons.length - 1];
        Set<String> middle = null;
        boolean soundMiddle = false;
        for (int step = 1; step <= last; step++) {
            HeadlessDriver.Halt halt = driver.stepToInputWait();
            if (halt.reason() == HeadlessDriver.Reason.HERO_DEAD) {
                return leaf(Properties.End.DEATH, step, hidden, middle, Launcher.hiddenFacts(launch), soundMiddle, false);
            }
            if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                return leaf(Properties.End.LEFT, step, hidden, middle, Launcher.hiddenFacts(launch), soundMiddle, sound());
            }
            if (step == horizons[0]) {
                middle = Launcher.hiddenFacts(launch);
                soundMiddle = sound();
            }
            if (step == last) {
                return leaf(Properties.End.HORIZON, step, hidden, middle, Launcher.hiddenFacts(launch), soundMiddle, sound());
            }
            Observation now = new Observer().observe();
            Action chosen = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                chosen = decider.decide(now);
                if (chosen == null) {
                    throw new Dropped("no Decision");
                }
                if (chosen instanceof Action.Descend) {
                    return leaf(Properties.End.LEFT, step, hidden, middle, Launcher.hiddenFacts(launch), soundMiddle, sound());
                }
                if (executor.execute(now, chosen) instanceof Outcome.Applied) {
                    break;
                }
                chosen = null;
            }
            if (chosen == null) {
                throw new Dropped("refusals that stay");
            }
        }
        throw new Dropped("fell out of the loop");
    }

    /** Whether the hero stands with at least half its health: read from the game for the second payoff, never handed to a driver. */
    private static boolean sound() {
        return Dungeon.hero != null && Dungeon.hero.isAlive() && Dungeon.hero.HP * 2 >= Dungeon.hero.HT;
    }

    /**
     * The leaf for a playout that ended after {@code step} waits with {@code end}. {@code middle} is the hidden set at the
     * first horizon if the playout got there, and {@code finish} the set at its end; a horizon the playout ended before
     * reads the end, and its payoff is dead only if the hero died by then.
     */
    private static Properties.Leaf leaf(Properties.End end, int step, Set<String> hidden, Set<String> middle, Set<String> finish,
                                        boolean soundMiddle, boolean soundFinish) {
        int[] horizons = Properties.HORIZONS;
        Properties.At[] at = new Properties.At[horizons.length];
        for (int i = 0; i < horizons.length; i++) {
            boolean midway = step > horizons[i] && middle != null && i < horizons.length - 1;
            Set<String> read = midway ? middle : finish;
            boolean alive = !(end == Properties.End.DEATH && step <= horizons[i]);
            at[i] = new Properties.At(alive, alive && (midway ? soundMiddle : soundFinish), revealed(hidden, read, true),
                    revealed(hidden, read, false));
        }
        return new Properties.Leaf(end, step, at);
    }

    /** How many of the snapshot's facts are not in {@code now}; of the floor's reachable ones, or of all. */
    static int revealed(Set<String> hidden, Set<String> now, boolean floorOnly) {
        int n = 0;
        for (String fact : hidden) {
            if ((!floorOnly || isFloor(fact)) && !now.contains(fact)) {
                n++;
            }
        }
        return n;
    }

    /** A fact reachable on the floor: everything but the Run's unknown appearances that no item of the floor shows. */
    static boolean isFloor(String fact) {
        return !fact.startsWith("elsewhere ");
    }

    private static int count(Set<String> hidden, boolean floorOnly) {
        int n = 0;
        for (String fact : hidden) {
            n += !floorOnly || isFloor(fact) ? 1 : 0;
        }
        return n;
    }

    /** Facts by kind, the kind being the word before the first space; the floor's identities and the Run's others are apart. */
    static Map<String, Integer> kinds(Set<String> facts) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String fact : facts) {
            counts.merge(fact.substring(0, fact.indexOf(' ')), 1, Integer::sum);
        }
        return counts;
    }

    /** Up to {@code count} distinct Actions of the valid set, shuffled by {@code random}, never a descent. */
    static List<Action> siblings(Observation observation, int count, Random random) {
        List<Action> offered = new ArrayList<>(observation.actions().actions());
        offered.removeIf(a -> a instanceof Action.Descend);
        Collections.shuffle(offered, random);
        return offered.subList(0, Math.min(count, offered.size()));
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
        out.append("- ORACLE: the hidden facts below were counted through the oracle sidecar from the launcher's `--oracle` door"
                + " (`Launcher.hiddenFacts`); no driver was handed one.\n");
        out.append(String.format(en, "- Configuration: set `%s`, %d seeds, up to %d mid-fight snapshots each at least %d waits apart,"
                        + " up to %d sibling Actions per snapshot, horizons %d and %d waits, %d bootstrap resamples\n",
                r.config().set(), r.config().seeds(), r.config().perSeed(), r.config().gap(), r.config().siblings(),
                Properties.HORIZONS[0], Properties.HORIZONS[1], r.config().resamples()));
        out.append(String.format(en, "- Measured: %d snapshots, each played by %d drivers\n", r.snapshots(), DRIVERS.size()));
        for (String s : r.skipped()) {
            out.append("- Note: seed ").append(s).append('\n');
        }
        out.append('\n').append(Properties.tables(r.rows(), r.config().resamples(), 0x5EED_6005L));
        return out.toString();
    }

    // --- the command

    public static void main(String[] args) throws IOException {
        Config config = Config.defaults();
        boolean oracle = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--oracle")) {
                oracle = true;
            } else {
                String value = i + 1 < args.length ? args[i + 1] : "";
                config = withOption(config, args[i], value);
                i++;
            }
        }
        if (!oracle) {
            throw new IllegalArgumentException("the properties measurement counts hidden facts through the oracle and runs only with --oracle");
        }
        Launcher.Launch launch = Launcher.Launch.parse(new String[] {"--oracle"});
        HeadlessDriver.boot();
        Report report = run(config, launch, System.err);
        System.out.println(markdown(report, commit(), "./gradlew :harness:propertiesMeasurement -Pproperties.args=\"" + config.command() + "\""));
    }

    private static Config withOption(Config c, String option, String value) {
        return switch (option) {
            case "--set" -> new Config(value, c.seeds(), c.perSeed(), c.siblings(), c.gap(), c.waitLimit(), c.resamples());
            case "--seeds" -> new Config(c.set(), Integer.parseInt(value), c.perSeed(), c.siblings(), c.gap(), c.waitLimit(), c.resamples());
            case "--per-seed" -> new Config(c.set(), c.seeds(), Integer.parseInt(value), c.siblings(), c.gap(), c.waitLimit(), c.resamples());
            case "--siblings" -> new Config(c.set(), c.seeds(), c.perSeed(), Integer.parseInt(value), c.gap(), c.waitLimit(), c.resamples());
            case "--gap" -> new Config(c.set(), c.seeds(), c.perSeed(), c.siblings(), Integer.parseInt(value), c.waitLimit(), c.resamples());
            case "--wait-limit" -> new Config(c.set(), c.seeds(), c.perSeed(), c.siblings(), c.gap(), Integer.parseInt(value), c.resamples());
            case "--resamples" -> new Config(c.set(), c.seeds(), c.perSeed(), c.siblings(), c.gap(), c.waitLimit(), Integer.parseInt(value));
            default -> throw new IllegalArgumentException("unknown option " + option);
        };
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
