package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of every Decision (story 4.4, FR-32, UX-DR13): a Goal, the chosen Action with its score,
 * at least one alternative whenever the screen offers another Action, distinct alternatives, and
 * reasons written as labels and numbers rather than sentences.
 */
class DecisionShapeTest {

    /** Screens of every kind the first Policies meet: ordinary, a Prompt, a single Action. */
    private static List<Observation> screens() {
        List<Observation> screens = new ArrayList<>();
        screens.add(Screens.offering(1, new Action.Wait()));
        screens.add(Screens.offering(1, new Action.Wait(), new Action.Search()));
        screens.add(Screens.offering(2, new Action.Wait(), new Action.Search(), new Action.Step(0),
                new Action.Step(2), new Action.Rest(false), new Action.Descend()));
        screens.add(Screens.prompting(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1)));
        screens.add(Screens.prompting(new Action.AnswerPrompt(1)));
        screens.add(Screens.prompting(new Action.DismissPrompt()));
        screens.add(Screens.asking(List.of("Take the gold", "Take the key", "Cancel"),
                new Action.AnswerPrompt(0), new Action.AnswerPrompt(1), new Action.AnswerPrompt(2)));
        return screens;
    }

    @Test
    @DisplayName("every Decision has a Goal, and an alternative whenever another Action is offered")
    void goal_and_alternatives() {
        int decisions = 0;
        for (long seed = 0; seed < 20; seed++) {
            Brain brain = new Brain(Screens.CODEX, seed);
            Belief belief = null;
            for (Observation screen : screens()) {
                belief = brain.update(screen, belief);
                RunLog.Decision decision = brain.decide(screen, belief).decision();
                assertFalse(decision.goal().isBlank(), decision.toString());
                int offered = screen.actions().actions().size();
                if (offered > 1) {
                    assertFalse(decision.alternatives().isEmpty(), "another Action was offered: " + decision);
                }
                assertTrue(decision.alternatives().size() <= Math.min(RunLog.Decision.ALTERNATIVES, offered - 1),
                        decision.toString());
                Set<Action> actions = new HashSet<>();
                actions.add(decision.chosen().action());
                for (RunLog.Choice alternative : decision.alternatives()) {
                    assertTrue(actions.add(alternative.action()), "alternatives are distinct Actions: " + decision);
                    assertTrue(screen.actions().actions().contains(alternative.action()), decision.toString());
                }
                decisions++;
            }
        }
        assertEquals(20 * screens().size(), decisions);
    }

    @Test
    @DisplayName("an ordinary screen of six Actions records three alternatives, each scored one in six")
    void three_alternatives() {
        Brain brain = new Brain(Screens.CODEX, 3L);
        Observation screen = screens().get(2);
        RunLog.Decision decision = brain.decide(screen, brain.update(screen, null)).decision();
        assertEquals(3, decision.alternatives().size());
        assertEquals(1_667, decision.chosen().score(), "one in six, rounded to the nearest ten-thousandth");
        for (RunLog.Choice alternative : decision.alternatives()) {
            assertEquals(1_667, alternative.score());
            assertEquals("uniform 1/6", alternative.why());
        }
    }

    @Test
    @DisplayName("the prompt Policy scores its pick and names the button it presses")
    void the_prompt_says_which_button() {
        Brain brain = new Brain(Screens.CODEX, 3L);
        Observation screen = screens().get(6);
        RunLog.Decision decision = brain.decide(screen, brain.update(screen, null)).decision();
        assertEquals(new Action.AnswerPrompt(2), decision.chosen().action());
        assertEquals(Policies.CERTAIN, decision.chosen().score());
        assertEquals("decline: Cancel", decision.chosen().why());
        assertEquals("answer: Take the gold", decision.alternatives().get(0).why());
        assertEquals(0, decision.alternatives().get(0).score());
        assertEquals("prompt: close", decision.goal());
    }

    /** A label: a lower-case word, then ": " and a value, or a space and a number (UX-DR13). */
    private static final java.util.regex.Pattern LABEL = java.util.regex.Pattern.compile("^[a-z-]+(: .+| [0-9/]+)?$");

    @Test
    @DisplayName("reasons and goals are labels and numbers, not sentences")
    void labels_not_sentences() {
        int said = 0;
        for (long seed = 0; seed < 10; seed++) {
            Brain brain = new Brain(Screens.CODEX, seed);
            for (Observation screen : screens()) {
                RunLog.Decision decision = brain.decide(screen, brain.update(screen, null)).decision();
                List<String> texts = new ArrayList<>(List.of(decision.goal(), decision.chosen().why()));
                decision.alternatives().forEach(alternative -> texts.add(alternative.why()));
                for (String text : texts) {
                    assertTrue(LABEL.matcher(text).matches(), "a label, not a sentence: \"" + text + "\"");
                    assertTrue(text.length() <= 40, "a label, not a paragraph: " + text);
                    said++;
                }
            }
        }
        assertTrue(said > 100, "labels were checked: " + said);
        for (String sentence : List.of("the first answer the prompt offers", "Close the prompt.", "act, when idle")) {
            assertFalse(LABEL.matcher(sentence).matches(), "the grammar refuses a sentence: " + sentence);
        }
    }

    @Test
    @DisplayName("the Safety flags are read off the screen, and the cells the chosen Action targets are highlighted")
    void flags_and_highlights() {
        Brain brain = new Brain(Screens.CODEX, 0L);
        Observation calm = Screens.offering(1, new Action.Step(2));
        Brain.Decided decided = brain.decide(calm, brain.update(calm, null));
        assertEquals(List.of(), decided.decision().flags());
        assertEquals(List.of(2), decided.highlights(), "a Step highlights the cell it steps to");

        Observation hurt = Screens.hurt(1, 3, new Action.Wait());
        Brain.Decided flagged = brain.decide(hurt, brain.update(hurt, null));
        assertEquals(List.of(Safety.HP_LOW, Safety.ENEMY_IN_VIEW, Safety.STARVING), flagged.decision().flags());
        assertEquals(List.of(), flagged.highlights(), "a Wait points at no cell");
        assertFalse(Safety.flags(Screens.hurt(2, 3, new Action.Wait())).contains(Safety.HP_LOW),
                "two of three is not low");
        // The status pane's threshold, HP/HT < 0.334 (StatusPane.java:300-310), exactly.
        assertTrue(Safety.flags(Screens.hurt(333, 1000, new Action.Wait())).contains(Safety.HP_LOW));
        assertFalse(Safety.flags(Screens.hurt(334, 1000, new Action.Wait())).contains(Safety.HP_LOW),
                "0.334 is not below 0.334");
        assertTrue(Safety.flags(Screens.hurt(0, 20, new Action.Wait())).contains(Safety.HP_LOW), "zero health is low");
    }
}
