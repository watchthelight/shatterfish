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
    private final Pickup pickup;

    /** The Policies, highest priority first, fighting by {@code knowledge} and scoring by {@code evaluation}. */
    private static List<Policy> policies(Codex.Knowledge knowledge, Evaluation evaluation) {
        return List.of(Policies.ANSWER_PROMPT, new Heal(knowledge), new Fight(knowledge), new Eat(),
                new Pickup(evaluation, knowledge), new Equip(evaluation, knowledge), new Descend(knowledge),
                new Explore(), Policies.fallback(evaluation));
    }

    /**
     * What decides this Brain's behaviour besides its code and its seed: the Policies in priority
     * order, the version of the memory it carries, and the weights it scores by (story 4.5). The
     * rig hashes it into the log header, so two weight sets are two configurations.
     */
    public static String configuration(Weights weights) {
        // A weight set the Evaluation would refuse is no configuration at all.
        new Evaluation(weights);
        return "policies=" + String.join(",", policyNames())
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
        this.policies = policies(knowledge, evaluation);
        this.pickup = new Pickup(evaluation, knowledge);
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

    /** The enemies the fight Policy treats as scenery, by the name the screen shows (story 4.7). */
    public static java.util.Set<String> passiveEnemies() {
        return Fight.PASSIVE;
    }

    /** The name under which the Codex measures the mage's staff, whatever wand it holds (story 4.7). */
    public static String magesStaff() {
        return Fight.MAGES_STAFF;
    }

    /** The foods the eat Policy knows, by the name the inventory shows, and their energy (story 4.9). */
    public static java.util.Map<String, Integer> foods() {
        return Eat.ENERGY;
    }

    /** The foods the Codex names that the eat Policy never eats (story 4.9). */
    public static java.util.Set<String> uneaten() {
        return Eat.UNEATEN;
    }

    /** The names of the Policies every Brain arbitrates, highest priority first. */
    public static List<String> policyNames() {
        return List.of(Policies.ANSWER_PROMPT.name(), Heal.NAME, Fight.NAME, Eat.NAME, Pickup.NAME, Equip.NAME,
                Descend.NAME, Explore.NAME, Policies.FALLBACK);
    }

    /** The Policies, highest priority first, by name. */
    public List<String> policies() {
        return policies.stream().map(Policy::name).toList();
    }

    /** How many waits a region the fight Policy retreated from stays avoided. */
    static final int AVOID_WAITS = 100;

    /**
     * The Belief after this Brain hands over {@code decided} on {@code observation} (stories 4.7, 4.9): the
     * kind of the Action, which the next {@link #update} reads to know what a still hero means, and,
     * when the fight Policy retreated, the region around the nearest enemy, which the explore Policy
     * keeps out of for {@link #AVOID_WAITS} waits so it does not walk straight back into view. The
     * region reaches one past where the enemy was seen from; and, when the heal Policy handed over a
     * drink, the wait it did; and, when the descend Policy handed over a rest, one more rest on this
     * floor. Nothing here assumes the Action is applied.
     */
    public Belief handed(Observation observation, Belief belief, Decided decided) {
        Memory memory = Memory.of(belief).handed(Beliefs.kind(decided.action()));
        RunLog.Decision decision = decided.decision();
        // A drink the heal Policy handed over (story 4.9): the heal lands over the next turns with no
        // buff icon, and the floating heal text the game shows is not in the Observation, so the heal
        // Policy counts the waits since.
        if (decision != null && Heal.NAME.equals(decision.policy())) {
            memory = memory.drinking();
        }
        // A rest the descend Policy handed over before going down (story 4.12), counted toward its bound.
        if (decision != null && Descend.NAME.equals(decision.policy()) && decided.action() instanceof Action.Rest) {
            memory = memory.resting();
        }
        if (decision != null && Fight.NAME.equals(decision.policy())
                && decision.chosen().why().startsWith("retreat ")) {
            java.util.List<org.shatterfish.api.ActorView> enemies = Fight.enemies(observation);
            int hero = observation.hero().cell();
            int width = observation.map().width();
            org.shatterfish.api.ActorView nearest = null;
            int best = Integer.MAX_VALUE;
            for (org.shatterfish.api.ActorView enemy : enemies) {
                int distance = Math.max(Math.abs(hero % width - enemy.cell() % width),
                        Math.abs(hero / width - enemy.cell() / width));
                if (distance < best) {
                    best = distance;
                    nearest = enemy;
                }
            }
            if (nearest != null) {
                memory = memory.avoiding(new Memory.Avoid(observation.header().depth(), observation.header().branch(),
                        nearest.cell(), Math.min(8, best + 1), memory.waits() + AVOID_WAITS));
            }
        }
        return memory.belief();
    }

    /** The Belief after seeing {@code observation}, given the Belief before it (null at the start). */
    public Belief update(Observation observation, Belief belief) {
        // Where the pick-up Policy's plan goes on this screen (story 4.8): computed from the screen
        // and the memory, so the next screen can tell a refused pick-up or Step from a wait spent
        // otherwise.
        Memory memory = Beliefs.fold(Memory.of(belief), observation, knowledge);
        return memory.aiming(pickup.aim(observation, memory)).belief();
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
