package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.ModeState.Mode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coach's switch (story 8.9, FR-47): off by default, a setting of HUMAN and not a Mode, disabled in RUNNING and
 * PAUSED with its reason in words, and recorded through the Run's seam once per switch. No game: the state is a
 * pure object over a seam, as {@link ControlState} is.
 */
class CoachStateTest {

    /** A seam that records what it was told and can refuse, as a Run the person does not play does. */
    private static final class Recorded implements CoachState.Seam {
        final List<Boolean> told = new ArrayList<>();
        boolean accepts = true;

        @Override
        public boolean coach(boolean on) {
            if (accepts) {
                told.add(on);
            }
            return accepts;
        }
    }

    @Test
    @DisplayName("coach is off by default, and a switch in HUMAN turns it on and off, telling the Run each time")
    void switches_in_human() {
        Recorded run = new Recorded();
        CoachState coach = new CoachState(run);
        assertFalse(coach.on(), "off by default");
        assertTrue(coach.toggle(Mode.HUMAN, false));
        assertTrue(coach.on());
        assertTrue(coach.toggle(Mode.HUMAN, false));
        assertFalse(coach.on());
        assertEquals(List.of(true, false), run.told);
    }

    @Test
    @DisplayName("RUNNING, PAUSED and a Run that is over refuse the switch, each with its own reason, and tell the Run nothing")
    void refused_outside_human() {
        Recorded run = new Recorded();
        CoachState coach = new CoachState(run);
        assertEquals(ControlEnablement.COACH_BOT_PLAYS, ControlEnablement.coachReason(Mode.RUNNING, false));
        assertEquals(ControlEnablement.COACH_PAUSED, ControlEnablement.coachReason(Mode.PAUSED, false));
        for (Mode mode : Mode.values()) {
            assertEquals(ControlEnablement.COACH_RUN_OVER, ControlEnablement.coachReason(mode, true), mode.name());
            assertFalse(coach.toggle(mode, true), mode.name() + " over");
        }
        assertFalse(coach.toggle(Mode.RUNNING, false));
        assertFalse(coach.toggle(Mode.PAUSED, false));
        assertNull(ControlEnablement.coachReason(Mode.HUMAN, false));
        assertFalse(coach.on());
        assertTrue(run.told.isEmpty(), "nothing was written to the log");
        // The words name what to do about it.
        for (String reason : List.of(ControlEnablement.COACH_BOT_PLAYS, ControlEnablement.COACH_PAUSED)) {
            assertTrue(reason.contains("take over"), reason);
        }
    }

    @Test
    @DisplayName("the coach goes off the frame the person stops playing, so a later takeover starts without it")
    void off_when_the_person_stops() {
        CoachState coach = new CoachState(new Recorded());
        coach.toggle(Mode.HUMAN, false);
        coach.sync(Mode.HUMAN, false);
        assertTrue(coach.on(), "still the person's turn");
        coach.sync(Mode.PAUSED, false);
        assertFalse(coach.on(), "handed back");
        coach.toggle(Mode.HUMAN, false);
        coach.sync(Mode.HUMAN, true);
        assertFalse(coach.on(), "the Run ended");
    }

    @Test
    @DisplayName("a Run that does not take the switch (the Brain's, not the person's) leaves the coach off")
    void a_refusing_run() {
        Recorded run = new Recorded();
        run.accepts = false;
        CoachState coach = new CoachState(run);
        assertFalse(coach.toggle(Mode.HUMAN, false));
        assertFalse(coach.on());
        assertTrue(CoachState.detached().toggle(Mode.HUMAN, false), "a Panel with no Run switches its own state only");
    }
}
