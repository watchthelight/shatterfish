package org.shatterfish.api;

/**
 * The emote icon a sprite shows, or none: the four the game draws
 * ({@code core/.../effects/EmoIcon.java:78}, {@code :102}, {@code :126}, {@code :150}). It is what
 * is drawn, not the mob's state, which the Observer never reads (ADR-0006).
 */
public enum Emote {
    NONE, SLEEP, ALERT, LOST, INVESTIGATE
}
