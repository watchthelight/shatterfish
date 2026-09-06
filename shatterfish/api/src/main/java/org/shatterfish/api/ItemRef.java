package org.shatterfish.api;

/**
 * A stable reference to an inventory item for an {@link Action} (ADR-0014, option 11): the item's
 * position in the inventory section, which is the belongings' iteration order, plus its display
 * name and quantity, so that an executor re-walking that order can detect a desync instead of
 * acting on the wrong item. An Observation refuses an action whose reference does not match its
 * inventory.
 *
 * @param index the position in {@link InventorySection#items()}
 * @param name the item's display name, as the inventory lists it
 * @param quantity the item's quantity, as the inventory lists it
 */
public record ItemRef(int index, String name, int quantity) {

    public ItemRef {
        Canon.require(index >= 0, "an item reference is an index into the inventory: " + index);
        name = Canon.text(name, "item reference name");
        Canon.require(!name.isEmpty(), "an item reference names its item");
        Canon.require(quantity >= 1, "an item reference has a quantity of at least one: " + quantity);
    }

    /** Whether this reference names {@code item}: same display name, same quantity. */
    public boolean matches(ItemView item) {
        return item.name().equals(name) && item.quantity() == quantity;
    }
}
