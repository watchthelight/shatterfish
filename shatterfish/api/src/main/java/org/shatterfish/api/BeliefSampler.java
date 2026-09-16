package org.shatterfish.api;

/**
 * Draws one {@link BeliefSample} from a Brain's belief at an Observation (ADR-0009). Reserved in
 * E1 with no implementation; a Brain that searches supplies one in E6.
 */
@FunctionalInterface
public interface BeliefSampler {

    /**
     * One sample of what the player cannot know, consistent with {@code observation}, drawn with
     * {@code draw} so that a rollout is reproducible.
     */
    BeliefSample sample(Belief belief, Observation observation, long draw);
}
