package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The Decision card's content, from a constructed {@link RunLog.Decision} (story 5.3, FR-38, FR-39,
 * UX-DR3, UX-DR5, UX-DR13).
 *
 * <p>The card shows the chosen Action with its score and up to three alternatives with scores and
 * one-line reasons (the epic's own acceptance criterion). The Explain expansion opens below it, inside
 * the Panel, and what it says is {@link ExplainContent}'s: the Policy, every reason and term table, the
 * flags and the Search block (story 8.2). This class only knows whether it is open.
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

    /** One row: the Action, its exact-decimal score, and its reason. */
    record Row(Action action, String score, String reason) {
    }

    /**
     * @param present     whether a Decision has been served yet
     * @param headline    {@link #NEXT_PRESS} in Next Step mode, null otherwise
     * @param chosen      the chosen Action's row, null when {@code !present}
     * @param alternatives up to {@link RunLog.Decision#ALTERNATIVES} rows, empty when {@code !present}
     * @param explaining  whether the Explain expansion is open; what it says is {@link ExplainContent}'s
     */
    record Content(boolean present, String headline, Row chosen, List<Row> alternatives, boolean explaining,
                   boolean shadow, String notice, boolean troubled) {

        /** A card with no Brain trouble on it. */
        Content(boolean present, String headline, Row chosen, List<Row> alternatives, boolean explaining,
                boolean shadow, String notice) {
            this(present, headline, chosen, alternatives, explaining, shadow, notice, false);
        }

        /** A Brain's card: not a shadow, and nothing to say about a Replay. */
        Content(boolean present, String headline, Row chosen, List<Row> alternatives, boolean explaining) {
            this(present, headline, chosen, alternatives, explaining, false, null);
        }
    }

    /**
     * A HUMAN Run's card (story 5.9): the Decision is the Brain's shadow, never executed, and greyed;
     * {@code current} when it answers the wait the person is looking at; {@code unverifiableFrom} the
     * first wait a Replay cannot reproduce (0 for none), with why.
     */
    record Shadow(boolean current, long unverifiableFrom, String why, boolean person) {

        /** A HUMAN Run's shadow. */
        Shadow(boolean current, long unverifiableFrom, String why) {
            this(current, unverifiableFrom, why, true);
        }

        /**
         * The notice alone, for a Brain's Run that was a person's before (a hand back, issue #220): the card is
         * the Brain's own, not a shadow, and says where the Replay stops.
         */
        static Shadow noticeOnly(long unverifiableFrom, String why) {
            return new Shadow(false, unverifiableFrom, why, false);
        }
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
        return of(decision, nextStep, explain, shadow, null);
    }

    /**
     * As above, for a Brain that failed to decide (story 5.15, UX-DR7): {@code trouble} is the card's words,
     * {@code brain error: <class>} or {@code no valid action; waiting}, and they are the headline, over the
     * wait the Overlay chose in the Brain's place. Null for a Brain that decided.
     */
    static Content of(RunLog.Decision decision, boolean nextStep, boolean explain, Shadow shadow, String trouble) {
        String notice = shadow == null || shadow.unverifiableFrom() <= 0 ? null
                : UNVERIFIABLE + shadow.unverifiableFrom() + ": " + shadow.why();
        if (decision == null) {
            return new Content(false, null, null, List.of(), false, shadow != null && shadow.person(), notice);
        }
        boolean person = shadow != null && shadow.person();
        String headline = trouble != null ? trouble
                : person ? (shadow.current() ? SHADOW_NOW : SHADOW_LAST) : nextStep ? NEXT_PRESS : null;
        Row chosen = row(decision.chosen());
        List<Row> alternatives = decision.alternatives().stream().map(DecisionCardContent::row).toList();
        return new Content(true, headline, chosen, alternatives, explain, person, notice, trouble != null);
    }

    private static Row row(RunLog.Choice choice) {
        return new Row(choice.action(), Columns.score(choice.score()), choice.why());
    }
}
