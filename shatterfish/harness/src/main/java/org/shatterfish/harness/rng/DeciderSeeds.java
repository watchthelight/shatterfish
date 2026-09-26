package org.shatterfish.harness.rng;

import org.shatterfish.api.HeroClass;

/**
 * The seeds a decider's stream starts from, for every place that builds one: the Rig
 * ({@code Brains.agentSeed}, {@code Brains.brainSeed}) and the Overlay ({@code OverlayAgents}), which
 * may not depend on each other and must seed the same decider the same way for the same Run
 * (story 4.1's fairness rule, story 5.1).
 *
 * <p>Two seeds, for two kinds of decider. A random agent's is a function of the Run's own triple (the
 * dungeon seed, the hero class and the challenges), which a human at the same screen has, and never
 * the salt. A Brain's is its name mixed with a constant and nothing about the Run: the triple's mix is
 * a bijection of the dungeon seed and two values the Observation header states, so a Brain holding it
 * could undo it and read the seed (see {@code Brains.brainSeed}).
 */
public final class DeciderSeeds {

    /** Mixed into a Brain's name to give its stream a seed. */
    public static final long BRAIN_STREAM = 0x5F15_B4A1L;

    private DeciderSeeds() {
    }

    /** The seed of the Brain whose canonical name is {@code name}. */
    public static long brain(String name) {
        return Mix.mix(BRAIN_STREAM, name.hashCode());
    }

    /** The seed of a random agent playing this triple. */
    public static long agent(long seed, HeroClass heroClass, int challengeFlags) {
        return Mix.mix(Mix.mix(seed, heroClass.ordinal()), challengeFlags);
    }
}
