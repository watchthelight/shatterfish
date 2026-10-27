package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.overlay.ExplainContent.Kind;
import org.shatterfish.overlay.ExplainContent.Line;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Explain expansion says (story 8.2, FR-44), against constructed Decisions and no game: the order
 * (Policy, chosen with its terms, each alternative with its reason and terms, flags, and the Search block only
 * when there is one), the exact text, and that a Decision with no terms gets no table.
 */
class ExplainContentTest {

    /** Words for the Actions the tests use, so no Observation is needed. */
    private static final Function<Action, String> LABEL = action -> switch (action) {
        case Action.Attack a -> "attack " + a.cell();
        case Action.Step a -> "step " + a.cell();
        case Action.Wait a -> "wait";
        default -> action.getClass().getSimpleName();
    };

    private static RunLog.Decision withTerms() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Attack(42), 7_800, "corridor, full hp",
                List.of(new RunLog.Term("depth", 10_000), new RunLog.Term("enemies", -3_000),
                        new RunLog.Term("tier", 800)));
        RunLog.Choice first = new RunLog.Choice(new Action.Step(12), 5_100, "corridor",
                List.of(new RunLog.Term("depth", 10_000), new RunLog.Term("hunger", -4_900)));
        RunLog.Choice second = new RunLog.Choice(new Action.Wait(), 2_000, "");
        return new RunLog.Decision("fight: gnoll scout", chosen, List.of(first, second), List.of("hp-low", "enemy-in-view"),
                "fight-in-corridors");
    }

    private static List<Kind> kinds(List<Line> lines) {
        return lines.stream().map(Line::kind).toList();
    }

    @Test
    @DisplayName("a Decision with terms: Policy, chosen and its table, each alternative with its reason and table, the flags; no Search block")
    void with_terms() {
        List<Line> lines = ExplainContent.of(withTerms(), LABEL);
        assertEquals(List.of(
                new Line(Kind.POLICY, "policy: fight-in-corridors", ""),
                new Line(Kind.CHOSEN, "chosen: attack 42", "0.7800"),
                new Line(Kind.WHY, "corridor, full hp", ""),
                new Line(Kind.TERM, "depth", "1.0000"),
                new Line(Kind.TERM, "enemies", "-0.3000"),
                new Line(Kind.TERM, "tier", "0.0800"),
                new Line(Kind.ALTERNATIVE, "alternative 1: step 12", "0.5100"),
                new Line(Kind.WHY, "corridor", ""),
                new Line(Kind.TERM, "depth", "1.0000"),
                new Line(Kind.TERM, "hunger", "-0.4900"),
                new Line(Kind.ALTERNATIVE, "alternative 2: wait", "0.2000"),
                new Line(Kind.FLAGS, "flags: hp-low, enemy-in-view", "")), lines);
    }

    @Test
    @DisplayName("the terms printed under a Choice add up to the score printed on its row, for the chosen and every alternative")
    void terms_add_up_to_the_displayed_score() {
        List<Line> lines = ExplainContent.of(withTerms(), LABEL);
        java.math.BigDecimal shown = null;
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        int checked = 0;
        for (Line line : lines) {
            switch (line.kind()) {
                case CHOSEN, ALTERNATIVE, FLAGS -> {
                    if (shown != null && sum.signum() != 0) {
                        assertEquals(0, shown.compareTo(sum), "the terms under " + shown);
                        checked++;
                    }
                    shown = line.kind() == Kind.FLAGS ? null : new java.math.BigDecimal(line.right());
                    sum = java.math.BigDecimal.ZERO;
                }
                case TERM -> sum = sum.add(new java.math.BigDecimal(line.right()));
                default -> {
                }
            }
        }
        assertEquals(2, checked, "the chosen and the first alternative have tables; the second has none");
    }

    @Test
    @DisplayName("a Decision with no terms shows the Policy and the reasons it has, and says nothing of terms: no rows, no header, no empty table")
    void without_terms() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Search(), 10_000, "nothing better");
        RunLog.Choice alternative = new RunLog.Choice(new Action.Wait(), 0, "");
        RunLog.Decision decision = new RunLog.Decision("search", chosen, List.of(alternative), List.of(), "explore");
        List<Line> lines = ExplainContent.of(decision, LABEL);
        assertEquals(List.of(
                new Line(Kind.POLICY, "policy: explore", ""),
                new Line(Kind.CHOSEN, "chosen: Search", "1.0000"),
                new Line(Kind.WHY, "nothing better", ""),
                new Line(Kind.ALTERNATIVE, "alternative 1: wait", "0.0000"),
                new Line(Kind.FLAGS, "flags: none", "")), lines);
        assertFalse(kinds(lines).contains(Kind.TERM));
        assertFalse(kinds(lines).contains(Kind.SEARCH));
        assertTrue(lines.stream().noneMatch(line -> line.left().toLowerCase().contains("term")),
                "nothing about terms is said");
    }

    @Test
    @DisplayName("a Decision with a Search block ends with it: each Action tried, its expected outcome and its sample count")
    void with_search() {
        RunLog.Decision base = withTerms();
        RunLog.Decision decision = new RunLog.Decision(base.goal(), base.chosen(), base.alternatives(), base.flags(),
                base.policy(), List.of(new RunLog.SearchOutcome(new Action.Attack(42), 6_250, 64),
                new RunLog.SearchOutcome(new Action.Step(12), -1_000, 64)));
        List<Line> lines = ExplainContent.of(decision, LABEL);
        List<Line> plain = ExplainContent.of(base, LABEL);
        assertEquals(plain, lines.subList(0, plain.size()), "everything before the Search block is what it was");
        assertEquals(List.of(
                new Line(Kind.SEARCH, ExplainContent.SEARCH_HEADING, ""),
                new Line(Kind.OUTCOME, "attack 42", "0.6250  n=64"),
                new Line(Kind.OUTCOME, "step 12", "-0.1000  n=64")), lines.subList(plain.size(), lines.size()));
    }

    @Test
    @DisplayName("an alternative's reason is shown in full, however long, and an empty one is not invented")
    void reasons_in_full() {
        String long_ = "the corridor is one wide and the gnoll is alone, so the fight is taken here and not in the open room behind";
        RunLog.Choice chosen = new RunLog.Choice(new Action.Attack(1), 9_000, "x");
        RunLog.Choice alternative = new RunLog.Choice(new Action.Step(2), 1_000, long_);
        RunLog.Choice silent = new RunLog.Choice(new Action.Wait(), 500, "");
        RunLog.Decision decision = new RunLog.Decision("g", chosen, List.of(alternative, silent), List.of(), "p");
        List<Line> lines = ExplainContent.of(decision, LABEL);
        assertTrue(lines.contains(new Line(Kind.WHY, long_, "")));
        assertEquals(2, lines.stream().filter(line -> line.kind() == Kind.WHY).count(),
                "the chosen reason and the long one; the silent alternative adds no line");
    }

    @Test
    @DisplayName("no Decision is no lines")
    void no_decision() {
        assertEquals(List.of(), ExplainContent.of(null, LABEL));
    }

    @Test
    @DisplayName("Actions are worded through ActionText when an Observation is given, as the card words them")
    void worded_as_the_card_words_them() {
        RunLog.Decision decision = withTerms();
        List<Line> lines = ExplainContent.of(decision, (org.shatterfish.api.Observation) null);
        assertEquals("chosen: attack", lines.get(1).left());
        assertEquals("alternative 1: step 12", lines.get(6).left());
    }
}
