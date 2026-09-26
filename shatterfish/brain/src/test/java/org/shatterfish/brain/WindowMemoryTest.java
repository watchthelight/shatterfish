package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A window the Brain left is not reopened on the same floor (story 4.11): the Action that opened a
 * shop, a guess or a spell list is remembered while the window is open and shunned once the prompt
 * Policy leaves it, so a Brain cannot open and leave the same window forever with no time passing.
 */
class WindowMemoryTest {

    private static final Action TALK = new Action.Interact(2);

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
    }

    private static Observation shop() {
        return Screens.asked(HeroClass.WARRIOR, PromptKind.SHOP, "Shopkeeper", List.of("Sell an item", "Talk"),
                List.of());
    }

    /** The Belief after the Brain hands over {@code opener} at a calm screen, sees {@code window}, and answers it. */
    private static Belief openAndAnswer(Brain brain, Action opener, Observation window) {
        Observation calm = Screens.offering(1, new Action.Wait());
        Belief belief = brain.update(calm, null);
        belief = brain.handed(calm, belief, new Brain.Decided(opener, null, List.of(), ""));
        belief = brain.update(window, belief);
        Brain.Decided answer = brain.decide(window, belief);
        return brain.handed(window, belief, answer);
    }

    @Test
    @DisplayName("a shop opened by a talk and left: the talk is not offered to the Policies again on that floor")
    void a_left_window_is_not_reopened() {
        Brain brain = brain();
        Belief belief = openAndAnswer(brain, TALK, shop());
        Memory memory = Memory.of(belief);
        assertEquals(List.of(new Memory.Shun(1, 0, TALK.toString())), memory.windows().shunned(),
                "the talk that opened the shop is shunned on depth 1");

        Observation after = Screens.offering(1, TALK, new Action.Wait());
        belief = brain.update(after, belief);
        assertEquals(List.of(new Action.Wait()), Brain.unshunned(after, Memory.of(belief)));
        assertEquals(new Action.Wait(), brain.decide(after, belief).action(), "the only Action left");

        Observation deeper = Screens.offering(2, TALK, new Action.Wait());
        assertEquals(List.of(TALK, new Action.Wait()), Brain.unshunned(deeper, Memory.of(brain.update(deeper, belief))),
                "on another floor it is offered again");
        Observation only = Screens.offering(1, TALK);
        assertEquals(List.of(TALK), Brain.unshunned(only, Memory.of(brain.update(only, belief))),
                "and when it is all there is, it stays");
    }

    @Test
    @DisplayName("a window answered rather than left shuns nothing, nor does a window a Step or a search opened")
    void only_a_left_window_by_an_opener_is_shunned() {
        Observation guess = Screens.asked(HeroClass.WARRIOR, PromptKind.GUESS, "Crimson Potion",
                List.of("Potion of Healing"), List.of(Screens.item(org.shatterfish.api.ItemKind.POTION, "crimson potion", 1)));
        Action stone = new Action.UseItemOn(new org.shatterfish.api.ItemRef(1, "stone of intuition", 1), "USE",
                new org.shatterfish.api.ItemRef(2, "crimson potion", 1));
        Brain brain = brain();
        assertTrue(Memory.of(openAndAnswer(brain, stone, guess)).windows().shunned().isEmpty(),
                "a guess made is not a window left");
        assertTrue(Memory.of(openAndAnswer(brain(), new Action.Step(2), shop())).windows().shunned().isEmpty(),
                "a Step onto an item for sale is never shunned");
        assertTrue(Memory.of(openAndAnswer(brain(), new Action.Search(), shop())).windows().shunned().isEmpty(),
                "a search opens no shop; whatever opened it, it was not the Brain's search");
    }

    @Test
    @DisplayName("a shop that follows an answer has no opener: the answer closed a window, it opened none")
    void an_answer_opens_nothing() {
        Brain brain = brain();
        Observation calm = Screens.offering(1, new Action.Wait());
        Belief belief = brain.update(calm, null);
        belief = brain.handed(calm, belief, new Brain.Decided(new Action.AnswerPrompt(0), null, List.of(), ""));
        assertEquals("", Memory.of(brain.update(shop(), belief)).windows().opener());
        belief = brain.handed(calm, belief, new Brain.Decided(TALK, null, List.of(), ""));
        assertEquals(TALK.toString(), Memory.of(brain.update(shop(), belief)).windows().opener(),
                "while a talk did open it");
    }

    @Test
    @DisplayName("the windows survive the Belief's bytes, and a new window forgets the old opener")
    void the_windows_round_trip() {
        Brain brain = brain();
        Belief belief = openAndAnswer(brain, TALK, shop());
        Memory memory = Memory.of(belief);
        assertEquals(memory, Memory.of(memory.belief()), "the bytes carry every field of the windows");
        assertTrue(Memory.VERSION >= 9, "version 9 brought the windows (story 4.11)");
        Observation calm = Screens.offering(1, new Action.Wait());
        Memory later = Memory.of(brain.update(calm, belief));
        assertEquals("", later.windows().opener(), "no window open, no opener");
        assertFalse(later.windows().shunned().isEmpty(), "while the shun stays");
    }
}
