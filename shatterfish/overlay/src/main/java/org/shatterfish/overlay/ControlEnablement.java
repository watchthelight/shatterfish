package org.shatterfish.overlay;

import java.util.EnumSet;
import java.util.Set;

/**
 * The enablement matrix of the Panel's controls (story 5.6, FR-39, UX-DR7): for each state, which of
 * the controls can do something. A control that cannot is dim and takes no press, so no state leaves a
 * control enabled that would do nothing.
 *
 * <p>The matrix is a pure function of the Mode, whether the Run is over, and the {@link Capabilities}
 * the Overlay has built so far. It does not depend on THINKING: a Next Step pressed while the Brain is
 * over budget is queued (UX-DR7), so it is as enabled then as it is without the suffix. {@link #matrix}
 * is what {@code UX-DR7} says; {@link #of} is that, less the controls whose action is a later story's,
 * which stay dim until that story turns its capability on.
 *
 * <p>The states of UX-DR7 that are not yet reachable (Brain error, no valid action, hero busy, no Run;
 * story 5.15) are not rows here. Panel collapsed hides the whole row and changes no enablement: the
 * hotkeys (story 5.11) still act through the same matrix.
 */
final class ControlEnablement {

    /** The controls of UX-DR7's Controls row, in the order the row lays them out. */
    enum Control {
        /** Pause while RUNNING, Resume otherwise. */
        PAUSE_RESUME,
        NEXT_STEP,
        RUN_N,
        /** The Run N count stepper's two buttons. */
        RUN_N_COUNT,
        /** The speed selector. */
        SPEED,
        /** The Human play speed interval stepper's two buttons. */
        INTERVAL,
        /** Take over while the bot plays, Hand back while the human does. */
        TAKE_OVER,
        EXPLAIN
    }

    /**
     * What the Overlay can do about a control yet. {@code pacing}: Human play speed paces a Run by its
     * interval (story 5.7); a Run that cannot pace skips it in the selector and dims the interval stepper.
     * {@code takeover}: Take over and Hand back switch who plays (story 5.8).
     */
    record Capabilities(boolean pacing, boolean takeover) {
        /** Everything UX-DR7 describes. */
        static final Capabilities ALL = new Capabilities(true, true);
        /** What is built: the pacing of story 5.7 and the takeover of story 5.8, which is everything UX-DR7 describes. */
        static final Capabilities NOW = new Capabilities(true, true);
    }

    private ControlEnablement() {
    }

    /** UX-DR7's matrix, with every control built. */
    static Set<Control> matrix(ModeState.Mode mode, boolean over) {
        return of(mode, over, Capabilities.ALL);
    }

    /** The controls that act in {@code mode}, less those {@code capabilities} says are not built yet. */
    static Set<Control> of(ModeState.Mode mode, boolean over, Capabilities capabilities) {
        Set<Control> enabled = EnumSet.noneOf(Control.class);
        if (over) {
            // Run over: every control but the Panel toggle (which is not in this row) is disabled.
            return enabled;
        }
        switch (mode) {
            case RUNNING -> enabled.addAll(EnumSet.of(Control.PAUSE_RESUME, Control.NEXT_STEP, Control.SPEED,
                    Control.INTERVAL, Control.TAKE_OVER, Control.EXPLAIN));
            case PAUSED -> enabled.addAll(EnumSet.allOf(Control.class));
            case HUMAN -> enabled.addAll(EnumSet.of(Control.TAKE_OVER, Control.EXPLAIN));
        }
        if (!capabilities.pacing()) {
            enabled.remove(Control.INTERVAL);
        }
        if (!capabilities.takeover()) {
            enabled.remove(Control.TAKE_OVER);
        }
        return enabled;
    }

    /** The first button's label: Pause while the bot is running, Resume when it is not. */
    static String pauseLabel(ModeState.Mode mode) {
        return mode == ModeState.Mode.RUNNING ? "Pause" : "Resume";
    }

    /** The takeover button's label: Hand back while the human plays, Take over otherwise. */
    static String takeOverLabel(ModeState.Mode mode) {
        return mode == ModeState.Mode.HUMAN ? "Hand back" : "Take over";
    }
}
