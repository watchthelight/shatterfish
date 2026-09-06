package org.shatterfish.api;

/**
 * Where an equipped item sits: the hero's six equipment fields
 * ({@code core/.../actors/hero/Belongings.java:82-86}, {@code :95}), or {@link #NONE} for an item in
 * the backpack.
 */
public enum EquipSlot {
    NONE, WEAPON, ARMOR, ARTIFACT, MISC, RING, SECOND_WEAPON
}
