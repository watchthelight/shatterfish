package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;

import java.util.List;
import java.util.Random;

/**
 * Takes a uniformly random Action from the set the screen offers. This is not a Brain and never
 * becomes one: it lives in {@code harness} because it is a tool for exercising the loop, and
 * {@code brain} stays empty until an epic gives it something to hold (epic 1's own rule).
 *
 * <p>It is deliberately without preference. A chooser that liked stepping over resting, or that
 * skipped an Action kind it found awkward, would put its taste into every number measured through
 * it — the depth a random Run reaches is a property of the game, and it stops being one the moment
 * the agent has an opinion. The only thing it reads is {@link Observation#actions()}, which is the
 * same door the Brain will use.
 */
public final class RandomAgent implements Decider {

    private final Random choices;

    /** An agent whose stream is this seed's, so a Run of the same tuple makes the same choices. */
    public RandomAgent(long seed) {
        this.choices = new Random(seed);
    }

    /**
     * The Action to take at this wait, or null when the screen offers nothing at all — which is not
     * a case the valid set is supposed to produce, and the caller ends the Run by name rather than
     * inventing an input for it.
     */
    @Override
    public Action decide(Observation observation) {
        List<Action> offered = observation.actions().actions();
        if (offered.isEmpty()) {
            return null;
        }
        return offered.get(choices.nextInt(offered.size()));
    }
}
