package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;

import java.util.List;
import java.util.Random;

/**
 * The random agent with {@code Descend} taken out of what it may choose: the deliberately worse Brain
 * of story 3.9 (SM-5), whose one job is to be rejected by the Rig.
 *
 * <p>It is {@link RandomAgent} in every other respect -- the same uniform choice, the same seed, the
 * same stream -- so the pair it forms with the Baseline differs in exactly one thing. Until a
 * {@code Descend} is first offered the two make the same draws from the same offered sets and play
 * the same Run; from there they part, and one of them can never leave the floor.
 *
 * <p>When {@code Descend} is all the screen offers there is nothing left to choose, and it returns
 * null, which the caller ends the Run on by name, as it does for an empty offered set.
 */
public final class NoDescendAgent implements Decider {

    private final Random choices;

    /** An agent whose stream is this seed's, as {@link RandomAgent}'s is. */
    public NoDescendAgent(long seed) {
        this.choices = new Random(seed);
    }

    @Override
    public Action decide(Observation observation) {
        List<Action> offered = observation.actions().actions().stream()
                .filter(action -> !(action instanceof Action.Descend))
                .toList();
        if (offered.isEmpty()) {
            return null;
        }
        return offered.get(choices.nextInt(offered.size()));
    }
}
