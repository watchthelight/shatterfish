package org.shatterfish.api;

/**
 * A potion, scroll or ring identified in this Run, by the name every item of that kind now shows
 * ({@code core/.../items/potions/Potion.java:402-404}, and the scrolls' and rings' equivalents;
 * ADR-0006, Known appearances). The cross-Run catalog is not this.
 *
 * @param kind {@link ItemKind#POTION}, {@link ItemKind#SCROLL} or {@link ItemKind#RING}
 * @param name the item's true name
 */
public record KnownAppearance(ItemKind kind, String name) {

    public KnownAppearance {
        java.util.Objects.requireNonNull(kind, "kind");
        Canon.require(kind == ItemKind.POTION || kind == ItemKind.SCROLL || kind == ItemKind.RING,
                "only potions, scrolls and rings have appearances to know: " + kind);
        name = Canon.text(name, "known item name");
        Canon.require(!name.isEmpty(), "a known item has a name");
    }
}
