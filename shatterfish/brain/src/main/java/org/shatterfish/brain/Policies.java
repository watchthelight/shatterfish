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

    /**
     * The fallback's name, which {@link Brain#policyNames()} lists without building one.
     */
    static final String FALLBACK = "fallback";

    /**
     * The floor under every Policy a later story adds: uniformly, from the Brain's seeded stream,
     * among the offered Actions that score highest by {@code evaluation} (stories 4.1, 4.4, 4.5).
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
     */
    static Policy fallback(Evaluation evaluation) {
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
                for (Action action : offered) {
                    tiers.computeIfAbsent(evaluation.of(observation, action), score -> new ArrayList<>()).add(action);
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
                        ranked.add(new RunLog.Choice(left.remove(stream.below(left.size())), score, why));
                    }
                    top = false;
                }
                return ranked;
            }
        };
    }
}
