package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * One interruptible thing the Brain can be doing (story 4.1, FR-28).
 *
 * <p>Arbitration asks every Policy afresh at every Input wait, in priority order: the first whose
 * cheap {@link #enters} predicate holds and which offers a {@link RunLog.Choice} takes the wait. A
 * Policy keeps no state of its own between waits -- anything it needs to remember goes through the
 * {@link Memory} -- so one that was interrupted, or whose last Action a human replaced, simply
 * decides again from what is on the screen.
 */
interface Policy {

    /** The Policy's name, as the Decision records it. */
    String name();

    /** What the Policy is for, as the Decision's goal. */
    String goal();

    /** Whether the Policy could act here: cheap, and read from the Observation and memory alone. */
    boolean enters(Observation observation, Memory memory);

    /**
     * The Choice this Policy would take, from among {@code offered}, or null when it has none after
     * all. Only an Action in {@code offered} may be chosen.
     */
    RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream);

    /**
     * Every Choice this Policy would take, best first (story 4.4): the first is its pick, the rest
     * what it would have done otherwise, which the Decision records as alternatives. Only Actions in
     * {@code offered}. A Policy with nothing more to say ranks its one Choice.
     */
    default List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        RunLog.Choice choice = choose(observation, memory, offered, stream);
        return choice == null ? List.of() : List.of(choice);
    }
}
