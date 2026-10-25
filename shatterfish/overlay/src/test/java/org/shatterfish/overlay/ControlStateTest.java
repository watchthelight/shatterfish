package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /** A Run that cannot pace (story 5.6's capabilities): the selector skips Human, the interval stepper is dim. */
    static final Capabilities NO_PACING = new Capabilities(false, false);

    /** A Run that records what it is told, with the credit it has and whether it thinks or has ended. */
    static final class FakeRun implements ControlState.Seam {
        final List<String> calls = new ArrayList<>();
        boolean held;
        int credits;
        boolean thinking;
        boolean ready;
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
        public boolean ready() {
            return ready;
        }

        @Override
        public boolean ended() {
            return ended;
        }

        /** Whether the human plays now, as the Run says: a test sets it when the switch has applied at an Input wait. */
        boolean person;

        @Override
        public void takeOver(String speed) {
            calls.add("takeOver " + speed);
        }

        @Override
        public void handBack(String speed) {
            calls.add("handBack " + speed);
        }

        @Override
        public boolean humanTurn() {
            return person;
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
        assertTrue(run.held, "Human play speed gives its credits one at a time, so the Run stays held");
        assertTrue(controls.nextStep(), "in RUNNING the press pauses");
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);
        assertEquals(0, run.credits, "and gives no wait: the next press steps");
        assertTrue(controls.nextStep());
        assertEquals(1, run.credits);
    }

    @Test
    @DisplayName("Resume from Next Step plays on at the readable pace (Fast if the Run cannot pace); Pause holds again")
    void resume_and_pause() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        assertEquals(Mode.RUNNING, controls.mode());
        assertEquals(SpeedMode.HUMAN_PLAY, controls.speed(), "Next Step waits for a press, so a Run that plays on is not at it");
        controls.pauseResume();
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);

        FakeRun unpaced = new FakeRun();
        ControlState cannot = new ControlState(unpaced, NO_PACING);
        cannot.pauseResume();
        assertEquals(SpeedMode.FAST, cannot.speed(), "a Run that cannot pace plays on as fast as it goes");
        assertFalse(unpaced.held);
    }

    @Test
    @DisplayName("Run N gives the Run its waits one at a time, reads RUNNING while they last, and lands in PAUSED when they are spent")
    void run_n() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.countDown();
        controls.countDown();
        assertEquals(8, controls.count());
        assertTrue(controls.runN());
        assertEquals(Mode.RUNNING, controls.mode());
        assertEquals(0, run.credits, "no credit is banked ahead of a Decision to spend it on");
        assertFalse(controls.runN(), "RUNNING: Run N is dim");

        run.ready = true;
        for (int i = 0; i < 8; i++) {
            controls.frame(0.016);
            assertEquals(1, run.credits, "a Decision waits, so one credit is given");
            assertEquals(Mode.RUNNING, controls.mode(), "waits left");
            run.credits = 0;
        }
        controls.frame(0.016);
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(0, run.credits, "and no ninth");
        assertTrue(run.held, "still held: nothing plays on");
    }

    @Test
    @DisplayName("Pause in the middle of a Run N cancels the waits it still owed")
    void pause_cancels_run_n() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.runN();
        run.ready = true;
        controls.frame(0.016);
        assertEquals(1, run.credits);
        controls.pauseResume();
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(0, run.credits);
        controls.frame(0.016);
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
    @DisplayName("the speed selector cycles Next Step, Human, Fast, and skips Human if the Run cannot pace")
    void speed_cycle() {
        ControlState now = new ControlState(new FakeRun(), NO_PACING);
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
        assertEquals(SpeedMode.HUMAN_PLAY, controls.speed());
        controls.cycleSpeed();
        assertEquals(SpeedMode.FAST, controls.speed());
        assertFalse(run.held, "Fast as it can lets the Run go");
        controls.cycleSpeed();
        assertEquals(SpeedMode.NEXT_STEP, controls.speed());
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held);
    }

    @Test
    @DisplayName("Take over holds the Run and asks it to change hands; the Mode follows when the Run has, and Hand back lands in PAUSED, never RUNNING")
    void take_over_and_hand_back() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, new Capabilities(true, false));
        assertFalse(controls.takeOverHandBack(), "dim while the capability is off");
        assertEquals(Mode.PAUSED, controls.mode());

        FakeRun other = new FakeRun();
        ControlState all = new ControlState(other, Capabilities.NOW);
        all.pauseResume();
        assertEquals(Mode.RUNNING, all.mode());
        assertTrue(all.takeOverHandBack());
        assertTrue(other.held, "a Take over withdraws the credit the Run would have spent");
        assertEquals("takeOver Human", other.calls.get(other.calls.size() - 1));
        assertEquals(Mode.RUNNING, all.mode(), "the Mode changes when the Run does, at its Input wait");
        assertTrue(all.switching());
        other.person = true;
        all.frame(0.016);
        assertEquals(Mode.HUMAN, all.mode());
        assertFalse(all.switching());
        assertEquals(SpeedMode.PLAYER, all.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 1, 1, false)).speed());

        assertTrue(all.takeOverHandBack(), "Hand back");
        assertEquals("handBack Human", other.calls.get(other.calls.size() - 1));
        assertEquals(Mode.HUMAN, all.mode(), "still the human's until the Run has taken the game back");
        other.person = false;
        all.frame(0.016);
        assertEquals(Mode.PAUSED, all.mode(), "never straight to RUNNING");
        assertTrue(other.held);
        assertEquals(0, other.credits);
    }

    @Test
    @DisplayName("a Take over pressed while the hero is busy is queued: every control but Explain is dim until the Run has changed hands, then it applies")
    void take_over_queued_while_the_hero_is_busy() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(controls.takeOverHandBack());
        assertEquals(Set.of(Control.EXPLAIN), controls.enabled(), "the pressed control and the rest are dim");
        assertFalse(controls.takeOverHandBack(), "a second press does not stack");
        assertFalse(controls.nextStep());
        assertFalse(controls.pauseResume());
        assertFalse(controls.runN());
        assertFalse(controls.cycleSpeed());
        int calls = run.calls.size();
        controls.frame(5.0);
        assertEquals(calls, run.calls.size(), "nothing is given to the Run while the switch waits");
        assertEquals(0, run.credits);
        assertEquals(Mode.PAUSED, controls.mode());
        run.person = true;
        controls.frame(0.016);
        assertEquals(Mode.HUMAN, controls.mode());
        assertEquals(Set.of(Control.TAKE_OVER, Control.EXPLAIN), controls.enabled(), "HUMAN: Hand back and Explain");
    }

    @Test
    @DisplayName("a Take over pressed in RUNNING gives the Decision that was about to execute no credit, even at Human play speed")
    void take_over_gives_no_credit_while_it_waits() {
        FakeRun run = new FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        run.ready = true;
        controls.takeOverHandBack();
        controls.frame(10.0);
        assertEquals(0, run.credits, "the interval elapsed, and the withheld Decision still got no credit");
        assertFalse(run.calls.contains("advance 1"), run.calls.toString());
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
