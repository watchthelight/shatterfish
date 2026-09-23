package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Brain (story 4.1, FR-27, FR-28): a pure function of what it has seen.
 *
 * <p>At every Input wait the caller hands it the Observation and the Belief it returned last time.
 * {@link #update} folds the Observation into the Belief; {@link #decide} arbitrates the Policies,
 * in priority order, from scratch. Nothing about the Brain's own previous Decision survives the wait
 * except through the Belief, and the Belief holds what was seen, not what was intended -- so when a
 * human takes a turn, or the executor refuses an Action, the next Decision is computed from the
 * Observation the game actually produced.
 *
 * <p>It reads nothing but its arguments and what it was built with: the Codex manifest, which its
 * caller read from disk (the Brain cannot open a file), and a seed, from which its randomness is
 * arithmetic ({@link Stream}).
 */
public final class Brain {

    /** What a wait produced: the Action, the Decision behind it, and, when there is no Action, why. */
    public record Decided(Action action, RunLog.Decision decision, String why) {
    }

    private final Codex.Manifest codex;
    private final long seed;
    private final List<Policy> policies;

    /**
     * @param codex the Codex manifest, read by the caller; the Brain records what it was built on
     * @param seed  the seed of the Brain's stream, from the caller
     */
    public Brain(Codex.Manifest codex, long seed) {
        if (codex == null) {
            throw new IllegalArgumentException("a Brain is built on a Codex its caller read");
        }
        this.codex = codex;
        this.seed = seed;
        this.policies = List.of(Policies.ANSWER_PROMPT, Policies.FALLBACK);
    }

    /** The Codex this Brain was built on. */
    public Codex.Manifest codex() {
        return codex;
    }

    /** The Policies, highest priority first, by name. */
    public List<String> policies() {
        return policies.stream().map(Policy::name).toList();
    }

    /** The Belief after seeing {@code observation}, given the Belief before it (null at the start). */
    public Belief update(Observation observation, Belief belief) {
        Memory memory = Memory.of(belief);
        return new Memory(memory.waits() + 1, Math.max(memory.deepest(), observation.header().depth())).belief();
    }

    /**
     * The Decision for {@code observation} under {@code belief}: the first Policy, in priority order,
     * that enters and offers a Choice takes the wait; the next up to three that would also have
     * acted are its recorded alternatives.
     */
    public Decided decide(Observation observation, Belief belief) {
        Memory memory = Memory.of(belief);
        List<Action> offered = observation.actions().actions();
        if (offered.isEmpty()) {
            return new Decided(null, null, "the screen offers no Action");
        }
        Policy taken = null;
        RunLog.Choice chosen = null;
        List<RunLog.Choice> alternatives = new ArrayList<>();
        for (Policy policy : policies) {
            if (!policy.enters(observation, memory)) {
                continue;
            }
            // Each Policy draws from its own copy of the wait's stream, so whether an earlier
            // Policy drew changes nothing a later one chooses.
            RunLog.Choice choice = policy.choose(observation, memory, offered, Stream.at(seed, memory.waits()));
            if (choice == null) {
                continue;
            }
            if (!offered.contains(choice.action())) {
                throw new IllegalStateException("the Policy " + policy.name() + " chose " + choice.action()
                        + ", which the screen does not offer");
            }
            if (taken == null) {
                taken = policy;
                chosen = choice;
            } else if (alternatives.size() < RunLog.Decision.ALTERNATIVES) {
                alternatives.add(choice);
            }
        }
        if (taken == null) {
            return new Decided(null, null, "no Policy offered a Choice among " + offered.size() + " Actions");
        }
        return new Decided(chosen.action(),
                new RunLog.Decision(taken.goal(), chosen, alternatives, List.of(), taken.name()), "");
    }
}
