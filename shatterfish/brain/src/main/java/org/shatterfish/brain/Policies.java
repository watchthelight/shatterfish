package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The first Policies (story 4.1): the minimum a Run needs to go on. Later stories put the real ones
 * above them -- explore, fight, eat, test items, descend -- and these stay at the bottom as what a
 * Brain does when nothing better applies.
 */
final class Policies {

    private Policies() {
    }

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

    /**
     * Answer a Prompt the screen holds open: decline when an offered answer says so, else the lowest
     * answer offered, else dismiss it.
     */
    static final Policy ANSWER_PROMPT = new Policy() {
        @Override
        public String name() {
            return "answer-prompt";
        }

        @Override
        public String goal() {
            return "close the prompt the screen holds open";
        }

        @Override
        public boolean enters(Observation observation, Memory memory) {
            return observation.header().prompt() != PromptKind.NONE;
        }

        @Override
        public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
            Action.AnswerPrompt first = null;
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer
                        && (first == null || answer.option() < first.option())) {
                    first = answer;
                }
            }
            List<String> labels = observation.prompt().options();
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer && answer.option() >= 0
                        && answer.option() < labels.size()
                        && declines(labels.get(answer.option()))) {
                    return new RunLog.Choice(answer, 0, "the prompt offers to decline: " + labels.get(answer.option()));
                }
            }
            if (first != null) {
                return new RunLog.Choice(first, 0, "the first answer the prompt offers");
            }
            for (Action action : offered) {
                if (action instanceof Action.DismissPrompt) {
                    return new RunLog.Choice(action, 0, "the prompt offers no answer, so it is dismissed");
                }
            }
            return null;
        }
    };

    /**
     * Anything the screen offers, uniformly, from the Brain's seeded stream: the random agent's
     * choice, as the floor under every Policy a later story adds.
     */
    static final Policy FALLBACK = new Policy() {
        @Override
        public String name() {
            return "fallback";
        }

        @Override
        public String goal() {
            return "act, when no better Policy applies";
        }

        @Override
        public boolean enters(Observation observation, Memory memory) {
            return true;
        }

        @Override
        public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
            if (offered.isEmpty()) {
                return null;
            }
            return new RunLog.Choice(offered.get(stream.below(offered.size())), 0,
                    "uniform over the " + offered.size() + " Actions offered");
        }
    };
}
