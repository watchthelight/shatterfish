package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.agent.RunEnding;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Run that is over, and a Run that is resumed (story 5.14, UX-DR7, ADR-0013): the Mode strip's line, the
 * controls, the Run log notice's wrapping, and the flag that resumes.
 */
class RunOverContentTest {

    private static ModeState over(RunEnding ending) {
        return new ModeState(ModeState.Mode.PAUSED, ModeState.SpeedMode.FAST, 0, 1_842, 5, false, true, ending);
    }

    @Test
    @DisplayName("the strip of a finished Run reads RUN OVER with the cause, the turn and the floor")
    void the_strip_reads_run_over() {
        RunEnding ending = new RunEnding("killed by Goo", 1_842, 5, Path.of("overlay-runs", "run.jsonl"));
        assertEquals("RUN OVER: killed by Goo, turn 1,842, floor 5", ModeStripContent.text(over(ending)));
    }

    @Test
    @DisplayName("a finished Run with nothing to read says only RUN OVER, and its colour is neither a Mode's nor red")
    void no_ending_no_cause() {
        assertEquals("RUN OVER", ModeStripContent.text(over(null)));
        int colour = ModeStripContent.color(over(null));
        assertEquals(ModeStripContent.OVER_COLOR, colour);
        for (ModeState.Mode mode : ModeState.Mode.values()) {
            assertNotEquals(ModeStripContent.color(mode), colour);
        }
        assertNotEquals(0xFF4444, colour);
    }

    @Test
    @DisplayName("a Run that plays is unchanged by the ending, and its colour is its Mode's")
    void a_playing_run_is_unchanged() {
        ModeState playing = new ModeState(ModeState.Mode.RUNNING, ModeState.SpeedMode.FAST, 0, 7, 2, false);
        assertTrue(ModeStripContent.text(playing).startsWith("RUNNING"));
        assertEquals(ModeStripContent.RUNNING_COLOR, ModeStripContent.color(playing));
    }

    @Test
    @DisplayName("every control but the Panel toggle is disabled when the Run is over")
    void controls_are_off() {
        for (ModeState.Mode mode : ModeState.Mode.values()) {
            assertTrue(ControlEnablement.matrix(mode, true).isEmpty(), mode + ": " + ControlEnablement.matrix(mode, true));
        }
    }

    @Test
    @DisplayName("a long path is cut into rows with real newlines, every character kept")
    void a_path_wraps() {
        String path = "Run log: C:/Users/someone/overlay-runs/seed-31415926-warrior-5a175a17.jsonl";
        String wrapped = GoalLine.wrap(path, 20);
        assertEquals(path, wrapped.replace("\n", ""));
        for (String row : wrapped.split("\n")) {
            assertTrue(row.length() <= 20, row);
        }
        assertEquals("short", GoalLine.wrap("short", 20));
        assertThrows(IllegalArgumentException.class, () -> GoalLine.wrap("x", 0));
    }

    @Test
    @DisplayName("--resume takes the Run from its marker and log, and refuses a flag that would change the Run")
    void resume_flag(@TempDir Path profile) {
        assertTrue(LaunchOptions.KNOWN.contains("--resume"));
        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--resume", profile.toString(), "--seed", "1"}));
        assertTrue(changed.getMessage().contains("--seed"), changed.getMessage());
        IllegalStateException bare = assertThrows(IllegalStateException.class,
                () -> LaunchOptions.parse(new String[] {"--resume", profile.toString()}));
        assertTrue(bare.getMessage().contains("cannot be resumed"), bare.getMessage());
    }
}
