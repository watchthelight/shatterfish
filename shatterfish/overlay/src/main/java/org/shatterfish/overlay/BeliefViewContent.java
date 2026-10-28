package org.shatterfish.overlay;

import org.shatterfish.api.BeliefView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Beliefs view's content (story 8.6, FR-46): everything the Brain believes at a wait, most ambiguous
 * appearance first, then the floor's facts, the monsters remembered out of view, and each guaranteed drop's
 * counter; and the rules for when the view is shown and how it scrolls.
 *
 * <p>A pure function of a {@link BeliefView} and the oracle rows, in the style of {@link BeliefSummaryContent}:
 * a test holds it against a constructed view without booting the game. The oracle rows are made by the dock
 * for an oracle Run only ({@link OracleMarkContent#beliefRows}); a fair Run passes none, so the same view has
 * no such line, and a row is placed under the appearance it is for, with its {@code oracle} prefix, never in
 * place of the Brain's own line.
 */
final class BeliefViewContent {

    /** Nothing to show yet (UX-DR14: absence is a word too). */
    static final String NOTHING_YET = BeliefSummaryContent.NOTHING_YET;
    static final String UNKNOWN_ITEMS = "unknown items, least sure first";
    static final String FLOOR = "this floor holds";
    static final String UNSEEN = "remembered, out of view";
    static final String DROPS = "guaranteed drops";

    /** One line of the view; a heading is drawn in the muted ink, and any other line in the ordinary one. */
    record Line(String text, boolean heading) {
    }

    /** The view, built: its lines and the hash it was built from (the key that tells a new Belief from the same one). */
    record Content(String hash, List<Line> lines) {

        Content {
            lines = List.copyOf(lines);
        }

        /** Just the texts, in order. */
        List<String> texts() {
            return lines.stream().map(Line::text).toList();
        }
    }

    private BeliefViewContent() {
    }

    /**
     * Whether the view is on screen: the person has opened it and the Controls matrix lets it act now, which
     * is PAUSED and HUMAN (and a replay, which joins the matrix with its Mode) and never RUNNING, where the
     * Brain moves on and a view of one wait would be stale at the next frame.
     */
    static boolean shown(boolean open, java.util.Set<ControlEnablement.Control> enabled) {
        return open && enabled.contains(ControlEnablement.Control.BELIEFS);
    }

    /** {@code view}'s content with no oracle rows (a fair Run). */
    static Content of(BeliefView view) {
        return of(view, Map.of());
    }

    /**
     * {@code view}'s content. {@code oracleRows} maps an appearance's label to its oracle row (already
     * prefixed); a label not in the map has none.
     */
    static Content of(BeliefView view, Map<String, String> oracleRows) {
        if (view == null) {
            return new Content("", List.of(new Line(NOTHING_YET, false)));
        }
        List<Line> lines = new ArrayList<>();
        List<BeliefView.Appearance> sorted = new ArrayList<>(view.appearances());
        sorted.sort(Comparator.comparingDouble(BeliefViewContent::top).thenComparing(BeliefView.Appearance::label));
        if (!sorted.isEmpty()) {
            lines.add(new Line(UNKNOWN_ITEMS, true));
            for (BeliefView.Appearance appearance : sorted) {
                lines.add(new Line(appearance.label() + ": " + candidates(appearance), false));
                String oracle = oracleRows.get(appearance.label());
                if (oracle != null) {
                    lines.add(new Line(oracle, false));
                }
            }
        }
        section(lines, FLOOR, view.floor());
        section(lines, UNSEEN, view.monsters());
        section(lines, DROPS, view.chapters());
        if (lines.isEmpty()) {
            lines.add(new Line(NOTHING_YET, false));
        }
        return new Content(view.hash(), lines);
    }

    private static void section(List<Line> lines, String heading, List<String> rows) {
        if (rows.isEmpty()) {
            return;
        }
        lines.add(new Line(heading, true));
        rows.forEach(row -> lines.add(new Line(row, false)));
    }

    /** An appearance's candidates in the Brain's order, each with its probability, as the summary words one. */
    private static String candidates(BeliefView.Appearance appearance) {
        List<String> parts = new ArrayList<>();
        for (BeliefView.Candidate candidate : appearance.candidates()) {
            parts.add(candidate.name() + " " + Columns.probability(candidate.probability()));
        }
        return String.join(", ", parts);
    }

    /** How sure the Brain is of an appearance: its top candidate's probability (the summary's own measure). */
    private static double top(BeliefView.Appearance appearance) {
        return appearance.candidates().isEmpty() ? 1.0 : appearance.candidates().get(0).probability();
    }

    /**
     * Whether a scroll position is kept when the content changes. The same Belief (same hash) keeps the place
     * the person scrolled to, as a re-draw of the same wait must; a new Belief starts at the top, where the
     * most ambiguous appearance is.
     */
    static boolean keepsPlace(String shownHash, String newHash) {
        return Objects.equals(shownHash, newHash);
    }

    /** The furthest the content scrolls: its height less the viewport's, never negative. */
    static float maxScroll(float contentHeight, float viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /** {@code scroll} held between the top and {@link #maxScroll}. */
    static float clamp(float scroll, float contentHeight, float viewportHeight) {
        return Math.max(0, Math.min(scroll, maxScroll(contentHeight, viewportHeight)));
    }

    /** Scrolls by {@code delta} (a wheel notch or a drag), held in range. */
    static float scrolled(float scroll, float delta, float contentHeight, float viewportHeight) {
        return clamp(scroll + delta, contentHeight, viewportHeight);
    }
}
