package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The map section of an Observation (ADR-0005): the floor as the player sees it. Cells are
 * row-major, {@code cell = y * width + x}, as the game indexes them; {@code tiles} and
 * {@code fog} are positional and have one entry per cell, and every other list is ordered by
 * cell and names each cell at most once.
 *
 * <p>The record refuses what the fog would not draw: an {@link Fog#UNKNOWN} cell carries
 * {@link Tile#NONE} and nothing else does; traps, heaps, blobs and transitions stand on cells the
 * player has seen (ADR-0006).
 *
 * @param tiles what each cell looks like, one per cell
 * @param fog how much of each cell the player can see, one per cell
 * @param traps the traps in view, by cell
 * @param heaps the heaps seen, by cell
 * @param blobs the cells with a blob on them, by cell
 * @param feeling the floor feeling announced on arrival, or {@link Feeling#NONE}
 * @param transitions the stairs and exits seen, by cell
 */
public record MapSection(int width, int height, List<Tile> tiles, List<Fog> fog, List<TrapView> traps,
                         List<HeapView> heaps, List<BlobCell> blobs, Feeling feeling,
                         List<TransitionView> transitions) {

    public MapSection {
        Canon.require(width > 0 && height > 0, "a map has a width and a height: " + width + "x" + height);
        int cells = width * height;
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        fog = List.copyOf(Objects.requireNonNull(fog, "fog"));
        Canon.require(tiles.size() == cells, "one tile per cell: " + tiles.size() + " for " + cells + " cells");
        Canon.require(fog.size() == cells, "one fog level per cell: " + fog.size() + " for " + cells + " cells");
        for (int cell = 0; cell < cells; cell++) {
            boolean unknown = fog.get(cell) == Fog.UNKNOWN;
            Canon.require(unknown == (tiles.get(cell) == Tile.NONE),
                    "cell " + cell + " is " + fog.get(cell) + " and shows " + tiles.get(cell)
                            + ": an unknown cell shows nothing, and a known cell shows something");
        }
        Objects.requireNonNull(feeling, "feeling");
        traps = Canon.sorted(traps, Comparator.comparingInt(TrapView::cell), "traps");
        Canon.distinctBy(traps, TrapView::cell, "traps");
        heaps = Canon.sorted(heaps, Comparator.comparingInt(HeapView::cell), "heaps");
        Canon.distinctBy(heaps, HeapView::cell, "heaps");
        blobs = Canon.sorted(blobs, Comparator.comparingInt(BlobCell::cell), "blobs");
        Canon.distinctBy(blobs, BlobCell::cell, "blobs");
        transitions = Canon.sorted(transitions, Comparator.comparingInt(TransitionView::cell), "transitions");
        Canon.distinctBy(transitions, TransitionView::cell, "transitions");
        for (TrapView trap : traps) {
            seen(fog, trap.cell(), "a trap");
        }
        for (HeapView heap : heaps) {
            seen(fog, heap.cell(), "a heap");
        }
        for (BlobCell blob : blobs) {
            seen(fog, blob.cell(), "a blob");
        }
        for (TransitionView transition : transitions) {
            seen(fog, transition.cell(), "a transition");
        }
    }

    private static void seen(List<Fog> fog, int cell, String what) {
        Canon.require(cell < fog.size(), what + " is off the map at cell " + cell);
        Canon.require(fog.get(cell) != Fog.UNKNOWN, what + " stands on cell " + cell + ", which the player has never seen");
    }

    /** The number of cells, {@code width * height}. */
    public int cells() {
        return width * height;
    }
}
