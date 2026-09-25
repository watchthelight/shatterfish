package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Weights;

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
 * <p>It reads nothing but its arguments and what it was built with: the Codex's knowledge and the
 * Evaluation's weights, which its caller read from disk (the Brain cannot open a file), and a seed,
 * from which its randomness is arithmetic ({@link Stream}).
 */
public final class Brain {

    /**
     * What a wait produced: the Action, the Decision behind it, the map cells it points at (story
     * 4.4), and, when there is no Action, why.
     */
    public record Decided(Action action, RunLog.Decision decision, List<Integer> highlights, String why) {

        public Decided {
            highlights = List.copyOf(highlights);
        }
    }

    private final Codex.Knowledge knowledge;
    private final Evaluation evaluation;
    private final long seed;
    private final List<Policy> policies;

    /** The Policies, highest priority first, scoring by {@code evaluation} over {@code knowledge}. */
    private static List<Policy> policies(Evaluation evaluation, Codex.Knowledge knowledge) {
        return List.of(Policies.ANSWER_PROMPT, new Pickup(evaluation, knowledge), new Equip(evaluation, knowledge),
                new Explore(), Policies.fallback(evaluation));
    }

    /**
     * What decides this Brain's behaviour besides its code and its seed: the Policies in priority
     * order, the version of the memory it carries, and the weights it scores by (story 4.5). The
     * rig hashes it into the log header, so two weight sets are two configurations.
     */
    public static String configuration(Weights weights) {
        return "policies=" + String.join(",", policies(new Evaluation(weights), Codex.Knowledge.of(new Codex.Manifest(Codex.VERSION, "v0", List.of()))).stream().map(Policy::name).toList())
                + ";memory=" + Memory.VERSION + ";weights=" + weights.canonical();
    }

    /**
     * @param knowledge the Codex's general game knowledge, read by the caller (story 4.2)
     * @param weights   the Evaluation's weights, read by the caller (story 4.5)
     * @param seed      the seed of the Brain's stream, from the caller
     */
    public Brain(Codex.Knowledge knowledge, Weights weights, long seed) {
        if (knowledge == null) {
            throw new IllegalArgumentException("a Brain is built on a Codex its caller read");
        }
        this.knowledge = knowledge;
        this.evaluation = new Evaluation(weights);
        this.seed = seed;
        this.policies = policies(evaluation, knowledge);
    }

    /** The weights this Brain scores by. */
    public Weights weights() {
        return evaluation.weights();
    }

    /** The seed of this Brain's stream, as its caller gave it. */
    public long seed() {
        return seed;
    }

    /** The Codex this Brain was built on. */
    public Codex.Manifest codex() {
        return knowledge.manifest();
    }

    /** What this Brain believes at {@code observation}, holding {@code belief} (the one {@link #update} returned for it). */
    public Beliefs beliefs(Observation observation, Belief belief) {
        return Beliefs.view(Memory.of(belief), observation, knowledge);
    }

    /**
     * Every Safety flag a Decision can carry, by label (story 4.4): what the Brain's Rules index has
     * to account for, alongside the Policies.
     */
    public static List<String> safetyFlags() {
        return Safety.ALL;
    }

    /** The names of the Policies every Brain arbitrates, highest priority first. */
    public static List<String> policyNames() {
        return List.of(Policies.ANSWER_PROMPT.name(), Pickup.NAME, Equip.NAME, Explore.NAME, Policies.FALLBACK);
    }

    /** The Policies, highest priority first, by name. */
    public List<String> policies() {
        return policies.stream().map(Policy::name).toList();
    }

    /** The Belief after seeing {@code observation}, given the Belief before it (null at the start). */
    public Belief update(Observation observation, Belief belief) {
        return Beliefs.fold(Memory.of(belief), observation, knowledge).belief();
    }

    /**
     * The Decision for {@code observation} under {@code belief}: the first Policy, in priority order,
     * that enters and ranks a Choice takes the wait with its best one. Up to three alternatives are
     * recorded, distinct Actions all: the taking Policy's own next ranks first, then what later
     * Policies would have done (story 4.4). The Decision carries the screen's Safety flags, and
     * the Decided the cells the chosen Action points at.
     */
    public Decided decide(Observation observation, Belief belief) {
        Memory memory = Memory.of(belief);
        List<Action> offered = observation.actions().actions();
        if (offered.isEmpty()) {
            return new Decided(null, null, List.of(), "the screen offers no Action");
        }
        Policy taken = null;
        RunLog.Choice chosen = null;
        List<RunLog.Choice> alternatives = new ArrayList<>();
        for (Policy policy : policies) {
            if (!policy.enters(observation, memory)) {
                continue;
            }
            // Each Policy draws from a stream of its own, keyed on its name and the wait, so
            // whether an earlier Policy drew changes nothing a later one chooses, two Policies that
            // both draw do not draw the same numbers, and a Policy added to the list later leaves
            // every other Policy's draws as they were (story 4.6; the key was the list position
            // before, which a new Policy would have shifted).
            List<RunLog.Choice> ranked = policy.ranked(observation, memory, offered,
                    Stream.at(Stream.mix(seed ^ Stream.mix(policy.name().hashCode())), memory.waits()));
            for (RunLog.Choice choice : ranked) {
                if (!offered.contains(choice.action())) {
                    throw new IllegalStateException("the Policy " + policy.name() + " chose " + choice.action()
                            + ", which the screen does not offer");
                }
                if (taken == null) {
                    taken = policy;
                    chosen = choice;
                } else if (alternatives.size() < RunLog.Decision.ALTERNATIVES
                        && !choice.action().equals(chosen.action())
                        && alternatives.stream().noneMatch(other -> other.action().equals(choice.action()))) {
                    // An alternative is another Action a Policy would have taken; the same Action
                    // again is not one.
                    alternatives.add(choice);
                }
            }
        }
        if (taken == null) {
            return new Decided(null, null, List.of(),
                    "no Policy offered a Choice among " + offered.size() + " Actions");
        }
        return new Decided(chosen.action(),
                new RunLog.Decision(taken.goal(), chosen, alternatives, Safety.flags(observation), taken.name()),
                Highlights.of(chosen.action()), "");
    }
}
