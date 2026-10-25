package org.shatterfish.rig;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The numbers every Brain PR carries, read from a folder of Run logs (and the heap records that
 * {@link DirectionPlay} wrote beside them), and the table that sets two such folders side by side.
 *
 * <p>Everything is read from the logs through {@link RunLogReader}; nothing here knows a game rule.
 * A Run counts when its log is there. A Run whose log has no ending still counts in {@code runs}
 * and in the depth it reached, and in none of the columns that need an ending.
 */
final class DirectionMetrics {

    /** A search wait that has to be a search for the frontier or the stairs, not a search spot. */
    private static final String SEARCH = "search ";
    private static final String SEARCH_SPOT = "search-spot";

    /** A Run alternating between two cells for at least this many consecutive Steps oscillates. */
    static final int OSCILLATION_LEAST = 6;

    /** A heap taking more waits than this from first sight to pick-up is slow. */
    static final int SLOW_HEAP = 100;

    /** One side's numbers. */
    record Side(int runs, int reached5, int gooKills, List<Double> turns, List<Integer> deepest,
                java.util.Map<Integer, Integer> deaths, int floors, int searches, int searchSpots,
                int oscillation, int heapsSeen, int heapsTaken, List<Integer> seenToTaken) {

        double medianTurns() {
            return median(turns.stream().map(Double::doubleValue).toList());
        }

        double deepestMean() {
            return deepest.isEmpty() ? 0 : deepest.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        int deepestMax() {
            return deepest.stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        double searchesPerFloor() {
            return floors == 0 ? 0 : (double) searches / floors;
        }

        double searchWaitsPerFloor() {
            return floors == 0 ? 0 : (double) (searches + searchSpots) / floors;
        }

        double seenToTakenMedian() {
            return median(seenToTaken.stream().map(Integer::doubleValue).toList());
        }

        int slowHeaps() {
            return (int) seenToTaken.stream().filter(d -> d > SLOW_HEAP).count();
        }

        int deathsAt(int depth) {
            return deaths.getOrDefault(depth, 0);
        }

        int deepestDeath() {
            return deaths.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    }

    private DirectionMetrics() {
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    /** The numbers of every {@code *.jsonl} Run log in {@code folder}. */
    static Side read(Path folder) {
        int runs = 0;
        int reached5 = 0;
        int goo = 0;
        List<Double> turns = new ArrayList<>();
        List<Integer> deepest = new ArrayList<>();
        java.util.Map<Integer, Integer> deaths = new TreeMap<>();
        int floors = 0;
        int searches = 0;
        int spots = 0;
        int osc = 0;
        for (Path log : list(folder, ".jsonl")) {
            RunLogReader.Log read = RunLogReader.of(log);
            if (read.readable() && !read.records().isEmpty() && read.records().get(0) instanceof RunLog.Header header
                    && (header.oracle() || !header.registration().isEmpty())) {
                throw new IllegalStateException(log + " is an oracle or a ranked Run; a direction check reads"
                        + " only unranked development Runs");
            }
            runs++;
            List<RunLog.Wait> waits = read.waits();
            int maxDepth = 0;
            Set<Long> seenFloors = new HashSet<>();
            List<Action> actions = new ArrayList<>();
            for (RunLog.Wait wait : waits) {
                maxDepth = Math.max(maxDepth, wait.depth());
                seenFloors.add(((long) wait.depth() << 32) | wait.branch());
                actions.add(wait.action());
                RunLog.Decision decision = wait.decision();
                if (decision != null) {
                    String why = decision.chosen() == null ? "" : decision.chosen().why();
                    String policy = decision.policy();
                    if (why.startsWith(SEARCH) && ("explore".equals(policy) || "descend".equals(policy))) {
                        searches++;
                    }
                    if (why.startsWith(SEARCH_SPOT)) {
                        spots++;
                    }
                }
            }
            if (maxDepth >= 5) {
                reached5++;
            }
            floors += seenFloors.size();
            osc += oscillation(actions);
            RunLog.End end = read.end();
            if (end != null) {
                RunLog.Outcome outcome = end.outcome();
                turns.add(outcome.turns() / 1000.0);
                deepest.add(outcome.depth());
                if (outcome.bosses() >= 1) {
                    goo++;
                }
                if ("DEATH".equals(outcome.cause())) {
                    deaths.merge(outcome.depth(), 1, Integer::sum);
                }
            }
        }
        int heaps = 0;
        int taken = 0;
        List<Integer> seenToTaken = new ArrayList<>();
        for (Path tsv : list(folder, DirectionPlay.HEAPS)) {
            List<String> lines = lines(tsv);
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] cells = line.split("\t");
                if (cells.length < 6) {
                    continue;
                }
                heaps++;
                int seen = Integer.parseInt(cells[4]);
                int took = Integer.parseInt(cells[5]);
                if (took >= 0) {
                    taken++;
                    seenToTaken.add(took - seen);
                }
            }
        }
        return new Side(runs, reached5, goo, turns, deepest, deaths, floors, searches, spots, osc, heaps,
                taken, seenToTaken);
    }

    /**
     * Steps that belong to a run of at least {@link #OSCILLATION_LEAST} consecutive Steps that go
     * back and forth between the same two cells.
     */
    static int oscillation(List<Action> actions) {
        int total = 0;
        int i = 0;
        int n = actions.size();
        while (i < n - 3) {
            if (!(actions.get(i) instanceof Action.Step first)) {
                i++;
                continue;
            }
            int j = i + 1;
            List<Integer> cells = new ArrayList<>(List.of(first.cell()));
            while (j < n && actions.get(j) instanceof Action.Step step) {
                int c = step.cell();
                if (cells.size() >= 2 && c != cells.get(cells.size() - 2)) {
                    break;
                }
                if (cells.size() == 1 && c == cells.get(0)) {
                    break;
                }
                cells.add(c);
                j++;
            }
            if (j - i >= OSCILLATION_LEAST && new HashSet<>(cells).size() == 2) {
                total += j - i;
                i = j;
            } else {
                i++;
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ the table

    /**
     * The PR table. {@code baseline} may be null, and then there are two columns instead of four.
     */
    static String table(String currentName, Side current, String baselineName, Side baseline) {
        int depths = Math.max(current.deepestDeath(), baseline == null ? 0 : baseline.deepestDeath());
        List<String[]> rows = new ArrayList<>();
        rows.add(row("Runs", baseline, current, s -> String.valueOf(s.runs()), s -> s.runs()));
        rows.add(row("reach depth 5", baseline, current, s -> s.reached5() + "/" + s.runs(), s -> s.reached5()));
        rows.add(row("Goo kills", baseline, current, s -> s.gooKills() + "/" + s.runs(), s -> s.gooKills()));
        rows.add(row("median turns", baseline, current, s -> number(s.medianTurns(), 1), s -> s.medianTurns()));
        rows.add(row("deepest mean / max", baseline, current,
                s -> number(s.deepestMean(), 3) + " / " + s.deepestMax(), s -> s.deepestMean()));
        rows.add(row("deaths by depth 1.." + depths, baseline, current, s -> {
            List<String> each = new ArrayList<>();
            for (int d = 1; d <= depths; d++) {
                each.add(String.valueOf(s.deathsAt(d)));
            }
            return depths == 0 ? "none" : String.join("/", each);
        }, null));
        rows.add(row("searches per floor", baseline, current, s -> number(s.searchesPerFloor(), 2),
                s -> s.searchesPerFloor()));
        rows.add(row("search waits per floor", baseline, current, s -> number(s.searchWaitsPerFloor(), 2),
                s -> s.searchWaitsPerFloor()));
        rows.add(row("oscillation Steps", baseline, current, s -> String.valueOf(s.oscillation()),
                s -> s.oscillation()));
        rows.add(row("heaps taken / seen", baseline, current, s -> s.heapsTaken() + "/" + s.heapsSeen(),
                s -> s.heapsTaken()));
        rows.add(row("seen to taken, median waits", baseline, current, s -> number(s.seenToTakenMedian(), 0),
                s -> s.seenToTakenMedian()));
        rows.add(row("heaps taking over " + SLOW_HEAP + " waits", baseline, current,
                s -> s.slowHeaps() + " of " + s.heapsSeen(), s -> s.slowHeaps()));
        StringBuilder out = new StringBuilder();
        if (baseline == null) {
            out.append("| | ").append(currentName).append(" |\n|---|---|\n");
            for (String[] r : rows) {
                out.append("| ").append(r[0]).append(" | ").append(r[2]).append(" |\n");
            }
        } else {
            out.append("| | ").append(baselineName).append(" | ").append(currentName).append(" | delta |\n")
                    .append("|---|---|---|---|\n");
            for (String[] r : rows) {
                out.append("| ").append(r[0]).append(" | ").append(r[1]).append(" | ").append(r[2])
                        .append(" | ").append(r[3]).append(" |\n");
            }
        }
        return out.toString();
    }

    private static String[] row(String label, Side baseline, Side current,
                                java.util.function.Function<Side, String> text,
                                java.util.function.ToDoubleFunction<Side> value) {
        String delta = "";
        if (baseline != null) {
            delta = value == null ? "" : signed(value.applyAsDouble(current) - value.applyAsDouble(baseline));
        }
        return new String[] {label, baseline == null ? "" : text.apply(baseline), text.apply(current), delta};
    }

    private static String number(double v, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", v);
    }

    private static String signed(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            long whole = Math.round(d);
            return whole > 0 ? "+" + whole : String.valueOf(whole);
        }
        String s = String.format(Locale.ROOT, "%.2f", d);
        return d > 0 ? "+" + s : s;
    }

    // ------------------------------------------------------------------ files

    private static List<Path> list(Path folder, String suffix) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .filter(p -> !p.getFileName().toString().equals(RunIndex.RUNS)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + folder, e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
