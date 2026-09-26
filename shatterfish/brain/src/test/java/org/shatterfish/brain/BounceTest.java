package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Back and forth between two cells (story 4.13): two Policies that undo each other's Step -- the fight
 * Policy approaching an enemy that shows only from one cell, the pick-up Policy walking back to a heap
 * from the other -- went on for hundreds of turns. After {@link Memory#BOUNCES} the Step back is withheld.
 */
class BounceTest {

    private static Observation at(int x) {
        StringBuilder row = new StringBuilder("#.......#");
        row.setCharAt(x, '@');
        return ExplorePolicyTest.screen(2, "#########", row.toString(), "#########");
    }

    @Test
    @DisplayName("the fold counts the waits in a row the hero is back on the cell of two waits ago, and starts again otherwise")
    void counted() {
        Memory memory = Memory.START;
        int[] xs = {3, 4, 3, 4, 3, 4, 5};
        int[] expected = {0, 0, 1, 2, 3, 4, 0};
        for (int i = 0; i < xs.length; i++) {
            memory = Beliefs.fold(memory, at(xs[i]), Screens.CODEX);
            assertEquals(expected[i], memory.bounces(), "wait " + i);
        }
        assertEquals(at(4).hero().cell(), memory.prior(), "the cell of two waits ago");
        Memory still = Beliefs.fold(memory, at(5), Screens.CODEX);
        assertEquals(0, still.bounces(), "standing still is no bounce");
        assertEquals(memory, Memory.of(memory.belief()), "prior and bounces survive the Belief's bytes");
    }

    @Test
    @DisplayName("after BOUNCES, the Step back to the cell of two waits ago is not a choice for any Policy this wait")
    void withheld() {
        int back = at(3).hero().cell();
        Memory bouncing = Memory.START;
        for (int i = 0; i < Memory.BOUNCES + 2; i++) {
            bouncing = Beliefs.fold(bouncing, at(i % 2 == 0 ? 3 : 4), Screens.CODEX);
        }
        assertTrue(bouncing.bounces() >= Memory.BOUNCES, "bounces " + bouncing.bounces());
        assertEquals(back, bouncing.prior());
        Brain.Decided decided = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(at(4), bouncing.belief());
        assertNotEquals(new Action.Step(back), decided.action(), decided.decision().toString());
        for (RunLog.Choice choice : decided.decision().alternatives()) {
            assertNotEquals(new Action.Step(back), choice.action(), "not even as an alternative");
        }
        Brain.Decided fresh = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L).decide(at(4),
                Beliefs.fold(Memory.START, at(4), Screens.CODEX).belief());
        assertTrue(fresh.decision() != null, "without the bounces the Brain decides as it would");
    }
}
