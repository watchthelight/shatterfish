package org.shatterfish.brain;

import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * The missiles the hero threw and left lying (issue #412): the cells, on the floor the hero is on, of plain
 * heaps the screen draws showing a missile weapon after an own throw was aimed at them.
 *
 * <p>Only what was seen. A thrown weapon that misses lands on the target's cell, with no scatter
 * (MissileWeapon.java:428-431, Item.java:195-199); one that hits and survives is dropped there too
 * (MissileWeapon.java:424), and a throwing stone is spent after five hits, a dart never
 * (ThrowingStone.java:37, Dart.java:60), so some throws leave no heap. The Memory therefore records the
 * cell a throw was aimed at when the Brain hands the throw over ({@link Memory#thrown}), and the next
 * screen keeps it only while a missile heap is drawn there: the screen keeps drawing a heap whose cell is
 * out of sight, as the game does (ADR-0006), so an entry leaves the Memory when a screen shows the heap gone
 * -- taken, broken, or never dropped -- and never lingers as a guess. Other floors' entries are forgotten.
 */
final class Missiles {

    private Missiles() {
    }

    /** The most throws remembered at once; the oldest is forgotten first. */
    static final int KEPT = 16;

    /** Whether {@code title} names a missile the Ranged Policy throws, quantity suffix and all. */
    static boolean missile(String title) {
        return !title.isEmpty() && Ranged.TIERS.containsKey(Beliefs.untitled(title));
    }

    /** The throw cells after {@code observation}: this floor's, kept while a missile heap is drawn on them. */
    static List<Memory.Spot> remember(Memory memory, Observation observation) {
        if (memory.missiles().isEmpty()) {
            return memory.missiles();
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<Memory.Spot> kept = new ArrayList<>();
        for (Memory.Spot spot : memory.missiles()) {
            if (spot.on(depth, branch) && lying(observation, spot.cell())) {
                kept.add(spot);
            }
        }
        return kept.equals(memory.missiles()) ? memory.missiles() : List.copyOf(kept);
    }

    /** Whether the screen draws a plain heap showing a missile on {@code cell}. */
    static boolean lying(Observation observation, int cell) {
        for (HeapView heap : observation.map().heaps()) {
            if (heap.cell() == cell && heap.kind() == HeapKind.HEAP && missile(heap.item())) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code heap} is a missile this Brain threw, by the cell remembered. */
    static boolean own(Memory memory, Observation observation, HeapView heap) {
        if (heap.kind() != HeapKind.HEAP || !missile(heap.item())) {
            return false;
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Spot spot : memory.missiles()) {
            if (spot.on(depth, branch) && spot.cell() == heap.cell()) {
                return true;
            }
        }
        return false;
    }
}
