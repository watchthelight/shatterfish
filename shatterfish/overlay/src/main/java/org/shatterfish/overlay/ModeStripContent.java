package org.shatterfish.overlay;

/**
 * The Mode strip's one line (story 5.3, FR-38, UX-DR3, UX-DR14): the mode word in its colour, the
 * speed mode with its interval, the turn and the floor, and a {@code THINKING} suffix when the Brain
 * is over budget. Every value is a word or a number in the line; the colour {@link #color} names is
 * never the only thing that says the Mode (UX-DR14).
 */
final class ModeStripContent {

    /** Up to five digits: the Glossary's turn cap is 20,000 (epics.md, "the turn cap of 20,000"). */
    static final int TURN_WIDTH = 5;
    /** Up to two digits: the deepest floor is 26 (25 dungeon floors and the surface at 0 is not shown). */
    static final int FLOOR_WIDTH = 2;

    /** The Mode strip colour tokens ({@code DESIGN.md} Colors): running green, paused amber, human blue. */
    static final int RUNNING_COLOR = 0x66DD66;
    static final int PAUSED_COLOR = 0xFFD34D;
    static final int HUMAN_COLOR = 0x7FB8FF;

    /**
     * What {@link ModeState.SpeedMode#NORMAL} shows instead of an interval, since it never had one: an
     * em dash reads as "no value here" (UX-DR14, a state in words), where a number -- even a placeholder
     * {@code 0.0s} -- reads as a measurement nobody made. Story 5.7 replaces this with the real interval.
     */
    static final String NO_INTERVAL_YET = "—";

    private ModeStripContent() {
    }

    /**
     * The colour of a Run that is over (story 5.14): the neutral ink, since the Run is in no Mode. Not red,
     * which {@code DESIGN.md} keeps for Danger and the oracle; the words say what happened.
     */
    static final int OVER_COLOR = 0xF0F0F0;

    /** The words that open a finished Run's line (UX-DR7's Run over row). */
    static final String RUN_OVER = "RUN OVER";

    /** The colour {@code mode}'s word is shown in; the word itself is what {@link #text} writes. */
    static int color(ModeState.Mode mode) {
        return switch (mode) {
            case RUNNING -> RUNNING_COLOR;
            case PAUSED -> PAUSED_COLOR;
            case HUMAN -> HUMAN_COLOR;
        };
    }

    /** The colour the strip's line is shown in: the Mode's, or the neutral ink once the Run is over. */
    static int color(ModeState state) {
        return state.over() ? OVER_COLOR : color(state.mode());
    }

    /**
     * The strip's whole line: mode, speed and interval, turn, floor, and THINKING when it applies; or, once
     * the Run is over, {@code RUN OVER: killed by Goo, turn 1,842, floor 5} (EXPERIENCE.md, Flow 3), the cause
     * as the game's own record words it (story 5.14). A Run that is over with no ending to read says only
     * {@code RUN OVER}.
     */
    static String text(ModeState state) {
        if (state.over()) {
            return over(state);
        }
        StringBuilder line = new StringBuilder();
        line.append(state.mode().name()).append("  ").append(speed(state));
        line.append("  turn ").append(Columns.number(state.turn(), TURN_WIDTH));
        line.append("  floor ").append(Columns.number(state.floor(), FLOOR_WIDTH));
        if (state.thinking()) {
            line.append("  THINKING");
        }
        return line.toString();
    }

    private static String over(ModeState state) {
        org.shatterfish.harness.agent.RunEnding ending = state.ending();
        if (ending == null) {
            return RUN_OVER;
        }
        return RUN_OVER + ": " + ending.cause() + ", turn " + String.format(java.util.Locale.ROOT, "%,d", ending.turn())
                + ", floor " + ending.floor();
    }

    /** The speed mode's word, with its interval appended when that speed mode shows one (UX-DR6). */
    private static String speed(ModeState state) {
        ModeState.SpeedMode speed = state.speed();
        if (!speed.showsInterval) {
            return speed.word;
        }
        String interval = speed == ModeState.SpeedMode.NORMAL ? NO_INTERVAL_YET : Columns.seconds(state.intervalSeconds());
        return speed.word + " " + interval;
    }
}
