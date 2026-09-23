package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.RunLog;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Composite outcome's order, step by step (story 3.6, PRD glossary).
 *
 * <p>Each case holds every earlier step equal and moves exactly one later one the other way, so it
 * passes only if that step is consulted, at that position, in that direction. The expectations are
 * the verdict a person would give, written down, not computed.
 */
class PairScoreTest {

    /** A Run: win, score, bosses, depth, turns. */
    private static RunLog.Outcome run(boolean win, long score, int bosses, int depth, long turns) {
        return new RunLog.Outcome(win, false, score, depth, turns, win ? "WIN" : "DEATH", bosses);
    }

    @Test
    @DisplayName("a win beats any loss, however deep or long the loss")
    void a_win_comes_first() {
        assertEquals(PairScore.BETTER, PairScore.of(run(true, 1, 0, 1, 1), run(false, 99999, 5, 26, 99999)));
        assertEquals(PairScore.WORSE, PairScore.of(run(false, 99999, 5, 26, 99999), run(true, 1, 0, 1, 1)));
    }

    @Test
    @DisplayName("between two wins, the higher Score, whatever came after")
    void score_decides_two_wins() {
        assertEquals(PairScore.BETTER, PairScore.of(run(true, 500, 1, 5, 9), run(true, 400, 5, 26, 99)));
    }

    @Test
    @DisplayName("between two losses Score is not consulted, because it rewards gold picked up on the way to dying")
    void score_is_ignored_for_losses() {
        assertEquals(PairScore.EQUAL, PairScore.of(run(false, 9000, 1, 5, 100), run(false, 10, 1, 5, 100)));
    }

    @Test
    @DisplayName("then bosses killed, above depth, so diving past nothing is not rewarded")
    void bosses_before_depth() {
        assertEquals(PairScore.BETTER, PairScore.of(run(false, 0, 1, 5, 100), run(false, 0, 0, 9, 900)));
    }

    @Test
    @DisplayName("then depth, above turns")
    void depth_after_bosses() {
        assertEquals(PairScore.BETTER, PairScore.of(run(false, 0, 1, 6, 100), run(false, 0, 1, 5, 900)));
    }

    @Test
    @DisplayName("then turns survived, where more is better")
    void turns_last() {
        assertEquals(PairScore.BETTER, PairScore.of(run(false, 0, 1, 5, 101), run(false, 0, 1, 5, 100)));
        assertEquals(PairScore.WORSE, PairScore.of(run(false, 0, 1, 5, 99), run(false, 0, 1, 5, 100)));
    }

    @Test
    @DisplayName("equal at every step is a tie, and a missing Run is a tie")
    void ties() {
        assertEquals(PairScore.EQUAL, PairScore.of(run(false, 3, 1, 5, 100), run(false, 3, 1, 5, 100)));
        assertEquals(PairScore.EQUAL, PairScore.of(null, run(true, 1, 1, 1, 1)),
                "a Brain cannot win by crashing on the seeds it would lose");
        assertEquals(PairScore.EQUAL, PairScore.of(run(true, 1, 1, 1, 1), null));
    }

    @Test
    @DisplayName("the scores are zero, one and two halves")
    void halves() {
        assertEquals(0, PairScore.WORSE.halves());
        assertEquals(1, PairScore.EQUAL.halves());
        assertEquals(2, PairScore.BETTER.halves());
    }
}
