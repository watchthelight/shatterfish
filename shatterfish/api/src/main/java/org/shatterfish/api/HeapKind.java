package org.shatterfish.api;

/**
 * The kinds of heap the game draws ({@code core/.../items/Heap.java:62-71}). A plain heap or a
 * for-sale heap shows its top item; every other kind shows only its container, and a crystal
 * chest names the category of what is inside (ADR-0006). A neutral, passive mimic is drawn as a
 * {@link #CHEST} and is emitted as one, never as an actor.
 */
public enum HeapKind {
    HEAP, FOR_SALE, CHEST, LOCKED_CHEST, CRYSTAL_CHEST, TOMB, SKELETON, REMAINS
}
