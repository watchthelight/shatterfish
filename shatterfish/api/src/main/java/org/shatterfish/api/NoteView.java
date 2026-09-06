package org.shatterfish.api;

/**
 * One record of the journal's notes as the notes tab lists it: a landmark on a floor, keys held
 * for a floor with their count, or a note the player wrote with its title and body
 * ({@code core/.../journal/Notes.java:73-100}, {@code :145-151}, {@code :296-306}, {@code :383-420}).
 *
 * @param kind which of the three records this is
 * @param depth the floor the record belongs to
 * @param title the record's title, a landmark's name, a key's name or the note's own title
 * @param text a written note's body; empty for a landmark or a key
 * @param quantity the count of keys, 1 for anything else
 */
public record NoteView(NoteKind kind, int depth, String title, String text, int quantity) {

    public NoteView {
        java.util.Objects.requireNonNull(kind, "kind");
        Canon.require(depth >= 0, "a note's depth is counted from the surface: " + depth);
        title = Canon.text(title, "note title");
        text = Canon.text(text, "note text");
        Canon.require(quantity >= 1, "a note counts at least one: " + quantity);
        Canon.require(kind == NoteKind.CUSTOM || text.isEmpty(), "only a written note has a body");
        Canon.require(kind == NoteKind.KEY || quantity == 1, "only keys are counted");
    }
}
