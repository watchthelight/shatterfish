package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The Brain is a function of its Observations and its seed (story 4.1): the same sequence and seed
 * give the same Actions, Decisions and Beliefs, and another seed gives another stream.
 */
class BrainDeterminismTest {

    /** A sequence of forty screens with offered sets of varying size, and a Prompt among them. */
    private static List<Observation> screens() {
        List<Observation> screens = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            if (i % 13 == 7) {
                screens.add(Screens.prompting(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)));
                continue;
            }
            List<Action> offered = new ArrayList<>(List.of(new Action.Wait(), new Action.Search()));
            for (int cell = 0; cell < 1 + i % 3; cell++) {
                offered.add(new Action.Step(cell));
            }
            screens.add(Screens.offering(1 + i / 10, offered.toArray(new Action[0])));
        }
        return screens;
    }

    private static List<String> play(long seed) {
        BrainDecider brain = new BrainDecider(new Brain(Screens.CODEX, Screens.WEIGHTS, seed));
        List<String> trace = new ArrayList<>();
        for (Observation screen : screens()) {
            Action action = brain.decide(screen);
            trace.add(action + " " + brain.lastDecision() + " " + brain.belief().hash());
        }
        return trace;
    }

    @Test
    @DisplayName("the same screens and seed give the same Actions, Decisions and Beliefs")
    void deterministic() {
        assertEquals(play(42L), play(42L));
    }

    @Test
    @DisplayName("another seed is another stream")
    void the_seed_matters() {
        assertNotEquals(play(42L), play(43L));
    }
}
