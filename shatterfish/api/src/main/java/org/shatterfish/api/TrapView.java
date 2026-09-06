package org.shatterfish.api;

/**
 * A trap the player can see: one that is revealed and stands on a cell the fog does not hide
 * (ADR-0006; {@code core/.../levels/traps/Trap.java:62-67}). The kind is its display name; an
 * inactive trap is drawn as one.
 */
public record TrapView(int cell, String kind, boolean active) {

    public TrapView {
        Canon.cell(cell, "a trap");
        kind = Canon.text(kind, "kind");
        Canon.require(!kind.isEmpty(), "a trap has a name");
    }
}
