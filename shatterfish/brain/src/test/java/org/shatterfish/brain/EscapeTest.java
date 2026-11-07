package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaving a harmful cloud (issue #408): the Policy that steps out of fire and gas the hero did not seed
 * itself, with and without an enemy beside it. Watched on standard Warrior seed 648322377831, depth 4,
 * turns 1194-1202: a hero in a trap's toxic gas, no enemy, picked eight fallback Actions at random.
 */
class EscapeTest {

    /** A corridor eleven cells long, the hero on cell 3, toxic gas on cells 2 to 5: cell 1 is the near clear one. */
    private static final int CELLS = 11;

    private static Observation corridor(int hero, int hp, List<ActorView> actors, List<BlobCell> blobs, Action... more) {
        return corridor(4, List.of(), hero, hp, actors, blobs, more);
    }

    private static Observation corridor(int depth, List<org.shatterfish.api.ItemView> items, int hero, int hp,
                                        List<ActorView> actors, List<BlobCell> blobs, Action... more) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(CELLS, Tile.EMPTY));
        List<Action> offered = new ArrayList<>();
        for (int next : new int[] {hero - 1, hero + 1}) {
            boolean occupied = actors.stream().anyMatch(actor -> actor.cell() == next);
            if (next >= 0 && next < CELLS && !occupied) {
                offered.add(new Action.Step(next));
            }
        }
        offered.addAll(List.of(more));
        return Screens.lab(depth, CELLS, tiles, blobs, Screens.heroAt(hero, hp, 30, List.of()), items, actors,
                List.of(), offered.toArray(Action[]::new));
    }

    private static List<BlobCell> gas(String kind, int... cells) {
        List<BlobCell> blobs = new ArrayList<>();
        for (int cell : cells) {
            blobs.add(new BlobCell(cell, List.of(kind)));
        }
        return blobs;
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 7L);
        return brain.decide(screen, brain.update(screen, null));
    }

    private static ActorView rat(int cell, int pips) {
        return new ActorView(cell, "rat", Alignment.ENEMY, pips, false, Emote.NONE, List.of());
    }

    @Test
    @DisplayName("in toxic gas with no enemy: the hero steps toward the nearest clear cell, not Rest, Search or the far way")
    void steps_out_of_gas_with_no_enemy() {
        Observation screen = corridor(3, 30, List.of(), gas("ToxicGas", 2, 3, 4, 5), new Action.Rest(false), new Action.Search());
        for (long seed = 0; seed < 20; seed++) {
            Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, seed);
            Brain.Decided decided = brain.decide(screen, brain.update(screen, null));
            assertEquals("escape", decided.decision().policy(), "seed " + seed);
            assertEquals(new Action.Step(2), decided.action(), "one gas cell to cross on the left, two on the right");
            assertTrue(decided.decision().flags().contains("in-harm"), decided.decision().flags().toString());
        }
    }

    @Test
    @DisplayName("in toxic gas beside a rat: the hero still steps out, since the rat does not die this turn")
    void steps_out_of_gas_beside_a_rat() {
        Observation screen = corridor(3, 30, List.of(rat(4, org.shatterfish.api.ObservationCodec.MAX_HEALTH_PIPS)),
                gas("ToxicGas", 2, 3, 4, 5), new Action.Attack(4), new Action.Rest(false));
        Brain.Decided decided = decide(screen);
        assertEquals("escape", decided.decision().policy());
        assertEquals(new Action.Step(2), decided.action());
    }

    @Test
    @DisplayName("beside a rat one blow kills: the hero finishes it first")
    void finishes_a_rat_that_dies_this_turn() {
        org.shatterfish.api.ItemView axe = Screens.gear(org.shatterfish.api.ItemKind.WEAPON, "greataxe",
                org.shatterfish.api.EquipSlot.WEAPON, true, false);
        Observation screen = corridor(1, List.of(axe), 3, 30, List.of(rat(4, 1)), gas("ToxicGas", 2, 3, 4, 5),
                new Action.Attack(4));
        Brain.Decided decided = decide(screen);
        assertEquals("escape", decided.decision().policy());
        assertEquals(new Action.Attack(4), decided.action());
        assertTrue(decided.decision().chosen().why().startsWith("finish: "), decided.decision().chosen().why());
    }

    @Test
    @DisplayName("out of the gas and beside nothing: the Policy does not enter")
    void does_not_enter_on_a_clear_cell() {
        Observation screen = corridor(1, 30, List.of(), gas("ToxicGas", 3, 4, 5), new Action.Rest(false));
        assertFalse(new Escape(Screens.CODEX, org.shatterfish.api.Playbook.reference("shatterfish"))
                .enters(screen, Memory.START));
        assertFalse(decide(screen).decision().flags().contains("in-harm"));
    }

    @Test
    @DisplayName("standing in the gas, the explore plan has a way out over the clouded cells")
    void explore_is_not_pathless_in_a_cloud() {
        Observation inside = corridor(3, 30, List.of(), gas("ToxicGas", 2, 3, 4, 5), new Action.Rest(false));
        boolean[] walk = Explore.walkable(inside, Memory.START);
        assertTrue(walk[2] && walk[4], "a hero in a cloud may cross it");
        Observation outside = corridor(1, 30, List.of(), gas("ToxicGas", 2, 3, 4, 5), new Action.Rest(false));
        assertFalse(Explore.walkable(outside, Memory.START)[2], "a hero outside still keeps out of it");
    }

    @Test
    @DisplayName("beside a paralytic gas: the hero steps away from it before it spreads, never toward or along it")
    void leaves_before_the_paralytic_gas_arrives() {
        Observation screen = corridor(5, 30, List.of(), gas("ParalyticGas", 3, 4), new Action.Rest(false));
        Brain.Decided decided = decide(screen);
        assertEquals("escape", decided.decision().policy());
        assertEquals(new Action.Step(6), decided.action());
        // Two cells off, the gas cannot reach the hero this turn: nothing to do.
        Observation clear = corridor(6, 30, List.of(), gas("ParalyticGas", 3, 4), new Action.Rest(false));
        assertFalse(new Escape(Screens.CODEX, org.shatterfish.api.Playbook.reference("shatterfish"))
                .enters(clear, Memory.START));
        assertFalse(Explore.walkable(clear, Memory.START)[5], "and the cell beside the gas is one no plan walks onto");
    }
}
