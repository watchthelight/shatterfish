package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Prison Guard's chain (story 10.10, issue #356, Rule {@code guard-chain}).
 *
 * <p>An awake Guard that has not used its chain pulls its enemy once, with no warning, when the enemy is in view,
 * not beside it, at most four cells away and on a clear straight line (Guard.java:179-187, :71-79); the hero lands on
 * the first free open cell on that line, crippled (Guard.java:82-93, :127). On its own that costs the hero little: it
 * is the approach the Guard would have made, and the hero strikes first either way. It costs a lot when the hero
 * stood where it was holding other mobs back (a door, a corridor): the pull takes it out of there into the room.
 * So when another awake enemy is in view and the hero is in the Guard's line, the hero steps to a cell the line
 * does not reach, not farther from the Guard than it is, and the Guard has to walk round to it.
 *
 * <p>Everything here is read from the Observation: the Guard's name and emote, the drawn tiles and the characters
 * drawn. The line is {@link Ranged#clear}, which differs from the game's by a cell here and there.
 */
final class Guard {

    /** The Guard's name on the screen. */
    static final String NAME = "prison guard";

    /** The farthest the chain reaches: {@code distance < 5} (actors/mobs/Guard.java:183). */
    static final int REACH = 4;

    private Guard() {
    }

    /** The awake Guard among {@code enemies}, or null. */
    static ActorView awake(List<ActorView> enemies) {
        for (ActorView enemy : enemies) {
            if (enemy.name().equals(NAME) && enemy.emote() != Emote.SLEEP) {
                return enemy;
            }
        }
        return null;
    }

    private static int distance(int width, int a, int b) {
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }

    /** Whether a Guard on {@code guard} could pull a hero standing on {@code cell}: two to four cells, a clear line. */
    static boolean pulls(Observation observation, int guard, int cell) {
        int distance = distance(observation.map().width(), guard, cell);
        return distance >= 2 && distance <= REACH && Ranged.clear(observation, guard, cell);
    }

    /**
     * The step out of the chain's line, or null: no awake Guard, a fight already on (an enemy beside the hero), the
     * hero not in the line, no other awake enemy in view while the fight is one the hero means to take (the pull is
     * then the approach it would have made), or no step that leaves the line.
     *
     * <p>{@code fleeing} is the Fight Policy's verdict that the fight is not favourable. A hero that is holding or
     * retreating from a Guard it does not mean to fight gains nothing from the pull: it is set down beside the Guard,
     * crippled for four turns (Guard.java:82-93, :127), and the retreat it was making is over. In the Runs of story
     * 10.10, 19 of the 24 heroes a Guard killed had been set down that way, 11 of them while holding in the line. So
     * when fleeing the hero steps to the cell out of the line that is farthest from the Guard, whoever else is in view.
     */
    static List<RunLog.Choice> engage(Observation observation, List<Action> offered, List<ActorView> enemies,
            boolean fleeing) {
        ActorView guard = awake(enemies);
        if (guard == null) {
            return null;
        }
        int hero = observation.hero().cell();
        if (!pulls(observation, guard.cell(), hero)) {
            return null;
        }
        boolean company = false;
        for (ActorView enemy : enemies) {
            if (enemy != guard && enemy.emote() != Emote.SLEEP) {
                company = true;
            }
            if (offered.contains(new Action.Attack(enemy.cell()))) {
                return null;
            }
        }
        if (!company && !fleeing) {
            return null;
        }
        int width = observation.map().width();
        int now = distance(width, guard.cell(), hero);
        Action best = null;
        int bestDistance = 0;
        for (Action action : offered) {
            if (!(action instanceof Action.Step step)) {
                continue;
            }
            int away = distance(width, guard.cell(), step.cell());
            if (away < 2 || (!fleeing && away > now) || pulls(observation, guard.cell(), step.cell())) {
                continue;
            }
            if (best == null || (fleeing ? away > bestDistance : away < bestDistance)) {
                best = action;
                bestDistance = away;
            }
        }
        return best == null ? null : new ArrayList<>(List.of(new RunLog.Choice(best, Policies.CERTAIN, "guard: dodge " + bestDistance)));
    }
}
