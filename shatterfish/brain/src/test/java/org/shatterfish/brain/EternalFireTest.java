package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The magical fire room's flames (issue #219, step 6). Its EternalFire blob sets alight a character
 * standing on a flame or on any of the four cells beside one (MagicalFireRoom.java:211-216), is
 * put out by nothing the hero carries, and guards the room's prizes, which lie behind it. The
 * screen draws the flames, so the Brain treats those cells, and the four beside each, as fire it does
 * not walk into, rest beside or test beside, however much the item behind it is worth.
 */
class EternalFireTest {

    private static final int CELLS = 12;

    private static final int FIRE = 5;

    private static final int HEAP = 8;

    /** A one-row floor of {@code CELLS} cells, flames on {@code fire}, a potion of strength on {@code HEAP}, the hero on {@code hero}. */
    private static Observation room(int hero, List<Integer> fire, boolean fireInView) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(CELLS, Tile.EMPTY));
        List<BlobCell> blobs = new ArrayList<>();
        for (int cell : fire) {
            blobs.add(new BlobCell(cell, List.of("EternalFire")));
        }
        List<Action> steps = new ArrayList<>();
        if (hero > 0) {
            steps.add(new Action.Step(hero - 1));
        }
        if (hero < CELLS - 1) {
            steps.add(new Action.Step(hero + 1));
        }
        Observation lab = Screens.lab(1, CELLS, tiles, fireInView ? blobs : List.of(), Screens.heroAt(hero, 20, 20, List.of()),
                List.of(), List.of(), List.of(), steps.toArray(Action[]::new));
        MapSection map = lab.map();
        List<Fog> fog = new ArrayList<>(map.fog());
        if (!fireInView) {
            for (int cell = FIRE - 1; cell <= FIRE + 1; cell++) {
                fog.set(cell, Fog.VISITED);
            }
        }
        MapSection withHeap = new MapSection(map.width(), map.height(), map.tiles(), fog, map.traps(),
                List.of(new HeapView(HEAP, HeapKind.HEAP, false, "potion of strength", 0, "")), map.blobs(),
                map.feeling(), map.transitions());
        return new Observation(lab.header(), withHeap, lab.actors(), lab.hero(), lab.inventory(), lab.journal(),
                lab.log(), lab.actions(), lab.prompt());
    }

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    @Test
    @DisplayName("a potion of strength behind the flames is not fetched: no Policy walks to the fire or beside it")
    void the_prize_behind_the_flames_is_left() {
        Brain brain = brain();
        Observation screen = room(1, List.of(FIRE), true);
        Brain.Decided decided = brain.decide(screen, brain.update(screen, null));
        assertNotEquals("pick-up", decided.decision().policy(), "the only way to it is through the flames");
        assertFalse(decided.action() instanceof Action.Step step && step.cell() >= FIRE - 1,
                "no Step onto the flame or the cell beside it: " + decided.action());
    }

    @Test
    @DisplayName("the cells beside a flame are walled off too, and stay so when the flames are out of view")
    void the_cells_beside_are_remembered() {
        Brain brain = brain();
        Observation seen = room(2, List.of(FIRE), true);
        Belief belief = brain.update(seen, null);
        Memory memory = Memory.of(belief);
        for (int cell = FIRE - 1; cell <= FIRE + 1; cell++) {
            final int c = cell;
            assertTrue(memory.clouds().stream().anyMatch(cloud -> cloud.cell() == c), "cell " + c + " is kept out of");
        }
        assertFalse(memory.clouds().stream().anyMatch(cloud -> cloud.cell() == FIRE - 2), "two cells off is safe");

        // A thousand waits later, the hero back at the room's mouth with the flames out of view.
        Observation unseen = room(2, List.of(FIRE), false);
        Memory later = Memory.of(brain.update(unseen, belief));
        assertTrue(later.clouded(1, 0, FIRE, later.waits() + 900), "the flames do not burn out, so the memory does not lapse");
        boolean[] walk = Explore.walkable(unseen, later);
        assertFalse(walk[FIRE - 1] || walk[FIRE] || walk[FIRE + 1]);
        assertTrue(walk[FIRE - 2]);
    }

    @Test
    @DisplayName("a rest or a test is not taken beside the flames")
    void no_rest_beside_the_flames() {
        Observation beside = room(FIRE - 1, List.of(FIRE), true);
        assertTrue(TestItem.inHarm(beside), "the cell beside a flame ignites whoever stands on it");
        assertEquals("EternalFire", TestItem.harm(beside));
        assertFalse(TestItem.inHarm(room(FIRE - 2, List.of(FIRE), true)));
    }
}
