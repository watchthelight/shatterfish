package org.shatterfish.overlay;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.ActionContext;
import org.shatterfish.harness.agent.BoundedLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Decision log's lines (story 5.4, FR-38): one per Input wait, with the turn, the actor, the
 * Action and its score, newest at the bottom; a goal change or a Mode change gets a line of its
 * own where it happens.
 *
 * <p>This is a view over the Run log's own records ({@link RunLog.Wait}, {@link RunLog.Mode}), not
 * a second source of truth (the epic's own acceptance criterion): every line is read straight off
 * a record {@code EmbeddedRun} already built for the file ({@code EmbeddedRun.history()}), and a
 * goal change is <em>derived</em> from two consecutive waits' own {@code Decision.goal()} rather
 * than being a record of its own -- the Run log carries no "goal changed" kind, and inventing one
 * only for the Overlay to read back would be a second source for something {@code Wait} already
 * states. {@link RunLog.Mode} already exists in the schema (ADR-0013) for a real Mode change, and
 * nothing writes one yet (stories 5.5 to 5.7); this class already reads it, so those stories add a
 * writer, not a reader.
 *
 * <p>A pure function of the history list, in the style of {@link ModeStripContent}: a test holds it
 * against constructed {@link RunLog} records without booting the game or driving a Run.
 */
final class DecisionLogContent {

    /** One line of the log; muted or ink is the component's call ({@code DecisionLog}), not this class's. */
    record Line(String text) {
    }

    private DecisionLogContent() {
    }

    /** {@code history}'s lines, oldest first, newest last, with a goal-change and a Mode-change line where either happens. */
    static List<Line> of(List<BoundedLog.Entry> history) {
        List<Line> lines = new ArrayList<>();
        String goal = null;
        for (BoundedLog.Entry entry : history) {
            RunLog record = entry.record();
            if (record instanceof RunLog.Wait wait) {
                String thisGoal = wait.decision() == null ? null : wait.decision().goal();
                if (thisGoal != null && !thisGoal.equals(goal)) {
                    lines.add(new Line("goal: " + thisGoal));
                    goal = thisGoal;
                }
                lines.add(new Line(waitLine(wait, entry.context())));
            } else if (record instanceof RunLog.Mode mode) {
                lines.add(new Line(modeLine(mode)));
            }
            // Prompt, Shadow, Boundary, Unsupported, Header and End: not a wait and not a line of
            // their own here. A Prompt rides beside the wait that answered it, which already has a
            // line; the rest are not something an Input wait's log reads back.
        }
        return List.copyOf(lines);
    }

    /**
     * {@code turn <n>  <actor>  <action>  <score>}, an em dash where a Decider with no Decision
     * leaves no score. {@code context} (story 5.4's review round) is what {@link ActionText} needs
     * to read {@code wait}'s Action the same way the Decision card would (a compass direction, a
     * named target, an option's text), not a raw cell; null when none was captured (a history entry
     * constructed directly, without going through {@code EmbeddedRun}).
     */
    private static String waitLine(RunLog.Wait wait, ActionContext context) {
        String turn = "turn " + Columns.number(wait.turn() / 1000, ModeStripContent.TURN_WIDTH);
        String action = ActionText.of(wait.action(), context);
        String score = wait.decision() == null ? ModeStripContent.NO_INTERVAL_YET
                : Columns.score(wait.decision().chosen().score());
        return turn + "  " + wait.actor() + "  " + action + "  " + score;
    }

    /** {@code mode: <MODE> <speed>} (ADR-0013's Mode record; unwritten by any headless caller today). */
    private static String modeLine(RunLog.Mode mode) {
        return "mode: " + mode.mode() + " " + mode.speed();
    }
}
