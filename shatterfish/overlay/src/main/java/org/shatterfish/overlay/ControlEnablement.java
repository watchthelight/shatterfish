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
 * <p>The states of UX-DR7 that add no row here (Brain error, no valid action, hero busy, no Run;
 * story 5.15) are held by {@code PanelStateTest}. Panel collapsed hides the whole row and changes no enablement: the
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
        /** Hands a quiet stretch to the bot from HUMAN (story 8.10, FR-47). */
        AUTOEXPLORE,
        EXPLAIN,
        /** Opens the Beliefs view, or closes it: everything the Brain believes, at a wait (story 8.6, FR-46). */
        BELIEFS
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
            // (RUNNING is the Brain moving on: the Beliefs view is for a wait that holds, so it is not here.)
            case PAUSED -> {
                enabled.addAll(EnumSet.allOf(Control.class));
                // Autoexplore starts from HUMAN: the person is the one who hands a stretch over (story 8.10).
                enabled.remove(Control.AUTOEXPLORE);
            }
            case HUMAN -> enabled.addAll(EnumSet.of(Control.TAKE_OVER, Control.AUTOEXPLORE, Control.EXPLAIN, Control.BELIEFS));
        }
        if (!capabilities.pacing()) {
            enabled.remove(Control.INTERVAL);
        }
        if (!capabilities.takeover()) {
            enabled.remove(Control.TAKE_OVER);
            // Autoexplore changes hands twice, so a Run that cannot change hands cannot do it.
            enabled.remove(Control.AUTOEXPLORE);
        }
        return enabled;
    }

    /** Said in the Mode strip when Explain is pressed with the Panel collapsed (story 8.2). */
    static final String EXPLAIN_PANEL_COLLAPSED = "Explain is off: the Panel is collapsed, restore it first";
    /** Said when Explain is pressed in a Run that is over. */
    static final String EXPLAIN_RUN_OVER = "Explain is off: the Run is over";
    /** Said when Explain is pressed before any Decision has landed. */
    static final String EXPLAIN_NO_DECISION = "Explain is off: no decision yet";

    /**
     * Why Explain cannot act, or null when it can (story 8.2). The expansion opens inside the Panel, so with
     * the Panel collapsed to the Mode strip there is nowhere to open it: the control is disabled and the
     * reason is shown, where the matrix of {@link #of} alone said only "enabled". A Run that is over and a
     * Run with no Decision yet are the matrix's own and the card's own, said in words here too.
     *
     * @param panelFull whether the Panel is drawn in full, not collapsed to the Mode strip
     */
    static String explainReason(ModeState.Mode mode, boolean over, boolean panelFull, boolean hasDecision) {
        if (!of(mode, over, Capabilities.NOW).contains(Control.EXPLAIN)) {
            return EXPLAIN_RUN_OVER;
        }
        if (!panelFull) {
            return EXPLAIN_PANEL_COLLAPSED;
        }
        return hasDecision ? null : EXPLAIN_NO_DECISION;
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
