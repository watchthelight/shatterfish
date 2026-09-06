package org.shatterfish.api;

/**
 * The hero's hunger as the HUD shows it: no icon, the hungry icon, or the starving icon
 * ({@code core/.../actors/buffs/Hunger.java:179-187}), never the value behind them (ADR-0006).
 */
public enum Hunger {
    NONE, HUNGRY, STARVING
}
