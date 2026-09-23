package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The betting e-process (story 3.8).
 *
 * <p>The stops below are worked by hand from the bets, not read back from the class: a first win
 * against a prior of one pair at ½ bets λ = 0 (the running mean is exactly p0), so the wealth first
 * moves on the second pair, and so on. Where a stop is a count, the count is the smallest at which
 * the hand computation crosses the bound.
 */
class EProcessTest {

    private static EProcess test() {
        return new EProcess(0.50, 0.60, 0.05, 0.05, 500);
    }

    @Test
    @DisplayName("every pair a win: the wealth against H0 reaches 1/α and the test accepts, with no burn-in")
    void wins_accept() {
        Gsprt.Result result = test().run(Collections.nCopies(100, PairScore.BETTER));

        assertEquals(Gsprt.Verdict.ACCEPT, result.verdict());
        assertTrue(result.llr() >= Math.log(20), "log wealth past log(1/α): " + result.llr());
        // By hand: the first bet is 0 (the prior's mean is ½ exactly). Before the second, the mean
        // is 0.75 and the variance 0.15625, so λ = 0.25 / (0.15625 + 0.0625) = 1.14, clipped to
        // ½/p0 = 1, and it stays clipped as the mean rises and the variance falls. A win at λ = 1
        // multiplies the wealth by 1 + (1 − ½) = 1.5, so the wealth is 1.5^(n−1), which passes 20
        // at n − 1 = 8 (1.5^7 = 17.1, 1.5^8 = 25.6).
        assertEquals(9, result.pairs());
        assertEquals(1, test().burnIn());
    }

    @Test
    @DisplayName("every pair a loss: the futility wealth reaches 1/β and the test rejects")
    void losses_reject() {
        Gsprt.Result result = test().run(Collections.nCopies(100, PairScore.WORSE));

        assertEquals(Gsprt.Verdict.REJECT, result.verdict());
        assertTrue(result.pairs() < 20, "quickly: " + result.pairs());
        // The reported statistic is the leading wealth, the futility one negated, so the REJECT can
        // be checked against the trace and the lower bound like a GSPRT's.
        assertTrue(result.llr() <= result.lower(), result.llr() + " past " + result.lower());
        assertEquals(result.llr(), result.trace().get(result.trace().size() - 1));
        assertTrue(result.trace().subList(0, result.trace().size() - 1).stream()
                .allMatch(w -> w > result.lower()), "and not before the pair it stopped on");
    }

    @Test
    @DisplayName("every pair a tie at p0: no bet on the candidate, so no wealth and never an accept")
    void ties_never_accept() {
        Gsprt.Result result = test().run(Collections.nCopies(500, PairScore.EQUAL));

        // The acceptance wealth never moves (the mean never leaves p0, so the bet is 0), which
        // leaves the futility wealth ahead from the first pair: every entry is that, negated.
        assertTrue(result.trace().stream().allMatch(w -> w < 0.0), result.trace().toString());
        assertTrue(result.verdict() != Gsprt.Verdict.ACCEPT, result.verdict().toString());
        assertEquals(Gsprt.Verdict.REJECT, result.verdict(),
                "and ties at ½ are evidence against a mean of 0.6");
    }

    @Test
    @DisplayName("undecided at the maximum, and void past the missing cap, as the GSPRT is")
    void maximum_and_void() {
        List<PairScore> alternating = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            alternating.add(i % 2 == 0 ? PairScore.BETTER : PairScore.WORSE);
        }
        Gsprt.Result capped = new EProcess(0.50, 0.60, 0.05, 0.05, 10).run(alternating);
        assertEquals(Gsprt.Verdict.UNDECIDED, capped.verdict());
        assertEquals(10, capped.pairs());

        Gsprt.Result voided = test().allowingMissing(50)
                .run(Collections.nCopies(100, PairScore.BETTER), Collections.nCopies(100, true));
        assertEquals(Gsprt.Verdict.VOID, voided.verdict());
    }

    @Test
    @DisplayName("the bet is aGRAPA's, clipped to [0, cap]")
    void the_bet() {
        assertEquals(0.0, EProcess.bet(0.5, 0.25, 0.5, 1.0), 0.0, "no edge, no bet");
        assertEquals(0.0, EProcess.bet(0.4, 0.25, 0.5, 1.0), 0.0, "never a bet the candidate is worse");
        assertEquals(0.1 / (0.2 + 0.01), EProcess.bet(0.6, 0.2, 0.5, 1.0), 1e-12);
        assertEquals(1.0, EProcess.bet(0.9, 0.01, 0.5, 1.0), 0.0, "clipped");
    }

    @Test
    @DisplayName("a Registration's thousandths become the test, missing cap included, and a baseline is refused")
    void from_a_registration() {
        org.shatterfish.api.Registration.Brain random = new org.shatterfish.api.Registration.Brain(
                "random", "abc1234", "0".repeat(64));
        org.shatterfish.api.Registration comparison = new org.shatterfish.api.Registration(
                "H-0120-e", "a comparison", new org.shatterfish.api.Registration.Brain("random",
                        "aaaaaaa", "0".repeat(64)), random, SeedSets.SMOKE, 1, 40, 60, 20, 300, 0,
                "a laptop", false, 500, 600, 250);

        EProcess test = EProcess.of(comparison);

        assertEquals(0.5, test.p0());
        assertEquals(0.6, test.p1());
        assertEquals(Math.log(1 / 0.04), test.upper(), 1e-12, "alpha from alpha");
        assertEquals(-Math.log(1 / 0.06), test.lower(), 1e-12, "beta from beta");
        assertEquals(300, test.maximum());
        assertEquals(250, test.missingPerMil(), "the crash cap travels with it");
        assertEquals(SequentialTest.Statistic.EPROCESS, test.statistic());
        org.shatterfish.api.Registration baseline = new org.shatterfish.api.Registration("H-0121-b",
                "a baseline", null, random, SeedSets.SMOKE, 1, 50, 50, 8, 25, 0, "a laptop", false);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> EProcess.of(baseline));
        assertTrue(refused.getMessage().contains("fixes a baseline"),
                "refused as a baseline, not for the zero hypotheses it carries: " + refused.getMessage());
        // And the Rig asks the interface, which answers in the gate's design.
        assertEquals(SequentialTest.GATE, SequentialTest.of(comparison).statistic());
    }

    @Test
    @DisplayName("parameters that are not hypotheses, rates or a maximum are refused")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.6, 0.5, 0.05, 0.05, 10));
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.5, 0.5, 0.05, 0.05, 10));
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.0, 0.6, 0.05, 0.05, 10));
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.5, 1.0, 0.05, 0.05, 10));
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.5, 0.6, 0.6, 0.5, 10));
        assertThrows(IllegalArgumentException.class, () -> new EProcess(0.5, 0.6, 0.05, 0.05, 0));
        assertThrows(IllegalArgumentException.class, () -> test().allowingMissing(1001));
    }
}
