package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.BeliefSummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Belief summary section's content (story 5.4, FR-38): unknown items with their top candidate
 * and probability, then floor facts, then chapter counters -- against a constructed
 * {@link BeliefSummary}, without booting the game.
 */
class BeliefSummaryContentTest {

    @Test
    @DisplayName("null (no Brain, or before the first decide()): nothing present")
    void null_summary_is_not_present() {
        BeliefSummaryContent.Content content = BeliefSummaryContent.of(null);
        assertFalse(content.present());
        assertTrue(content.items().isEmpty());
        assertTrue(content.floor().isEmpty());
        assertTrue(content.chapters().isEmpty());
    }

    @Test
    @DisplayName("an empty BeliefSummary (nothing to report) is also not present, not a blank space (UX-DR14)")
    void empty_summary_is_not_present() {
        BeliefSummaryContent.Content content = BeliefSummaryContent.of(new BeliefSummary(List.of(), List.of(), List.of()));
        assertFalse(content.present());
    }

    @Test
    @DisplayName("items, then floor, then chapters, each item's candidate and probability formatted to two decimals")
    void items_then_floor_then_chapters() {
        BeliefSummary summary = new BeliefSummary(
                List.of(new BeliefSummary.Item("crimson potion", "potion of frost", 0.157894736842)),
                List.of("potion of invisibility (pool room)"),
                List.of("potion of strength 1/2"));

        BeliefSummaryContent.Content content = BeliefSummaryContent.of(summary);

        assertTrue(content.present());
        assertEquals(List.of("crimson potion: potion of frost 0.16"), content.items());
        assertEquals(List.of("potion of invisibility (pool room)"), content.floor());
        assertEquals(List.of("potion of strength 1/2"), content.chapters());
    }

    @Test
    @DisplayName("floor facts alone (no unknown items, no chapters) are still present")
    void floor_alone_is_present() {
        BeliefSummary summary = new BeliefSummary(List.of(), List.of("potion of invisibility (pool room)"), List.of());
        assertTrue(BeliefSummaryContent.of(summary).present());
    }
}
