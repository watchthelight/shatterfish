package org.shatterfish.rig;

import java.util.List;

/**
 * Simultaneous perturbation stochastic approximation, as Fishtest's SPSA tuner uses it (story 10.36,
 * ADR-0026): the arithmetic only, with no Run, no file and no clock, so that a test can hold it to a
 * known optimum.
 *
 * <p>Each iteration {@code k} moves every parameter by plus or minus one perturbation, all at once, the
 * sign of each drawn from a generator that depends on nothing but the fixed seed, the iteration and the
 * parameter's place in the list. The two vectors are played on the same seeds (the caller's job), the
 * difference of their scores says which way the whole vector should move, and the step taken is
 * {@code a_k} times that difference over the perturbation actually played, per parameter.
 *
 * <p>Parameters differ in size by two orders of magnitude (a range of 40 and one of 1000), so every
 * quantity is counted in the parameter's own <em>steps</em> ({@link Param#step}): the perturbation is
 * {@code c_k} steps, the gradient is the score change per step, and a move is a number of steps. The
 * objective is maximised.
 *
 * <p>Values are whole numbers wherever they are played, because that is how the Playbook spells them.
 * The state {@code theta} is kept as a real number, so a move smaller than one unit is not lost to
 * rounding but gathers over iterations; it is rounded only when played and when written as the candidate.
 */
final class Spsa {

    private Spsa() {
    }

    /**
     * The default {@code r_end}: the learning rate at the last planned iteration. A full-scale result (the
     * normalised difference at plus or minus 1) moves a number about {@code r_end * c_end} steps there, and about
     * three times that at the first iteration, so a run of 10 iterations can drift a number at most about 3 steps
     * whatever the noise says (the worst case, every iteration at full scale in one direction, is a test).
     */
    static final double R_END = 0.2;

    /** The default {@code c_end}: the perturbation, in steps, at the last planned iteration. */
    static final double C_END = 1.0;

    /**
     * The score difference that counts as a full-scale result, in score points (a Run is worth a floor per
     * depth and 5 per boss, so this is two floors of mean paired difference). The difference is divided by it
     * and clamped to plus or minus 1 before it moves anything, as Fishtest's SPSA takes a game result in [-1, 1].
     */
    static final double SCALE = 2.0;

    /** Spall's recommended exponent of the gain's decay. */
    static final double ALPHA = 0.602;

    /** Spall's recommended exponent of the perturbation's decay. */
    static final double GAMMA = 0.101;

    /** The most one iteration moves a parameter, in steps: one noisy pair must not throw a parameter across its range. */
    static final double MAX_MOVE = 1.0;

    /** The fixed seed of the signs, so that a rerun of the same configuration perturbs identically. */
    static final long SEED = 0x5F15F15F5EEDL;

    /**
     * The constants of a tuning run, calibrated as Fishtest's SPSA is: by what a number may do at the end of the
     * planned run, not by a gain whose effect depends on the scale of the score.
     *
     * <p>{@code c_k = c_end * horizon^gamma / (k+1)^gamma} is the perturbation in steps, so it is {@code c_end} at
     * the last planned iteration. {@code a_k = r_end * c_end^2 * (stability + horizon)^alpha / (stability + k + 1)^alpha}
     * is the gain, and a move is {@code a_k / c_k} steps for a normalised result of 1, which is {@code r_end * c_end}
     * at the last planned iteration. The drift of a number over a run is bounded by the sum of those, whatever the
     * scores are.
     *
     * @param rEnd      the learning rate at the last planned iteration
     * @param cEnd      the perturbation, in steps, at the last planned iteration
     * @param horizon   the iterations the schedule is calibrated for; fixed when the folder is made, so a resume that
     *                  asks for more iterations continues the same decay
     * @param scale     the score difference that is a full-scale result
     * @param stability {@code A}: the offset that keeps the first gains from being huge; about a tenth of the horizon
     * @param alpha     the gain's exponent
     * @param gamma     the perturbation's exponent
     * @param maxMove   the most a parameter moves in one iteration, in steps
     * @param seed      the seed of the signs
     */
    record Config(double rEnd, double cEnd, int horizon, double scale, double stability, double alpha, double gamma,
                  double maxMove, long seed) {

        Config {
            if (!(rEnd > 0) || !(cEnd > 0) || horizon < 1 || !(scale > 0) || stability < 0 || !(alpha > 0) || !(gamma >= 0)
                    || !(maxMove > 0)) {
                throw new IllegalArgumentException("SPSA constants are positive (stability and gamma may be 0): " + this);
            }
        }

        /** The defaults for a run of {@code iterations} iterations. */
        static Config of(int iterations) {
            return new Config(R_END, C_END, iterations, SCALE, 0.1 * iterations, ALPHA, GAMMA, MAX_MOVE, SEED);
        }
    }

    /** One parameter's range and step, from the registry. */
    record Param(String name, long min, long max, long step) {

        Param {
            if (min >= max || step < 1) {
                throw new IllegalArgumentException("a parameter has a range and a step of at least 1: " + name);
            }
        }

        long clamp(long value) {
            return Math.max(min, Math.min(max, value));
        }

        double clamp(double value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /**
     * The two vectors of one iteration.
     *
     * @param iteration the iteration, from 0
     * @param ck        the perturbation in steps
     * @param ak        the gain
     * @param delta     the sign of each parameter's perturbation, +1 or -1
     * @param plus      the vector of {@code theta + delta * ck * step}, rounded and clamped
     * @param minus     the vector of {@code theta - delta * ck * step}, rounded and clamped
     */
    record Probe(int iteration, double ck, double ak, int[] delta, long[] plus, long[] minus) {
    }

    /**
     * What an update did.
     *
     * @param next     the new state, clamped to the bounds
     * @param gradient the estimated change of the score per step of each parameter
     * @param move     how far each parameter moved, in its own units, after the cap and the bounds
     */
    record Update(double[] next, double[] gradient, double[] move) {
    }

    static double perturbation(Config config, int k) {
        return config.cEnd() * Math.pow(config.horizon(), config.gamma()) / Math.pow(k + 1, config.gamma());
    }

    static double gain(Config config, int k) {
        return config.rEnd() * config.cEnd() * config.cEnd() * Math.pow(config.stability() + config.horizon(), config.alpha())
                / Math.pow(config.stability() + k + 1, config.alpha());
    }

    /** The finaliser of splitmix64, written out so that no JDK release changes a sign. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The sign, +1 or -1, of parameter {@code i} at iteration {@code k}: a function of the seed and of nothing else. */
    static int sign(long seed, int k, int i) {
        long h = mix(seed + 0x9E3779B97F4A7C15L * (((long) k << 20) + i + 1));
        return (h & 1L) == 0 ? 1 : -1;
    }

    /** The pair of vectors of iteration {@code k} at the state {@code theta}. */
    static Probe probe(Config config, List<Param> params, double[] theta, int k) {
        double ck = perturbation(config, k);
        int n = params.size();
        int[] delta = new int[n];
        long[] plus = new long[n];
        long[] minus = new long[n];
        for (int i = 0; i < n; i++) {
            Param p = params.get(i);
            delta[i] = sign(config.seed(), k, i);
            double reach = ck * p.step();
            long up = p.clamp(Math.round(theta[i] + delta[i] * reach));
            long down = p.clamp(Math.round(theta[i] - delta[i] * reach));
            if (up == down) {
                // Rounding or a bound closed the gap, and a gap of nothing says nothing about the slope.
                // Open it by one unit toward the side the bound leaves free.
                if (delta[i] > 0) {
                    if (up < p.max()) {
                        up++;
                    } else {
                        down--;
                    }
                } else {
                    if (down < p.max()) {
                        down++;
                    } else {
                        up--;
                    }
                }
            }
            plus[i] = up;
            minus[i] = down;
        }
        return new Probe(k, ck, gain(config, k), delta, plus, minus);
    }

    /**
     * The state after an iteration whose plus vector scored {@code yPlus} and minus vector {@code yMinus}.
     *
     * <p>The score difference is first made a result {@code r} in [-1, 1] (over {@link Config#scale}, clamped), so
     * one lucky pair cannot be worth more than a decisive one. The gradient of a parameter is {@code r} over the
     * half-gap that was <em>played</em>, in steps (the half-gap is {@code c_k} unless rounding or a bound changed
     * it), so a parameter pressed against its bound is estimated from the pair it actually had. A move is
     * {@code a_k} times that, capped at {@link Config#maxMove} steps, and the state is clamped to the bounds.
     */
    static Update update(Config config, List<Param> params, double[] theta, Probe probe, double yPlus, double yMinus) {
        int n = params.size();
        double[] next = new double[n];
        double[] gradient = new double[n];
        double[] move = new double[n];
        double result = Math.max(-1.0, Math.min(1.0, (yPlus - yMinus) / config.scale()));
        for (int i = 0; i < n; i++) {
            Param p = params.get(i);
            double half = (probe.plus()[i] - probe.minus()[i]) / (2.0 * p.step());
            gradient[i] = result / half;
            double steps = Math.max(-config.maxMove(), Math.min(config.maxMove(), probe.ak() * gradient[i]));
            next[i] = p.clamp(theta[i] + steps * p.step());
            move[i] = next[i] - theta[i];
        }
        return new Update(next, gradient, move);
    }

    /** {@code theta} as the whole numbers that are played and written as the candidate. */
    static long[] rounded(List<Param> params, double[] theta) {
        long[] out = new long[theta.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = params.get(i).clamp(Math.round(theta[i]));
        }
        return out;
    }
}
