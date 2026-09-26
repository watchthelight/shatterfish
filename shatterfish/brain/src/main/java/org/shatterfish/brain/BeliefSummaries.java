package org.shatterfish.brain;

import org.shatterfish.api.BeliefSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns this Brain's own {@link Beliefs} (story 4.2) into the api's {@link BeliefSummary} (story
 * 5.4, FR-38): the shape the Overlay may show, without the Overlay depending on brain's internal
 * types. A pure function, in the same style as {@link Highlights}.
 */
final class BeliefSummaries {

    private BeliefSummaries() {
    }

    /**
     * {@code beliefs}, summarised: at most {@link BeliefSummary#ITEMS} unknown items, the least
     * confident first, and every floor fact and chapter counter formatted as one line.
     *
     * <p>"Most relevant" (FR-38) is read here as "most worth a second look": an appearance the
     * Brain is already all but certain about is unlikely to be the bad belief this story exists to
     * let the human catch (its own "As the human" line: "so that I can spot a bad belief before it
     * costs a run"). An appearance near a toss-up is exactly where the Brain's odds and the truth
     * are likeliest to differ; showing the most confident guesses first would bury the one item most
     * worth a second look behind ones needing none. Ties (equal top probability) break by label, so
     * the choice of which three show is not order-of-computation.
     */
    static BeliefSummary of(Beliefs beliefs) {
        List<Beliefs.Guess> byConfidence = new ArrayList<>(beliefs.identities());
        byConfidence.sort(Comparator.comparingDouble(BeliefSummaries::topProbability)
                .thenComparing(Beliefs.Guess::label));
        List<BeliefSummary.Item> items = new ArrayList<>();
        for (Beliefs.Guess guess : byConfidence) {
            if (guess.odds().isEmpty()) {
                // Nothing this appearance could still be (every candidate ruled out or fully
                // identified): not a belief worth showing, ambiguous or not.
                continue;
            }
            if (items.size() >= BeliefSummary.ITEMS) {
                break;
            }
            Beliefs.Odds top = guess.odds().get(0);
            items.add(new BeliefSummary.Item(guess.label(), top.name(), top.probability()));
        }
        List<String> floor = beliefs.floor().stream()
                .map(item -> item.item() + " (" + item.because() + ")").toList();
        List<String> chapters = beliefs.chapters().stream()
                .map(chapter -> chapter.item() + " " + chapter.found() + "/" + (chapter.found() + chapter.owed()))
                .toList();
        return new BeliefSummary(items, floor, chapters);
    }

    /** {@code guess}'s own top candidate's probability ({@code Beliefs.identities}'s own sort: most likely first). */
    private static double topProbability(Beliefs.Guess guess) {
        return guess.odds().isEmpty() ? 1.0 : guess.odds().get(0).probability();
    }
}
