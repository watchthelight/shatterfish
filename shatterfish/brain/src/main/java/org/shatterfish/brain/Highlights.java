package org.shatterfish.brain;

import org.shatterfish.api.Action;

import java.util.List;

/**
 * The map cells a Decision points at (story 4.4, ADR-0011): the ones the Panel highlights and the
 * Replay scrubber shows, recorded so that neither re-derives them.
 *
 * <p>For now, the cell the chosen Action targets, when it targets one. A later Policy that plans a
 * path adds the path's cells; the field is a list for that reason.
 */
final class Highlights {

    private Highlights() {
    }

    /** The cells {@code action} targets: its cell, or none for an Action without one. */
    static List<Integer> of(Action action) {
        return switch (action) {
            case Action.Step step -> List.of(step.cell());
            case Action.MoveTo move -> List.of(move.cell());
            case Action.Attack attack -> List.of(attack.cell());
            case Action.Interact interact -> List.of(interact.cell());
            case Action.OpenChest chest -> List.of(chest.cell());
            case Action.Buy buy -> List.of(buy.cell());
            case Action.Unlock unlock -> List.of(unlock.cell());
            case Action.UseItemAt use -> List.of(use.cell());
            case Action.AbilityAt ability -> List.of(ability.cell());
            case null -> List.of();
            default -> List.of();
        };
    }
}
