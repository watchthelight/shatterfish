package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.List;

/**
 * The first boss's one tell (story 4.13): Goo announces a pump-up in the game log, and its next
 * attack is a pumped one.
 *
 * <p><b>The mechanics.</b> A pump-up is announced as "Goo is pumping itself up!" (Goo.java:221-224,
 * actors.properties:1699). A pumped attack doubles Goo's accuracy and triples its damage roll
 * (Goo.java:67-89) and reaches two cells along a clear line (Goo.java:141-152): up to 24 hit points,
 * 36 once Goo is below half its hit points, against a hero of 40 to 50. But the pump is dropped the
 * moment Goo has to step (getCloser and getFurther reset it, Goo.java:244-258). So a hero that steps
 * out of its reach when the pump is announced makes Goo come after it, and the pump is gone.
 *
 * <p><b>What the Brain reads.</b> The game log, which the Observation carries (ADR-0006), and Goo's
 * cell as drawn: never Goo's state. The {@link Memory} keeps a hash of the log's lines, so an
 * announcement is acted on when the log has changed, and Goo's cell when it was made: once Goo has
 * moved, or after {@link #PUMP_WAITS} waits, the pump is dropped. An announcement still among the last
 * {@link #RECENT} lines when a later line arrives renews the pump at Goo's cell; that is within the
 * turns the pump takes to land.
 */
final class Goo {

    /** The boss's name as the screen shows it (actors.properties:1695). */
    static final String NAME = "Goo";

    /** The announcement (actors.properties:1699). */
    static final String PUMP = "Goo is pumping itself up!";

    /** How far a pumped Goo reaches (Goo.java:146). */
    static final int REACH = 2;

    /** The waits after an announcement a pump is still expected: the charge and the attack (Goo.java:180-186). */
    static final int PUMP_WAITS = 3;

    /**
     * How near the end of the log an announcement may sit and still be this screen's: the pump-up's
     * line can be followed by others the same turn (Goo's yell, a hit, an ooze), and the charge and
     * the attack take the next two of Goo's turns (Goo.java:180-186).
     */
    static final int RECENT = 3;

    private Goo() {
    }

    /**
     * A hash of every line the log section shows: it changes when a line is added, also once the
     * section is full and the oldest line drops (LogSection.MAX_LINES; GameLogListener.java:98-101),
     * since every line then moves up one. Only a window of identical lines would hash the same.
     */
    static int tail(Observation observation) {
        int hash = 1;
        for (LogLine line : observation.log().lines()) {
            hash = 31 * hash + line.text().hashCode();
        }
        return hash;
    }

    /** Whether one of the log's last {@link #RECENT} lines announces a pump-up. */
    static boolean announced(Observation observation) {
        List<LogLine> lines = observation.log().lines();
        for (LogLine line : lines.subList(Math.max(0, lines.size() - RECENT), lines.size())) {
            if (line.text().equals(PUMP)) {
                return true;
            }
        }
        return false;
    }

    /** Goo's cell as drawn, or -1 when it is not in view. */
    static int cell(Observation observation) {
        for (ActorView actor : observation.actors().actors()) {
            if (actor.name().equals(NAME)) {
                return actor.cell();
            }
        }
        return -1;
    }

    /**
     * A Step away from a pumped Goo, when a pump is pending and Goo is within its reach: the offered
     * Step that puts the hero farthest from Goo, if farther than it stands; else null. From beside
     * Goo one Step is not out of reach, but the pump takes Goo two turns, a charge and the attack
     * (Goo.java:180-186), so two Steps are: the second wait's dodge finishes it.
     */
    static RunLog.Choice dodge(Observation observation, Memory memory, List<Action> offered) {
        int goo = cell(observation);
        if (goo < 0 || memory.pump() != goo) {
            return null;
        }
        MapSection map = observation.map();
        int now = distance(map, observation.hero().cell(), goo);
        if (now > REACH) {
            return null;
        }
        boolean[] walk = Explore.walkable(observation, memory, false);
        Action best = null;
        int farthest = now;
        for (Action action : offered) {
            if (action instanceof Action.Step step && walk[step.cell()]) {
                int away = distance(map, step.cell(), goo);
                if (away > farthest) {
                    best = step;
                    farthest = away;
                }
            }
        }
        return best == null ? null : new RunLog.Choice(best, Policies.CERTAIN, "dodge: pump");
    }

    private static int distance(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }
}
