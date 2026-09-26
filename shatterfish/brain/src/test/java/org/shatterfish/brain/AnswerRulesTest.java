package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each kind of Prompt answered by its own rule (story 4.11): the subclass by the class, the shop and
 * the spell list left, the upgrade confirmed, the guess made from the Beliefs, and a Prompt whose
 * rule recognises nothing a Brain error, never a Wait.
 */
class AnswerRulesTest {

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 11L);
    }

    private static Action decide(Observation screen) {
        Brain brain = brain();
        return brain.decide(screen, brain.update(screen, null)).action();
    }

    /** The subclass window as the game draws it for a class (actors.properties:838-871; windows.properties:37). */
    private static List<String> subclasses(String first, String second) {
        return List.of("The _" + first + "_ does one thing.", "The _" + second + "_ does another.", "I'll decide later");
    }

    @Test
    @DisplayName("the subclass window: the class's chosen subclass, whichever button it is, then yes to the are-you-sure")
    void subclass() {
        assertEquals(new Action.AnswerPrompt(0), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.SUBCLASS,
                "Tengu's Mask", subclasses("Berserker", "Gladiator"), List.of())));
        assertEquals(new Action.AnswerPrompt(1), decide(Screens.asked(HeroClass.CLERIC, PromptKind.SUBCLASS,
                "Tengu's Mask", subclasses("Priest", "Paladin"), List.of())), "the Cleric takes the Paladin, the second button");
        assertEquals(new Action.AnswerPrompt(1), decide(Screens.asked(HeroClass.HUNTRESS, PromptKind.SUBCLASS,
                "Tengu's Mask", subclasses("Sniper", "Warden"), List.of())));
        // The are-you-sure, titled with the subclass (WndChooseSubclass.java:103-108), yes first.
        assertEquals(new Action.AnswerPrompt(0), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.SUBCLASS,
                "Berserker", List.of("Yes, I've made my choice.", "No, I'll decide later."), List.of())));
        // An are-you-sure for a subclass the Brain did not choose is not affirmed: nothing it knows.
        assertThrows(Answers.BrainError.class, () -> decide(Screens.asked(HeroClass.WARRIOR, PromptKind.SUBCLASS,
                "Gladiator", List.of("Yes, I've made my choice.", "No, I'll decide later."), List.of())));
    }

    @Test
    @DisplayName("a word inside another word does not name a subclass")
    void names_are_words() {
        assertTrue(Answers.names("The _Monk_ builds energy", "Monk"));
        assertTrue(!Answers.names("Monkey business", "Monk"));
        assertTrue(!Answers.names("A Chimonk", "Monk"));
        assertTrue(Answers.names("monk", "Monk"), "case-blind");
    }

    @Test
    @DisplayName("a shop is left, whatever it offers to buy")
    void shop() {
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.SHOP,
                "Food for 10g", List.of("Buy for 10g"), List.of())));
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.SHOP,
                "Shopkeeper", List.of("Sell an item", "Talk"), List.of())), "not the sell button, which opens the bag");
    }

    @Test
    @DisplayName("the spell list is left")
    void spell() {
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.CLERIC, PromptKind.SPELL,
                "Cast a Spell", List.of(), List.of())));
    }

    @Test
    @DisplayName("the upgrade window is confirmed, never sent back to the selector")
    void upgrade() {
        assertEquals(new Action.AnswerPrompt(0), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.UPGRADE,
                "Upgrade an Item", List.of("Upgrade", "Back"), List.of())));
        assertEquals(new Action.AnswerPrompt(1), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.UPGRADE,
                "Upgrade an Item", List.of("Back", "Upgrade"), List.of())), "by the label, not the position");
        assertThrows(Answers.BrainError.class, () -> decide(Screens.asked(HeroClass.WARRIOR, PromptKind.UPGRADE,
                "Upgrade an Item", List.of("Back"), List.of())), "no upgrade button is a Brain error, not the back button");
    }

    /** The guess window for the crimson potion, as the harness lists it: the guess button first once shown, then the icons by name. */
    private static Observation guessing(List<String> labels) {
        return Screens.asked(HeroClass.WARRIOR, PromptKind.GUESS, "Crimson Potion", labels,
                List.of(Screens.item(ItemKind.POTION, "crimson potion", 1)));
    }

    private static final List<String> ICONS = List.of("Potion of Frost", "Potion of Healing", "Potion of Mind Vision",
            "Potion of Strength");

    @Test
    @DisplayName("the guess: the likeliest identity by the Beliefs, its icon and then the guess button, over two waits")
    void guess() {
        // Screens.CODEX: strength is weighted as the heaviest (a guarantee) and ties healing at 6;
        // the tie goes by name, so healing is the likeliest identity of the one potion held.
        Brain brain = brain();
        Observation icons = guessing(ICONS);
        Belief belief = brain.update(icons, null);
        Brain.Decided tapped = brain.decide(icons, belief);
        assertEquals(new Action.AnswerPrompt(1), tapped.action(), "the healing icon");
        belief = brain.handed(icons, belief, tapped);

        List<String> shown = new java.util.ArrayList<>(List.of("Potion of Healing"));
        shown.addAll(ICONS);
        Observation button = guessing(shown);
        belief = brain.update(button, belief);
        assertEquals(new Action.AnswerPrompt(0), brain.decide(button, belief).action(),
                "the guess button, labelled with the choice, which the window lists first");
    }

    @Test
    @DisplayName("the guess is left when the Beliefs have no odds for the item or its likeliest identity is not offered")
    void guess_left() {
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.GUESS,
                "Mystery Potion", ICONS, List.of(Screens.item(ItemKind.POTION, "mystery potion", 1)))),
                "no family knows the appearance");
        assertEquals(new Action.DismissPrompt(), decide(guessing(List.of("Potion of Frost", "Potion of Strength"))),
                "healing, the likeliest, is not among the icons");
    }

    @Test
    @DisplayName("the general rule still declines a chasm and affirms the broken seal")
    void general() {
        assertEquals(new Action.AnswerPrompt(1), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.CHASM_JUMP,
                "Chasm", List.of("Yes", "No"), List.of())));
        assertEquals(new Action.AnswerPrompt(0), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.ITEM,
                "Broken Seal", List.of("Yes", "No"), List.of())));
        assertEquals(new Action.AnswerPrompt(0), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.ITEM,
                "Scroll of Upgrade", List.of("Yes, I'm positive", "No, I changed my mind"), List.of())),
                "story 4.10's cancel confirmation: no declining label, so the lowest answer");
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.MESSAGE,
                "Sewers", List.of(), List.of())));
    }
}
