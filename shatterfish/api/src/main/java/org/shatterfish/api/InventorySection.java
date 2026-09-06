package org.shatterfish.api;

import java.util.List;

/**
 * The inventory in the belongings' own iteration order: the equipped items in slot order, then
 * the backpack's contents ({@code core/.../actors/hero/Belongings.java:428-429}, {@code :446-453}).
 * The order is positional, because an {@link ItemRef} is a position in it (ADR-0014, option 11),
 * so the record refuses a list whose slots are out of that order or repeated.
 *
 * @param items the items, equipped first
 */
public record InventorySection(List<ItemView> items) {

    public InventorySection {
        items = Canon.positional(items, "inventory");
        EquipSlot last = EquipSlot.NONE;
        boolean inBackpack = false;
        for (ItemView item : items) {
            if (item.slot() == EquipSlot.NONE) {
                inBackpack = true;
            } else {
                Canon.require(!inBackpack, "an equipped item follows the backpack: " + item.name());
                Canon.require(item.slot().ordinal() > last.ordinal(),
                        "equipped items are in slot order, each slot once: " + item.name() + " in " + item.slot());
                last = item.slot();
            }
        }
    }
}
