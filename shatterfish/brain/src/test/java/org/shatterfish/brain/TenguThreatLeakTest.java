package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The threat map is fair (story 10.20, issue #366; Non-negotiable 1). Tengu throws his bombs, fire and shockers
 * out of the hero's sight as well as in it, and the Brain may use the ones the screen draws and nothing else.
 * Four claims, each on constructed screens where the only input to the Brain is the screen:
 *
 * <ol>
 *   <li><b>The map is a function of what is drawn.</b> Over 300 random screens it equals a model written apart
 *       from {@link Threats}, which reads the same heaps and blobs and nothing else.</li>
 *   <li><b>A seed, a different Brain RNG or a hidden thing moves nothing.</b> The map and the Decision of a screen
 *       are the same under a permuted Brain seed, and the Observation has no way to carry a bomb under fog or a blob on
 *       a remembered cell (the record refuses both).</li>
 *   <li><b>An effect not yet drawn is not on the map.</b> The fire's cone is laid a step at a time; the cells
 *       beyond the front the screen shows cost nothing, and a hero beside them is not moved by them.</li>
 *   <li><b>The permutation control.</b> Two screens alike but for one drawn bomb give different Decisions; without
 *       this the claims above would hold for a Brain that never looked at the map.</li>
 * </ol>
 */
class TenguThreatLeakTest {

    private static final Playbook.Tengu COSTS = Playbook.Tengu.REFERENCE;

    private static int cell(int x, int y) {
        return TenguFightTest.cell(x, y);
    }

    private static Brain.Decided decide(long seed, Observation screen) {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, seed);
        Belief belief = brain.update(screen, null);
        return brain.decide(screen, belief);
    }

    /** The map as a model written apart from {@link Threats}: an open room, so the blast's steps are the king's distance. */
    private static int[] model(Observation screen) {
        int cells = screen.map().tiles().size();
        int[] cost = new int[cells];
        boolean[] shocked = new boolean[cells];
        for (int cell = 0; cell < cells; cell++) {
            if (screen.map().tiles().get(cell) == Tile.WALL) {
                continue;
            }
            for (HeapView heap : screen.map().heaps()) {
                int reach = Math.max(Math.abs(cell % 12 - heap.cell() % 12), Math.abs(cell / 12 - heap.cell() / 12));
                if (heap.item().equals("Smoke Bomb") && reach <= 2) {
                    cost[cell] += COSTS.bombCost();
                }
                if (heap.item().equals("Shocker") && reach <= 1) {
                    shocked[cell] = true;
                }
            }
        }
        for (BlobCell blob : screen.map().blobs()) {
            if (blob.kinds().contains("FireBlob")) {
                cost[blob.cell()] += COSTS.fireCost();
            }
            if (blob.kinds().contains("ShockerBlob")) {
                shocked[blob.cell()] = true;
            }
        }
        for (int cell = 0; cell < cells; cell++) {
            if (shocked[cell]) {
                cost[cell] += COSTS.shockCost();
            }
        }
        return cost;
    }

    @Test
    @DisplayName("the map equals a model that reads only the drawn heaps and blobs, over 300 random screens")
    void the_map_is_a_function_of_what_is_drawn() {
        Random random = new Random(366);
        for (int round = 0; round < 300; round++) {
            List<HeapView> heaps = new ArrayList<>();
            List<BlobCell> blobs = new ArrayList<>();
            java.util.Set<Integer> taken = new java.util.HashSet<>();
            taken.add(cell(6, 2));
            int hero = cell(1 + random.nextInt(4), 1 + random.nextInt(4));
            taken.add(hero);
            for (int n = random.nextInt(4); n > 0; n--) {
                int at = cell(1 + random.nextInt(10), 1 + random.nextInt(4));
                if (taken.add(at)) {
                    heaps.add(random.nextBoolean() ? TenguThreatMapTest.bomb(at) : TenguThreatMapTest.shocker(at));
                }
            }
            for (int n = random.nextInt(6); n > 0; n--) {
                int at = cell(1 + random.nextInt(10), 1 + random.nextInt(4));
                if (blobs.stream().noneMatch(blob -> blob.cell() == at)) {
                    blobs.add(TenguThreatMapTest.blob(at, random.nextBoolean() ? Threats.FIRE : Threats.SHOCK));
                }
            }
            // A heap of anything else is no threat at all.
            int gold = cell(1 + random.nextInt(10), 1 + random.nextInt(4));
            if (taken.add(gold)) {
                heaps.add(new HeapView(gold, HeapKind.HEAP, false, "gold", 0, ""));
            }
            Observation screen = TenguThreatMapTest.screen(hero, heaps, blobs);
            Threats.Map map = Threats.map(screen, COSTS);
            int[] expected = model(screen);
            for (int cell = 0; cell < expected.length; cell++) {
                assertEquals(expected[cell], map.cost(cell), "round " + round + ", cell " + cell);
            }
        }
    }

    @Test
    @DisplayName("a permuted Brain seed changes neither the map nor the Decision, and a thing under fog has no way into the screen")
    void a_seed_or_a_hidden_thing_moves_nothing() {
        Observation screen = TenguThreatMapTest.screen(cell(5, 2),
                List.of(TenguThreatMapTest.bomb(cell(4, 3))), List.of());
        Brain.Decided a = decide(5L, screen);
        for (long seed : new long[] {0L, 1L, 99L, 123456789L}) {
            Brain.Decided b = decide(seed, screen);
            assertEquals(a.action(), b.action(), "seed " + seed + ": " + b.decision());
            assertEquals(a.decision().chosen().why(), b.decision().chosen().why());
        }

        // A bomb on a cell the player has never seen cannot be put on the screen, nor a blob on a remembered one.
        Observation plain = TenguThreatMapTest.screen(cell(5, 2), List.of(), List.of());
        MapSection map = plain.map();
        List<Fog> fog = new ArrayList<>(map.fog());
        int unseen = cell(0, 0);
        assertEquals(Fog.VISIBLE, fog.get(cell(8, 3)));
        assertThrows(IllegalArgumentException.class, () -> new MapSection(map.width(), map.height(), map.tiles(),
                unknownAt(fog, unseen), map.traps(), List.of(TenguThreatMapTest.bomb(unseen)), List.of(), map.feeling(),
                map.transitions()), "a heap on an unseen cell");
        List<Fog> remembered = new ArrayList<>(map.fog());
        remembered.set(cell(8, 3), Fog.MAPPED);
        assertThrows(IllegalArgumentException.class, () -> new MapSection(map.width(), map.height(), map.tiles(),
                remembered, map.traps(), List.of(), List.of(TenguThreatMapTest.blob(cell(8, 3), Threats.FIRE)),
                map.feeling(), map.transitions()), "a blob on a remembered cell");
    }

    private static List<Fog> unknownAt(List<Fog> fog, int cell) {
        List<Fog> out = new ArrayList<>(fog);
        out.set(cell, Fog.UNKNOWN);
        return out;
    }

    @Test
    @DisplayName("the fire's cone is laid a step at a time: the cells beyond the front are not on the map, and the hero beside them is not moved")
    void an_effect_not_yet_drawn_is_not_on_the_map() {
        // Tengu at column 6; the fire is drawn on column 4 so far, the cells at columns 3 and 2 not yet.
        List<BlobCell> front = List.of(TenguThreatMapTest.blob(cell(4, 1), Threats.FIRE),
                TenguThreatMapTest.blob(cell(4, 2), Threats.FIRE), TenguThreatMapTest.blob(cell(4, 3), Threats.FIRE));
        Observation drawn = TenguThreatMapTest.screen(cell(3, 2), List.of(), front);
        Threats.Map map = Threats.map(drawn, COSTS);
        assertEquals(COSTS.fireCost(), map.cost(cell(4, 2)));
        for (int y = 1; y <= 4; y++) {
            assertEquals(0, map.cost(cell(3, y)), "not drawn yet: column 3");
            assertEquals(0, map.cost(cell(2, y)), "not drawn yet: column 2");
        }
        // The hero on column 3 is on no threat; it is not told to flee the cells the cone will reach, and it
        // does not walk onto the drawn ones where it can go round (it is three cells from Tengu).
        Brain.Decided decided = decide(5L, drawn);
        assertTrue(decided.action() instanceof org.shatterfish.api.Action.Step, decided.decision().toString());
        int to = ((org.shatterfish.api.Action.Step) decided.action()).cell();
        assertNotEquals(cell(4, 2), to, "not onto the fire: " + decided.decision());
    }

    @Test
    @DisplayName("the control: two screens alike but for one drawn bomb give different Decisions")
    void the_permutation_control() {
        Observation without = TenguThreatMapTest.screen(cell(5, 2), List.of(), List.of());
        Observation with = TenguThreatMapTest.screen(cell(5, 2), List.of(TenguThreatMapTest.bomb(cell(4, 3))), List.of());
        Brain.Decided a = decide(5L, without);
        Brain.Decided b = decide(5L, with);
        assertEquals("tengu: attack", a.decision().chosen().why());
        assertNotEquals(a.action(), b.action(), b.decision().toString());
        // And an Observation that differs in nothing the Brain reads gives one Decision.
        assertEquals(a.action(), decide(5L, without).action());
    }
}
