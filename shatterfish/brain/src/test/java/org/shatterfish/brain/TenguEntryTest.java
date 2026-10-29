package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.NoteView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tengu's floor before the fight (story 10.18, issue #364), on a constructed hallway: the iron key held
 * and the locked door drawn, the hero walks to the door, rests first when hurt, hands over the Unlock; with
 * no key, a sealed floor or another depth the Policy has nothing to say; and the stairs up are never taken
 * on the floor as a way down. The same floor played in the game, with a real Brain, is
 * {@code TenguEntryRunTest} in the rig.
 *
 * <p>The hallway is a row, as the real one is a column: the hero at its west end, the locked door at the
 * east end, a wall around.
 */
class TenguEntryTest {

    private static final int DEPTH = Tengu.DEPTH;

    /** The door's cell: the east end of the row, which is row 1 of a ten-wide screen. */
    private static final int DOOR = 19;

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    /** The hallway at {@code depth}, the hero at column {@code x}, the door drawn locked, {@code hp} of 20. */
    private static Observation hallway(int depth, boolean sealed, int x, int hp, Hunger hunger, boolean keyHeld) {
        char[] row = "#........#".substring(0, 10).toCharArray();
        row[x] = '@';
        Observation plain = ExplorePolicyTest.screen(depth, sealed, HeroClass.WARRIOR,
                "##########", new String(row), "##########");
        Observation locked = DescendPolicyTest.tile(plain, DOOR, Tile.LOCKED_DOOR);
        Observation hurt = DescendPolicyTest.hero(locked, hp, 20, hunger, ExplorePolicyTest.PACK.toArray(
                new org.shatterfish.api.ItemView[0]));
        return keyHeld ? journal(hurt, List.of(new NoteView(NoteKind.KEY, depth, Tengu.IRON_KEY, "", 1))) : hurt;
    }

    private static Observation journal(Observation screen, List<NoteView> notes) {
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), screen.hero(),
                screen.inventory(), new JournalSection(notes, screen.journal().known()), screen.log(),
                ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    /** {@code screen} with the hero standing on the entrance stairs, which lead up. */
    private static Observation onEntrance(Observation screen) {
        MapSection map = screen.map();
        List<Tile> tiles = new ArrayList<>(map.tiles());
        tiles.set(screen.hero().cell(), Tile.ENTRANCE);
        List<TransitionView> transitions = new ArrayList<>(map.transitions());
        transitions.add(new TransitionView(screen.hero().cell(), TransitionKind.REGULAR_ENTRANCE));
        MapSection drawn = new MapSection(map.width(), map.height(), tiles, map.fog(), map.traps(), map.heaps(),
                map.blobs(), map.feeling(), transitions);
        Observation bare = new Observation(screen.header(), drawn, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, PromptSection.NONE);
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Observation screen) {
        return brain().decide(screen, Beliefs.fold(Memory.START, screen, Screens.CODEX).belief());
    }

    @Test
    @DisplayName("with the key held and the door drawn locked, the hero walks to the door, one Step a wait, and hands over the Unlock")
    void walks_to_the_door_and_unlocks() {
        Brain.Decided far = decide(hallway(DEPTH, false, 1, 20, Hunger.NONE, true));
        assertEquals(Tengu.NAME, far.decision().policy(), far.decision().toString());
        assertEquals(new Action.Step(12), far.action());
        assertEquals("tengu: door 7", far.decision().chosen().why());

        Brain.Decided beside = decide(hallway(DEPTH, false, 8, 20, Hunger.NONE, true));
        assertEquals(Tengu.NAME, beside.decision().policy());
        assertEquals(new Action.Unlock(DOOR), beside.action());
        assertEquals("tengu: unlock", beside.decision().chosen().why());
    }

    @Test
    @DisplayName("hurt, it rests before it opens the door that seals the floor; a rest limit, starvation or a lower share lets it through")
    void rests_before_the_seal() {
        Observation hurt = hallway(DEPTH, false, 8, 10, Hunger.NONE, true);
        Brain.Decided rest = decide(hurt);
        assertEquals(new Action.Rest(true), rest.action(), rest.decision().toString());
        assertEquals("tengu: rest", rest.decision().chosen().why());
        assertEquals(Tengu.NAME, rest.decision().policy());

        Tengu tengu = new Tengu(Screens.CODEX, Playbook.Tengu.REFERENCE);
        List<Action> offered = hurt.actions().actions();
        Memory spent = DescendPolicyTest.arrivedAt(12, 10, 1, Playbook.Tengu.REFERENCE.restLimit());
        assertEquals(new Action.Unlock(DOOR), tengu.choose(hurt, spent, offered, null).action(),
                "the rests are spent: the door is opened hurt rather than rest for ever");
        Observation starving = hallway(DEPTH, false, 8, 10, Hunger.STARVING, true);
        assertEquals(new Action.Unlock(DOOR), tengu.choose(starving, Memory.START, starving.actions().actions(), null).action(),
                "a starving hero regenerates nothing: it does not rest");
        Tengu hasty = new Tengu(Screens.CODEX, Playbook.Tengu.REFERENCE.with(Map.of("enter_per_mille", 500L)));
        assertEquals(new Action.Unlock(DOOR), hasty.choose(hurt, Memory.START, offered, null).action(),
                "half of the hit points is enough for the entry's share of a half");
        Tengu fussy = new Tengu(Screens.CODEX, Playbook.Tengu.REFERENCE);
        Observation nearlyWhole = hallway(DEPTH, false, 8, 19, Hunger.NONE, true);
        assertEquals("tengu: rest", fussy.choose(nearlyWhole, Memory.START, nearlyWhole.actions().actions(), null).why(),
                "the reference rests to the last hit point");
    }

    @Test
    @DisplayName("the rest is counted: a rest handed over by this Policy is a rest on the floor, as the descend Policy's is")
    void the_rest_is_counted() {
        Brain brain = brain();
        Observation hurt = hallway(DEPTH, false, 8, 10, Hunger.NONE, true);
        org.shatterfish.api.Belief belief = brain.update(hurt, null);
        assertEquals(0, Memory.of(belief).rests());
        Brain.Decided decided = brain.decide(hurt, belief);
        assertEquals(new Action.Rest(true), decided.action());
        assertEquals(1, Memory.of(brain.handed(hurt, belief, decided)).rests());
    }

    @Test
    @DisplayName("no key held: the Policy says nothing, so the pick-up and explore Policies look for it; and nothing opens a locked door by hope")
    void without_the_key() {
        Observation noKey = hallway(DEPTH, false, 8, 20, Hunger.NONE, false);
        Tengu tengu = new Tengu(Screens.CODEX, Playbook.Tengu.REFERENCE);
        assertNull(tengu.choose(noKey, Memory.START, noKey.actions().actions(), null));
        assertNotEquals(Tengu.NAME, decide(noKey).decision().policy());
        // A key for another floor is not this floor's key.
        Observation other = journal(noKey, List.of(new NoteView(NoteKind.KEY, DEPTH - 1, Tengu.IRON_KEY, "", 1)));
        assertNull(tengu.choose(other, Memory.START, other.actions().actions(), null));
    }

    @Test
    @DisplayName("sealed, or on another floor, the Policy is not entered; the door in front of the hero is the fight's business or the floor's")
    void only_on_the_unsealed_boss_floor() {
        Tengu tengu = new Tengu(Screens.CODEX, Playbook.Tengu.REFERENCE);
        assertTrue(tengu.enters(hallway(DEPTH, false, 8, 20, Hunger.NONE, true), Memory.START));
        assertFalse(tengu.enters(hallway(DEPTH, true, 8, 20, Hunger.NONE, true), Memory.START), "sealed");
        assertFalse(tengu.enters(hallway(DEPTH - 1, false, 8, 20, Hunger.NONE, true), Memory.START), "depth 9");
        assertFalse(tengu.enters(hallway(5, false, 8, 20, Hunger.NONE, true), Memory.START), "Goo's floor");
        // No locked door drawn: nothing to open.
        Observation open = DescendPolicyTest.tile(hallway(DEPTH, false, 8, 20, Hunger.NONE, true), DOOR, Tile.DOOR);
        assertFalse(tengu.enters(open, Memory.START), "the door is a plain door once unlocked: the explore Policy walks in");
    }

    @Test
    @DisplayName("the stairs up are never taken on the unsealed boss floor, over many waits, though the screen offers them; on depth 9 they stay offered")
    void the_stairs_up_are_no_way_down() {
        Observation stairs = onEntrance(hallway(DEPTH, false, 1, 20, Hunger.NONE, false));
        assertTrue(stairs.actions().actions().contains(new Action.Ascend()), "the screen offers the way up");
        Brain brain = brain();
        org.shatterfish.api.Belief belief = null;
        for (int wait = 0; wait < 80; wait++) {
            belief = brain.update(stairs, belief);
            Brain.Decided decided = brain.decide(stairs, belief);
            assertNotEquals(new Action.Ascend(), decided.action(), "wait " + wait + ": " + decided.decision());
            belief = brain.handed(stairs, belief, decided);
        }
        assertTrue(Tengu.holds(stairs, Screens.CODEX));
        assertFalse(Tengu.holds(onEntrance(hallway(DEPTH - 1, false, 1, 20, Hunger.NONE, false)), Screens.CODEX));
        assertFalse(Tengu.holds(onEntrance(hallway(DEPTH, true, 1, 20, Hunger.NONE, false)), Screens.CODEX),
                "sealed: the floor is the fight's");
    }
}
