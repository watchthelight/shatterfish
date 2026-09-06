package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;

/**
 * This Run's journal: the notes tab's records, and the potions, scrolls and rings identified so
 * far (ADR-0006, Journal and Known appearances). The guide's pages and the bestiary are not here:
 * the first is static text the Codex carries and the second is cross-Run state.
 *
 * @param notes the landmarks, keys and written notes, by depth then kind, title, text and count
 * @param known the identified potions, scrolls and rings, by kind then name
 */
public record JournalSection(List<NoteView> notes, List<KnownAppearance> known) {

    public JournalSection {
        notes = Canon.sorted(notes, Comparator.comparingInt(NoteView::depth)
                .thenComparing(note -> note.kind().name())
                .thenComparing(NoteView::title)
                .thenComparing(NoteView::text)
                .thenComparingInt(NoteView::quantity), "notes");
        Canon.noRepeats(notes, "notes");
        known = Canon.sorted(known, Comparator.comparing((KnownAppearance item) -> item.kind().name())
                .thenComparing(KnownAppearance::name), "known appearances");
        Canon.noRepeats(known, "known appearances");
    }
}
