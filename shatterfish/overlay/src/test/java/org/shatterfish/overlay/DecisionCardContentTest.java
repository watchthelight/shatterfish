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
 * three alternatives with scores and one-line reasons, the Next Step headline, and the Explain
 * expansion's Policy and Safety flags.
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
        assertNull(content.explain());
        assertEquals(DecisionCardContent.NO_DECISION_YET, DecisionCardContent.NO_DECISION_YET);
    }

    @Test
    @DisplayName("the chosen Action and up to three alternatives, each with a score and a one-line reason")
    void chosen_and_alternatives() {
        RunLog.Decision decision = decision(List.of());
        DecisionCardContent.Content content = DecisionCardContent.of(decision, false, false);
        assertTrue(content.present());
        assertNull(content.headline(), "not Next Step mode: no headline");
        assertNull(content.explain(), "Explain not asked for");

        assertEquals("Attack[cell=42]", content.chosen().action());
        assertEquals(Columns.score(7_800), content.chosen().score());
        assertEquals("corridor, full hp", content.chosen().reason());
        assertEquals("Attack[cell=42]  " + Columns.score(7_800) + "  corridor, full hp", content.chosen().line());

        assertEquals(2, content.alternatives().size());
        assertEquals("Step[cell=12]  " + Columns.score(5_100) + "  corridor", content.alternatives().get(0).line());
        // The second alternative's reason is empty (Action.Wait's, above): no trailing double space, and
        // the empty reason is not invented text.
        assertEquals("Wait[]  " + Columns.score(2_000), content.alternatives().get(1).line());
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
    @DisplayName("Explain adds the Policy that fired and the Safety flags that applied; a second press collapses it")
    void explain_expansion() {
        RunLog.Decision decision = decision(List.of("hp-low", "enemy-in-view"));
        DecisionCardContent.Content collapsed = DecisionCardContent.of(decision, false, false);
        DecisionCardContent.Content expanded = DecisionCardContent.of(decision, false, true);
        assertNull(collapsed.explain());
        assertEquals("fight-in-corridors", expanded.explain().policy());
        assertEquals("policy: fight-in-corridors", expanded.explain().policyLine());
        assertEquals(List.of("hp-low", "enemy-in-view"), expanded.explain().flags());
        assertEquals("flags: hp-low, enemy-in-view", expanded.explain().flagsLine());
        // The alternatives' reasons "in full": Explain does not truncate them, because the collapsed
        // card already shows the whole (only) reason a Choice carries.
        assertEquals(collapsed.alternatives(), expanded.alternatives());
        assertEquals(collapsed.chosen(), expanded.chosen());
    }

    @Test
    @DisplayName("no Safety flags is stated as \"none\", never a blank flags line (UX-DR14)")
    void no_flags_says_so() {
        DecisionCardContent.Content expanded = DecisionCardContent.of(decision(List.of()), false, true);
        assertEquals(List.of(), expanded.explain().flags());
        assertEquals("flags: none", expanded.explain().flagsLine());
    }

    @Test
    @DisplayName("scores sit in a fixed-width right-aligned column whatever their magnitude (UX-DR5)")
    void scores_are_fixed_width_columns() {
        String small = Columns.score(100);
        String large = Columns.score(9_999);
        assertEquals(small.length(), large.length(), "one score column width");
        assertTrue(small.startsWith(" "), "the smaller score is padded");
        assertEquals("0.0100", small.trim());
        assertEquals("0.9999", large.trim());
        // Exact decimals, not floats rounded on the way: a one-in-six score is not "0.17" but the
        // precise ten-thousandth the Brain recorded (DecisionShapeTest.three_alternatives: 1_667).
        assertEquals("0.1667", Columns.score(1_667).trim());
    }
}
