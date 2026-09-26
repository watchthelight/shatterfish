package org.shatterfish.overlay;

import org.shatterfish.api.BeliefSummary;

import java.util.List;

/**
 * The Belief summary section's content (story 5.4, FR-38): unknown items with their top candidate
 * and probability, then floor facts, then chapter counters, in that order; absent when there is
 * nothing to show, since a Run's very first wait (before any wait has been served) carries no
 * {@code BeliefSummary} at all ({@link org.shatterfish.api.Deliberator#beliefSummary()}'s own
 * default, null), and a Run whose Codex has no guarantees and whose floor holds no facts yet may
 * carry one with every list empty.
 *
 * <p>A pure function of a {@link BeliefSummary}, in the style of {@link ModeStripContent} and
 * {@link DecisionCardContent}: {@code items}, {@code floor} and {@code chapters} are already the
 * exact lines the section draws, one {@code RenderedTextBlock} each, so a test holds this against a
 * constructed {@code BeliefSummary} without booting the game. {@code floor} and {@code chapters}
 * are already formatted strings on {@code BeliefSummary} itself (the brain module's own choice of
 * words); this class only formats {@code items}, since {@code BeliefSummary.Item} carries its
 * probability as a plain {@code double} and this is the one place a UI number's exact text is
 * decided ({@link Columns#probability}).
 */
final class BeliefSummaryContent {

    /** One unknown item's line, already formatted. */
    record Content(boolean present, List<String> items, List<String> floor, List<String> chapters) {
    }

    /** Nothing to show yet (UX-DR14: absence is a word too), before the first Decision or with an empty summary. */
    static final String NOTHING_YET = "nothing believed yet";

    private BeliefSummaryContent() {
    }

    /** {@code summary}'s content; {@link #present} is false when {@code summary} is null or carries nothing at all. */
    static Content of(BeliefSummary summary) {
        if (summary == null) {
            return new Content(false, List.of(), List.of(), List.of());
        }
        boolean present = !summary.items().isEmpty() || !summary.floor().isEmpty() || !summary.chapters().isEmpty();
        if (!present) {
            return new Content(false, List.of(), List.of(), List.of());
        }
        List<String> items = summary.items().stream()
                .map(item -> item.label() + ": " + item.candidate() + " " + Columns.probability(item.probability()))
                .toList();
        return new Content(true, items, summary.floor(), summary.chapters());
    }
}
