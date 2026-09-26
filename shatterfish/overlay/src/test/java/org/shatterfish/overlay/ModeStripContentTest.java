package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.agent.EmbeddedRun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Mode strip's content (story 5.3, FR-38, UX-DR3, UX-DR5, UX-DR14): the mode word in its colour,
 * the speed mode with its interval, the turn and the floor, all against constructed {@link ModeState}s
 * rather than a live Run, since {@link ModeStripContent} is a pure function of one.
 *
 * <p>Expected lines are built from {@link Columns} rather than hand-counted spaces, so a test failure
 * means the production format changed, not a miscounted literal; the two-space field separator and the
 * "turn " / "floor " labels are written out, since those are the mockup's own convention
 * ({@code key-panel-paused.html}: {@code PAUSED  Next Step  turn 14  floor 2}) and are what a mutant
 * changing them should be caught by.
 */
class ModeStripContentTest {

    private static ModeState state(ModeState.Mode mode, ModeState.SpeedMode speed, double interval, int turn,
                                    int floor, boolean thinking) {
        return new ModeState(mode, speed, interval, turn, floor, thinking);
    }

    private static String line(String mode, String speedText, int turn, int floor, boolean thinking) {
        String line = mode + "  " + speedText + "  turn " + Columns.number(turn, ModeStripContent.TURN_WIDTH)
                + "  floor " + Columns.number(floor, ModeStripContent.FLOOR_WIDTH);
        return thinking ? line + "  THINKING" : line;
    }

    @Test
    @DisplayName("every Mode is a word in the line, not only a colour, and each has its own colour (UX-DR14)")
    void every_mode_is_a_word() {
        assertEquals(line("RUNNING", "Fast", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 1, 1, false)));
        assertEquals(line("PAUSED", "Fast", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.PAUSED, ModeState.SpeedMode.FAST, 0, 1, 1, false)));
        assertEquals(line("HUMAN", "Fast", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.HUMAN, ModeState.SpeedMode.FAST, 0, 1, 1, false)));
        int running = ModeStripContent.color(ModeState.Mode.RUNNING);
        int paused = ModeStripContent.color(ModeState.Mode.PAUSED);
        int human = ModeStripContent.color(ModeState.Mode.HUMAN);
        assertEquals(0x66DD66, running, "DESIGN.md colors.running");
        assertEquals(0xFFD34D, paused, "DESIGN.md colors.paused");
        assertEquals(0x7FB8FF, human, "DESIGN.md colors.human");
        assertNotEquals(running, paused);
        assertNotEquals(paused, human);
        assertNotEquals(running, human);
    }

    @Test
    @DisplayName("a speed mode without an interval shows only its word; one that has an interval shows it, formatted to one decimal")
    void speed_mode_and_interval() {
        assertEquals(line("RUNNING", "Fast", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 9, 1, 1, false)),
                "Fast shows no interval, even though one was given: it never has one (UX-DR6)");
        assertEquals(line("RUNNING", "Next Step", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.NEXT_STEP, 9, 1, 1, false)),
                "Next Step shows no interval either");
        assertEquals(line("RUNNING", "Human 1.0s", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.HUMAN_PLAY, 1.0, 1, 1, false)));
        assertEquals(line("RUNNING", "Human 2.5s", 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.HUMAN_PLAY, 2.5, 1, 1, false)));
        // The placeholder speed shows an em dash, not a number: 0.0s would read as a measurement
        // nobody made (the review that found "normal 0.0s" on screen).
        assertEquals(line("RUNNING", "normal " + ModeStripContent.NO_INTERVAL_YET, 1, 1, false),
                ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.NORMAL,
                        ModeState.PLACEHOLDER_INTERVAL, 1, 1, false)));
    }

    @Test
    @DisplayName("THINKING is a word in the line, present only while the Brain is deciding")
    void thinking_marker() {
        String thinking = ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 1, 1, true));
        String idle = ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 1, 1, false));
        assertEquals(line("RUNNING", "Fast", 1, 1, true), thinking);
        assertFalse(idle.contains("THINKING"));
        assertEquals(thinking, idle + "  THINKING");
    }

    @Test
    @DisplayName("the turn and the floor are shown, and their columns are a fixed width whatever the digit count (UX-DR5)")
    void turn_and_floor_are_fixed_width_columns() {
        String small = ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 3, 1, false));
        String big = ModeStripContent.text(state(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 12_345, 20, false));
        assertEquals(line("RUNNING", "Fast", 3, 1, false), small);
        assertEquals(line("RUNNING", "Fast", 12_345, 20, false), big);
        // Same column: "turn " starts at the same offset in both lines, whatever the digit count, and
        // so does "floor " right after it -- the property UX-DR5 asks for.
        assertEquals(small.indexOf("turn "), big.indexOf("turn "));
        assertEquals(small.indexOf("floor "), big.indexOf("floor "));
        assertEquals(Columns.number(3, ModeStripContent.TURN_WIDTH).length(),
                Columns.number(12_345, ModeStripContent.TURN_WIDTH).length(), "one turn column width");
        assertTrue(Columns.number(3, ModeStripContent.TURN_WIDTH).startsWith(" "), "the shorter number is padded");
        assertFalse(Columns.number(12_345, ModeStripContent.TURN_WIDTH).startsWith(" "),
                "the column is exactly as wide as the widest turn it was sized for");
    }

    @Test
    @DisplayName("ModeState.of reads the placeholder Mode and speed, and the Run's real turn, floor and thinking flag")
    void of_a_snapshot() {
        ModeState none = ModeState.of(null);
        assertEquals(ModeState.Mode.RUNNING, none.mode());
        assertEquals(ModeState.SpeedMode.NORMAL, none.speed());
        assertEquals(0, none.turn());
        assertEquals(0, none.floor());
        assertFalse(none.thinking());

        EmbeddedRun.Snapshot snapshot = new EmbeddedRun.Snapshot(null, 42, 3, null, EmbeddedRun.State.THINKING);
        ModeState mode = ModeState.of(snapshot);
        assertEquals(ModeState.Mode.RUNNING, mode.mode());
        assertEquals(ModeState.SpeedMode.NORMAL, mode.speed());
        assertEquals(42, mode.turn());
        assertEquals(3, mode.floor());
        assertTrue(mode.thinking());

        EmbeddedRun.Snapshot playing = new EmbeddedRun.Snapshot(null, 42, 3, null, EmbeddedRun.State.PLAYING);
        assertFalse(ModeState.of(playing).thinking());
    }
}
