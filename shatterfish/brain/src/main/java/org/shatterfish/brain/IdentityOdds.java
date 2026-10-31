package org.shatterfish.brain;

import org.shatterfish.api.Codex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each unidentified appearance of a family may be, given how often it has turned up and on which
 * floors (issue #410). The arithmetic of {@link Beliefs#identities}; it reads no Observation and no
 * Memory, only counts and the Codex's numbers.
 *
 * <p><b>The model.</b> The appearances are shuffled over the identities once per Run
 * (ItemStatusHandler.java:42-62), so a consistent world is a bijection from appearances to identities. The
 * data are the finds: for each appearance, how many were picked up on each floor. Each (appearance,
 * identity) pair gets a likelihood of that appearance's finds if it were that identity, and an assignment is
 * as likely as the product of its pairs'. The odds of an appearance are its marginal over every
 * assignment, computed exactly by subsets of the identities.
 *
 * <p>Two sources put an identity on a floor:
 * <ul>
 * <li><b>The decks.</b> A random item is a scroll or potion about {@code draws} times a floor (3.5 items,
 * 8 of 35 each: RegularLevel.java:380, Generator.java:221-252), and which one is drawn by deck weight without
 * replacement (Generator.java:711-741). An identity of weight {@code w} out of {@code W} then turns up as a
 * Poisson count of rate {@code notice * draws * w / W} a floor. Without replacement is stricter than Poisson;
 * the Poisson is the approximation, and the calibration page measures it.
 * <li><b>The guarantee.</b> An identity the decks never draw (weight 0) but a guarantee places (upgrade,
 * strength) turns up by the schedule of {@code Dungeon.souNeeded} and {@code posNeeded}
 * (Dungeon.java:529-554), applied on every non-boss floor of the main branch before the random items
 * (Level.java:224-241). The likelihood of a pattern of finds is a forward pass over the floors of each set of
 * five, tracking how many of the set's quota are placed so far; a placed item is noticed with the
 * {@code notice} chance, and any floor can show a stray one at the {@code leak} rate (a shop never sells
 * one: ShopRoom.java:277-288).
 * </ul>
 *
 * <p>A floor the hero has not finished is not evidence of absence: on the current floor a zero count is
 * taken as no data. A label on the screen with no find recorded counts as one find on the current floor
 * (it is there to be found).
 *
 * <p>An appearance identified already is in the assignment too, as a column the appearances not on the
 * screen may fill: the Brain cannot tell which label was identified (the journal names the identity, not
 * the label), and treating it as one of the unknowns would be wrong; the exact sum is cheap.
 */
final class IdentityOdds {

    private IdentityOdds() {
    }

    /** The most identities enumerated by subsets; a family beyond it falls back to independent rows. */
    static final int EXACT = 13;

    /**
     * The model's three numbers, in thousandths.
     *
     * @param notice the chance a generated item is picked up or seen by the hero
     * @param draws  random scrolls (or potions) generated per floor
     * @param leak   stray items of a guaranteed identity per floor, from sources outside the guarantee
     */
    record Params(int notice, int draws, int leak) {

        static final Params REFERENCE = new Params(Beliefs.NOTICE_PER_MILLE, Beliefs.DRAWS_PER_MILLE, Beliefs.LEAK_PER_MILLE);
    }

    /** One column of the assignment: an identity, its weight and its guarantee kind. */
    private record Column(String name, int weight, Guarantee guarantee, boolean identified) {
    }

    private enum Guarantee { NONE, UPGRADE_LIKE, STRENGTH_LIKE }

    /**
     * The odds of each label in {@code view}, over {@code family}'s candidates in the family's order
     * (zero for an identity already identified or one the game never places).
     *
     * @param counts     finds per label, indexed by depth
     * @param depth      the floor the hero is on
     * @param deepest    the deepest floor reached
     * @param view       the labels on the screen
     * @param identified the names of the candidates identified
     */
    static Map<String, double[]> odds(Codex.Identities family, List<Codex.Guarantee> guarantees,
                                      Map<String, int[]> counts, int depth, int deepest, Set<String> view,
                                      Set<String> identified, Params params) {
        List<Codex.Candidate> candidates = family.candidates();
        List<Column> columns = new ArrayList<>();
        List<Integer> index = new ArrayList<>();
        for (int c = 0; c < candidates.size(); c++) {
            Codex.Candidate candidate = candidates.get(c);
            Guarantee kind = Guarantee.NONE;
            for (Codex.Guarantee guarantee : guarantees) {
                if (guarantee.className().equals(candidate.className())) {
                    kind = candidate.className().endsWith("PotionOfStrength") ? Guarantee.STRENGTH_LIKE : Guarantee.UPGRADE_LIKE;
                }
            }
            if (candidate.weight() > 0 || kind != Guarantee.NONE) {
                columns.add(new Column(candidate.name(), candidate.weight(), kind, identified.contains(candidate.name())));
                index.add(c);
            }
        }
        Map<String, double[]> result = new LinkedHashMap<>();
        int n = columns.size();
        if (n == 0) {
            return result;
        }
        int total = candidates.stream().mapToInt(Codex.Candidate::weight).sum();
        int last = Math.max(depth, deepest);
        List<String> rows = new ArrayList<>(family.labels());
        boolean exact = n <= EXACT && rows.size() <= n;
        int square = exact ? n : rows.size();
        double[][] likelihood = new double[square][n];
        for (int r = 0; r < square; r++) {
            String label = r < rows.size() ? rows.get(r) : null;
            boolean shown = label != null && view.contains(label);
            int[] found = label == null ? new int[0] : counts.getOrDefault(label, new int[0]);
            if (shown && sum(found) == 0) {
                found = new int[Math.max(depth + 1, 1)];
                found[Math.min(depth, found.length - 1)] = 1;
            }
            double best = 0;
            for (int x = 0; x < n; x++) {
                Column column = columns.get(x);
                double value = shown && column.identified() ? 0 : likelihood(column, total, found, depth, last, params);
                likelihood[r][x] = value;
                best = Math.max(best, value);
            }
            if (best > 0) {
                for (int x = 0; x < n; x++) {
                    likelihood[r][x] /= best;
                }
            }
        }
        double[][] marginal = exact ? marginal(likelihood, n) : independent(likelihood, n);
        for (int r = 0; r < rows.size(); r++) {
            String label = rows.get(r);
            if (!view.contains(label)) {
                continue;
            }
            double[] row = new double[candidates.size()];
            double sum = 0;
            for (int x = 0; x < n; x++) {
                if (!columns.get(x).identified()) {
                    row[index.get(x)] = marginal[r][x];
                    sum += marginal[r][x];
                }
            }
            if (sum > 0) {
                for (int c = 0; c < row.length; c++) {
                    row[c] /= sum;
                }
            }
            result.put(label, row);
        }
        return result;
    }

    private static int sum(int[] found) {
        int sum = 0;
        for (int one : found) {
            sum += one;
        }
        return sum;
    }

    /**
     * The likelihood of {@code found} (finds per depth) if the appearance were {@code column}, up to a factor
     * that is the same for every column.
     */
    private static double likelihood(Column column, int total, int[] found, int depth, int last, Params params) {
        if (column.guarantee() != Guarantee.NONE) {
            return scheduled(column, found, depth, last, params);
        }
        double rate = params.notice() / 1000.0 * params.draws() / 1000.0 * column.weight() / Math.max(1, total);
        double value = 1;
        for (int d = 1; d <= last; d++) {
            if (d % 5 == 0) {
                continue;
            }
            int c = d < found.length ? found[d] : 0;
            if (d == depth) {
                // The floor the hero is on is not finished: finds count for their rate, absence is no data.
                value *= Math.pow(rate, c);
                continue;
            }
            value *= Math.exp(-rate) * Math.pow(rate, c);
        }
        return value;
    }

    /**
     * The likelihood under a guarantee's schedule: floors of each set of five in order, the state being how
     * many of the set's quota are placed so far. The rule of the floor is {@code Dungeon.souNeeded}
     * (Dungeon.java:545-554) for the upgrade-like and {@code posNeeded} (:529-543) for the strength-like.
     */
    private static double scheduled(Column column, int[] found, int depth, int last, Params params) {
        double notice = params.notice() / 1000.0;
        double leak = params.leak() / 1000.0;
        int quota = column.guarantee() == Guarantee.STRENGTH_LIKE ? 2 : 3;
        double value = 1;
        for (int set = 0; set * 5 + 1 <= last; set++) {
            double[] state = new double[quota + 1];
            state[0] = 1;
            for (int f = 1; f <= 4; f++) {
                int d = set * 5 + f;
                if (d > last) {
                    break;
                }
                int c = d < found.length ? found[d] : 0;
                double[] next = new double[quota + 1];
                for (int g = 0; g <= quota; g++) {
                    if (state[g] == 0) {
                        continue;
                    }
                    double place = column.guarantee() == Guarantee.STRENGTH_LIKE
                            ? placeStrength(f, quota - g) : placeUpgrade(f, quota - g);
                    double miss = observe(c, false, notice, leak, d == depth);
                    double hit = observe(c, true, notice, leak, d == depth);
                    next[g] += state[g] * (1 - place) * miss;
                    if (g < quota) {
                        next[g + 1] += state[g] * place * hit;
                    } else {
                        next[g] += state[g] * place * miss;
                    }
                }
                state = next;
            }
            double sum = 0;
            for (double one : state) {
                sum += one;
            }
            value *= sum;
        }
        return value;
    }

    /** {@code Random.Int(5 - floorThisSet) < left} (Dungeon.java:553). */
    static double placeUpgrade(int floorThisSet, int left) {
        if (left <= 0) {
            return 0;
        }
        return Math.min(1.0, left / (double) (5 - floorThisSet));
    }

    /** The test of Dungeon.java:537-540: a target that falls by one on a coin flip on odd floors. */
    static double placeStrength(int floorThisSet, int left) {
        if (left <= 0) {
            return 0;
        }
        int target = 2 - floorThisSet / 2;
        if (floorThisSet % 2 == 1) {
            return 0.5 * (target < left ? 1 : 0) + 0.5 * (target - 1 < left ? 1 : 0);
        }
        return target < left ? 1 : 0;
    }

    /** The chance of seeing {@code c} on a floor, given whether the guarantee placed one there. */
    private static double observe(int c, boolean placed, double notice, double leak, boolean current) {
        if (c == 0 && current) {
            return 1;
        }
        if (!placed) {
            return poisson(c, leak);
        }
        return (1 - notice) * poisson(c, leak) + (c >= 1 ? notice * poisson(c - 1, leak) : 0);
    }

    private static double poisson(int c, double rate) {
        double value = Math.exp(-rate);
        for (int i = 1; i <= c; i++) {
            value *= rate / i;
        }
        return value;
    }

    /** The marginal of each row over the columns, in every bijection, weighting a bijection by the product of its cells. */
    static double[][] marginal(double[][] likelihood, int n) {
        int size = 1 << n;
        double[] forward = new double[size];
        forward[0] = 1;
        for (int s = 0; s < size; s++) {
            if (forward[s] == 0) {
                continue;
            }
            int row = Integer.bitCount(s);
            if (row >= n) {
                continue;
            }
            for (int x = 0; x < n; x++) {
                if ((s & (1 << x)) == 0) {
                    forward[s | (1 << x)] += forward[s] * likelihood[row][x];
                }
            }
        }
        // backward[T]: the last |T| rows given exactly the columns in T.
        double[] backward = new double[size];
        backward[0] = 1;
        for (int t = 0; t < size; t++) {
            if (backward[t] == 0) {
                continue;
            }
            int placed = Integer.bitCount(t);
            if (placed >= n) {
                continue;
            }
            int row = n - 1 - placed;
            for (int x = 0; x < n; x++) {
                if ((t & (1 << x)) == 0) {
                    backward[t | (1 << x)] += backward[t] * likelihood[row][x];
                }
            }
        }
        double[][] marginal = new double[n][n];
        int full = size - 1;
        for (int s = 0; s < size; s++) {
            if (forward[s] == 0) {
                continue;
            }
            int row = Integer.bitCount(s);
            if (row >= n) {
                continue;
            }
            for (int x = 0; x < n; x++) {
                if ((s & (1 << x)) == 0) {
                    marginal[row][x] += forward[s] * likelihood[row][x] * backward[full ^ s ^ (1 << x)];
                }
            }
        }
        for (int r = 0; r < n; r++) {
            double sum = 0;
            for (int x = 0; x < n; x++) {
                sum += marginal[r][x];
            }
            for (int x = 0; x < n; x++) {
                marginal[r][x] = sum > 0 ? marginal[r][x] / sum : 1.0 / n;
            }
        }
        return marginal;
    }

    /** Each row alone, by its own likelihoods: the fallback for a family too big for the subsets. */
    static double[][] independent(double[][] likelihood, int n) {
        double[][] marginal = new double[likelihood.length][n];
        for (int r = 0; r < likelihood.length; r++) {
            double sum = 0;
            for (int x = 0; x < n; x++) {
                sum += likelihood[r][x];
            }
            for (int x = 0; x < n; x++) {
                marginal[r][x] = sum > 0 ? likelihood[r][x] / sum : 1.0 / n;
            }
        }
        return marginal;
    }
}
