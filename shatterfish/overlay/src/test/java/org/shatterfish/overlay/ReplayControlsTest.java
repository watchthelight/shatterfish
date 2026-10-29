package org.shatterfish.overlay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The REPLAY state of the Panel (story 8.5, FR-46), against a Run that is a list of calls: it opens stopped at its
 * log, it is its own Mode with its own enablement row (every control of a PAUSED Run but Take over, since nobody
 * plays a replay), Next Step and Run N give credits as they do for a Brain's Run, Resume plays at the speed mode,
 * and no Pause-on condition ever stops it.
 */
class ReplayControlsTest {

    private static final int WAITS = 340;

    private static Observation base;

    @BeforeAll
    static void boot() {
        base = PauseOnTest.firstWait();
    }

    private static ControlState replaying(PauseOnControlsTest.Run run) {
        return ControlState.replaying(run, Capabilities.NOW, WAITS);
    }

    private static ModeState view(ControlState controls) {
        return controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 100, 3, false));
    }

    @Test
    @DisplayName("a loaded log opens stopped as REPLAY, held, with the place in the log on the strip")
    void opens_stopped() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = replaying(run);
        assertTrue(controls.replay());
        assertTrue(run.held, "held from the first Input wait, so nothing is applied until a press");
        ModeState shown = view(controls);
        assertEquals(Mode.REPLAY, shown.mode());
        assertFalse(shown.replayPlaying());
        String line = ModeStripContent.text(shown);
        assertTrue(line.startsWith("REPLAY  wait 0 of " + WAITS + ", stopped  Next Step"), line);
        assertEquals(ModeStripContent.REPLAY_COLOR, ModeStripContent.color(shown));
        assertNotEquals(ModeStripContent.REPLAY_COLOR, ModeStripContent.color(Mode.HUMAN));
        assertNotEquals(ModeStripContent.REPLAY_COLOR, ModeStripContent.color(Mode.PAUSED));
        assertNotEquals(ModeStripContent.REPLAY_COLOR, ModeStripContent.color(Mode.RUNNING));
    }

    @Test
    @DisplayName("the enablement row of REPLAY: every control of PAUSED but Take over, and Run over disables all")
    void the_row() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = replaying(run);
        Set<Control> stopped = EnumSet.allOf(Control.class);
        stopped.remove(Control.TAKE_OVER);
        assertEquals(stopped, controls.enabled());
        assertEquals(stopped, ControlEnablement.of(Mode.REPLAY, false, Capabilities.NOW));
        assertEquals(Set.of(), ControlEnablement.of(Mode.REPLAY, true, Capabilities.NOW));

        assertTrue(controls.pauseResume(), "Resume");
        Set<Control> playing = EnumSet.of(Control.PAUSE_RESUME, Control.NEXT_STEP, Control.SPEED, Control.INTERVAL,
                Control.EXPLAIN);
        assertEquals(playing, controls.enabled(), "playing is the RUNNING row without Take over");
        assertTrue(view(controls).replayPlaying());
        assertTrue(ModeStripContent.text(view(controls)).contains(", playing  Human"), ModeStripContent.text(view(controls)));
    }

    @Test
    @DisplayName("Take over is refused in REPLAY, stopped or playing, and the Run is never asked to change hands")
    void take_over_is_refused() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = replaying(run);
        assertFalse(controls.takeOverHandBack());
        controls.pauseResume();
        assertFalse(controls.takeOverHandBack());
        assertTrue(run.calls.stream().noneMatch(call -> call.startsWith("takeOver") || call.startsWith("handBack")),
                run.calls.toString());
        assertFalse(controls.switching());
    }

    @Test
    @DisplayName("Next Step gives one wait and stays stopped; Run N gives N and stops; Pause stops a playing replay")
    void steps() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = replaying(run);
        run.land(1, base, new Action.Rest(false));
        controls.frame(0.016);
        assertTrue(controls.nextStep());
        run.serve();
        assertEquals(List.of(1L), run.executed, "exactly one recorded wait applied");
        assertFalse(view(controls).replayPlaying());

        assertTrue(controls.countDown());
        assertEquals(9, controls.count());
        assertTrue(controls.runN());
        assertTrue(view(controls).replayPlaying(), "Run N plays");
        int applied = 0;
        for (long wait = 2; wait < 20 && controls.mode() == Mode.RUNNING; wait++) {
            run.land(wait, base, new Action.Rest(false));
            controls.frame(1.0);
            run.serve();
            applied = run.executed.size() - 1;
        }
        assertEquals(9, applied, "N waits, and then it stops");
        assertFalse(view(controls).replayPlaying());
        assertEquals(Mode.PAUSED, controls.mode());

        assertTrue(controls.pauseResume());
        assertTrue(controls.pauseResume(), "Pause");
        assertFalse(view(controls).replayPlaying());
    }

    @Test
    @DisplayName("no Pause-on condition stops a replay, whatever the settings say, and the settings are not taken")
    void pause_on_does_nothing() {
        PauseOnControlsTest.Run run = new PauseOnControlsTest.Run();
        ControlState controls = replaying(run);
        controls.settings(OverlaySettings.all());
        assertFalse(controls.settings().anyPauseOn(), "a replay takes no Pause-on settings");
        controls.pauseResume();
        run.land(7, base, new Action.Descend());
        controls.frame(1.0);
        run.serve();
        assertNull(controls.pausedFor());
        assertEquals(Mode.RUNNING, controls.mode(), "the stairs did not stop it");
        assertEquals(List.of(7L), run.executed, "and the recorded Action was applied");
    }
}
