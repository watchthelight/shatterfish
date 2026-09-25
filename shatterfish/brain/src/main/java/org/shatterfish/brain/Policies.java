package org.shatterfish.brain;

import org.shatterfish.api.Action;
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

    /**
     * The answers that decline, as the Prompt labels them. A Prompt the Brain does not understand is
     * usually a confirmation -- jump into the chasm, drink the unknown potion, leave the item behind
     * -- and declining is what leaves the Run as it was.
     */
    private static final List<String> DECLINING = List.of("no", "cancel", "never mind", "not now");

    /**
     * The Prompts, by title, whose "yes" is what leaves the Run as it was (story 4.8). The Warrior
     * putting on new armour is asked whether to move the broken seal from the armour coming off
     * (Armor.java:261-283, titled with the seal's name, items.properties:2392); "no" leaves the seal
     * on the armour in the pack, where it does nothing for him (items.properties:96).
     */
    private static final List<String> AFFIRMED = List.of("broken seal");

    /** The answer that affirms such a Prompt. */
    private static final String YES = "yes";

    /** Whether a button label declines. Case-blind without a Locale, which the Brain may not read. */
    private static boolean declines(String label) {
        String stripped = label.strip();
        return DECLINING.stream().anyMatch(stripped::equalsIgnoreCase);
    }

    /** The label of the button an answer presses, or empty when the Prompt draws none for it. */
    private static String label(List<String> labels, Action.AnswerPrompt answer) {
        return answer.option() >= 0 && answer.option() < labels.size() ? labels.get(answer.option()).strip() : "";
    }

    /** The first of a ranking, or null when it is empty. */
    private static RunLog.Choice first(List<RunLog.Choice> ranked) {
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /**
     * Answer a Prompt the screen holds open: decline when an offered answer says so, else the lowest
     * answer offered, else dismiss it. It ranks every way of closing the Prompt the screen offers,
     * best first, and scores only its pick: the others are what it would have done otherwise, not
     * things it prefers less by some measure.
     */
    static final Policy ANSWER_PROMPT = new Policy() {
        @Override
        public String name() {
            return "answer-prompt";
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
            return first(ranked(observation, memory, offered, stream));
        }

        @Override
        public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
            List<String> labels = observation.prompt().options();
            String title = observation.prompt().title().strip();
            List<RunLog.Choice> ranked = new ArrayList<>();
            if (AFFIRMED.stream().anyMatch(title::equalsIgnoreCase)) {
                for (Action action : offered) {
                    if (action instanceof Action.AnswerPrompt answer && YES.equalsIgnoreCase(label(labels, answer))) {
                        ranked.add(new RunLog.Choice(answer, CERTAIN, "affirm: " + label(labels, answer)));
                    }
                }
            }
            List<Action.AnswerPrompt> declining = new ArrayList<>();
            List<Action.AnswerPrompt> answers = new ArrayList<>();
            Action dismiss = null;
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer) {
                    (declines(label(labels, answer)) ? declining : answers).add(answer);
                } else if (action instanceof Action.DismissPrompt) {
                    dismiss = action;
                }
            }
            declining.sort(Comparator.comparingInt(Action.AnswerPrompt::option));
            answers.sort(Comparator.comparingInt(Action.AnswerPrompt::option));
            boolean affirmed = !ranked.isEmpty();
            for (Action.AnswerPrompt answer : declining) {
                ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? CERTAIN : 0,
                        "decline: " + label(labels, answer)));
            }
            for (Action.AnswerPrompt answer : answers) {
                String label = label(labels, answer);
                if (affirmed && YES.equalsIgnoreCase(label)) {
                    continue;
                }
                ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? CERTAIN : 0,
                        "answer: " + (label.isEmpty() ? Integer.toString(answer.option()) : label)));
            }
            if (dismiss != null) {
                ranked.add(new RunLog.Choice(dismiss, ranked.isEmpty() ? CERTAIN : 0, "dismiss"));
            }
            return ranked;
        }
    };

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
                // Never eating, unless eating is all the screen offers: a Run is not ended for it.
                List<Action> drawn = offered.stream().filter(action -> !eats(action)).toList();
                for (Action action : drawn.isEmpty() ? offered : drawn) {
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
