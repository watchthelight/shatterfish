package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.api.Tunables;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tune runner (story 10.36): the loop, its trajectory, its resume, its pairing and its candidate. The
 * arithmetic is {@link SpsaTest}'s and the refusals are {@link TuneRefusalTest}'s. Nothing here plays the game:
 * a Run is a fixture log or a planted objective, because the tuner's own claims are about what it does with
 * scores, and a Run's reproducibility is the harness's (the reference Run, the replay tests).
 */
class TuneRunnerTest {

    private static final Path ROOT = SeedSetsTest.ROOT;

    private static final String ZERO = "0".repeat(64);

    private static final List<String> NAMES = List.of("fight.margin_per_mille", "identify.reach", "descend.rests");

    private static Tune.Config config(int iterations) {
        double[] start = new double[NAMES.size()];
        for (int i = 0; i < start.length; i++) {
            start[i] = Tunables.named(NAMES.get(i)).def();
        }
        return new Tune.Config("smoke", List.of(HeroClass.WARRIOR), 3, 5000, iterations, NAMES, start, Spsa.Config.of(iterations));
    }

    /** A planted objective: a concave bowl with its peak at {@code peak}, counted in steps, the same for every pair. */
    private static Tune.Evaluator planted(Map<String, Long> peak) {
        return (k, plus, minus) -> new Tune.Evaluation(bowl(plus, peak), bowl(minus, peak), 0.1, 6, 0, List.of());
    }

    private static double bowl(Map<String, Long> vector, Map<String, Long> peak) {
        double total = 0;
        for (Map.Entry<String, Long> e : peak.entrySet()) {
            double steps = (vector.get(e.getKey()) - e.getValue()) / (double) Tunables.named(e.getKey()).step();
            total -= 0.5 * steps * steps;
        }
        return total;
    }

    private static final Map<String, Long> PEAK = Map.of("fight.margin_per_mille", 900L, "identify.reach", 20L, "descend.rests", 60L);

    private static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ determinism, the control, the candidate

    @Test
    @DisplayName("three iterations give the same trajectory every time, and the first iteration is pinned")
    void three_iterations_are_reproducible(@TempDir Path root) throws IOException {
        Tune.Config config = config(3);
        Path a = root.resolve("a");
        Path b = root.resolve("b");
        Tune.execute(config, a, planted(PEAK), line -> { });
        Tune.execute(config, b, planted(PEAK), line -> { });
        assertEquals(text(a.resolve(Tune.TRAJECTORY)), text(b.resolve(Tune.TRAJECTORY)), "the same folder twice");
        assertEquals(text(a.resolve(Tune.CONFIG)), text(b.resolve(Tune.CONFIG)));
        List<Map<String, String>> lines = Tune.trajectory(a);
        assertEquals(3, lines.size());
        // Pinned: the signs, the rounding and the perturbation schedule are what a recorded trajectory means. The
        // numbers are those of iteration 0 from the defaults of the three, so they hold on any JVM.
        Map<String, String> first = lines.get(0);
        assertEquals("{descend.rests=14, fight.margin_per_mille=556, identify.reach=4}", Json.object(first.get("plus")).toString());
        assertEquals("{descend.rests=26, fight.margin_per_mille=444, identify.reach=12}", Json.object(first.get("minus")).toString());
    }

    @Test
    @DisplayName("a planted objective with a known optimum pulls every number toward it, and the control is not the default")
    void the_tuner_discriminates() throws IOException {
        Path folder = Files.createTempDirectory("tune-planted");
        Tune.Config config = config(120);
        double[] theta = Tune.execute(config, folder, planted(PEAK), line -> { });
        long[] found = Spsa.rounded(config.spsaParams(), theta);
        for (int i = 0; i < NAMES.size(); i++) {
            Tunables.Tunable t = Tunables.named(NAMES.get(i));
            long peak = PEAK.get(t.name());
            assertTrue(Math.abs(found[i] - peak) < Math.abs(t.def() - peak),
                    t.name() + " moved from " + t.def() + " to " + found[i] + ", toward " + peak);
            assertTrue(Math.abs(found[i] - peak) <= t.step(), t.name() + " ended at " + found[i] + ", the peak is " + peak);
        }
        // The control: an objective flat in every number moves nothing, so the movement above is the objective's.
        Path flat = Files.createTempDirectory("tune-flat");
        double[] still = Tune.execute(config, flat, (k, p, m) -> new Tune.Evaluation(3, 3, 0.1, 6, 0, List.of()), line -> { });
        assertArrayEquals(config.start(), still);
    }

    private static void assertArrayEquals(double[] expected, double[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual, 0.0);
    }

    @Test
    @DisplayName("every value played, every state and the candidate stay inside every bound, whatever the objective asks")
    void everything_stays_inside_the_bounds(@TempDir Path root) throws IOException {
        // A peak far outside every range pushes every number against its bound for the whole run.
        Tune.Evaluator pushing = (k, plus, minus) -> {
            double up = 0;
            double down = 0;
            for (String name : NAMES) {
                up += plus.get(name);
                down += minus.get(name);
            }
            return new Tune.Evaluation(up, down, 0.1, 6, 0, List.of());
        };
        Tune.Config config = config(40);
        Path folder = root.resolve("push");
        Tune.execute(config, folder, pushing, line -> { });
        for (Map<String, String> line : Tune.trajectory(folder)) {
            for (String field : List.of("plus", "minus", "theta", "next")) {
                Map<String, String> values = Json.object(line.get(field));
                for (Map.Entry<String, String> e : values.entrySet()) {
                    Tunables.Tunable t = Tunables.named(e.getKey());
                    double v = Double.parseDouble(e.getValue());
                    assertTrue(v >= t.min() && v <= t.max(), field + " " + e.getKey() + " = " + v);
                    if (field.equals("plus") || field.equals("minus")) {
                        assertEquals(Math.rint(v), v, 0.0, "a played value is a whole number");
                    }
                }
            }
        }
        double[] last = lastNext(folder, config);
        Playbook base = PlaybookFile.read(PlaybookFile.of(ROOT, "shatterfish"), "shatterfish",
                ROOT.resolve(CodexManifest.FOLDER).resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag()));
        String committed = text(PlaybookFile.of(ROOT, "shatterfish"));
        Map<String, Long> moved = Tune.writeCandidate(config, base, last, folder);
        assertFalse(moved.isEmpty(), "the push moved something");
        Playbook candidate = PlaybookFile.read(folder.resolve(Tune.CANDIDATE), "shatterfish",
                ROOT.resolve(CodexManifest.FOLDER).resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag()));
        for (String name : NAMES) {
            Tunables.Tunable t = Tunables.named(name);
            long v = Tunables.value(candidate, t);
            assertTrue(v >= t.min() && v <= t.max(), name + " in the candidate is " + v);
            assertEquals(moved.containsKey(name) ? moved.get(name) : Tunables.value(base, t), v, name);
        }
        assertEquals(committed, text(PlaybookFile.of(ROOT, "shatterfish")), "the committed Playbook is not edited");
        assertTrue(text(folder.resolve(Tune.CANDIDATE_DIFF)).contains("fight.margin_per_mille"));
    }

    private static double[] lastNext(Path folder, Tune.Config config) {
        List<Map<String, String>> lines = Tune.trajectory(folder);
        Map<String, String> next = Json.object(lines.get(lines.size() - 1).get("next"));
        double[] out = new double[NAMES.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = Double.parseDouble(next.get(NAMES.get(i)));
        }
        return out;
    }

    // ------------------------------------------------------------------ resume

    @Test
    @DisplayName("a run stopped after three iterations and resumed ends with the trajectory of one that never stopped")
    void resume_is_equivalent(@TempDir Path root) throws IOException {
        Path whole = root.resolve("whole");
        Path halves = root.resolve("halves");
        Tune.execute(config(8), whole, planted(PEAK), line -> { });
        Tune.execute(config(8).withIterations(3), halves, planted(PEAK), line -> { });
        assertEquals(3, Tune.trajectory(halves).size());
        // A killed iteration leaves a pairs file and no trajectory line; the resume replays it and nothing else.
        Files.writeString(halves.resolve(Tune.PAIRS).resolve("iter-0003.jsonl"), "stale\n", StandardCharsets.UTF_8);
        List<Integer> played = new ArrayList<>();
        Tune.Evaluator watching = (k, plus, minus) -> {
            played.add(k);
            return planted(PEAK).evaluate(k, plus, minus);
        };
        Tune.execute(config(8), halves, watching, line -> { });
        assertEquals(List.of(3, 4, 5, 6, 7), played, "only the iterations not finished are played");
        assertEquals(text(whole.resolve(Tune.TRAJECTORY)), text(halves.resolve(Tune.TRAJECTORY)));
        assertFalse(text(halves.resolve(Tune.PAIRS).resolve("iter-0003.jsonl")).contains("stale"));
    }

    @Test
    @DisplayName("a folder is one tuning run: another configuration, or fewer iterations, is refused")
    void a_folder_keeps_its_configuration(@TempDir Path root) {
        Path folder = root.resolve("f");
        Tune.execute(config(4), folder, planted(PEAK), line -> { });
        Tune.Config other = new Tune.Config("smoke", List.of(HeroClass.WARRIOR), 4, 5000, 4, NAMES, config(4).start(), Spsa.Config.of(4));
        assertThrows(IllegalArgumentException.class, () -> Tune.execute(other, folder, planted(PEAK), line -> { }));
        assertThrows(IllegalArgumentException.class, () -> Tune.execute(config(2), folder, planted(PEAK), line -> { }));
        Tune.execute(config(4).withIterations(6), folder, planted(PEAK), line -> { });
        assertEquals(6, Tune.trajectory(folder).size());
    }

    // ------------------------------------------------------------------ the triples and the pairing

    @Test
    @DisplayName("an iteration's triples are one list per class, salted from the base, and no salt is played twice in a run")
    void picks_rotate_and_salts_are_unique() {
        Tune.Config config = new Tune.Config("smoke", List.of(HeroClass.values()), 3, 5000, 5, NAMES, config(5).start(), Spsa.Config.of(5));
        Set<Long> salts = new HashSet<>();
        List<DirectionSelect.Pick> zero = Tune.picks(ROOT, config, 0);
        assertEquals(18, zero.size());
        for (int k = 0; k < 5; k++) {
            List<DirectionSelect.Pick> picks = Tune.picks(ROOT, config, k);
            assertEquals(picks, Tune.picks(ROOT, config, k), "the same iteration is the same list");
            for (DirectionSelect.Pick pick : picks) {
                assertTrue(salts.add(pick.salt()), "salt " + pick.salt() + " played twice");
                assertTrue(pick.salt() >= Tune.SALT_FLOOR);
            }
            for (int ci = 0; ci < 6; ci++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(HeroClass.values()[ci], picks.get(ci * 3 + j).entry().heroClass());
                }
            }
        }
        assertNotEquals(zero, Tune.picks(ROOT, config, 1), "the window moves");
        assertEquals(18 * 5, salts.size());
    }

    private static Path write(Path folder, DirectionSelect.Pick pick, int depth, int bosses) throws IOException {
        SeedSet.Entry entry = pick.entry();
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", entry.heroClass(), entry.challengeFlags(),
                entry.seed(), entry.seedCode(), pick.salt(), 20_000, 3, 2, 8, new RunLog.Brain("shatterfish", "abc1234", ZERO),
                "", false, "a laptop", "2026-10-29T00:00:00Z");
        List<RunLog> records = new ArrayList<>();
        records.add(header);
        records.add(new RunLog.End(0, new RunLog.Outcome(false, false, 0, depth, 1_000, "DEATH", bosses), true));
        StringBuilder out = new StringBuilder();
        String chain = "";
        for (RunLog record : records) {
            out.append(RunLogJson.line(chain, record)).append('\n');
            chain = RunLogJson.chain(chain, record);
        }
        Files.createDirectories(folder);
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("the plus and the minus vector are played on the same triples under the same salts, and scored pair by pair")
    void both_vectors_play_the_same_seeds(@TempDir Path root) throws IOException {
        Tune.Config config = new Tune.Config("smoke", List.of(HeroClass.values()), 3, 5000, 2, NAMES, config(2).start(), Spsa.Config.of(2));
        List<List<DirectionSelect.Pick>> picked = new ArrayList<>();
        List<String> vectors = new ArrayList<>();
        // The depth a Run reaches is the descend.rests of the vector over ten, and the minus side loses one Run.
        Tune.Player player = (picks, folder, tunables) -> {
            picked.add(picks);
            vectors.add(tunables);
            int rests = Integer.parseInt(java.util.Arrays.stream(tunables.split(",")).filter(s -> s.startsWith("descend.rests="))
                    .findFirst().orElseThrow().substring("descend.rests=".length()));
            try {
                for (int i = 0; i < picks.size(); i++) {
                    if (folder.getFileName().toString().equals("minus") && i == 4) {
                        continue;
                    }
                    write(folder, picks.get(i), rests / 10, 0);
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return 0;
        };
        Tune.Evaluator evaluator = Tune.playing(ROOT, config, root.resolve("tune"), player, true);
        Tune.Evaluation e = evaluator.evaluate(0, Map.of("descend.rests", 50L, "identify.reach", 4L),
                Map.of("descend.rests", 20L, "identify.reach", 12L));
        assertEquals(2, picked.size());
        assertEquals(picked.get(0), picked.get(1), "the same triples under the same salts on both sides");
        assertEquals("descend.rests=50,identify.reach=4", vectors.get(0));
        assertEquals("descend.rests=20,identify.reach=12", vectors.get(1));
        assertEquals(17, e.pairs(), "a triple that lost a Run on one side is dropped from both");
        assertEquals(1, e.dropped());
        assertEquals(5.0, e.plus(), 1e-9);
        assertEquals(2.0, e.minus(), 1e-9);
        assertEquals(17, e.rows().size());
        assertTrue(Files.isDirectory(root.resolve("tune").resolve("iter-0000")), "--keep-logs keeps the logs");
    }

    @Test
    @DisplayName("the score of a Run is five floors per boss killed plus the deepest depth; no ending, no score")
    void the_objective(@TempDir Path root) throws IOException {
        DirectionSelect.Pick pick = Tune.picks(ROOT, config(1), 0).get(0);
        Path log = write(root, pick, 7, 1);
        assertEquals(12.0, Tune.score(RunLogReader.of(log)), 0.0);
        assertEquals(Tune.BOSS_WEIGHT, 5);
        // A log that ends nowhere is not scored.
        String cut = text(log).lines().findFirst().orElseThrow() + "\n";
        assertEquals(null, Tune.score(RunLogReader.of(cut)));
    }

    @Test
    @DisplayName("each class is one voice: the score is the mean of the class means, not of the Runs")
    void classes_weigh_equally() {
        List<Tune.Pair> pairs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            pairs.add(new Tune.Pair("MAGE", "S" + i, i, 10, 10));
        }
        pairs.add(new Tune.Pair("WARRIOR", "W", 100, 4, 2));
        Tune.Evaluation e = Tune.combine(pairs, 0, 0);
        assertEquals(7.0, e.plus(), 1e-9, "(10 + 4) / 2, not (90 + 4) / 10");
        assertEquals(6.0, e.minus(), 1e-9);
        assertEquals(10, e.pairs());
    }
}
