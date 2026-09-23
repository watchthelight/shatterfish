package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;

import java.util.List;
import java.util.Random;

/**
 * The random agent with one kind of Action taken out of what it may choose: the deliberately worse
 * Brains of story 3.9 (SM-5), whose job is to be told apart from the Baseline by the Rig.
 *
 * <p>It is {@link RandomAgent} in every other respect -- the same uniform choice, the same seed, the
 * same stream -- so the pair it forms with the Baseline differs in exactly one thing. Until the
 * withheld kind is first offered the two make the same draws from the same offered sets and play
 * the same Run; from there they part.
 *
 * <p>When the withheld kind is all the screen offers there is nothing left to choose, and it returns
 * null, which the caller ends the Run on by name, as it does for an empty offered set.
 */
public final class WithholdingAgent implements Decider {

    private final Random choices;

    private final Class<? extends Action> withheld;

    /** The random agent less {@code withheld}, whose stream is this seed's. */
    public WithholdingAgent(long seed, Class<? extends Action> withheld) {
        if (withheld == null) {
            throw new IllegalArgumentException("a kind of Action to withhold");
        }
        this.choices = new Random(seed);
        this.withheld = withheld;
    }

    @Override
    public Action decide(Observation observation) {
        List<Action> offered = observation.actions().actions().stream()
                .filter(action -> !withheld.isInstance(action))
                .toList();
        if (offered.isEmpty()) {
            return null;
        }
        return offered.get(choices.nextInt(offered.size()));
    }
}
