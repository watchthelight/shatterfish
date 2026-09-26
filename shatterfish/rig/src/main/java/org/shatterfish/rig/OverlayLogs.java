package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

/**
 * The Rig takes no Overlay Run for one of its own (story 5.1's fairness review, ADR-0013).
 *
 * <p>An Overlay Run is played by the embedded driver inside the desktop game, whose extra frames draw
 * from the Run's generator until story 5.13 routes them away, so it is not reproducible from its tuple
 * or its Action list: a named exception to non-negotiable 5. Its log's header says
 * {@code driver: embedded}. Every path by which the Rig reads a Run's log to count, score, calibrate, compare or
 * replay it asks this first, and a log that says it was played in the Overlay is refused by name
 * rather than counted, even when a later line of it is unreadable.
 *
 * <p>The field is a label, not tamper protection. It is chained, so removing it from a log that
 * otherwise verifies breaks the chain; but the chain rules are published, and anyone can rewrite a
 * whole log without it. What it prevents is an honest mistake: an Overlay log dropped into a Rig
 * folder and counted as a Rig Run.
 *
 * <p>The debug views, the death gallery's snapshots ({@code Gallery.snapshot}) and the strategy log
 * ({@code :rig:strategy}), may show an Overlay log: they count and score nothing, and reading what the
 * Brain did in the desktop game is what they are for.
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

    /**
     * Refuses {@code log} if any header it holds says the Overlay's driver played it, reading every
     * record up to the first line that could not be read.
     */
    static void refuse(RunLogReader.Log log, Object where) {
        for (RunLog record : log.records()) {
            if (record instanceof RunLog.Header header) {
                refuse(header, where);
            }
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
