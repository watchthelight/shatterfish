package org.shatterfish.overlay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;
import org.shatterfish.overlay.PauseOn.Condition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Controls do with a Pause-on condition (story 8.3, FR-45), against a Run that is a list of calls with
 * a Decision that can be put in front of it: a condition that holds in RUNNING moves the Overlay to PAUSED
 * before the Decision executes, names itself on the Mode strip, and does not fire again for the same wait after
 * Resume; in PAUSED it does nothing; and with no condition enabled nothing about the controls changes.
 */
class PauseOnControlsTest {

    private static Observation base;

    @BeforeAll
    static void boot() {
        base = PauseOnTest.firstWait();
    }

    /** A Run with at most one pending wait; credits are spent by {@link #serve}, as the Run's frame does. */
    static final class Run implements ControlState.Seam {
        final List<String> calls = new ArrayList<>();
        boolean held;
        int credits;
        ControlState.Pending pending;
        final List<Long> executed = new ArrayList<>();

        void land(long wait, Observation observation, Action action) {
            pending = new ControlState.Pending(wait, observation, PauseOnTest.deciding(action));
        }

        /** The Run's frame: a held Run with a credit executes the pending Decision. */
        void serve() {
            if (pending != null && held && credits > 0) {
                credits--;
                executed.add(pending.index());
                pending = null;
            } else if (pending != null && !held) {
                executed.add(pending.index());
                pending = null;
            }
        }

        @Override
        public void hold(boolean held) {
            calls.add("hold " + held);
            this.held = held;
            this.credits = 0;
        }

        @Override
        public void advance(int waits) {
            calls.add("advance " + waits);
            if (held) {
                credits += waits;
            }
        }

        @Override
        public int credits() {
            return held ? credits : 0;
        }

        @Override
        public boolean thinking() {
            return false;
        }

        @Override
        public boolean ready() {
            return pending != null && held && credits == 0;
        }

        @Override
        public boolean ended() {
            return false;
        }

        @Override
        public void takeOver(String speed) {
        }

        @Override
        public void handBack(String speed) {
        }

        @Override
        public boolean humanTurn() {
            return false;
        }

        @Override
        public ControlState.Pending pending() {
            return ready() ? pending : null;
        }

        @Override
        public void pausedOn(String speed) {
            calls.add("pausedOn " + speed);
        }
    }

    private static ControlState controls(Run run, Condition... conditions) {
        Set<Condition> set = EnumSet.noneOf(Condition.class);
        set.addAll(List.of(conditions));
        return new ControlState(run, Capabilities.NOW, OverlaySettings.DEFAULTS.withPauseOn(set), Set.of(5, 10, 15, 20, 25));
    }

    @Test
    @DisplayName("a condition that holds in RUNNING pauses before the Decision executes, names itself, and Resume executes it once")
    void pauses_before_executing_and_resume_executes() {
        Run run = new Run();
        ControlState controls = controls(run, Condition.STAIRS);
        controls.pauseResume();
        assertEquals(Mode.RUNNING, controls.mode());
        run.land(7, base, new Action.Descend());
        run.serve();
        controls.frame(0.016);
        assertEquals(Mode.PAUSED, controls.mode(), "paused at the Input wait");
        assertEquals("before stairs", controls.pausedFor());
        assertEquals(List.of(), run.executed, "nothing executed yet");
        assertTrue(run.calls.contains("pausedOn Human"), "the Run writes the Mode record: " + run.calls);
        ModeState view = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 100, 3, false));
        assertTrue(ModeStripContent.text(view).startsWith("PAUSED: before stairs"), ModeStripContent.text(view));

        run.serve();
        controls.frame(0.016);
        assertEquals(List.of(), run.executed, "PAUSED holds the Decision for as long as it is held");

        assertTrue(controls.pauseResume(), "Resume");
        controls.frame(0.016);
        run.serve();
        assertEquals(List.of(7L), run.executed, "the Decision that was about to run runs, once");
        assertEquals(Mode.RUNNING, controls.mode(), "and it did not fire again for the same wait");
        assertNull(controls.pausedFor());
    }

    @Test
    @DisplayName("Next Step from the pause executes the pending Decision and stays PAUSED")
    void next_step_executes() {
        Run run = new Run();
        ControlState controls = controls(run, Condition.STAIRS);
        controls.pauseResume();
        run.land(3, base, new Action.Descend());
        controls.frame(0.016);
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(controls.nextStep());
        run.serve();
        assertEquals(List.of(3L), run.executed);
        assertEquals(Mode.PAUSED, controls.mode());
    }

    @Test
    @DisplayName("the next wait is looked at too: a later wait that holds pauses again")
    void a_later_wait_pauses_again() {
        Run run = new Run();
        ControlState controls = controls(run, Condition.STAIRS);
        controls.pauseResume();
        run.land(1, base, new Action.Descend());
        controls.frame(0.016);
        controls.pauseResume();
        controls.frame(0.016);
        run.serve();
        run.land(2, base, new Action.Rest(false));
        controls.frame(1.0);
        assertEquals(Mode.RUNNING, controls.mode(), "a rest holds no condition");
        run.serve();
        run.land(3, base, new Action.Descend());
        controls.frame(1.0);
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(List.of(1L, 2L), run.executed);
    }

    @Test
    @DisplayName("in PAUSED a condition does nothing, and the Resume that follows does not fire for that wait")
    void nothing_in_paused() {
        Run run = new Run();
        ControlState controls = controls(run, Condition.STAIRS);
        assertEquals(Mode.PAUSED, controls.mode());
        run.land(1, base, new Action.Descend());
        controls.frame(0.016);
        assertEquals(Mode.PAUSED, controls.mode());
        assertNull(controls.pausedFor(), "no condition is named: the person paused this");
        assertFalse(run.calls.stream().anyMatch(call -> call.startsWith("pausedOn")), run.calls.toString());
        controls.pauseResume();
        controls.frame(0.016);
        assertEquals(Mode.RUNNING, controls.mode(), "the wait was looked at once, in PAUSED");
    }

    @Test
    @DisplayName("with no condition enabled the Run is never paused and Fast still lets the Run go")
    void defaults_change_nothing() {
        Run run = new Run();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        controls.cycleSpeed();
        assertEquals(SpeedMode.FAST, controls.speed());
        assertFalse(run.held, "Fast as it can lets the Run go, as before");
        run.land(1, base, new Action.Descend());
        controls.frame(0.016);
        assertEquals(Mode.RUNNING, controls.mode());
        assertNull(controls.pausedFor());
    }

    @Test
    @DisplayName("with a condition enabled Fast keeps the Run held so the Decision can be looked at, and gives its credit")
    void fast_stays_held_with_a_condition() {
        Run run = new Run();
        ControlState controls = controls(run, Condition.STAIRS);
        controls.pauseResume();
        controls.cycleSpeed();
        assertEquals(SpeedMode.FAST, controls.speed());
        assertTrue(run.held, "held so a Decision lands and waits for the look");
        run.land(1, base, new Action.Rest(false));
        controls.frame(0.016);
        run.serve();
        assertEquals(List.of(1L), run.executed, "and a Decision that holds nothing is paid for at once");
    }
}
