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
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, arrived, rests, -1, -1, List.of());
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

    /** The Decisions at each of {@code screens} in turn, driven as the Brain's driver drives them, from {@code start}. */
    private static List<Brain.Decided> each(Memory start, Observation... screens) {
        Brain brain = brain();
        Belief belief = start.belief();
        List<Brain.Decided> all = new ArrayList<>();
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
            all.add(decided);
            belief = brain.handed(screen, belief, decided);
        }
        return all;
    }

    /** A spent floor's corridor with the hero at column {@code x} of row 1, at {@code hp} of 20. */
    private static Observation corridor(int x, int hp) {
        StringBuilder row = new StringBuilder("#......>#");
        row.setCharAt(x, '@');
        return hero(ExplorePolicyTest.screen(2, "#########", row.toString(), "#########"), hp, 20, Hunger.NONE);
    }

    @Test
    @DisplayName("walking to the exit of a spent floor hurt: it stops beside the exit and rests to full, and only then takes the Step that travels")
    void rests_beside_the_exit() {
        // The last Step onto the exit takes the stairs (a click on a transition cell with no enemy in
        // view), so the rest has to come before it, on the cell beside the exit.
        List<Brain.Decided> walk = each(spent(2, 10, 1, 0),
                corridor(1, 8), corridor(2, 8), corridor(3, 8), corridor(4, 8), corridor(5, 8), corridor(6, 8),
                corridor(6, 8), corridor(6, 20));
        for (int i = 0; i < 5; i++) {
            assertEquals(new Action.Step(corridor(i + 1, 8).hero().cell() + 1), walk.get(i).action(), "wait " + i);
            assertTrue(walk.get(i).decision().chosen().why().startsWith("exit: spent "), walk.get(i).decision().toString());
        }
        assertEquals(new Action.Rest(true), walk.get(5).action(), "beside the exit, hurt: rest, not the Step");
        assertEquals("rest: descent", walk.get(5).decision().chosen().why());
        assertEquals(new Action.Rest(true), walk.get(6).action());
        assertEquals(new Action.Step(corridor(6, 20).hero().cell() + 1), walk.get(7).action(), "healed: onto the exit");
        assertEquals("exit: spent 1", walk.get(7).decision().chosen().why());
    }

    @Test
    @DisplayName("beside the exit, its rests are bounded: after RESTS on the floor it takes the Step hurt")
    void rests_beside_are_bounded() {
        Observation beside = corridor(6, 8);
        Observation[] screens = new Observation[Descend.RESTS + 1];
        java.util.Arrays.fill(screens, beside);
        List<Brain.Decided> all = each(spent(2, 10, 1, 0), screens);
        for (int i = 0; i < Descend.RESTS; i++) {
            assertEquals(new Action.Rest(true), all.get(i).action(), "wait " + i);
        }
        assertEquals(new Action.Step(beside.hero().cell() + 1), all.get(Descend.RESTS).action(), "the bound reached");
    }

    @Test
    @DisplayName("come up from the floor below onto the exit of a spent floor: it rests there, then goes down")
    void rest_on_arrival() {
        Brain.Decided hurt = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 10, 1, 0).belief());
        assertEquals(Descend.NAME, hurt.decision().policy());
        assertEquals(new Action.Rest(true), hurt.action());
        assertEquals("rest: descent", hurt.decision().chosen().why());

        Brain.Decided whole = brain().decide(hero(ON, 20, 20, Hunger.NONE), spent(2, 10, 1, 0).belief());
        assertEquals(new Action.Descend(), whole.action());
        assertEquals("descend: spent", whole.decision().chosen().why());
    }

    @Test
    @DisplayName("it rests hungry, which regenerates, but not starving, which does not")
    void no_rest_when_starving() {
        assertEquals(new Action.Rest(true), brain().decide(hero(ON, 8, 20, Hunger.HUNGRY), spent(2, 10, 1, 0).belief()).action());
        assertEquals(new Action.Descend(), brain().decide(hero(ON, 8, 20, Hunger.STARVING), spent(2, 10, 1, 0).belief()).action());
        assertEquals(new Action.Step(corridor(6, 8).hero().cell() + 1),
                brain().decide(hero(corridor(6, 8), 8, 20, Hunger.STARVING), spent(2, 10, 1, 0).belief()).action());
    }

    @Test
    @DisplayName("its rests on the exit are bounded: after RESTS on a floor, it goes down hurt")
    void rests_are_bounded() {
        Brain.Decided last = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 30, 1, Descend.RESTS - 1).belief());
        assertEquals(new Action.Rest(true), last.action());
        Brain.Decided done = brain().decide(hero(ON, 8, 20, Hunger.NONE), spent(2, 30, 1, Descend.RESTS).belief());
        assertEquals(new Action.Descend(), done.action());
    }

    @Test
    @DisplayName("over several waits through the Brain: a rest cut short is counted, and the hero goes down after RESTS")
    void rests_counted_through_the_brain() {
        Observation hurt = hero(ON, 8, 20, Hunger.NONE);
        Observation[] screens = new Observation[Descend.RESTS + 2];
        java.util.Arrays.fill(screens, hurt);
        List<Brain.Decided> all = each(spent(2, 10, 1, 0), screens);
        for (int i = 0; i < Descend.RESTS; i++) {
            assertEquals(new Action.Rest(true), all.get(i).action(), "wait " + i);
        }
        assertEquals(new Action.Descend(), all.get(Descend.RESTS).action(), "the bound reached, it goes down");
        Brain brain = brain();
        Belief belief = spent(2, 10, 1, 0).belief();
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            belief = brain.handed(screen, belief, brain.decide(screen, belief));
        }
        assertEquals(Descend.RESTS, Memory.of(belief).rests(), "the descents are no rests");
    }

    @Test
    @DisplayName("hungry with no food and nothing left to uncover, it leaves; with a frontier left, food held, or a boss floor below, it does not")
    void hungry_without_food() {
        // The room is uncovered, but its walls not yet searched: not spent.
        Brain.Decided hungry = brain().decide(hero(ROOM, 20, 20, Hunger.HUNGRY), at(List.of(), 5, 1, 0).belief());
        assertEquals(Descend.NAME, hungry.decision().policy(), hungry.decision().toString());
        assertEquals("exit: hungry 3", hungry.decision().chosen().why());

        Brain.Decided scroll = brain().decide(hero(ROOM, 20, 20, Hunger.HUNGRY,
                Screens.item(org.shatterfish.api.ItemKind.SCROLL, "scroll of KAUNAN", 1)), at(List.of(), 5, 1, 0).belief());
        assertEquals("exit: hungry 3", scroll.decision().chosen().why(), "a scroll is no food");
        assertFalse(Descend.fed(hero(ROOM, 20, 20, Hunger.HUNGRY,
                Screens.item(org.shatterfish.api.ItemKind.SCROLL, "scroll of KAUNAN", 1))));

        Brain.Decided ration = brain().decide(hero(ROOM, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("ration of food", 1)),
                at(List.of(), 5, 1, 0).belief());
        assertEquals(Eat.NAME, ration.decision().policy());

        // Hungry holding only a pasty: the eat Policy waits for starving (a pasty wastes at hungry), and
        // the hero is not without food, so the searches go on.
        Brain.Decided pasty = brain().decide(hero(ROOM, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("pasty", 1)),
                at(List.of(), 5, 1, 0).belief());
        assertEquals(Explore.NAME, pasty.decision().policy(), "food held is food, eaten now or not");
        assertTrue(Descend.fed(hero(ROOM, 20, 20, Hunger.HUNGRY, EatPolicyTest.food("pasty", 1))));

        // A frontier left: this floor's own food is likelier there than anywhere.
        Brain.Decided open = brain().decide(hero(OPEN, 20, 20, Hunger.HUNGRY), at(List.of(), 5, 1, 0).belief());
        assertEquals(Explore.NAME, open.decision().policy(), "explore on while a frontier is left");

        // Floor 4: the floor below is Goo's, which places no food and seals behind the hero.
        Observation four = hero(ExplorePolicyTest.screen(4, "#######", "#@..>.#", "#######"), 20, 20, Hunger.HUNGRY);
        assertTrue(Descend.bossNext(four, Screens.CODEX));
        assertEquals(null, Descend.leaving(four, at(List.of(), 5, 1, 0), Screens.CODEX));
        assertEquals(Explore.NAME, brain().decide(four, at(List.of(), 5, 1, 0).belief()).decision().policy());
    }

    @Test
    @DisplayName("spent or overstayed with no exit shown: it never steps toward the fog for one, and searches on past explore's budget")
    void exit_never_seen() {
        Observation closed = ExplorePolicyTest.screen(2,
                "#######",
                "#@....#",
                "#.....#",
                "#######");
        assertTrue(closed.map().transitions().isEmpty());
        Brain.Decided decided = brain().decide(closed, spent(2, 10, 1, 0).belief());
        assertEquals(Descend.NAME, decided.decision().policy(), decided.decision().toString());
        assertTrue(decided.decision().chosen().why().startsWith("no exit: search"), decided.decision().chosen().why());

        // The extra searches spent too: nothing of its own to do.
        List<Memory.Spot> all = new ArrayList<>();
        for (int i = 0; i < Descend.SEARCHES; i++) {
            all.add(new Memory.Spot(2, 0, 100 + i));
        }
        Brain.Decided done = brain().decide(closed, at(all, 10, 1, 0).belief());
        assertFalse(Descend.NAME.equals(done.decision().policy()), done.decision().toString());

        // Overstayed with a frontier left and no exit shown: it uncovers the frontier, a cell the screen
        // shows, never a Step into the fog.
        Observation fog = ExplorePolicyTest.screen(2,
                "##########",
                "#..@....  ",
                "##########");
        Brain.Decided late = brain().decide(fog, at(List.of(), 5000, 1, 0).belief());
        assertEquals(Descend.NAME, late.decision().policy());
        assertTrue(late.decision().chosen().why().startsWith("no exit: frontier"), late.decision().chosen().why());
        Action.Step step = (Action.Step) late.action();
        assertEquals(org.shatterfish.api.Fog.VISIBLE, fog.map().fog().get(step.cell()));
    }

    @Test
    @DisplayName("it stands aside while the explore Policy owes a rest before going back down, or the hero is inside a region the fight Policy retreated from")
    void yields_to_the_fight_plans() {
        List<Memory.Spot> dwelt = new ArrayList<>();
        for (int i = 0; i < Explore.SEARCHES; i++) {
            dwelt.add(new Memory.Spot(2, 0, 100 + i));
        }
        // Fled floor 3 by the stairs, not yet rested: explore's rest comes first.
        Memory fled = new Memory(10, 3, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1,
                List.of(new Memory.Found("3:0", 0, 1)), List.of());
        Observation hurt = hero(ROOM, 8, 20, Hunger.NONE);
        assertTrue(Explore.restOwed(hurt, fled));
        Brain.Decided rest = brain().decide(hurt, fled.belief());
        assertEquals(Explore.NAME, rest.decision().policy());
        assertEquals("rest: before-descent", rest.decision().chosen().why());

        // Inside a region avoided with a Step farther out: explore's step away comes first.
        Observation inside = corridor(3, 20);
        Memory avoiding = new Memory(10, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1, List.of(),
                List.of(new Memory.Avoid(2, 0, inside.hero().cell() + 1, 2, 500)));
        Brain.Decided away = brain().decide(inside, avoiding.belief());
        assertEquals(Explore.NAME, away.decision().policy(), away.decision().toString());
        assertTrue(away.decision().chosen().why().startsWith("away "), away.decision().chosen().why());
        // With no Step farther out, it does not leave the wait to chance: it goes on to the exit.
        Memory cornered = new Memory(10, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1, List.of(),
                List.of(new Memory.Avoid(2, 0, ROOM.hero().cell() + 2, 2, 500)));
        assertEquals(Descend.NAME, brain().decide(ROOM, cornered.belief()).decision().policy());
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
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, 1, 0, -1, -1, List.of());
        assertEquals(2.0 / 4, Descend.expectedHere(one, found, Screens.CODEX), 1e-9);
        // A potion picked up unidentified may be a potion of strength: counted as found too, and never a
        // scroll of upgrade.
        Memory pending = new Memory(5, 1, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new Memory.Found("crimson potion", 0, 1)), List.of(),
                Memory.Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of());
        Observation holding = hero(one, 20, 20, Hunger.NONE, Screens.item(org.shatterfish.api.ItemKind.POTION, "crimson potion", 1));
        assertEquals((1.0 + 3.0) / 4, Descend.expectedHere(holding, pending, Screens.CODEX), 1e-9);
        // No longer held (drunk, dropped, identified as something else): it stands for nothing.
        assertEquals(5.0 / 4, Descend.expectedHere(one, pending, Screens.CODEX), 1e-9,
                "a find that is not held and unidentified no longer counts");
    }

    /** {@code screen} with the hero showing {@code buffs}. */
    static Observation buffed(Observation screen, String... buffs) {
        HeroSection h = screen.hero();
        List<org.shatterfish.api.BuffView> shown = new ArrayList<>();
        for (String buff : buffs) {
            shown.add(new org.shatterfish.api.BuffView(buff, false, 0));
        }
        HeroSection hero = new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(), h.level(), h.exp(),
                h.expToLevel(), h.hp(), h.ht(), h.shield(), h.strength(), h.strengthBonus(), h.gold(), h.energy(),
                h.hunger(), shown, h.talents(), h.talentPointsAvailable(), h.quickslots());
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), hero, screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    @Test
    @DisplayName("rooted, over many waits through the Brain: never a Step or the stairs, a search a wait so time passes, and on again once the roots fall")
    void rooted() {
        Observation roots = buffed(corridor(3, 20), Explore.ROOTED);
        Observation free = corridor(3, 20);
        Observation[] screens = new Observation[8];
        java.util.Arrays.fill(screens, roots);
        screens[7] = free;
        List<Brain.Decided> all = each(spent(2, 10, 1, 0), screens);
        for (int i = 0; i < 7; i++) {
            Action action = all.get(i).action();
            assertFalse(action instanceof Action.Step || action instanceof Action.Descend, "wait " + i + ": " + action);
            assertEquals(new Action.Search(), action, "wait " + i);
            assertEquals("rooted", all.get(i).decision().chosen().why());
        }
        assertTrue(all.get(7).action() instanceof Action.Step, "the roots fell: on to the exit");
        // Nothing was blocked: a rooted hero's still waits are not refusals.
        Brain brain = brain();
        Belief belief = spent(2, 10, 1, 0).belief();
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            belief = brain.handed(screen, belief, brain.decide(screen, belief));
        }
        assertTrue(Memory.of(belief).blocked().isEmpty() && Memory.of(belief).fleeting().isEmpty());
        // On the exit, rooted: not the stairs either (they are refused too, Hero.java:1442-1445).
        Brain.Decided onExit = brain().decide(buffed(hero(ON, 20, 20, Hunger.NONE), Explore.ROOTED), spent(2, 10, 1, 0).belief());
        assertFalse(onExit.action() instanceof Action.Descend, onExit.decision().toString());
    }

    @Test
    @DisplayName("a still hero under vertigo after a Step is no refusal: the Step may have been spent against a wall")
    void vertigo() {
        Observation dizzy = buffed(corridor(3, 20), Explore.VERTIGO);
        Brain brain = brain();
        Belief belief = spent(2, 10, 1, 0).belief();
        for (int i = 0; i < 4; i++) {
            belief = brain.update(dizzy, belief);
            belief = brain.handed(dizzy, belief, brain.decide(dizzy, belief));
        }
        assertEquals(0, Memory.of(belief).streak());
        assertTrue(Memory.of(belief).blocked().isEmpty());
    }

    @Test
    @DisplayName("the refusal count: rooted is no refusal, a blocked cell starts the count again, and lapsed fight blocks are forgotten")
    void refusal_bookkeeping() {
        Observation room = corridor(3, 20);
        int x = room.hero().cell() + 1;
        // A Step handed over, then the screen shows the hero rooted where it stood: no refusal.
        Memory stepped = Memory.of(Beliefs.fold(spent(2, 10, 1, 0), room, Screens.CODEX).belief()).handed(Beliefs.STEP, x);
        Memory rooted = Beliefs.fold(stepped, buffed(room, Explore.ROOTED), Screens.CODEX);
        assertEquals(0, rooted.streak());
        assertEquals(-1, rooted.tried());
        Memory once = Beliefs.fold(stepped, room, Screens.CODEX);
        assertEquals(1, once.streak(), "not rooted: a refusal");
        assertEquals(x, once.tried());

        // Refused twice at x: blocked, and the count starts again at the next refusal of x.
        Memory twice = Beliefs.fold(once.handed(Beliefs.STEP, x), room, Screens.CODEX);
        assertTrue(twice.blocked().contains(new Memory.Spot(2, 0, x)));
        assertEquals(-1, twice.tried());
        Memory again = Beliefs.fold(twice.handed(Beliefs.STEP, x), room, Screens.CODEX);
        assertEquals(1, again.streak(), "a fresh count after the block");

        // A fight block past its wait is forgotten.
        Memory stale = new Memory(10, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, 1, 0, -1, -1,
                List.of(new Memory.Cloud(2, 0, x, 5)));
        assertTrue(Beliefs.fold(stale, room, Screens.CODEX).fleeting().isEmpty());
    }

    @Test
    @DisplayName("a block recorded in a fight lapses after FLEETING_WAITS waits; one recorded on a calm screen does not")
    void fleeting_blocks() {
        Observation room = corridor(3, 20);
        int cell = room.hero().cell() + 1;
        Memory held = new Memory(5, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, 1, 0, -1, -1,
                List.of(new Memory.Cloud(2, 0, cell, 50)));
        assertFalse(Explore.walkable(room, held)[cell], "blocked until wait 50");
        Memory later = new Memory(51, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, List.of(), List.of(), "", 0, -1, -1, List.of(), List.of(), "", List.of(),
                Memory.Pack.NONE, Memory.Aim.NONE, -1, Memory.Trial.NONE, List.of(), 0, -1, List.of(), -1, 1, 0, -1, -1,
                List.of(new Memory.Cloud(2, 0, cell, 50)));
        assertTrue(Explore.walkable(room, later)[cell], "lapsed");
        assertEquals(later, Memory.of(later.belief()), "the Belief carries the fleeting blocks");
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
