package org.shatterfish.api;

/**
 * The floor feeling the game announces on arrival ({@code core/.../levels/Level.java:125-133}),
 * as the player reads it: a title such as "secrets floor" and a line of text
 * ({@code core/src/main/assets/messages/levels/levels.properties:254-267}). {@link #SECRETS} is
 * that announcement, not the secrets themselves, which stay unrepresentable in {@link Tile}.
 */
public enum Feeling {
    NONE, CHASM, WATER, GRASS, DARK, LARGE, TRAPS, SECRETS
}
