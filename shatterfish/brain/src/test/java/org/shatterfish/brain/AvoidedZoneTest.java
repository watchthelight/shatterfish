package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regions the fight Policy retreats from (issues #182, #183), played out wait by wait through the Brain
 * in {@link OscillationTest}'s small worlds: a region over the only door to unexplored space is walked
 * through, not searched around; a heap inside one is taken; and a region lapses after
 * {@link Brain#AVOID_WAITS} waits, or at once when the screen shows it clear.
 *
 * <p>Worlds are drawn as text: {@code #} wall, {@code .} floor in view, {@code ~} floor seen before and out
 * of view now, a space a cell never seen.
 */
class AvoidedZoneTest {

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 11L);
    }

    /**
     * A room whose only way on is the door in its bottom wall, at x 5, with never-seen cells beyond. The
     * cells round the door are seen before and out of view now, so a region round it is not seen clear.
     */
    private static final String[] DOOR_ROOM = {
        "##########",
        "#........#",
        "#...~~~..#",
        "#####~####",
        "          ",
    };

    private static final int WIDTH = 10;
    private static final int DOOR = 5 + 3 * WIDTH;

    @Test
    @DisplayName("a region over the only door to unexplored space: the Brain walks through it to the door and makes "
            + "no search on the way, where the plan around the region searched every wall in reach")
    void walks_through_the_door_rather_than_search() {
        Memory.Avoid region = new Memory.Avoid(1, 0, DOOR, 2, 500);
        Belief start = Memory.START.avoiding(region).belief();
        IntFunction<Observation> world = hero -> OscillationTest.screen(DOOR_ROOM, hero, 20, List.of(), List.of());
        OscillationTest.Played played = OscillationTest.play(brain(), world, 1 + WIDTH, start, 10);
        int reached = played.cells().indexOf(DOOR);
        assertTrue(reached >= 0, "the hero reaches the door: " + played.cells() + " " + played.decisions());
        assertFalse(played.actions().subList(0, reached + 1).contains(new Action.Search()),
                "no search before the door: " + played.decisions());
        assertTrue(played.decisions().get(0).why().startsWith("frontier "), played.decisions().get(0).toString());
    }

    @Test
    @DisplayName("the plan around a region comes first while it reaches a frontier; the frontier through it only when "
            + "none is left around it, and a search only after both")
    void frontier_order() {
        Memory memory = Memory.START.avoiding(new Memory.Avoid(1, 0, DOOR, 2, 500));
        Observation screen = OscillationTest.screen(DOOR_ROOM, 1 + WIDTH, 20, List.of(), List.of());
        List<Action> offered = screen.actions().actions();
        assertTrue(Explore.frontier(screen, memory), "a frontier is reachable through the region");
        assertEquals("frontier 4", Explore.uncover(screen, memory, offered, Explore.SEARCHES, false).why(),
                "through the region: the door is four Steps away");
        assertFalse(Explore.spent(screen, memory));
    }

    /** A room seen whole, the heap's corner of it out of view now. */
    private static final String[] ROOM = {
        "##########",
        "#........#",
        "#....~~~~#",
        "#....~~~~#",
        "##########",
    };

    @Test
    @DisplayName("a heap inside a region is walked to through it and taken, and the Step into the region lifts it")
    void pick_up_through_a_region() {
        int heap = 7 + 2 * WIDTH;
        Memory.Avoid region = new Memory.Avoid(1, 0, 8 + 3 * WIDTH, 2, Brain.AVOID_WAITS / 2 - 1);
        Belief start = Memory.START.avoiding(region).belief();
        int[] taken = {0};
        IntFunction<Observation> world = hero -> {
            if (hero == heap) {
                taken[0] = 1;
            }
            List<HeapView> heaps = taken[0] == 0 ? List.of(OscillationTest.ration(heap)) : List.of();
            return OscillationTest.screen(ROOM, hero, 20, List.of(), heaps);
        };
        OscillationTest.Played played = OscillationTest.play(brain(), world, 2 + WIDTH, start, 8);
        assertTrue(played.cells().contains(heap), "the hero reaches the heap: " + played.cells() + " "
                + played.decisions());
        assertEquals(Pickup.NAME, played.decisions().get(0).policy(), played.decisions().get(0).toString());
        assertTrue(Memory.of(played.belief()).avoid().isEmpty(), "the region was crossed, and lifted");
    }

    @Test
    @DisplayName("a heap beyond a fresh region is left to the explore Policy until the region has run half its life: "
            + "walking back into a fresh one meets the enemy it was set for")
    void pick_up_waits_for_a_fresh_zone() {
        int heap = 7 + 2 * WIDTH;
        Observation screen = OscillationTest.screen(ROOM, 2 + WIDTH, 20, List.of(), List.of(OscillationTest.ration(heap)));
        Brain brain = brain();
        Memory.Avoid fresh = new Memory.Avoid(1, 0, 8 + 3 * WIDTH, 2, 500);
        Brain.Decided decided = brain.decide(screen, brain.update(screen, Memory.START.avoiding(fresh).belief()));
        assertFalse(Pickup.NAME.equals(decided.decision().policy()), decided.decision().toString());
        Memory.Avoid aged = new Memory.Avoid(1, 0, 8 + 3 * WIDTH, 2, Brain.AVOID_WAITS / 2 - 1);
        Brain.Decided later = brain.decide(screen, brain.update(screen, Memory.START.avoiding(aged).belief()));
        assertEquals(Pickup.NAME, later.decision().policy(), later.decision().toString());
    }

    @Test
    @DisplayName("a retreat's zone lasts 40 waits and reaches at most 5 cells, the numbers the measurement chose")
    void zone_size() {
        assertEquals(40, Brain.AVOID_WAITS);
        assertEquals(5, Brain.AVOID_RADIUS);
    }

    @Test
    @DisplayName("a region lapses after AVOID_WAITS waits out of view, and not before")
    void region_lapses() {
        Memory memory = Memory.START.avoiding(new Memory.Avoid(1, 0, DOOR, 2, Brain.AVOID_WAITS));
        Observation screen = OscillationTest.screen(DOOR_ROOM, 1 + WIDTH, 20, List.of(), List.of());
        for (int i = 0; i < Brain.AVOID_WAITS; i++) {
            memory = Beliefs.fold(memory, screen, FightPolicyTest.KNOWLEDGE);
        }
        assertEquals(1, memory.avoided(1, 0, memory.waits()).size(), "kept through wait AVOID_WAITS");
        memory = Beliefs.fold(memory, screen, FightPolicyTest.KNOWLEDGE);
        assertTrue(memory.avoid().isEmpty(), "gone the wait after");
    }

    @Test
    @DisplayName("a region is lifted once the screen shows where the enemy stood with no enemy inside it; kept while "
            + "its centre is out of view, or an enemy stands inside it, or it is another floor's")
    void region_seen_clear() {
        String[] rows = {
            "##########",
            "#........#",
            "#....~~~~#",
            "##########",
        };
        int inView = 3 + WIDTH;
        int outOfView = 7 + 2 * WIDTH;
        Observation calm = OscillationTest.screen(rows, 1 + WIDTH, 20, List.of(), List.of());
        Memory.Avoid seen = new Memory.Avoid(1, 0, inView, 2, 500);
        Memory.Avoid unseen = new Memory.Avoid(1, 0, outOfView, 2, 500);
        Memory.Avoid elsewhere = new Memory.Avoid(2, 0, inView, 2, 500);
        Memory memory = Memory.START.avoiding(seen).avoiding(unseen).avoiding(elsewhere);
        assertEquals(List.of(unseen, elsewhere), Beliefs.fold(memory, calm, FightPolicyTest.KNOWLEDGE).avoid(),
                "the region whose centre is in view with no enemy in it is lifted");
        Observation rat = OscillationTest.screen(rows, 1 + WIDTH, 20, List.of(OscillationTest.rat(4 + WIDTH)), List.of());
        assertEquals(List.of(seen, unseen, elsewhere), Beliefs.fold(memory, rat, FightPolicyTest.KNOWLEDGE).avoid(),
                "an enemy inside the region keeps it");
        Observation far = OscillationTest.screen(rows, 1 + WIDTH, 20, List.of(OscillationTest.rat(8 + WIDTH)), List.of());
        assertEquals(List.of(unseen, elsewhere), Beliefs.fold(memory, far, FightPolicyTest.KNOWLEDGE).avoid(),
                "an enemy in view outside it does not");
    }

    /** Two doors to unexplored space: the near one inside a region, the far one clear of it. */
    private static final String[] TWO_DOORS = {
        "###########",
        "#.........#",
        "#.~~~.....#",
        "###~#####.#",
        "           ",
    };

    @Test
    @DisplayName("a frontier clear of the region is taken before a nearer one through it")
    void around_before_through() {
        Memory memory = Memory.START.avoiding(new Memory.Avoid(1, 0, 3 + 3 * 11, 1, 500));
        Observation screen = OscillationTest.screen(TWO_DOORS, 1 + 11, 20, List.of(), List.of());
        assertEquals("frontier 8", Explore.uncover(screen, memory, screen.actions().actions(), Explore.SEARCHES, false).why(),
                "the far door, eight Steps away, not the near one two Steps away");
    }

    /** A closed corridor with no unexplored space: the only wall nobody has searched lies inside the region. */
    private static final String[] DEAD_END = {
        "##########",
        "#........#",
        "##########",
    };

    @Test
    @DisplayName("with no frontier and every search spot clear of the region used up, the search goes through the region")
    void searches_through_when_nothing_else() {
        Memory memory = DescendPolicyTest.at(List.of(new Memory.Spot(1, 0, 1 + 10), new Memory.Spot(1, 0, 3 + 10)),
                0, 0, 0).avoiding(new Memory.Avoid(1, 0, 7 + 10, 3, 500));
        Observation screen = OscillationTest.screen(DEAD_END, 1 + 10, 20, List.of(), List.of());
        RunLog.Choice choice = Explore.uncover(screen, memory, screen.actions().actions(), Explore.SEARCHES, false);
        assertTrue(choice != null && choice.why().startsWith("search-spot "), String.valueOf(choice));
    }
}
