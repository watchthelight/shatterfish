package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The E1 benchmark runs on a tiny configuration and its report carries every field with a finite
 * number in it (story 1.21): the published page is this report at a real size on a described
 * machine, and the smoke is what keeps the command working between publications. The tiny
 * configuration's outcomes are deterministic, so they are pinned: the same Runs, twice in one
 * process, and the values the story recorded; a change to the game's outcomes from anything on
 * the harness side (fonts, layout, the agent) fails here rather than drifting. The flags parse
 * and are refused by name, and the statistics are held on fixed vectors.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BenchmarkSmokeTest {

    private static final Launcher.Launch TINY = new Launcher.Launch(14_142_135L, true, true, 2, 2, 2, 4);

    @Test
    @DisplayName("a tiny benchmark runs to a report with every field, every number finite, and the same outcomes twice")
    void a_tiny_benchmark_reports() {
        ByteArrayOutputStream progress = new ByteArrayOutputStream();
        Launcher.Benchmark.Report report = Launcher.Benchmark.run(TINY, new PrintStream(progress, true, StandardCharsets.UTF_8));
        assertEquals(2, report.runs());
        assertTrue(report.seconds() > 0 && report.waits() > 0 && report.turns() > 0, report.toString());
        assertTrue(report.waitsPerSecond() > 0 && report.turnsPerSecond() > 0 && report.runsPerMinute() > 0, report.toString());
        assertTrue(report.medianTurns() > 0 && report.medianWaits() > 0, report.toString());
        assertEquals(2, report.causes().values().stream().mapToInt(Integer::intValue).sum(), report.causes().toString());
        assertTrue(report.costWaits() > 0 && report.observeMicros() > 0 && report.codecMicros() > 0 && report.writerMicros() > 0, report.toString());
        assertTrue(report.codecBytes() > 0 && report.writerBytes() > 0, report.toString());
        assertTrue(report.tactics());
        assertEquals(2, report.samplesAsked());
        assertEquals(report.samplesAsked(), report.samplesTaken() + report.skips().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.samplesTaken() >= 1, "at least one sample taken: " + report.skips());
        assertTrue(report.playouts() >= 2, "a pair of playouts: " + report.samples());
        assertFalse(Double.isNaN(report.leafCorrelation()), "leaf correlation from the samples taken");
        assertTrue(report.leafCorrelation() >= 0 && report.leafCorrelation() <= 1, report.toString());
        assertTrue(report.chanceAgreement() >= 0.5 && report.chanceAgreement() <= 1, report.toString());
        assertTrue(report.survival() >= 0 && report.survival() <= 1, report.toString());
        assertTrue(Double.isNaN(report.disambiguation()) || (report.disambiguation() >= 0 && report.disambiguation() <= 1), report.toString());
        assertTrue(report.hiddenByKind().get("identity") > 0, "unknown appearances are hidden facts: " + report.hiddenByKind());
        String markdown = report.markdown();
        for (String field : new String[] {"## Environment", "## Throughput", "Input waits:", "Turns:", "Runs per minute", "Median Run length",
                "## Costs per Observation", "Observer.observe()", "ObservationCodec.encode", "Observation.json()", "## Tactics",
                "ORACLE", "Leaf correlation", "chance agreement", "Survival at the horizon", "Disambiguation factor",
                "Hidden facts per sample, by kind", "Bias: not measured", "## Samples"}) {
            assertTrue(markdown.contains(field), "the report names " + field + ":\n" + markdown);
        }
        assertFalse(markdown.contains("NaN"), "no NaN reaches a page:\n" + markdown);
        assertTrue(progress.toString(StandardCharsets.UTF_8).contains("Benchmark: sample 2 of 2"));

        // The pinned outcomes: what this configuration played when the story recorded it.
        assertEquals(PINNED_WAITS, report.waits(), "applied Actions over the two Runs");
        assertEquals(PINNED_TURNS, report.turns(), "turns over the two Runs");
        assertEquals(PINNED_CAUSES, report.causes());
        assertEquals(PINNED_DEPTHS, report.depths());
        assertEquals(PINNED_SAMPLES, report.samples());

        Launcher.Benchmark.Report again = Launcher.Benchmark.run(TINY, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertEquals(report.waits(), again.waits(), "the same Runs twice in one process");
        assertEquals(report.turns(), again.turns());
        assertEquals(report.causes(), again.causes());
        assertEquals(report.depths(), again.depths());
        assertEquals(report.samples(), again.samples());
        assertEquals(report.codecBytes(), again.codecBytes());
    }

    private static final long PINNED_WAITS = 128;
    private static final long PINNED_TURNS = 3118;
    private static final Map<String, Integer> PINNED_CAUSES = Map.of("DEATH", 2);
    private static final Map<Integer, Integer> PINNED_DEPTHS = Map.of(1, 2);
    private static final List<String> PINNED_SAMPLES = List.of(
            "seed 14142138 wait 6: 2 playouts, payoffs [1, 1], hidden facts 33 {identity=33}, revealed 0.00",
            "seed 14142139 wait 13: 2 playouts, payoffs [1, 1], hidden facts 33 {identity=33}, revealed 0.00");

    @Test
    @DisplayName("without --oracle the tactics half does not run, and the report says so")
    void without_the_oracle_no_tactics() {
        Launcher.Launch fair = new Launcher.Launch(14_142_135L, false, true, 1, 1, 2, 4);
        Launcher.Benchmark.Report report = Launcher.Benchmark.run(fair, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertFalse(report.tactics());
        assertEquals(0, report.samplesAsked());
        assertEquals(0, report.samplesTaken());
        assertTrue(report.samples().isEmpty());
        String markdown = report.markdown();
        assertTrue(markdown.contains("Not measured: the tactics half reads the oracle sidecar and runs only with `--oracle`"), markdown);
        assertFalse(markdown.contains("ORACLE"), "nothing oracle-derived without the flag:\n" + markdown);
    }

    @Test
    @DisplayName("the benchmark's flags parse, and a count that is not a positive number is refused by name")
    void the_flags_parse() {
        Launcher.Launch launch = Launcher.Launch.parse(new String[] {"--benchmark", "--oracle", "--runs", "3", "--samples", "5", "--siblings", "2", "--horizon", "7", "42"});
        assertEquals(new Launcher.Launch(42L, true, true, 3, 5, 2, 7), launch);
        assertEquals(new Launcher.Launch(Launcher.Launch.DEFAULT_SEED, false, true, 200, 24, 4, 20), Launcher.Launch.parse(new String[] {"--benchmark"}));
        assertFalse(Launcher.Launch.parse(new String[] {"7"}).benchmark(), "off by default");
        for (String[] bad : new String[][] {{"--runs"}, {"--runs", "0"}, {"--runs", "x"}, {"--horizon", "-3"}, {"--runs", "9999999999"},
                {"--benchmark", "--siblings", "1"}, {"--runs", "3"}, {"--benchmark", "--runs", "5", "--runs", "7"},
                {"--benchmark", "--benchmark"}, {"--benchmark", String.valueOf(com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed.TOTAL_SEEDS - 1)}}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Launcher.Launch.parse(bad), String.join(" ", bad));
            String named = bad[bad.length - 1].matches("[0-9-]+|x") ? bad[bad.length - 2] : bad[bad.length - 1];
            assertTrue(refused.getMessage().contains(named.replace("--", "")) || refused.getMessage().contains("seed out of range"),
                    String.join(" ", bad) + " -> " + refused.getMessage());
        }
    }

    @Test
    @DisplayName("the statistics on fixed vectors: pairwise agreement, the median, the kinds, the salt")
    void the_statistics() {
        assertEquals(0.5, Launcher.Benchmark.agreement(List.of(1, 1, 1, 0)), 1e-9, "three of six pairs agree");
        assertEquals(1.0, Launcher.Benchmark.agreement(List.of(0, 0)), 1e-9);
        assertEquals(0.0, Launcher.Benchmark.agreement(List.of(0, 1)), 1e-9);
        assertTrue(Double.isNaN(Launcher.Benchmark.agreement(List.of(1))), "no pair, no agreement");
        assertEquals(2.0, Launcher.Benchmark.median(List.of(3L, 1L, 2L)), 1e-9);
        assertEquals(2.5, Launcher.Benchmark.median(List.of(4L, 1L, 2L, 3L)), 1e-9, "the mean of the two middles");
        assertEquals(0.0, Launcher.Benchmark.median(List.of()), 1e-9);
        assertEquals(Map.of("identity", 2, "door", 1), Launcher.Benchmark.kinds(Set.of("identity potion a", "door 5", "identity scroll b")));
        assertEquals(42L * 31 + 7, Launcher.Benchmark.saltFor(42L));
    }
}
