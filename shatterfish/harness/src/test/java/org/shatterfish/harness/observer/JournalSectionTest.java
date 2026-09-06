package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.NoteView;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The journal's notes are the records the notes tab draws (ADR-0006, Journal): a landmark at its
 * floor, a key with its count, a written note with its body, each with the title the tab shows on
 * a tap ({@code …/windows/WndJournal.java:497-541}).
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class JournalSectionTest {

    private static final long SEED = 16_180_339L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
    }

    @Test
    @DisplayName("a landmark, a counted key and a written note are notes with their kind, depth, title, body and count")
    void the_notes_as_the_tab_draws_them() {
        atTheFirstWait();
        assertEquals(1, Dungeon.depth);
        Observer observer = new Observer();
        List<NoteView> before = observer.journal().notes();

        assertTrue(Notes.add(Notes.Landmark.WELL_OF_HEALTH));
        assertTrue(Notes.add(new IronKey(1)));
        assertTrue(Notes.add(new IronKey(1)), "a second key of the floor counts on the same record (Notes.java:600-611)");
        assertTrue(Notes.add(new Notes.CustomRecord(3, "Beware", "a trap by the door")));
        assertTrue(Notes.add(new Notes.CustomRecord("Plan", "rush the stairs")));

        List<NoteView> notes = observer.journal().notes();
        assertEquals(before.size() + 4, notes.size(), "four records added: " + notes);
        String well = Notes.getRecords(Notes.LandmarkRecord.class).stream()
                .filter(r -> r.title().equals(Messages.get(Notes.Landmark.class, "WELL_OF_HEALTH")))
                .findFirst().orElseThrow().title();
        assertTrue(notes.contains(new NoteView(NoteKind.LANDMARK, 1, well, "", 1)), "the landmark: " + notes);
        Notes.KeyRecord keyRecord = Notes.getRecords(Notes.KeyRecord.class).get(0);
        assertEquals(2, keyRecord.quantity());
        assertTrue(notes.contains(new NoteView(NoteKind.KEY, 1, keyRecord.title(), "", 2)), "the key: " + notes);
        assertTrue(notes.contains(new NoteView(NoteKind.CUSTOM, 3, "Beware", "a trap by the door", 1)),
                "a note written for a floor carries the floor (Notes.java:430-437): " + notes);
        assertTrue(notes.contains(new NoteView(NoteKind.CUSTOM, 0, "Plan", "rush the stairs", 1)),
                "a plain note has no floor: " + notes);

        // The section is canonical: by depth, then kind, title, text and count, whatever the game's order.
        for (int i = 1; i < notes.size(); i++) {
            assertTrue(notes.get(i - 1).depth() <= notes.get(i).depth(), "sorted by depth: " + notes);
        }
    }

    @Test
    @DisplayName("two readings of one wait are one section, records and bytes")
    void determinism() {
        atTheFirstWait();
        assertTrue(Notes.add(Notes.Landmark.GARDEN));
        assertTrue(Notes.add(new IronKey(1)));
        Observer observer = new Observer();
        JournalSection first = observer.journal();
        assertEquals(first, new Observer().journal());
        assertArrayEquals(ObservationCodec.encode(Skeleton.everything(observer)),
                ObservationCodec.encode(Skeleton.everything(new Observer())));
    }
}
