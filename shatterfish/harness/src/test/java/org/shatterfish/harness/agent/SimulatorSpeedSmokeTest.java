package org.shatterfish.harness.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The simulator-speed measurement runs in a tiny configuration and keeps its structure (story 6.4): the five cost rows are
 * present, the counts add up, the percentiles are ordered, and the page prints every table. The timings themselves are
 * not pinned: they are this machine's, at this moment, and a test that held them would measure the machine running it.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class SimulatorSpeedSmokeTest {

    @Test
    @DisplayName("a tiny measurement reports all five costs, with counts that add up, and no timing is pinned")
    void the_five_cost_rows_are_present_and_the_counts_add_up() throws IOException {
        SimulatorSpeed.Config tiny = new SimulatorSpeed.Config("smoke", 1, 1, 2, 0, 1_500, 0);
        SimulatorSpeed.Report report = SimulatorSpeed.run(tiny);

        assertEquals(1, report.snapshots(), "one mid-fight snapshot on the first smoke seed");
        assertEquals(2, report.scrubs(), "two samples scrubbed at it");
        int horizons = SimulatorSpeed.HORIZONS.length;
        assertEquals(report.scrubs() * horizons, report.runs().size(), "each sample is rolled out at every horizon");

        for (String cost : SimulatorSpeed.COSTS) {
            assertTrue(report.stats().stream().anyMatch(s -> s.name().equals(cost)), "the " + cost + " row is present");
        }
        for (SimulatorSpeed.Stat s : report.stats()) {
            int expected = s.name().equals("scrub") ? report.scrubs()
                    : s.name().equals("rollout") ? report.scrubs() : report.runs().size();
            assertEquals(expected, s.count(), "the count of " + s.name() + " " + s.horizon());
            assertTrue(s.medianMs() >= 0 && s.p95Ms() >= s.medianMs(), s.name() + ": the 95th percentile is not under the median");
        }
        for (int h : SimulatorSpeed.HORIZONS) {
            assertEquals(report.scrubs(), report.stat("rollout", h).count(), "a rollout row for horizon " + h);
        }
        assertTrue(report.runs().stream().allMatch(r -> r.applied() <= r.horizon()), "no rollout applies more than its horizon");
        assertTrue(report.runs().stream().anyMatch(r -> r.applied() >= 1), "some rollout applied an Action");

        String page = SimulatorSpeed.markdown(report, "test", "test");
        for (String heading : List.of("### Environment", "### The five costs", "### Redetermined rollouts per second per process",
                "### Which cost dominates", "### What fits a per-Decision budget")) {
            assertTrue(page.contains(heading), "the page has " + heading);
        }
        assertFalse(page.contains("NaN"), "every figure on the page is a number");
    }

    @Test
    @DisplayName("median and nearest-rank 95th percentile are what they say, on numbers chosen for the purpose")
    void the_statistics_are_the_definitions() {
        long[] twenty = new long[20];
        for (int i = 0; i < 20; i++) {
            twenty[19 - i] = (i + 1) * 1_000_000L;
        }
        SimulatorSpeed.Stat s = SimulatorSpeed.stat("x", 0, twenty);
        assertEquals(10.5, s.medianMs(), 1e-9, "the median of 1..20 ms");
        assertEquals(19.0, s.p95Ms(), 1e-9, "the 95th percentile of 1..20 ms by nearest rank is the 19th value");
        assertEquals(5.0, SimulatorSpeed.stat("x", 0, new long[] {5_000_000L}).p95Ms(), 1e-9, "one value is its own percentile");
        assertEquals(0, SimulatorSpeed.stat("x", 0, new long[0]).count());
    }

    @Test
    @DisplayName("the measurement refuses the sets that are not for measuring")
    void goo_and_holdout_are_refused() {
        assertThrows(IllegalArgumentException.class, () -> SimulatorSpeed.main(new String[] {"--set", "goo"}));
        assertThrows(IllegalArgumentException.class, () -> SimulatorSpeed.main(new String[] {"--set", "holdout"}));
    }
}
