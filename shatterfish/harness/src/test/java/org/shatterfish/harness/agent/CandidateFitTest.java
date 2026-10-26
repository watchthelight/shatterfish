package org.shatterfish.harness.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 6.6: the fit arithmetic that the Results page quotes is recomputed from the committed report, not typed. */
class CandidateFitTest {

    private static final int REGISTERED_BUDGET_MS = 140;

    @Test
    void the_costs_are_read_from_the_committed_report() throws IOException {
        CandidateFit.Costs c = CandidateFit.read(CandidateFit.report());
        assertEquals(7.516, c.snapshot(), 1e-9);
        assertEquals(3.232, c.scrub(), 1e-9);
        assertEquals(15.236, c.load(), 1e-9);
        assertEquals(2.633, c.rollout3(), 1e-9);
        assertEquals(18.101, c.restore(), 1e-9);
        assertEquals(43.486, c.rolloutMs(), 1e-9);
    }

    @Test
    void the_ceiling_is_the_budget_at_which_search_doubles_a_run() {
        double ceiling = CandidateFit.ceilingMs();
        assertTrue(ceiling > 140 && ceiling < 160, "ceiling " + ceiling);
        assertEquals(2 * CandidateFit.runSeconds(), CandidateFit.runSecondsWith(ceiling), 1e-9);
        assertTrue(REGISTERED_BUDGET_MS <= ceiling, "the registered budget stays under the ceiling");
    }

    @Test
    void no_design_fits_the_registered_budget_and_each_needs_more() throws IOException {
        CandidateFit.Costs c = CandidateFit.read(CandidateFit.report());
        double previous = 0;
        for (CandidateFit.Design d : CandidateFit.DESIGNS) {
            double t = c.decisionMs(d);
            assertFalse(t <= REGISTERED_BUDGET_MS, "option " + d.option() + " needs " + t + " ms");
            assertTrue(t > previous, "options are listed from cheapest to dearest");
            previous = t;
        }
        // Option 3 is the probe's own shape: 3 candidates in 2 worlds, 6 rollouts and 2 scrubs.
        CandidateFit.Design three = CandidateFit.DESIGNS.get(0);
        assertEquals(6, three.rollouts());
        assertEquals(6 * 43.486 + 2 * 3.232, c.decisionMs(three), 1e-9);
    }

    @Test
    void the_table_names_every_option_and_its_verdict() throws IOException {
        String md = CandidateFit.markdown(CandidateFit.read(CandidateFit.report()), REGISTERED_BUDGET_MS);
        assertTrue(md.contains("| 3 |") && md.contains("| 4 |") && md.contains("| 5 |"));
        assertFalse(md.contains("| yes |"));
    }
}
