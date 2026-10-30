package org.shatterfish.api;

/**
 * A {@link Decider} that can use a {@link Lookahead} when the loop that drives it has one to lend (story 10.24). The Run
 * loop of the Rig lends one at each Input wait before it asks for the Action and takes it back after; the Overlay's loop
 * lends none (ADR-0025: no-go for the Overlay), and a Brain without one decides as its rules do.
 */
public interface Searching {

    /** Whether this decider would use a look-ahead, so that the loop need not build the machinery for one that would not. */
    boolean searches();

    /** The look-ahead for the wait about to be decided, or null for none; called with null after the wait. */
    void lookahead(Lookahead lookahead);
}
