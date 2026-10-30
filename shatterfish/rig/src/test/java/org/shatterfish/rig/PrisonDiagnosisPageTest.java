package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 10.7, the Prison diagnosis: every table of the page is recomputed here from the committed summary of the 960
 * Runs ({@code runs.tsv}, {@code arrivals.tsv}, {@code moments.tsv}, written by {@code tools/prison-diagnosis.py}
 * from the 960 full logs, which are too large to commit) and compared with the page, so a figure on the page that the
 * files do not give fails the build. The arithmetic here is written again, not shared with the Python tool: integer
 * percentages and means, and a 40-digit decimal model for the reach figures.
 */
class PrisonDiagnosisPageTest {

    static final Path ROOT = SeedSetsTest.ROOT;

    static final Path PAGE = ROOT.resolve("docs/results/2026-10-30-10-7-the-prison-diagnosis.md");

    static final Path FOLDER = ROOT.resolve("docs/results/logs/2026-10-30-10-7-prison-diagnosis");

    static final List<String> CLASSES = List.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC");

    static final MathContext MC = new MathContext(40, RoundingMode.HALF_EVEN);

    /** The families of the page's last table, in the order the tie-break uses: story, label, killers. */
    record Family(String story, String label, List<String> killers) {
    }

    static final List<Family> FAMILIES = List.of(
            new Family("10.8", "Skeleton bone burst", List.of("Skeleton")),
            new Family("10.9", "DM-100 lightning", List.of("DM100")),
            new Family("10.10", "Guard chain pull", List.of("Guard")),
            new Family("10.11", "Thief and Bandit steal", List.of("Thief", "Bandit")),
            new Family("10.12", "Necromancer priority", List.of("Necromancer", "Necromancer$NecroSkeleton")),
            new Family("10.13", "Swarm split", List.of("Swarm")),
            new Family("10.15", "Prison food budgeting", List.of("Hunger")));

    static final Family GOO = new Family("(none open)", "Goo on depth 5", List.of("Goo"));

    // ------------------------------------------------------------------------------------------------ reading

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

    // ------------------------------------------------------------------------------------------------ rendering

    static String table(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder("| " + String.join(" | ", header) + " |\n");
        out.append("|").append("---|".repeat(header.size()));
        for (List<String> row : rows) {
            out.append("\n| ").append(String.join(" | ", row)).append(" |");
        }
        return out.toString();
    }

    static String pct(int n, int d) {
        if (d == 0) {
            return "-";
        }
        int t = (n * 1000 + d / 2) / d;
        return (t / 10) + "." + (t % 10) + "%";
    }

    static String mean1(int total, int n) {
        if (n == 0) {
            return "-";
        }
        int t = (total * 20 + n) / (2 * n);
        return (t / 10) + "." + (t % 10);
    }

    /** The column groups of the page's tables: one per class, then the pooled one. */
    static Map<String, List<Map<String, String>>> columns(List<Map<String, String>> runs) {
        Map<String, List<Map<String, String>>> cols = new LinkedHashMap<>();
        for (String c : CLASSES) {
            cols.put(c, runs.stream().filter(r -> r.get("class").equals(c)).toList());
        }
        cols.put("pooled", runs);
        return cols;
    }

    static List<Map<String, String>> deaths(List<Map<String, String>> rs, int lo, int hi) {
        return rs.stream().filter(r -> r.get("cause").equals("DEATH") && n(r, "depth") >= lo && n(r, "depth") <= hi).toList();
    }

    static long count(List<Map<String, String>> rs, java.util.function.Predicate<Map<String, String>> p) {
        return rs.stream().filter(p).count();
    }

    static String sum(List<Map<String, String>> rs, String key) {
        return String.valueOf(rs.stream().mapToInt(r -> n(r, key)).sum());
    }

    static List<String> head(Map<String, ?> cols) {
        List<String> h = new ArrayList<>();
        h.add("");
        h.addAll(cols.keySet());
        return h;
    }

    static String reach(List<Map<String, String>> runs) {
        var cols = columns(runs);
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("Runs", cols, rs -> String.valueOf(rs.size())));
        rows.add(row("reach depth 5", cols, rs -> sum(rs, "reach5") + "/" + rs.size()));
        rows.add(row("reach depth 10", cols, rs -> sum(rs, "reach10") + "/" + rs.size()));
        rows.add(row("Goo kills", cols, rs -> sum(rs, "goo_kill") + "/" + rs.size()));
        rows.add(row("deaths on depth 5", cols, rs -> String.valueOf(deaths(rs, 5, 5).size())));
        rows.add(row("of them to Goo", cols, rs -> String.valueOf(count(deaths(rs, 5, 5), r -> r.get("killer").equals("Goo")))));
        rows.add(row("deaths on depths 1 to 4", cols, rs -> String.valueOf(deaths(rs, 1, 4).size())));
        rows.add(row("deaths on depths 6 to 9", cols, rs -> String.valueOf(deaths(rs, 6, 9).size())));
        rows.add(row("deaths on depth 10 or deeper", cols, rs -> String.valueOf(deaths(rs, 10, 99).size())));
        return table(head(cols), rows);
    }

    static List<String> row(String label, Map<String, List<Map<String, String>>> cols,
                            java.util.function.Function<List<Map<String, String>>, String> cell) {
        List<String> row = new ArrayList<>();
        row.add(label);
        cols.values().forEach(rs -> row.add(cell.apply(rs)));
        return row;
    }

    static String share(List<Map<String, String>> base, java.util.function.Predicate<Map<String, String>> p) {
        int k = (int) count(base, p);
        return k + " (" + pct(k, base.size()) + ")";
    }

    static String hunger(List<Map<String, String>> runs) {
        var cols = columns(runs);
        List<List<String>> rows = new ArrayList<>();
        java.util.function.Predicate<Map<String, String>> starving = r -> r.get("hunger_end").equals("STARVING");
        java.util.function.Predicate<Map<String, String>> hungry = r -> r.get("hunger_end").equals("HUNGRY");
        java.util.function.Predicate<Map<String, String>> killer = r -> r.get("killer").equals("Hunger");
        rows.add(row("deaths", cols, rs -> String.valueOf(deaths(rs, 1, 99).size())));
        rows.add(row("killer is Hunger", cols, rs -> share(deaths(rs, 1, 99), killer)));
        rows.add(row("STARVING on the last Decision", cols, rs -> share(deaths(rs, 1, 99), starving)));
        rows.add(row("HUNGRY on the last Decision", cols, rs -> share(deaths(rs, 1, 99), hungry)));
        rows.add(row("deaths on depths 6 to 9", cols, rs -> String.valueOf(deaths(rs, 6, 9).size())));
        rows.add(row("of them killer Hunger", cols, rs -> share(deaths(rs, 6, 9), killer)));
        rows.add(row("of them STARVING on the last Decision", cols, rs -> share(deaths(rs, 6, 9), starving)));
        rows.add(row("of them HUNGRY on the last Decision", cols, rs -> share(deaths(rs, 6, 9), hungry)));
        return table(head(cols), rows);
    }

    static String killerList(List<Map<String, String>> ds) {
        Map<String, Integer> counts = new TreeMap<>();
        ds.forEach(r -> counts.merge(r.get("killer"), 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
    }

    /** One block per class and the pooled one: "#### NAME" and its table. */
    static Map<String, String> killers(List<Map<String, String>> runs) {
        Map<String, String> out = new LinkedHashMap<>();
        columns(runs).forEach((name, rs) -> {
            List<List<String>> rows = new ArrayList<>();
            for (int depth = 1; depth < 40; depth++) {
                var ds = deaths(rs, depth, depth);
                if (!ds.isEmpty()) {
                    rows.add(List.of(String.valueOf(depth), String.valueOf(ds.size()), killerList(ds)));
                }
            }
            out.put(name, table(List.of("depth", "deaths", "killers"), rows));
        });
        return out;
    }

    static String arrivals(List<Map<String, String>> arrivals) {
        List<List<String>> rows = new ArrayList<>();
        List<String> names = new ArrayList<>(CLASSES);
        names.add("pooled");
        for (String c : names) {
            List<String> cells = new ArrayList<>();
            cells.add(c);
            for (int depth = 1; depth <= 9; depth++) {
                final int d = depth;
                var a = arrivals.stream().filter(x -> (c.equals("pooled") || x.get("class").equals(c)) && n(x, "depth") == d).toList();
                cells.add(a.isEmpty() ? "-" : mean1(a.stream().mapToInt(x -> n(x, "level")).sum(), a.size()) + " / "
                        + mean1(a.stream().mapToInt(x -> n(x, "strength")).sum(), a.size()) + " (" + a.size() + ")");
            }
            rows.add(cells);
        }
        List<String> header = new ArrayList<>(List.of("class"));
        for (int i = 1; i <= 9; i++) {
            header.add("d" + i);
        }
        return table(header, rows);
    }

    static Map<String, String> moments(List<Map<String, String>> moments) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String c : CLASSES) {
            List<List<String>> rows = new ArrayList<>();
            for (var m : moments) {
                if (m.get("class").equals(c)) {
                    rows.add(List.of(m.get("rank"), m.get("seedcode") + "/" + m.get("salt"), m.get("depth"),
                            m.get("hp") + "/" + m.get("ht"), m.get("loss"), m.get("policy"), m.get("why").replace("|", "/"),
                            m.get("enemies").isEmpty() ? "-" : m.get("enemies")));
                }
            }
            out.put(c, table(List.of("rank", "seed/salt", "depth", "hp", "lost", "policy", "why", "enemies in view"), rows));
        }
        return out;
    }

    /** Expected Runs more reaching depth 8 when the killers never killed on depths 5 to 7, for one class. */
    static BigDecimal gain(List<Map<String, String>> runs, List<Map<String, String>> arrivals, List<String> killers) {
        Map<Integer, Integer> a = new LinkedHashMap<>();
        for (int d = 5; d <= 8; d++) {
            final int depth = d;
            a.put(d, (int) arrivals.stream().filter(x -> n(x, "depth") == depth).map(x -> x.get("salt")).distinct().count());
        }
        BigDecimal with = BigDecimal.valueOf(a.get(5));
        BigDecimal without = BigDecimal.valueOf(a.get(5));
        for (int d = 5; d <= 7; d++) {
            int deaths = deaths(runs, d, d).size();
            int family = (int) count(deaths(runs, d, d), r -> killers.contains(r.get("killer")));
            if (a.get(d) == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal arrived = BigDecimal.valueOf(a.get(d));
            with = with.multiply(BigDecimal.valueOf(a.get(d) - deaths + family), MC).divide(arrived, MC);
            without = without.multiply(BigDecimal.valueOf(a.get(d) - deaths), MC).divide(arrived, MC);
        }
        return with.subtract(without, MC);
    }

    static String gain1(BigDecimal x) {
        int t = x.multiply(BigDecimal.TEN, MC).setScale(0, RoundingMode.HALF_UP).intValueExact();
        return "+" + (t / 10) + "." + (t % 10);
    }

    static String stories(List<Map<String, String>> runs, List<Map<String, String>> arrivals) {
        record Row(Family family, List<BigDecimal> per, List<Integer> deaths) {
            BigDecimal total() {
                BigDecimal t = BigDecimal.ZERO;
                for (BigDecimal g : per) {
                    t = t.add(g, MC);
                }
                return t;
            }
        }
        List<Family> all = new ArrayList<>(FAMILIES);
        all.add(GOO);
        List<Row> rows = new ArrayList<>();
        for (Family f : all) {
            List<BigDecimal> per = new ArrayList<>();
            List<Integer> ds = new ArrayList<>();
            for (String c : CLASSES) {
                var rs = runs.stream().filter(r -> r.get("class").equals(c)).toList();
                var ar = arrivals.stream().filter(x -> x.get("class").equals(c)).toList();
                per.add(gain(rs, ar, f.killers()));
                ds.add((int) count(deaths(rs, 5, 9), r -> f.killers().contains(r.get("killer"))));
            }
            rows.add(new Row(f, per, ds));
        }
        rows.sort(Comparator.comparing(Row::total).reversed());
        List<String> header = new ArrayList<>(List.of("story", "family", "killers counted"));
        CLASSES.forEach(c -> header.add("deaths " + c));
        header.add("deaths pooled");
        CLASSES.forEach(c -> header.add("+d8 " + c));
        header.add("+d8 pooled");
        List<List<String>> body = new ArrayList<>();
        for (Row r : rows) {
            List<String> cells = new ArrayList<>(List.of(r.family().story(), r.family().label(), String.join(", ", r.family().killers())));
            r.deaths().forEach(d -> cells.add(String.valueOf(d)));
            cells.add(String.valueOf(r.deaths().stream().mapToInt(Integer::intValue).sum()));
            r.per().forEach(g -> cells.add(gain1(g)));
            cells.add(gain1(r.total()));
            body.add(cells);
        }
        return table(header, body);
    }

    static String story(String killer) {
        return FAMILIES.stream().filter(f -> f.killers().contains(killer)).map(Family::story).findFirst().orElse("-");
    }

    static String killerShare(List<Map<String, String>> runs) {
        var ds = deaths(runs, 1, 99);
        Map<String, Integer> counts = new TreeMap<>();
        ds.forEach(r -> counts.merge(r.get("killer"), 1, Integer::sum));
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();
        List<List<String>> rows = new ArrayList<>();
        for (var e : sorted) {
            var mine = ds.stream().filter(r -> r.get("killer").equals(e.getKey())).toList();
            rows.add(List.of(e.getKey(), String.valueOf(deaths(mine, 1, 4).size()), String.valueOf(deaths(mine, 5, 5).size()),
                    String.valueOf(deaths(mine, 6, 9).size()), String.valueOf(e.getValue()), pct(e.getValue(), ds.size()),
                    story(e.getKey())));
        }
        return table(List.of("killer", "depths 1 to 4", "depth 5", "depths 6 to 9", "deaths", "share of all deaths", "story"), rows);
    }

    static String goo(List<Map<String, String>> runs) {
        var cols = columns(runs);
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("deaths to Goo", cols, rs -> String.valueOf(count(deaths(rs, 1, 99), r -> r.get("killer").equals("Goo")))));
        for (String state : List.of("NONE", "HUNGRY", "STARVING")) {
            rows.add(row("of them hunger " + state + " on the last Decision", cols, rs -> {
                var ds = deaths(rs, 1, 99).stream().filter(r -> r.get("killer").equals("Goo")).toList();
                int k = (int) count(ds, r -> r.get("hunger_end").equals(state));
                return k + " (" + pct(k, ds.size()) + ")";
            }));
        }
        return table(head(cols), rows);
    }

    static String cornered(List<Map<String, String>> runs) {
        var cols = columns(runs);
        List<List<String>> rows = new ArrayList<>();
        int[][] spans = {{6, 9}, {1, 9}};
        String[] labels = {"deaths on depths 6 to 9", "deaths on depths 1 to 9"};
        for (int i = 0; i < 2; i++) {
            final int lo = spans[i][0];
            final int hi = spans[i][1];
            rows.add(row(labels[i] + ", last Decision says cornered", cols, rs -> {
                var ds = deaths(rs, lo, hi);
                int k = (int) count(ds, r -> r.get("cornered_last").equals("1"));
                return k + " of " + ds.size() + " (" + pct(k, ds.size()) + ")";
            }));
        }
        return table(head(cols), rows);
    }

    // ------------------------------------------------------------------------------------------------ the tests

    static String page() throws IOException {
        return Files.readString(PAGE, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("the summary is the 960 Runs of the two slices: 80 per class per salt base, seeds the first 80 standard entries of the class")
    void the_summary_is_the_two_slices() throws IOException {
        var runs = rows("runs.tsv");
        assertEquals(960, runs.size());
        String set = Files.readString(ROOT.resolve("seeds/standard.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"heroClass\":\"(\\w+)\",\"seed\":\\d+,\"seedCode\":\"([A-Z-]+)\"").matcher(set);
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        while (m.find()) {
            byClass.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(m.group(2));
        }
        for (String c : CLASSES) {
            for (int base : new int[] {1000, 3000}) {
                for (int i = 0; i < 80; i++) {
                    final int salt = base + i;
                    var hit = runs.stream().filter(r -> r.get("class").equals(c) && n(r, "salt") == salt).toList();
                    assertEquals(1, hit.size(), c + " salt " + salt + " is played once");
                    assertEquals(byClass.get(c).get(i), hit.get(0).get("seedcode"),
                            c + " salt " + salt + " plays entry " + i + " of the class in the standard set");
                }
            }
        }
        assertTrue(runs.stream().allMatch(r -> r.get("cause").equals("DEATH")),
                "every Run in the slices ended in a death; a Run that did not would need a column of its own");
    }

    @Test
    @DisplayName("the reach, hunger, killer, arrival, moment, killer-share and cornered tables on the page are the ones the summary gives")
    void the_page_tables_are_the_summary() throws IOException {
        var runs = rows("runs.tsv");
        var arrivals = rows("arrivals.tsv");
        var moments = rows("moments.tsv");
        String page = page();
        assertTrue(page.contains(reach(runs)), "the reach table drifted from runs.tsv");
        assertTrue(page.contains(hunger(runs)), "the hunger table drifted from runs.tsv");
        killers(runs).forEach((name, table) -> assertTrue(page.contains("#### " + name + "\n\n" + table),
                "the killer table of " + name + " drifted from runs.tsv"));
        assertTrue(page.contains(arrivals(arrivals)), "the level and strength table drifted from arrivals.tsv");
        moments(moments).forEach((name, table) -> assertTrue(page.contains("#### " + name + "\n\n" + table),
                "the expensive moments of " + name + " drifted from moments.tsv"));
        assertTrue(page.contains(killerShare(runs)), "the killer-share table drifted from runs.tsv");
        assertTrue(page.contains(cornered(runs)), "the cornered table drifted from runs.tsv");
        assertTrue(page.contains(goo(runs)), "the Goo table drifted from runs.tsv");
    }

    @Test
    @DisplayName("the page ends with the ordered list of the mob stories, ordered by the reach the model gives, and that table is the last one")
    void the_page_ends_with_the_order() throws IOException {
        var runs = rows("runs.tsv");
        var arrivals = rows("arrivals.tsv");
        String page = page();
        String stories = stories(runs, arrivals);
        int at = page.indexOf(stories);
        assertTrue(at > 0, "the stories table drifted from runs.tsv and arrivals.tsv");
        assertFalse(page.substring(at + stories.length()).contains("\n|"), "no table follows the stories table");
        assertEquals("## The order of the mob stories", lastHeading(page, at), "the stories table sits under the last section");
        for (String story : List.of("10.8", "10.9", "10.10", "10.11", "10.12", "10.13", "10.14", "10.15", "10.16")) {
            assertTrue(page.substring(at).contains("Story " + story), "the closing list names story " + story);
        }
    }

    static String lastHeading(String page, int upTo) {
        String last = "";
        for (String line : page.substring(0, upTo).split("\n")) {
            if (line.startsWith("## ")) {
                last = line;
            }
        }
        return last;
    }

    @Test
    @DisplayName("every class has 20 moments, in falling order of HP lost, each with the Policy that chose it")
    void the_moments_are_twenty_and_sorted() throws IOException {
        var moments = rows("moments.tsv");
        for (String c : CLASSES) {
            var mine = moments.stream().filter(m -> m.get("class").equals(c)).toList();
            assertEquals(20, mine.size(), c);
            for (int i = 0; i < mine.size(); i++) {
                assertEquals(String.valueOf(i + 1), mine.get(i).get("rank"));
                assertFalse(mine.get(i).get("policy").isBlank(), c + " moment " + (i + 1) + " names its Policy");
                if (i > 0) {
                    assertTrue(n(mine.get(i - 1), "loss") >= n(mine.get(i), "loss"), c + " moments fall in cost");
                }
            }
        }
    }

    @Test
    @DisplayName("control: a planted change to one Run's killer changes the rendered tables, so the page comparison discriminates")
    void a_planted_change_is_seen() throws IOException {
        var runs = rows("runs.tsv");
        var arrivals = rows("arrivals.tsv");
        List<Map<String, String>> planted = new ArrayList<>();
        boolean done = false;
        for (var r : runs) {
            Map<String, String> copy = new LinkedHashMap<>(r);
            if (!done && copy.get("killer").equals("Skeleton")) {
                copy.put("killer", "Hunger");
                copy.put("hunger_end", "STARVING");
                done = true;
            }
            planted.add(copy);
        }
        assertTrue(done, "the summary holds a Skeleton death");
        assertNotEquals(hunger(runs), hunger(planted));
        assertNotEquals(killerShare(runs), killerShare(planted));
        assertNotEquals(stories(runs, arrivals), stories(planted, arrivals));
        assertFalse(page().contains(killerShare(planted)), "the page does not hold the planted table");
    }
}
