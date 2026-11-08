package org.shatterfish.rig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story 10.11: the thefts and escapes of a Run, counted once from the game log the hero sees. */
class DirectionHarmTheftTest {

    private static final String STOLE = "The thief stole potion of healing from you!";
    private static final String ESCAPED = "The thief gets away with your potion of healing!";

    /** Counts the way {@link DirectionPlay} does: each screen's new lines, over a sequence of log sections. */
    private static int[] count(List<List<String>> screens) {
        List<String> last = List.of();
        int stolen = 0;
        int escaped = 0;
        for (List<String> screen : screens) {
            List<String> fresh = DirectionHarm.added(last, screen);
            stolen += DirectionHarm.stolen(fresh);
            escaped += DirectionHarm.escaped(fresh);
            last = screen;
        }
        return new int[] {stolen, escaped};
    }

    @Test
    @DisplayName("a theft that stays in the log over many screens is one theft, and its escape one escape")
    void counted_once() {
        int[] counts = count(List.of(
                List.of("You see a thief."),
                List.of("You see a thief.", STOLE),
                List.of("You see a thief.", STOLE),
                List.of("You see a thief.", STOLE, "You hit the thief."),
                List.of("You see a thief.", STOLE, "You hit the thief.", ESCAPED),
                List.of("You see a thief.", STOLE, "You hit the thief.", ESCAPED)));
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
    }

    @Test
    @DisplayName("a second theft after the first has scrolled up is counted when the section is full and shifting")
    void shifting_window() {
        List<String> full = new java.util.ArrayList<>();
        full.add(STOLE);
        for (int i = 0; i < 63; i++) {
            full.add("line " + i);
        }
        List<String> shifted = new java.util.ArrayList<>(full.subList(1, full.size()));
        shifted.add(STOLE);
        int[] counts = count(List.of(full, shifted));
        assertEquals(2, counts[0], "the first theft, and the one the shifted section added");
    }

    @Test
    @DisplayName("a Run with a kill of the thief and no escape has a theft and no loss; other messages count for nothing")
    void kept_item() {
        int[] counts = count(List.of(
                List.of("The thief stole shortsword from you!"),
                List.of("The thief stole shortsword from you!", "You defeated crazy thief.", "You see a shortsword.")));
        assertEquals(1, counts[0]);
        assertEquals(0, counts[1]);
    }

    @Test
    @DisplayName("an old harm record of three fields reads as no theft")
    void old_record_reads() {
        assertEquals(0, DirectionHarm.stolen(List.of("The thief is carrying a potion of healing. Stolen obviously.")));
    }
}
