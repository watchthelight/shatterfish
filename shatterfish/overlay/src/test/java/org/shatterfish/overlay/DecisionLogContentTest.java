package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DecisionLogContent#of} (story 5.4, FR-38): one line per wait, with the turn, the actor,
 * the Action and its score, a goal-change line and a Mode-change line where either happens, held
 * against constructed {@link RunLog} records -- a view over them, not a second source of truth.
 */
class DecisionLogContentTest {

    private static RunLog.Wait wait(long k, long turn, String actor, Action action, RunLog.Decision decision) {
        return new RunLog.Wait(k, turn, 1, 0, "a".repeat(64), Map.of("hero", "a".repeat(64)), action, true, actor,
                decision, "", List.of(), 0);
    }

    private static RunLog.Decision decision(String goal, Action action, long score) {
        return new RunLog.Decision(goal, new RunLog.Choice(action, score, "reason"), List.of(), List.of(), "policy");
    }

    @Test
    @DisplayName("one line per wait: turn, actor, Action and score")
    void one_line_per_wait() {
        List<RunLog> history = List.of(
                wait(1, 1000, RunLog.BOT, new Action.Search(), decision("explore: floor", new Action.Search(), 10_000)));

        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);

        assertEquals(2, lines.size(), "the goal line, then the wait's own line");
        assertEquals("goal: explore: floor", lines.get(0).text());
        assertEquals("turn " + Columns.number(1, ModeStripContent.TURN_WIDTH) + "  bot  search  " + Columns.score(10_000),
                lines.get(1).text());
    }

    @Test
    @DisplayName("newest at the bottom: waits appear in the history's own order")
    void newest_at_the_bottom() {
        List<RunLog> history = List.of(
                wait(1, 1000, RunLog.BOT, new Action.Search(), decision("explore", new Action.Search(), 1_000)),
                wait(2, 2000, RunLog.BOT, new Action.Wait(), decision("explore", new Action.Wait(), 500)));

        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);

        // The goal line once (unchanged the second time), then two wait lines.
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).text().contains("search"));
        assertTrue(lines.get(2).text().contains("wait"), "the second wait's line is last: newest at the bottom");
    }

    @Test
    @DisplayName("a goal change gets a line of its own where it happens, and only where it changes")
    void goal_change_gets_its_own_line() {
        List<RunLog> history = List.of(
                wait(1, 1000, RunLog.BOT, new Action.Search(), decision("explore: floor", new Action.Search(), 1_000)),
                wait(2, 2000, RunLog.BOT, new Action.Wait(), decision("explore: floor", new Action.Wait(), 500)),
                wait(3, 3000, RunLog.BOT, new Action.Attack(5), decision("fight: gnoll", new Action.Attack(5), 900)));

        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);

        // goal:explore, wait1, wait2 (no repeat), goal:fight, wait3.
        assertEquals(5, lines.size());
        assertEquals("goal: explore: floor", lines.get(0).text());
        assertEquals("goal: fight: gnoll", lines.get(3).text());
    }

    @Test
    @DisplayName("a Mode change (ADR-0013's RunLog.Mode, unwritten by any headless caller today) gets a line of its own")
    void mode_change_gets_its_own_line() {
        List<RunLog> history = List.of(
                wait(1, 1000, RunLog.BOT, new Action.Search(), decision("explore", new Action.Search(), 1_000)),
                new RunLog.Mode(2, "PAUSED", "Next Step"),
                wait(3, 3000, RunLog.HUMAN, new Action.Wait(), null));

        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);

        // goal:explore, wait1, mode:PAUSED, wait3 (no Decision, so no goal line for it).
        assertEquals(4, lines.size());
        assertEquals("mode: PAUSED Next Step", lines.get(2).text());
        assertTrue(lines.get(3).text().contains("human"), "the actor: a human's turn (ADR-0011)");
    }

    @Test
    @DisplayName("a wait with no Decision (a plain Decider) shows an em dash where the score would be, not a number")
    void no_decision_shows_an_em_dash() {
        List<RunLog> history = List.of(wait(1, 1000, RunLog.BOT, new Action.Wait(), null));
        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);
        assertEquals(1, lines.size(), "no Decision, so no goal line either");
        assertTrue(lines.get(0).text().endsWith(ModeStripContent.NO_INTERVAL_YET));
    }

    @Test
    @DisplayName("an empty history is an empty log")
    void empty_history() {
        assertEquals(List.of(), DecisionLogContent.of(List.of()));
    }
}
