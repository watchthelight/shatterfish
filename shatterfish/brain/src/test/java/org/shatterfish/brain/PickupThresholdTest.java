package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pick-up Policy (story 4.8): an item is taken when what it is worth carried exceeds what the
 * turns to reach and take it cost, under the committed weights.
 */
class PickupThresholdTest {

    private static final Pickup PICKUP = new Pickup(new Evaluation(Screens.WEIGHTS), Screens.CODEX);

    /** A room {@code cells} long, the hero on cell 1, one heap. */
    private static Observation room(int cells, int heapCell, String item, Action... offered) {
        return Screens.floor(1, Screens.heroAt(1, 10), Collections.nCopies(cells, Tile.EMPTY),
                List.of(new HeapView(heapCell, HeapKind.HEAP, false, item, 0, "")), List.of(), offered);
    }

    private static RunLog.Choice choose(Observation observation) {
        return PICKUP.choose(observation, Memory.START, observation.actions().actions(), Stream.at(1, 1));
    }

    private static final Action[] STEPS = {new Action.Step(0), new Action.Step(2)};

    @Test
    @DisplayName("a potion of strength two cells away is taken; a single gold piece across an explored room is not")
    void the_ordering() {
        RunLog.Choice strength = choose(room(12, 3, "potion of strength", STEPS));
        assertEquals(new Action.Step(2), strength.action());
        assertEquals("item: potion of strength 2", strength.why());

        assertNull(choose(room(12, 11, "gold", STEPS)), "one gold piece ten cells away is not worth eleven turns");

        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        Observation gold = room(12, 11, "gold", STEPS);
        assertFalse("pick-up".equals(brain.decide(gold, brain.update(gold, null)).decision().policy()));
        Observation potion = room(12, 3, "potion of strength", STEPS);
        assertEquals("pick-up", brain.decide(potion, brain.update(potion, null)).decision().policy(),
                "and the Brain arbitrates it above exploring");
    }

    @Test
    @DisplayName("gold is worth its pieces: a stack of a hundred two cells away is taken, forty is not")
    void gold_by_the_piece() {
        assertEquals(new Action.Step(2), choose(room(12, 3, "gold x100", STEPS)).action());
        assertNull(choose(room(12, 3, "gold x40", STEPS)));
        assertEquals(100, Pickup.quantity("gold x100"));
        assertEquals(1, Pickup.quantity("gold"));
        assertEquals(1, Pickup.quantity("ring of might +2"));
    }

    @Test
    @DisplayName("an unidentified potion is worth what the Beliefs say it may be: more than a plain item, less than known strength")
    void believed_worth() {
        Observation screen = room(30, 20, "crimson potion", STEPS);
        long unknown = PICKUP.worth("crimson potion", screen);
        long strength = PICKUP.worth("potion of strength", screen);
        long plain = PICKUP.worth("ration of food", screen);
        assertTrue(plain < unknown && unknown < strength, plain + " < " + unknown + " < " + strength);
        // Healing and strength weigh 6 of 19 each here; strength adds the strength weight.
        assertEquals(2000 + Math.round(2000.0 * 6 / 19), unknown);
        assertEquals(new Action.Step(2), choose(room(30, 11, "crimson potion", STEPS)).action(), "ten cells: taken");
        assertNull(choose(room(30, 20, "crimson potion", STEPS)), "nineteen cells: not");
    }

    @Test
    @DisplayName("standing on the heap, it picks it up; with an enemy in view, or on a chest, it does not enter")
    void underfoot_and_not() {
        Observation on = Screens.floor(1, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "potion of strength", 0, "")), List.of(),
                new Action.PickUp(), new Action.Step(2));
        RunLog.Choice take = choose(on);
        assertEquals(new Action.PickUp(), take.action());
        assertEquals("take: potion of strength", take.why());

        Observation enemy = Screens.world(1, Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "potion of strength", 0, "")),
                List.of(Screens.enemy("rat", 5)), List.of(), List.of());
        assertFalse(PICKUP.enters(enemy, Memory.START));
        Observation chest = Screens.floor(1, Screens.heroAt(1, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.CHEST, false, "", 0, "")), List.of(), STEPS);
        assertFalse(PICKUP.enters(chest, Memory.START));
    }

    @Test
    @DisplayName("an item no walkable path reaches is not planned for")
    void unreachable() {
        List<Tile> walled = new java.util.ArrayList<>(Collections.nCopies(12, Tile.EMPTY));
        walled.set(5, Tile.WALL);
        assertNull(choose(Screens.floor(1, Screens.heroAt(1, 10), walled,
                List.of(new HeapView(8, HeapKind.HEAP, false, "potion of strength", 0, "")), List.of(), STEPS)));
    }
}
