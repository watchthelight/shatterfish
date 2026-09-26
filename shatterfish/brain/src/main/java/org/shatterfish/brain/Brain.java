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
    private final TestItem testItem;

    /** The Policies, highest priority first, fighting by {@code knowledge} and scoring by {@code evaluation}. */
    private static List<Policy> policies(Codex.Knowledge knowledge, Evaluation evaluation) {
        return List.of(Policies.answerPrompt(knowledge), new Heal(knowledge), new Fight(knowledge), new Eat(),
                new TestItem(knowledge), new Pickup(evaluation, knowledge), new Equip(evaluation, knowledge),
                new Descend(knowledge),
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
        this.testItem = new TestItem(knowledge);
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
        return List.of(Policies.ANSWER_PROMPT, Heal.NAME, Fight.NAME, Eat.NAME, TestItem.NAME, Pickup.NAME,
                Equip.NAME, Descend.NAME, Explore.NAME, Policies.FALLBACK);
    }

    /** The Policies, highest priority first, by name. */
    public List<String> policies() {
        return policies.stream().map(Policy::name).toList();
    }

    /** How many waits a region the fight Policy retreated from stays avoided. */
    static final int AVOID_WAITS = 100;

    /** The kinds of Action whose window, once left, is shunned on the floor (story 4.11). */
    static final java.util.Set<String> OPENERS = java.util.Set.of("Interact", "Buy", "UseItem", "UseItemOn",
            "UseItemAt");

    /**
     * The Belief after this Brain hands over {@code decided} on {@code observation} (stories 4.7, 4.9): the
     * kind of the Action, which the next {@link #update} reads to know what a still hero means, and,
     * when the fight Policy retreated, the region around the nearest enemy, which the explore Policy
     * keeps out of for {@link #AVOID_WAITS} waits so it does not walk straight back into view. The
     * region reaches one past where the enemy was seen from; and, when the heal Policy handed over a
     * drink, the wait it did; and, when the descend Policy handed over a rest, one more rest on this
     * floor. Nothing here assumes the Action is applied.
     *
     * <p>Story 4.11 adds the windows: the Action itself and the item it was used on, which the next
     * screen reads to tell the window a read opened from one the game chained after it; and, when the
     * prompt Policy leaves a shop, a guess or a spell list, the Action that opened it, shunned on this
     * floor so the Brain does not reopen it (a window left takes no time, so reopening it forever
     * would pass none either). Only an Action that can open such a window is shunned
     * ({@link #OPENERS}): a talk with the shopkeeper, a purchase, an item's use. A Step onto an item
     * for sale opens the trade window too, but the hero has moved by then and shunning a Step could
     * wall off a floor; and the opener is the Brain's own last Action, so a window a person opened
     * over the Brain's turn would otherwise shun whatever the Brain did before it.
     *
     * <p>Issue #174 adds three: a retreat by the stairs sets a region too; a Step the explore or the
     * descend Policy hands over into a region lifts it, since they plan through one only when there is
     * no way around; and an approach records where its enemy stands, the fight Policy's chase.
     */
    public Belief handed(Observation observation, Belief belief, Decided decided) {
        Memory before = Memory.of(belief);
        Memory memory = before.handed(Beliefs.kind(decided.action()),
                decided.action() instanceof Action.Step step ? step.cell() : -1);
        RunLog.Decision decision = decided.decision();
        Memory.Windows windows = memory.windows();
        java.util.List<Memory.Shun> shunned = windows.shunned();
        if (decided.action() instanceof Action.DismissPrompt && decision != null
                && decision.chosen().why().startsWith(Answers.LEAVE) && !windows.opener().isEmpty()
                && OPENERS.contains(windows.opener().substring(0, Math.max(0, windows.opener().indexOf('['))))) {
            Memory.Shun shun = new Memory.Shun(observation.header().depth(), observation.header().branch(),
                    windows.opener());
            if (!shunned.contains(shun)) {
                shunned = new ArrayList<>(shunned);
                shunned.add(shun);
                while (shunned.size() > Memory.Windows.SHUNNED) {
                    shunned.remove(0);
                }
            }
        }
        // The item a read went onto is kept while the Brain answers the windows it opened, so the
        // upgrade window chained after it can still name the item.
        String target = decided.action() instanceof Action.UseItemOn on ? on.target().name()
                : decided.action() instanceof Action.AnswerPrompt || decided.action() instanceof Action.DismissPrompt
                ? windows.target() : "";
        // So is whether it was the worn armour, by its slot on this screen (story 4.13).
        boolean worn = decided.action() instanceof Action.UseItemOn on ? TestItem.worn(observation, on.target())
                : (decided.action() instanceof Action.AnswerPrompt || decided.action() instanceof Action.DismissPrompt)
                && windows.worn();
        memory = memory.windowing(new Memory.Windows(decided.action() == null ? "" : decided.action().toString(),
                target, worn, windows.opener(), shunned));
        // A drink the heal Policy handed over (story 4.9): the heal lands over the next turns with no
        // buff icon, and the floating heal text the game shows is not in the Observation, so the heal
        // Policy counts the waits since.
        if (decision != null && Heal.NAME.equals(decision.policy())) {
            memory = memory.drinking();
        }
        // A test handed over (story 4.10): which appearance, how many were held, and the Step's cell
        // when it walks to a testing cell, so the next screen can tell a test the game refused.
        if (decision != null && TestItem.NAME.equals(decision.policy())) {
            Memory.Trial trial = testItem.trial(observation, before, decided.action());
            memory = memory.trying(trial, trial.step() < 0 && !trial.label().isEmpty()
                    ? SafeTest.refuge(observation, observation.hero().cell()) : -1);
        }
        // A rest the descend Policy handed over before going down (story 4.12), counted toward its bound.
        if (decision != null && Descend.NAME.equals(decision.policy()) && decided.action() instanceof Action.Rest) {
            memory = memory.resting();
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        // The chase (issue #174): an approach records the enemy it goes for, where the screen shows it; a
        // chase keeps it; anything else the fight Policy hands over -- an attack, a retreat, a hold -- ends it.
        if (decision != null && Fight.NAME.equals(decision.policy())) {
            String why = decision.chosen().why();
            if (why.startsWith("approach ") && Fight.quarry(observation) >= 0) {
                memory = memory.chasing(new Memory.Spot(depth, branch, Fight.quarry(observation)), memory.waits());
            } else if (!why.startsWith("chase ")) {
                memory = memory.chasing(Memory.Spot.NOWHERE, -1);
            }
        }
        // A Step into a region the fight Policy retreated from, handed over by the explore or the descend
        // Policy, which plan around the regions and through one only when no way around is left (issue
        // #174): the region is crossed, and lifted. Explore's own way out of a region ("away") is no crossing.
        if (decision != null && (Explore.NAME.equals(decision.policy()) || Descend.NAME.equals(decision.policy()))
                && decided.action() instanceof Action.Step step && !decision.chosen().why().startsWith("away ")) {
            memory = memory.lifting(depth, branch, step.cell(), observation.map().width());
        }
        // A retreat, by the stairs or away (issue #174 added the stairs: an enemy that drops out of view
        // one Step toward them let the explore Policy walk straight back, with no region to keep it out).
        if (decision != null && Fight.NAME.equals(decision.policy())
                && decision.chosen().why().startsWith("retreat")) {
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
        // The test-item Policy ranks above pick-up (story 4.10): when it plans on this screen, pick-up
        // does not take the wait, and its plan is no plan.
        Memory.Aim aim = testItem.plan(observation, memory, observation.actions().actions()) != null
                ? Memory.Aim.NONE : pickup.aim(observation, memory);
        return memory.aiming(aim).belief();
    }

    /**
     * The Actions the screen offers less those whose window the Brain left on this floor (story 4.11),
     * unless that would leave none; under a Prompt, all of them, since its answers are all there is.
     */
    static List<Action> unshunned(Observation observation, Memory memory) {
        List<Action> offered = observation.actions().actions();
        if (observation.header().prompt() != org.shatterfish.api.PromptKind.NONE
                || memory.windows().shunned().isEmpty()) {
            return offered;
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<Action> kept = offered.stream()
                .filter(action -> !memory.windows().shuns(depth, branch, action.toString())).toList();
        return kept.isEmpty() ? offered : kept;
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
        List<Action> offered = unshunned(observation, memory);
        // A rooted hero's Steps and stairs are refused with no time spent, so they are no choice at all
        // until the roots wear off (story 4.12, Explore.rooted); nor, on a calm screen, a dizzy hero's,
        // which go where the vertigo sends them (story 4.13, Explore.dizzy) -- unless the hero stands in
        // fire or gas, where a Step anywhere beats staying put.
        if (Explore.rooted(observation) || Explore.dizzy(observation) && !TestItem.inHarm(observation)) {
            offered = offered.stream().filter(action -> !(action instanceof Action.Step
                    || action instanceof Action.Descend || action instanceof Action.Ascend)).toList();
        }
        // A Step onto a chasm asks whether to jump, and a jump is a fall with its damage and the floor
        // left unfinished; no Policy means one, so none is offered one (issue #170, Explore.chasm). The
        // fight Policy's chokepoint and retreat pick among the offered Steps without a plan's walkable
        // cells, and a chasm, which no enemy stands on, looked like the best chokepoint there was.
        offered = offered.stream().filter(action -> !(action instanceof Action.Step step
                && Explore.chasm(observation, step.cell()))).toList();
        // Back and forth between two cells for Memory.BOUNCES waits: the Step back is withheld from
        // every Policy for this wait, so two Policies that undo each other's Step stop (story 4.13); the fold
        // has blocked its cell for Memory.BOUNCE_WAITS waits too, so they do not start again (issue #174).
        if (memory.bounces() >= Memory.BOUNCES && memory.prior() >= 0) {
            Action back = new Action.Step(memory.prior());
            offered = offered.stream().filter(action -> !action.equals(back)).toList();
        }
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
