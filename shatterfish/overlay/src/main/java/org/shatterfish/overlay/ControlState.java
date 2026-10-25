package org.shatterfish.overlay;

import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.Set;

/**
 * What the Panel's controls have set, and what pressing them does (story 5.6, FR-39, UX-DR6, UX-DR7).
 * One per Brain's Run, owned by the Overlay: the Mode, the speed mode and the two steppers' values,
 * which hold for the session only (they are not saved anywhere). A Run starts in PAUSED with Next Step
 * (EXPERIENCE.md, "Run starts in PAUSED").
 *
 * <p>Every press goes through {@link ControlEnablement}: a press on a control the matrix says is dim
 * does nothing and says so ({@code false}). What a press does to the Run goes through {@link Seam}, so
 * the matrix and the transitions are held by a test that has no game ({@code ControlStateTest}).
 *
 * <p>Pause holds the Run at its next Input wait; Resume lets it go; Next Step spends one credit, which
 * the held Run spends on one wait; Run N spends N and lands in PAUSED when they are spent. How fast the
 * credits are spent -- Human play speed's interval, a Next Step queued behind a thinking Brain, the
 * Decision shown before its press -- is story 5.7's. Until then Run N and Resume play as fast as the
 * Brain answers.
 */
final class ControlState {

    /** The Run N count's default and range (EXPERIENCE.md: "N defaults to 10 (range 1 to 999)"). */
    static final int COUNT_DEFAULT = 10;
    static final int COUNT_MIN = 1;
    static final int COUNT_MAX = 999;
    /** Human play speed's interval default and range, in seconds (UX-DR6: one second, half a second to five). */
    static final double INTERVAL_DEFAULT = 1.0;
    static final double INTERVAL_MIN = 0.5;
    static final double INTERVAL_MAX = 5.0;
    static final double INTERVAL_STEP = 0.5;

    /** What a press does to the Run; {@code EmbeddedRun} is its only implementation outside tests. */
    interface Seam {
        /** Holds the Run at its next Input wait, or lets it go. Holding clears any credit. */
        void hold(boolean held);

        /** Lets a held Run confirm {@code waits} more Input waits. */
        void advance(int waits);

        /** The Input waits a held Run may still confirm. */
        int credits();

        /** Whether the Brain has a Decision pending. */
        boolean thinking();

        /** Whether the Run is over. */
        boolean ended();

        /** Opens or shuts the human's hand on the game (story 5.8's switch, story 5.5's gate). */
        void humanInput(boolean allowed);
    }

    private final Seam seam;
    private final Capabilities capabilities;
    private Mode mode = Mode.PAUSED;
    private SpeedMode speed = SpeedMode.NEXT_STEP;
    private int count = COUNT_DEFAULT;
    private double interval = INTERVAL_DEFAULT;
    /** Whether a Run N is under way: RUNNING until its credits are spent. */
    private boolean runN;

    /** A fresh Run's controls: the Run is held at once, PAUSED with Next Step. */
    ControlState(Seam seam, Capabilities capabilities) {
        this.seam = seam;
        this.capabilities = capabilities;
        seam.hold(true);
    }

    Mode mode() {
        return mode;
    }

    SpeedMode speed() {
        return speed;
    }

    int count() {
        return count;
    }

    double interval() {
        return interval;
    }

    Capabilities capabilities() {
        return capabilities;
    }

    /** Whether the Run is over, as the Run says. */
    boolean over() {
        return seam.ended();
    }

    /** The controls that act now. */
    Set<Control> enabled() {
        return ControlEnablement.of(mode, over(), capabilities);
    }

    boolean enabled(Control control) {
        return enabled().contains(control);
    }

    /** The Mode, speed and values as the Mode strip shows them, over the Run's turn, floor and thinking flag. */
    ModeState view(ModeState run) {
        return new ModeState(mode, speed, interval, run.turn(), run.floor(), run.thinking(), over());
    }

    /**
     * Once per frame: a Run N whose credits are spent and whose last Decision has landed has landed in
     * PAUSED. The Run's own end is read through {@link #over()}.
     */
    void frame() {
        if (runN && seam.credits() == 0 && !seam.thinking()) {
            runN = false;
            mode = Mode.PAUSED;
        }
    }

    /** Pause while RUNNING, Resume while PAUSED. */
    boolean pauseResume() {
        if (!enabled(Control.PAUSE_RESUME)) {
            return false;
        }
        if (mode == Mode.RUNNING) {
            pause();
        } else {
            resume();
        }
        return true;
    }

    /** Next Step: one Input wait from PAUSED; from RUNNING it pauses first (one press to pause, the next to step). */
    boolean nextStep() {
        if (!enabled(Control.NEXT_STEP)) {
            return false;
        }
        if (mode == Mode.RUNNING) {
            pause();
        } else {
            seam.advance(1);
        }
        return true;
    }

    /** Run N: N Input waits, then PAUSED. */
    boolean runN() {
        if (!enabled(Control.RUN_N)) {
            return false;
        }
        mode = Mode.RUNNING;
        runN = true;
        seam.advance(count);
        return true;
    }

    boolean countUp() {
        return stepCount(1);
    }

    boolean countDown() {
        return stepCount(-1);
    }

    private boolean stepCount(int by) {
        if (!enabled(Control.RUN_N_COUNT)) {
            return false;
        }
        count = Math.max(COUNT_MIN, Math.min(COUNT_MAX, count + by));
        return true;
    }

    boolean intervalUp() {
        return stepInterval(INTERVAL_STEP);
    }

    boolean intervalDown() {
        return stepInterval(-INTERVAL_STEP);
    }

    private boolean stepInterval(double by) {
        if (!enabled(Control.INTERVAL)) {
            return false;
        }
        interval = Math.max(INTERVAL_MIN, Math.min(INTERVAL_MAX, interval + by));
        return true;
    }

    /**
     * The speed selector, one press: Next Step, Human, Fast, and round (EXPERIENCE.md); Human is skipped
     * while pacing is not built. Choosing Next Step while RUNNING pauses the bot, since it then acts
     * only on a press.
     */
    boolean cycleSpeed() {
        if (!enabled(Control.SPEED)) {
            return false;
        }
        speed = switch (speed) {
            case NEXT_STEP -> capabilities.pacing() ? SpeedMode.HUMAN_PLAY : SpeedMode.FAST;
            case HUMAN_PLAY -> SpeedMode.FAST;
            default -> SpeedMode.NEXT_STEP;
        };
        if (speed == SpeedMode.NEXT_STEP && mode == Mode.RUNNING) {
            pause();
        }
        return true;
    }

    /** Take over while the bot plays (story 5.8's seam: dim until it is built), Hand back while the human does. */
    boolean takeOverHandBack() {
        if (!enabled(Control.TAKE_OVER)) {
            return false;
        }
        if (mode == Mode.HUMAN) {
            // Hand back lands in PAUSED, never straight in RUNNING (UX-DR9).
            mode = Mode.PAUSED;
            seam.hold(true);
            seam.humanInput(false);
        } else {
            runN = false;
            mode = Mode.HUMAN;
            seam.hold(true);
            seam.humanInput(true);
        }
        return true;
    }

    private void pause() {
        runN = false;
        mode = Mode.PAUSED;
        seam.hold(true);
    }

    private void resume() {
        // A Run that resumes plays on its own; Next Step is the speed that waits for a press, so the
        // speed becomes the readable pace when it can pace, and Fast until then.
        if (speed == SpeedMode.NEXT_STEP) {
            speed = capabilities.pacing() ? SpeedMode.HUMAN_PLAY : SpeedMode.FAST;
        }
        runN = false;
        mode = Mode.RUNNING;
        seam.hold(false);
    }
}
