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
 * cell as drawn: never Goo's state. The {@link Memory} keeps a hash of the log's last lines, so an
 * announcement is acted on once, and Goo's cell when it was made: once Goo has moved, the pump is
 * dropped.
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

    /** The log's last lines the tail hash covers. */
    private static final int TAIL = 4;

    private Goo() {
    }

    /** A hash of the log's last lines: it changes when a line is added. */
    static int tail(Observation observation) {
        List<LogLine> lines = observation.log().lines();
        int hash = lines.size();
        for (LogLine line : lines.subList(Math.max(0, lines.size() - TAIL), lines.size())) {
            hash = 31 * hash + line.text().hashCode();
        }
        return hash;
    }

    /** Whether the log's last line announces a pump-up. */
    static boolean announced(Observation observation) {
        List<LogLine> lines = observation.log().lines();
        return !lines.isEmpty() && lines.get(lines.size() - 1).text().equals(PUMP);
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
