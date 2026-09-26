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

    private ModeStripContent() {
    }

    /** The colour {@code mode}'s word is shown in; the word itself is what {@link #text} writes. */
    static int color(ModeState.Mode mode) {
        return switch (mode) {
            case RUNNING -> RUNNING_COLOR;
            case PAUSED -> PAUSED_COLOR;
            case HUMAN -> HUMAN_COLOR;
        };
    }

    /** The strip's whole line: mode, speed and interval, turn, floor, and THINKING when it applies. */
    static String text(ModeState state) {
        StringBuilder line = new StringBuilder();
        line.append(state.mode().name()).append("  ").append(speed(state));
        line.append("  turn ").append(Columns.number(state.turn(), TURN_WIDTH));
        line.append("  floor ").append(Columns.number(state.floor(), FLOOR_WIDTH));
        if (state.thinking()) {
            line.append("  THINKING");
        }
        return line.toString();
    }

    /** The speed mode's word, with its interval appended when that speed mode shows one (UX-DR6). */
    private static String speed(ModeState state) {
        ModeState.SpeedMode speed = state.speed();
        if (!speed.showsInterval) {
            return speed.word;
        }
        return speed.word + " " + Columns.seconds(state.intervalSeconds());
    }
}
