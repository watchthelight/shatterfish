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

    /** The Policies, highest priority first. */
    private static final List<Policy> POLICIES = List.of(Policies.ANSWER_PROMPT, Policies.FALLBACK);

    private final Codex.Knowledge knowledge;
    private final long seed;
    private final List<Policy> policies;

    /**
     * What decides this Brain's behaviour besides its code and its seed: the Policies in priority
     * order and the version of the memory it carries. The rig hashes it into the log header.
     */
    public static String configuration() {
        return "policies=" + String.join(",", POLICIES.stream().map(Policy::name).toList())
                + ";memory=" + Memory.VERSION;
    }

    /**
     * @param knowledge the Codex's general game knowledge, read by the caller (story 4.2)
     * @param seed      the seed of the Brain's stream, from the caller
     */
    public Brain(Codex.Knowledge knowledge, long seed) {
        if (knowledge == null) {
            throw new IllegalArgumentException("a Brain is built on a Codex its caller read");
        }
        this.knowledge = knowledge;
        this.seed = seed;
        this.policies = POLICIES;
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
        for (int index = 0; index < policies.size(); index++) {
            Policy policy = policies.get(index);
            if (!policy.enters(observation, memory)) {
                continue;
            }
            // Each Policy draws from a stream of its own, keyed on its place in the list and the
            // wait, so whether an earlier Policy drew changes nothing a later one chooses, and two
            // Policies that both draw do not draw the same numbers.
            RunLog.Choice choice = policy.choose(observation, memory, offered,
                    Stream.at(Stream.mix(seed + index), memory.waits()));
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
            } else if (alternatives.size() < RunLog.Decision.ALTERNATIVES
                    && !choice.action().equals(chosen.action())
                    && alternatives.stream().noneMatch(other -> other.action().equals(choice.action()))) {
                // An alternative is another Action a Policy would have taken; the same Action again
                // is not one.
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
