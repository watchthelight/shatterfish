package org.shatterfish.api;

/**
 * A {@link Decider} that also says why (story 4.1, ADR-0011): after each {@link #decide} it reports
 * the Decision behind the Action it returned and the Belief it now holds, so the Run loop can write
 * both into the wait's record without knowing what either means.
 *
 * <p>It is an {@code api} interface for the reason {@link Decider} is: a Brain may depend on
 * nothing else, and the harness logs what a Brain hands it through types both can see. The random
 * agent is not one; it has no reasons, and a Decision half filled in would read as a Brain that had
 * thought about it.
 */
public interface Deliberator extends Decider {

    /** The Decision behind the last Action {@link #decide} returned, or null before the first or when it returned null. */
    RunLog.Decision lastDecision();

    /** The Belief held after the last {@link #decide}, or null before the first. */
    Belief belief();

    /**
     * The hash of {@link #belief()} as the Run log records it, or the empty string when there is
     * none. A replay follows a log without the Belief behind it, only its hash, and says so here.
     */
    default String beliefHash() {
        return belief() == null ? "" : belief().hash();
    }
}
