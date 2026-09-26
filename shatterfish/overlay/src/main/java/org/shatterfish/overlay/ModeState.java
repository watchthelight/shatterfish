package org.shatterfish.overlay;

import org.shatterfish.harness.agent.EmbeddedRun;

/**
 * The Run's Mode and speed mode, as the Mode strip and the Decision card show them (story 5.3, FR-38,
 * UX-DR3, UX-DR6).
 *
 * <p>Pause (story 5.5), takeover (5.8) and the speed controls (5.6, 5.7) are not built yet, so nothing
 * today changes this Run's {@link Mode} or paces it: {@link #of} always reads {@link Mode#RUNNING} at
 * the placeholder {@link SpeedMode#NORMAL}, whose interval is {@link #PLACEHOLDER_INTERVAL} -- a
 * stand-in this class documents rather than hides, since {@code SpeedMode.HUMAN_PLAY}'s real interval
 * is story 5.7's. The turn, the floor and whether the Brain is thinking are real, read from the Run's
 * last served wait and its live state ({@link EmbeddedRun.Snapshot}).
 */
final class ModeState {

    /** ADR-0013's three Modes. Only {@link #RUNNING} is reachable before story 5.5 (PAUSED) and 5.8 (HUMAN). */
    enum Mode {
        RUNNING, PAUSED, HUMAN
    }

    /** UX-DR6's four speed modes, plus {@link #NORMAL}, today's placeholder. */
    enum SpeedMode {
        /** Nothing paces a Run yet (stories 5.6, 5.7): the word this story shows until they do. */
        NORMAL("normal", true),
        /** The stepping speed; a caller constructs this today, since 5.6 has not wired its control yet. */
        NEXT_STEP("Next Step", false),
        /** The paced speed; its interval is real only from story 5.7. */
        HUMAN_PLAY("Human", true),
        /** The uncapped speed. */
        FAST("Fast", false);

        final String word;
        final boolean showsInterval;

        SpeedMode(String word, boolean showsInterval) {
            this.word = word;
            this.showsInterval = showsInterval;
        }
    }

    /** The placeholder interval {@link SpeedMode#NORMAL} shows until story 5.7 gives the Overlay a real one. */
    static final double PLACEHOLDER_INTERVAL = 0.0;

    private final Mode mode;
    private final SpeedMode speed;
    private final double intervalSeconds;
    private final int turn;
    private final int floor;
    private final boolean thinking;

    ModeState(Mode mode, SpeedMode speed, double intervalSeconds, int turn, int floor, boolean thinking) {
        if (mode == null || speed == null) {
            throw new IllegalArgumentException("a Mode and a speed mode");
        }
        this.mode = mode;
        this.speed = speed;
        this.intervalSeconds = intervalSeconds;
        this.turn = turn;
        this.floor = floor;
        this.thinking = thinking;
    }

    /**
     * {@code snapshot} as this story shows it: always RUNNING at the placeholder NORMAL speed, with the
     * real turn, floor and thinking flag from the Run's last served wait; {@code null} (no Run attached
     * yet, or nothing served) reads as turn 0, floor 0, not thinking.
     */
    static ModeState of(EmbeddedRun.Snapshot snapshot) {
        if (snapshot == null) {
            return new ModeState(Mode.RUNNING, SpeedMode.NORMAL, PLACEHOLDER_INTERVAL, 0, 0, false);
        }
        return new ModeState(Mode.RUNNING, SpeedMode.NORMAL, PLACEHOLDER_INTERVAL, snapshot.turn(), snapshot.floor(),
                snapshot.state() == EmbeddedRun.State.THINKING);
    }

    Mode mode() {
        return mode;
    }

    SpeedMode speed() {
        return speed;
    }

    double intervalSeconds() {
        return intervalSeconds;
    }

    int turn() {
        return turn;
    }

    int floor() {
        return floor;
    }

    boolean thinking() {
        return thinking;
    }
}
