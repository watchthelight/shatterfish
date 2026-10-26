package org.shatterfish.harness.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The fit arithmetic of story 6.6. ADR-0010's choice rule 2 asks of each rollout design whether its median Decision time
 * "completes the search it was configured for" at the registered budget. This class holds the configurations and the
 * arithmetic, and reads the medians from the committed report of story 6.4 so that no cost is typed twice. It plays no
 * Run and times nothing.
 *
 * <p>A configuration is the smallest one that is still a search: at least two sampled worlds (one world is a rollout, not a
 * search), a line of three Actions, and for each design the number of rollouts that the shape of the design needs. The
 * probe search of story 6.3 is option 3 at that size ({@link ProbeSearch#CANDIDATES} candidates in
 * {@link ProbeSearch#SAMPLES} worlds). Smaller configurations are not searches; larger ones only cost more.
 */
final class CandidateFit {

    /** One design and the least search it is configured for. */
    record Design(String option, String name, int worlds, int rolloutsPerWorld, String basis) {
        int rollouts() {
            return worlds * rolloutsPerWorld;
        }
    }

    /** The five costs at the three-Action horizon, medians in milliseconds, as story 6.4 measured them. */
    record Costs(double snapshot, double scrub, double load, double rollout3, double restore) {
        /** One rollout pays the swap and the rules; a scrub is paid once per world. */
        double rolloutMs() {
            return snapshot + load + rollout3 + restore;
        }

        double decisionMs(Design d) {
            return d.rollouts() * rolloutMs() + d.worlds() * scrub;
        }
    }

    /** The Brain's rate per process, from the throughput page; the Run mix below is counted from a committed log. */
    static final double WAITS_PER_SECOND = 95.0;

    static final List<Design> DESIGNS = List.of(
            new Design("3", "depth-limited sampled search", ProbeSearch.SAMPLES, ProbeSearch.CANDIDATES,
                    ProbeSearch.CANDIDATES + " candidate Actions, one rollout each, in " + ProbeSearch.SAMPLES + " worlds (the probe)"),
            new Design("5", "hybrid, abstract tree with one rollout per leaf", 2, 9,
                    "a tree of 3 Actions to depth 2 is 9 leaves, one rollout each, in 2 worlds"),
            new Design("4", "information-set search", 100, 1,
                    "100 iterations, each a fresh world and one rollout (a floor stated for a tree search, not measured)"));

    private CandidateFit() {
    }

    /**
     * Mean decision records and mean decisions by the fight policy per Run, from the committed count of 20 Warrior Runs of
     * the {@code standard} set (columns: run, decision records, of them by the fight policy). The fight policy is where
     * the one-ply model runs and where a search would.
     */
    static double[] mix() {
        try {
            double decisions = 0, fight = 0;
            int runs = 0;
            for (String line : Files.readAllLines(repoRoot().resolve("docs/results/logs/2026-10-26-6-6-fight-decisions.txt"))) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] c = line.trim().split("\\s+");
                decisions += Double.parseDouble(c[1]);
                fight += Double.parseDouble(c[2]);
                runs++;
            }
            return new double[] {decisions / runs, fight / runs};
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a Run costs without a search, in seconds. */
    static double runSeconds() {
        return mix()[0] / WAITS_PER_SECOND;
    }

    /** Decisions in a Run where the search would run. */
    static double searchDecisionsPerRun() {
        return mix()[1];
    }

    /** The per-Decision budget, in ms, at which searching doubles a Run's wall time. */
    static double ceilingMs() {
        return runSeconds() * 1000.0 / searchDecisionsPerRun();
    }

    /** Seconds one Run takes when every search Decision spends the given milliseconds. */
    static double runSecondsWith(double decisionMs) {
        return runSeconds() + searchDecisionsPerRun() * decisionMs / 1000.0;
    }

    static Costs read(Path log) throws IOException {
        double snapshot = 0, scrub = 0, load = 0, rollout3 = 0, restore = 0;
        for (String line : Files.readAllLines(log)) {
            String[] c = line.split("\\|");
            if (c.length < 6) {
                continue;
            }
            String kind = c[1].trim();
            String horizon = c[2].trim();
            if (!kind.equals("snapshot") && !kind.equals("scrub") && !kind.equals("load")
                    && !kind.equals("rollout") && !kind.equals("restore")) {
                continue;
            }
            double median = Double.parseDouble(c[4].trim());
            switch (kind) {
                case "snapshot" -> snapshot = median;
                case "scrub" -> scrub = median;
                case "load" -> load = median;
                case "restore" -> restore = median;
                default -> {
                    if (horizon.startsWith("3")) {
                        rollout3 = median;
                    }
                }
            }
        }
        return new Costs(snapshot, scrub, load, rollout3, restore);
    }

    static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("docs/results/logs"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("docs/results/logs not found above the working directory");
        }
        return p;
    }

    static Path report() {
        return repoRoot().resolve("docs/results/logs/2026-10-26-simulator-speed-run1.txt");
    }

    static String markdown(Costs costs, int budgetMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "One rollout cycle at three Actions: %.3f ms (snapshot %.3f, load %.3f, rules %.3f, restore %.3f), a scrub %.3f ms per world.%n%n",
                costs.rolloutMs(), costs.snapshot(), costs.load(), costs.rollout3(), costs.restore(), costs.scrub()));
        sb.append("| Option | Least configured search | Rollouts | Decision time (ms) | Fits ").append(budgetMs)
                .append(" ms | Budget that would fit (ms) | Run seconds with it |\n|---|---|---|---|---|---|---|\n");
        for (Design d : DESIGNS) {
            double t = costs.decisionMs(d);
            sb.append(String.format(Locale.ROOT, "| %s | %s | %d | %.0f | %s | %.0f | %.0f |%n", d.option(), d.basis(),
                    d.rollouts(), t, t <= budgetMs ? "yes" : "no", Math.ceil(t), runSecondsWith(t)));
        }
        return sb.toString();
    }

    /** A test-source tool: it prints the table for the Results page and plays nothing. */
    public static void main(String[] args) throws IOException {
        Costs costs = read(report());
        System.out.printf(Locale.ROOT, "Run without a search: %.1f s; search Decisions per Run: %.1f; ceiling: %.0f ms%n%n",
                runSeconds(), searchDecisionsPerRun(), ceilingMs());
        System.out.println(markdown(costs, 30));
    }
}
