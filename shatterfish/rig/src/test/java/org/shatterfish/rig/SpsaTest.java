package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic of SPSA (story 10.36), on objectives whose optimum is known. */
class SpsaTest {

    private static final List<Spsa.Param> PARAMS = List.of(
            new Spsa.Param("a.wide", 0, 1000, 50),
            new Spsa.Param("b.narrow", 0, 12, 1),
            new Spsa.Param("c.signed", -5, 5, 1));

    /** Runs the loop on {@code objective}, which sees the played (rounded) vector. */
    private static double[] run(Spsa.Config config, double[] start, int iterations, ToDoubleFunction<long[]> objective) {
        double[] theta = start.clone();
        for (int k = 0; k < iterations; k++) {
            Spsa.Probe probe = Spsa.probe(config, PARAMS, theta, k);
            theta = Spsa.update(config, PARAMS, theta, probe, objective.applyAsDouble(probe.plus()),
                    objective.applyAsDouble(probe.minus())).next();
        }
        return theta;
    }

    /** A concave quadratic with its peak at {@code best}, each term counted in the parameter's own steps. */
    private static ToDoubleFunction<long[]> quadratic(long... best) {
        return v -> {
            double total = 0;
            for (int i = 0; i < best.length; i++) {
                double steps = (v[i] - best[i]) / (double) PARAMS.get(i).step();
                total -= 0.5 * steps * steps;
            }
            return total;
        };
    }

    /** A quadratic in one parameter and flat in the rest. */
    private static ToDoubleFunction<long[]> only(int index, long best) {
        return v -> {
            double steps = (v[index] - best) / (double) PARAMS.get(index).step();
            return -0.5 * steps * steps;
        };
    }

    @Test
    @DisplayName("on a quadratic with its peak inside the bounds the rounded state reaches the peak, from far away")
    void converges_on_a_quadratic() {
        long[] best = {650, 9, -2};
        double[] start = {50, 1, 4};
        double[] theta = run(Spsa.Config.of(200), start, 200, quadratic(best));
        long[] found = Spsa.rounded(PARAMS, theta);
        for (int i = 0; i < best.length; i++) {
            assertTrue(Math.abs(found[i] - best[i]) <= PARAMS.get(i).step(),
                    PARAMS.get(i).name() + " ended at " + found[i] + ", the peak is " + best[i]);
        }
    }

    @Test
    @DisplayName("noise on the scores slows it down and does not send it away: the peak is still found within two steps")
    void converges_with_noise() {
        long[] best = {650, 9, -2};
        ToDoubleFunction<long[]> clean = quadratic(best);
        // Noise that depends on the vector played, as luck depends on the Run: deterministic, so the test is.
        ToDoubleFunction<long[]> noisy = v -> clean.applyAsDouble(v)
                + 0.4 * ((Spsa.mix(v[0] * 31 + v[1] * 17 + v[2]) >>> 11) / (double) (1L << 53) - 0.5);
        double[] theta = run(Spsa.Config.of(400), new double[] {50, 1, 4}, 400, noisy);
        long[] found = Spsa.rounded(PARAMS, theta);
        for (int i = 0; i < best.length; i++) {
            assertTrue(Math.abs(found[i] - best[i]) <= 2L * PARAMS.get(i).step(),
                    PARAMS.get(i).name() + " ended at " + found[i] + ", the peak is " + best[i]);
        }
    }

    @Test
    @DisplayName("a peak outside the bounds is reached as the bound, and never passed")
    void a_peak_beyond_a_bound_stops_at_the_bound() {
        // One number at a time. The others take a random walk on the same difference (that is SPSA: every number
        // is credited with the whole score change), so only the number whose peak is out of range is held to it.
        assertEquals(1000, Spsa.rounded(PARAMS, run(Spsa.Config.of(100), new double[] {500, 6, 0}, 100, only(0, 5000)))[0]);
        assertEquals(0, Spsa.rounded(PARAMS, run(Spsa.Config.of(100), new double[] {500, 6, 0}, 100, only(1, -40)))[1]);
        assertEquals(5, Spsa.rounded(PARAMS, run(Spsa.Config.of(100), new double[] {500, 6, 0}, 100, only(2, 30)))[2]);
    }

    @Test
    @DisplayName("a probe stays inside the bounds and its two vectors never agree on a parameter")
    void probes_are_clamped_and_apart() {
        for (int k = 0; k < 50; k++) {
            for (double[] theta : new double[][] {{0, 0, -5}, {1000, 12, 5}, {0.2, 11.9, 4.6}, {500, 6, 0}}) {
                Spsa.Probe probe = Spsa.probe(Spsa.Config.of(50), PARAMS, theta, k);
                for (int i = 0; i < PARAMS.size(); i++) {
                    Spsa.Param p = PARAMS.get(i);
                    assertTrue(probe.plus()[i] >= p.min() && probe.plus()[i] <= p.max(), p.name() + " plus " + probe.plus()[i]);
                    assertTrue(probe.minus()[i] >= p.min() && probe.minus()[i] <= p.max(), p.name() + " minus " + probe.minus()[i]);
                    assertNotEquals(probe.plus()[i], probe.minus()[i], p.name() + " at " + theta[i] + ", iteration " + k);
                    assertEquals(probe.delta()[i] > 0, probe.plus()[i] > probe.minus()[i], "the plus vector is on the side of its sign");
                }
            }
        }
    }

    @Test
    @DisplayName("a perturbation under one unit is widened to one, so a whole-number parameter still gets a slope")
    void integers_get_a_gap() {
        Spsa.Config tiny = new Spsa.Config(4, 0.1, 0, Spsa.ALPHA, Spsa.GAMMA, 2, 1);
        Spsa.Probe probe = Spsa.probe(tiny, PARAMS, new double[] {500, 6, 0}, 0);
        assertEquals(1, Math.abs(probe.plus()[1] - probe.minus()[1]), "narrow: a tenth of a step rounds to nothing, so the gap is one");
        assertEquals(1, Math.abs(probe.plus()[2] - probe.minus()[2]));
    }

    @Test
    @DisplayName("a move is capped at the most it may take, in steps, and the state is clamped and rounded when played")
    void moves_are_capped_and_the_state_clamped() {
        Spsa.Config config = Spsa.Config.of(10);
        double[] theta = {990, 6, 0};
        Spsa.Probe probe = Spsa.probe(config, PARAMS, theta, 0);
        // A difference so large that the uncapped step would leave every range.
        Spsa.Update up = Spsa.update(config, PARAMS, theta, probe, 1.0e9, 0);
        for (int i = 0; i < PARAMS.size(); i++) {
            Spsa.Param p = PARAMS.get(i);
            assertTrue(Math.abs(up.move()[i]) <= config.maxMove() * p.step() + 1e-9, p.name() + " moved " + up.move()[i]);
            assertTrue(up.next()[i] >= p.min() && up.next()[i] <= p.max(), p.name() + " is " + up.next()[i]);
        }
        assertArrayEquals(new long[] {1000, 3, 0},
                Spsa.rounded(PARAMS, new double[] {1000.4, 2.5, -0.4}));
    }

    @Test
    @DisplayName("equal scores do not move the state")
    void a_tie_stays() {
        Spsa.Config config = Spsa.Config.of(10);
        double[] theta = {500, 6, 0};
        Spsa.Probe probe = Spsa.probe(config, PARAMS, theta, 3);
        assertArrayEquals(theta, Spsa.update(config, PARAMS, theta, probe, 7.5, 7.5).next());
    }

    @Test
    @DisplayName("the signs depend on the seed, the iteration and the place and on nothing else, and they are pinned")
    void signs_are_stable() {
        // Pinned: a change to the generator would change every tuning run ever recorded.
        StringBuilder signs = new StringBuilder();
        for (int k = 0; k < 4; k++) {
            for (int i = 0; i < 8; i++) {
                signs.append(Spsa.sign(Spsa.SEED, k, i) > 0 ? '+' : '-');
            }
            signs.append(' ');
        }
        assertEquals("+--+++++ ++-+--+- -+-+--++ +-+++--+", signs.toString().trim());
        long plus = java.util.stream.IntStream.range(0, 4000).filter(n -> Spsa.sign(Spsa.SEED, n / 40, n % 40) > 0).count();
        assertTrue(plus > 1800 && plus < 2200, "the signs are balanced: " + plus + " of 4000 are plus");
    }

    @Test
    @DisplayName("the gain and the perturbation decay with the iteration")
    void schedules_decay() {
        Spsa.Config config = Spsa.Config.of(100);
        assertTrue(Spsa.gain(config, 0) > Spsa.gain(config, 50));
        assertTrue(Spsa.gain(config, 50) > Spsa.gain(config, 99));
        assertTrue(Spsa.perturbation(config, 0) > Spsa.perturbation(config, 99));
        assertEquals(config.perturbation(), Spsa.perturbation(config, 0), 1e-12);
    }
}
