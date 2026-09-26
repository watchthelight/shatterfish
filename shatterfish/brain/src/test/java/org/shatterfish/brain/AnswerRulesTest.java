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
        assertEquals(new Action.AnswerPrompt(0), afterReading(upgrading(List.of("Upgrade", "Back"))).action());
        Brain.Decided byLabel = afterReading(upgrading(List.of("Back", "Upgrade")));
        assertEquals(new Action.AnswerPrompt(1), byLabel.action(), "by the label, not the position");
        assertEquals("upgrade: shortsword", byLabel.decision().chosen().why(), "the reason names the item read onto");
        assertThrows(Answers.BrainError.class, () -> afterReading(upgrading(List.of("Back"))),
                "no upgrade button is a Brain error, not the back button");
    }

    @Test
    @DisplayName("the upgrade window the game chains after an upgrade is a Brain error, never a second upgrade")
    void chained_upgrade() {
        Brain brain = brain();
        Observation first = upgrading(List.of("Upgrade", "Back"));
        Belief belief = read(brain, first);
        Brain.Decided confirmed = brain.decide(first, belief);
        assertEquals(new Action.AnswerPrompt(0), confirmed.action());
        belief = brain.handed(first, belief, confirmed);
        // WndUpgrade.java:447-455: with another scroll held, the same window opens again at once.
        Observation again = upgrading(List.of("Upgrade", "Back"));
        Belief chained = brain.update(again, belief);
        Answers.BrainError error = assertThrows(Answers.BrainError.class, () -> brain.decide(again, chained));
        assertTrue(error.getMessage().contains("shortsword"), error.getMessage());
        assertTrue(error instanceof org.shatterfish.api.Decider.CannotDecide, "the error the Run loop ends on");
    }

    @Test
    @DisplayName("the chained upgrade window is confirmed when its item is the worn armour, where every upgrade goes (story 4.13)")
    void chained_upgrade_on_the_armour() {
        Brain brain = brain();
        Observation first = armoured("cloth armor");
        Belief chained = chain(brain, "cloth armor", org.shatterfish.api.EquipSlot.ARMOR, first);
        assertEquals(new Action.AnswerPrompt(0), brain.decide(first, chained).action(),
                "the second scroll goes onto the armour too");
        // An upgrade that lifts a curse renames the armour (Armor.java:471-472, :578): the chain still
        // follows the worn slot, not the name the read was handed over with.
        Observation renamed = armoured("cloth armor of flow");
        Belief after = chain(brain, "cursed cloth armor", org.shatterfish.api.EquipSlot.ARMOR, renamed);
        assertEquals(new Action.AnswerPrompt(0), brain.decide(renamed, after).action(), "renamed, still worn");
    }

    @Test
    @DisplayName("a chained upgrade window after a read onto an item that is not the worn armour is a Brain error, even when the names match")
    void chained_upgrade_off_the_armour() {
        Brain brain = brain();
        Observation first = armoured("cloth armor");
        Belief chained = chain(brain, "cloth armor", org.shatterfish.api.EquipSlot.NONE, first);
        assertThrows(Answers.BrainError.class, () -> brain.decide(first, chained));
    }

    /** The upgrade window over a pack whose worn armour is named {@code name}. */
    private static Observation armoured(String name) {
        org.shatterfish.api.ItemView worn = new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.ARMOR,
                name, 1, true, 0, true, false, "", org.shatterfish.api.EquipSlot.ARMOR, List.of(), "");
        return Screens.asked(HeroClass.WARRIOR, PromptKind.UPGRADE, "Upgrade an Item", List.of("Upgrade", "Back"),
                List.of(worn));
    }

    /**
     * The Belief after a read onto an item named {@code name} in {@code slot}, the upgrade window it
     * opened confirmed, and the game's chained window, {@code window}, drawn.
     */
    private static Belief chain(Brain brain, String name, org.shatterfish.api.EquipSlot slot, Observation window) {
        org.shatterfish.api.ItemView item = new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.ARMOR,
                name, 1, true, 0, true, false, "", slot, List.of(), "");
        org.shatterfish.api.ItemView scroll = new org.shatterfish.api.ItemView(org.shatterfish.api.ItemKind.SCROLL,
                "scroll of upgrade", 2, true, 0, true, false, "", org.shatterfish.api.EquipSlot.NONE, List.of("READ"), "");
        Action onto = new Action.UseItemOn(new org.shatterfish.api.ItemRef(1, "scroll of upgrade", 2), "READ",
                new org.shatterfish.api.ItemRef(0, name, 1));
        Observation before = Screens.holding(List.of(item, scroll), onto);
        Belief belief = brain.update(before, null);
        belief = brain.handed(before, belief, new Brain.Decided(onto, null, List.of(), ""));
        belief = brain.update(window, belief);
        Brain.Decided confirmed = brain.decide(window, belief);
        assertEquals(new Action.AnswerPrompt(0), confirmed.action());
        belief = brain.handed(window, belief, confirmed);
        return brain.update(window, belief);
    }

    @Test
    @DisplayName("an upgrade window no read of the Brain's opened is a Brain error")
    void upgrade_without_a_read() {
        assertThrows(Answers.BrainError.class, () -> decide(upgrading(List.of("Upgrade", "Back"))));
    }

    /** The upgrade window, as drawn with {@code labels}. */
    private static Observation upgrading(List<String> labels) {
        return Screens.asked(HeroClass.WARRIOR, PromptKind.UPGRADE, "Upgrade an Item", labels, List.of());
    }

    /** The read of a scroll of upgrade onto the shortsword, as the Brain hands it over. */
    static final Action READ = new Action.UseItemOn(new org.shatterfish.api.ItemRef(2, "scroll of upgrade", 1), "READ",
            new org.shatterfish.api.ItemRef(0, "shortsword", 1));

    /** The Belief at {@code window}, the Brain having handed over the read that opened it. */
    static Belief read(Brain brain, Observation window) {
        Observation before = Screens.offering(1, new Action.Wait());
        Belief belief = brain.update(before, null);
        belief = brain.handed(before, belief, new Brain.Decided(READ, null, List.of(), ""));
        return brain.update(window, belief);
    }

    /** What the Brain decides at {@code window}, after the read that opened it. */
    private static Brain.Decided afterReading(Observation window) {
        Brain brain = brain();
        return brain.decide(window, read(brain, window));
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
                "Scroll of Upgrade", Answers.SCROLL_CANCEL, List.of("Yes, I'm positive", "No, I changed my mind"),
                List.of())), "story 4.10's scroll cancel is affirmed: its no reopens a picker no Action answers");
        assertEquals(new Action.AnswerPrompt(1), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.HARMFUL_POTION,
                "Harmful Potion", "Are you sure you want to drink it?", List.of("Yes, I'm positive",
                        "No, I changed my mind"), List.of())), "every other item confirmation is declined (story 4.10)");
        assertEquals(new Action.DismissPrompt(), decide(Screens.asked(HeroClass.WARRIOR, PromptKind.MESSAGE,
                "Sewers", List.of(), List.of())));
    }
}
