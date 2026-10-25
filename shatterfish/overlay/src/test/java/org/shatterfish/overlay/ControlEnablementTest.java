package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full enablement matrix of UX-DR7 for every state it gives a Controls row (story 5.6, FR-39): which
 * of Pause and Resume, Next Step, Run N and its count stepper, the speed selector and its interval
 * stepper, Take over and Hand back, and Explain act in RUNNING, PAUSED, HUMAN, THINKING and Run over.
 * The expected sets are written out here from EXPERIENCE.md's State Patterns table, not computed from the
 * code under test, so a change to the matrix is a change to this file.
 *
 * <p>Panel collapsed hides the row and changes nothing in it (the hotkeys act through the same matrix);
 * the states of story 5.15 (Brain error, no valid action, hero busy, no Run) are that story's.
 */
class ControlEnablementTest {

    private static final Set<Control> EVERY = EnumSet.allOf(Control.class);

    /** EXPERIENCE.md, RUNNING: Pause, speed selector, Take over, Explain; Next Step acts as Pause; Run N disabled. */
    private static final Set<Control> RUNNING = EnumSet.of(Control.PAUSE_RESUME, Control.NEXT_STEP, Control.SPEED,
            Control.INTERVAL, Control.TAKE_OVER, Control.EXPLAIN);

    /** PAUSED: Resume, Next Step, Run N, speed selector, Take over, Explain. */
    private static final Set<Control> PAUSED = EVERY;

    /** HUMAN: Hand back and Explain; everything else disabled. */
    private static final Set<Control> HUMAN = EnumSet.of(Control.TAKE_OVER, Control.EXPLAIN);

    /** Run over: every control disabled (the Panel toggle, which is not in this row, stays). */
    private static final Set<Control> OVER = EnumSet.noneOf(Control.class);

    @Test
    @DisplayName("RUNNING, PAUSED, HUMAN and Run over enable exactly the controls EXPERIENCE.md's State Patterns table lists")
    void the_matrix_of_each_state() {
        assertEquals(RUNNING, ControlEnablement.matrix(Mode.RUNNING, false));
        assertEquals(PAUSED, ControlEnablement.matrix(Mode.PAUSED, false));
        assertEquals(HUMAN, ControlEnablement.matrix(Mode.HUMAN, false));
        for (Mode mode : Mode.values()) {
            assertEquals(OVER, ControlEnablement.matrix(mode, true), "a Run that is over, whatever its Mode was: " + mode);
        }
    }

    @Test
    @DisplayName("THINKING changes nothing in the matrix: a Next Step pressed over budget is queued, so it is as enabled as it was")
    void thinking_is_not_a_row_of_its_own() {
        for (Mode mode : Mode.values()) {
            ModeState quiet = new ModeState(mode, SpeedMode.NEXT_STEP, 1.0, 5, 1, false);
            ModeState thinking = new ModeState(mode, SpeedMode.NEXT_STEP, 1.0, 5, 1, true);
            assertEquals(ControlEnablement.matrix(quiet.mode(), quiet.over()),
                    ControlEnablement.matrix(thinking.mode(), thinking.over()), mode.name());
        }
        assertTrue(ControlEnablement.matrix(Mode.PAUSED, false).contains(Control.NEXT_STEP), "PAUSED over budget: queued");
        assertTrue(ControlEnablement.matrix(Mode.RUNNING, false).contains(Control.NEXT_STEP), "RUNNING over budget: pauses");
    }

    @Test
    @DisplayName("Run N and its count are enabled only in PAUSED, so a Run N is never started over a Run N or a human's turn")
    void run_n_only_when_paused() {
        for (Mode mode : Mode.values()) {
            boolean paused = mode == Mode.PAUSED;
            assertEquals(paused, ControlEnablement.matrix(mode, false).contains(Control.RUN_N), mode.name());
            assertEquals(paused, ControlEnablement.matrix(mode, false).contains(Control.RUN_N_COUNT), mode.name());
        }
    }

    @Test
    @DisplayName("HUMAN leaves Pause, Next Step, Run N, the speed selector and both steppers dim; the human plays")
    void human_disables_everything_but_hand_back_and_explain() {
        Set<Control> human = ControlEnablement.matrix(Mode.HUMAN, false);
        for (Control control : Control.values()) {
            boolean expected = control == Control.TAKE_OVER || control == Control.EXPLAIN;
            assertEquals(expected, human.contains(control), control.name());
        }
    }

    @Test
    @DisplayName("with stories 5.7 and 5.8 built, what the Overlay can do is all UX-DR7 describes; a capability that is off dims its control in every state")
    void capabilities() {
        for (Mode mode : Mode.values()) {
            assertEquals(Capabilities.ALL, Capabilities.NOW, "nothing is withheld now");
            Set<Control> now = ControlEnablement.of(mode, false, Capabilities.NOW);
            assertEquals(ControlEnablement.matrix(mode, false), now);
            Set<Control> unpaced = ControlEnablement.of(mode, false, new Capabilities(false, true));
            assertFalse(unpaced.contains(Control.INTERVAL), "a Run that cannot pace has no interval to set: " + mode);
            Set<Control> untaken = ControlEnablement.of(mode, false, new Capabilities(true, false));
            assertFalse(untaken.contains(Control.TAKE_OVER), "a Run that cannot change hands has no Take over: " + mode);
            Set<Control> withheld = EnumSet.copyOf(now);
            withheld.removeAll(untaken);
            assertTrue(EnumSet.of(Control.TAKE_OVER).containsAll(withheld), "nothing else is withheld: " + mode);
        }
    }

    @Test
    @DisplayName("the buttons that change their label do: Pause or Resume, Take over or Hand back")
    void labels_follow_the_mode() {
        assertEquals("Pause", ControlEnablement.pauseLabel(Mode.RUNNING));
        assertEquals("Resume", ControlEnablement.pauseLabel(Mode.PAUSED));
        assertEquals("Resume", ControlEnablement.pauseLabel(Mode.HUMAN));
        assertEquals("Take over", ControlEnablement.takeOverLabel(Mode.RUNNING));
        assertEquals("Take over", ControlEnablement.takeOverLabel(Mode.PAUSED));
        assertEquals("Hand back", ControlEnablement.takeOverLabel(Mode.HUMAN));
    }
}
