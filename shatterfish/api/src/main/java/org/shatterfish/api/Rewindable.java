package org.shatterfish.api;

/**
 * A {@link Decider} whose state between two decisions can be put back (story 5.1).
 *
 * <p>The Overlay's Run asks its decider on a worker thread while the desktop game keeps drawing, and
 * the screen can change before the answer is served: a scene rebuilt by a resize, or an input the Run
 * did not make. Such an answer is dropped and the same wait is asked again from what is in front
 * now. A decider that carries anything from one decision to the next (a Brain's Belief, a random
 * agent's stream) has by then already moved on from a question that no longer counts, and the second
 * asking would be answered from two Observations, which no headless Run ever does. So the Run takes a
 * mark before it asks, and rewinds to it when it drops the answer.
 *
 * <p>A mark is opaque to the Run: it is taken and handed back on the Run's own thread, never while
 * the decider is deciding, and it is good only for the decider that made it.
 */
public interface Rewindable {

    /** Everything this decider carries from one decision to the next, as a token for {@link #rewind}. */
    Object mark();

    /** Puts back what {@code mark} captured, as if no decision had been asked for since. */
    void rewind(Object mark);
}
