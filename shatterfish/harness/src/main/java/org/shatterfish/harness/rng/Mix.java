package org.shatterfish.harness.rng;

/**
 * The one function that turns a Run's salt and a wait index into the seed that wait draws from.
 *
 * <p>It is written here exactly as ADR-0007 defines it, and it is published with a test vector on
 * the methodology page, because the whole point of it is that a skeptic can recompute it. A Run
 * says which salt it used and which wait it was at; anyone can then say which numbers the game
 * should have drawn, without running anything of ours.
 *
 * <p>The definition is {@code splitmix64_finalize(salt + k * 0x9E3779B97F4A7C15)}: the golden-ratio
 * odd constant that SplitMix64 advances its state by, and then that algorithm's own finalizer,
 * which is what makes two nearby inputs give two unrelated outputs. Nothing here is Shatterfish's
 * invention, which is the reason to use it: it is checkable against any other implementation of
 * SplitMix64 in any language.
 *
 * <p>What happens to this value afterwards is the game's business, not ours: the game scrambles
 * every seed it is given through MX3 before {@code java.util.Random} sees it
 * ({@code SPD-classes/.../utils/Random.java:55-65}). The vector is of the mix, and stops there.
 */
public final class Mix {

    /** SplitMix64's increment: the odd 64-bit approximation of the golden ratio. */
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Mix() {
    }

    /**
     * The seed for wait {@code k} of a Run salted {@code salt}.
     *
     * <p>Both arguments are taken as they are: the salt is a full 64-bit value drawn by whoever
     * runs the Run, and {@code k} counts the Input waits from the first one. Overflow in the
     * multiplication and the addition is the algorithm, not an accident.
     */
    public static long mix(long salt, long k) {
        return finalize(salt + k * GOLDEN_GAMMA);
    }

    /**
     * SplitMix64's finalizer, written as ADR-0007 writes it. The shifts are unsigned, which in Java
     * is {@code >>>} and not {@code >>}; with the arithmetic shift the function is a different one
     * and the vector will say so.
     */
    private static long finalize(long state) {
        long z = state;
        z ^= z >>> 30;
        z *= 0xBF58476D1CE4E5B9L;
        z ^= z >>> 27;
        z *= 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return z;
    }
}
