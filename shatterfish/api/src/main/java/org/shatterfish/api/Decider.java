package org.shatterfish.api;

/**
 * What takes the decision at an Input wait: an Observation in, an Action out, and nothing else.
 *
 * <p>It lives in {@code api} because that is the only module a Brain may depend on (AD-1, and the
 * build fails if {@code brain} reaches further). The harness's own random agent implements it, the
 * rig will drive it, and a Brain will be one — so the seam a Brain arrives at is declared where a
 * Brain can see it, rather than inside the module that imports the game.
 *
 * <p>The Observation is the whole of what a decider may read. That is information parity as
 * architecture rather than intention: there is no second argument, and no way to ask for one.
 */
@FunctionalInterface
public interface Decider {

    /**
     * The Action to take at this wait, or null when the screen offers nothing at all — which the
     * valid set is not supposed to produce, and which the caller ends the Run by name rather than
     * inventing an input for.
     */
    Action decide(Observation observation);
}
