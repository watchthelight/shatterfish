package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 10.37, the first tuning run: every figure of the page is recomputed from the committed files it names and
 * compared with the page. The trajectory is the tuner's own ({@code trajectory.jsonl}); the two slices are the
 * {@code runs.tsv} that {@code :rig:confirm} wrote from the Run logs, which are too large to commit. The pairing and
 * the Sequential test are {@link TuneConfirm}'s own, so a verdict on the page that the files do not give fails the
 * build.
 */
class TuneRunPageTest {

    static final Path ROOT = SeedSetsTest.ROOT;

    static final Path PAGE = ROOT.resolve("docs/results/2026-11-07-10-37-the-first-tuning-run.md");

    static final Path FOLDER = ROOT.resolve("docs/results/logs/2026-11-07-10-37-first-tuning-run");

    /** The configuration of the run, as the page states it (the run's {@code tune.json} did not survive; see the page). */
    static final int FIRST = 8;
    static final int CLASSES = 6;
    static final int ITERATIONS = 41;
    static final long SALT_BASE = 5000;

    static String page() throws IOException {
        return Files.readString(PAGE, StandardCharsets.UTF_8);
    }

    static String f(double value, int places) {
        // The exact binary value, ties to even: what the page's generator printed.
        return new java.math.BigDecimal(value).setScale(places, java.math.RoundingMode.HALF_EVEN).toPlainString();
    }

    static double d(Map<String, String> row, String key) {
        return Double.parseDouble(row.get(key));
    }

    @Test
    @DisplayName("the trajectory is whole, chained, and inside the tuner's salts")
    void trajectory_is_whole() throws IOException {
        List<Map<String, String>> rows = Tune.trajectory(FOLDER);
        assertEquals(ITERATIONS, rows.size());
        for (int k = 0; k < rows.size(); k++) {
            Map<String, String> row = rows.get(k);
            assertEquals(k, Integer.parseInt(row.get("iteration")));
            assertEquals(d(row, "plusScore") - d(row, "minusScore"), d(row, "diff"), 1e-9);
            if (k > 0) {
                assertEquals(rows.get(k - 1).get("next"), row.get("theta"), "iteration " + k + " starts where " + (k - 1) + " ended");
            }
        }
        long last = SALT_BASE + ((long) (ITERATIONS - 1) * CLASSES + (CLASSES - 1)) * FIRST + (FIRST - 1);
        assertTrue(SALT_BASE >= Tune.SALT_FLOOR && last < Tune.SALT_CEILING, "salts " + SALT_BASE + " to " + last);
        assertTrue(page().contains("salts 5000 to " + last), "the page names the salts the run played: " + last);
    }

    @Test
    @DisplayName("the trajectory table and its summary are the file's")
    void trajectory_table_matches() throws IOException {
        String page = page();
        List<Map<String, String>> rows = Tune.trajectory(FOLDER);
        double sum = 0;
        int positive = 0;
        for (Map<String, String> row : rows) {
            sum += d(row, "diff");
            positive += d(row, "diff") > 0 ? 1 : 0;
            String line = "| " + row.get("iteration") + " | " + f(d(row, "plusScore"), 3) + " | " + f(d(row, "minusScore"), 3)
                    + " | " + signed(d(row, "diff"), 3) + " | " + f(d(row, "se"), 3) + " |";
            assertTrue(page.contains(line + "\n"), "the page has the row " + line);
        }
        assertTrue(page.contains("Mean paired difference " + signed(sum / rows.size(), 3) + " over " + rows.size()
                + " iterations; " + positive + " positive."), "the summary line");
    }

    static String signed(double value, int places) {
        String text = f(value, places);
        return value > 0 && !text.startsWith("-") ? "+" + text : text;
    }

    @Test
    @DisplayName("the candidate table is the final state, rounded and clamped, and moves nothing outside a bound")
    void candidate_table_matches() throws IOException {
        String page = page();
        List<Map<String, String>> rows = Tune.trajectory(FOLDER);
        Map<String, String> next = Json.object(rows.get(rows.size() - 1).get("next"));
        // The registry as the run saw it (41 numbers); numbers registered since are not in the run.
        List<Tunables.Tunable> registry = next.keySet().stream().map(Tunables::named).toList();
        assertEquals(41, registry.size());
        assertFalse(registry.contains(null));
        int moved = 0;
        for (Tunables.Tunable t : registry) {
            long value = Math.max(t.min(), Math.min(t.max(), Math.round(Double.parseDouble(next.get(t.name())))));
            moved += value != t.def() ? 1 : 0;
            String line = "| `" + t.name() + "` | " + t.def() + " | " + value + " | " + signed((value - t.def()) / (double) t.step(), 2) + " |";
            assertTrue(page.contains(line + "\n"), "the page has the row " + line);
        }
        assertTrue(page.contains(moved + " of " + registry.size() + " numbers moved."), "the moved count: " + moved);
        String diff = read(FOLDER.resolve("candidate.diff.txt"));
        for (Tunables.Tunable t : registry) {
            long value = Math.max(t.min(), Math.min(t.max(), Math.round(Double.parseDouble(next.get(t.name())))));
            if (value != t.def()) {
                assertTrue(diff.contains(t.name() + "=" + value), "the --tunables text states " + t.name() + "=" + value);
            }
        }
    }

    static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** One slice's verdict rows as the page prints them. */
    static List<String> verdictRows(Path runs) {
        List<TuneConfirm.Verdict> verdicts = TuneConfirm.verdicts(TuneConfirm.fromTable(runs, "candidate"),
                TuneConfirm.fromTable(runs, "current"));
        List<String> out = new ArrayList<>();
        for (TuneConfirm.Verdict v : verdicts) {
            out.add("| " + v.group() + " | " + v.pairs() + " | " + v.missing() + " | " + v.worse() + " | " + v.equal() + " | "
                    + v.better() + " | " + v.verdict() + " | " + v.consumed() + " | " + v.bossesCandidate() + " | "
                    + v.bossesCurrent() + " | " + (!v.group().equals("POOLED") && TuneConfirm.vetoed(v) ? "veto" : "no") + " |");
        }
        return out;
    }

    /** One slice's think-time rows: mean milliseconds per Decision, current then candidate, per class. */
    static List<String> thinkRows(Path runs) {
        List<String> out = new ArrayList<>();
        List<TuneConfirm.Run> cur = TuneConfirm.fromTable(runs, "current");
        List<TuneConfirm.Run> cand = TuneConfirm.fromTable(runs, "candidate");
        for (String hero : TuneConfirm.CLASSES) {
            out.add("| " + hero + " | " + think(cur, hero) + " | " + think(cand, hero) + " |");
        }
        out.add("| POOLED | " + think(cur, null) + " | " + think(cand, null) + " |");
        return out;
    }

    private static String think(List<TuneConfirm.Run> runs, String hero) {
        long ms = 0;
        long decisions = 0;
        for (TuneConfirm.Run r : runs) {
            if (hero == null || r.heroClass().equals(hero)) {
                ms += r.thinkMs();
                decisions += r.decisions();
            }
        }
        return f(ms / (double) decisions, 1);
    }

    @Test
    @DisplayName("each slice's verdict table and think-time table are recomputed from its Run summary")
    void slices_match() throws IOException {
        String page = page();
        for (String slice : List.of("1000", "3000")) {
            Path runs = FOLDER.resolve("slice-" + slice).resolve("runs.tsv");
            List<String> verdicts = verdictRows(runs);
            assertEquals(1 + CLASSES, verdicts.size());
            for (String row : verdicts) {
                String line = "| " + slice + " |" + row.substring(1);
                assertTrue(page.contains(line + "\n"), "the page has, for slice " + slice + ": " + line);
            }
            for (String row : thinkRows(runs)) {
                String line = "| " + slice + " |" + row.substring(1);
                assertTrue(page.contains(line + "\n"), "the page has, for slice " + slice + ": " + line);
            }
            // 40 of each class on each side, so the slice is the one the page says.
            for (String side : List.of("current", "candidate")) {
                assertEquals(CLASSES * 40, TuneConfirm.fromTable(runs, side).size(), side + " Runs on slice " + slice);
            }
        }
    }

    @Test
    @DisplayName("the verdict is what the keep rule says of the two slices")
    void verdict_follows_the_rule() throws IOException {
        String page = page();
        boolean kept = true;
        for (String slice : List.of("1000", "3000")) {
            Path runs = FOLDER.resolve("slice-" + slice).resolve("runs.tsv");
            kept &= TuneConfirm.kept(TuneConfirm.verdicts(TuneConfirm.fromTable(runs, "candidate"),
                    TuneConfirm.fromTable(runs, "current")));
        }
        assertTrue(page.contains("Verdict: " + (kept ? "KEPT" : "REJECTED") + "."), "the verdict line");
        assertFalse(page.contains("Verdict: " + (kept ? "REJECTED" : "KEPT") + "."), "one verdict only");
    }

    @Test
    @DisplayName("every log the page names is committed, and the page names only the three sets the tuner may play")
    void named_logs_exist() throws IOException {
        String page = page();
        Matcher m = Pattern.compile("`(docs/results/logs/[^`]+)`").matcher(page);
        int named = 0;
        while (m.find()) {
            named++;
            assertTrue(Files.exists(ROOT.resolve(m.group(1))), m.group(1) + " is committed");
        }
        assertTrue(named >= 5, "the page names its logs: " + named);
        assertFalse(page.contains("--set goo") || page.contains("--set holdout") || page.contains("--set tengu"));
    }
}
