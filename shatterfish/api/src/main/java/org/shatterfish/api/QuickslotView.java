package org.shatterfish.api;

/**
 * One quickslot as its button draws it: the display name of the item in it, or empty, and whether
 * the slot holds a placeholder, an item used up whose slot is kept and drawn disabled
 * ({@code core/.../QuickSlot.java:36-41}; {@code core/.../ui/QuickSlotButton.java:306}).
 *
 * @param item the item's display name, or empty for an empty slot
 * @param placeholder whether the slot holds a placeholder rather than an item; never with an empty slot
 */
public record QuickslotView(String item, boolean placeholder) {

    public QuickslotView {
        item = Canon.text(item, "quickslot item");
        Canon.require(!placeholder || !item.isEmpty(), "an empty quickslot holds no placeholder");
    }
}
