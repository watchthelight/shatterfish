package org.shatterfish.api;

/**
 * One message of the game log as the game emitted it: the tone its prefix names and the text
 * after the prefix ({@code core/.../utils/GLog.java:32-39}; {@code core/.../ui/GameLog.java:72-87}).
 * The pane merges consecutive lines of one colour and wraps them by UI size on the render thread;
 * the Observation keeps the messages apart (ADR-0006, Log).
 *
 * @param tone the colour the line is drawn in
 * @param text the message, without its prefix
 */
public record LogLine(LogTone tone, String text) {

    public LogLine {
        java.util.Objects.requireNonNull(tone, "tone");
        text = Canon.text(text, "log text");
    }
}
