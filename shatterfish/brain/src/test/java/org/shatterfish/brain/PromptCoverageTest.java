package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Prompt kind has an answer rule (story 4.11, AC: {@code PromptCoverageTest}). The executor
 * answers any Prompt by its buttons or the back key, whatever its kind, so every kind the
 * Observation can carry is one it supports; each is given a representative screen as the harness
 * draws it, and the Brain answers it with one of the Prompt's own Actions, never a Wait.
 */
class PromptCoverageTest {

    /** A screen per kind, as the game draws it: its title and labels. */
    private static final Map<PromptKind, List<String>> LABELS = Map.ofEntries(
            Map.entry(PromptKind.SUBCLASS, List.of("The _Berserker_ rages.", "The _Gladiator_ combos.", "I'll decide later")),
            Map.entry(PromptKind.TALENT, List.of("Yes", "No")),
            Map.entry(PromptKind.QUEST, List.of("I'll help", "Not now")),
            Map.entry(PromptKind.SHOP, List.of("Buy for 10g")),
            Map.entry(PromptKind.ALCHEMY, List.of("Brew")),
            Map.entry(PromptKind.CHASM_JUMP, List.of("Yes", "No")),
            Map.entry(PromptKind.HARMFUL_POTION, List.of("Yes", "No")),
            Map.entry(PromptKind.RESURRECTION, List.of("Resurrect")),
            Map.entry(PromptKind.ITEM, List.of("Yes", "No")),
            Map.entry(PromptKind.OTHER, List.of("Left", "Right")),
            Map.entry(PromptKind.MESSAGE, List.of()),
            Map.entry(PromptKind.UPGRADE, List.of("Upgrade", "Back")),
            Map.entry(PromptKind.GUESS, List.of("Potion of Frost", "Potion of Healing")),
            Map.entry(PromptKind.SPELL, List.of()));

    @Test
    @DisplayName("every Prompt kind has a rule, and every kind but none has a screen here")
    void every_kind_has_a_rule() {
        for (PromptKind kind : PromptKind.values()) {
            if (kind == PromptKind.NONE) {
                assertThrows(Answers.BrainError.class, () -> Answers.rule(kind), "no Prompt is nothing to answer");
                continue;
            }
            assertNotNull(Answers.rule(kind), kind + " has a rule");
            assertTrue(LABELS.containsKey(kind), kind + " has a representative screen in this test");
        }
    }

    @Test
    @DisplayName("under every kind of Prompt the Brain answers with one of the Prompt's own Actions, never a Wait")
    void never_a_wait() {
        for (Map.Entry<PromptKind, List<String>> screen : LABELS.entrySet()) {
            Observation observation = Screens.asked(HeroClass.WARRIOR, screen.getKey(), "A Prompt", screen.getValue(),
                    List.of());
            Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
            // The upgrade window is answered only when the Brain's own read opened it.
            org.shatterfish.api.Belief belief = screen.getKey() == PromptKind.UPGRADE
                    ? AnswerRulesTest.read(brain, observation) : brain.update(observation, null);
            Brain.Decided decided = brain.decide(observation, belief);
            Action action = decided.action();
            assertTrue(action instanceof Action.AnswerPrompt || action instanceof Action.DismissPrompt,
                    screen.getKey() + " is answered or dismissed: " + action);
            assertTrue(observation.actions().actions().contains(action), screen.getKey() + " offered it");
            assertEquals(Policies.ANSWER_PROMPT, decided.decision().policy(), screen.getKey() + " by the prompt Policy");
        }
    }
}
