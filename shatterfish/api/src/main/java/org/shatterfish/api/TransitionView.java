package org.shatterfish.api;

import java.util.Objects;

/** Stairs or an exit the player has seen: the cell and the kind of transition drawn there. */
public record TransitionView(int cell, TransitionKind kind) {

    public TransitionView {
        Canon.cell(cell, "a transition");
        Objects.requireNonNull(kind, "kind");
    }
}
