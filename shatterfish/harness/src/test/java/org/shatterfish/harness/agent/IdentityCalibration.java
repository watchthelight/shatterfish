package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefView;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The calibration of the Brain's identity odds (issue #410): how close the probabilities in its {@link BeliefView}
 * are to the identities the Run really has, as a Brier score. A test-source tool, run by
 * {@code ./gradlew :harness:identityCalibration}, that plays the real Brain on the first seeds of a Seed set and
 * scores it.
 *
 * <p><b>The oracle.</b> The true identities are read through {@link Launcher#identityTruth}, the launcher's door,
 * which refuses without {@code --oracle}. They are read here, once a Run, by the scorer and kept in the scorer:
 * the Brain is handed the same {@link Observation} it is handed in a ranked Run and never sees the truth, and
 * nothing it decides is read back from this class. No ranked Rig Run can call the door
 * ({@code IdentityOracleGateTest}). The tool plays {@code smoke} or {@code standard} only, never {@code goo} or
 * {@code holdout}.
 *
 * <p><b>The score.</b> One sample is an appearance on a floor: the last view the Brain held of it while the hero
 * stood on that floor (so an appearance read to identity on the floor it was first met is scored on what the
 * Brain believed just before). Its Brier score is the sum over the candidates of (probability less one if the
 * candidate is the truth)^2, plus one when the truth is not among the candidates at all. A score of 0 is a
 * certain and right call; naming the truth with probability 1/k among k equal candidates scores (k - 1)/k.
 * The table reports the mean over samples, and the mean of those where the Brain's top call was right.
 */
public final class IdentityCalibration {

    /** One scored appearance on a floor. */
    record Sample(long seed, int depth, String label, String truth, double brier, boolean topRight, double truthProbability,
                  int candidates) {
    }

    private final String set;
    private final int seeds;
    private final int turnCap;
    private final PrintStream progress;
    private final Launcher.Launch launch = Launcher.Launch.parse(new String[] {"--oracle"});
    private final List<Sample> samples = new ArrayList<>();

    IdentityCalibration(String set, int seeds, int turnCap, PrintStream progress) {
        if (!set.equals("smoke") && !set.equals("standard")) {
            throw new IllegalArgumentException("the calibration plays the smoke and standard sets, not '" + set + "'");
        }
        this.set = set;
        this.seeds = seeds;
        this.turnCap = turnCap;
        this.progress = progress;
    }

    /** Wraps the Brain: delegates, then reads the view it left and scores it against the truth. */
    private final class Scoring implements Decider {

        private final BrainDecider inner;
        private final long seed;
        private Map<String, String> truth;
        /** The last scored view of each appearance on each floor, keyed {@code depth|label}. */
        private final Map<String, Sample> last = new LinkedHashMap<>();

        Scoring(BrainDecider inner, long seed) {
            this.inner = inner;
            this.seed = seed;
        }

        @Override
        public Action decide(Observation observation) {
            Action action = inner.decide(observation);
            if (truth == null) {
                truth = Launcher.identityTruth(launch);
            }
            BeliefView view = inner.beliefView();
            if (view != null) {
                for (BeliefView.Appearance appearance : view.appearances()) {
                    Sample one = score(observation.header().depth(), appearance);
                    if (one != null) {
                        last.put(one.depth() + "|" + one.label(), one);
                    }
                }
            }
            return action;
        }

        private Sample score(int depth, BeliefView.Appearance appearance) {
            String real = null;
            for (Map.Entry<String, String> entry : truth.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(" " + appearance.label().toLowerCase(Locale.ROOT))) {
                    real = entry.getValue();
                }
            }
            if (real == null) {
                return null;
            }
            double brier = 0;
            double onTruth = 0;
            boolean found = false;
            for (BeliefView.Candidate candidate : appearance.candidates()) {
                boolean right = candidate.name().equalsIgnoreCase(real);
                found |= right;
                if (right) {
                    onTruth = candidate.probability();
                }
                double miss = candidate.probability() - (right ? 1 : 0);
                brier += miss * miss;
            }
            if (!found) {
                brier += 1;
            }
            boolean top = !appearance.candidates().isEmpty() && appearance.candidates().get(0).name().equalsIgnoreCase(real);
            return new Sample(seed, depth, appearance.label(), real, brier, top, onTruth, appearance.candidates().size());
        }
    }

    Report run() throws IOException {
        List<SimulatorSpeed.Triple> triples = SimulatorSpeed.triples(set, seeds);
        for (int index = 0; index < triples.size(); index++) {
            SimulatorSpeed.Triple triple = triples.get(index);
            progress.println("IdentityCalibration: seed " + (index + 1) + " of " + triples.size() + ", " + samples.size() + " samples");
            Scoring scoring = new Scoring(RealBrain.decider(), triple.seed());
            new RunLoop().play(triple.seed(), triple.heroClass(), 7_000L + index, scoring, turnCap);
            samples.addAll(scoring.last.values());
        }
        return new Report(set, triples.size(), List.copyOf(samples));
    }

    record Report(String set, int seeds, List<Sample> samples) {

        double mean() {
            return samples.stream().mapToDouble(Sample::brier).average().orElse(Double.NaN);
        }

        String markdown(String commit) {
            StringBuilder out = new StringBuilder();
            out.append("Identity calibration, set `").append(set).append("`, first ").append(seeds).append(" seeds, commit `")
                    .append(commit).append("`.\n\n");
            out.append("| Family | Samples | Brier | Top call right | Mean P(truth) |\n|---|---:|---:|---:|---:|\n");
            Map<String, List<Sample>> byFamily = new TreeMap<>();
            for (Sample sample : samples) {
                byFamily.computeIfAbsent(sample.label().startsWith("scroll") ? "scroll" : "potion", k -> new ArrayList<>()).add(sample);
            }
            byFamily.forEach((family, list) -> row(out, family, list));
            row(out, "**all**", samples);
            Map<String, List<Sample>> byTruth = new TreeMap<>();
            for (Sample sample : samples) {
                byTruth.computeIfAbsent(sample.truth().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(sample);
            }
            out.append("\n| True identity | Samples | Brier | Mean P(truth) |\n|---|---:|---:|---:|\n");
            byTruth.forEach((truth, list) -> out.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f |%n", truth, list.size(),
                    list.stream().mapToDouble(Sample::brier).average().orElse(0),
                    list.stream().mapToDouble(Sample::truthProbability).average().orElse(0))));
            return out.toString();
        }

        private static void row(StringBuilder out, String name, List<Sample> list) {
            out.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.1f%% | %.3f |%n", name, list.size(),
                    list.stream().mapToDouble(Sample::brier).average().orElse(Double.NaN),
                    100.0 * list.stream().filter(Sample::topRight).count() / Math.max(1, list.size()),
                    list.stream().mapToDouble(Sample::truthProbability).average().orElse(Double.NaN)));
        }
    }

    public static void main(String[] args) throws IOException {
        String set = "standard";
        int seeds = 20;
        int turnCap = 20_000;
        boolean oracle = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--oracle" -> oracle = true;
                case "--set" -> set = args[++i];
                case "--seeds" -> seeds = Integer.parseInt(args[++i]);
                case "--turn-cap" -> turnCap = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (!oracle) {
            throw new IllegalArgumentException("the identity calibration reads the true identities through the oracle and runs only with --oracle");
        }
        HeadlessBoot.ensure();
        Report report = new IdentityCalibration(set, seeds, turnCap, System.err).run();
        System.out.println(report.markdown(commit()));
    }

    private static String commit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            return p.waitFor() == 0 ? text : "unknown";
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
