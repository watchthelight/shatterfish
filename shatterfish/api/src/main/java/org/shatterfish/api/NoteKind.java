package org.shatterfish.api;

/**
 * The three kinds of record the journal's notes hold ({@code core/.../journal/Notes.java:145},
 * {@code :296}, {@code :383}): a landmark seen on a floor, keys held for a floor, and a note the
 * player wrote.
 */
public enum NoteKind {
    LANDMARK, KEY, CUSTOM
}
