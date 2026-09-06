package org.shatterfish.api;

/**
 * The floor feeling ({@code core/.../levels/Level.java:125-133}) as the player reads it: its line
 * of text is logged on arrival ({@code core/.../scenes/GameScene.java:663-685}) and its title,
 * such as "secrets floor", heads the window the menu pane opens for it
 * ({@code core/.../ui/MenuPane.java:112-115};
 * {@code core/src/main/assets/messages/levels/levels.properties:254-267}). {@link #SECRETS} is that
 * announcement, not the secrets themselves, which stay unrepresentable in {@link Tile}.
 */
public enum Feeling {
    NONE, CHASM, WATER, GRASS, DARK, LARGE, TRAPS, SECRETS
}
