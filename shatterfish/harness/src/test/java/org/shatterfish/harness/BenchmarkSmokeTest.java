package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The E1 benchmark runs on a tiny configuration and its report carries every field with a finite
 * number in it (story 1.21): the published page is this report at a real size on a described
 * machine, and the smoke is what keeps the command working between publications. The flags parse
 * and are refused by name.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BenchmarkSmokeTest {

    @Test
    @DisplayName("a tiny benchmark runs to a report with every field, and every number is finite")
    void a_tiny_benchmark_reports() {
        Launcher.Launch launch = new Launcher.Launch(14_142_135L, false, true, 2, 2, 2, 4);
        ByteArrayOutputStream progress = new ByteArrayOutputStream();
        Launcher.Benchmark.Report report = Launcher.Benchmark.run(launch, new PrintStream(progress, true, StandardCharsets.UTF_8));
        assertEquals(2, report.runs());
        assertTrue(report.seconds() > 0 && report.waits() > 0 && report.turns() > 0, report.toString());
        assertTrue(report.waitsPerSecond() > 0 && report.runsPerMinute() > 0, report.toString());
        assertTrue(report.medianTurns() > 0 && report.medianWaits() > 0, report.toString());
        assertEquals(2, report.causes().values().stream().mapToInt(Integer::intValue).sum(), report.causes().toString());
        assertTrue(report.costWaits() > 0 && report.observeMicros() > 0 && report.codecMicros() > 0 && report.writerMicros() > 0, report.toString());
        assertTrue(report.codecBytes() > 0 && report.writerBytes() > 0, report.toString());
        assertEquals(2, report.samplesAsked());
        assertEquals(report.samplesAsked(), report.samplesTaken() + report.samplesSkipped());
        if (report.samplesTaken() > 0) {
            assertFalse(Double.isNaN(report.leafCorrelation()), "leaf correlation from the samples taken");
            assertTrue(report.leafCorrelation() >= 0 && report.leafCorrelation() <= 1, report.toString());
            assertTrue(report.survival() >= 0 && report.survival() <= 1, report.toString());
            assertTrue(Double.isNaN(report.disambiguation()) || (report.disambiguation() >= 0 && report.disambiguation() <= 1), report.toString());
        }
        String markdown = report.markdown();
        for (String field : new String[] {"## Throughput", "Input waits:", "Runs per minute", "Median Run length", "## Costs per Observation",
                "Observer.observe()", "ObservationCodec.encode", "Observation.json()", "## Tactics", "Leaf correlation", "Disambiguation factor",
                "Bias: not measured", "## Samples"}) {
            assertTrue(markdown.contains(field), "the report names " + field + ":\n" + markdown);
        }
        assertTrue(progress.toString(StandardCharsets.UTF_8).contains("Benchmark: sample 2 of 2"));
    }

    @Test
    @DisplayName("the benchmark's flags parse, and a count that is not a positive number is refused")
    void the_flags_parse() {
        Launcher.Launch launch = Launcher.Launch.parse(new String[] {"--benchmark", "--runs", "3", "--samples", "5", "--siblings", "2", "--horizon", "7", "42"});
        assertEquals(new Launcher.Launch(42L, false, true, 3, 5, 2, 7), launch);
        assertEquals(new Launcher.Launch(Launcher.Launch.DEFAULT_SEED, false, true, 200, 24, 4, 20), Launcher.Launch.parse(new String[] {"--benchmark"}));
        assertFalse(Launcher.Launch.parse(new String[] {"7"}).benchmark(), "off by default");
        for (String[] bad : new String[][] {{"--runs"}, {"--runs", "0"}, {"--runs", "x"}, {"--horizon", "-3"}}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Launcher.Launch.parse(bad));
            assertTrue(refused.getMessage().contains(bad[0]), refused.getMessage());
        }
    }
}
