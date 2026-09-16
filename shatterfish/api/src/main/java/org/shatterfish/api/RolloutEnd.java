package org.shatterfish.api;

/** How a rollout stopped: at its horizon, at the hero's death, at the win, or at a refused Action. */
public enum RolloutEnd {
    HORIZON, DEATH, WIN, REFUSED
}
