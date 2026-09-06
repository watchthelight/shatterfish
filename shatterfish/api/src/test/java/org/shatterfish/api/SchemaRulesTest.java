package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The records refuse what the screen would not draw, so the leaks ADR-0006 names have no way into
 * an Observation: a container's contents, a heap or trap on a cell never seen, a character out of
 * view, a tile on an unknown cell, a price on what is not for sale.
 */
class SchemaRulesTest {

    private static MapSection map(List<Tile> tiles, List<Fog> fog, List<TrapView> traps, List<HeapView> heaps,
                                  List<BlobCell> blobs, List<TransitionView> transitions) {
        return new MapSection(Corpus.WIDTH, Corpus.HEIGHT, tiles, fog, traps, heaps, blobs, Feeling.NONE, transitions);
    }

    @Test
    @DisplayName("an unknown cell shows nothing, and a known cell shows something")
    void unknown_cells_carry_no_tile() {
        List<Tile> tiles = new ArrayList<>(Corpus.tiles());
        tiles.set(21, Tile.WALL);
        assertThrows(IllegalArgumentException.class,
                () -> map(tiles, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
        List<Tile> blank = new ArrayList<>(Corpus.tiles());
        blank.set(0, Tile.NONE);
        assertThrows(IllegalArgumentException.class,
                () -> map(blank, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("one tile and one fog level per cell")
    void one_entry_per_cell() {
        List<Tile> tiles = new ArrayList<>(Corpus.tiles());
        tiles.remove(0);
        assertThrows(IllegalArgumentException.class,
                () -> map(tiles, Corpus.fog(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("nothing stands on a cell the player has never seen")
    void nothing_on_an_unknown_cell() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(22, "Gripping trap", true)), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(),
                List.of(new HeapView(22, HeapKind.CHEST, false, "", 0, "")), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(22, List.of("Fire"))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(), List.of(new TransitionView(22, TransitionKind.REGULAR_EXIT))));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(24, "Gripping trap", true)), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("a blob stands only in view: the emitter draws it nowhere else")
    void a_blob_stands_in_view() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(12, List.of("ToxicGas"))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(),
                List.of(new BlobCell(19, List.of("ToxicGas"))), List.of()));
        map(Corpus.tiles(), Corpus.fog(), List.of(), List.of(), List.of(new BlobCell(1, List.of("ToxicGas"))), List.of());
    }

    @Test
    @DisplayName("a cell is named at most once per list")
    void one_entry_per_cell_per_list() {
        assertThrows(IllegalArgumentException.class, () -> map(Corpus.tiles(), Corpus.fog(),
                List.of(new TrapView(2, "Toxic gas trap", true), new TrapView(2, "Alarm trap", true)),
                List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ActorsSection(List.of(
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of()),
                new ActorView(4, "Crab", Alignment.ENEMY, 11, false, Emote.NONE, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> new HeaderSection(1, "v3.3.8", "", HeroClass.MAGE,
                List.of(Challenge.NO_FOOD, Challenge.NO_FOOD), 1, 0, false, false, PromptKind.NONE));
        assertThrows(IllegalArgumentException.class, () -> new BlobCell(5, List.of("Fire", "Fire")));
    }

    @Test
    @DisplayName("a container shows nothing but itself: no item, no price, no category")
    void a_container_shows_only_itself() {
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.CHEST, false, "Potion of Strength", 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.LOCKED_CHEST, false, "", 0, "artifact"));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.HEAP, false, "Dart", 10, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new HeapView(3, HeapKind.FOR_SALE, false, "Dart", -1, ""));
        new HeapView(3, HeapKind.CRYSTAL_CHEST, false, "", 0, "ring");
        new HeapView(3, HeapKind.FOR_SALE, false, "Dart", 0, "");
    }

    @Test
    @DisplayName("a character is drawn only in view")
    void an_actor_stands_in_view() {
        assertThrows(IllegalArgumentException.class, () -> new Observation(Corpus.header(), Corpus.map(),
                new ActorsSection(List.of(new ActorView(13, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())))));
        assertThrows(IllegalArgumentException.class, () -> new Observation(Corpus.header(), Corpus.map(),
                new ActorsSection(List.of(new ActorView(21, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())))));
        assertThrows(IllegalArgumentException.class, () -> new Observation(Corpus.header(), Corpus.map(),
                new ActorsSection(List.of(new ActorView(24, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())))));
    }

    @Test
    @DisplayName("health is pips of the bar, a buff shows turns only when timed")
    void bounded_numbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActorView(4, "Rat", Alignment.ENEMY, 12, false, Emote.NONE, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ActorView(4, "Rat", Alignment.ENEMY, -1, false, Emote.NONE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("Poisoned", false, 100));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("Poisoned", true, -1));
        assertThrows(IllegalArgumentException.class, () -> new BuffView("", true, 100));
    }

    @Test
    @DisplayName("lists come out sorted, immutable and null-free")
    void lists_are_canonical() {
        ActorsSection actors = new ActorsSection(List.of(
                new ActorView(11, "Ghost", Alignment.NEUTRAL, 11, true, Emote.NONE, List.of()),
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of())));
        assertEquals(List.of(4, 11), actors.actors().stream().map(ActorView::cell).toList());
        assertThrows(UnsupportedOperationException.class, () -> actors.actors().add(null));
        List<Challenge> withNull = new ArrayList<>();
        withNull.add(Challenge.DARKNESS);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new HeaderSection(1, "v3.3.8", "", HeroClass.MAGE, withNull,
                1, 0, false, false, PromptKind.NONE));
    }
}
