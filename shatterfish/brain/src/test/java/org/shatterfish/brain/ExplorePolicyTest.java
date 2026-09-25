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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explore Policy (story 4.6, FR-31): toward the nearest frontier one Step per wait, around
 * the cells where a click is not a step, searching where a search reaches walls no search has
 * covered, a bounded number of spots, then the way down; yielding to an enemy in view, and once to a
 * Step the game keeps refusing, which it then goes round.
 *
 * <p>Screens are drawn as text: {@code #} wall, {@code .} floor, a space a cell never seen,
 * {@code @} the hero on floor, {@code E} the hero on the exit, {@code >} the exit, {@code C} a
 * chest, {@code h} a plain heap, {@code ^} an armed trap, {@code t} a disarmed one, {@code r} an
 * enemy rat, {@code a} an ally, {@code c} a chasm, {@code W} a well, {@code w} water. The offered
 * Actions are the ones {@code ValidActions} computes for the screen, as the Observer attaches them.
 */
class ExplorePolicyTest {

    static Observation screen(int depth, String... rows) {
        return screen(depth, false, HeroClass.WARRIOR, rows);
    }

    /** A screen from rows of text, at {@code depth}, sealed or not, for a hero of {@code heroClass}. */
    static Observation screen(int depth, boolean sealed, HeroClass heroClass, String... rows) {
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
                    case '>', 'E' -> Tile.EXIT;
                    case 'c' -> Tile.CHASM;
                    case 'W' -> Tile.WELL;
                    case 'w' -> Tile.WATER;
                    default -> Tile.EMPTY;
                };
                tiles.add(tile);
                fog.add(c == ' ' ? Fog.UNKNOWN : Fog.VISIBLE);
                switch (c) {
                    case '@' -> hero = cell;
                    case 'E' -> {
                        hero = cell;
                        transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    }
                    case '>' -> transitions.add(new TransitionView(cell, TransitionKind.REGULAR_EXIT));
                    case 'C' -> heaps.add(new HeapView(cell, HeapKind.CHEST, false, "", 0, ""));
                    case 'h' -> heaps.add(new HeapView(cell, HeapKind.HEAP, false, "gold", 0, ""));
                    case '^' -> traps.add(new TrapView(cell, "worn dart trap", true));
                    case 't' -> traps.add(new TrapView(cell, "worn dart trap", false));
                    case 'r' -> actors.add(new ActorView(cell, "rat", Alignment.ENEMY, 3, false, Emote.NONE, List.of()));
                    case 'a' -> actors.add(new ActorView(cell, "sheep", Alignment.ALLY, 3, false, Emote.NONE, List.of()));
                    default -> {
                    }
                }
            }
        }
        HeaderSection header = new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", heroClass,
                List.of(), depth, 0, sealed, false, PromptKind.NONE);
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

    /** The Belief after showing {@code screens} in order. */
    private static Belief seen(Observation... screens) {
        Brain brain = brain();
        Belief belief = null;
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
        }
        return belief;
    }

    /** The Decision after showing {@code screens} in order, at the last. */
    private static Brain.Decided after(Observation... screens) {
        return brain().decide(screens[screens.length - 1], seen(screens));
    }

    /** A memory whose searched spots are {@code dwelt}, and nothing else. */
    private static Memory searched(List<Memory.Spot> dwelt) {
        return new Memory(1, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of());
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

        Observation fork = screen(1,
                "###########",
                "  ..@.... #",
                "###########");
        assertEquals(new Action.Step(cell(fork, 3, 1)), after(fork).action());
        assertEquals("frontier 2", after(fork).decision().chosen().why());
    }

    @Test
    @DisplayName("the cells it may walk on: not the stairs, a chest, an armed trap, a chasm, a well or an enemy; a plain heap, a disarmed trap and an ally, yes")
    void what_it_walks_on() {
        Observation row = screen(1,
                "##############",
                "#@.>C^cWhtar.#",
                "##############");
        boolean[] walk = Explore.walkable(row, Memory.START);
        String expected = "..-----+++-.";
        for (int x = 1; x <= 12; x++) {
            char at = expected.charAt(x - 1);
            if (at != '.') {
                assertEquals(at == '+', walk[cell(row, x, 1)], "column " + x + " (" + row.map().tiles().get(cell(row, x, 1)) + ")");
            }
        }
    }

    @Test
    @DisplayName("it goes round what it may not walk on, by a path it chooses the same way every time")
    void detour() {
        Observation detour = screen(1,
                "#######",
                "#@.>.  ",
                "#....##",
                "#######");
        Brain.Decided decided = after(detour);
        assertEquals(new Action.Step(cell(detour, 2, 1)), decided.action());
        assertEquals("frontier 3", decided.decision().chosen().why(), "round the stairs through the row below");
    }

    @Test
    @DisplayName("an enemy in view leaves the wait to the fight Policy")
    void yields_to_an_enemy() {
        Observation rat = screen(1,
                "#########",
                "#@...r  #",
                "#########");
        assertEquals(Fight.NAME, after(rat).decision().policy());
    }

    @Test
    @DisplayName("an exhausted floor is searched where a search reaches uncovered walls, a spot at a time")
    void search() {
        Observation room = screen(2,
                "#####",
                "#@..#",
                "#...#",
                "#####");
        Brain.Decided first = after(room);
        assertEquals(new Action.Search(), first.action(), "the hero stands beside walls no search has covered");
        assertEquals("search 1/" + Explore.SEARCHES, first.decision().chosen().why());

        // The next wait sees the hero still there after a calm screen: that spot is searched.
        Brain.Decided second = after(room, room);
        assertEquals(Explore.NAME, second.decision().policy());
        assertTrue(second.action() instanceof Action.Step, second.action().toString());
        assertTrue(second.decision().chosen().why().startsWith("search-spot "), second.decision().chosen().why());
        assertEquals(List.of(new Memory.Spot(2, 0, room.hero().cell())), Memory.of(seen(room, room)).dwelt());
    }

    @Test
    @DisplayName("standing still beside an enemy or at a Prompt is no search")
    void a_pause_is_no_search() {
        Observation room = screen(2,
                "######",
                "#@..r#",
                "######");
        assertEquals(List.of(), Memory.of(seen(room, room, room)).dwelt());
    }

    @Test
    @DisplayName("a spot whose walls are all within reach of searches already made is not worth searching")
    void covered_walls() {
        Observation hall = screen(1,
                "#########",
                "#@......#",
                "#########");
        boolean[] walk = Explore.walkable(hall, Memory.START);
        List<Memory.Spot> twoAround = List.of(new Memory.Spot(1, 0, cell(hall, 1, 1)), new Memory.Spot(1, 0, cell(hall, 3, 1)));
        assertFalse(Explore.worth(hall.map(), walk, cell(hall, 2, 1), 1, twoAround, false),
                "the walls beside (2,1) are within one of (1,1) or (3,1)");
        assertTrue(Explore.worth(hall.map(), walk, cell(hall, 4, 1), 1, twoAround, false), "(5,*) is not");
        assertFalse(Explore.worth(hall.map(), walk, cell(hall, 2, 1), 2, twoAround, false),
                "for a Rogue, two away, the searches reach x=0..5, all the walls (2,1) reaches");
        assertTrue(Explore.worth(hall.map(), walk, cell(hall, 2, 1), 2, List.of(new Memory.Spot(1, 0, cell(hall, 1, 1))), false));
        assertEquals(2, Explore.radius(screen(1, false, HeroClass.ROGUE, "###", "#@#", "###")));
        assertEquals(1, Explore.radius(hall));

        Observation behind = screen(1,
                "   ######",
                "#########",
                "#@......#",
                "#########");
        boolean[] beside = Explore.walkable(behind, Memory.START);
        assertTrue(Explore.worth(behind.map(), beside, cell(behind, 2, 2), 1, List.of(), true),
                "the wall above (2,2) has unseen cells within two of it");
        assertFalse(Explore.worth(behind.map(), beside, cell(behind, 6, 2), 1, List.of(), true),
                "the walls around (6,2) have none");
        assertTrue(Explore.worth(behind.map(), beside, cell(behind, 6, 2), 1, List.of(), false));
    }

    @Test
    @DisplayName("with the floor's searches spent or covering every wall, it walks to the exit and onto it")
    void down_when_done() {
        Observation room = screen(2,
                "#######",
                "#@..>.#",
                "#######");
        List<Memory.Spot> spent = new ArrayList<>();
        for (int i = 0; i < Explore.SEARCHES; i++) {
            spent.add(new Memory.Spot(2, 0, 100 + i));
        }
        Brain.Decided toward = brain().decide(room, searched(spent).belief());
        assertEquals(Explore.NAME, toward.decision().policy());
        assertEquals(new Action.Step(cell(room, 2, 1)), toward.action());
        assertEquals("exit 3", toward.decision().chosen().why(), "onto the exit is the last Step");

        List<Memory.Spot> everywhere = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            everywhere.add(new Memory.Spot(2, 0, cell(room, x, 1)));
        }
        assertEquals("exit 3", brain().decide(room, searched(everywhere).belief()).decision().chosen().why(),
                "every wall covered: nothing left to search");

        Observation on = screen(2,
                "#######",
                "#..E..#",
                "#######");
        Brain.Decided descend = brain().decide(on, searched(spent).belief());
        assertEquals(new Action.Descend(), descend.action());
        assertEquals("descend", descend.decision().chosen().why());

        Observation sealed = screen(2, true, HeroClass.WARRIOR,
                "#######",
                "#@..>.#",
                "#######");
        assertEquals(Policies.FALLBACK, brain().decide(sealed, searched(spent).belief()).decision().policy(),
                "never down from a sealed floor");
        Observation sealedOn = screen(2, true, HeroClass.WARRIOR,
                "#######",
                "#..E..#",
                "#######");
        assertEquals(Policies.FALLBACK, brain().decide(sealedOn, searched(spent).belief()).decision().policy());

        List<Memory.Spot> elsewhere = spent.stream().map(spot -> new Memory.Spot(3, 0, spot.cell())).toList();
        assertEquals("search 1/" + Explore.SEARCHES,
                brain().decide(room, searched(elsewhere).belief()).decision().chosen().why(),
                "another floor's searches do not count against this one");
        List<Memory.Spot> otherBranch = spent.stream().map(spot -> new Memory.Spot(2, 1, spot.cell())).toList();
        assertEquals("search 1/" + Explore.SEARCHES,
                brain().decide(room, searched(otherBranch).belief()).decision().chosen().why(),
                "nor another branch's at the same depth");
    }

    @Test
    @DisplayName("a Step the game keeps refusing: it yields once, the cell is blocked, and the next plan goes round it")
    void stuck() {
        Observation corridor = screen(1,
                "#########",
                "#@....  #",
                "#.......#",
                "#########");
        int refused = cell(corridor, 2, 1);
        assertEquals(new Action.Step(refused), after(corridor, corridor).action());
        Brain.Decided third = after(corridor, corridor, corridor);
        assertEquals(Policies.FALLBACK, third.decision().policy(), "the third wait on the cell is the fallback's");
        assertEquals(List.of(new Memory.Spot(1, 0, refused)), Memory.of(seen(corridor, corridor, corridor)).blocked());
        Brain.Decided fourth = after(corridor, corridor, corridor, corridor);
        assertEquals(Explore.NAME, fourth.decision().policy(), "and then explore again");
        assertEquals(new Action.Step(cell(corridor, 2, 2)), fourth.action(), "round the refused cell");
    }

    @Test
    @DisplayName("the oldest of more than 256 spots is forgotten")
    void eviction() {
        List<Memory.Spot> spots = List.of();
        for (int i = 0; i <= Memory.DWELT; i++) {
            spots = Memory.with(spots, new Memory.Spot(1, 0, i));
        }
        assertEquals(Memory.DWELT, spots.size());
        assertEquals(new Memory.Spot(1, 0, 1), spots.get(0));
        assertEquals(spots, Memory.with(spots, new Memory.Spot(1, 0, 5)), "a spot already held is not added again");
    }

    @Test
    @DisplayName("of two frontiers equally near, the one the screen's first offered Step leads to, every time")
    void deterministic() {
        Observation fork = screen(1,
                "############",
                "  ....@.... ",
                "############");
        Brain.Decided decided = after(fork);
        assertEquals(new Action.Step(cell(fork, 5, 1)), decided.action());
        assertEquals("frontier 4", decided.decision().chosen().why());
        assertEquals(decided.decision(), after(fork).decision());
        assertNull(Explore.stepCell(screen(1, "###", "#@#", "###"), Memory.START), "no Step in a closed cell");
    }
}
