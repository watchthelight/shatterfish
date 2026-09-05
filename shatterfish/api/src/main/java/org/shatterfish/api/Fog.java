package org.shatterfish.api;

/**
 * How much of a cell the player can see, in the four levels the fog of war draws
 * ({@code core/.../tiles/FogOfWar.java:288-299}): in view now, seen before, revealed by mapping,
 * or never seen. The Observer reads {@code heroFOV}, {@code visited} and {@code mapped} in that
 * order (ADR-0006); an {@link #UNKNOWN} cell carries {@link Tile#NONE}.
 */
public enum Fog {
    VISIBLE, VISITED, MAPPED, UNKNOWN
}
