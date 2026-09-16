package org.shatterfish.api;

import java.util.List;

/**
 * What a rollout hands back (ADR-0009): the Observations it produced, one per Input wait it
 * reached, and how it ended. Never state: a Brain reads a rollout the way it reads a Run.
 *
 * @param observations the Observation at each wait the rollout reached, in order
 * @param applied how many of the Actions were applied before the rollout stopped, so that a
 *                caller can align the Observations with its Actions when the rollout ended early
 * @param end how the rollout stopped
 */
public record RolloutResult(List<Observation> observations, int applied, RolloutEnd end) {

    public RolloutResult {
        Canon.require(end != null, "a rollout's end");
        Canon.require(applied >= 0, "a rollout applies zero or more Actions: " + applied);
        observations = Canon.positional(observations, "a rollout's observations");
    }
}
