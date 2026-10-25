package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.NoteView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The chest Policy (issue #181): a chest, a skeleton or remains is opened on a calm screen when the
 * hero can take the risk; a locked or crystal chest only with its key in the journal; a tomb only
 * from a nook with few free cardinal sides. Everything goes through {@link Brain#decide}.
 */
class ChestPolicyTest {

    private static final int WIDTH = 9;

    /** A 9 by 5 room, walled round, with the hero on {@code hero} and one heap, the screen offering what the game offers. */
    private static Observation room(int depth, int hero, int hp, int ht, HeapKind kind, int heapCell, boolean hidden,
                                    List<NoteView> notes) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(WIDTH * 5, Tile.EMPTY));
        for (int x = 0; x < WIDTH; x++) {
            tiles.set(x, Tile.WALL);
            tiles.set(x + 4 * WIDTH, Tile.WALL);
        }
        for (int y = 0; y < 5; y++) {
            tiles.set(y * WIDTH, Tile.WALL);
            tiles.set(y * WIDTH + WIDTH - 1, Tile.WALL);
        }
        Observation bare = Screens.grid(depth, WIDTH, tiles, List.of(new HeapView(heapCell, kind, hidden, "", 0, "")));
        Observation placed = new Observation(bare.header(), bare.map(), bare.actors(), Screens.heroAt(hero, 10, hp, ht),
                bare.inventory(), new JournalSection(notes, List.of()), bare.log(), bare.actions(), bare.prompt());
        return placed.withActions(ValidActions.of(placed));
    }

    private static NoteView key(int depth, String title) {
        return new NoteView(NoteKind.KEY, depth, title, "", 1);
    }

    private static Brain.Decided decide(Observation observation) {
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        return brain.decide(observation, brain.update(observation, null));
    }

    private static final int HERO = WIDTH + 2;

    @Test
    @DisplayName("a plain chest in a calm room is walked to and opened: a Step toward it, then the Step onto it from beside it")
    void plain_chest() {
        Brain.Decided far = decide(room(1, HERO, 200, 200, HeapKind.CHEST, WIDTH + 6, false, List.of()));
        assertEquals("open-chest", far.decision().policy(), far.decision().toString());
        assertEquals(new Action.Step(HERO + 1), far.action());

        Brain.Decided beside = decide(room(1, HERO, 200, 200, HeapKind.CHEST, HERO + 1, false, List.of()));
        assertEquals("open-chest", beside.decision().policy());
        assertEquals(new Action.Step(HERO + 1), beside.action(), "the click on the chest opens it from beside it");

        Brain.Decided on = decide(room(1, HERO + 1, 200, 200, HeapKind.CHEST, HERO + 1, false, List.of()));
        assertEquals(new Action.OpenChest(HERO + 1), on.action(), "standing on it, the open");
        assertNotEquals("fallback", on.decision().policy());
    }

    @Test
    @DisplayName("a locked chest is opened with the golden key in the journal for this floor, and left without it or with another floor's")
    void locked_chest_needs_its_key() {
        int chest = WIDTH + 6;
        Brain.Decided held = decide(room(3, HERO, 20, 20, HeapKind.LOCKED_CHEST, chest, false,
                List.of(key(3, "golden key"))));
        assertEquals("open-chest", held.decision().policy(), held.decision().toString());
        assertEquals(new Action.Step(HERO + 1), held.action());

        assertNotEquals("open-chest", decide(room(3, HERO, 20, 20, HeapKind.LOCKED_CHEST, chest, false,
                List.of())).decision().policy(), "no key held");
        assertNotEquals("open-chest", decide(room(3, HERO, 20, 20, HeapKind.LOCKED_CHEST, chest, false,
                List.of(key(2, "golden key")))).decision().policy(), "a key for another floor");
        assertNotEquals("open-chest", decide(room(3, HERO, 20, 20, HeapKind.LOCKED_CHEST, chest, false,
                List.of(key(3, "Crystal key")))).decision().policy(), "the wrong key");
    }

    @Test
    @DisplayName("a chest may be a hidden mimic: opened only with the hit points to take its bite and the fight after, a locked chest with one bite")
    void mimic_margin() {
        int chest = WIDTH + 6;
        for (int depth : new int[] {1, 2, 5}) {
            Observation probe = room(depth, HERO, 100000, 100000, HeapKind.CHEST, chest, false, List.of());
            double cost = Chests.mimicCost(probe, Screens.CODEX);
            // The bite alone is 2 + 2 * depth (Mimic.java:229-236); the fight after it is the hero's duel model's.
            assertEquals(true, cost > Chests.mimicBite(depth), "the fight after the bite is counted at depth " + depth);
            int enough = (int) Math.floor(cost * Chests.MIMIC_MARGIN_PER_MILLE / 1000.0) + 1;
            assertEquals("open-chest", decide(room(depth, HERO, enough, 100000, HeapKind.CHEST, chest, false, List.of()))
                    .decision().policy(), "depth " + depth + " with " + enough + " hit points");
            assertNotEquals("open-chest", decide(room(depth, HERO, enough - 1, 100000, HeapKind.CHEST, chest, false, List.of()))
                    .decision().policy(), "depth " + depth + " with " + (enough - 1) + " hit points");
        }
        // A hero with 20 hit points and no weapon does not take on a depth 2 mimic (18 health, 3 to 6 a hit) for one item.
        assertEquals("open-chest", decide(room(1, HERO, 1000, 1000, HeapKind.CHEST, chest, false, List.of()))
                .decision().policy());
        assertNotEquals("open-chest", decide(room(2, HERO, 20, 20, HeapKind.CHEST, chest, false, List.of()))
                .decision().policy(), "a fresh hero does not take on a depth 2 mimic for one item");
        // A locked chest with its key needs the health to survive one bite: depth 6 is 14.
        List<NoteView> golden = List.of(key(6, "golden key"));
        assertEquals("open-chest", decide(room(6, HERO, 15, 40, HeapKind.LOCKED_CHEST, chest, false, golden))
                .decision().policy());
        assertNotEquals("open-chest", decide(room(6, HERO, 14, 40, HeapKind.LOCKED_CHEST, chest, false, golden))
                .decision().policy());
    }

    @Test
    @DisplayName("skeletons and remains are searched unless a haunting would be too much; a faint heap and an ebony chest never are")
    void bones_and_the_never() {
        int bones = WIDTH + 6;
        for (HeapKind kind : List.of(HeapKind.SKELETON, HeapKind.REMAINS)) {
            Brain.Decided healthy = decide(room(4, HERO, 20, 20, kind, bones, false, List.of()));
            assertEquals("open-chest", healthy.decision().policy(), kind + " " + healthy.decision());
            // A haunted heap spawns a wraith at it or costs half the health (Heap.java:96-100).
            assertNotEquals("open-chest", decide(room(4, HERO, 12, 20, kind, bones, false, List.of()))
                    .decision().policy(), "12 hit points do not take two hits of a wraith at depth 4");
        }
        assertNotEquals("open-chest", decide(room(1, HERO, 20, 20, HeapKind.CHEST, bones, true, List.of()))
                .decision().policy(), "a faint heap is not a chest to open");
        assertNotEquals("open-chest", decide(room(1, HERO, 20, 20, HeapKind.EBONY_CHEST, bones, false, List.of()))
                .decision().policy(), "an ebony chest is always a mimic");
    }

    /**
     * A one-cell-wide corridor along the third row of a 9 by 5 room, the hero on its fourth cell, and a tomb in an
     * alcove above the corridor's second cell: from the corridor's first cell, diagonal to the tomb, three sides are walls.
     */
    private static Observation corridor(int depth, int hp) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(WIDTH * 5, Tile.WALL));
        for (int x = 1; x < WIDTH - 1; x++) {
            tiles.set(2 * WIDTH + x, Tile.EMPTY);
        }
        tiles.set(WIDTH + 2, Tile.EMPTY);
        Observation bare = Screens.grid(depth, WIDTH, tiles,
                List.of(new HeapView(WIDTH + 2, HeapKind.TOMB, false, "", 0, "")));
        Observation placed = new Observation(bare.header(), bare.map(), bare.actors(), Screens.heroAt(2 * WIDTH + 4, 10, hp, 20),
                bare.inventory(), bare.journal(), bare.log(), bare.actions(), bare.prompt());
        return placed.withActions(ValidActions.of(placed));
    }

    @Test
    @DisplayName("a tomb is opened only from a nook with at most one free cardinal side, and with the health for that wraith; in the open, never")
    void tomb_nook() {
        // The wraiths come on every free cardinal side of the hero (Wraith.java:102-110, Heap.java:86): at the
        // corridor's dead end, diagonal to the tomb, one side is free.
        Brain.Decided nook = decide(corridor(1, 20));
        assertEquals("open-chest", nook.decision().policy(), nook.decision().toString());
        assertEquals(new Action.Step(2 * WIDTH + 3), nook.action(), "walks to the nook first");
        // One wraith at depth 1 is two hits of up to 3.
        assertEquals("open-chest", decide(corridor(1, 7)).decision().policy());
        assertNotEquals("open-chest", decide(corridor(1, 6)).decision().policy());
        // In the open room all four sides are free.
        assertNotEquals("open-chest", decide(room(1, HERO, 20, 20, HeapKind.TOMB, 2 * WIDTH + 4, false, List.of()))
                .decision().policy());
    }

    @Test
    @DisplayName("an open the game keeps refusing is given up after two tries, not repeated for ever")
    void refused_open_is_given_up() {
        Observation screen = room(1, HERO, 200, 200, HeapKind.CHEST, HERO + 1, false, List.of());
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        assertEquals("open-chest", brain.decide(screen, Screens.drive(brain, screen)).decision().policy());
        assertEquals("open-chest", brain.decide(screen, Screens.drive(brain, screen, screen)).decision().policy());
        // The click twice handed over, the hero still where it was: the game refused it.
        assertNotEquals("open-chest", brain.decide(screen, Screens.drive(brain, screen, screen, screen)).decision().policy());
    }

    @Test
    @DisplayName("a chest is not walked to while an enemy seen a moment ago is out of view: it would drag the hero into the enemy's sight again and again")
    void no_chest_with_an_enemy_about() {
        int chest = WIDTH + 6;
        int enemyCell = 3 * WIDTH + 7;
        Observation calm = room(1, HERO, 200, 200, HeapKind.CHEST, chest, false, List.of());
        Observation seen = new Observation(calm.header(), calm.map(),
                new ActorsSection(List.of(Screens.enemy("rat", enemyCell))), calm.hero(), calm.inventory(),
                calm.journal(), calm.log(), calm.actions(), calm.prompt());
        seen = seen.withActions(ValidActions.of(seen));
        // The same room a wait later: the rat's cell is out of view, so it is not on the screen but was seen.
        List<Fog> fog = new ArrayList<>(calm.map().fog());
        fog.set(enemyCell, Fog.VISITED);
        MapSection dim = calm.map();
        Observation hidden = new Observation(calm.header(),
                new MapSection(dim.width(), dim.height(), dim.tiles(), fog, dim.traps(), dim.heaps(), dim.blobs(),
                        dim.feeling(), dim.transitions()),
                calm.actors(), calm.hero(), calm.inventory(), calm.journal(), calm.log(), calm.actions(), calm.prompt());
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
        assertNotEquals("open-chest", brain.decide(hidden, Screens.drive(brain, seen, hidden)).decision().policy());
        // Without the sighting, the same screen opens it.
        assertEquals("open-chest", brain.decide(hidden, Screens.drive(brain, calm, hidden)).decision().policy());
    }

    @Test
    @DisplayName("a crystal chest is opened with the crystal key, whatever its capital, and not with the golden one")
    void crystal_chest_needs_its_key() {
        int chest = WIDTH + 6;
        assertEquals("open-chest", decide(room(3, HERO, 5000, 5000, HeapKind.CRYSTAL_CHEST, chest, false,
                List.of(key(3, "Crystal key")))).decision().policy());
        assertNotEquals("open-chest", decide(room(3, HERO, 5000, 5000, HeapKind.CRYSTAL_CHEST, chest, false,
                List.of(key(3, "golden key")))).decision().policy());
        assertNotEquals("open-chest", decide(room(3, HERO, 5000, 5000, HeapKind.CRYSTAL_CHEST, chest, false,
                List.of())).decision().policy());
    }
}
