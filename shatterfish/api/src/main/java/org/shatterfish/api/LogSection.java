package org.shatterfish.api;

import java.util.List;

/**
 * The newest messages of the game log, oldest first, as the game emitted them (ADR-0006, Log):
 * the raw signal every message goes through ({@code core/.../utils/GLog.java:39}), which is the
 * game log non-negotiable 1 names as a source and the reproducible one, since frame timing is not
 * part of the Run tuple. The pane is a view of it: it takes the messages of a frame in one batch,
 * merges same-colour messages, and trims the oldest entries beyond three or five lines of text
 * before the frame is drawn ({@code core/.../ui/GameLog.java:55-131}, {@code :59}, {@code :89},
 * {@code :107-122}), so a burst that exceeds the lines in one frame loses its oldest messages
 * before they are ever drawn, and the Observation may carry a message the pane never showed.
 * {@link #MAX_LINES} bounds the section's size, not the pane's.
 *
 * @param lines the messages, in the order they were emitted
 */
public record LogSection(List<LogLine> lines) {

    /** The most messages an Observation carries. */
    public static final int MAX_LINES = 64;

    public LogSection {
        lines = Canon.positional(lines, "log");
        Canon.require(lines.size() <= MAX_LINES, "the log carries at most " + MAX_LINES + " lines: " + lines.size());
    }
}
