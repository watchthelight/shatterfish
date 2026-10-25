package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cells the dungeon view outlines for a Decision, and in which role (story 5.10, FR-41, UX-DR10): a
 * pure function from the Decision record and the highlight cells its wait record carries.
 *
 * <p>Nothing is computed here that the record does not already say. The wait record's {@code highlights}
 * are the planned path's cells in walking order and then the target last ({@code Deliberator.lastHighlights},
 * ADR-0011), so the last is the target and the rest are the path. The considered cells are the cells the
 * Decision's alternatives point at, read off the {@link RunLog.Decision} the Decision card shows. No path is
 * searched for, no cell is looked up on the map, and a cell the Brain could not have seen cannot appear,
 * because the record is made from the Observation (non-negotiable 1).
 */
final class MapHighlightContent {

    private MapHighlightContent() {
    }

    /** Which kind of outline a cell gets; each has its own colour and shape (DESIGN.md Map highlight). */
    enum Role {
        /** The planned path, in the Chosen colour. */
        PATH(0xFFB347),
        /** The target cell, in the Danger colour, drawn heavier so the colour is not alone in saying so. */
        TARGET(0xFF5555),
        /** A cell an alternative points at, in the Alternative colour, drawn as corner brackets. */
        CONSIDERED(0x7FB8FF);

        private final int rgb;

        Role(int rgb) {
            this.rgb = rgb;
        }

        /** The documented colour, as 24-bit RGB. */
        int rgb() {
            return rgb;
        }
    }

    /** The cells to outline: the path (walking order), the target, and the considered cells. Disjoint. */
    record Cells(List<Integer> path, List<Integer> target, List<Integer> considered) {

        static final Cells NONE = new Cells(List.of(), List.of(), List.of());

        Cells {
            path = List.copyOf(path);
            target = List.copyOf(target);
            considered = List.copyOf(considered);
        }

        boolean isEmpty() {
            return path.isEmpty() && target.isEmpty() && considered.isEmpty();
        }

        /** The cells of one role. */
        List<Integer> of(Role role) {
            return switch (role) {
                case PATH -> path;
                case TARGET -> target;
                case CONSIDERED -> considered;
            };
        }
    }

    /**
     * What to outline for {@code decision}, whose wait record carries {@code highlights}. Nothing without a
     * Decision. A cell is outlined once, in the strongest role the record gives it: target, then path, then
     * considered.
     */
    static Cells of(RunLog.Decision decision, List<Integer> highlights) {
        if (decision == null) {
            return Cells.NONE;
        }
        List<Integer> path = new ArrayList<>();
        List<Integer> target = new ArrayList<>();
        Set<Integer> taken = new LinkedHashSet<>();
        if (!highlights.isEmpty()) {
            int last = highlights.get(highlights.size() - 1);
            target.add(last);
            taken.add(last);
            for (int cell : highlights.subList(0, highlights.size() - 1)) {
                if (taken.add(cell)) {
                    path.add(cell);
                }
            }
        }
        List<Integer> considered = new ArrayList<>();
        for (RunLog.Choice alternative : decision.alternatives()) {
            int cell = cellOf(alternative.action());
            if (cell >= 0 && taken.add(cell)) {
                considered.add(cell);
            }
        }
        return new Cells(path, target, considered);
    }

    /**
     * The cell {@code action} points at, or -1 for an Action that points at none. Every kind is named, with
     * no default, so a kind added to the sealed {@link Action} does not compile here until someone has said
     * whether it points at a cell (the discipline {@code brain}'s {@code Highlights} and {@link ActionText}
     * hold themselves to).
     */
    static int cellOf(Action action) {
        return switch (action) {
            case null -> -1;
            case Action.Step step -> step.cell();
            case Action.MoveTo move -> move.cell();
            case Action.Attack attack -> attack.cell();
            case Action.Interact interact -> interact.cell();
            case Action.OpenChest chest -> chest.cell();
            case Action.Buy buy -> buy.cell();
            case Action.Unlock unlock -> unlock.cell();
            case Action.UseItemAt use -> use.cell();
            case Action.AbilityAt ability -> ability.cell();
            case Action.PickUp pickUp -> -1;
            case Action.Descend descend -> -1;
            case Action.Ascend ascend -> -1;
            case Action.UseItem use -> -1;
            case Action.UseItemOn use -> -1;
            case Action.Rest rest -> -1;
            case Action.Search search -> -1;
            case Action.Talent talent -> -1;
            case Action.Ability ability -> -1;
            case Action.AnswerPrompt answer -> -1;
            case Action.DismissPrompt dismiss -> -1;
            case Action.Wait wait -> -1;
        };
    }

    /**
     * Whether a Run shows highlights at all: never in HUMAN mode (UX-DR10), and only for the Decision the
     * hero has not yet acted on, which the snapshot flags as awaiting.
     */
    static boolean shown(ModeState.Mode mode, boolean awaiting) {
        return mode != ModeState.Mode.HUMAN && awaiting;
    }
}
