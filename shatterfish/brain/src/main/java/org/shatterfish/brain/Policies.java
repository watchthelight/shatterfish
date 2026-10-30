package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The first Policies (story 4.1): the minimum a Run needs to go on. Later stories put the real ones
 * above them -- explore, fight, eat, test items, descend -- and these stay at the bottom as what a
 * Brain does when nothing better applies.
 *
 * <p>Their goals and reasons are labels and numbers, not sentences (story 4.4, UX-DR13): a
 * lower-case word, then either {@code ": "} and a value or a space and a number --
 * "prompt: close", "decline: No", "uniform 1/5". The Panel and the strategy log print them as they
 * are, and {@code DecisionShapeTest} holds them to that grammar.
 */
final class Policies {

    private Policies() {
    }

    /** The score of a Choice a Policy is sure of, in ten-thousandths. */
    static final long CERTAIN = 10_000;

    /** Story 4.10's scroll-cancel confirmation, which {@link Answers} affirms (kept here for its tests). */
    static final String SCROLL_CANCEL = Answers.SCROLL_CANCEL;

    /** The answer that declines every other item confirmation (story 4.10; {@link Answers}). */
    static final String ITEM_DECLINE = Answers.ITEM_DECLINE;

    /** The first of a ranking, or null when it is empty. */
    private static RunLog.Choice first(List<RunLog.Choice> ranked) {
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** The prompt Policy's name, which {@link Brain#policyNames()} lists without building one. */
    static final String ANSWER_PROMPT = "answer-prompt";

    /**
     * Answer a Prompt the screen holds open, by the rule its kind has (story 4.11, {@link Answers}).
     * It ranks the ways of closing the Prompt the rule accepts, best first, and scores only its pick:
     * the others are what it would have done otherwise, not things it prefers less by some measure.
     * A Prompt its rule cannot answer is a {@link Answers.BrainError}, never a Wait: no Wait is
     * offered under a Prompt (ValidActions), and the Brain does not look past this Policy for one.
     */
    static Policy answerPrompt(Codex.Knowledge knowledge) {
        return new Policy() {
            @Override
            public String name() {
                return ANSWER_PROMPT;
            }

            @Override
            public String goal() {
                return "prompt: close";
            }

            @Override
            public boolean enters(Observation observation, Memory memory) {
                return observation.header().prompt() != PromptKind.NONE;
            }

            @Override
            public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
                return ranked(observation, memory, offered, stream).get(0);
            }

            @Override
            public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered,
                                              Stream stream) {
                return Answers.ranked(observation, memory, offered, knowledge);
            }
        };
    }

    /** Whether {@code action} uses an item from the pack: on its own, at a cell or on another item. */
    static boolean usesItem(Action action) {
        return action instanceof Action.UseItem || action instanceof Action.UseItemAt
                || action instanceof Action.UseItemOn;
    }

    /** Whether {@code action} eats an item, which only the eat Policy does (story 4.9). */
    static boolean eats(Action action) {
        return action instanceof Action.UseItem use && use.action().equals("EAT");
    }

    /**
     * The fallback's name, which {@link Brain#policyNames()} lists without building one.
     */
    static final String FALLBACK = "fallback";

    /**
     * The floor under every Policy a later story adds: uniformly, from the Brain's seeded stream,
     * among the offered Actions that score highest by {@code evaluation} (stories 4.1, 4.4, 4.5).
     * Eating is not among them (story 4.9): it is the eat Policy's, which eats only when the hunger
     * icon says the food will not be wasted, and a uniform draw would eat at full satiety.
     *
     * <p>It ranks by tiers. The Actions are grouped by their Evaluation score, highest first, and
     * each tier is drawn from uniformly with the stream, one draw per Choice, until a pick and as
     * many alternatives as a Decision records are drawn. The pick is the first draw from the top
     * tier; recording alternatives changes nothing it chooses.
     *
     * <p><b>The score a Choice records is the chance of drawing it from its tier</b>, in
     * ten-thousandths: one in k rounded to the nearest, so 1667 for a tier of six. That is the unit
     * every Decision score is in, and it is comparable with the other Policies' (a Policy that is
     * sure scores {@link #CERTAIN}). The Evaluation's own score is not what is recorded: it is a
     * position's worth, not a Policy's preference, and a figure like 23000 beside a Prompt answer's
     * 10000 would read as a stronger preference than any Policy can hold. The reason says the tier:
     * "uniform 1/k" when every Action offered scores alike -- the committed weights, which give the
     * Action features no weight, so play and draws are story 4.4's exactly -- else "top 1/k" for the
     * highest tier and "lower 1/k" for a tier below it.
     *
     * <p>It leaves the items alone while anything else is offered (story 4.10): an item use is drawn
     * only on a screen that offers nothing but item uses. A random use wastes the item and can open a
     * window no Action answers -- the Cleric's spell window from the holy tome (HolyTome.java:93),
     * the upgrade window from a scroll of upgrade read onto an item (ScrollOfUpgrade.java:64), the
     * guess window from a stone of intuition (StoneOfIntuition.java:73) -- which is how every Run
     * that ended on an unknown window in story 4.8's direction check ended. Using items is the
     * Policies' to do deliberately: equip wears gear, test-item drinks and reads.
     */
    static Policy fallback(Evaluation evaluation) {
        return fallback(evaluation, org.shatterfish.api.Tunables.Vector.REFERENCE);
    }

    /**
     * {@link #fallback(Evaluation)} that, under the vector's {@code larder.fallback_rests} of 0, draws no rest while the
     * hero is hungry with no food held (story 10.39): a rest runs until the hit points are full or something
     * interrupts it, hundreds of turns on food that is not there, and a starving hero regenerates none
     * (Regeneration.java:57).
     */
    static Policy fallback(Evaluation evaluation, org.shatterfish.api.Tunables.Vector tunables) {
        return new Policy() {
            @Override
            public String name() {
                return FALLBACK;
            }

            @Override
            public String goal() {
                return "act: nothing better applies";
            }

            @Override
            public boolean enters(Observation observation, Memory memory) {
                return true;
            }

            @Override
            public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
                return first(ranked(observation, memory, offered, stream));
            }

            @Override
            public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered,
                                              Stream stream) {
                // The tiers, highest score first, each in the order the screen offers its Actions.
                java.util.TreeMap<Long, List<Action>> tiers = new java.util.TreeMap<>(Comparator.reverseOrder());
                // Each Action's terms, from the same call that scored it (story 8.1), never a second one.
                java.util.Map<Action, List<RunLog.Term>> termsOf = new java.util.HashMap<>();
                // Never an item use -- eating among them, which is the eat Policy's (story 4.9) --
                // unless item uses are all the screen offers: a Run is not ended for want of one.
                List<Action> drawn = offered.stream().filter(action -> !usesItem(action)).toList();
                if (tunables.getInt(org.shatterfish.api.Tunables.LARDER_FALLBACK_RESTS) == 0
                        && observation.hero().hunger() != org.shatterfish.api.Hunger.NONE && Larder.food(observation) == 0) {
                    List<Action> still = drawn.stream().filter(action -> !(action instanceof Action.Rest)).toList();
                    drawn = still.isEmpty() ? drawn : still;
                }
                for (Action action : drawn.isEmpty() ? offered : drawn) {
                    Evaluation.Scored scored = evaluation.scored(observation, action);
                    termsOf.put(action, scored.terms());
                    tiers.computeIfAbsent(scored.score(), score -> new ArrayList<>()).add(action);
                }
                boolean alike = tiers.size() <= 1;
                List<RunLog.Choice> ranked = new ArrayList<>();
                boolean top = true;
                for (List<Action> tier : tiers.values()) {
                    List<Action> left = new ArrayList<>(tier);
                    // One in k, rounded to the nearest ten-thousandth: 1667 for six, as 0.1667 prints.
                    long score = (CERTAIN + tier.size() / 2) / tier.size();
                    String why = (alike ? "uniform 1/" : top ? "top 1/" : "lower 1/") + tier.size();
                    // The pick and at most as many alternatives as a Decision records: drawing more
                    // would record nothing, and the stream is this wait's alone.
                    while (!left.isEmpty() && ranked.size() <= RunLog.Decision.ALTERNATIVES) {
                        Action drew = left.remove(stream.below(left.size()));
                        ranked.add(new RunLog.Choice(drew, score, why, explained(termsOf.get(drew), score)));
                    }
                    top = false;
                }
                return ranked;
            }
        };
    }

    /** The name of the closing term that carries a worth over into a chance of being drawn. */
    static final String TIER = "tier";

    /**
     * The terms behind a fallback Choice's recorded score (story 8.1). The Evaluation's terms sum to
     * the Action's worth, and the score recorded is the chance of its draw from its tier, so the two
     * differ; a closing {@code tier} term carries the difference, which keeps the one rule that a
     * list of terms sums to the score it explains. Nothing scored, nothing recorded.
     */
    private static List<RunLog.Term> explained(List<RunLog.Term> worth, long score) {
        if (worth.isEmpty()) {
            return List.of();
        }
        long sum = 0;
        for (RunLog.Term term : worth) {
            sum = Math.addExact(sum, term.contribution());
        }
        if (sum == score) {
            return worth;
        }
        List<RunLog.Term> all = new ArrayList<>(worth);
        all.add(new RunLog.Term(TIER, Math.subtractExact(score, sum)));
        return List.copyOf(all);
    }
}
