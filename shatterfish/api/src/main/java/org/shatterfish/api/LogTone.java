package org.shatterfish.api;

/**
 * The colour a log line is drawn in, from the prefix the game puts on the message
 * ({@code core/.../utils/GLog.java:32-35}; {@code core/.../ui/GameLog.java:72-87}): none, positive
 * (green), negative (red), warning (orange), or highlight (drawn neutral).
 */
public enum LogTone {
    PLAIN, POSITIVE, NEGATIVE, WARNING, HIGHLIGHT
}
