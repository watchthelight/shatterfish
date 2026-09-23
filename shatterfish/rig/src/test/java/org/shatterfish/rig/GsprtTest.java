package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the sequential test stops, and where it must not (story 3.6, ADR-0012).
 *
 * <p>{@code GsprtReferenceTest} holds the arithmetic to Fishtest's. This holds what Fishtest's
 * {@code set_state} does not have, because Fishtest's server evaluates a running total and a person
 * decides: the burn-in, the maximum, the order, and what a stop reports.
 */
class GsprtTest {

    private static List<PairScore> repeat(PairScore score, int times) {
        return new ArrayList<>(Collections.nCopies(times, score));
    }

    private static Gsprt test(int burnIn, int maximum) {
        return new Gsprt(0.50, 0.55, 0.05, 0.05, burnIn, maximum);
    }

    @Test
    @DisplayName("a candidate that wins every pair is accepted, but not before the burn-in")
    void a_bound_before_the_burn_in_does_not_stop_the_test() {
        // Ten straight wins cross the upper bound long before pair 40. Stopping there is what the
        // burn-in exists to prevent: the normal approximation is poor for a three-valued score on a
        // handful of pairs, and early luck is what it would be rewarding.
        Gsprt.Result result = test(40, 400).run(repeat(PairScore.BETTER, 100));

        assertEquals(Gsprt.Verdict.ACCEPT, result.verdict());
        assertEquals(40, result.pairs(), "the first pair at which a stop is allowed");
        assertTrue(result.trace().get(9) >= result.upper(),
                "the bound was already crossed at pair 10: " + result.trace().get(9));
        assertEquals(40, result.trace().size());
        assertEquals(result.upper(), result.llr(), 0, "the reported LLR is clamped to the bound");
        assertTrue(result.clamped(), "and it says it overshot");
    }

    @Test
    @DisplayName("a candidate that loses every pair is rejected")
    void losing_is_rejected() {
        Gsprt.Result result = test(20, 400).run(repeat(PairScore.WORSE, 100));

        assertEquals(Gsprt.Verdict.REJECT, result.verdict());
        assertEquals(20, result.pairs());
        assertArrayEquals(new int[] {20, 0, 0}, result.counts());
    }

    @Test
    @DisplayName("no bound by the maximum is undecided at the maximum, not a rejection")
    void the_maximum_is_undecided() {
        List<PairScore> pairs = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            pairs.add(i % 2 == 0 ? PairScore.BETTER : PairScore.WORSE);
        }
        Gsprt.Result result = test(10, 30).run(pairs);

        assertEquals(Gsprt.Verdict.UNDECIDED, result.verdict());
        assertEquals(30, result.pairs(), "no pair past the maximum is consumed");
        assertEquals(30, result.trace().size());
    }

    @Test
    @DisplayName("a Seed set that runs out before a bound is undecided, which is not a rejection")
    void running_out_is_undecided() {
        // Alternating, so the mean is one half with all the variance a pair can have: twelve pairs
        // of that is nowhere near a bound. (Twelve *ties* would not do: a mean of exactly one half
        // with almost no variance is overwhelming evidence for H0, and the test rejects -- see below.)
        List<PairScore> pairs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            pairs.add(i % 2 == 0 ? PairScore.BETTER : PairScore.WORSE);
        }
        Gsprt.Result result = test(10, 400).run(pairs);

        assertEquals(Gsprt.Verdict.UNDECIDED, result.verdict());
        assertEquals(12, result.pairs());
    }

    @Test
    @DisplayName("a run of ties is evidence for H0, and is rejected once the burn-in allows")
    void ties_are_evidence_for_the_null() {
        // Two identical Brains tie every pair. That is not "no information": a mean of exactly one
        // half, with the variance regularized to almost nothing, says as loudly as the data can that
        // the candidate is not better. Fishtest's arithmetic does the same (GsprtReferenceTest holds
        // the case (0, 25, 0)).
        Gsprt.Result result = test(10, 400).run(repeat(PairScore.EQUAL, 50));

        assertEquals(Gsprt.Verdict.REJECT, result.verdict());
        assertEquals(10, result.pairs(), "at the burn-in, not before");
    }

    @Test
    @DisplayName("the LLR depends on the counts, so the order changes only the path")
    void the_llr_is_a_function_of_the_counts() {
        List<PairScore> pairs = new ArrayList<>(repeat(PairScore.BETTER, 30));
        pairs.addAll(repeat(PairScore.EQUAL, 50));
        pairs.addAll(repeat(PairScore.WORSE, 20));
        List<PairScore> reversed = new ArrayList<>(pairs);
        Collections.reverse(reversed);
        Gsprt late = new Gsprt(0.50, 0.55, 0.05, 0.05, 99, 1000);

        double counted = late.llr(20, 50, 30).raw();
        assertEquals(counted, late.run(pairs).trace().get(99), 1e-12);
        assertEquals(counted, late.run(reversed).trace().get(99), 1e-12);
    }

    @Test
    @DisplayName("the bounds are log(β/(1−α)) and log((1−β)/α)")
    void the_bounds_are_walds() {
        Gsprt test = new Gsprt(0.5, 0.55, 0.10, 0.05, 1, 2);
        assertEquals(Math.log(0.05 / 0.90), test.lower(), 1e-15);
        assertEquals(Math.log(0.95 / 0.10), test.upper(), 1e-15);
    }

    @Test
    @DisplayName("a test with no hypotheses to separate, or no room to stop, is refused")
    void nonsense_is_refused() {
        assertThrows(IllegalArgumentException.class, () -> new Gsprt(0.55, 0.50, 0.05, 0.05, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Gsprt(0.5, 1.0, 0.05, 0.05, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Gsprt(0.5, 0.55, 0, 0.05, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Gsprt(0.5, 0.55, 0.05, 0.05, 5, 5));
        // Each rate alone is a rate, but together at one or more the lower bound lands above the
        // upper one and nearly everything accepts at the burn-in.
        assertThrows(IllegalArgumentException.class, () -> new Gsprt(0.5, 0.55, 0.6, 0.5, 1, 2));
    }

    @Test
    @DisplayName("a result over nothing but missing pairs is void, even when every pair may be missing")
    void all_missing_is_void() {
        // allowingMissing(1000) tolerates any share, so only the all-missing rule can void this: a
        // run of ties that are all missing is no evidence at all, not evidence for H0.
        Gsprt.Result result = test(10, 100).allowingMissing(1000)
                .run(repeat(PairScore.EQUAL, 20), Collections.nCopies(20, true));
        assertEquals(Gsprt.Verdict.VOID, result.verdict());
    }
}
