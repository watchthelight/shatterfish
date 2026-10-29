package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Potion identities from the floor's evidence (issue #219): a special room that queues a fixed potion, seen
 * on a floor, raises that potion for an appearance first found there; a stack bounds what it can be; ordinary
 * traps say nothing (docs/rules/generation.md, the potion-room-inference rows).
 */
class RoomEvidenceTest {

    private static final Codex.Knowledge CODEX = new Codex.Knowledge(Screens.CODEX.manifest(),
            List.of(new Codex.Identities(ItemKind.POTION, List.of("crimson potion", "amber potion", "golden potion", "ivory potion"),
                    List.of(new Codex.Candidate("items.potions.PotionOfStrength", "potion of strength", 0),
                            new Codex.Candidate("items.potions.PotionOfHealing", "potion of healing", 6),
                            new Codex.Candidate("items.potions.PotionOfLiquidFlame", "potion of liquid flame", 3),
                            new Codex.Candidate("items.potions.PotionOfFrost", "potion of frost", 3),
                            new Codex.Candidate("items.potions.PotionOfHaste", "potion of haste", 2)))),
            List.of(new Codex.RoomSpawn("levels.rooms.special.StorageRoom", "items.potions.PotionOfLiquidFlame",
                            "potion of liquid flame"),
                    new Codex.RoomSpawn("levels.rooms.special.SentryRoom", "items.potions.PotionOfHaste", "potion of haste")),
            Screens.CODEX.guarantees(), List.of(), List.of(), List.of(), List.of(), List.of());

    private static final int W = 6;
    private static final Tile E = Tile.EMPTY;
    private static final Tile X = Tile.WALL;

    /** A 6x3 floor: a barricade set in a wall at cell 8 (walls above and below it) with the special floor beside it. */
    private static final List<Tile> STORAGE = List.of(
            E, E, X, E, E, E,
            E, E, Tile.BARRICADE, Tile.EMPTY_SP, E, E,
            E, E, X, E, E, E);

    private static final List<Tile> PLAIN = Collections.nCopies(18, E);

    private static Observation screen(int depth, List<Tile> tiles, List<TrapView> traps, List<BlobCell> blobs,
                                      List<ActorView> actors, List<HeapView> heaps, List<org.shatterfish.api.ItemView> items) {
        Observation base = Screens.grid(depth, W, tiles, heaps);
        MapSection m = base.map();
        MapSection map = new MapSection(m.width(), m.height(), m.tiles(), m.fog(), traps, m.heaps(), blobs, m.feeling(),
                m.transitions());
        return new Observation(base.header(), map, new org.shatterfish.api.ActorsSection(actors), base.hero(),
                new org.shatterfish.api.InventorySection(items), base.journal(), base.log(), base.actions(), base.prompt());
    }

    private static Beliefs after(Observation... screens) {
        Brain brain = new Brain(CODEX, Screens.WEIGHTS, 3L);
        Belief belief = null;
        for (Observation s : screens) {
            belief = brain.update(s, belief);
        }
        return brain.beliefs(screens[screens.length - 1], belief);
    }

    private static double p(Beliefs beliefs, String label, String name) {
        return beliefs.identities().stream().filter(g -> g.label().equals(label)).findFirst().orElseThrow().odds().stream()
                .filter(o -> o.name().equals(name)).mapToDouble(Beliefs.Odds::probability).sum();
    }

    private static HeapView potion(int cell, String name) {
        return new HeapView(cell, HeapKind.HEAP, false, name, 0, "");
    }

    @Test
    @DisplayName("a storage room on the floor makes the one new potion there liquid flame, far above its deck share")
    void storage_room() {
        Beliefs with = after(screen(2, STORAGE, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()));
        Beliefs without = after(screen(2, PLAIN, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()));
        double prior = p(without, "crimson potion", "potion of liquid flame");
        assertEquals(3.0 / 26, prior, 1e-9, "no room: the deck share");
        assertTrue(p(with, "crimson potion", "potion of liquid flame") > 0.5, with.identities().toString());
    }

    @Test
    @DisplayName("the room's potion belongs to the floor it was seen on: a potion first found on another floor stays at its prior")
    void other_floor() {
        Beliefs beliefs = after(screen(1, PLAIN, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()),
                screen(2, STORAGE, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()));
        assertEquals(3.0 / 26, p(beliefs, "crimson potion", "potion of liquid flame"), 1e-9);
    }

    @Test
    @DisplayName("two new potions on the room's floor share it: each is liquid flame about half as often as one alone")
    void two_candidates() {
        Beliefs beliefs = after(screen(2, STORAGE, List.of(), List.of(), List.of(),
                List.of(potion(0, "crimson potion"), potion(1, "amber potion")), List.of()));
        double a = p(beliefs, "crimson potion", "potion of liquid flame");
        double b = p(beliefs, "amber potion", "potion of liquid flame");
        assertEquals(a, b, 1e-9);
        assertTrue(a > 3.0 / 26 && a < 0.5, "" + a);
    }

    @Test
    @DisplayName("ordinary traps and a lone barricade change nothing")
    void negative() {
        List<TrapView> traps = new ArrayList<>();
        for (int cell : new int[]{0, 3, 6, 9, 12}) {
            traps.add(new TrapView(cell, "fire trap", false));
        }
        Beliefs scattered = after(screen(2, PLAIN, traps, List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()));
        assertEquals(3.0 / 26, p(scattered, "crimson potion", "potion of liquid flame"), 1e-9);
        List<Tile> open = new ArrayList<>(PLAIN);
        open.set(8, Tile.BARRICADE);
        Beliefs lone = after(screen(2, open, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion")), List.of()));
        assertEquals(3.0 / 26, p(lone, "crimson potion", "potion of liquid flame"), 1e-9, "no wall, no special floor");
    }

    @Test
    @DisplayName("a red sentry in view is the sentry room: haste")
    void sentry() {
        Observation s = screen(2, PLAIN, List.of(), List.of(), List.of(Screens.enemy(Sentry.RED, 5)),
                List.of(potion(0, "crimson potion")), List.of());
        assertTrue(RoomSigns.sentry(s));
        assertTrue(p(after(s), "crimson potion", "potion of haste") > 0.4);
    }

    @Test
    @DisplayName("a stack of four cannot be a weight-three potion, and is healing far more than the weights say")
    void stack() {
        Beliefs beliefs = after(screen(1, PLAIN, List.of(), List.of(), List.of(), List.of(potion(0, "crimson potion x4")), List.of()));
        assertEquals(0.0, p(beliefs, "crimson potion", "potion of liquid flame"), 1e-12);
        assertEquals(0.0, p(beliefs, "crimson potion", "potion of haste"), 1e-12);
        assertTrue(p(beliefs, "crimson potion", "potion of healing") > 6.0 / 26);
    }

    @Test
    @DisplayName("the signs: vents, flames and a trap field are recognised, a few traps are not")
    void signs() {
        List<TrapView> vents = List.of(new TrapView(0, RoomSigns.TOXIC_VENT, false), new TrapView(1, RoomSigns.TOXIC_VENT, false));
        assertTrue(RoomSigns.toxicGas(screen(1, PLAIN, vents, List.of(), List.of(), List.of(), List.of()).map()));
        assertFalse(RoomSigns.toxicGas(screen(1, PLAIN, vents.subList(0, 1), List.of(), List.of(), List.of(), List.of()).map()));
        List<BlobCell> flames = List.of(new BlobCell(6, List.of(RoomSigns.ETERNAL_FIRE)), new BlobCell(7, List.of(RoomSigns.ETERNAL_FIRE)),
                new BlobCell(8, List.of(RoomSigns.ETERNAL_FIRE)));
        assertTrue(RoomSigns.magicalFire(screen(1, PLAIN, List.of(), flames, List.of(), List.of(), List.of()).map()));
        List<TrapView> field = new ArrayList<>();
        for (int cell = 0; cell < 6; cell++) {
            field.add(new TrapView(cell, "alarm trap", false));
        }
        assertTrue(RoomSigns.traps(screen(1, PLAIN, field, List.of(), List.of(), List.of(), List.of()).map()));
        assertFalse(RoomSigns.traps(screen(1, PLAIN, field.subList(0, 3), List.of(), List.of(), List.of(), List.of()).map()));
    }
}
