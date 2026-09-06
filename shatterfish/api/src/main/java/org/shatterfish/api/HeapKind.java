package org.shatterfish.api;

/**
 * The kinds of heap the game draws ({@code core/.../items/Heap.java:62-71}), plus the one chest
 * that is never a heap. A plain heap or a for-sale heap shows its top item; every other kind
 * shows only its container, and a crystal chest names the category of what is inside (ADR-0006).
 * A hidden mimic is drawn as the chest it imitates and is emitted as that chest, never as an
 * actor: a {@link #CHEST}, a {@link #LOCKED_CHEST} or a {@link #CRYSTAL_CHEST}, or the
 * {@link #EBONY_CHEST} only an ebony mimic wears
 * ({@code core/.../sprites/ItemSpriteSheet.java:124}; {@code core/.../actors/mobs/EbonyMimic.java:47-71}),
 * which the screen shows and the schema therefore carries.
 */
public enum HeapKind {
    HEAP, FOR_SALE, CHEST, LOCKED_CHEST, CRYSTAL_CHEST, TOMB, SKELETON, REMAINS, EBONY_CHEST
}
