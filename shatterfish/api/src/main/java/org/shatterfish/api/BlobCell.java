package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;

/**
 * The kinds of blob present on one cell the player can see, by class name, in name order, and
 * never a volume: the emitter draws one particle per cell whatever the amount, and the cell's
 * description names the blob only (ADR-0006; {@code core/.../effects/BlobEmitter.java:59-70}).
 */
public record BlobCell(int cell, List<String> kinds) {

    public BlobCell {
        Canon.cell(cell, "a blob cell");
        kinds = Canon.sorted(kinds, Comparator.naturalOrder(), "kinds");
        Canon.require(!kinds.isEmpty(), "a blob cell names at least one kind");
        Canon.noRepeats(kinds, "kinds");
        for (String kind : kinds) {
            Canon.require(!kind.isEmpty(), "a blob kind has a name");
        }
    }
}
