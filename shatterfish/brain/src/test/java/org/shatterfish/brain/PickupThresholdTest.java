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
    @DisplayName("a heap the game would not let the hero take is refused on that floor: no pick-up handed over forever")
    void refused_heap() {
        Observation on = Screens.floor(2, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "ration of food", 0, "")), List.of(),
                new Action.PickUp(), new Action.Step(2));
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        org.shatterfish.api.Belief first = brain.update(on, null);
        assertEquals("pick-up", brain.decide(on, first).decision().policy(), "the first time, it tries");
        org.shatterfish.api.Belief second = brain.update(on, first);
        Memory memory = Memory.of(second);
        assertTrue(memory.refuses(2, 0, 3, "ration of food"), memory.refused().toString());
        assertFalse(PICKUP.enters(on, memory), "still there under the same title: refused, not tried again");
        assertFalse("pick-up".equals(brain.decide(on, second).decision().policy()));
        assertEquals(memory, Memory.of(memory.belief()), "the refusal survives the Belief's bytes");

        Observation other = Screens.floor(3, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "ration of food", 0, "")), List.of(),
                new Action.PickUp(), new Action.Step(2));
        assertTrue(PICKUP.enters(other, memory), "on another floor the same cell and title are another heap");
        Observation lighter = Screens.floor(2, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "ration of food x2", 0, "")), List.of(),
                new Action.PickUp(), new Action.Step(2));
        assertTrue(PICKUP.enters(lighter, memory), "a heap showing another title is not the one refused");

        Observation passing = Screens.floor(2, Screens.heroAt(2, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "ration of food", 0, "")), List.of(), STEPS);
        assertTrue(Memory.of(brain.update(on, brain.update(passing, null))).refused().isEmpty(),
                "arriving on a heap is not standing on it twice");
    }

    @Test
    @DisplayName("a dewdrop at full health is not gone for; hurt, it is")
    void dewdrop() {
        List<HeapView> dew = List.of(new HeapView(3, HeapKind.HEAP, false, "dewdrop", 0, ""));
        assertNull(choose(Screens.floor(1, Screens.heroAt(1, 10, 20, 20), Collections.nCopies(6, Tile.EMPTY), dew,
                List.of(), STEPS)));
        assertEquals(new Action.Step(2), choose(Screens.floor(1, Screens.heroAt(1, 10, 12, 20),
                Collections.nCopies(6, Tile.EMPTY), dew, List.of(), STEPS)).action());
    }

    @Test
    @DisplayName("not a heap a character stands on, never a Step away from the heap, and it yields when stuck")
    void no_wandering() {
        Observation ally = Screens.world(1, Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "potion of strength", 0, "")),
                List.of(new org.shatterfish.api.ActorView(3, "sheep", org.shatterfish.api.Alignment.NEUTRAL, 1, false,
                        org.shatterfish.api.Emote.NONE, List.of())), List.of(), List.of());
        assertFalse(PICKUP.enters(ally, Memory.START), "the screen offers an Interact onto that cell, not a Step");

        assertNull(choose(room(12, 5, "potion of strength", new Action.Step(0))),
                "the one Step offered leads away: no Step at all rather than a sideways one");

        Observation near = room(12, 3, "potion of strength", STEPS);
        Memory stuck = new Memory(3, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 1), Explore.STUCK - 1, true, List.of(), List.of());
        assertNull(PICKUP.choose(near, stuck, near.actions().actions(), Stream.at(1, 1)), "it yields when the hero has stood still as long as explore does");
        Memory longer = new Memory(5, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Memory.Spot(1, 0, 1), Explore.STUCK + 2, true, List.of(), List.of());
        assertNull(PICKUP.choose(near, longer, near.actions().actions(), Stream.at(1, 1)),
                "and for as long as the hero stays put, not one wait only");
        assertEquals(new Action.Step(2), choose(near).action());
        Observation on = Screens.floor(1, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY),
                List.of(new HeapView(3, HeapKind.HEAP, false, "potion of strength", 0, "")), List.of(),
                new Action.PickUp(), new Action.Step(2));
        assertEquals(new Action.PickUp(), PICKUP.choose(on, longer, on.actions().actions(), Stream.at(1, 1)).action(),
                "standing on the heap it goes for, the pick-up is still taken");
    }

    @Test
    @DisplayName("a Step the game keeps refusing: pick-up yields from then on, and its own Step's cell is blocked")
    void refused_step() {
        // The game refuses the Step to cell 2 (a forge drawn as floor, say): the hero stays on 1.
        Observation screen = room(12, 3, "potion of strength", STEPS);
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        org.shatterfish.api.Belief belief = null;
        List<String> policies = new java.util.ArrayList<>();
        for (int wait = 0; wait < 6; wait++) {
            belief = brain.update(screen, belief);
            policies.add(brain.decide(screen, belief).decision().policy());
        }
        assertEquals(List.of("pick-up", "pick-up"), policies.subList(0, 2), policies.toString());
        assertTrue(policies.subList(2, 6).stream().noneMatch("pick-up"::equals), policies.toString());
        assertTrue(Memory.of(belief).blocked().contains(new Memory.Spot(1, 0, 2)), Memory.of(belief).blocked().toString());
        assertEquals(new Memory.Aim(3, 2), Memory.of(brain.update(screen, null)).aim(), "the aim is recorded");
    }

    @Test
    @DisplayName("no refusal when the pack changed (a heap of like items) or when pick-up was not going for that heap")
    void refusal_false_positives() {
        List<HeapView> heap = List.of(new HeapView(3, HeapKind.HEAP, false, "ration of food", 0, ""));
        Observation before = Screens.floor(2, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY), heap,
                List.of(), new Action.PickUp(), new Action.Step(2));
        Observation after = Screens.floor(2, Screens.heroAt(3, 10), Collections.nCopies(6, Tile.EMPTY), heap,
                List.of(Screens.gear(org.shatterfish.api.ItemKind.FOOD, "ration of food",
                        org.shatterfish.api.EquipSlot.NONE, true, false)), new Action.PickUp(), new Action.Step(2));
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        org.shatterfish.api.Belief first = brain.update(before, null);
        assertTrue(Memory.of(brain.update(after, first)).refused().isEmpty(),
                "the next ration shows the same title, but the pack grew: taken, not refused");
        assertEquals(1, Memory.of(brain.update(before, first)).refused().size(), "unchanged: refused");

        Memory elsewhere = Memory.of(first).aiming(Memory.Aim.NONE);
        assertTrue(Beliefs.fold(elsewhere, before, Screens.CODEX).refused().isEmpty(),
                "a heap stood on that pick-up was not going for was never tried");
        Memory otherTarget = Memory.of(first).aiming(new Memory.Aim(5, 4));
        assertTrue(Beliefs.fold(otherTarget, before, Screens.CODEX).refused().isEmpty());
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
