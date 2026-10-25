package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What pressing the Controls row's buttons does (story 5.6, FR-39, UX-DR6, UX-DR7), against a Run that is
 * a list of calls: the Run starts in PAUSED with Next Step; Pause, Resume and Next Step turn the hold;
 * Run N lands in PAUSED; the steppers keep their values and range; and the property the story is for --
 * a control the matrix enables changes something, and a control it leaves dim changes nothing.
 */
class ControlStateTest {

    /** A Run that records what it is told, with the credit it has and whether it thinks or has ended. */
    static final class FakeRun implements ControlState.Seam {
        final List<String> calls = new ArrayList<>();
        boolean held;
        int credits;
        boolean thinking;
        boolean ended;

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
            return thinking;
        }

        @Override
        public boolean ended() {
            return ended;
        }

        @Override
        public void humanInput(boolean allowed) {
            calls.add("human " + allowed);
        }
    }

    @Test
    @DisplayName("the Run starts held, in PAUSED with Next Step, the count at 10 and the interval at 1.0 s")
    void starts_paused_with_next_step() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(SpeedMode.NEXT_STEP, controls.speed());
        assertEquals(10, controls.count());
        assertEquals(1.0, controls.interval());
        assertTrue(run.held, "held from the first Input wait, so the Run waits for the first press");
        assertEquals(0, run.credits);
    }

    @Test
    @DisplayName("Next Step in PAUSED gives the Run one wait and stays PAUSED; in RUNNING it pauses first")
    void next_step() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(controls.nextStep());
        assertEquals(1, run.credits);
        assertEquals(Mode.PAUSED, controls.mode());

        assertTrue(controls.pauseResume(), "Resume");
        assertEquals(Mode.RUNNING, controls.mode());
        assertFalse(run.held);
        assertTrue(controls.nextStep(), "in RUNNING the press pauses");
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);
        assertEquals(0, run.credits, "and gives no wait: the next press steps");
        assertTrue(controls.nextStep());
        assertEquals(1, run.credits);
    }

    @Test
    @DisplayName("Resume from Next Step plays on at Fast (Human play speed is story 5.7's); Pause holds again")
    void resume_and_pause() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        assertEquals(Mode.RUNNING, controls.mode());
        assertEquals(SpeedMode.FAST, controls.speed(), "Next Step waits for a press, so a Run that plays on is not at it");
        controls.pauseResume();
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);

        ControlState later = new ControlState(new FakeRun(), Capabilities.ALL);
        later.pauseResume();
        assertEquals(SpeedMode.HUMAN_PLAY, later.speed(), "once the Run can pace, the readable pace");
    }

    @Test
    @DisplayName("Run N gives the Run N waits, reads RUNNING while they last, and lands in PAUSED when they are spent and decided")
    void run_n() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.countDown();
        controls.countDown();
        assertEquals(8, controls.count());
        assertTrue(controls.runN());
        assertEquals(Mode.RUNNING, controls.mode());
        assertEquals(8, run.credits);
        assertFalse(controls.runN(), "RUNNING: Run N is dim");

        run.credits = 3;
        controls.frame();
        assertEquals(Mode.RUNNING, controls.mode(), "waits left");
        run.credits = 0;
        run.thinking = true;
        controls.frame();
        assertEquals(Mode.RUNNING, controls.mode(), "the last wait is still being decided");
        run.thinking = false;
        controls.frame();
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held, "still held: nothing plays on");
    }

    @Test
    @DisplayName("Pause in the middle of a Run N cancels the waits it still owed")
    void pause_cancels_run_n() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.runN();
        controls.pauseResume();
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(0, run.credits);
        controls.frame();
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(0, run.credits);
    }

    @Test
    @DisplayName("the steppers hold their range: the count 1 to 999, the interval 0.5 s to 5 s in halves")
    void steppers() {
        ControlState controls = new ControlState(new FakeRun(), Capabilities.ALL);
        for (int i = 0; i < 20; i++) {
            controls.countDown();
        }
        assertEquals(1, controls.count());
        for (int i = 0; i < 2_000; i++) {
            controls.countUp();
        }
        assertEquals(999, controls.count());
        for (int i = 0; i < 20; i++) {
            controls.intervalDown();
        }
        assertEquals(0.5, controls.interval());
        controls.intervalUp();
        assertEquals(1.0, controls.interval());
        for (int i = 0; i < 20; i++) {
            controls.intervalUp();
        }
        assertEquals(5.0, controls.interval());
    }

    @Test
    @DisplayName("the speed selector cycles Next Step, Human, Fast, and skips Human until the Run can pace")
    void speed_cycle() {
        ControlState now = new ControlState(new FakeRun(), Capabilities.NOW);
        now.cycleSpeed();
        assertEquals(SpeedMode.FAST, now.speed());
        now.cycleSpeed();
        assertEquals(SpeedMode.NEXT_STEP, now.speed());

        ControlState all = new ControlState(new FakeRun(), Capabilities.ALL);
        all.cycleSpeed();
        assertEquals(SpeedMode.HUMAN_PLAY, all.speed());
        all.cycleSpeed();
        assertEquals(SpeedMode.FAST, all.speed());
        all.cycleSpeed();
        assertEquals(SpeedMode.NEXT_STEP, all.speed());
    }

    @Test
    @DisplayName("choosing Next Step while RUNNING pauses the bot, since it then acts only on a press")
    void next_step_speed_pauses() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        assertEquals(SpeedMode.FAST, controls.speed());
        controls.cycleSpeed();
        assertEquals(SpeedMode.NEXT_STEP, controls.speed());
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);
    }

    @Test
    @DisplayName("Take over is dim until story 5.8; with it built, Take over lands in HUMAN and Hand back in PAUSED, never RUNNING")
    void take_over_and_hand_back() {
        FakeRun run = new FakeRun();
        ControlState now = new ControlState(run, Capabilities.NOW);
        assertFalse(now.takeOverHandBack(), "dim");
        assertEquals(Mode.PAUSED, now.mode());

        FakeRun other = new FakeRun();
        ControlState all = new ControlState(other, Capabilities.ALL);
        all.pauseResume();
        assertTrue(all.takeOverHandBack());
        assertEquals(Mode.HUMAN, all.mode());
        assertTrue(other.held);
        assertEquals("human true", other.calls.get(other.calls.size() - 1));
        assertTrue(all.takeOverHandBack(), "Hand back");
        assertEquals(Mode.PAUSED, all.mode());
        assertEquals("human false", other.calls.get(other.calls.size() - 1));
        assertTrue(other.held);
    }

    @Test
    @DisplayName("a Run that is over disables every control and the strip reads it")
    void run_over() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.ALL);
        run.ended = true;
        assertTrue(controls.enabled().isEmpty());
        assertFalse(controls.pauseResume());
        assertFalse(controls.nextStep());
        assertFalse(controls.runN());
        assertTrue(controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 7, 2, false)).over());
    }

    @Test
    @DisplayName("view puts the controls' Mode, speed and interval over the Run's own turn, floor and THINKING")
    void view() {
        ControlState controls = new ControlState(new FakeRun(), Capabilities.ALL);
        ModeState seen = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 41, 3, true));
        assertEquals(Mode.PAUSED, seen.mode());
        assertEquals(SpeedMode.NEXT_STEP, seen.speed());
        assertEquals(41, seen.turn());
        assertEquals(3, seen.floor());
        assertTrue(seen.thinking());
        assertTrue(ModeStripContent.text(seen).startsWith("PAUSED  Next Step  turn "), ModeStripContent.text(seen));
        assertTrue(ModeStripContent.text(seen).endsWith("THINKING"));
    }

    /**
     * The story's property: in every state a Brain's Run can be in, pressing a control the matrix enables
     * changes something (the controls' own state, or a call to the Run), and pressing one it leaves dim
     * changes nothing and answers false -- so no state leaves a control enabled that would do nothing.
     * The states are reached by pressing, and the steppers are at values inside their range, where a
     * press moves them.
     */
    @Test
    @DisplayName("in every state, a control the matrix enables does something and a control it leaves dim does nothing")
    void enabled_acts_and_dim_does_not() {
        List<Function<FakeRun, ControlState>> states = List.of(
                run -> new ControlState(run, Capabilities.ALL),
                run -> {
                    ControlState controls = new ControlState(run, Capabilities.ALL);
                    controls.pauseResume();
                    return controls;
                },
                run -> {
                    ControlState controls = new ControlState(run, Capabilities.ALL);
                    controls.runN();
                    return controls;
                },
                run -> {
                    ControlState controls = new ControlState(run, Capabilities.ALL);
                    controls.takeOverHandBack();
                    return controls;
                },
                run -> {
                    ControlState controls = new ControlState(run, Capabilities.NOW);
                    controls.pauseResume();
                    return controls;
                },
                run -> {
                    ControlState controls = new ControlState(run, Capabilities.ALL);
                    run.ended = true;
                    return controls;
                });
        for (Function<FakeRun, ControlState> make : states) {
            for (Control control : Control.values()) {
                if (control == Control.EXPLAIN) {
                    continue; // the Panel's: the card toggles; ControlsRowTest presses it.
                }
                for (boolean second : new boolean[]{false, true}) {
                    if (second && control != Control.RUN_N_COUNT && control != Control.INTERVAL) {
                        continue; // only the steppers have a second button
                    }
                    FakeRun run = new FakeRun();
                    ControlState controls = make.apply(run);
                    boolean enabled = controls.enabled(control);
                    String before = fingerprint(controls, run);
                    boolean answered = press(controls, control, second);
                    String after = fingerprint(controls, run);
                    String where = control + (second ? " (second button)" : "") + " in " + before;
                    assertEquals(enabled, answered, "the press answers as the matrix says: " + where);
                    if (enabled) {
                        assertTrue(!before.equals(after), "an enabled control that did nothing: " + where);
                    } else {
                        assertEquals(before, after, "a dim control that changed something: " + where);
                    }
                }
            }
        }
    }

    private static String fingerprint(ControlState controls, FakeRun run) {
        return controls.mode() + "/" + controls.speed() + "/" + controls.count() + "/" + controls.interval() + "/"
                + run.held + "/" + run.credits + "/" + run.calls.size();
    }

    private static boolean press(ControlState controls, Control control, boolean second) {
        return switch (control) {
            case PAUSE_RESUME -> controls.pauseResume();
            case NEXT_STEP -> controls.nextStep();
            case RUN_N -> controls.runN();
            case RUN_N_COUNT -> second ? controls.countDown() : controls.countUp();
            case SPEED -> controls.cycleSpeed();
            case INTERVAL -> second ? controls.intervalDown() : controls.intervalUp();
            case TAKE_OVER -> controls.takeOverHandBack();
            case EXPLAIN -> throw new AssertionError("not a ControlState press");
        };
    }
}
