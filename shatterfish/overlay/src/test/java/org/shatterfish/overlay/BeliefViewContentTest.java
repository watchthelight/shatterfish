package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.BeliefView;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Beliefs view's content (story 8.6, FR-46): the order, the scrolling rules, when it is shown, and the
 * oracle gating, against constructed {@link BeliefView}s and without booting the game.
 */
class BeliefViewContentTest {

    private static BeliefView.Appearance appearance(String label, double... probabilities) {
        List<BeliefView.Candidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            candidates.add(new BeliefView.Candidate(label + " #" + i, probabilities[i]));
        }
        return new BeliefView.Appearance(label, candidates);
    }

    private static BeliefView view(String hash, BeliefView.Appearance... appearances) {
        return new BeliefView(hash, List.of(appearances), List.of("potion of invisibility (pool room)"),
                List.of("Rat, last seen at wait 41, cell 612"), List.of("potion of strength 1/2"));
    }

    @Test
    @DisplayName("most ambiguous appearance first (lowest top probability), ties by label, every candidate in the Brain's order")
    void most_ambiguous_first() {
        BeliefView view = view("h", appearance("certain", 0.9, 0.1), appearance("toss-up", 0.34, 0.33, 0.33),
                appearance("tie b", 0.5, 0.5), appearance("tie a", 0.5, 0.5), appearance("sure", 0.7, 0.3));

        BeliefViewContent.Content content = BeliefViewContent.of(view);

        List<String> texts = content.texts();
        assertEquals(BeliefViewContent.UNKNOWN_ITEMS, texts.get(0));
        assertEquals(List.of("toss-up", "tie a", "tie b", "sure", "certain"),
                texts.subList(1, 6).stream().map(t -> t.substring(0, t.indexOf(':'))).toList());
        assertEquals("toss-up: toss-up #0 0.34, toss-up #1 0.33, toss-up #2 0.33", texts.get(1),
                "all candidates, with the summary's own probability format, in the Brain's order");
        assertEquals("h", content.hash());
    }

    @Test
    @DisplayName("then the floor's facts, the monsters out of view with their wait, and each drop's counter, each under a heading")
    void the_other_sections() {
        BeliefViewContent.Content content = BeliefViewContent.of(view("h", appearance("a", 0.5, 0.5)));

        assertEquals(List.of(BeliefViewContent.UNKNOWN_ITEMS, "a: a #0 0.50, a #1 0.50",
                BeliefViewContent.FLOOR, "potion of invisibility (pool room)",
                BeliefViewContent.UNSEEN, "Rat, last seen at wait 41, cell 612",
                BeliefViewContent.DROPS, "potion of strength 1/2"), content.texts());
        assertEquals(List.of(true, false, true, false, true, false, true, false),
                content.lines().stream().map(BeliefViewContent.Line::heading).toList());
    }

    @Test
    @DisplayName("nothing believed yet is a sentence, not a blank space, for no view and for an empty one")
    void nothing_is_a_word() {
        assertEquals(List.of(BeliefViewContent.NOTHING_YET), BeliefViewContent.of(null).texts());
        assertEquals(List.of(BeliefViewContent.NOTHING_YET), BeliefViewContent.of(
                new BeliefView("h", List.of(), List.of(), List.of(), List.of())).texts());
    }

    @Test
    @DisplayName("scrolling: held between the top and the content's height less the viewport's, and a short content does not scroll")
    void scrolling() {
        assertEquals(60, BeliefViewContent.maxScroll(100, 40), 0);
        assertEquals(0, BeliefViewContent.maxScroll(30, 40), 0, "content shorter than the viewport");
        assertEquals(0, BeliefViewContent.clamp(-5, 100, 40), 0);
        assertEquals(60, BeliefViewContent.clamp(500, 100, 40), 0);
        assertEquals(25, BeliefViewContent.clamp(25, 100, 40), 0);
        assertEquals(35, BeliefViewContent.scrolled(25, 10, 100, 40), 0);
        assertEquals(60, BeliefViewContent.scrolled(55, 10, 100, 40), 0, "stops at the end");
        assertEquals(0, BeliefViewContent.scrolled(5, -10, 100, 40), 0, "and at the start");
    }

    @Test
    @DisplayName("the place is kept for the same Belief and lost for a new one, which starts at the top")
    void the_place_follows_the_hash() {
        assertTrue(BeliefViewContent.keepsPlace("abc", "abc"));
        assertFalse(BeliefViewContent.keepsPlace("abc", "abd"));
    }

    @Test
    @DisplayName("shown only when opened and the Controls matrix allows it: PAUSED and HUMAN, never RUNNING, never a Run that is over")
    void shown_by_mode() {
        for (Mode mode : Mode.values()) {
            boolean allowed = mode != Mode.RUNNING;
            assertEquals(allowed, ControlEnablement.matrix(mode, false).contains(Control.BELIEFS), mode.name());
            assertEquals(allowed, BeliefViewContent.shown(true, ControlEnablement.matrix(mode, false)), mode.name());
            assertFalse(BeliefViewContent.shown(false, ControlEnablement.matrix(mode, false)), "not opened: " + mode);
            assertFalse(BeliefViewContent.shown(true, ControlEnablement.matrix(mode, true)), "Run over: " + mode);
        }
    }

    @Test
    @DisplayName("oracle rows appear only when given, under the appearance they are for, prefixed; the same view without them has none")
    void oracle_gating() {
        BeliefView view = view("h", appearance("crimson potion", 0.5, 0.5), appearance("grey scroll", 0.4, 0.6));
        String row = OracleMarkContent.PREFIX + "crimson potion = potion of frost";

        List<String> fair = BeliefViewContent.of(view).texts();
        assertTrue(fair.stream().noneMatch(line -> line.startsWith(OracleMarkContent.PREFIX)),
                "without the flag the same view carries no such line: " + fair);
        assertTrue(BeliefViewContent.of(view, Map.of()).texts().equals(fair));

        List<String> oracle = BeliefViewContent.of(view, Map.of("crimson potion", row)).texts();
        assertEquals(fair.size() + 1, oracle.size());
        int at = oracle.indexOf(row);
        assertTrue(at > 0 && oracle.get(at - 1).startsWith("crimson potion: "), "directly under the Brain's own line: " + oracle);
        assertEquals(fair.get(at - 1), oracle.get(at - 1), "the Brain's own line is not replaced");
        assertEquals(1, oracle.stream().filter(line -> line.startsWith(OracleMarkContent.PREFIX)).count());
    }
}
