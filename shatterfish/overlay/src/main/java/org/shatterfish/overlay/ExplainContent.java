package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What the Explain expansion says about a Decision (story 8.2, FR-44, UX-DR15): a pure function of the
 * {@link RunLog.Decision}, so a test holds the order and the text against a constructed one without a game.
 *
 * <p>The order is fixed: the Policy that fired; the chosen Action with its score, its reason and its term
 * table; each alternative with its score, its reason in full and its term table; the Safety flags that
 * applied; and, only when the Decision has one, the Search block. A Choice with no terms (the random agent,
 * a Policy that scores nothing through the Evaluation) gets no term rows and no table header, never an
 * empty table, and a Choice with no reason gets no reason line: nothing is shown that no Brain said.
 *
 * <p>A {@link Line} is one row of the expansion: the words on the left and, for a score, a contribution or
 * an outcome, the number on the right. {@link ExplainView} puts the right-hand text in a column of its own,
 * by position and not by padding (UX-DR5). Every number is {@link Columns#score}, the exact decimal.
 */
final class ExplainContent {

    /** The row kinds, which the view reads for indent, size and ink. */
    enum Kind {
        /** {@code policy: <name>}. */
        POLICY,
        /** The chosen Action, its score on the right. */
        CHOSEN,
        /** An alternative, its score on the right. */
        ALTERNATIVE,
        /** A Choice's reason, in full. */
        WHY,
        /** One Evaluation term of the Choice above it: the feature on the left, its contribution on the right. */
        TERM,
        /** {@code flags: a, b} or {@code flags: none}. */
        FLAGS,
        /** The Search block's heading. */
        SEARCH,
        /** One Action the Search tried: the Action on the left, its expected outcome and sample count on the right. */
        OUTCOME
    }

    /** One row: {@code right} is empty when the row has no number. */
    record Line(Kind kind, String left, String right) {
    }

    /** The word for a flag list with nothing in it (UX-DR14: absence is a word, not a blank). */
    static final String NO_FLAGS = "none";

    /** The Search block's heading. */
    static final String SEARCH_HEADING = "search: what each Action was expected to give";

    private ExplainContent() {
    }

    /** {@code decision}'s expansion, its Actions worded by {@link ActionText} against {@code observation} (may be null). */
    static List<Line> of(RunLog.Decision decision, Observation observation) {
        return of(decision, action -> ActionText.of(action, observation));
    }

    /** {@code decision}'s expansion, its Actions worded by {@code label}; empty for no Decision. */
    static List<Line> of(RunLog.Decision decision, Function<Action, String> label) {
        if (decision == null) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Kind.POLICY, "policy: " + decision.policy(), ""));
        choice(lines, Kind.CHOSEN, "chosen: ", decision.chosen(), label);
        int number = 1;
        for (RunLog.Choice alternative : decision.alternatives()) {
            choice(lines, Kind.ALTERNATIVE, "alternative " + number++ + ": ", alternative, label);
        }
        lines.add(new Line(Kind.FLAGS, "flags: " + (decision.flags().isEmpty() ? NO_FLAGS
                : String.join(", ", decision.flags())), ""));
        if (!decision.search().isEmpty()) {
            lines.add(new Line(Kind.SEARCH, SEARCH_HEADING, ""));
            for (RunLog.SearchOutcome outcome : decision.search()) {
                lines.add(new Line(Kind.OUTCOME, label.apply(outcome.action()),
                        Columns.score(outcome.outcome()) + "  n=" + outcome.samples()));
            }
        }
        return List.copyOf(lines);
    }

    private static void choice(List<Line> lines, Kind kind, String prefix, RunLog.Choice choice,
                               Function<Action, String> label) {
        lines.add(new Line(kind, prefix + label.apply(choice.action()), Columns.score(choice.score())));
        if (!choice.why().isEmpty()) {
            lines.add(new Line(Kind.WHY, choice.why(), ""));
        }
        for (RunLog.Term term : choice.terms()) {
            lines.add(new Line(Kind.TERM, term.feature(), Columns.score(term.contribution())));
        }
    }
}
