package org.shatterfish.harness.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The two definitions of story 6.5 (ADR-0010's measurement 2, as its story 1.21 amendment fixes them) and their intervals,
 * as arithmetic over plain values, so that a reader can check them on cases small enough to do by hand
 * ({@code PropertiesDefinitionTest}). Nothing here touches the game.
 *
 * <p>A <em>sample</em> is one mid-fight snapshot played by one driving agent: its hidden facts counted once, at the
 * snapshot, and one <em>leaf</em> per sibling Action, the result of playing that sibling and then the driver on.
 *
 * <ul>
 *   <li><b>Leaf correlation</b> (Long et al.: the probability that sibling terminal nodes share a payoff). The payoff of a
 *       leaf is 1 if the hero is alive when the playout ends at the horizon and 0 if it died by then. A sample's agreement
 *       is the share of its pairs of leaves with the same payoff; the leaf correlation is the mean over the samples that
 *       have a pair. Chance agreement at pooled survival s is s^2 + (1-s)^2 and kappa is (agreement - chance) / (1 - chance),
 *       as story 1.21 published them.</li>
 *   <li><b>Disambiguation factor</b> (Long et al.: how fast the information set shrinks). A leaf's share is the fraction of
 *       the sample's hidden facts that are revealed when the playout ends. The factor is the mean over the samples that
 *       have a hidden fact and a counted leaf of the mean share over the counted leaves. Story 1.21 counted the Run's
 *       whole set of unknown appearances in the denominator and only the leaves alive at the horizon; this story changes
 *       both: the denominator is the facts reachable on the floor of the sample ({@link Basis#FLOOR}) and every leaf counts,
 *       a death at the moment it happens ({@link Counted#ALL}). The old reading is kept as {@link Basis#RUN} with
 *       {@link Counted#SURVIVORS} so that each change can be shown on its own.</li>
 * </ul>
 *
 * <p>Bias is not measured: it is a two-player quantity (ADR-0010).
 */
final class Properties {

    /** The horizons a leaf is read at, in waits after the sibling: the tactical horizon of ADR-0010 and story 1.21's. */
    static final int[] HORIZONS = {4, 20};

    /** How a playout ended. */
    enum End {
        /** It reached its last wait with the hero alive. */
        HORIZON,
        /** The hero died on the way. */
        DEATH,
        /** The hero left the floor alive: the driver chose the stairs, or the game switched scene (a chasm). */
        LEFT
    }

    /**
     * What a leaf says at one horizon: the payoffs and how many of the sample's facts it has revealed by then. {@code sound}
     * is the second payoff, alive with at least half its health left, which is there because a Brain that rarely dies
     * leaves the first payoff with nothing to disagree about.
     */
    record At(boolean alive, boolean sound, int revealedFloor, int revealedRun) {
        At(boolean alive, int revealedFloor, int revealedRun) {
            this(alive, alive, revealedFloor, revealedRun);
        }
    }

    /** Which payoff the leaves are compared on. */
    enum Payoff {
        /** 1 if the hero is alive when the playout ends or at the horizon, 0 if it died by then: the terminal a tactical search scores at. */
        ALIVE,
        /** 1 if the hero is alive with at least half its health, 0 otherwise. */
        SOUND;

        boolean of(At at) {
            return this == ALIVE ? at.alive() : at.sound();
        }
    }

    /** One played sibling. {@code at[i]} reads {@link #HORIZONS}{@code [i]}; a playout that ended earlier says its end. */
    record Leaf(End end, int endedAfter, At[] at) {
        Leaf {
            if (at.length != HORIZONS.length) {
                throw new IllegalArgumentException("a leaf is read at " + HORIZONS.length + " horizons");
            }
        }

        /** A leaf that reads the same at every horizon: the shape the hand-built cases use. */
        static Leaf same(boolean alive, int revealedFloor, int revealedRun) {
            At one = new At(alive, revealedFloor, revealedRun);
            return new Leaf(alive ? End.HORIZON : End.DEATH, HORIZONS[HORIZONS.length - 1], new At[] {one, one});
        }
    }

    /**
     * One snapshot played by one driver: where it was, how many hidden facts it held (reachable on the floor, and the Run's
     * whole set), by kind, and the leaves.
     */
    record Sample(long seed, long waitIndex, String driver, int hiddenFloor, int hiddenRun, Map<String, Integer> hiddenByKind,
                  List<Leaf> leaves) {
        Sample {
            hiddenByKind = Map.copyOf(hiddenByKind);
            leaves = List.copyOf(leaves);
        }
    }

    /** What the denominator of the disambiguation factor is. */
    enum Basis {
        /** The facts reachable on the floor of the sample: story 6.5's. */
        FLOOR,
        /** Every unknown appearance of the Run besides the floor's secrets: story 1.21's. */
        RUN
    }

    /** Which leaves the disambiguation factor averages over. */
    enum Counted {
        /** Every leaf, a death counted at the death: story 6.5's. */
        ALL,
        /** Only the leaves whose hero is alive at the horizon: story 1.21's. */
        SURVIVORS
    }

    private Properties() {
    }

    // --- the definitions, point estimates

    /** The share of pairs of {@code payoffs} that agree; NaN with fewer than two leaves. */
    static double agreement(List<Boolean> payoffs) {
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

    /** Mean over the samples with a pair of the share of leaf pairs that agree on the payoff at horizon {@code hi}. */
    static double leafCorrelation(List<Sample> samples, int hi) {
        return leafCorrelation(samples, hi, Payoff.ALIVE);
    }

    /** {@link #leafCorrelation(List, int)} on a chosen payoff. */
    static double leafCorrelation(List<Sample> samples, int hi, Payoff payoff) {
        double sum = 0;
        int counted = 0;
        for (Sample s : samples) {
            double a = agreement(s.leaves().stream().map(l -> payoff.of(l.at()[hi])).toList());
            if (!Double.isNaN(a)) {
                sum += a;
                counted++;
            }
        }
        return counted == 0 ? Double.NaN : sum / counted;
    }

    /** Alive leaves over all leaves, pooled over the samples; NaN with none. */
    static double survival(List<Sample> samples, int hi) {
        return survival(samples, hi, Payoff.ALIVE);
    }

    /** {@link #survival(List, int)} on a chosen payoff: the share of leaves with payoff 1. */
    static double survival(List<Sample> samples, int hi, Payoff payoff) {
        long alive = 0;
        long all = 0;
        for (Sample s : samples) {
            for (Leaf l : s.leaves()) {
                all++;
                alive += payoff.of(l.at()[hi]) ? 1 : 0;
            }
        }
        return all == 0 ? Double.NaN : (double) alive / all;
    }

    /** Two independent leaves that each survive with probability {@code survival} agree by chance with this probability. */
    static double chance(double survival) {
        return survival * survival + (1 - survival) * (1 - survival);
    }

    /** (agreement - chance) / (1 - chance), NaN when chance is 1 (every leaf has the same payoff, so nothing is left to explain). */
    static double kappa(double leafCorrelation, double survival) {
        double chance = chance(survival);
        return Double.isNaN(leafCorrelation) || Double.isNaN(chance) || chance == 1.0 ? Double.NaN : (leafCorrelation - chance) / (1 - chance);
    }

    /** The kappa of a list of samples at horizon {@code hi}. */
    static double kappa(List<Sample> samples, int hi) {
        return kappa(samples, hi, Payoff.ALIVE);
    }

    /** The kappa of a list of samples at horizon {@code hi} on a chosen payoff. */
    static double kappa(List<Sample> samples, int hi, Payoff payoff) {
        return kappa(leafCorrelation(samples, hi, payoff), survival(samples, hi, payoff));
    }

    /** The disambiguation factor at horizon {@code hi}; NaN with no sample that has a hidden fact and a counted leaf. */
    static double disambiguation(List<Sample> samples, int hi, Basis basis, Counted counted) {
        double sum = 0;
        int n = 0;
        for (Sample s : samples) {
            int denominator = basis == Basis.FLOOR ? s.hiddenFloor() : s.hiddenRun();
            if (denominator == 0) {
                continue;
            }
            double shares = 0;
            int leaves = 0;
            for (Leaf l : s.leaves()) {
                At at = l.at()[hi];
                if (counted == Counted.SURVIVORS && !at.alive()) {
                    continue;
                }
                shares += (double) (basis == Basis.FLOOR ? at.revealedFloor() : at.revealedRun()) / denominator;
                leaves++;
            }
            if (leaves > 0) {
                sum += shares / leaves;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    /** The number of samples that enter {@link #disambiguation}. */
    static int disambiguated(List<Sample> samples, int hi, Basis basis, Counted counted) {
        int n = 0;
        for (Sample s : samples) {
            if ((basis == Basis.FLOOR ? s.hiddenFloor() : s.hiddenRun()) > 0
                    && s.leaves().stream().anyMatch(l -> counted == Counted.ALL || l.at()[hi].alive())) {
                n++;
            }
        }
        return n;
    }

    // --- intervals

    /** A statistic with its 95% percentile interval over {@code resamples} bootstrap draws, {@code valid} of which were defined. */
    record Interval(double point, double low, double high, int valid, int resamples) {
    }

    /** A statistic of a list of samples. */
    @FunctionalInterface
    interface Statistic {
        double of(List<Sample> samples);
    }

    /**
     * The cluster bootstrap: the samples of one seed are one cluster (they come from one Run and are not independent of
     * each other), the clusters are drawn with replacement, and the statistic is recomputed on the drawn samples. The
     * interval is the 2.5th to the 97.5th percentile of the defined resamples. The draws are a function of {@code seed},
     * so the same samples give the same interval.
     */
    static Interval bootstrap(List<Sample> samples, Statistic statistic, int resamples, long seed) {
        double point = statistic.of(samples);
        Map<Long, List<Sample>> clusters = new LinkedHashMap<>();
        for (Sample s : samples) {
            clusters.computeIfAbsent(s.seed(), k -> new ArrayList<>()).add(s);
        }
        List<List<Sample>> groups = new ArrayList<>(clusters.values());
        List<Double> draws = new ArrayList<>();
        Random random = new Random(seed);
        for (int r = 0; r < resamples && !groups.isEmpty(); r++) {
            List<Sample> drawn = new ArrayList<>();
            for (int g = 0; g < groups.size(); g++) {
                drawn.addAll(groups.get(random.nextInt(groups.size())));
            }
            double value = statistic.of(drawn);
            if (!Double.isNaN(value)) {
                draws.add(value);
            }
        }
        if (draws.isEmpty()) {
            return new Interval(point, Double.NaN, Double.NaN, 0, resamples);
        }
        draws.sort(Double::compare);
        return new Interval(point, percentile(draws, 0.025), percentile(draws, 0.975), draws.size(), resamples);
    }

    /** Linear interpolation between the closest ranks of a sorted list. */
    static double percentile(List<Double> sorted, double p) {
        double position = p * (sorted.size() - 1);
        int below = (int) Math.floor(position);
        int above = (int) Math.ceil(position);
        return sorted.get(below) + (position - below) * (sorted.get(above) - sorted.get(below));
    }

    // --- the page

    /** Everything one driving agent measured, as the page prints it. */
    record Row(String driver, List<Sample> samples, int playouts, int dropped, Map<String, Integer> droppedWhy) {
    }

    static String number(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
    }

    static String interval(Interval i) {
        return i.valid() == 0 ? number(i.point()) + " (no interval)"
                : number(i.point()) + " [" + number(i.low()) + ", " + number(i.high()) + "]";
    }

    /** The Markdown tables of a measurement: both properties per driver and horizon, and the two changes shown apart. */
    static String tables(List<Row> rows, int resamples, long seed) {
        StringBuilder md = new StringBuilder();
        md.append("### The two properties, per driving Brain\n\n");
        md.append("Each cell is the point estimate and its 95% interval (cluster bootstrap over seeds, ").append(resamples)
                .append(" resamples).\n\n");
        for (int hi = HORIZONS.length - 1; hi >= 0; hi--) {
            md.append("**Horizon ").append(HORIZONS[hi]).append(" waits.**\n\n");
            md.append("| Driver | Snapshots | Leaves | Survival | Leaf correlation | Chance agreement | Kappa | Disambiguation factor |\n");
            md.append("|---|---|---|---|---|---|---|---|\n");
            for (Row row : rows) {
                final int h = hi;
                List<Sample> s = row.samples();
                long leaves = s.stream().mapToLong(x -> x.leaves().size()).sum();
                Interval survival = bootstrap(s, x -> survival(x, h), resamples, seed);
                Interval leaf = bootstrap(s, x -> leafCorrelation(x, h), resamples, seed + 1);
                Interval kappa = bootstrap(s, x -> kappa(x, h), resamples, seed + 2);
                Interval dis = bootstrap(s, x -> disambiguation(x, h, Basis.FLOOR, Counted.ALL), resamples, seed + 3);
                md.append(String.format(Locale.ROOT, "| %s | %d | %d | %s | %s | %s | %s | %s |%n", row.driver(), s.size(), leaves,
                        interval(survival), interval(leaf), number(chance(survival.point())), interval(kappa), interval(dis)));
            }
            md.append("\nThe same leaves on the second payoff, alive with at least half the hero's health left:\n\n");
            md.append("| Driver | Sound | Leaf correlation | Chance agreement | Kappa |\n|---|---|---|---|---|\n");
            for (Row row : rows) {
                final int h = hi;
                List<Sample> s = row.samples();
                Interval sound = bootstrap(s, x -> survival(x, h, Payoff.SOUND), resamples, seed + 6);
                Interval leaf = bootstrap(s, x -> leafCorrelation(x, h, Payoff.SOUND), resamples, seed + 7);
                Interval kappa = bootstrap(s, x -> kappa(x, h, Payoff.SOUND), resamples, seed + 8);
                md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s |%n", row.driver(), interval(sound), interval(leaf),
                        number(chance(sound.point())), interval(kappa)));
            }
            md.append('\n');
        }
        md.append("### Story 1.21's reading and the two changes, one at a time\n\n");
        md.append("The disambiguation factor at the horizon of ").append(HORIZONS[HORIZONS.length - 1])
                .append(" waits, counted as story 1.21 counted it (the Run's whole set of unknown appearances, surviving leaves"
                        + " only), then with the floor's facts as the denominator, then with deaths counted as well.\n\n");
        md.append("| Driver | Run-wide denominator, survivors | Floor denominator, survivors | Floor denominator, every leaf (story 6.5) |\n");
        md.append("|---|---|---|---|\n");
        int last = HORIZONS.length - 1;
        for (Row row : rows) {
            List<Sample> s = row.samples();
            Interval a = bootstrap(s, x -> disambiguation(x, last, Basis.RUN, Counted.SURVIVORS), resamples, seed + 4);
            Interval b = bootstrap(s, x -> disambiguation(x, last, Basis.FLOOR, Counted.SURVIVORS), resamples, seed + 5);
            Interval c = bootstrap(s, x -> disambiguation(x, last, Basis.FLOOR, Counted.ALL), resamples, seed + 3);
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s |%n", row.driver(), interval(a), interval(b), interval(c)));
        }
        md.append("\n### What the leaves did\n\n");
        md.append("| Driver | Leaves | Horizon | Death | Left the floor | Dropped | Hidden facts per snapshot (floor / Run) |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            Map<End, Long> ends = new TreeMap<>();
            for (Sample s : row.samples()) {
                for (Leaf l : s.leaves()) {
                    ends.merge(l.end(), 1L, Long::sum);
                }
            }
            double floor = row.samples().stream().mapToInt(Sample::hiddenFloor).average().orElse(Double.NaN);
            double run = row.samples().stream().mapToInt(Sample::hiddenRun).average().orElse(Double.NaN);
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d %s | %.1f / %.1f |%n", row.driver(), row.playouts() - row.dropped(),
                    ends.getOrDefault(End.HORIZON, 0L), ends.getOrDefault(End.DEATH, 0L), ends.getOrDefault(End.LEFT, 0L),
                    row.dropped(), row.droppedWhy(), floor, run));
        }
        md.append("\n### Hidden facts held at a snapshot, by kind (mean per snapshot)\n\n");
        List<String> kinds = List.of("identity", "door", "trap", "mimic");
        md.append("| Driver |");
        for (String kind : kinds) {
            md.append(' ').append(kind).append(" held |");
        }
        md.append("\n|---|").append("---|".repeat(kinds.size())).append('\n');
        for (Row row : rows) {
            md.append("| ").append(row.driver()).append(" |");
            for (String kind : kinds) {
                double held = row.samples().stream().mapToInt(s -> s.hiddenByKind().getOrDefault(kind, 0)).average().orElse(Double.NaN);
                md.append(String.format(Locale.ROOT, " %.2f |", held));
            }
            md.append('\n');
        }
        return md.toString();
    }
}
