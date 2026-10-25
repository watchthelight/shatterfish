package org.shatterfish.overlay;

import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.observer.OracleView;

/**
 * Where an oracle Run keeps what the oracle saw beside the Observation it handed the Brain, for the Panel
 * to mark and for nothing else (story 5.12, FR-43).
 *
 * <p>The launcher's oracle door puts each read here as it makes it ({@link ShatterfishLauncher#observer});
 * the dock asks for marks once a frame. It is made only for an oracle Run and a fair Run has none, so no code
 * path of a fair Run can reach a hidden fact through it. The view is held beside the exact Observation it was
 * read with, and {@link #marks} answers only for the same screen, so a mark is never
 * drawn for a screen it was not read at: a floor change, a turn on, or a Panel still showing the last wait's
 * screen get nothing rather than a stale enemy. The Brain is never handed this: it takes the Observation
 * alone, and no type the Run log or the Snapshot carries can hold an {@link OracleView}
 * ({@code OracleGateTest}).
 */
final class OracleSidecar {

    /** One read: the marked Observation and the view read with it. */
    private record Held(Observation observation, OracleView view) {
    }

    private volatile Held held;

    /** Keeps the latest read, replacing the one before. */
    void take(Observation observation, OracleView view) {
        held = new Held(observation, view);
    }

    /**
     * Whether {@code shown} is the screen {@code read} was: the same instance, or the same header, map, actors and
     * hero (the Run may have filled in the valid Actions since). Any turn the hero takes changes at least one.
     */
    private static boolean sameScreen(Observation read, Observation shown) {
        return read == shown || (read.header().equals(shown.header()) && read.hero().equals(shown.hero())
                && read.actors().equals(shown.actors()) && read.map().equals(shown.map()));
    }

    /** The marks for the screen the Panel shows now; none unless it is the screen the view was read at. */
    OracleMarkContent.Marks marks(Observation shown, BeliefSummary summary) {
        Held now = held;
        if (now == null || shown == null || !sameScreen(now.observation(), shown)) {
            return OracleMarkContent.Marks.NONE;
        }
        return OracleMarkContent.of(now.view(), shown, summary);
    }
}
