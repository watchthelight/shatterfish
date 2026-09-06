package org.shatterfish.api;

/**
 * The family an item's sprite and bag place it in, one member per item package of the game at the
 * tag ({@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/}: {@code armor},
 * {@code artifacts}, {@code bags}, {@code bombs}, {@code food}, {@code keys}, {@code potions},
 * {@code quest}, {@code rings}, {@code scrolls}, {@code spells}, {@code stones},
 * {@code trinkets}, {@code wands}, {@code weapon/melee}, {@code weapon/missiles}) plus
 * {@link #SEED} for the plants' seeds ({@code core/.../plants/Plant.java}) and {@link #OTHER} for
 * the rest: the items at the package's root (gold, an ankh, a torch, a waterskin, the amulet),
 * the {@code journal} package's guide pages and the {@code remains} package's remains of a fallen
 * hero. {@code weapon/} itself, where the spirit bow sits, is {@link #WEAPON} like
 * {@code weapon/melee}.
 *
 * <p>The family is what a player sees at a glance, since an unidentified potion is still drawn as
 * a potion; the exact class of an unidentified potion, scroll or ring is what the family must
 * never become (ADR-0006, Items). Story 1.10 does the mapping.
 */
public enum ItemKind {
    WEAPON, MISSILE, ARMOR, WAND, RING, ARTIFACT, TRINKET, POTION, SCROLL, STONE, SEED, SPELL, BOMB, FOOD, KEY, BAG,
    QUEST, OTHER
}
