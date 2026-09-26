package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.Observation;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeliefSummaries#of} (story 5.4, FR-38): at most three unknown items, the least confident
 * first, and every floor fact and chapter counter as one formatted line -- constructed against
 * built {@link Beliefs} records, and, for the one property that must hold against the real
 * pipeline, a real {@link Brain} and {@link Observation} too.
 */
class BeliefSummaryTest {

    private static Beliefs.Guess guess(String label, double... probabilities) {
        List<Beliefs.Odds> odds = new java.util.ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            odds.add(new Beliefs.Odds(label + "-candidate-" + i, probabilities[i]));
        }
        return new Beliefs.Guess(label, ItemKind.POTION, List.copyOf(odds));
    }

    @Test
    @DisplayName("at most three items, the least confident (lowest top probability) first")
    void least_confident_first_and_capped_at_three() {
        Beliefs beliefs = new Beliefs(
                List.of(guess("certain", 0.90, 0.10), guess("toss-up", 0.34, 0.33, 0.33),
                        guess("fairly-sure", 0.70, 0.30), guess("least-sure", 0.40, 0.30, 0.30),
                        guess("most-confident", 0.99, 0.01)),
                List.of(), List.of(), List.of());

        BeliefSummary summary = BeliefSummaries.of(beliefs);

        assertEquals(BeliefSummary.ITEMS, summary.items().size(), "capped at BeliefSummary.ITEMS");
        assertEquals(List.of("toss-up", "least-sure", "fairly-sure"),
                summary.items().stream().map(BeliefSummary.Item::label).toList(),
                "the three lowest top-probability guesses, ascending: toss-up (0.34) is the least "
                        + "confident, then least-sure (0.40), then fairly-sure (0.70); certain (0.90) and "
                        + "most-confident (0.99) are exactly the ones a bad belief is least likely to be");
        // Each item's own top candidate and probability, unchanged from the Guess's own odds.
        BeliefSummary.Item first = summary.items().get(0);
        assertEquals("toss-up-candidate-0", first.candidate());
        assertEquals(0.34, first.probability(), 1e-12);
    }

    @Test
    @DisplayName("a tie in top probability breaks by label, so the choice of three is not order-of-computation")
    void ties_break_by_label() {
        Beliefs beliefs = new Beliefs(
                List.of(guess("zeta", 0.50), guess("alpha", 0.50), guess("mu", 0.50), guess("beta", 0.99)),
                List.of(), List.of(), List.of());

        BeliefSummary summary = BeliefSummaries.of(beliefs);

        assertEquals(List.of("alpha", "mu", "zeta"),
                summary.items().stream().map(BeliefSummary.Item::label).toList());
    }

    @Test
    @DisplayName("a Guess with no odds left (every candidate ruled out) is not shown and does not use up the cap")
    void guesses_with_no_odds_are_skipped() {
        Beliefs beliefs = new Beliefs(
                List.of(guess("nothing-left"), guess("a", 0.5), guess("b", 0.6), guess("c", 0.7)),
                List.of(), List.of(), List.of());

        BeliefSummary summary = BeliefSummaries.of(beliefs);

        assertEquals(3, summary.items().size());
        assertTrue(summary.items().stream().noneMatch(item -> item.label().equals("nothing-left")));
    }

    @Test
    @DisplayName("floor facts and chapter counters are formatted as one line each, in Beliefs' own order")
    void floor_and_chapters_are_formatted_lines() {
        Beliefs beliefs = new Beliefs(List.of(),
                List.of(new Beliefs.FloorItem("potion of invisibility", "pool room")),
                List.of(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 1, 1)),
                List.of());

        BeliefSummary summary = BeliefSummaries.of(beliefs);

        assertEquals(List.of("potion of invisibility (pool room)"), summary.floor());
        assertEquals(List.of("potion of strength 1/2"), summary.chapters());
    }

    @Test
    @DisplayName("an empty Beliefs (nothing seen yet) summarises to nothing")
    void empty_beliefs() {
        BeliefSummary summary = BeliefSummaries.of(new Beliefs(List.of(), List.of(), List.of(), List.of()));
        assertTrue(summary.items().isEmpty());
        assertTrue(summary.floor().isEmpty());
        assertTrue(summary.chapters().isEmpty());
    }

    /**
     * The fairness property (non-negotiable 1): the summary never names anything beyond what the
     * Brain's own odds already offered for that appearance. Driven through the real pipeline --
     * {@code Screens.CODEX}'s potion family (healing, strength, mind vision, frost; none
     * identified) seen by a real {@link Brain} -- rather than a constructed {@code Beliefs}, so this
     * holds the whole path from an Observation to the summary, not only the mapping in isolation.
     */
    @Test
    @DisplayName("the summary's candidate for an unidentified item is always one of the Codex's own candidates, never a true identity")
    void never_names_a_true_identity() {
        Set<String> possibleCandidates = Set.of("potion of strength", "potion of healing",
                "potion of mind vision", "potion of frost");
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        Observation screen = Screens.world(1, List.of(org.shatterfish.api.Tile.EMPTY, org.shatterfish.api.Tile.EMPTY,
                        org.shatterfish.api.Tile.EMPTY), List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), List.of());
        Belief belief = brain.update(screen, null);
        BeliefSummary summary = brain.beliefs(screen, belief) == null ? null
                : BeliefSummaries.of(brain.beliefs(screen, belief));

        assertEquals(1, summary.items().size());
        BeliefSummary.Item item = summary.items().get(0);
        assertEquals("crimson potion", item.label());
        assertTrue(possibleCandidates.contains(item.candidate()),
                "the candidate is one of the Codex's own named identities: " + item.candidate());
        assertTrue(item.probability() > 0 && item.probability() < 1, "a real unidentified item is not a certainty");
    }
}
