package org.shatterfish.rig;

import org.shatterfish.api.Registration;

import java.util.List;

/**
 * A sequential test over Per-pair scores: what a comparison runs to reach a verdict (ADR-0012).
 *
 * <p>Two designs implement it and both stay in the tree: {@link Gsprt}, the generalized sequential
 * probability ratio test (story 3.6), and {@link EProcess}, a betting e-process that needs no
 * pre-registered alternative to accept (story 3.8). {@link #GATE} is the one a ranked comparison
 * runs. It was chosen by the rule ADR-0012 states -- the e-process replaces the GSPRT if the
 * GSPRT's realized error rate exceeds its nominal rate by more than the margin story 3.7 declared --
 * and {@code CalibrationTest} holds it to what {@link Calibration} concludes from the committed
 * outcomes, so a later tag whose calibration goes the other way fails the build until this changes.
 */
public interface SequentialTest {

    /** The designs. */
    enum Statistic {

        GSPRT,

        EPROCESS
    }

    /** The statistic ranked comparisons run: the rule's outcome on the committed calibration. */
    Statistic GATE = Statistic.GSPRT;

    /** The test a Registration fixes, in the gate's design. */
    static SequentialTest of(Registration registration) {
        return GATE == Statistic.GSPRT ? Gsprt.of(registration) : EProcess.of(registration);
    }

    Statistic statistic();

    /** Runs the test over {@code pairs} in their order. */
    Gsprt.Result run(List<PairScore> pairs);

    /** H0's pair-score mean. */
    double p0();

    /** H1's pair-score mean. */
    double p1();

    /** Pairs before any stop. */
    int burnIn();

    /** Pairs after which the result is undecided. */
    int maximum();

    /** The share of consumed pairs, in thousandths, that may be missing before the result is void. */
    int missingPerMil();

    /** The bound a REJECT crosses, on the scale of the reported statistic. */
    double lower();

    /** The bound an ACCEPT crosses. */
    double upper();

    /**
     * {@link #run}, then void if too many of the consumed pairs were missing a Run.
     *
     * <p>Over the pairs consumed: a stop at pair 40 is about pairs 1 to 40, and a set that is clean
     * after the stop cannot excuse one that was not before it. A result with every pair missing is
     * void whatever the cap, because it measured nothing.
     *
     * @param missing one flag per pair, parallel to {@code pairs}
     */
    default Gsprt.Result run(List<PairScore> pairs, List<Boolean> missing) {
        Gsprt.Result result = run(pairs);
        long gone = missing.subList(0, result.pairs()).stream().filter(b -> b).count();
        if (result.pairs() > 0 && (gone == result.pairs()
                || gone * 1000 > (long) missingPerMil() * result.pairs())) {
            return new Gsprt.Result(Gsprt.Verdict.VOID, result.pairs(), result.llr(),
                    result.clamped(), result.lower(), result.upper(), result.trace(), result.counts());
        }
        return result;
    }
}
