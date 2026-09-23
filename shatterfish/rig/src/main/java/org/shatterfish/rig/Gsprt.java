package org.shatterfish.rig;

import java.util.ArrayList;
import java.util.List;

/**
 * The sequential test over Per-pair scores (story 3.6, ADR-0012 option 6, FR-21).
 *
 * <p><b>What it computes.</b> A generalized sequential probability ratio test between two
 * hypotheses about the mean pair score, H0: {@code p0} and H1: {@code p1}, using the normal
 * approximation to the generalized log-likelihood ratio published by Michel Van den Bergh
 * (<i>A simple approximation for the GSPRT</i>, cantate.be/Fishtest/GSPRT_approximation.pdf, eq. 2.1):
 *
 * <pre>
 *   LLR(N) = N · (p1 − p0) · (2μ̂ − p0 − p1) / (2σ̂²)
 * </pre>
 *
 * where μ̂ and σ̂² are the sample mean and variance of the N pair scores. The test accepts H1 when
 * the LLR reaches {@code log((1−β)/α)}, rejects it at {@code log(β/(1−α))}, never stops before the
 * burn-in {@code n0} and is undecided at the maximum {@code nmax}.
 *
 * <p><b>Regularized.</b> With three possible scores and few pairs, one of them is often unobserved,
 * and a sample with every pair equal has no variance at all: the formula divides by zero. Every
 * zero count is replaced by a thousandth of a pair before the mean and variance are taken, which is
 * the regularization ADR-0012 names and Fishtest applies.
 *
 * <p><b>Clamped.</b> An LLR past a bound is reported as the bound, and the test says so when it had
 * gone more than three percent past, which marks a stop the approximation had overshot.
 *
 * <p><b>Why it is written here rather than ported.</b> Fishtest's implementation carries no
 * licence, so its code is all rights reserved and none of it is in this file. This implements the
 * published formula; the pinned Fishtest checkout is used only as an oracle, run outside this
 * repository, to produce the reference values {@code GsprtReferenceTest} holds this class to.
 *
 * <p><b>Pairs are fed in the Seed set's order.</b> This class does not know that and cannot check
 * it, and it matters: stopping is legitimate only when the order was fixed before any outcome was
 * seen. Completion order depends on which Runs were slow, which depends on how they went.
 */
public final class Gsprt {

    /** The pseudo-count a zero is replaced by. */
    static final double REGULARIZATION = 1e-3;

    /** How far past a bound an LLR may go before the report says the stop overshot. */
    static final double OVERSHOOT = 1.03;

    /** Where the test stands. */
    public enum Verdict {

        /** H1: the candidate is better by at least what the Registration asked. */
        ACCEPT,

        /** H0: it is not. */
        REJECT,

        /** Neither bound reached by the maximum, or the pairs ran out first. */
        UNDECIDED
    }

    /**
     * The LLR for a set of counts, clamped, and whether it overshot.
     *
     * @param llr     the log-likelihood ratio, clamped to the bounds
     * @param raw     the same before clamping
     * @param clamped whether {@code raw} went more than three percent past a bound
     */
    public record Llr(double llr, double raw, boolean clamped) {
    }

    /**
     * What the test concluded, and how it got there.
     *
     * @param pairs   how many pairs it consumed before it stopped
     * @param trace   the LLR after each pair, unclamped, so the path can be drawn
     * @param counts  worse, equal and better, over the pairs consumed
     */
    public record Result(Verdict verdict, int pairs, double llr, boolean clamped, double lower,
                         double upper, List<Double> trace, int[] counts) {

        public Result {
            trace = List.copyOf(trace);
            counts = counts.clone();
        }

        @Override
        public int[] counts() {
            return counts.clone();
        }
    }

    private final double p0;
    private final double p1;
    private final double lower;
    private final double upper;
    private final int burnIn;
    private final int maximum;

    /**
     * A test between pair-score means {@code p0} and {@code p1}, with false-accept rate
     * {@code alpha}, false-reject rate {@code beta}, burn-in {@code burnIn} and maximum {@code maximum}.
     */
    public Gsprt(double p0, double p1, double alpha, double beta, int burnIn, int maximum) {
        if (!(p0 > 0 && p0 < p1 && p1 < 1)) {
            throw new IllegalArgumentException("H0 and H1 are pair-score means with 0 < p0 < p1 < 1: "
                    + p0 + ", " + p1);
        }
        if (!(alpha > 0 && alpha < 1 && beta > 0 && beta < 1)) {
            throw new IllegalArgumentException("error rates are rates: " + alpha + ", " + beta);
        }
        if (burnIn < 1 || maximum <= burnIn) {
            throw new IllegalArgumentException("a burn-in of at least one and a maximum past it: "
                    + burnIn + ", " + maximum);
        }
        this.p0 = p0;
        this.p1 = p1;
        this.lower = Math.log(beta / (1 - alpha));
        this.upper = Math.log((1 - beta) / alpha);
        this.burnIn = burnIn;
        this.maximum = maximum;
    }

    /** The test a Registration fixes, from its thousandths. */
    public static Gsprt of(org.shatterfish.api.Registration registration) {
        if (!registration.comparison()) {
            throw new IllegalArgumentException("the Registration " + registration.id()
                    + " fixes a baseline, and a baseline has no hypotheses to test between");
        }
        return new Gsprt(registration.p0PerMil() / 1000.0, registration.p1PerMil() / 1000.0,
                registration.alphaPerMil() / 1000.0, registration.betaPerMil() / 1000.0,
                registration.burnIn(), registration.maximum());
    }

    /** The lower bound, {@code log(β/(1−α))}. */
    public double lower() {
        return lower;
    }

    /** The upper bound, {@code log((1−β)/α)}. */
    public double upper() {
        return upper;
    }

    /**
     * The LLR for {@code worse} losses, {@code equal} ties and {@code better} wins, from the
     * candidate's side.
     *
     * <p>Counts rather than a path: the approximation depends only on the sample's mean and
     * variance, so two orders of the same pairs give the same LLR. The path matters only for where
     * the test stops, which is {@link #run}'s job.
     */
    public Llr llr(double worse, double equal, double better) {
        double w = worse == 0 ? REGULARIZATION : worse;
        double e = equal == 0 ? REGULARIZATION : equal;
        double b = better == 0 ? REGULARIZATION : better;
        double n = w + e + b;
        double mean = (0.5 * e + b) / n;
        double variance = (w * mean * mean + e * (0.5 - mean) * (0.5 - mean)
                + b * (1 - mean) * (1 - mean)) / n;
        double raw = n * (p1 - p0) * (2 * mean - p0 - p1) / (2 * variance);
        boolean clamped = raw > OVERSHOOT * upper || raw < OVERSHOOT * lower;
        double llr = Math.max(lower, Math.min(upper, raw));
        return new Llr(llr, raw, clamped);
    }

    /**
     * Feeds the pairs in the order given and stops at the first bound reached after the burn-in.
     *
     * <p>At most {@code maximum} pairs are consumed. When the list is shorter than that and no
     * bound was reached, the result is {@link Verdict#UNDECIDED}: the Seed set ran out, which is
     * the same answer as running out of budget and must not be mistaken for a rejection.
     */
    public Result run(List<PairScore> pairs) {
        int[] counts = new int[3];
        List<Double> trace = new ArrayList<>();
        Llr last = llr(0, 0, 0);
        int consumed = 0;
        for (PairScore pair : pairs) {
            if (consumed == maximum) {
                break;
            }
            counts[pair.halves()]++;
            consumed++;
            last = llr(counts[0], counts[1], counts[2]);
            trace.add(last.raw());
            if (consumed >= burnIn) {
                if (last.raw() >= upper) {
                    return new Result(Verdict.ACCEPT, consumed, last.llr(), last.clamped(), lower,
                            upper, trace, counts);
                }
                if (last.raw() <= lower) {
                    return new Result(Verdict.REJECT, consumed, last.llr(), last.clamped(), lower,
                            upper, trace, counts);
                }
            }
        }
        return new Result(Verdict.UNDECIDED, consumed, last.llr(), last.clamped(), lower, upper,
                trace, counts);
    }
}
