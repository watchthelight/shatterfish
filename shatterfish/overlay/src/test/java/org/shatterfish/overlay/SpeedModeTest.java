package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The speed modes and the stepping unit (story 5.7, FR-39, UX-DR6): the number of Input waits each mode
 * advances, that a Next Step pressed while the Brain thinks is queued and fires exactly once, and that
 * switching speed mode never loses a turn. Run against {@link SimRun}, which keeps the order of
 * {@code EmbeddedRun.frame} for a held Run: the wait is confirmed and the Brain asked at once, the
 * Decision that lands waits for a credit, and a credit is spent when its Decision executes.
 */
class SpeedModeTest {

    /** A frame of the simulation, in seconds: sixty to the second. */
    private static final double DT = 1.0 / 60;

    /** One Brain, a fixed number of frames to decide, and the held Run's rule about credits. */
    static final class SimRun implements ControlState.Seam {
        final int thinkFrames;
        boolean held;
        int credits;
        boolean thinking;
        boolean landed;
        boolean ready;
        int left;
        int confirmed;
        final List<Integer> executed = new ArrayList<>();
        final List<Double> executedAt = new ArrayList<>();
        double now;

        SimRun(int thinkFrames) {
            this.thinkFrames = thinkFrames;
        }

        @Override
        public void hold(boolean held) {
            this.held = held;
            this.credits = 0;
        }

        @Override
        public void advance(int waits) {
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

        /** The Run's frame: confirm and ask, wait for the Brain, then execute against a credit or hold. */
        void frame() {
            now += DT;
            if (!thinking && !landed) {
                confirmed++;
                thinking = true;
                left = thinkFrames;
            }
            if (thinking && --left <= 0) {
                thinking = false;
                landed = true;
            }
            if (landed) {
                if (held && credits == 0) {
                    ready = true;
                } else {
                    if (held) {
                        credits--;
                    }
                    executed.add(confirmed);
                    executedAt.add(now);
                    landed = false;
                    ready = false;
                }
            }
        }
    }

    private static void play(ControlState controls, SimRun run, int frames) {
        for (int i = 0; i < frames; i++) {
            controls.frame(DT);
            run.frame();
        }
    }

    @Test
    @DisplayName("Next Step advances exactly one Input wait per press, and nothing between presses")
    void next_step_advances_one_wait() {
        SimRun run = new SimRun(3);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        play(controls, run, 600);
        assertEquals(0, run.executed.size(), "PAUSED with Next Step: the Decision is made and shown, not executed");
        assertTrue(run.ready);
        for (int press = 1; press <= 4; press++) {
            assertTrue(controls.nextStep());
            play(controls, run, 600);
            assertEquals(press, run.executed.size(), "press " + press + " advanced exactly one wait");
            assertEquals(Mode.PAUSED, controls.mode());
        }
        assertEquals(List.of(1, 2, 3, 4), run.executed, "the waits in order, none skipped");
    }

    @Test
    @DisplayName("Run N advances N Input waits and lands in PAUSED")
    void run_n_advances_n_and_pauses() {
        SimRun run = new SimRun(2);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        for (int i = 0; i < 5; i++) {
            controls.countDown();
        }
        assertEquals(5, controls.count());
        assertTrue(controls.runN());
        assertEquals(Mode.RUNNING, controls.mode());
        play(controls, run, 2_000);
        assertEquals(5, run.executed.size());
        assertEquals(Mode.PAUSED, controls.mode());
        assertTrue(run.held, "held again: nothing plays on");
        play(controls, run, 600);
        assertEquals(5, run.executed.size(), "and no sixth");
    }

    @Test
    @DisplayName("Human play speed advances one Input wait per interval: a second by default, and the interval is live")
    void human_play_speed_paces_by_the_interval() {
        SimRun run = new SimRun(2);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertEquals(1.0, controls.interval());
        assertTrue(controls.pauseResume());
        assertEquals(SpeedMode.HUMAN_PLAY, controls.speed());
        play(controls, run, 60 * 10 + 5);
        assertTrue(run.executed.size() >= 10 && run.executed.size() <= 11,
                "ten seconds at one a second: " + run.executed.size());
        assertGaps(run, 1.0, 0.1);

        controls.intervalDown();
        assertEquals(0.5, controls.interval());
        int before = run.executed.size();
        play(controls, run, 60 * 10);
        int after = run.executed.size() - before;
        assertTrue(after >= 19 && after <= 21, "ten seconds at half a second: " + after);

        for (int i = 0; i < 20; i++) {
            controls.intervalUp();
        }
        assertEquals(5.0, controls.interval());
        before = run.executed.size();
        play(controls, run, 60 * 10);
        after = run.executed.size() - before;
        assertTrue(after >= 1 && after <= 3, "ten seconds at five: " + after);
        assertEquals(Mode.RUNNING, controls.mode());
    }

    private static void assertGaps(SimRun run, double interval, double slack) {
        for (int i = 1; i < run.executedAt.size(); i++) {
            double gap = run.executedAt.get(i) - run.executedAt.get(i - 1);
            assertTrue(gap >= interval - 1e-9 && gap <= interval + slack, "gap " + i + " was " + gap);
        }
    }

    @Test
    @DisplayName("Fast as it can advances as soon as the Brain returns: the Run is let go, no interval applies")
    void fast_advances_when_the_brain_returns() {
        SimRun run = new SimRun(2);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        controls.cycleSpeed();
        assertEquals(SpeedMode.FAST, controls.speed());
        assertFalse(run.held);
        play(controls, run, 60);
        assertTrue(run.executed.size() >= 15, "a wait every few frames, not one a second: " + run.executed.size());
    }

    @Test
    @DisplayName("a Next Step pressed while the Brain thinks is queued, shows THINKING, and fires exactly once when the Decision lands")
    void queued_next_step_fires_once() {
        SimRun run = new SimRun(30);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        play(controls, run, 5);
        assertTrue(run.thinking);
        assertTrue(controls.nextStep(), "accepted: the Brain is over budget, not the press");
        assertEquals(1, run.credits(), "queued as a credit");
        assertFalse(controls.nextStep(), "a second press while one is queued does not stack");
        assertEquals(1, run.credits());
        ModeState strip = controls.view(new ModeState(Mode.RUNNING, SpeedMode.NORMAL, 0, 3, 1, run.thinking()));
        assertEquals(Mode.PAUSED, strip.mode());
        assertTrue(ModeStripContent.text(strip).endsWith("THINKING"), ModeStripContent.text(strip));
        play(controls, run, 10);
        assertEquals(0, run.executed.size(), "still thinking: nothing fires early");
        play(controls, run, 300);
        assertEquals(1, run.executed.size(), "it fired when the Decision landed, and only once");
        assertEquals(0, run.credits());
        assertTrue(run.ready, "and the next Decision waits for the next press");
    }

    @Test
    @DisplayName("switching speed mode never loses a turn: every wait confirmed is executed once, in order")
    void switching_never_loses_a_turn() {
        SimRun run = new SimRun(4);
        ControlState controls = new ControlState(run, Capabilities.NOW);
        controls.pauseResume();
        for (int round = 0; round < 40; round++) {
            play(controls, run, 7 + round % 5);
            switch (round % 4) {
                case 0 -> controls.cycleSpeed();
                case 1 -> controls.intervalDown();
                case 2 -> {
                    controls.pauseResume();
                    controls.nextStep();
                }
                default -> controls.cycleSpeed();
            }
        }
        play(controls, run, 600);
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= run.executed.size(); i++) {
            expected.add(i);
        }
        assertEquals(expected, run.executed, "each wait once, in order");
        assertTrue(run.confirmed - run.executed.size() <= 1, "at most the wait in front is left");
    }
}
