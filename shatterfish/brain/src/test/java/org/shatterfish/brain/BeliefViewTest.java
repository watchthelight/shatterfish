package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BeliefView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.Observation;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeliefViews#of} (story 8.6, FR-46): every appearance with every candidate in the Brain's own order,
 * the floor facts with their reasons, the unseen monsters with the wait each was last seen, every counter, each
 * formatted as the summary's lines are, and carrying the hash it is given. Constructed against built
 * {@link Beliefs}, and for the property that must hold against the real pipeline, a real {@link Brain}.
 */
class BeliefViewTest {

    private static Beliefs.Guess guess(String label, ItemKind kind, Beliefs.Odds... odds) {
        return new Beliefs.Guess(label, kind, List.of(odds));
    }

    @Test
    @DisplayName("every appearance, with every candidate and probability in the Brain's own order, not three and not the top one")
    void every_appearance_with_every_candidate() {
        Beliefs beliefs = new Beliefs(
                List.of(guess("crimson potion", ItemKind.POTION, new Beliefs.Odds("potion of healing", 0.5),
                                new Beliefs.Odds("potion of frost", 0.3), new Beliefs.Odds("potion of mind vision", 0.2)),
                        guess("scroll labeled ZYX", ItemKind.SCROLL, new Beliefs.Odds("scroll of identify", 0.6),
                                new Beliefs.Odds("scroll of lullaby", 0.4)),
                        guess("a", ItemKind.RING, new Beliefs.Odds("ring of haste", 1.0)),
                        guess("b", ItemKind.RING, new Beliefs.Odds("ring of might", 1.0))),
                List.of(), List.of(), List.of());

        BeliefView view = BeliefViews.of(beliefs, "abc");

        assertEquals("abc", view.hash());
        assertEquals(4, view.appearances().size(), "more than the summary's three");
        BeliefView.Appearance potion = view.appearances().get(0);
        assertEquals("crimson potion", potion.label());
        assertEquals(List.of("potion of healing", "potion of frost", "potion of mind vision"),
                potion.candidates().stream().map(BeliefView.Candidate::name).toList(), "the Brain's order");
        assertEquals(0.3, potion.candidates().get(1).probability(), 1e-12);
    }

    @Test
    @DisplayName("an appearance with no candidate left is not a belief and is left out, as the summary leaves it out")
    void nothing_left_is_skipped() {
        Beliefs beliefs = new Beliefs(List.of(guess("gone", ItemKind.POTION),
                guess("here", ItemKind.POTION, new Beliefs.Odds("potion of frost", 1.0))), List.of(), List.of(), List.of());

        assertEquals(List.of("here"), BeliefViews.of(beliefs, "h").appearances().stream()
                .map(BeliefView.Appearance::label).toList());
    }

    @Test
    @DisplayName("floor facts carry their reason, counters read found/total, and only the monsters out of view are remembered, each with its wait")
    void floor_counters_and_unseen_monsters() {
        Beliefs beliefs = new Beliefs(List.of(),
                List.of(new Beliefs.FloorItem("potion of invisibility", "pool room")),
                List.of(new Beliefs.Chapter("STRENGTH_POTIONS", "potion of strength", 0, 1, 1)),
                List.of(new Beliefs.Monster("Rat", 612, 41, true), new Beliefs.Monster("Gnoll", 30, 90, false)));

        BeliefView view = BeliefViews.of(beliefs, "h");

        assertEquals(List.of("potion of invisibility (pool room)"), view.floor());
        assertEquals(List.of("potion of strength 1/2"), view.chapters());
        assertEquals(List.of("Rat, last seen at wait 41, cell 612"), view.monsters(),
                "the Gnoll is in view and is on the screen, not in the unseen memory");
        assertEquals(view.floor(), BeliefSummaries.of(beliefs).floor(), "formatted as the summary's lines are");
        assertEquals(view.chapters(), BeliefSummaries.of(beliefs).chapters());
    }

    @Test
    @DisplayName("an empty Beliefs gives an empty view")
    void empty() {
        BeliefView view = BeliefViews.of(new Beliefs(List.of(), List.of(), List.of(), List.of()), "h");
        assertTrue(view.appearances().isEmpty() && view.floor().isEmpty() && view.monsters().isEmpty()
                && view.chapters().isEmpty());
    }

    /**
     * The fairness property (non-negotiable 1) through the real pipeline: a real Brain and an unidentified
     * potion; each candidate is one of the Codex's own potion identities, and the view's hash is the hash of the
     * Belief the decider holds after the wait, which is the one the Run loop logs.
     */
    @Test
    @DisplayName("through a real Brain: candidates are the family's own identities, and the hash is the Belief's hash after the wait")
    void real_pipeline() {
        Set<String> possible = Set.of("potion of strength", "potion of healing", "potion of mind vision",
                "potion of frost");
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        BrainDecider decider = new BrainDecider(brain);
        assertNull(decider.beliefView(), "before the first wait");
        Observation screen = Screens.world(1, List.of(org.shatterfish.api.Tile.EMPTY, org.shatterfish.api.Tile.EMPTY,
                        org.shatterfish.api.Tile.EMPTY), List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), List.of());

        decider.decide(screen);

        BeliefView view = decider.beliefView();
        Belief held = decider.belief();
        assertEquals(held.hash(), view.hash(), "the Belief hash the Wait record stores");
        assertEquals(decider.beliefHash(), view.hash());
        assertEquals(1, view.appearances().size());
        BeliefView.Appearance appearance = view.appearances().get(0);
        assertEquals("crimson potion", appearance.label());
        double total = 0;
        for (BeliefView.Candidate candidate : appearance.candidates()) {
            assertTrue(possible.contains(candidate.name()), "a possible identity, not a hidden one: " + candidate.name());
            total += candidate.probability();
        }
        assertEquals(1.0, total, 1e-9, "a distribution over the identities it may be");
    }

    @Test
    @DisplayName("a mark put back restores the view with the Belief")
    void rewind_restores_the_view() {
        BrainDecider decider = new BrainDecider(new Brain(Screens.CODEX, Screens.WEIGHTS, 7L));
        Observation screen = Screens.world(1, List.of(org.shatterfish.api.Tile.EMPTY, org.shatterfish.api.Tile.EMPTY,
                        org.shatterfish.api.Tile.EMPTY), List.of(), List.of(),
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)), List.of());
        decider.decide(screen);
        BeliefView first = decider.beliefView();
        Object mark = decider.mark();
        decider.decide(screen);
        decider.rewind(mark);
        assertEquals(first, decider.beliefView());
        assertEquals(decider.belief().hash(), decider.beliefView().hash());
    }
}
