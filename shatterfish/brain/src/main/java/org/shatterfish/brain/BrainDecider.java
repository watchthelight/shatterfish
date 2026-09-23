package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

/**
 * The Brain as the Run loop drives it (story 4.1): a {@link Deliberator} that carries the Belief
 * from one wait to the next and hands over the Decision behind each Action for the log.
 *
 * <p>It carries the Belief and nothing else. It does not remember which Action it returned, so if
 * that Action was not the one applied -- a human's turn, a refused input -- nothing here assumes
 * otherwise: the next wait's Observation is the only account of what happened.
 */
public final class BrainDecider implements Deliberator {

    private final Brain brain;
    private Belief belief;
    private RunLog.Decision last;
    private String why = "";

    public BrainDecider(Brain brain) {
        if (brain == null) {
            throw new IllegalArgumentException("a Brain to drive");
        }
        this.brain = brain;
    }

    /** The Brain this drives. */
    public Brain brain() {
        return brain;
    }

    @Override
    public Action decide(Observation observation) {
        belief = brain.update(observation, belief);
        Brain.Decided decided = brain.decide(observation, belief);
        last = decided.decision();
        why = decided.why();
        return decided.action();
    }

    /** Why the last {@link #decide} returned no Action, or the empty string when it returned one. */
    public String why() {
        return why;
    }

    @Override
    public RunLog.Decision lastDecision() {
        return last;
    }

    @Override
    public Belief belief() {
        return belief;
    }
}
