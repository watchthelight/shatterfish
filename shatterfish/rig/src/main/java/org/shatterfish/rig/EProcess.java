package org.shatterfish.rig;

import org.shatterfish.api.Registration;

import java.util.ArrayList;
import java.util.List;

/**
 * The alternative sequential test: a betting e-process (story 3.8, ADR-0012 option 8).
 *
 * <p><b>Acceptance needs no alternative.</b> A gambler starts with wealth 1 and, before each pair,
 * bets a fraction λ of it that the pair scores above {@code p0}: after a pair scoring {@code x} in
 * [0, 1] the wealth is multiplied by {@code 1 + λ(x − p0)}. If the true mean is at most {@code p0},
 * no betting strategy fixed before the pair it bets on makes money on average, so the wealth is a
 * nonnegative supermartingale, and Ville's inequality bounds the chance that it <em>ever</em>
 * reaches {@code 1/α} by α -- at every stopping time, with no burn-in and no asymptotics. The test
 * accepts when it does. Nothing about H1 enters: the bet is sized from the pairs already seen.
 *
 * <p><b>The bet</b> is aGRAPA, from Ian Waudby-Smith and Aaditya Ramdas, <i>Estimating means of
 * bounded random variables by betting</i> (JRSS B, 2024): λ = (μ̂ − p0) / (σ̂² + (μ̂ − p0)²), the
 * growth-rate-optimal bet for the running estimates μ̂ and σ̂², which start from ½ and ¼ as one prior
 * pair and are updated only after the pair they bet on. It is clipped to [0, ½/p0] so that the wealth
 * never falls below half on one pair and never bets that the candidate is worse.
 *
 * <p><b>Futility</b> is the same construction the other way: a second gambler bets that the mean is
 * below {@code p1}, with wealth multiplied by {@code 1 + λ'(p1 − x)}, λ' clipped to [0, ½/(1 − p1)],
 * and the test rejects when that wealth reaches {@code 1/β}. That side does use {@code p1}: "not
 * better by as much as p1" is the only thing a futility stop can mean.
 *
 * <p><b>What the result reports.</b> {@code llr} and the trace are the log of the acceptance wealth,
 * which is compared with {@code upper = log(1/α)}. The futility wealth is not in the trace; its
 * crossing is reported as a REJECT with {@code lower = −log(1/β)}. The missing-pair VOID rule is
 * {@link SequentialTest}'s, the same as the GSPRT's. {@code burnIn} is 1: validity does not need one.
 */
public final class EProcess implements SequentialTest {

    /** The largest fraction of the distance to zero wealth any one bet may risk. */
    static final double CAP = 0.5;

    private final double p0;
    private final double p1;
    private final double lower;
    private final double upper;
    private final int maximum;
    private int missingPerMil = 1000;

    public EProcess(double p0, double p1, double alpha, double beta, int maximum) {
        if (!(p0 > 0 && p0 < p1 && p1 < 1)) {
            throw new IllegalArgumentException("H0 and H1 are pair-score means with 0 < p0 < p1 < 1: "
                    + p0 + ", " + p1);
        }
        if (!(alpha > 0 && alpha < 1 && beta > 0 && beta < 1 && alpha + beta < 1)) {
            throw new IllegalArgumentException("error rates are rates, together below one: " + alpha
                    + ", " + beta);
        }
        if (maximum < 1) {
            throw new IllegalArgumentException("a maximum of at least one pair: " + maximum);
        }
        this.p0 = p0;
        this.p1 = p1;
        this.upper = Math.log(1 / alpha);
        this.lower = -Math.log(1 / beta);
        this.maximum = maximum;
    }

    /** The test a Registration fixes, from its thousandths; the burn-in is not used. */
    public static EProcess of(Registration registration) {
        if (!registration.comparison()) {
            throw new IllegalArgumentException("the Registration " + registration.id()
                    + " fixes a baseline, and a baseline has no hypotheses to test between");
        }
        return new EProcess(registration.p0PerMil() / 1000.0, registration.p1PerMil() / 1000.0,
                registration.alphaPerMil() / 1000.0, registration.betaPerMil() / 1000.0,
                registration.maximum()).allowingMissing(registration.missingPerMil());
    }

    /** The share of consumed pairs, in thousandths, that may be missing before the result is void. */
    public EProcess allowingMissing(int perMil) {
        if (perMil < 0 || perMil > 1000) {
            throw new IllegalArgumentException("a missing fraction is a fraction: " + perMil);
        }
        this.missingPerMil = perMil;
        return this;
    }

    @Override
    public Statistic statistic() {
        return Statistic.EPROCESS;
    }

    @Override
    public double p0() {
        return p0;
    }

    @Override
    public double p1() {
        return p1;
    }

    @Override
    public int burnIn() {
        return 1;
    }

    @Override
    public int maximum() {
        return maximum;
    }

    @Override
    public int missingPerMil() {
        return missingPerMil;
    }

    @Override
    public double lower() {
        return lower;
    }

    @Override
    public double upper() {
        return upper;
    }

    /** The aGRAPA bet on a mean above {@code m}, from the estimates before the pair, clipped. */
    static double bet(double mean, double variance, double m, double most) {
        double edge = mean - m;
        double lambda = edge / (variance + edge * edge);
        return Math.max(0, Math.min(most, lambda));
    }

    @Override
    public Gsprt.Result run(List<PairScore> pairs) {
        int[] counts = new int[3];
        List<Double> trace = new ArrayList<>();
        double sum = 0;
        double squares = 0;
        double wealth = 0;
        double futility = 0;
        int consumed = 0;
        for (PairScore pair : pairs) {
            if (consumed == maximum) {
                break;
            }
            // The estimates before this pair: one prior pair at ½ with variance ¼, then the pairs
            // already seen. A bet sized with this pair's own score would not be a bet.
            double mean = (0.5 + sum) / (consumed + 1);
            double variance = (0.25 + squares) / (consumed + 1);
            double up = bet(mean, variance, p0, CAP / p0);
            double down = bet(1 - mean, variance, 1 - p1, CAP / (1 - p1));
            double x = pair.halves() / 2.0;
            wealth += Math.log1p(up * (x - p0));
            futility += Math.log1p(down * (p1 - x));
            counts[pair.halves()]++;
            consumed++;
            sum += x;
            double after = (0.5 + sum) / (consumed + 1);
            squares += (x - after) * (x - after);
            trace.add(wealth);
            if (wealth >= upper) {
                return new Gsprt.Result(Gsprt.Verdict.ACCEPT, consumed, wealth, false, lower, upper,
                        trace, counts);
            }
            if (futility >= -lower) {
                return new Gsprt.Result(Gsprt.Verdict.REJECT, consumed, wealth, false, lower, upper,
                        trace, counts);
            }
        }
        return new Gsprt.Result(Gsprt.Verdict.UNDECIDED, consumed, wealth, false, lower, upper,
                trace, counts);
    }
}
