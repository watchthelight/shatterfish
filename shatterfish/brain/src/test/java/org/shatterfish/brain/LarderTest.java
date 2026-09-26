package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.Observation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The food economy (story 4.13): the Brain's hunger clock, the budget, and frugal searching. */
class LarderTest {

    private static final Observation ROOM = ExplorePolicyTest.screen(2,
            "#######",
            "#..@..#",
            "#######");

    private static Observation at(int hp, Hunger hunger, org.shatterfish.api.ItemView... items) {
        return DescendPolicyTest.hero(ROOM, hp, 20, hunger, items);
    }

    @Test
    @DisplayName("the clock: each Action handed over adds what it costs, a meal takes off its energy, and the icon clamps it")
    void clock() {
        assertEquals(11, Larder.clock(10, "Step", true, 0, 0, OPEN, false, Hunger.NONE), "a Step taken: one turn");
        assertEquals(10, Larder.clock(10, "Step", false, 0, 0, OPEN, false, Hunger.NONE), "a Step refused: none");
        assertEquals(16, Larder.clock(10, "Search", false, 0, 0, OPEN, false, Hunger.NONE), "a search: two turns and four hunger");
        assertEquals(60, Larder.clock(10, "Rest", false, 5, 0, OPEN, false, Hunger.NONE), "a rest: ten turns a hit point restored");
        assertEquals(11, Larder.clock(10, "Rest", false, 0, 0, OPEN, false, Hunger.NONE), "a rest that restored nothing: a turn");
        assertEquals(0, Larder.clock(100, "UseItem", false, 0, 300, OPEN, false, Hunger.NONE), "a ration eaten, never below 0");
        assertEquals(299, Larder.clock(350, "Step", true, 0, 0, OPEN, false, Hunger.NONE), "no icon: under 300");
        assertEquals(300, Larder.clock(50, "Step", true, 0, 0, OPEN, false, Hunger.HUNGRY), "the hungry icon: at least 300");
        assertEquals(449, Larder.clock(600, "Step", true, 0, 0, OPEN, false, Hunger.HUNGRY), "the hungry icon: under 450");
        assertEquals(450, Larder.clock(0, "", false, 0, 0, OPEN, false, Hunger.STARVING), "starving: 450");
        assertEquals(201, Larder.clock(200, "UseItem", false, 0, 0, OPEN, false, Hunger.NONE), "an item used: one turn");
        assertEquals(3, Larder.clock(300, "UseItem", false, 0, 300, OPEN, false, Hunger.NONE),
                "a meal: three turns, not four, less its energy");
        assertEquals(10, Larder.clock(10, "AnswerPrompt", false, 0, 0, OPEN, false, Hunger.NONE), "an answer: no turn");
        assertEquals(10, Larder.clock(10, "DismissPrompt", false, 0, 0, OPEN, false, Hunger.NONE), "a dismissal: no turn");
        assertEquals(21, Larder.clock(10, "Rest", false, 15, 0, 11, false, Hunger.NONE),
                "a rest while a potion's heal lands: at most the heal's turns");
        assertEquals(10, Larder.clock(10, "Search", false, 0, 0, OPEN, true, Hunger.NONE), "a locked floor: no hunger");
        assertEquals(10, Larder.clock(10, "Rest", false, 5, 0, OPEN, true, Hunger.NONE), "nor for a rest there");
    }

    /** No cap on a rest's turns: no potion's heal is landing. */
    private static final int OPEN = Integer.MAX_VALUE;

    @Test
    @DisplayName("the fold keeps the clock, the hit points and the food held, and they survive the Belief's bytes")
    void folded() {
        Memory first = Beliefs.fold(Memory.START, at(10, Hunger.NONE, Screens.item(ItemKind.FOOD, "ration of food", 1)),
                Screens.CODEX);
        assertEquals(300, first.food());
        assertEquals(10, first.hp());
        Memory rested = Beliefs.fold(first.handed("Rest", -1), at(14, Hunger.NONE,
                Screens.item(ItemKind.FOOD, "ration of food", 1)), Screens.CODEX);
        assertEquals(40, rested.hunger(), "four hit points rested: forty turns");
        Memory ate = Beliefs.fold(rested.handed("UseItem", -1), at(14, Hunger.NONE), Screens.CODEX);
        assertEquals(0, ate.hunger(), "a ration eaten");
        assertEquals(ate, Memory.of(ate.belief()));
    }

    @Test
    @DisplayName("frugal under 600 turns: the food held plus what is left of the clock")
    void frugal() {
        Observation fed = at(8, Hunger.NONE, Screens.item(ItemKind.FOOD, "ration of food", 1));
        assertFalse(Larder.frugal(fed, Beliefs.fold(Memory.START, fed, Screens.CODEX)), "a ration and an empty stomach: 750");
        Observation empty = at(8, Hunger.NONE);
        assertTrue(Larder.frugal(empty, Beliefs.fold(Memory.START, empty, Screens.CODEX)), "no food: 450");
    }

    @Test
    @DisplayName("while food is tight the explore Policy searches only promising spots, at most four a floor; before a boss floor it searches in full")
    void frugal_searches() {
        Observation room = at(20, Hunger.NONE);
        Memory none = Beliefs.fold(Memory.START, room, Screens.CODEX);
        assertEquals(Explore.FRUGAL_SEARCHES, Explore.searches(room, none));
        Observation fed = at(20, Hunger.NONE, Screens.item(ItemKind.FOOD, "ration of food", 1));
        assertEquals(Explore.SEARCHES, Explore.searches(fed, Beliefs.fold(Memory.START, fed, Screens.CODEX)));
        Observation four = DescendPolicyTest.hero(ExplorePolicyTest.screen(4, "#######", "#..@..#", "#######"), 20, 20,
                Hunger.NONE);
        assertEquals(Explore.SEARCHES, Explore.searches(four, Beliefs.fold(Memory.START, four, Screens.CODEX)),
                "depth 4: the floor below is a boss floor");
        // Only promising spots while food is tight: this room's walls have no unseen cell near them, so
        // there is nothing to search, where a fed hero searches them.
        assertTrue(Explore.spent(room, none), "food tight: no promising wall, the floor is spent");
        assertFalse(Explore.spent(fed, Beliefs.fold(Memory.START, fed, Screens.CODEX)), "fed: the walls are searched");
    }
}
