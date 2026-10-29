package org.shatterfish.rig;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.EndingRecord;
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

    /** The name a scroll of upgrade shows in a heap record (items.properties), which it does from the first Run. */
    static final String UPGRADE_SCROLL = "scroll of upgrade";

    /** The reason a Decision gives for confirming the upgrade window; one such answer is one upgrade spent. */
    static final String UPGRADE_WHY = "upgrade:";

    /** The last depth the upgrade columns count: what a Run has found and read by the Goo's floor. */
    static final int UPGRADE_DEPTH = 5;

    /** A heap taking more waits than this from first sight to pick-up is slow. */
    static final int SLOW_HEAP = 100;

    /** One death: the floor and who the game says killed the hero ("unknown" when no ending record says). */
    record Death(int depth, String killer) {
    }

    /**
     * One side's numbers.
     *
     * <p>{@code withEnding} counts the Runs whose ending record (killer, bosses by name) was found
     * beside the log; a log from before that record, or a baseline cached before it, has none, and
     * its boss identity and killer are then "not recorded" rather than guessed from the count.
     */
    record Side(int runs, int reached5, int bossesKilled, List<Double> turns, List<Integer> deepest,
                java.util.Map<Integer, Integer> deaths, int floors, int searches, int searchSpots,
                int oscillation, int heapsSeen, int heapsTaken, List<Integer> seenToTaken,
                int upgradesTaken, int upgradesRead, int reached10, int reached15, int withEnding,
                java.util.Map<String, Integer> bossKills, List<Long> thinkMs, List<Death> killers) {

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

        /** Runs that killed the boss named {@code boss} (an {@link EndingRecord#BOSSES} name). */
        int killed(String boss) {
            return bossKills.getOrDefault(boss, 0);
        }

        /** Mean milliseconds a Decision took, or 0 with none. */
        double thinkMean() {
            return thinkMs.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        /** The 95th percentile (nearest rank) of the milliseconds a Decision took, or 0 with none. */
        double thinkP95() {
            if (thinkMs.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(thinkMs);
            sorted.sort(Comparator.naturalOrder());
            return sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
        }

        /** Several sides added together, for a pooled column. */
        static Side pool(List<Side> sides) {
            java.util.Map<Integer, Integer> deaths = new TreeMap<>();
            java.util.Map<String, Integer> kills = new TreeMap<>();
            List<Double> turns = new ArrayList<>();
            List<Integer> deepest = new ArrayList<>();
            List<Integer> seen = new ArrayList<>();
            List<Long> think = new ArrayList<>();
            List<Death> killers = new ArrayList<>();
            int[] n = new int[13];
            for (Side s : sides) {
                int[] each = {s.runs, s.reached5, s.bossesKilled, s.floors, s.searches, s.searchSpots,
                        s.oscillation, s.heapsSeen, s.heapsTaken, s.upgradesTaken, s.upgradesRead,
                        s.reached10, s.withEnding};
                for (int i = 0; i < n.length; i++) {
                    n[i] += each[i];
                }
                turns.addAll(s.turns);
                deepest.addAll(s.deepest);
                seen.addAll(s.seenToTaken);
                think.addAll(s.thinkMs);
                killers.addAll(s.killers);
                s.deaths.forEach((d, c) -> deaths.merge(d, c, Integer::sum));
                s.bossKills.forEach((b, c) -> kills.merge(b, c, Integer::sum));
            }
            int reached15 = sides.stream().mapToInt(Side::reached15).sum();
            return new Side(n[0], n[1], n[2], turns, deepest, deaths, n[3], n[4], n[5], n[6], n[7], n[8], seen,
                    n[9], n[10], n[11], reached15, n[12], kills, think, killers);
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

    /** The numbers of every {@code *.jsonl} Run log in {@code folder}, all classes together. */
    static Side read(Path folder) {
        return Side.pool(new ArrayList<>(readByClass(folder).values()));
    }

    /**
     * The numbers of every Run log in {@code folder}, one {@link Side} per hero class, in the game's
     * class order. A Run whose log has no readable header belongs to no class and shows only in
     * {@link #read}'s pooled side.
     */
    static java.util.Map<org.shatterfish.api.HeroClass, Side> classes(Path folder) {
        java.util.Map<org.shatterfish.api.HeroClass, Side> out =
                new java.util.EnumMap<>(org.shatterfish.api.HeroClass.class);
        java.util.Map<String, Side> read = readByClass(folder);
        for (org.shatterfish.api.HeroClass c : org.shatterfish.api.HeroClass.values()) {
            Side side = read.get(c.name());
            if (side != null) {
                out.put(c, side);
            }
        }
        return out;
    }

    /** What is gathered for one hero class while the logs are read. */
    private static final class Acc {
        int runs;
        int reached5;
        int reached10;
        int reached15;
        int bossesKilled;
        int withEnding;
        List<Double> turns = new ArrayList<>();
        List<Integer> deepest = new ArrayList<>();
        java.util.Map<Integer, Integer> deaths = new TreeMap<>();
        java.util.Map<String, Integer> bossKills = new TreeMap<>();
        List<Long> thinkMs = new ArrayList<>();
        List<Death> killers = new ArrayList<>();
        int floors;
        int searches;
        int spots;
        int osc;
        int upgradesRead;
        int heaps;
        int taken;
        List<Integer> seenToTaken = new ArrayList<>();
        int upgradesTaken;

        Side side() {
            return new Side(runs, reached5, bossesKilled, turns, deepest, deaths, floors, searches, spots, osc,
                    heaps, taken, seenToTaken, upgradesTaken, upgradesRead, reached10, reached15, withEnding,
                    bossKills, thinkMs, killers);
        }
    }

    private static java.util.Map<String, Side> readByClass(Path folder) {
        java.util.Map<String, Acc> accs = new TreeMap<>();
        for (Path log : list(folder, ".jsonl")) {
            RunLogReader.Log read = RunLogReader.of(log);
            if (read.readable() && !read.records().isEmpty() && read.records().get(0) instanceof RunLog.Header header
                    && (header.oracle() || !header.registration().isEmpty())) {
                throw new IllegalStateException(log + " is an oracle or a ranked Run; a direction check reads"
                        + " only unranked development Runs");
            }
            // A practice Run began on a boss floor with a made-up kit; no direction table, and so no
            // baseline, may rest on one (ADR-0024).
            OverlayLogs.refuse(read, log);
            String heroClass = read.readable() && !read.records().isEmpty()
                    && read.records().get(0) instanceof RunLog.Header first ? first.heroClass().name() : "";
            Acc acc = accs.computeIfAbsent(heroClass, k -> new Acc());
            acc.runs++;
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
                    acc.thinkMs.add(wait.thinkMs());
                    String why = decision.chosen() == null ? "" : decision.chosen().why();
                    String policy = decision.policy();
                    if (why.startsWith(SEARCH) && ("explore".equals(policy) || "descend".equals(policy))) {
                        acc.searches++;
                    }
                    if (why.startsWith(SEARCH_SPOT)) {
                        acc.spots++;
                    }
                    if (why.startsWith(UPGRADE_WHY) && wait.action() instanceof Action.AnswerPrompt
                            && wait.depth() <= UPGRADE_DEPTH) {
                        acc.upgradesRead++;
                    }
                }
            }
            if (maxDepth >= 5) {
                acc.reached5++;
            }
            if (maxDepth >= 10) {
                acc.reached10++;
            }
            if (maxDepth >= 15) {
                acc.reached15++;
            }
            acc.floors += seenFloors.size();
            acc.osc += oscillation(actions);
            RunLog.End end = read.end();
            EndingRecord.Ending ending = EndingRecord.read(log);
            if (ending != null) {
                acc.withEnding++;
                for (String boss : ending.bossKills()) {
                    acc.bossKills.merge(boss, 1, Integer::sum);
                }
            }
            if (end != null) {
                RunLog.Outcome outcome = end.outcome();
                acc.turns.add(outcome.turns() / 1000.0);
                acc.deepest.add(outcome.depth());
                if (outcome.bosses() >= 1) {
                    acc.bossesKilled++;
                }
                if ("DEATH".equals(outcome.cause())) {
                    acc.deaths.merge(outcome.depth(), 1, Integer::sum);
                    acc.killers.add(new Death(outcome.depth(),
                            ending == null || ending.killer().isEmpty() ? UNKNOWN_KILLER : ending.killer()));
                }
            }
        }
        for (Path tsv : list(folder, DirectionPlay.HEAPS)) {
            String name = tsv.getFileName().toString();
            Acc acc = accs.computeIfAbsent(name.contains("-") ? name.substring(0, name.indexOf('-')) : "",
                    k -> new Acc());
            List<String> lines = lines(tsv);
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] cells = line.split("\t");
                if (cells.length < 6) {
                    continue;
                }
                acc.heaps++;
                int seen = Integer.parseInt(cells[4]);
                int took = Integer.parseInt(cells[5]);
                if (took >= 0 && cells[3].equals(UPGRADE_SCROLL) && Integer.parseInt(cells[0]) <= UPGRADE_DEPTH) {
                    acc.upgradesTaken++;
                }
                if (took >= 0) {
                    acc.taken++;
                    acc.seenToTaken.add(took - seen);
                }
            }
        }
        java.util.Map<String, Side> sides = new TreeMap<>();
        accs.forEach((k, v) -> sides.put(k, v.side()));
        return sides;
    }

    /** The killer shown for a death whose ending record is missing. */
    static final String UNKNOWN_KILLER = "not recorded";

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

    /** One row of the table: its label, how a side shows it, and the number its delta is taken on (null for none). */
    record Metric(String label, java.util.function.Function<Side, String> text,
                  java.util.function.ToDoubleFunction<Side> value) {
    }

    /** The boss row's cell: kills over Runs, with how many Runs had an ending record when not all did. */
    private static String killsOf(Side s, String boss) {
        if (s.withEnding() == 0) {
            return s.runs() == 0 ? "0/0" : "not recorded";
        }
        return s.killed(boss) + "/" + s.runs() + (s.withEnding() < s.runs() ? " (" + s.withEnding() + " recorded)" : "");
    }

    /** The rows of the table, in order; {@code depths} is how deep the deaths-by-depth row goes. */
    static List<Metric> metrics(int depths) {
        List<Metric> rows = new ArrayList<>();
        rows.add(new Metric("Runs", s -> String.valueOf(s.runs()), s -> s.runs()));
        rows.add(new Metric("reach depth 5", s -> s.reached5() + "/" + s.runs(), s -> s.reached5()));
        rows.add(new Metric("reach depth 10", s -> s.reached10() + "/" + s.runs(), s -> s.reached10()));
        rows.add(new Metric("reach depth 15", s -> s.reached15() + "/" + s.runs(), s -> s.reached15()));
        rows.add(new Metric("bosses killed", s -> s.bossesKilled() + "/" + s.runs(), s -> s.bossesKilled()));
        rows.add(new Metric("Goo kills", s -> killsOf(s, "Goo"), s -> s.killed("Goo")));
        rows.add(new Metric("Tengu kills", s -> killsOf(s, "Tengu"), s -> s.killed("Tengu")));
        rows.add(new Metric("median turns", s -> number(s.medianTurns(), 1), s -> s.medianTurns()));
        rows.add(new Metric("deepest mean / max", s -> number(s.deepestMean(), 3) + " / " + s.deepestMax(),
                s -> s.deepestMean()));
        rows.add(new Metric("deaths by depth 1.." + depths, s -> {
            List<String> each = new ArrayList<>();
            for (int d = 1; d <= depths; d++) {
                each.add(String.valueOf(s.deathsAt(d)));
            }
            return depths == 0 ? "none" : String.join("/", each);
        }, null));
        rows.add(new Metric("searches per floor", s -> number(s.searchesPerFloor(), 2), s -> s.searchesPerFloor()));
        rows.add(new Metric("search waits per floor", s -> number(s.searchWaitsPerFloor(), 2),
                s -> s.searchWaitsPerFloor()));
        rows.add(new Metric("oscillation Steps", s -> String.valueOf(s.oscillation()), s -> s.oscillation()));
        rows.add(new Metric("heaps taken / seen", s -> s.heapsTaken() + "/" + s.heapsSeen(), s -> s.heapsTaken()));
        rows.add(new Metric("seen to taken, median waits", s -> number(s.seenToTakenMedian(), 0),
                s -> s.seenToTakenMedian()));
        rows.add(new Metric("heaps taking over " + SLOW_HEAP + " waits", s -> s.slowHeaps() + " of " + s.heapsSeen(),
                s -> s.slowHeaps()));
        rows.add(new Metric("upgrade scrolls picked up by depth " + UPGRADE_DEPTH,
                s -> String.valueOf(s.upgradesTaken()), s -> s.upgradesTaken()));
        rows.add(new Metric("upgrades read by depth " + UPGRADE_DEPTH, s -> String.valueOf(s.upgradesRead()),
                s -> s.upgradesRead()));
        rows.add(new Metric("think ms per Decision, mean / p95",
                s -> s.thinkMs().isEmpty() ? "none" : number(s.thinkMean(), 1) + " / " + number(s.thinkP95(), 0),
                s -> s.thinkMean()));
        return rows;
    }

    private static int depthsOf(Side... sides) {
        int depths = 0;
        for (Side side : sides) {
            if (side != null) {
                depths = Math.max(depths, side.deepestDeath());
            }
        }
        return depths;
    }

    /**
     * The PR table. {@code baseline} may be null, and then there are two columns instead of four.
     */
    static String table(String currentName, Side current, String baselineName, Side baseline) {
        int depths = depthsOf(current, baseline);
        StringBuilder out = new StringBuilder();
        if (baseline == null) {
            out.append("| | ").append(currentName).append(" |\n|---|---|\n");
            for (Metric m : metrics(depths)) {
                out.append("| ").append(m.label()).append(" | ").append(m.text().apply(current)).append(" |\n");
            }
        } else {
            out.append("| | ").append(baselineName).append(" | ").append(currentName).append(" | delta |\n")
                    .append("|---|---|---|---|\n");
            for (Metric m : metrics(depths)) {
                String delta = m.value() == null ? ""
                        : signed(m.value().applyAsDouble(current) - m.value().applyAsDouble(baseline));
                out.append("| ").append(m.label()).append(" | ").append(m.text().apply(baseline)).append(" | ")
                        .append(m.text().apply(current)).append(" | ").append(delta).append(" |\n");
            }
        }
        return out.toString();
    }

    /** The label of the pooled column of a {@code --class ALL} table. */
    static final String POOLED = "pooled";

    /**
     * The {@code --class ALL} table. Without a baseline it is one table, a column per class that has
     * Runs and a pooled column. With one it is a block per class and a pooled block, each the
     * four-column table of {@link #table}, since a delta per class per cell does not fit one row.
     */
    static String tableAll(String currentName, java.util.Map<org.shatterfish.api.HeroClass, Side> current,
                           String baselineName, java.util.Map<org.shatterfish.api.HeroClass, Side> baseline) {
        Side pooled = Side.pool(new ArrayList<>(current.values()));
        Side basePooled = baseline == null ? null : Side.pool(new ArrayList<>(baseline.values()));
        StringBuilder out = new StringBuilder();
        if (baseline == null) {
            int depths = depthsOf(current.values().toArray(new Side[0]));
            out.append("| |");
            for (org.shatterfish.api.HeroClass c : current.keySet()) {
                out.append(' ').append(c).append(" |");
            }
            out.append(' ').append(POOLED).append(" |\n|---|");
            for (int i = 0; i <= current.size(); i++) {
                out.append("---|");
            }
            out.append('\n');
            for (Metric m : metrics(depths)) {
                out.append("| ").append(m.label()).append(" |");
                for (Side side : current.values()) {
                    out.append(' ').append(m.text().apply(side)).append(" |");
                }
                out.append(' ').append(m.text().apply(pooled)).append(" |\n");
            }
            return out.toString();
        }
        for (var entry : current.entrySet()) {
            Side base = baseline.get(entry.getKey());
            out.append("#### ").append(entry.getKey()).append("\n\n")
                    .append(table(currentName, entry.getValue(), baselineName,
                            base == null ? Side.pool(List.of()) : base))
                    .append('\n');
        }
        out.append("#### ").append(POOLED).append("\n\n").append(table(currentName, pooled, baselineName, basePooled));
        return out.toString();
    }

    /** How many killers the deaths-by-depth table names on a floor. */
    static final int TOP_KILLERS = 4;

    /**
     * Who killed the hero, by the floor it died on: one row a depth, the deaths there and the most
     * common killers with their counts. A death with no ending record is counted under
     * {@link #UNKNOWN_KILLER}. Diagnosis only; nothing a Brain reads (E10 W1).
     */
    static String killers(List<Death> deaths) {
        if (deaths.isEmpty()) {
            return "No Run died, so there are no killers to tabulate.\n";
        }
        java.util.Map<Integer, java.util.Map<String, Integer>> byDepth = new TreeMap<>();
        for (Death death : deaths) {
            byDepth.computeIfAbsent(death.depth(), d -> new TreeMap<>()).merge(death.killer(), 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder("| depth | deaths | top killers |\n|---|---|---|\n");
        for (var entry : byDepth.entrySet()) {
            List<java.util.Map.Entry<String, Integer>> sorted = new ArrayList<>(entry.getValue().entrySet());
            sorted.sort(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(java.util.Map.Entry.comparingByKey()));
            List<String> top = new ArrayList<>();
            for (var killer : sorted.subList(0, Math.min(TOP_KILLERS, sorted.size()))) {
                top.add(killer.getKey() + " " + killer.getValue());
            }
            if (sorted.size() > TOP_KILLERS) {
                top.add("+" + (sorted.size() - TOP_KILLERS) + " more");
            }
            out.append("| ").append(entry.getKey()).append(" | ")
                    .append(entry.getValue().values().stream().mapToInt(Integer::intValue).sum()).append(" | ")
                    .append(String.join(", ", top)).append(" |\n");
        }
        return out.toString();
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
