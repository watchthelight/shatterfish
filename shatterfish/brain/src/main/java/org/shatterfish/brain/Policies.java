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
 * <p>Their reasons are labels and numbers, not sentences (story 4.4, UX-DR13): "decline: No",
 * "uniform 1/5". The Panel and the strategy log print them as they are.
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
            return "close the prompt";
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
            List<RunLog.Choice> ranked = new ArrayList<>();
            for (Action.AnswerPrompt answer : declining) {
                ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? CERTAIN : 0,
                        "decline: " + label(labels, answer)));
            }
            for (Action.AnswerPrompt answer : answers) {
                String label = label(labels, answer);
                ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? CERTAIN : 0,
                        "answer " + answer.option() + (label.isEmpty() ? "" : ": " + label)));
            }
            if (dismiss != null) {
                ranked.add(new RunLog.Choice(dismiss, ranked.isEmpty() ? CERTAIN : 0, "dismiss"));
            }
            return ranked;
        }
    };

    /**
     * Anything the screen offers, uniformly, from the Brain's seeded stream: the random agent's
     * choice, as the floor under every Policy a later story adds. Its pick is the stream's first
     * draw; the alternatives it records are the next draws among what is left, so recording them
     * changes nothing it chooses.
     */
    static final Policy FALLBACK = new Policy() {
        @Override
        public String name() {
            return "fallback";
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
        public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
            List<RunLog.Choice> ranked = new ArrayList<>();
            List<Action> left = new ArrayList<>(offered);
            long score = offered.isEmpty() ? 0 : CERTAIN / offered.size();
            String why = "uniform 1/" + offered.size();
            // The pick and at most as many alternatives as a Decision records: drawing more would
            // record nothing, and the stream is this wait's alone.
            while (!left.isEmpty() && ranked.size() <= RunLog.Decision.ALTERNATIVES) {
                ranked.add(new RunLog.Choice(left.remove(stream.below(left.size())), score, why));
            }
            return ranked;
        }
    };
}
