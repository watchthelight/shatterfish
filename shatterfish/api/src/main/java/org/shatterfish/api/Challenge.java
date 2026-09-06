package org.shatterfish.api;

/**
 * The challenges a Run can carry, one per flag the game defines
 * ({@code core/.../Challenges.java:30-38}); the player chose them and the hero window lists them.
 */
public enum Challenge {
    NO_FOOD, NO_ARMOR, NO_HEALING, NO_HERBALISM, SWARM_INTELLIGENCE, DARKNESS, NO_SCROLLS,
    CHAMPION_ENEMIES, STRONGER_BOSSES
}
