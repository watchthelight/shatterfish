package org.shatterfish.api;

import java.util.Objects;

/**
 * A heap the player has seen, with what its sprite shows and nothing more (ADR-0006): a plain or
 * for-sale heap shows its current top item, a single for-sale item its price, a crystal chest the
 * category of what is inside, every other container only itself. A hidden heap is drawn faint.
 * The record refuses the rest, so a container's contents have no way in.
 *
 * @param item the top item's display name for a {@link HeapKind#HEAP} or {@link HeapKind#FOR_SALE};
 *             empty otherwise
 * @param price the price of a single for-sale item; zero for a stack of several and for every
 *              other kind
 * @param category the category a {@link HeapKind#CRYSTAL_CHEST} names; empty otherwise
 */
public record HeapView(int cell, HeapKind kind, boolean hidden, String item, int price, String category) {

    public HeapView {
        Canon.cell(cell, "a heap");
        Objects.requireNonNull(kind, "kind");
        item = Canon.text(item, "item");
        category = Canon.text(category, "category");
        boolean showsItem = kind == HeapKind.HEAP || kind == HeapKind.FOR_SALE;
        Canon.require(showsItem || item.isEmpty(), "a " + kind + " shows only its container, never an item");
        Canon.require(price >= 0, "a price is not negative: " + price);
        Canon.require(kind == HeapKind.FOR_SALE || price == 0, "only a for-sale heap shows a price");
        Canon.require(kind == HeapKind.CRYSTAL_CHEST || category.isEmpty(), "only a crystal chest names a category");
    }
}
