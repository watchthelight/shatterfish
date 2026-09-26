package org.shatterfish.api;

import java.util.List;

/**
 * What the Brain currently believes, in a form the Overlay may show (story 5.4, FR-38): the most
 * ambiguous unknown items in view, each with its most likely candidate and its probability, the
 * facts this floor is known to imply, and each guaranteed drop's counter for the current set of
 * floors.
 *
 * <p>This is {@code api} for the reason {@link Deliberator}'s other outputs are (story 4.1): the
 * brain module builds it (from its own {@code Beliefs}, story 4.2), and the harness and the
 * Overlay read it through types both can see, without either depending on {@code brain}'s internal
 * shape. Nothing here is more than the Brain's own odds and its own Memory already hold from what
 * the screen showed (non-negotiable 1): a candidate's name is one of the appearance's own possible
 * identities, never the true one, and its probability is the Brain's own marginal, never a fact
 * about the game's hidden state.
 *
 * @param items    at most {@link #ITEMS} unidentified appearances in view, each with its single
 *                 most likely candidate and that candidate's probability (the Brain's own {@code
 *                 Beliefs.Guess.odds()} is sorted most likely first; this keeps only the top one)
 * @param floor    the items this floor is known to hold, and why, already formatted as one line
 *                 each (the Brain's own {@code Beliefs.FloorItem})
 * @param chapters each guaranteed drop's counter for the current set of floors, already formatted
 *                 as one line each (the Brain's own {@code Beliefs.Chapter})
 */
public record BeliefSummary(List<Item> items, List<String> floor, List<String> chapters) {

    /**
     * At most this many unknown items are shown (FR-38: "the three most relevant unknown items").
     * "Most relevant" is decided by whoever builds this record (the brain module's own choice,
     * recorded in the story file); this record only carries the cap.
     */
    public static final int ITEMS = 3;

    /** One unidentified appearance's most likely candidate and that candidate's probability. */
    public record Item(String label, String candidate, double probability) {

        public Item {
            Canon.text(label, "an unknown item's label");
            Canon.text(candidate, "an unknown item's top candidate");
            Canon.require(probability >= 0 && probability <= 1,
                    "a probability is between 0 and 1: " + probability);
        }
    }

    public BeliefSummary {
        items = Canon.positional(items, "a belief summary's items");
        Canon.require(items.size() <= ITEMS, "at most " + ITEMS + " items are shown: " + items.size());
        floor = Canon.positional(floor, "a belief summary's floor facts");
        chapters = Canon.positional(chapters, "a belief summary's chapter counters");
    }
}
