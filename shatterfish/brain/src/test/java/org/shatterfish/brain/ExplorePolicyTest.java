package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.TrapView;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explore Policy (story 4.6, FR-31): toward the nearest frontier one Step per wait, around
 * the cells where a click is not a step, searching once per spot when the floor is exhausted, a
 * bounded number of spots, and yielding to an enemy in view or a Step the game keeps refusing.
 *
 * <p>Screens are drawn as text: {@code #} wall, {@code .} floor, a space a cell never seen,
 * {@code @} the hero on floor, {@code >} the stairs down, {@code C} a chest, {@code ^} an armed
 * trap, {@code r} an enemy rat, {@code c} a chasm, {@code w} water. The offered Actions are the
 * ones {@code ValidActions} computes for the screen, as the Observer attaches them.
 */
class ExplorePolicyTest {

    /** A screen from rows of text, at {@code depth}. */
    static Observation screen(int depth, String... rows) {
        int width = rows[0].length();
        List<Tile> tiles = new ArrayList<>();
        List<Fog> fog = new ArrayList<>();
        List<HeapView> heaps = new ArrayList<>();
        List<TrapView> traps = new ArrayList<>();
        List<TransitionView> transitions = new ArrayList<>();
        List<ActorView> actors = new ArrayList<>();
        int hero = -1;
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                char c = rows[y].charAt(x);
                int cell = x + y * width;
                Tile tile = switch (c) {
                    case ' ' -> Tile.NONE;
                    case '#' -> Tile.WALL;
                    case '>' -> Tile.EXIT;
                    case 'c' -> Tile.CHASM;
                    case 'w' -> Tile.WATER;
                    default -> Tile.EMPTY;
                };
                tiles.add(tile);
                fog.add(c == ' ' ? Fog.UNKNOWN : Fog.VISIBLE);
                switch (c) {
                    case '@' -> hero = cell;
                    case '>' -> transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    case 'C' -> heaps.add(new HeapView(cell, HeapKind.CHEST, false, "", 0, ""));
                    case '^' -> traps.add(new TrapView(cell, "worn dart trap", true));
                    case 'r' -> actors.add(new ActorView(cell, "rat", Alignment.ENEMY, 3, false, Emote.NONE, List.of()));
                    default -> {
                    }
                }
            }
        }
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.of(), depth, 0, false, false, PromptKind.NONE);
        MapSection map = new MapSection(width, rows.length, tiles, fog, traps, heaps, List.of(), Feeling.NONE,
                transitions);
        HeroSection section = new HeroSection(hero, "", HeroSubclass.NONE, "", 1, 0, 1, 20, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
        Observation bare = new Observation(header, map, new ActorsSection(actors), section, new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), ActionsSection.NONE,
                PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    /** The Decision after showing {@code screens} in order, at the last. */
    private static Brain.Decided after(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
        }
        return brain.decide(screens[screens.length - 1], belief);
    }

    private static int cell(Observation screen, int x, int y) {
        return x + y * screen.map().width();
    }

    @Test
    @DisplayName("it steps toward the nearest frontier through cells it may walk on, one Step at a time")
    void nearest_frontier() {
        Observation corridor = screen(1,
                "#########",
                "#@....  #",
                "#########");
        Brain.Decided decided = after(corridor);
        assertEquals(Explore.NAME, decided.decision().policy());
        assertEquals(new Action.Step(cell(corridor, 2, 1)), decided.action());
        assertEquals("frontier 4", decided.decision().chosen().why(), "the frontier is the floor beside the unseen cells");
        assertTrue(corridor.actions().actions().contains(decided.action()), "an Action the screen offers");

        // Two frontiers: the nearer one wins, whichever side it is on.
        Observation fork = screen(1,
                "###########",
                "  ..@.... #",
                "###########");
        assertEquals(new Action.Step(cell(fork, 3, 1)), after(fork).action());
        assertEquals("frontier 2", after(fork).decision().chosen().why());
    }

    @Test
    @DisplayName("it never walks through the stairs, a chest, an armed trap or a chasm, and goes round when it can")
    void around_what_a_click_would_do() {
        Observation blocked = screen(1,
                "##########",
                "#@.>C^c  #",
                "##########");
        boolean[] walk = Explore.walkable(blocked);
        for (int x = 3; x <= 6; x++) {
            assertFalse(walk[cell(blocked, x, 1)], "column " + x);
        }
        assertTrue(walk[cell(blocked, 2, 1)]);
        // The only way on is through them, so no frontier is reachable and the floor counts as
        // exhausted: the Policy searches beside the wall rather than stepping into any of them.
        Brain.Decided exhausted = after(blocked);
        assertEquals(new Action.Search(), exhausted.action());
        assertTrue(exhausted.decision().chosen().why().startsWith("search "), exhausted.decision().chosen().why());

        Observation detour = screen(1,
                "#######",
                "#@.>.  ",
                "#....##",
                "#######");
        Brain.Decided decided = after(detour);
        assertEquals(Explore.NAME, decided.decision().policy());
        Action step = decided.action();
        assertNotEquals(new Action.Step(cell(detour, 3, 1)), step);
        assertTrue(step.equals(new Action.Step(cell(detour, 2, 1))) || step.equals(new Action.Step(cell(detour, 2, 2)))
                || step.equals(new Action.Step(cell(detour, 1, 2))), step.toString());
    }

    @Test
    @DisplayName("an enemy in view leaves the wait to the Policies below it")
    void yields_to_an_enemy() {
        Observation rat = screen(1,
                "#########",
                "#@...r  #",
                "#########");
        assertEquals(Policies.FALLBACK, after(rat).decision().policy());
    }

    @Test
    @DisplayName("an exhausted floor is searched once per spot beside a wall, then from the next spot, and given up after the bound")
    void search() {
        Observation room = screen(2,
                "#####",
                "#@..#",
                "#...#",
                "#####");
        Brain.Decided first = after(room);
        assertEquals(new Action.Search(), first.action(), "the hero stands beside a wall: search here");
        assertEquals("search 1/" + Explore.SEARCHES, first.decision().chosen().why());

        // The next wait sees the hero still there: that spot is done, so it walks to another.
        Brain.Decided second = after(room, room);
        assertEquals(Explore.NAME, second.decision().policy());
        assertTrue(second.action() instanceof Action.Step, second.action().toString());
        assertTrue(second.decision().chosen().why().startsWith("search-spot "), second.decision().chosen().why());

        // Every spot of the floor dwelt on up to the bound: the floor is given up.
        List<Memory.Spot> dwelt = new ArrayList<>();
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 3; x++) {
                dwelt.add(new Memory.Spot(2, cell(room, x, y)));
            }
        }
        Memory all = new Memory(1, 2, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(2, room.hero().cell()), 0, dwelt);
        assertEquals(Policies.FALLBACK, brain().decide(room, all.belief()).decision().policy(),
                "no spot left to search");

        List<Memory.Spot> bounded = new ArrayList<>();
        Observation hall = screen(3,
                "################",
                "#@.............#",
                "################");
        for (int x = 2; x < 2 + Explore.SEARCHES; x++) {
            bounded.add(new Memory.Spot(3, cell(hall, x, 1)));
        }
        Memory enough = new Memory(1, 3, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, bounded);
        assertEquals(Policies.FALLBACK, brain().decide(hall, enough.belief()).decision().policy(),
                "the bound is reached though spots are left");
        Memory other = new Memory(1, 3, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, bounded.stream().map(spot -> new Memory.Spot(4, spot.cell())).toList());
        assertEquals(Explore.NAME, brain().decide(hall, other.belief()).decision().policy(),
                "another floor's spots do not count against this one");
    }

    @Test
    @DisplayName("a Step the game keeps refusing is given up after three waits on the cell")
    void stuck() {
        Observation corridor = screen(1,
                "#########",
                "#@....  #",
                "#########");
        assertEquals(Explore.NAME, after(corridor, corridor).decision().policy());
        assertEquals(Policies.FALLBACK, after(corridor, corridor, corridor).decision().policy());
    }

    @Test
    @DisplayName("the same screens give the same Decision")
    void deterministic() {
        // Two frontiers at four Steps each: the tie goes the same way every time.
        Observation fork = screen(1,
                "############",
                "  ....@.... ",
                "############");
        RunLog.Decision one = after(fork).decision();
        RunLog.Decision two = after(fork).decision();
        assertEquals(one, two);
    }
}
