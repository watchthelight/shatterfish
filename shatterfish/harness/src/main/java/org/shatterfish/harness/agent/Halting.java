package org.shatterfish.harness.agent;

/**
 * A decider that can say the Run must go no further (story 8.5): the log a replay follows has a wait whose
 * Observation this build does not see, or has nothing more to say.
 *
 * <p>A decider that only throws is answered, in the Overlay, with a wait the Run would execute (story 5.15: the
 * game never crashes because of the Brain), and a replay that executed a wait nobody recorded would play on past
 * the point where it stopped agreeing with the log. So the Run asks a {@code Halting} decider before it executes
 * anything it decided, and ends instead of executing when it has halted.
 */
public interface Halting {

    /** Why a replay stopped. */
    enum Kind {
        /** The Observation at a wait is not the one the log recorded. */
        MISMATCH,
        /** The log records an input the executor could not express, so nothing from there can be reproduced. */
        UNVERIFIABLE,
        /** The log has no wait left to apply. */
        END
    }

    /**
     * Where and why the decider halted.
     *
     * @param at   the wait the replay stopped at: the one that differs, or the first the log does not hold
     * @param kind why
     * @param why  the reason in a short phrase, which the Mode strip runs into its line
     * @param detail the whole message, with the hashes, for the console
     */
    record Halt(long at, Kind kind, String why, String detail) {
    }

    /** The halt, or null while the decider plays on. */
    Halt halted();

    /**
     * Compares how the Run ended on its own (it died, won or reached the cap) with how the log says it ended.
     * A difference is a mismatch like any other: it becomes the halt, and the phrase for the Run-over line is
     * returned. The Run asks only when nothing has halted it, and never for a Run a person closed.
     *
     * @param cause       the Run's ending cause, as the log's end record names it
     * @param depth       the floor it ended on
     * @param thousandths the turns it took, in the unit the log's end record counts them
     * @return what differs, or null when the endings agree or the decider has no ending to compare
     */
    default String checkEnding(String cause, int depth, long thousandths) {
        return null;
    }
}
