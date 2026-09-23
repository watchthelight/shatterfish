package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain never assumes its previous Decision was executed (story 4.1, FR-27, FR-28).
 *
 * <p>Each case feeds a Decision, then an Observation that could only follow a different Action --
 * a human's turn, or an input the executor refused -- and asserts the next Decision is computed from
 * that Observation and nothing else.
 */
class ReplanAfterForeignActionTest {

    private static final Action[] FLOOR = {new Action.Step(0), new Action.Step(2), new Action.Search(),
            new Action.Rest(false), new Action.Wait()};

    @Test
    @DisplayName("two Brains whose last Decisions differed decide alike once the screen is the same")
    void the_screen_decides_not_the_plan() {
        // Two seeds, so the two Brains chose differently at the first wait; then a human's turn
        // opened a Prompt neither of them chose. Both now answer the Prompt, the same way, for the
        // same reason: what either of them meant to do a moment ago does not enter into it.
        BrainDecider one = new BrainDecider(new Brain(Screens.CODEX, 1L));
        BrainDecider other = new BrainDecider(new Brain(Screens.CODEX, 2L));
        Observation floor = Screens.offering(1, FLOOR);
        Action mine = null;
        Action theirs = null;
        for (int i = 0; i < 20 && (mine == null || mine.equals(theirs)); i++) {
            one = new BrainDecider(new Brain(Screens.CODEX, 1L + 2 * i));
            other = new BrainDecider(new Brain(Screens.CODEX, 2L + 2 * i));
            mine = one.decide(floor);
            theirs = other.decide(floor);
        }
        assertNotEquals(mine, theirs, "the case needs two Brains that chose differently");

        Observation prompt = Screens.prompting(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1));
        Action next = one.decide(prompt);

        assertEquals(new Action.AnswerPrompt(0), next);
        assertEquals(next, other.decide(prompt));
        assertEquals(one.lastDecision().policy(), other.lastDecision().policy());
        assertEquals(one.belief(), other.belief(), "and they believe the same: the Belief holds what was seen");
    }

    @Test
    @DisplayName("an Action the screen no longer offers is never taken, whatever was intended")
    void no_stale_action() {
        for (long seed = 0; seed < 50; seed++) {
            BrainDecider brain = new BrainDecider(new Brain(Screens.CODEX, seed));
            Action intended = brain.decide(Screens.offering(1, FLOOR));
            // The Action it chose was not applied, and it is no longer offered.
            Observation after = Screens.offering(1, new Action.Step(2), new Action.Search(), new Action.Wait());
            if (after.actions().actions().contains(intended)) {
                continue;
            }
            Action next = brain.decide(after);
            assertTrue(after.actions().actions().contains(next), seed + ": " + next);
        }
    }

    @Test
    @DisplayName("the Decision at a wait is the one a fresh Brain makes from the same screens")
    void nothing_carries_but_the_belief() {
        // A Brain that decided at the first wait, and a Brain built afresh and shown the same two
        // screens, decide the second alike: the first Decision left nothing behind but the Belief,
        // and the Belief is a function of the screens.
        Observation first = Screens.offering(1, FLOOR);
        Observation second = Screens.offering(2, new Action.Descend(), new Action.Search(), new Action.Wait());
        BrainDecider used = new BrainDecider(new Brain(Screens.CODEX, 7L));
        used.decide(first);
        Action after = used.decide(second);

        Brain fresh = new Brain(Screens.CODEX, 7L);
        var belief = fresh.update(second, fresh.update(first, null));
        assertEquals(fresh.decide(second, belief).action(), after);
        assertEquals(belief, used.belief());
    }
}
