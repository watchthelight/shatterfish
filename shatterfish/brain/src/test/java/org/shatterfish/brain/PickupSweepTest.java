package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.MapSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #182: a seen item is not left on a floor the hero is about to leave because the walk to it
 * outweighs its worth. While the floor still has somewhere to explore the weighed worth decides, as
 * ever; once the floor is spent, the walk is the last thing it asks.
 */
class PickupSweepTest {

    private static final Pickup PICKUP = new Pickup(new Evaluation(Screens.WEIGHTS), Screens.CODEX);
    private static final Action[] STEPS = {new Action.Step(0), new Action.Step(2)};

    /**
     * A corridor of 30 cells with a wall row under it, the hero on cell 1, one heap, an exit on
     * {@code exit} (-1: none seen), and, when {@code frontier}, a cell in the wall row below cell 27
     * never seen, so that the corridor has ground to explore.
     */
    private static Observation row(int heapCell, String item, int exit, boolean frontier) {
        int width = 30;
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(width, Tile.EMPTY));
        tiles.addAll(Collections.nCopies(width, Tile.WALL));
        List<Fog> fog = new ArrayList<>(Collections.nCopies(2 * width, Fog.VISIBLE));
        if (frontier) {
            tiles.set(width + 27, Tile.NONE);
            fog.set(width + 27, Fog.UNKNOWN);
        }
        List<TransitionView> transitions = List.of();
        if (exit >= 0) {
            tiles.set(exit, Tile.EXIT);
            transitions = List.of(new TransitionView(exit, TransitionKind.REGULAR_EXIT));
        }
        Observation base = Screens.floor(1, Screens.heroAt(1, 10), Collections.nCopies(2 * width, Tile.EMPTY),
                List.of(new HeapView(heapCell, HeapKind.HEAP, false, item, 0, "")), List.of(), STEPS);
        MapSection map = new MapSection(width, 2, tiles, fog, List.of(), base.map().heaps(), List.of(),
                base.map().feeling(), transitions);
        return new Observation(base.header(), map, base.actors(), base.hero(), base.inventory(), base.journal(),
                base.log(), base.actions(), base.prompt());
    }

    private static RunLog.Choice choose(Observation observation) {
        return PICKUP.choose(observation, Memory.START, observation.actions().actions(), Stream.at(1, 1));
    }

    private static Action step(Observation observation) {
        RunLog.Choice choice = choose(observation);
        return choice == null ? null : choice.action();
    }

    @Test
    @DisplayName("a ration nineteen cells away is left while the floor has a frontier, and fetched once it is spent and lies on the way out")
    void far_item_on_the_way_out() {
        assertNull(step(row(20, "ration of food", 28, true)), "with ground left to explore, the weighed worth decides");
        assertEquals(new Action.Step(2), step(row(20, "ration of food", 28, false)),
                "a spent floor leaves nothing seen behind that costs no detour");
        assertTrue(PICKUP.enters(row(20, "ration of food", 28, false), Memory.START));
    }

    @Test
    @DisplayName("the same ration with the exit behind the hero is a detour of thirty-eight Steps: left")
    void far_item_off_the_way() {
        assertNull(step(row(20, "ration of food", 0, false)));
    }

    @Test
    @DisplayName("with no exit seen there is no detour to measure: the weighed worth decides")
    void no_exit_seen() {
        assertNull(step(row(20, "ration of food", -1, false)));
    }

    @Test
    @DisplayName("gold on the way out of a spent floor is still weighed: nothing the Brain does spends it")
    void gold_still_weighed() {
        assertNull(step(row(20, "gold x50", 28, false)));
    }
}
