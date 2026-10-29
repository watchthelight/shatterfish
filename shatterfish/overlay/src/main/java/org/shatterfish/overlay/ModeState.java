package org.shatterfish.overlay;

import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.RunEnding;

/**
 * The Run's Mode and speed mode, as the Mode strip and the Decision card show them (story 5.3, FR-38,
 * UX-DR3, UX-DR6).
 *
 * <p>{@link #of} reads a Run with no controls bound: {@link Mode#RUNNING} at the placeholder
 * {@link SpeedMode#NORMAL}, or {@link Mode#HUMAN} for a HUMAN Run. A Brain's Run in the Overlay takes its
 * Mode, speed mode and interval from its {@code ControlState} instead (story 5.6, {@code ControlState#view}).
 *
 * <p>The placeholder's {@link #PLACEHOLDER_INTERVAL} is never shown as a
 * number -- {@code ModeStripContent} reads an em dash for it instead, so a placeholder cannot be
 * mistaken for a measurement (the review that found "normal 0.0s" reading as one) -- and stays only
 * as the field every {@link SpeedMode} carries, since {@code SpeedMode.HUMAN_PLAY}'s real interval is
 * story 5.7's. The turn, the floor and whether the Brain is thinking are real, read from the Run's
 * last served wait and its live state ({@link EmbeddedRun.Snapshot}).
 */
final class ModeState {

    /** ADR-0013's three Modes. */
    enum Mode {
        RUNNING, PAUSED, HUMAN,
        /** A loaded Run log is played back one Input wait at a time (story 8.5, FR-46). */
        REPLAY
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
        FAST("Fast", false),
        /** A HUMAN Run's: the person's own pace, which no speed mode sets (story 5.9). */
        PLAYER("player", false);

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
    private final boolean over;
    private final RunEnding ending;
    private final String pausedFor;
    private final String note;
    private final String replayNote;
    private final boolean replayPlaying;

    ModeState(Mode mode, SpeedMode speed, double intervalSeconds, int turn, int floor, boolean thinking) {
        this(mode, speed, intervalSeconds, turn, floor, thinking, false);
    }

    /** As above, and whether the Run is over, which the Controls row reads (story 5.6, UX-DR7's Run over row). */
    ModeState(Mode mode, SpeedMode speed, double intervalSeconds, int turn, int floor, boolean thinking, boolean over) {
        this(mode, speed, intervalSeconds, turn, floor, thinking, over, null);
    }

    /** This state with the Pause-on condition the Run is paused for, in words, or null (story 8.3). */
    ModeState withPausedFor(String condition) {
        return new ModeState(mode, speed, intervalSeconds, turn, floor, thinking, over, ending, condition, note,
                replayNote, replayPlaying);
    }

    /**
     * This state with the words autoexplore adds to the strip (story 8.10): "autoexplore" while a stretch runs, and
     * "autoexplore stopped, enemy in view" once control is back with the person. Null for none.
     */
    ModeState withNote(String words) {
        return new ModeState(mode, speed, intervalSeconds, turn, floor, thinking, over, ending, pausedFor, words,
                replayNote, replayPlaying);
    }

    /** This state with where a replay stands in its log, in words ("wait 12 of 340, stopped"), or null (story 8.5). */
    ModeState withReplay(String text, boolean playing) {
        return new ModeState(mode, speed, intervalSeconds, turn, floor, thinking, over, ending, pausedFor, note,
                text, playing);
    }

    /** As above, and how the Run ended and where its log is, which the Mode strip reads once it is over (story 5.14). */
    ModeState(Mode mode, SpeedMode speed, double intervalSeconds, int turn, int floor, boolean thinking, boolean over,
              RunEnding ending) {
        this(mode, speed, intervalSeconds, turn, floor, thinking, over, ending, null, null, null, false);
    }

    private ModeState(Mode mode, SpeedMode speed, double intervalSeconds, int turn, int floor, boolean thinking,
                      boolean over, RunEnding ending, String pausedFor, String note, String replayNote,
                      boolean replayPlaying) {
        if (mode == null || speed == null) {
            throw new IllegalArgumentException("a Mode and a speed mode");
        }
        this.mode = mode;
        this.speed = speed;
        this.intervalSeconds = intervalSeconds;
        this.turn = turn;
        this.floor = floor;
        this.thinking = thinking;
        this.over = over;
        this.ending = ending;
        this.pausedFor = pausedFor;
        this.note = note;
        this.replayNote = replayNote;
        this.replayPlaying = replayPlaying;
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
        if (snapshot.human() != null) {
            // A HUMAN Run (story 5.9) is HUMAN for its whole length; THINKING is the shadow still pending.
            return new ModeState(Mode.HUMAN, SpeedMode.PLAYER, PLACEHOLDER_INTERVAL, snapshot.turn(), snapshot.floor(),
                    snapshot.state() == EmbeddedRun.State.THINKING,
                    snapshot.state() == EmbeddedRun.State.ENDED, snapshot.ending());
        }
        return new ModeState(Mode.RUNNING, SpeedMode.NORMAL, PLACEHOLDER_INTERVAL, snapshot.turn(), snapshot.floor(),
                snapshot.state() == EmbeddedRun.State.THINKING, snapshot.state() == EmbeddedRun.State.ENDED,
                snapshot.ending());
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

    boolean over() {
        return over;
    }

    /** How the Run ended, or null while it plays (story 5.14). */
    RunEnding ending() {
        return ending;
    }

    /** The Pause-on condition the Run is paused for, in words ("before stairs"), or null (story 8.3). */
    String pausedFor() {
        return pausedFor;
    }

    /** What autoexplore adds to the strip (story 8.10): the stretch under way, or why it stopped; null for none. */
    String note() {
        return note;
    }

    /** Where a replay stands in its log, in words, or null for any other Run (story 8.5). */
    String replayNote() {
        return replayNote;
    }

    /** Whether a replay plays on its own now, which the Pause and Resume label reads (story 8.5). */
    boolean replayPlaying() {
        return replayPlaying;
    }
}
