package org.shatterfish.api;

/**
 * What takes the Actions of a rollout one wait at a time (story 10.24, ADR-0025): the closed-loop sibling of the
 * list of Actions a {@link Simulator} plays. It is handed the Observation at each wait the rollout reaches, as a Run's
 * decider is, and nothing else, so a playout cannot read the rollout world any more than a Brain can read the live one.
 */
@FunctionalInterface
public interface Playout {

    /**
     * The Action to take at this wait, or null to stop the rollout here.
     *
     * @param observation the Observation at the wait
     * @param applied how many Actions the rollout has applied so far, from 0
     */
    Action next(Observation observation, int applied);
}
