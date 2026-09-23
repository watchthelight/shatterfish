package org.shatterfish.brain;

/**
 * The Brain's randomness: a SplitMix64 stream over a seed its caller gave it (story 4.1).
 *
 * <p>The Brain may not own a generator (BrainBoundaryTest denies {@code java.util.Random} and
 * {@code java.util.random}): a generator it seeded itself would be a second source of variation a
 * Run's (tag, seed, action list) does not name. This is arithmetic on a number it was handed, and
 * the stream for a wait is a function of that number and the wait's index, so a Decision never
 * depends on how many draws an earlier wait happened to make.
 */
final class Stream {

    private long state;

    private Stream(long state) {
        this.state = state;
    }

    /** The stream for wait {@code k} of a Brain seeded {@code seed}. */
    static Stream at(long seed, long k) {
        return new Stream(mix(seed + k * 0x9E3779B97F4A7C15L));
    }

    long next() {
        state += 0x9E3779B97F4A7C15L;
        return mix(state);
    }

    /** A draw in [0, bound), without the bias a plain modulo would carry for bounds that are not powers of two. */
    int below(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("a bound is positive: " + bound);
        }
        // A draw from the top 63 bits, rejected when it falls in the last partial block of `bound`,
        // which is the test java.util.Random uses (the sum overflows exactly then).
        while (true) {
            long draw = next() >>> 1;
            long value = draw % bound;
            if (draw - value + (bound - 1) >= 0) {
                return (int) value;
            }
        }
    }

    /** SplitMix64's finalizer. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
