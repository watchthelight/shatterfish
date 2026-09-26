package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two things story 5.9 added to the Run log's format: the {@code note} kind and a shadow's {@code skipped}. */
class RunLogNoteTest {

    private static RunLog.Decision decision() {
        return new RunLog.Decision("explore", new RunLog.Choice(new Action.Step(3), 10_000, "frontier"), List.of(),
                List.of(), "explore");
    }

    @Test
    @DisplayName("a note is one line: what was typed is cleaned into one, and a note with a break in it is refused")
    void one_line() {
        assertEquals("go left then down", RunLog.Note.clean("  go left\nthen\tdown \r\n"));
        assertEquals(RunLog.Note.MOST, RunLog.Note.clean("x".repeat(RunLog.Note.MOST + 50)).length());
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Note(1, "two\nlines"));
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Note(1, "   "));
        assertThrows(IllegalArgumentException.class, () -> new RunLog.Note(-1, "before the Run"));
        assertEquals("{\"k\":4,\"t\":\"note\",\"text\":\"why not\"}", RunLogJson.canonical(new RunLog.Note(4, "why not")));
    }

    @Test
    @DisplayName("a shadow in time keeps the bytes it always had; a skipped one says so, and the chain covers it")
    void skipped_is_written_only_when_true() {
        RunLog.Shadow inTime = new RunLog.Shadow(2, decision());
        RunLog.Shadow late = new RunLog.Shadow(2, decision(), true);
        assertFalse(RunLogJson.canonical(inTime).contains("skipped"));
        assertTrue(RunLogJson.canonical(late).contains("\"skipped\":true"));
        assertTrue(!RunLogJson.chain("", inTime).equals(RunLogJson.chain("", late)), "skipped is chained");
        assertTrue(!RunLogJson.chain("", new RunLog.Note(1, "a")).equals(RunLogJson.chain("", new RunLog.Note(1, "b"))),
                "a note is chained");
    }
}
