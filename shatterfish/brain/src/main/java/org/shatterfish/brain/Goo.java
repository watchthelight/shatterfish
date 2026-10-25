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
     * The Step that starts a dodge of a pending pump, when one is pending and Goo is within its reach;
     * else null. The pump takes Goo two more turns, a charge and the attack (Goo.java:178-186), and it
     * charges only while the hero is within {@link #REACH} cells (Goo.java:141-152, :179-186); once the
     * hero stands farther, Goo has to step and the pump is dropped (Goo.java:244-250). So the hero needs
     * two Steps from beside Goo to the third cell away from it, and one from two cells off. A Step
     * counts only when the walk can finish: beside Goo, the cell of the first Step is two away and a
     * neighbour of it three away. Of the plans that finish, the one that ends in the most open ground
     * wins ({@link #openness}), so the next dodge has room, ties to the lower cell.
     */
    static RunLog.Choice dodge(Observation observation, Memory memory, List<Action> offered) {
        int goo = cell(observation);
        if (goo < 0 || memory.pump() != goo) {
            return null;
        }
        Action.Step step = plan(observation, memory, offered, goo);
        return step == null ? null : new RunLog.Choice(step, Policies.CERTAIN, "dodge: pump");
    }

    private static Action.Step plan(Observation observation, Memory memory, List<Action> offered, int goo) {
        MapSection map = observation.map();
        int now = distance(map, observation.hero().cell(), goo);
        if (now > REACH) {
            return null;
        }
        boolean[] walk = Explore.walkable(observation, memory, false);
        Action.Step best = null;
        int bestOpen = -1;
        for (Action action : offered) {
            if (!(action instanceof Action.Step step) || step.cell() >= walk.length || !walk[step.cell()]) {
                continue;
            }
            int away = distance(map, step.cell(), goo);
            int open;
            if (away > REACH) {
                open = openness(map, walk, step.cell());
            } else if (now == 1 && away == REACH) {
                open = -1;
                for (int next : neighbours(map, step.cell())) {
                    if (walk[next] && distance(map, next, goo) > REACH) {
                        open = Math.max(open, openness(map, walk, next));
                    }
                }
                if (open < 0) {
                    continue;
                }
            } else {
                continue;
            }
            if (open > bestOpen || (open == bestOpen && best != null && step.cell() < best.cell())) {
                best = step;
                bestOpen = open;
            }
        }
        return best;
    }

    /** How many cells within two of {@code cell} the hero may walk on: the room a dodge has to go on. */
    static int openness(MapSection map, boolean[] walk, int cell) {
        int width = map.width();
        int open = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = cell % width + dx;
                int ny = cell / width + dy;
                if (nx >= 0 && ny >= 0 && nx < width && ny < map.height() && walk[nx + ny * width]) {
                    open++;
                }
            }
        }
        return open;
    }

    private static List<Integer> neighbours(MapSection map, int cell) {
        int width = map.width();
        List<Integer> cells = new java.util.ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cell % width + dx;
                int ny = cell / width + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()) {
                    cells.add(nx + ny * width);
                }
            }
        }
        return cells;
    }

    /**
     * The share of the hero's hit points, in thousandths, at or under which a potion of healing is
     * drunk against Goo alone. An assumption, not a Codex fact. A plain hit does 1 to 8, 1 to 12 once
     * Goo is below half its hit points, and only a pump does more (3 to 24, 3 to 36; Goo.java:67-81);
     * the potion heals {@code 0.8 * HT + 14} over several turns (Heal), so a drink at 40% leaves
     * little of it to waste and the hero out of the range of a plain hit.
     */
    static final int DRINK_PER_MILLE = 400;

    /** The hit points at or under which the hero drinks against Goo alone. */
    static int danger(org.shatterfish.api.HeroSection hero) {
        return (int) ((long) hero.ht() * DRINK_PER_MILLE + 999) / 1000;
    }

    /** The awake Goo among {@code enemies}, when it is the only one: the fight {@link #engage} plays. Else null. */
    static ActorView alone(List<ActorView> enemies) {
        if (enemies.size() != 1) {
            return null;
        }
        ActorView goo = enemies.get(0);
        return goo.name().equals(NAME) && goo.emote() != org.shatterfish.api.Emote.SLEEP ? goo : null;
    }

    /**
     * The fight with an awake Goo, the only enemy in view (issue #219). Goo's seal starts the turn it
     * wakes (Goo.java:134-136) and refuses every transition, so a retreat cannot break contact. So the
     * hero stays: it dodges an announced pump when it can ({@link #dodge}), strikes an adjacent Goo,
     * and otherwise waits for Goo to come to it, which gives it the first blow, and goes to Goo only
     * after {@link Fight#HOLDS} waits of holding. Null when this is not that fight.
     */
    static List<RunLog.Choice> engage(Observation observation, Memory memory, List<Action> offered,
                                      List<ActorView> enemies) {
        ActorView goo = alone(enemies);
        if (goo == null) {
            return null;
        }
        // Before the seal a hurt hero may still leave by the stairs; sealed, or above the low-health
        // warning, there is nothing to leave for.
        org.shatterfish.api.HeroSection hero = observation.hero();
        if (!observation.header().sealed() && 1000L * hero.hp() < 334L * hero.ht()) {
            return null;
        }
        List<RunLog.Choice> ranked = new java.util.ArrayList<>();
        RunLog.Choice dodge = memory.pump() == goo.cell() ? dodge(observation, memory, offered) : null;
        if (dodge != null) {
            ranked.add(dodge);
        }
        Action attack = new Action.Attack(goo.cell());
        if (offered.contains(attack)) {
            ranked.add(new RunLog.Choice(attack, ranked.isEmpty() ? Policies.CERTAIN : 0, "goo: attack"));
            return ranked;
        }
        if (memory.holds() < Fight.HOLDS) {
            for (Action wait : List.of(new Action.Wait(), new Action.Search())) {
                if (offered.contains(wait)) {
                    ranked.add(new RunLog.Choice(wait, ranked.isEmpty() ? Policies.CERTAIN : 0, "goo: wait"));
                    return ranked;
                }
            }
        }
        RunLog.Choice approach = Fight.approach(observation, memory, offered, enemies);
        if (approach != null) {
            ranked.add(new RunLog.Choice(approach.action(), ranked.isEmpty() ? Policies.CERTAIN : 0, "goo: " + approach.why()));
        }
        return ranked.isEmpty() ? null : ranked;
    }

    private static int distance(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }
}
