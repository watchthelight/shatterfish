package org.shatterfish.rig;

import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The measured win rate of story 7.10 (FR-18, FR-25): wins out of Runs with the Wilson interval, the Score of any wins,
 * deaths by depth, reach of depth 5 and Goo kills over reach, for all classes and for the Warrior slice, each beside the
 * Profile 6 baseline. Every figure is computed here from the {@code outcomes.jsonl} the Rig's extract committed under
 * {@code results/}, and {@link WinRateTest} compares the block this renders with the one on the Results page, so the page
 * cannot carry a figure the committed Runs do not give.
 *
 * <p>A <b>Goo kill</b> is a Run that killed at least one boss: the first boss of the main path is Goo on depth 5
 * ({@code Dungeon.java:441-443}), so for a Run that killed a boss and did not win, the count is Goo's. A Run that killed
 * more than one still counts once.
 */
final class WinRate {

    /** The 95% two-sided normal quantile, as the Rig's other interval pages use. */
    static final double Z = 1.959964;

    /** One Run's outcome line. */
    record Outcome(String heroClass, String cause, int depth, int bosses, boolean win, long score, long turns) {
    }

    private WinRate() {
    }

    /** The Wilson score interval for {@code k} of {@code n}, as {low, high}; {0, 1} when there are no Runs. */
    static double[] wilson(int k, int n) {
        if (n == 0) {
            return new double[] {0, 1};
        }
        double p = (double) k / n;
        double z2 = Z * Z;
        double centre = (p + z2 / (2 * n)) / (1 + z2 / n);
        double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n);
        return new double[] {Math.max(0, centre - half), Math.min(1, centre + half)};
    }

    /**
     * The exact (Clopper-Pearson) upper limit of the two-sided 95% binomial interval for {@code k} wins of {@code n}:
     * the largest rate p for which seeing {@code k} or fewer wins still has probability at least 2.5%. Found by
     * bisection on the binomial tail; 1 when every Run won.
     */
    static double exactUpper(int k, int n) {
        if (k >= n) {
            return 1.0;
        }
        double low = (double) k / n;
        double high = 1.0;
        for (int i = 0; i < 80; i++) {
            double mid = (low + high) / 2;
            if (binomialTail(k, n, mid) > 0.025) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /** P(X <= k) for X binomial(n, p), summed in log space. */
    private static double binomialTail(int k, int n, double p) {
        double sum = 0;
        double logChoose = 0;
        for (int i = 0; i <= k; i++) {
            if (i > 0) {
                logChoose += Math.log(n - i + 1) - Math.log(i);
            }
            sum += Math.exp(logChoose + i * Math.log(p) + (n - i) * Math.log1p(-p));
        }
        return sum;
    }

    static List<Outcome> read(Path file) throws IOException {
        List<Outcome> outcomes = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, String> row = Json.object(line);
            outcomes.add(new Outcome(Json.string(Json.required(row, "class", "outcome")),
                    Json.string(Json.required(row, "cause", "outcome")),
                    Json.integer(Json.required(row, "depth", "outcome")),
                    Json.integer(Json.required(row, "bosses", "outcome")),
                    Json.bool(Json.required(row, "win", "outcome")),
                    Json.number(Json.required(row, "score", "outcome")),
                    Json.number(Json.required(row, "turns", "outcome"))));
        }
        return outcomes;
    }

    static List<Outcome> only(List<Outcome> all, String heroClass) {
        return heroClass == null ? all : all.stream().filter(o -> o.heroClass().equals(heroClass)).toList();
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.2f%%", share * 100);
    }

    private static String ratio(int k, int n) {
        return k + "/" + n + (n == 0 ? "" : String.format(Locale.ROOT, " (%.1f%%)", 100.0 * k / n));
    }

    /** The cells of one side of the table, in the row order of {@link #ROWS}. */
    static List<String> column(List<Outcome> runs) {
        int n = runs.size();
        int wins = (int) runs.stream().filter(Outcome::win).count();
        double[] w = wilson(wins, n);
        List<Long> scores = runs.stream().filter(Outcome::win).map(Outcome::score).sorted().toList();
        String score = scores.isEmpty() ? "no wins" : "min " + scores.get(0) + ", median " + scores.get(scores.size() / 2)
                + ", max " + scores.get(scores.size() - 1);
        int reach = (int) runs.stream().filter(o -> o.depth() >= 5).count();
        int goo = (int) runs.stream().filter(o -> o.bosses() >= 1).count();
        int deepest = runs.stream().mapToInt(Outcome::depth).max().orElse(0);
        List<String> byDepth = new ArrayList<>();
        for (int d = 1; d <= Math.max(deepest, 1); d++) {
            final int depth = d;
            byDepth.add(String.valueOf(runs.stream().filter(o -> o.cause().equals("DEATH") && o.depth() == depth).count()));
        }
        Map<String, Integer> causes = new TreeMap<>();
        for (Outcome o : runs) {
            causes.merge(o.cause(), 1, Integer::sum);
        }
        List<Long> turns = runs.stream().map(Outcome::turns).sorted().toList();
        return List.of(
                String.valueOf(n),
                ratio(wins, n),
                n == 0 ? "-" : percent(w[0]) + " to " + percent(w[1]),
                score,
                ratio(reach, n),
                reach == 0 ? "-" : ratio(goo, reach),
                String.join("/", byDepth),
                causes.toString().replace("{", "").replace("}", "").replace("=", " "),
                turns.isEmpty() ? "-" : String.valueOf(turns.get(turns.size() / 2) / 1000));
    }

    static final List<String> ROWS = List.of("Runs", "wins", "win rate, Wilson 95%", "Score of the wins", "reach depth 5",
            "Goo kills over reach", "deaths by depth 1..deepest", "endings", "median turns");

    /** One table: the baseline column and the final column for a slice of the Runs. */
    static String table(String heading, List<Outcome> baseline, List<Outcome> current) {
        List<String> before = column(baseline);
        List<String> after = column(current);
        StringBuilder out = new StringBuilder("### ").append(heading).append("\n\n| | Profile 6 baseline | E7 final Brain |\n|---|---|---|\n");
        for (int i = 0; i < ROWS.size(); i++) {
            out.append("| ").append(ROWS.get(i)).append(" | ").append(before.get(i)).append(" | ").append(after.get(i)).append(" |\n");
        }
        return out.toString();
    }

    /** Story 7.1's count, from its Results section: found, linked to a Rule, to a Lore claim, to a measured row, unlinked. */
    static final int[] STORY_7_1 = {71, 35, 0, 5, 31};

    /**
     * The link count at the close beside story 7.1's: heuristics found, linked to a Rule, to a Lore claim, at each Tier,
     * unlinked, and the Tier 3 links that shipped with the Results page that is each one's evidence.
     */
    static String heuristicBlock() throws IOException {
        List<HeuristicIndex.Found> found = HeuristicIndex.annotated();
        Map<String, String> rules = HeuristicIndex.ruleTiers();
        Map<String, HeuristicIndex.Measured> measured = HeuristicIndex.measured();
        Map<String, HeuristicIndex.LoreClaim> lore = HeuristicIndex.loreClaims();
        int rule = 0;
        int claim = 0;
        int row = 0;
        int unlinked = 0;
        Map<String, Integer> tiers = new TreeMap<>();
        Map<String, List<String>> shippedTier3 = new TreeMap<>();
        for (HeuristicIndex.Found heuristic : found) {
            String slug = heuristic.slug();
            if (slug.equals(org.shatterfish.api.Heuristic.UNLINKED)) {
                unlinked++;
            } else if (rules.containsKey(slug)) {
                rule++;
                tiers.merge("Rule Tier " + rules.get(slug), 1, Integer::sum);
            } else if (lore.containsKey(slug)) {
                claim++;
                tiers.merge("Lore Tier " + lore.get(slug).tier(), 1, Integer::sum);
            } else {
                row++;
                HeuristicIndex.Measured m = measured.get(slug);
                tiers.merge("measured Tier " + m.tier(), 1, Integer::sum);
                if (m.tier().equals("3")) {
                    shippedTier3.computeIfAbsent("[`" + slug + "`](" + (m.evidence().startsWith("results/") ? m.evidence().substring("results/".length()) : "../" + m.evidence()) + ")", key -> new ArrayList<>())
                            .add("`" + heuristic.constant() + "`");
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("| | story 7.1 (the baseline) | at the close |\n|---|---|---|\n");
        out.append("| heuristics found | ").append(STORY_7_1[0]).append(" | ").append(found.size()).append(" |\n");
        out.append("| linked to a Rule | ").append(STORY_7_1[1]).append(" | ").append(rule).append(" |\n");
        out.append("| linked to a Lore claim | ").append(STORY_7_1[2]).append(" | ").append(claim).append(" |\n");
        out.append("| linked to a measured row | ").append(STORY_7_1[3]).append(" | ").append(row).append(" |\n");
        out.append("| unlinked | ").append(STORY_7_1[4]).append(" | ").append(unlinked).append(" |\n\n");
        out.append("Linked at each Tier: ");
        List<String> parts = new ArrayList<>();
        tiers.forEach((tier, count) -> parts.add(tier + ": " + count));
        out.append(String.join(", ", parts)).append(".\n\n");
        out.append("Tier 3 links on heuristics that shipped, each with the Results page that is its evidence:\n\n");
        shippedTier3.forEach((page, constants) -> out.append("- ").append(page).append(": ").append(String.join(", ", constants)).append('\n'));
        return out.toString();
    }

    /**
     * The holdout page's block: for the whole set and each class, the Brain on {@code standard} (story 7.10) beside the same
     * Brain on {@code holdout}, with the exact binomial upper limit as an extra row after the Wilson interval.
     */
    static String renderHoldout(List<Outcome> standard, List<Outcome> holdout) {
        StringBuilder out = new StringBuilder();
        out.append(holdoutTable("All classes", standard, holdout)).append('\n');
        out.append(holdoutTable("Warrior slice", only(standard, "WARRIOR"), only(holdout, "WARRIOR")));
        TreeMap<String, Integer> classes = new TreeMap<>();
        for (Outcome o : holdout) {
            classes.merge(o.heroClass(), 1, Integer::sum);
        }
        for (String heroClass : classes.keySet()) {
            if (!heroClass.equals("WARRIOR")) {
                out.append('\n').append(holdoutTable(heroClass.charAt(0) + heroClass.substring(1).toLowerCase(Locale.ROOT) + " slice",
                        only(standard, heroClass), only(holdout, heroClass)));
            }
        }
        return out.toString();
    }

    private static String holdoutTable(String heading, List<Outcome> standard, List<Outcome> holdout) {
        List<String> before = column(standard);
        List<String> after = column(holdout);
        StringBuilder out = new StringBuilder("### ").append(heading)
                .append("\n\n| | E7 final Brain on `standard` (story 7.10) | E7 final Brain on `holdout` |\n|---|---|---|\n");
        for (int i = 0; i < ROWS.size(); i++) {
            out.append("| ").append(ROWS.get(i)).append(" | ").append(before.get(i)).append(" | ").append(after.get(i)).append(" |\n");
            if (i == 2) {
                out.append("| win rate, exact binomial 95% upper limit | ").append(exactCell(standard)).append(" | ")
                        .append(exactCell(holdout)).append(" |\n");
            }
        }
        return out.toString();
    }

    private static String exactCell(List<Outcome> runs) {
        int wins = (int) runs.stream().filter(Outcome::win).count();
        return runs.isEmpty() ? "-" : percent(exactUpper(wins, runs.size()));
    }

    /** The whole generated block: all classes, the Warrior slice, then every other class that was measured. */
    static String render(List<Outcome> baseline, List<Outcome> current) {
        StringBuilder out = new StringBuilder();
        out.append(table("Warrior slice", only(baseline, "WARRIOR"), only(current, "WARRIOR"))).append('\n');
        out.append(table("All classes", baseline, current));
        TreeMap<String, Integer> classes = new TreeMap<>();
        for (Outcome o : current) {
            classes.merge(o.heroClass(), 1, Integer::sum);
        }
        for (String heroClass : classes.keySet()) {
            if (!heroClass.equals("WARRIOR")) {
                out.append('\n').append(table(heroClass.charAt(0) + heroClass.substring(1).toLowerCase(Locale.ROOT) + " slice",
                        only(baseline, heroClass), only(current, heroClass)));
            }
        }
        return out.toString();
    }
}
