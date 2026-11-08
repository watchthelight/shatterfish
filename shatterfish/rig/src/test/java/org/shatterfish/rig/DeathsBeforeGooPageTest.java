package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 10.42, the deaths before Goo: every table of the diagnosis half of the page is recomputed here from the two
 * committed summaries of the 520 baseline Runs ({@code runs.tsv}, every Run; {@code deaths.tsv}, the 256 that died on
 * depths 1 to 4, with what the hero carried and how its last Decisions ran), and compared with the page. The summaries
 * were read from Run logs and ending files too large to commit; a figure the files do not give fails the build.
 *
 * <p>The second half of the page, the fix measured on two slices, is {@link DeathsBeforeGooMeasureTest}'s.
 */
class DeathsBeforeGooPageTest {

    static final Path ROOT = SeedSetsTest.ROOT;

    static final Path PAGE = ROOT.resolve("docs/results/2026-11-08-10-42-the-deaths-before-goo.md");

    static final Path FOLDER = ROOT.resolve("docs/results/logs/2026-11-08-10-42-deaths-before-goo");

    static final List<String> CLASSES = List.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC");

    static List<Map<String, String>> rows(String name) throws IOException {
        List<String> lines = Files.readAllLines(FOLDER.resolve(name), StandardCharsets.UTF_8);
        String[] head = lines.get(0).split("\t", -1);
        List<Map<String, String>> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.length; i++) {
                row.put(head[i], p[i]);
            }
            out.add(row);
        }
        return out;
    }

    static int n(Map<String, String> row, String key) {
        return Integer.parseInt(row.get(key));
    }

    static String pct(int a, int b) {
        if (b == 0) {
            return "-";
        }
        int t = (a * 1000 + b / 2) / b;
        return (t / 10) + "." + (t % 10) + "%";
    }

    static String table(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder("| " + String.join(" | ", header) + " |\n");
        out.append("|").append("---|".repeat(header.size()));
        for (List<String> row : rows) {
            out.append("\n| ").append(String.join(" | ", row)).append(" |");
        }
        return out.toString();
    }

    static int count(List<Map<String, String>> rs, Predicate<Map<String, String>> p) {
        return (int) rs.stream().filter(p).count();
    }

    static int sum(List<Map<String, String>> rs, String key) {
        return rs.stream().mapToInt(r -> n(r, key)).sum();
    }

    static String sets(List<Map<String, String>> runs) {
        Map<String, List<Map<String, String>>> cols = new LinkedHashMap<>();
        cols.put("slice 1000", runs.stream().filter(r -> r.get("slice").equals("1000")).toList());
        cols.put("slice 3000", runs.stream().filter(r -> r.get("slice").equals("3000")).toList());
        cols.put("slice edc5", runs.stream().filter(r -> r.get("slice").equals("edc5")).toList());
        cols.put("pooled", runs);
        List<String> head = new ArrayList<>(List.of(""));
        head.addAll(cols.keySet());
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Runs", cols, rs -> String.valueOf(rs.size())));
        rows.add(row("reach depth 5", cols, rs -> sum(rs, "reach5") + "/" + rs.size()));
        rows.add(row("Goo kills", cols, rs -> sum(rs, "goo") + "/" + rs.size()));
        rows.add(row("Goo kills among the Runs that reach depth 5", cols, rs -> sum(rs, "goo") + "/" + sum(rs, "reach5")));
        rows.add(row("deaths on depths 1 to 4", cols, rs -> String.valueOf(count(rs, r -> n(r, "reach5") == 0))));
        return table(head, rows);
    }

    static List<String> row(String label, Map<String, List<Map<String, String>>> cols,
                            java.util.function.Function<List<Map<String, String>>, String> cell) {
        List<String> row = new ArrayList<>(List.of(label));
        cols.values().forEach(rs -> row.add(cell.apply(rs)));
        return row;
    }

    static String depths(List<Map<String, String>> ds) {
        return java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(dep -> String.valueOf(count(ds, d -> n(d, "depth") == dep)))
                .collect(Collectors.joining("/"));
    }

    static String killers(List<Map<String, String>> deaths) {
        Map<String, Integer> counts = new TreeMap<>();
        deaths.forEach(d -> counts.merge(d.get("killer"), 1, Integer::sum));
        List<String> big = counts.entrySet().stream().filter(e -> e.getValue() >= 5)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        List<List<String>> rows = new ArrayList<>();
        for (String killer : big) {
            List<Map<String, String>> ds = deaths.stream().filter(d -> d.get("killer").equals(killer)).toList();
            rows.add(killerRow(killer, ds, deaths.size()));
        }
        List<Map<String, String>> rest = deaths.stream().filter(d -> counts.get(d.get("killer")) < 5).toList();
        long kinds = rest.stream().map(d -> d.get("killer")).distinct().count();
        rows.add(killerRow("other (" + kinds + " kinds)", rest, deaths.size()));
        List<String> all = killerRow("all", deaths, deaths.size());
        rows.add(all);
        return table(List.of("killer", "depth 1", "depth 2", "depth 3", "depth 4", "deaths", "share"), rows);
    }

    static List<String> killerRow(String label, List<Map<String, String>> ds, int total) {
        List<String> row = new ArrayList<>(List.of(label));
        for (int dep = 1; dep <= 4; dep++) {
            int d = dep;
            row.add(String.valueOf(count(ds, r -> n(r, "depth") == d)));
        }
        row.add(String.valueOf(ds.size()));
        row.add(ds.size() == total ? "100.0%" : pct(ds.size(), total));
        return row;
    }

    static String classes(List<Map<String, String>> runs, List<Map<String, String>> deaths) {
        List<String> head = new ArrayList<>(List.of(""));
        head.addAll(CLASSES);
        head.add("pooled");
        List<List<Map<String, String>>> rs = new ArrayList<>();
        List<List<Map<String, String>>> ds = new ArrayList<>();
        for (String c : CLASSES) {
            rs.add(runs.stream().filter(r -> r.get("class").equals(c)).toList());
            ds.add(deaths.stream().filter(r -> r.get("class").equals(c)).toList());
        }
        rs.add(runs);
        ds.add(deaths);
        List<List<String>> rows = new ArrayList<>();
        rows.add(per("Runs", rs, ds, (r, d) -> String.valueOf(r.size())));
        rows.add(per("deaths on depths 1 to 4", rs, ds, (r, d) -> String.valueOf(d.size())));
        rows.add(per("share of the Runs", rs, ds, (r, d) -> pct(d.size(), r.size())));
        rows.add(per("by depth 1/2/3/4", rs, ds, (r, d) -> depths(d)));
        rows.add(per("to the Crab", rs, ds, (r, d) -> String.valueOf(count(d, x -> x.get("killer").equals("Crab")))));
        return table(head, rows);
    }

    static List<String> per(String label, List<List<Map<String, String>>> rs, List<List<Map<String, String>>> ds,
                            java.util.function.BiFunction<List<Map<String, String>>, List<Map<String, String>>, String> cell) {
        List<String> row = new ArrayList<>(List.of(label));
        for (int i = 0; i < rs.size(); i++) {
            row.add(cell.apply(rs.get(i), ds.get(i)));
        }
        return row;
    }

    static String carried(List<Map<String, String>> deaths) {
        int total = deaths.size();
        List<List<String>> rows = new ArrayList<>();
        rows.add(share("an unknown potion", deaths, d -> n(d, "unknown_potions") > 0));
        rows.add(share("an unknown scroll", deaths, d -> n(d, "unknown_scrolls") > 0));
        rows.add(share("a known potion of healing", deaths, d -> n(d, "heal_known") > 0));
        rows.add(share("a throwing weapon", deaths, d -> n(d, "missiles") > 0));
        rows.add(share("none of the four", deaths, d -> n(d, "unknown_potions") == 0 && n(d, "unknown_scrolls") == 0
                && n(d, "heal_known") == 0 && n(d, "missiles") == 0));
        return table(List.of("in the pack on arrival at the floor the hero died on", "deaths", "share of " + total), rows);
    }

    static List<String> share(String label, List<Map<String, String>> deaths, Predicate<Map<String, String>> p) {
        int k = count(deaths, p);
        return List.of(label, String.valueOf(k), pct(k, deaths.size()));
    }

    static String causes(List<Map<String, String>> deaths) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(cause("the first Decision of the fatal fight was at under a third of health", deaths, d -> n(d, "low_at_contact") == 1));
        rows.add(cause("10 or more of the 40 Decisions before it were under a third of health with no enemy in view", deaths,
                d -> n(d, "limp_before") >= 10));
        rows.add(cause("starving on the last Decision", deaths, d -> n(d, "starving_end") == 1));
        rows.add(cause("hungry or starving on the last Decision", deaths, d -> n(d, "hungry_end") == 1));
        rows.add(cause("waited (`hold: no-way`) for an enemy it could not walk away from", deaths, d -> n(d, "holds") > 0));
        rows.add(cause("of them, with a throwing weapon in the pack and none thrown", deaths,
                d -> n(d, "holds") > 0 && n(d, "missiles") > 0 && n(d, "throws") == 0));
        rows.add(cause("of them, with a throwing weapon in the pack and at least one thrown", deaths,
                d -> n(d, "holds") > 0 && n(d, "missiles") > 0 && n(d, "throws") > 0));
        rows.add(cause("of them, with nothing to throw", deaths, d -> n(d, "holds") > 0 && n(d, "missiles") == 0));
        rows.add(cause("with a throwing weapon in the pack and none thrown in the fatal fight, held or not", deaths,
                d -> n(d, "missiles") > 0 && n(d, "throws") == 0));
        rows.add(cause("went back and forth between a retreat and another Policy 4 times or more", deaths, d -> n(d, "flips") >= 4));
        rows.add(cause("died holding an unknown potion", deaths, d -> n(d, "unknown_potions") > 0));
        return table(List.of("cause, counted on the " + deaths.size() + " deaths (they overlap)", "deaths", "share", "killers (top three)"), rows);
    }

    static List<String> cause(String label, List<Map<String, String>> deaths, Predicate<Map<String, String>> p) {
        List<Map<String, String>> ds = deaths.stream().filter(p).toList();
        Map<String, Integer> counts = new TreeMap<>();
        ds.forEach(d -> counts.merge(d.get("killer"), 1, Integer::sum));
        String top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3).map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        return List.of(label, String.valueOf(ds.size()), pct(ds.size(), deaths.size()), top);
    }

    @Test
    @DisplayName("every table of the diagnosis is what the committed summaries give")
    void the_tables_are_the_logs() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);
        List<Map<String, String>> runs = rows("runs.tsv");
        List<Map<String, String>> deaths = rows("deaths.tsv");
        assertEquals(count(runs, r -> n(r, "reach5") == 0), deaths.size(), "a death row for every Run that did not reach depth 5");
        for (Map<String, String> d : deaths) {
            assertTrue(n(d, "depth") >= 1 && n(d, "depth") <= 4, d.toString());
        }
        assertTrue(page.contains(sets(runs)), "the Run sets table");
        assertTrue(page.contains(killers(deaths)), "the killers table");
        assertTrue(page.contains(classes(runs, deaths)), "the classes table");
        assertTrue(page.contains(carried(deaths)), "the carried table");
        assertTrue(page.contains(causes(deaths)), "the causes table");
    }

    @Test
    @DisplayName("the page names the committed files it reads and every one of them exists")
    void the_paths_exist() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("`(docs/results/logs/[^`]+)`").matcher(page);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(Files.exists(ROOT.resolve(m.group(1))), m.group(1));
        }
        assertTrue(found >= 2, "the page names the files");
    }
}
