package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One item as the inventory window and the item window show it (ADR-0006, Items). Paths
 * abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}.
 *
 * <p>The name is what the item shows, which for an unidentified potion, scroll or ring is its
 * appearance ({@code …/items/Item.java:483-499}); the exact class behind an appearance has no
 * field here, and neither does an unknown level or curse.
 *
 * @param kind the item's family, as its sprite and bag show it
 * @param name the display name ({@code Item.java:497-499})
 * @param quantity the quantity ({@code Item.java:538})
 * @param levelKnown whether the level is shown ({@code Item.java:86})
 * @param visiblyUpgraded the level shown after the name, 0 unless the level is known
 *                        ({@code Item.java:433-439}, {@code :483-495})
 * @param cursedKnown whether the curse state is shown ({@code Item.java:89})
 * @param visiblyCursed whether the item is shown cursed, never unless the curse state is known
 *                      ({@code Item.java:441-443})
 * @param status the status text drawn on the sprite, a quantity, a charge count, or empty
 *               ({@code Item.java:570-572}; {@code …/items/wands/Wand.java:336-343})
 * @param slot the equipment slot the item sits in, or {@link EquipSlot#NONE}
 * @param actions the actions the item window offers, by name ({@code Item.java:110-115};
 *                {@code …/windows/WndUseItem.java:54-76})
 * @param defaultAction the action a tap on the quickslot takes ({@code Item.java:179-181}), or empty
 */
public record ItemView(ItemKind kind, String name, int quantity, boolean levelKnown, int visiblyUpgraded,
                       boolean cursedKnown, boolean visiblyCursed, String status, EquipSlot slot, List<String> actions,
                       String defaultAction) {

    public ItemView {
        Objects.requireNonNull(kind, "kind");
        name = Canon.text(name, "item name");
        Canon.require(!name.isEmpty(), "an item has a name");
        Canon.require(quantity >= 1, "an item's quantity is at least 1: " + quantity);
        Canon.require(levelKnown || visiblyUpgraded == 0, "an item of unknown level shows no level");
        Canon.require(cursedKnown || !visiblyCursed, "an item of unknown curse state is not shown cursed");
        status = Canon.text(status, "item status");
        Objects.requireNonNull(slot, "slot");
        actions = Canon.sorted(actions, Comparator.naturalOrder(), "item actions");
        Canon.noRepeats(actions, "item actions");
        for (String action : actions) {
            Canon.require(!action.isEmpty(), "an item action has a name");
        }
        defaultAction = Canon.text(defaultAction, "default action");
    }
}
