package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Decision card's content (story 5.3, FR-38, FR-39, UX-DR3, UX-DR5, UX-DR13), against a
 * constructed {@link RunLog.Decision} rather than a live Run: the chosen Action with its score, up to
 * three alternatives with scores and one-line reasons, the Next Step headline, and whether the Explain
 * expansion is open (its words are ExplainContentTest's).
 */
class DecisionCardContentTest {

    private static RunLog.Decision decision(List<String> flags) {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Attack(42), 7_800, "corridor, full hp");
        RunLog.Choice alt1 = new RunLog.Choice(new Action.Step(12), 5_100, "corridor");
        RunLog.Choice alt2 = new RunLog.Choice(new Action.Wait(), 2_000, "");
        return new RunLog.Decision("fight: gnoll scout", chosen, List.of(alt1, alt2), flags, "fight-in-corridors");
    }

    @Test
    @DisplayName("no Decision yet is a card that says so in words, not a blank one (UX-DR14)")
    void no_decision_yet() {
        DecisionCardContent.Content content = DecisionCardContent.of(null, false, false);
        assertFalse(content.present());
        assertNull(content.headline());
        assertNull(content.chosen());
        assertEquals(List.of(), content.alternatives());
        assertFalse(content.explaining());
        assertEquals(DecisionCardContent.NO_DECISION_YET, DecisionCardContent.NO_DECISION_YET);
    }

    @Test
    @DisplayName("the chosen Action and up to three alternatives, each with a score and a one-line reason")
    void chosen_and_alternatives() {
        RunLog.Decision decision = decision(List.of());
        DecisionCardContent.Content content = DecisionCardContent.of(decision, false, false);
        assertTrue(content.present());
        assertNull(content.headline(), "not Next Step mode: no headline");
        assertFalse(content.explaining(), "Explain not asked for");

        // The Action itself, not a rendered label: DecisionCardContent is the Decision's shape, and
        // ActionText (ActionTextTest) is what turns an Action into words for the screen.
        assertEquals(new Action.Attack(42), content.chosen().action());
        assertEquals(Columns.score(7_800), content.chosen().score());
        assertEquals("corridor, full hp", content.chosen().reason());

        assertEquals(2, content.alternatives().size());
        assertEquals(new Action.Step(12), content.alternatives().get(0).action());
        assertEquals(Columns.score(5_100), content.alternatives().get(0).score());
        assertEquals("corridor", content.alternatives().get(0).reason());
        // The second alternative's reason is empty (Action.Wait's, above): an empty reason is not
        // invented text.
        assertEquals(new Action.Wait(), content.alternatives().get(1).action());
        assertEquals("", content.alternatives().get(1).reason());
    }

    @Test
    @DisplayName("in Next Step mode the card is labelled as what the next press will execute")
    void next_step_headline() {
        RunLog.Decision decision = decision(List.of());
        DecisionCardContent.Content withoutNextStep = DecisionCardContent.of(decision, false, false);
        DecisionCardContent.Content withNextStep = DecisionCardContent.of(decision, true, false);
        assertNull(withoutNextStep.headline());
        assertEquals(DecisionCardContent.NEXT_PRESS, withNextStep.headline());
        // The rows themselves do not change: Next Step changes only the framing, not the Decision shown.
        assertEquals(withoutNextStep.chosen(), withNextStep.chosen());
        assertEquals(withoutNextStep.alternatives(), withNextStep.alternatives());
    }

    @Test
    @DisplayName("the card only knows whether the expansion is open; its rows do not change, and what the expansion says is ExplainContent's")
    void explain_expansion() {
        RunLog.Decision decision = decision(List.of("hp-low", "enemy-in-view"));
        DecisionCardContent.Content collapsed = DecisionCardContent.of(decision, false, false);
        DecisionCardContent.Content expanded = DecisionCardContent.of(decision, false, true);
        assertFalse(collapsed.explaining());
        assertTrue(expanded.explaining());
        assertEquals(collapsed.alternatives(), expanded.alternatives());
        assertEquals(collapsed.chosen(), expanded.chosen());
    }

    @Test
    @DisplayName("a score is its exact decimal, never a float rounded on the way (UX-DR5); the pixel column is DecisionCard's, held by PanelContentTest")
    void scores_are_exact_decimals() {
        assertEquals("0.0100", Columns.score(100));
        assertEquals("0.9999", Columns.score(9_999));
        // A one-in-six score is not "0.17" but the precise ten-thousandth the Brain recorded
        // (DecisionShapeTest.three_alternatives: 1_667).
        assertEquals("0.1667", Columns.score(1_667));
    }
}
