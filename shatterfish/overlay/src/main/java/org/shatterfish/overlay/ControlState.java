package org.shatterfish.overlay;

import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.EnumSet;
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
 * <p><b>The stepping unit (story 5.7, FR-39, UX-DR6).</b> The unit is one Input wait: the Brain decides
 * the wait in front as soon as the hero is ready, and a held Run shows that Decision and executes it only
 * against a credit. Next Step gives one credit, so it advances exactly one wait; pressed while the Brain
 * is still thinking it is queued (the credit waits) and fires once when the Decision lands, with the Mode
 * strip reading THINKING meanwhile; a second press while one is queued is ignored, so a queued step fires
 * exactly once. Run N gives its credits one at a time and lands in PAUSED when the last is spent.
 *
 * <p><b>The speed modes.</b> Next Step acts only on a press. Human play speed gives one credit per
 * interval (default one second, half a second to five), the first at once, measured from the last
 * credit given, so the interval can be changed at any moment and the next wait uses it. Fast as it can
 * lets the Run go ({@code hold(false)}): a Decision executes as soon as the Brain returns. A speed mode
 * switched mid-Run takes effect at the next Input wait and never discards a credit already given, so a
 * wait is never lost: only a change of who holds the Run ({@code applyHold}) touches the Run, and
 * unholding executes whatever waits.
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

        /** Whether a Decision has landed for the wait in front and waits for a credit (story 5.7). */
        boolean ready();

        /** Whether the Run is over. */
        boolean ended();

        /**
         * Asks the Run to hand the game to the human (story 5.8). It applies at the next Input wait, and
         * {@link #humanTurn} turns true when it has; {@code speed} is the speed mode's word, for the log's
         * {@code mode} record.
         */
        void takeOver(String speed);

        /**
         * Asks the Run to take the game back (story 5.8). It applies at a wait the human has open, and
         * {@link #humanTurn} turns false when it has; the Run lands held, with a Decision for that wait.
         */
        void handBack(String speed);

        /** Whether the human plays now: a takeover has applied and a hand back has not. */
        boolean humanTurn();
    }

    private final Seam seam;
    private final Capabilities capabilities;
    private Mode mode = Mode.PAUSED;
    private SpeedMode speed = SpeedMode.NEXT_STEP;
    private int count = COUNT_DEFAULT;
    private double interval = INTERVAL_DEFAULT;
    /** Whether a Run N is under way: RUNNING until its credits are spent. */
    private boolean runN;
    /** The credits a Run N has still to give. */
    private int remaining;
    /** Whether the Run is held, as this last told it. */
    private boolean held;
    /** Seconds since the last credit the pacer gave. */
    private double sinceStep;
    /**
     * The Mode a Take over or Hand back press asked for and the Run has not applied yet (story 5.8): HUMAN or
     * PAUSED, or null. Until it applies the Mode is what it was and every control but Explain is dim, since a
     * press during the switch would act on a Run that is about to change hands (UX-DR7's Hero busy row).
     */
    private Mode target;

    /** A fresh Run's controls: the Run is held at once, PAUSED with Next Step. */
    ControlState(Seam seam, Capabilities capabilities) {
        this.seam = seam;
        this.capabilities = capabilities;
        seam.hold(true);
        held = true;
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
        if (target != null && !over()) {
            // The hero is busy: the press is queued, its control dim, until the hero is ready (story 5.8).
            return EnumSet.of(Control.EXPLAIN);
        }
        return ControlEnablement.of(mode, over(), capabilities);
    }

    /** The last Brain trouble this has answered, by the Run's own number for it (story 5.15). */
    private long troubleSeen;

    /**
     * The Run asks for PAUSED because the Brain failed (story 5.15, UX-DR7): a Brain error, or a third no
     * valid action in a row. Answered once per trouble ({@code seq} is the Run's count of them), landing in
     * PAUSED with Next Step -- the Run does not play on over a Brain that cannot decide -- and never over a
     * hand-over under way or a Run that is over. A person who presses Resume plays on; the Brain is asked again.
     */
    void brainTrouble(long seq, boolean pauses) {
        if (seq <= troubleSeen) {
            return;
        }
        troubleSeen = seq;
        if (!pauses || over() || target != null || mode == Mode.HUMAN) {
            return;
        }
        runN = false;
        speed = SpeedMode.NEXT_STEP;
        pause();
    }

    /** Whether a Take over or Hand back is queued and has not applied (story 5.8). */
    boolean switching() {
        return target != null;
    }

    boolean enabled(Control control) {
        return enabled().contains(control);
    }

    /** The Mode, speed and values as the Mode strip shows them, over the Run's turn, floor and thinking flag. */
    ModeState view(ModeState run) {
        // A person's own pace paces a HUMAN turn: no speed mode does (story 5.8).
        return new ModeState(mode, mode == Mode.HUMAN ? SpeedMode.PLAYER : speed, interval, run.turn(), run.floor(),
                run.thinking(), over(), run.ending());
    }

    /**
     * Once per frame, {@code seconds} after the last: gives the credits the speed mode owes and lands a
     * finished Run N in PAUSED. A credit is given only to a Run that has a Decision waiting for one and
     * none already given, so a credit is never banked ahead of the wait it is for.
     */
    void frame(double seconds) {
        sinceStep += seconds;
        if (target != null && seam.humanTurn() == (target == Mode.HUMAN)) {
            // The Run has changed hands at its Input wait: the Mode follows it (story 5.8).
            mode = target;
            target = null;
            sinceStep = 0;
        }
        if (over()) {
            return;
        }
        if (target == null && mode == Mode.RUNNING && held && seam.credits() == 0 && seam.ready() && (!runN || remaining > 0)
                && (speed != SpeedMode.HUMAN_PLAY || sinceStep >= interval)) {
            seam.advance(1);
            sinceStep = 0;
            if (runN) {
                remaining--;
            }
        }
        if (runN && remaining == 0 && seam.credits() == 0) {
            // The last credit is spent: its Decision was executed. Held already, so nothing plays on.
            runN = false;
            mode = Mode.PAUSED;
            applyHold();
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
            if (seam.credits() > 0) {
                // One Next Step is already queued or about to fire: it fires once, and pressing again before
                // it has does not stack (story 5.7).
                return false;
            }
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
        remaining = count;
        sinceStep = interval;
        applyHold();
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
     * only if the Run cannot pace. Choosing Next Step while RUNNING pauses the bot, since it then acts
     * only on a press; any other choice takes effect at the next Input wait.
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
        } else {
            applyHold();
        }
        return true;
    }

    /**
     * Take over while the bot plays, Hand back while the human does (story 5.8, FR-40, UX-DR9). Neither
     * changes the Mode at once: the Run changes hands at an Input wait, and the Mode follows it in
     * {@link #frame}, the control dim meanwhile, so a press while the hero is mid-animation applies when the
     * hero is ready. Take over holds the Run, which withdraws a credit not yet spent; the Decision the Brain
     * was about to execute is kept as a shadow, never executed. Hand back lands in PAUSED, never straight in
     * RUNNING, with the Decision for the wait in front, computed after the human's last turn.
     */
    boolean takeOverHandBack() {
        if (!enabled(Control.TAKE_OVER)) {
            return false;
        }
        runN = false;
        holdNow();
        if (mode == Mode.HUMAN) {
            target = Mode.PAUSED;
            seam.handBack(speed.word);
        } else {
            target = Mode.HUMAN;
            seam.takeOver(speed.word);
        }
        return true;
    }

    private void pause() {
        runN = false;
        mode = Mode.PAUSED;
        holdNow();
    }

    private void resume() {
        // A Run that resumes plays on its own; Next Step is the speed that waits for a press, so the
        // speed becomes the readable pace when it can pace.
        if (speed == SpeedMode.NEXT_STEP) {
            speed = capabilities.pacing() ? SpeedMode.HUMAN_PLAY : SpeedMode.FAST;
        }
        runN = false;
        mode = Mode.RUNNING;
        sinceStep = interval;
        applyHold();
    }

    /** Holds the Run and withdraws any credit not yet spent: a Pause, a Take over, a Hand back. */
    private void holdNow() {
        held = true;
        seam.hold(true);
    }

    /**
     * Whether the Run goes as fast as it can: Fast as it can, outside a Run N (story 5.13). The game then runs
     * its animations at {@link #FAST_FRAME} seconds a frame, so a sprite's movement ends in one frame and the
     * animation interval no longer sets the speed of the Run.
     */
    boolean unbounded() {
        return mode == Mode.RUNNING && speed == SpeedMode.FAST && !runN;
    }

    /** The game time one frame covers when {@link #unbounded}: longer than any animation of the game. */
    static final float FAST_FRAME = 1f;

    /**
     * Tells the Run whether it is held, only when that changes (telling it clears its credits, and a
     * credit already given is a wait the human asked for). Only Fast as it can, outside a Run N, lets the
     * Run go; every other state holds it, and Human play speed gives its credits in {@link #frame}.
     */
    private void applyHold() {
        boolean wanted = !(mode == Mode.RUNNING && speed == SpeedMode.FAST && !runN);
        if (wanted != held) {
            held = wanted;
            seam.hold(wanted);
        }
    }
}
