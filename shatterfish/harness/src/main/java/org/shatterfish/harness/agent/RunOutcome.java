package org.shatterfish.harness.agent;

/**
 * How a Run ended and what it did on the way. Every Run ends for exactly one reason, and the reason
 * is a value rather than a thrown exception, because a Run that ends badly is a result to count and
 * not a crash to debug: a thousand of these are a tally, and a tally with a hole in it says nothing.
 *
 * @param cause   the one reason this Run ended
 * @param depth   the deepest floor reached, as the game counts it ({@code core/.../Statistics.java:33})
 * @param turns   the turns passed, as the game counts them ({@code core/.../actors/Actor.java:196})
 * @param waits   the Input waits served
 * @param applied the Actions the executor applied
 * @param refused the Actions the executor refused, which change nothing and leave the wait open
 * @param detail  what a person would need to know beyond the cause, or empty
 */
public record RunOutcome(Cause cause, int depth, int turns, long waits, long applied, long refused,
                         String detail) {

    /** The reasons a Run ends. */
    public enum Cause {

        /** The hero died, which is how a Run is meant to end. */
        DEATH,

        /** The hero carried the amulet out, which is the other way a Run is meant to end. */
        WIN,

        /** The Run was still alive at the turn cap, so it was stopped and counted as a loss. */
        TURN_CAP,

        /**
         * The game asked for a scene the loop does not know how to serve. The Run stops rather than
         * guessing at what the game meant by it; {@link #detail} names the scene.
         */
        UNSERVED_SCENE,

        /**
         * A window the Prompt table does not name was in front, so no wait could follow and no
         * Action could move it. {@link #detail} names the window's class.
         */
        UNKNOWN_WINDOW,

        /**
         * The executor refused too many Actions in a row for the Run to be going anywhere, which
         * means the valid set and the game disagree. {@link #detail} carries the last reason.
         */
        REFUSED,

        /** A wait offered no Action at all, which the valid set is not supposed to produce. */
        NOTHING_OFFERED
    }

    /** Whether this Run ended the way a Run is meant to end, rather than by something going wrong. */
    public boolean ordinary() {
        return cause == Cause.DEATH || cause == Cause.WIN || cause == Cause.TURN_CAP;
    }

    @Override
    public String toString() {
        return cause + " on floor " + depth + " after " + turns + " turns and " + waits + " waits ("
                + applied + " applied, " + refused + " refused)" + (detail.isEmpty() ? "" : ": " + detail);
    }
}
