package org.shatterfish.harness.rng;

/**
 * The seed a Brain's stream starts from, for every place that builds a Brain: the Rig
 * ({@code Brains.brainSeed}) and the Overlay ({@code OverlayAgents}), which may not depend on each
 * other and must seed the same Brain the same way (story 4.1's fairness rule, story 5.1).
 *
 * <p>The seed is the Brain's name mixed with a constant, and nothing about the Run: a seed mixed from
 * the dungeon seed could be undone by a Brain holding it (see {@code Brains.brainSeed}).
 */
public final class BrainSeed {

    /** Mixed into a Brain's name to give its stream a seed. */
    public static final long STREAM = 0x5F15_B4A1L;

    private BrainSeed() {
    }

    /** The seed of the Brain whose canonical name is {@code name}. */
    public static long of(String name) {
        return Mix.mix(STREAM, name.hashCode());
    }
}
