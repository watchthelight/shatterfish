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

    /**
     * What a decider throws when it meets a screen it has no rule for (story 4.11): the one exception
     * the Run loop turns into an ending of its own, {@code BRAIN_ERROR}, with the message in the log.
     * Any other exception from a decider -- a Replay that diverged, a bug -- is not a decision the
     * decider declined to make, and is left to propagate.
     */
    class CannotDecide extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CannotDecide(String message) {
            super(message);
        }
    }
}
