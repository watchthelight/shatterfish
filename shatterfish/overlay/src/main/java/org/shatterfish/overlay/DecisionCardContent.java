package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The Decision card's content, from a constructed {@link RunLog.Decision} (story 5.3, FR-38, FR-39,
 * UX-DR3, UX-DR5, UX-DR13).
 *
 * <p>The collapsed card already shows the chosen Action with its score and up to three alternatives
 * with scores and one-line reasons (the epic's own acceptance criterion) -- so what the Explain
 * expansion adds is the Policy that fired and the Safety flags that applied; the alternatives' reasons
 * are already the whole reason a {@link RunLog.Choice} carries ({@code why()} is one line by
 * construction, {@code DecisionShapeTest}), so there is no truncated form to expand from, and Explain
 * repeats them unchanged (a design choice recorded in the story file: showing a second, longer reason
 * would be inventing text no Brain said).
 *
 * <p>A {@link Row} carries the {@link Action} itself, not a rendered label: {@code DecisionCard} turns
 * it into words through {@code ActionText} (the review that found {@code Action.toString()} on
 * screen, "Step[cell=659]"), which needs the Observation the Decision was made on for a Step's
 * direction, an Attack's target and an AnswerPrompt's option text. This class stays free of that --
 * it is the Decision's shape, not its rendering.
 */
final class DecisionCardContent {

    /** The card before any Decision has been served (UX-DR14: a state stated in words). */
    static final String NO_DECISION_YET = "no decision yet";

    /** The headline shown only in Next Step mode, since the chosen Action has not executed (the setup's AC). */
    static final String NEXT_PRESS = "next press executes:";

    /** The label before a flag list with nothing in it (UX-DR14: absence is a word, not a blank space). */
    static final String NO_FLAGS = "none";

    /** One row: the Action, its exact-decimal score, and its reason. */
    record Row(Action action, String score, String reason) {
    }

    /** The Policy and the Safety flags the Explain expansion adds (null unless {@code explain} was asked for). */
    record Explain(String policy, List<String> flags, String policyLine, String flagsLine) {
        Explain(String policy, List<String> flags) {
            this(policy, flags, "policy: " + policy,
                    "flags: " + (flags.isEmpty() ? NO_FLAGS : String.join(", ", flags)));
        }
    }

    /**
     * @param present     whether a Decision has been served yet
     * @param headline    {@link #NEXT_PRESS} in Next Step mode, null otherwise
     * @param chosen      the chosen Action's row, null when {@code !present}
     * @param alternatives up to {@link RunLog.Decision#ALTERNATIVES} rows, empty when {@code !present}
     * @param explain     the Policy and flags, null unless asked for
     */
    record Content(boolean present, String headline, Row chosen, List<Row> alternatives, Explain explain,
                   boolean shadow, String notice) {

        /** A Brain's card: not a shadow, and nothing to say about a Replay. */
        Content(boolean present, String headline, Row chosen, List<Row> alternatives, Explain explain) {
            this(present, headline, chosen, alternatives, explain, false, null);
        }
    }

    /**
     * A HUMAN Run's card (story 5.9): the Decision is the Brain's shadow, never executed, and greyed;
     * {@code current} when it answers the wait the person is looking at; {@code unverifiableFrom} the
     * first wait a Replay cannot reproduce (0 for none), with why.
     */
    record Shadow(boolean current, long unverifiableFrom, String why) {
    }

    /** The headline over a shadow that answers the wait in front (UX-DR14: said in words, not only greyed). */
    static final String SHADOW_NOW = "shadow, not executed: the Brain would";

    /** The headline over a shadow of a wait the person has already taken. */
    static final String SHADOW_LAST = "shadow of your last turn, not executed";

    /** The Panel's word that a Replay stops at a wait (FR-4, the story's acceptance criterion). */
    static final String UNVERIFIABLE = "replay unverifiable from wait ";

    private DecisionCardContent() {
    }

    /** {@code decision}'s content: {@link #NO_DECISION_YET} when it is null, the rows and Explain otherwise. */
    static Content of(RunLog.Decision decision, boolean nextStep, boolean explain) {
        return of(decision, nextStep, explain, null);
    }

    /** As above, and for a HUMAN Run's {@code shadow} (null for a Brain's Run): its headline, greyed rows and notice. */
    static Content of(RunLog.Decision decision, boolean nextStep, boolean explain, Shadow shadow) {
        String notice = shadow == null || shadow.unverifiableFrom() <= 0 ? null
                : UNVERIFIABLE + shadow.unverifiableFrom() + ": " + shadow.why();
        if (decision == null) {
            return new Content(false, null, null, List.of(), null, shadow != null, notice);
        }
        String headline = shadow != null ? (shadow.current() ? SHADOW_NOW : SHADOW_LAST) : nextStep ? NEXT_PRESS : null;
        Row chosen = row(decision.chosen());
        List<Row> alternatives = decision.alternatives().stream().map(DecisionCardContent::row).toList();
        Explain explanation = explain ? new Explain(decision.policy(), decision.flags()) : null;
        return new Content(true, headline, chosen, alternatives, explanation, shadow != null, notice);
    }

    private static Row row(RunLog.Choice choice) {
        return new Row(choice.action(), Columns.score(choice.score()), choice.why());
    }
}
