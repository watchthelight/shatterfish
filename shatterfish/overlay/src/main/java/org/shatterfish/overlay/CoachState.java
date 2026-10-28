package org.shatterfish.overlay;

import org.shatterfish.overlay.ModeState.Mode;

/**
 * Whether the coach is on (story 8.9, FR-47, UJ-4). Coach mode is a setting of HUMAN, not a fourth Mode: while the
 * person plays, the Brain's shadow Decision of each wait is made as it always is, and with the coach on the Panel
 * puts that Decision's sentence ({@link CoachLine}) over the Decision card. Nothing here reaches the game or the
 * Brain; switching writes one Mode record through {@link Seam}, of the existing kind.
 *
 * <p>The coach is off by default and off whenever the person is not the one playing: {@link #sync} turns it off
 * the frame the Mode is not HUMAN or the Run is over, so a Take over after a Hand back starts without it, and the
 * log never holds a coach record that the Mode strip did not show.
 */
final class CoachState {

    /** What switching does to the Run; {@code EmbeddedRun} is its only implementation outside tests. */
    interface Seam {
        /** Records the switch; false when the Run is not one the person plays. */
        boolean coach(boolean on);
    }

    private final Seam seam;
    private boolean on;

    CoachState(Seam seam) {
        this.seam = seam;
    }

    /** A coach that records nothing, for a Panel with no Run behind it. */
    static CoachState detached() {
        return new CoachState(on -> true);
    }

    boolean on() {
        return on;
    }

    /** Turns the coach off when the person is not playing; called once per frame with the Mode the Panel shows. */
    void sync(Mode mode, boolean over) {
        if (on && (mode != Mode.HUMAN || over)) {
            on = false;
        }
    }

    /**
     * Switches the coach, if {@link ControlEnablement#coachReason} allows it.
     *
     * @return whether it switched
     */
    boolean toggle(Mode mode, boolean over) {
        if (ControlEnablement.coachReason(mode, over) != null) {
            return false;
        }
        boolean next = !on;
        if (!seam.coach(next)) {
            return false;
        }
        on = next;
        return true;
    }
}
