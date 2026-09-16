package org.shatterfish.api;

import java.util.List;

/**
 * What a rollout hands back (ADR-0009): the Observations it produced, one per Input wait it
 * reached, and how it ended. Never state: a Brain reads a rollout the way it reads a Run.
 *
 * @param observations the Observation at each wait the rollout reached, in order
 * @param end how the rollout stopped
 */
public record RolloutResult(List<Observation> observations, RolloutEnd end) {

    public RolloutResult {
        Canon.require(observations != null, "a rollout's observations");
        Canon.require(end != null, "a rollout's end");
        observations = List.copyOf(observations);
    }
}
