package org.shatterfish.overlay;

import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.observer.OracleView;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * What an oracle Run adds to the instrument besides its border and its label (story 5.12, FR-43, UX-DR11):
 * oracle rows for the Belief summary, and the cells of the enemies the screen does not show, for the map.
 *
 * <p>A pure function of the oracle's sidecar ({@link OracleView}), the Observation on the Panel and the Belief
 * summary beside it. Everything it returns is plain text and cell numbers, so nothing downstream of it
 * (the Panel, the map layer) holds a hidden fact in a type of its own. This class and {@link OracleSidecar}
 * are the only ones in the Overlay that name an {@link OracleView} (the launcher aside;
 * {@code OracleMarkingTest}); no row or cell computed here ever reaches a Brain, a Decision, a Run log
 * record or the Snapshot, because it is made in the frame the Panel is drawn, from a value only the render
 * thread holds.
 */
final class OracleMarkContent {

    /** The prefix every oracle row carries in the Belief summary (UX-DR11), so a hidden fact is never read as a belief. */
    static final String PREFIX = "oracle: ";
    /** How many unseen enemies get a row of their own; the rest are counted, so the Panel keeps its room. */
    static final int MOB_ROWS = 4;

    private OracleMarkContent() {
    }

    /**
     * @param rows   the oracle rows, each beginning with {@link #PREFIX}: the true identity behind each unknown item
     *               the Belief summary shows, then the unseen enemies
     * @param unseen the cells of the enemies the screen does not show (outside view, or a mimic drawn as a chest),
     *               ascending and without repeats
     * @param mapWidth the width of the map the cells are on
     */
    record Marks(List<String> rows, List<Integer> unseen, int mapWidth) {

        static final Marks NONE = new Marks(List.of(), List.of(), 0);

        Marks {
            rows = List.copyOf(rows);
            unseen = List.copyOf(unseen);
        }
    }

    /** The marks for {@code view} as {@code shown} and {@code summary} are on the Panel; {@link Marks#NONE} without a map. */
    static Marks of(OracleView view, Observation shown, BeliefSummary summary) {
        if (view == null || shown == null || shown.map().width() <= 0) {
            return Marks.NONE;
        }
        int width = shown.map().width();
        List<String> rows = new ArrayList<>();
        if (summary != null) {
            for (BeliefSummary.Item item : summary.items()) {
                for (OracleView.Identity identity : view.identities()) {
                    if (!identity.appearance().isEmpty()
                            && item.label().toLowerCase().contains(identity.appearance().toLowerCase())) {
                        rows.add(PREFIX + item.label() + " = " + identity.trueName());
                        break;
                    }
                }
            }
        }
        TreeSet<Integer> cells = new TreeSet<>();
        int unseen = 0;
        for (OracleView.Presence mob : view.mobs()) {
            if (mob.seen()) {
                continue;
            }
            cells.add(mob.cell());
            if (unseen++ < MOB_ROWS) {
                rows.add(PREFIX + mob.name() + " at " + (mob.cell() % width) + "," + (mob.cell() / width) + ", unseen");
            }
        }
        if (unseen > MOB_ROWS) {
            rows.add(PREFIX + "+" + (unseen - MOB_ROWS) + " more unseen");
        }
        return new Marks(rows, List.copyOf(cells), width);
    }
}
