package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The descend Policy (story 4.12, FR-31): the hero goes down when the floor's remaining value no longer
 * covers the risk of staying -- the floor is spent, the hero is hungry with no food, or it has
 * overstayed an allowance that grows with the guaranteed drops still expected on the floor -- and,
 * standing on the exit, rests to full first when it may; never from a sealed floor, never through a
 * locked exit, and never retrying a Step the game refuses.
 *
 * <p>Screens are {@link ExplorePolicyTest#screen}'s text rows, with the hero's health, hunger and pack
 * set here.
 */
class DescendPolicyTest {

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    /** {@code screen} with the hero at {@code hp} of {@code ht}, {@code hunger}, holding {@code items}. */
    static Observation hero(Observation screen, int hp, int ht, Hunger hunger, ItemView... items) {
        HeroSection h = screen.hero();
        HeroSection hero = new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(), h.level(), h.exp(),
                h.expToLevel(), hp, ht, h.shield(), h.strength(), h.strengthBonus(), h.gold(), h.energy(), hunger,
                h.buffs(), h.talents(), h.talentPointsAvailable(), h.quickslots());
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), hero,
                new InventorySection(List.of(items)), screen.journal(), screen.log(), ActionsSection.NONE,
                PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    /** {@code screen} with {@code cell}'s tile drawn as {@code tile}. */
    static Observation tile(Observation screen, int cell, Tile tile) {
        MapSection map = screen.map();
        List<Tile> tiles = new ArrayList<>(map.tiles());
        tiles.set(cell, tile);
        MapSection drawn = new MapSection(map.width(), map.height(), tiles, map.fog(), map.traps(), map.heaps(),
                map.blobs(), map.feeling(), map.transitions());
        Observation bare = new Observation(screen.header(), drawn, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    /** A memory with the floor's searches spent at {@code depth}, {@code waits} waits in, arrived at {@code arrived}. */
    static Memory spent(int depth, long waits, long arrived, int rests) {
        List<Memory.Spot> dwelt = new ArrayList<>();
        for (int i = 0; i < Explore.SEARCHES; i++) {
            dwelt.add(new Memory.Spot(depth, 0, 100 + i));
        }
        return at(dwelt, waits, arrived, rests);
    }

    /** A memory with {@code dwelt} searched, {@code waits} waits in, arrived at {@code arrived}. */
    static Memory at(List<Memory.Spot> dwelt, long waits, long arrived, int rests) {
        return new Memory(waits, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, arrived, rests);
    }

    private static final Observation ROOM = ExplorePolicyTest.screen(2,
            "#######",
            "#@..>.#",
            "#######");

    private static final Observation ON = ExplorePolicyTest.screen(2,
            "#######",
            "#..E..#",
            "#######");

    /** A corridor with a frontier left and the exit behind the hero. */
    private static final Observation OPEN = ExplorePolicyTest.screen(2,
            "##########",
            "#>.@....  ",
            "##########");

    @Test
    @DisplayName("on the exit of a spent floor, hurt and fed, it rests to full first; at full health it goes down")
    void rest_before_descent() {
        Brain.Decided hurt = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 10, 1, 0).belief());
        assertEquals(Descend.NAME, hurt.decision().policy());
        assertEquals(new Action.Rest(true), hurt.action());
        assertEquals("rest: descent", hurt.decision().chosen().why());

        Brain.Decided whole = brain().decide(hero(ON, 20, 20, Hunger.NONE), spent(2, 10, 1, 0).belief());
        assertEquals(new Action.Descend(), whole.action());
        assertEquals("descend: spent", whole.decision().chosen().why());
    }

    @Test
    @DisplayName("it does not rest hungry (the next floor has food), nor starving (no health comes back)")
    void no_rest_when_hungry() {
        for (Hunger hunger : List.of(Hunger.HUNGRY, Hunger.STARVING)) {
            Brain.Decided decided = brain().decide(hero(ON, 8, 20, hunger), spent(2, 10, 1, 0).belief());
            assertEquals(new Action.Descend(), decided.action(), hunger.toString());
        }
    }

    @Test
    @DisplayName("its rests before going down are bounded: after RESTS on a floor, it goes down hurt")
    void rests_are_bounded() {
        Brain.Decided last = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 30, 1, Descend.RESTS - 1).belief());
        assertEquals(new Action.Rest(true), last.action());
        Brain.Decided done = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 30, 1, Descend.RESTS).belief());
        assertEquals(new Action.Descend(), done.action());
    }

    @Test
    @DisplayName("over several waits through the Brain: a rest cut short is counted, and the hero goes down after RESTS")
    void rests_counted_through_the_brain() {
        Brain brain = brain();
        Observation hurt = hero(ON, 8, 20, Hunger.NONE);
        Belief belief = spent(2, 10, 1, 0).belief();
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < Descend.RESTS + 2; i++) {
            belief = brain.update(hurt, belief);
            Brain.Decided decided = brain.decide(hurt, belief);
            actions.add(decided.action());
            belief = brain.handed(hurt, belief, decided);
        }
        for (int i = 0; i < Descend.RESTS; i++) {
            assertEquals(new Action.Rest(true), actions.get(i), "wait " + i);
        }
        assertEquals(new Action.Descend(), actions.get(Descend.RESTS), "the bound reached, it goes down");
        assertEquals(Descend.RESTS, Memory.of(belief).rests(), "the descents are no rests");
    }

    @Test
    @DisplayName("hungry with no food held, it leaves a floor with more to see; with food it eats instead")
    void hungry_without_food() {
        Brain.Decided hungry = brain().decide(hero(OPEN, 20, 20, Hunger.HUNGRY), at(List.of(), 5, 1, 0).belief());
        assertEquals(Descend.NAME, hungry.decision().policy());
        assertEquals("exit: hungry 2", hungry.decision().chosen().why());
        assertEquals(new Action.Step(OPEN.hero().cell() - 1), hungry.action());

        Brain.Decided fed = brain().decide(hero(OPEN, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("ration of food", 1)),
                at(List.of(), 5, 1, 0).belief());
        assertEquals(Eat.NAME, fed.decision().policy());

        // Hungry holding only a pasty: the eat Policy waits for starving (a pasty wastes at hungry), and
        // the hero is not without food, so it does not leave a floor with more to see.
        Brain.Decided pasty = brain().decide(hero(OPEN, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("pasty", 1)),
                at(List.of(), 5, 1, 0).belief());
        assertEquals(Explore.NAME, pasty.decision().policy(), "food held is food, eaten now or not");
        assertFalse(Descend.fed(hero(OPEN, 20, 20, Hunger.HUNGRY)));
        assertFalse(Descend.fed(hero(OPEN, 20, 20, Hunger.HUNGRY,
                Screens.item(org.shatterfish.api.ItemKind.SCROLL, "scroll of KAUNAN", 1))), "a scroll is no food");
        Brain.Decided scroll = brain().decide(hero(OPEN, 20, 20, Hunger.HUNGRY,
                Screens.item(org.shatterfish.api.ItemKind.SCROLL, "scroll of KAUNAN", 1)), at(List.of(), 5, 1, 0).belief());
        assertEquals("exit: hungry 2", scroll.decision().chosen().why(), "hungry with only a scroll: down for food");
        assertTrue(Descend.fed(hero(OPEN, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("pasty", 1))));

        Brain.Decided full = brain().decide(hero(OPEN, 20, 20, Hunger.NONE), at(List.of(), 5, 1, 0).belief());
        assertEquals(Explore.NAME, full.decision().policy(), "not hungry, the frontier comes first");
    }

    @Test
    @DisplayName("past the floor's allowance it leaves a floor with more to see; before it, it explores")
    void overstayed() {
        long allowance = Descend.allowance(OPEN, at(List.of(), 5, 1, 0), Screens.CODEX);
        Brain.Decided before = brain().decide(OPEN, at(List.of(), allowance, 1, 0).belief());
        assertEquals(Explore.NAME, before.decision().policy(), "one wait short of the allowance");
        Brain.Decided after = brain().decide(OPEN, at(List.of(), allowance + 1, 1, 0).belief());
        assertEquals(Descend.NAME, after.decision().policy());
        assertEquals("exit: overstayed 2", after.decision().chosen().why());
    }

    @Test
    @DisplayName("the allowance grows with the guaranteed drops expected on this floor: the owed share of the floors left in the set")
    void allowance() {
        Memory none = at(List.of(), 5, 1, 0);
        // Nothing found in the first set: 2 potions of strength and 3 scrolls of upgrade owed. At depth
        // 1, four floors are left in the set; at depth 4, one.
        Observation one = ExplorePolicyTest.screen(1, "#@#");
        assertEquals(5.0 / 4, Descend.expectedHere(one, none, Screens.CODEX), 1e-9);
        assertEquals(Descend.ALLOWANCE + Math.round(Descend.PER_DROP * 5.0 / 4), Descend.allowance(one, none, Screens.CODEX));
        Observation four = ExplorePolicyTest.screen(4, "#@#");
        assertEquals(5.0, Descend.expectedHere(four, none, Screens.CODEX), 1e-9);
        // The boss floor closing the set places none, and neither does the next set's first floor
        // for what the first set owed.
        assertEquals(0.0, Descend.expectedHere(ExplorePolicyTest.screen(5, "#@#"), none, Screens.CODEX), 1e-9);
        assertEquals(Descend.ALLOWANCE, Descend.allowance(ExplorePolicyTest.screen(5, "#@#"), none, Screens.CODEX));
        // What was found is owed no more.
        Memory found = new Memory(5, 1, List.of(), List.of(new Memory.Found("STRENGTH_POTIONS", 0, 2),
                new Memory.Found("UPGRADE_SCROLLS", 0, 1)), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, 1, 0);
        assertEquals(2.0 / 4, Descend.expectedHere(one, found, Screens.CODEX), 1e-9);
    }

    @Test
    @DisplayName("a sealed floor: it never goes down, nor walks to the exit")
    void sealed() {
        Observation sealedOn = ExplorePolicyTest.screen(2, true, HeroClass.WARRIOR,
                "#######",
                "#..E..#",
                "#######");
        Descend descend = new Descend(Screens.CODEX);
        assertFalse(descend.enters(sealedOn, spent(2, 10, 1, 0)));
        Brain.Decided decided = brain().decide(sealedOn, spent(2, 10, 1, 0).belief());
        assertEquals(Policies.FALLBACK, decided.decision().policy());
        Observation sealedHungry = hero(ExplorePolicyTest.screen(2, true, HeroClass.WARRIOR,
                "##########",
                "#>.@....  ",
                "##########"), 20, 20, Hunger.HUNGRY);
        assertFalse(descend.enters(sealedHungry, at(List.of(), 5, 1, 0)));
    }

    @Test
    @DisplayName("a locked exit is no way down: the boss floor's until its key opens it")
    void locked_exit() {
        int exit = ROOM.map().transitions().get(0).cell();
        Observation locked = tile(ROOM, exit, Tile.LOCKED_EXIT);
        Brain.Decided decided = brain().decide(locked, spent(2, 10, 1, 0).belief());
        assertFalse(Descend.NAME.equals(decided.decision().policy()), decided.decision().toString());
        Observation unlocked = tile(ROOM, exit, Tile.UNLOCKED_EXIT);
        assertEquals("exit: spent 3", brain().decide(unlocked, spent(2, 10, 1, 0).belief()).decision().chosen().why());
    }

    @Test
    @DisplayName("a Step toward the exit the game keeps refusing: it yields once and the cell is blocked, then goes round")
    void refused_step() {
        Observation corridor = ExplorePolicyTest.screen(2,
                "#######",
                "#@..>.#",
                "#.....#",
                "#######");
        Memory start = spent(2, 10, 1, 0);
        Brain brain = brain();
        assertEquals(new Action.Step(corridor.hero().cell() + 1), brain.decide(corridor, brain.update(corridor, start.belief())).action());
        // Drive three waits on the same screen: the Step handed over twice is refused twice.
        Belief belief = start.belief();
        List<Brain.Decided> decided = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            belief = brain.update(corridor, belief);
            Brain.Decided one = brain.decide(corridor, belief);
            decided.add(one);
            belief = brain.handed(corridor, belief, one);
        }
        assertEquals(Descend.NAME, decided.get(0).decision().policy());
        assertEquals(Descend.NAME, decided.get(1).decision().policy());
        assertFalse(Descend.NAME.equals(decided.get(2).decision().policy()), "the third wait on the cell is not its");
        assertTrue(Memory.of(belief).blocked().contains(new Memory.Spot(2, 0, corridor.hero().cell() + 1)),
                Memory.of(belief).blocked().toString());
        assertEquals(Descend.NAME, decided.get(3).decision().policy(), "and then down again");
        assertFalse(decided.get(3).action().equals(new Action.Step(corridor.hero().cell() + 1)), "round the refused cell");
        assertNull(Descend.stepCell(ON, spent(2, 10, 1, 0), Screens.CODEX), "no Step on the exit");
    }

    @Test
    @DisplayName("a floor seen first starts its stay and its rests afresh")
    void arrival() {
        Brain brain = brain();
        Belief belief = brain.update(ROOM, null);
        assertEquals(1, Memory.of(belief).arrived());
        belief = brain.update(ROOM, belief);
        belief = brain.update(ROOM, belief);
        assertEquals(1, Memory.of(belief).arrived(), "the same floor");
        Memory rested = Memory.of(belief).resting().resting();
        assertEquals(2, rested.rests());
        Observation deeper = ExplorePolicyTest.screen(3, "#######", "#@..>.#", "#######");
        Memory there = Memory.of(brain.update(deeper, rested.belief()));
        assertEquals(4, there.arrived());
        assertEquals(0, there.rests());
        assertEquals(there, Memory.of(there.belief()), "the Belief carries both");
    }

    @Test
    @DisplayName("the same screen and memory, the same Decision")
    void deterministic() {
        Belief belief = spent(2, 10, 1, 0).belief();
        assertEquals(brain().decide(ROOM, belief).decision(), brain().decide(ROOM, belief).decision());
    }
}
