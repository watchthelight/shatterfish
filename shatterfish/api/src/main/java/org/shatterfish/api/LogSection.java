package org.shatterfish.api;

import java.util.List;

/**
 * The newest messages of the game log, oldest first, as the game emitted them (ADR-0006, Log).
 * The pane itself keeps only what fits three or five lines of text and drops the rest as new
 * messages arrive ({@code core/.../ui/GameLog.java:59}, {@code :107-122}), so a player sees only
 * the newest few at once but has seen every message as it arrived; the Observation keeps the last
 * {@link #MAX_LINES} as a bound on its size, not as a claim about the pane.
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
