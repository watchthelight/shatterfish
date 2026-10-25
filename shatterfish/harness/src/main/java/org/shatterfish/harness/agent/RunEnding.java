package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Rankings;

import java.nio.file.Path;
import java.util.Set;

/**
 * How an embedded Run ended, in the words the Panel's Mode strip shows (story 5.14, UX-DR7's Run over
 * row): the cause, the turn and the floor the Run reached, and where its log is.
 *
 * <p><b>Where the cause comes from.</b> For a death or a win it is the game's own record: the ranking
 * the game added when the hero died or won ({@code core/.../Dungeon.java:872-888}), worded the way the
 * game words it in its rankings screen ({@code core/.../Rankings.java:517-534}, {@code Record.desc()}),
 * read once, after the Run is over. It is read for the Panel only. It reaches no Observation and the
 * Brain never sees it: the Run is over, and nothing decides after it. A Run that ended for any other
 * reason says so in a few plain words of the Overlay's own.
 *
 * @param cause the cause, lower-case first letter, no full stop: "killed by Goo", "turn cap reached"
 * @param turn  the turns the game counted
 * @param floor the deepest floor reached, as the game counts it
 * @param log   the Run's log, or null for a Run with none
 */
public record RunEnding(String cause, int turn, int floor, Path log) {

    public RunEnding {
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("a Run ends for a stated cause");
        }
    }

    /**
     * The ending of {@code outcome}. {@code rankingsBefore} is the ids of the rankings the Profile held
     * when the Run attached, which tells the ranking this Run added from the ones it held already.
     */
    static RunEnding of(RunOutcome outcome, Set<String> rankingsBefore, Path log) {
        return new RunEnding(cause(outcome, rankingsBefore), outcome.turns(), outcome.depth(), log);
    }

    private static String cause(RunOutcome outcome, Set<String> rankingsBefore) {
        return switch (outcome.cause()) {
            case DEATH -> fromTheGame(rankingsBefore, "died");
            case WIN -> fromTheGame(rankingsBefore, "won");
            case TURN_CAP -> "turn cap reached";
            case STALLED -> "stalled: waits passed with no turn";
            case REFUSED -> "stopped: the game refused every Action";
            case NOTHING_OFFERED -> "stopped: no Action was offered";
            case BRAIN_ERROR -> "brain error";
            case UNSERVED_SCENE -> "stopped: a screen the Overlay does not serve";
            case UNKNOWN_WINDOW -> "stopped: a window the Overlay does not know";
            case CLOSED -> "window closed";
            case SUSPENDED -> "saved and quit";
        };
    }

    /** The ids of the games the game's rankings hold now, so a ranking this Run adds can be told from an old one. */
    static Set<String> rankingsNow() {
        Set<String> ids = new java.util.HashSet<>();
        try {
            if (Rankings.INSTANCE.records != null) {
                for (Rankings.Record record : Rankings.INSTANCE.records) {
                    ids.add(record.gameID);
                }
            }
        } catch (RuntimeException unreadable) {
            // The Panel says the plain word instead.
        }
        return ids;
    }

    /** The first letter in lower case, which is how the Mode strip runs the cause into its line. */
    static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String fromTheGame(Set<String> rankingsBefore, String otherwise) {
        try {
            if (Rankings.INSTANCE.records != null) {
                for (Rankings.Record record : Rankings.INSTANCE.records) {
                    if (!rankingsBefore.contains(record.gameID)) {
                        String said = record.desc();
                        if (said != null && !said.isBlank()) {
                            return lowerFirst(said.strip());
                        }
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            // The Panel says the plain word below instead; a Run's ending never depends on this read.
        }
        return otherwise;
    }
}
