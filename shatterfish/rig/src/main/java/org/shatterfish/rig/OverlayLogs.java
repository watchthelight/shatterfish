package org.shatterfish.rig;

import org.shatterfish.api.RunLog;

/**
 * The Rig takes no Overlay Run for one of its own (story 5.1's fairness review, ADR-0013).
 *
 * <p>An Overlay Run is played by the embedded driver inside the desktop game, whose extra frames draw
 * from the Run's generator until story 5.13 routes them away, so it is not reproducible from its tuple
 * or its Action list: a named exception to non-negotiable 5. Its log's header says
 * {@code driver: embedded}, and that field is chained, so it cannot be stripped without breaking the
 * log. Every path by which the Rig reads a Run's log to count, score, calibrate or show it asks this
 * first, and a log that says it was played in the Overlay is refused by name rather than counted.
 */
final class OverlayLogs {

    private OverlayLogs() {
    }

    /** The refusal, a type of its own so a reader that forgives unreadable logs does not forgive it. */
    static final class Refused extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        Refused(String message) {
            super(message);
        }
    }

    /** Refuses {@code header} if the Overlay's driver played it; {@code where} names the log. */
    static void refuse(RunLog.Header header, Object where) {
        if (header != null && header.embedded()) {
            throw new Refused(where + " was played in the Overlay (driver: embedded), which is not reproducible"
                    + " from its tuple until story 5.13; the Rig counts, scores and publishes only its own Runs");
        }
    }
}
