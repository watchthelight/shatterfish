package org.shatterfish.brain;

import org.shatterfish.api.Heuristic;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
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

    /**
     * The buff a Goo hit leaves on one in three hits, by the name its icon carries (Goo.java:157-160,
     * Ooze.java; the name is the Ooze buff's "caustic ooze"): a point of damage a turn for its turns,
     * ended by standing in water (Ooze.java, act).
     */
    static final String OOZE = "caustic ooze";

    /** How far a pumped Goo reaches (Goo.java:146). */
    @Heuristic("goo-pump")
    static final int REACH = 2;

    /** The waits after an announcement a pump is still expected: the charge and the attack (Goo.java:180-186). */
    @Heuristic("goo-pump")
    static final int PUMP_WAITS = 3;

    /**
     * How near the end of the log an announcement may sit and still be this screen's: the pump-up's
     * line can be followed by others the same turn (Goo's yell, a hit, an ooze), and the charge and
     * the attack take the next two of Goo's turns (Goo.java:180-186).
     */
    @Heuristic("goo-pump")
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

    /**
     * Whether a pump announced with the hero on {@code hero} and Goo on {@code goo} could be dodged on
     * {@code walk}: some Step starts a plan {@link #plan} would finish. Goo farther than its reach
     * cannot pump at all.
     */
    static boolean dodgeable(MapSection map, boolean[] walk, int hero, int goo) {
        int now = distance(map, hero, goo);
        if (now > REACH) {
            return true;
        }
        for (int step : neighbours(map, hero)) {
            if (!walk[step] || step == goo) {
                continue;
            }
            int away = distance(map, step, goo);
            if (away > REACH) {
                return true;
            }
            if (now == 1 && away == REACH) {
                for (int next : neighbours(map, step)) {
                    if (walk[next] && next != goo && distance(map, next, goo) > REACH) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Where Goo will stand when it has come to the hero on {@code hero}: it steps toward the hero a cell a
     * turn, so this is the cell beside the hero a walk of nearest-to-the-hero neighbours ends on, ties to the
     * lower cell. A guess that holds on open ground; Goo's own walk is the game's path-finding (Mob.java,
     * getCloser).
     */
    static int arrival(MapSection map, boolean[] walk, int hero, int goo) {
        int width = map.width();
        int at = goo;
        for (int turn = 0; turn < 24 && distance(map, at, hero) > 1; turn++) {
            int best = -1;
            long bestScore = Long.MAX_VALUE;
            for (int next : neighbours(map, at)) {
                if (!walk[next] || next == hero) {
                    continue;
                }
                long dx = next % width - hero % width;
                long dy = next / width - hero / width;
                long score = distance(map, next, hero) * 1000L + dx * dx + dy * dy;
                if (score < bestScore) {
                    bestScore = score;
                    best = next;
                }
            }
            if (best < 0 || bestScore >= distance(map, at, hero) * 1000L + 1000L) {
                break;
            }
            at = best;
        }
        return at;
    }

    /**
     * The Step toward nearer ground to meet Goo on, when the ground the hero stands on is ground a pump
     * could not be dodged on where Goo will stand ({@link #arrival}, {@link #dodgeable}); else null. A
     * pump is dodged by walking straight away from Goo, two cells, so a hero with its back to a wall or in
     * a pocket can only take it (issue #219). Goo's walk toward the hero costs it its turns, so walking to
     * better ground before it arrives, or out of its reach, costs the hero no blow of Goo's: the walk never
     * ends beside Goo and never passes beside it. It goes to the nearest such ground within
     * the Playbook's {@code meet_range} Steps, the roomiest of those, ties to the lower cell. None in reach:
     * null, and the fight goes on where it is.
     */
    static RunLog.Choice position(Observation observation, Memory memory, List<Action> offered, int goo,
                                  Playbook.Goo entry) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory, false);
        if (dodgeable(map, walk, observation.hero().cell(), arrival(map, walk, observation.hero().cell(), goo))) {
            return null;
        }
        return walkTo(map, walk, observation.hero().cell(), offered, goo, entry.meetRange(),
                cell -> dodgeable(map, walk, cell, arrival(map, walk, cell, goo)), "goo: position");
    }

    /**
     * The Step toward dry ground to meet Goo on (story, issue #429). Goo regains hit points on every act it ends
     * standing in water (Goo.java:109-111), so a blow struck at a Goo in water is partly given back. Where Goo
     * will stand to fight the hero ({@link #arrival}, or its own cell once it is beside the hero) is read from the
     * shown terrain; when that is water the hero walks to the nearest ground, within the Playbook's
     * {@code meet_range} Steps, whose arrival cell is dry and where a pump could be dodged. Beside a Goo that is
     * hurt the walk is at most two Steps: Goo spends its turns following, so the walk costs the hero no blow and
     * Goo no heal, and it ends with Goo beside it on dry ground and the first blow the hero's. The walk never ends
     * or passes beside Goo, as {@link #position}'s, and it is made only with no pump pending. Null when Goo will
     * fight on dry ground, when a full-health Goo is already beside the hero, or when there is no such ground.
     */
    static RunLog.Choice dry(Observation observation, Memory memory, List<Action> offered, ActorView goo,
                             Playbook.Goo entry) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory, false);
        int hero = observation.hero().cell();
        boolean beside = distance(map, hero, goo.cell()) <= 1;
        int at = beside ? goo.cell() : arrival(map, walk, hero, goo.cell());
        if (map.tiles().get(at) != org.shatterfish.api.Tile.WATER
                || (beside && goo.healthPips() >= org.shatterfish.api.ObservationCodec.MAX_HEALTH_PIPS)) {
            return null;
        }
        return walkTo(map, walk, hero, offered, goo.cell(), beside ? Math.min(2, entry.meetRange()) : entry.meetRange(),
                cell -> {
                    int there = arrival(map, walk, cell, goo.cell());
                    return map.tiles().get(there) != org.shatterfish.api.Tile.WATER && dodgeable(map, walk, cell, there);
                }, "goo: dry");
    }

    /**
     * Whether caustic ooze is on the hero, by its buff icon. The icon's turns are not read: the Observation
     * does not show them for this buff, and a Goo hit sets them back to 20 (Ooze.java:32, :79-83; Goo.java:157-160).
     */
    static boolean oozed(Observation observation) {
        for (org.shatterfish.api.BuffView buff : observation.hero().buffs()) {
            if (buff.name().equals(OOZE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Step toward the nearest water to stand in, when caustic ooze is on the hero and the hero is not in
     * water, within the Playbook's {@code wash_range} Steps (ooze costs a point a turn for up to 20 turns, and
     * the walk is made while Goo is following, so it costs a point a Step and no blow). Ooze costs a point a turn and is washed off in water
     * (Ooze.java, act), and in water an ooze put on again costs one point and is washed off in turn. The
     * walk is as {@link #position}'s: it never ends or passes beside Goo, so it costs no blow of Goo's, and
     * it ends where a pump could be dodged (issue #219).
     */
    static RunLog.Choice wash(Observation observation, Memory memory, List<Action> offered, int goo,
                              Playbook.Goo entry) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        if (!oozed(observation) || map.tiles().get(hero) == org.shatterfish.api.Tile.WATER) {
            return null;
        }
        boolean[] walk = Explore.walkable(observation, memory, false);
        return walkTo(map, walk, hero, offered, goo, entry.washRange(),
                cell -> map.tiles().get(cell) == org.shatterfish.api.Tile.WATER
                        && dodgeable(map, walk, cell, arrival(map, walk, cell, goo)), "goo: wash");
    }

    /**
     * The first Step of the shortest walk of at most {@code range} Steps to a cell {@code goal} accepts, by
     * cells that are not beside Goo; of the goals at the shortest length the roomiest, ties to the lower
     * cell. Null when there is none.
     */
    private static RunLog.Choice walkTo(MapSection map, boolean[] walk, int hero, List<Action> offered, int goo, int range,
                                        java.util.function.IntPredicate goal, String why) {
        int cells = walk.length;
        int[] first = new int[cells];
        int[] steps = new int[cells];
        java.util.Arrays.fill(steps, -1);
        steps[hero] = 0;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < cells && walk[step.cell()] && steps[step.cell()] < 0
                    && distance(map, step.cell(), goo) >= 2) {
                steps[step.cell()] = 1;
                first[step.cell()] = step.cell();
                queue.add(step.cell());
            }
        }
        int bestOpen = -1;
        int bestCell = -1;
        int depth = -1;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            if (depth >= 0 && steps[cell] > depth) {
                break;
            }
            if (steps[cell] <= range && goal.test(cell)) {
                depth = steps[cell];
                int open = openness(map, walk, cell);
                if (open > bestOpen || (open == bestOpen && cell < bestCell)) {
                    bestOpen = open;
                    bestCell = cell;
                }
                continue;
            }
            if (steps[cell] >= range) {
                continue;
            }
            for (int next : neighbours(map, cell)) {
                if (walk[next] && steps[next] < 0 && distance(map, next, goo) >= 2) {
                    steps[next] = steps[cell] + 1;
                    first[next] = first[cell];
                    queue.add(next);
                }
            }
        }
        return bestCell < 0 ? null : new RunLog.Choice(new Action.Step(first[bestCell]), Policies.CERTAIN, why);
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
     * The hit points at or under which the hero drinks against Goo alone: the Playbook's
     * {@code drink_per_mille} of the maximum. An assumption, not a Codex fact. A plain hit does 1 to 8,
     * 1 to 12 once Goo is below half its hit points, and only a pump does more (3 to 24, 3 to 36;
     * Goo.java:67-81); the potion heals {@code 0.8 * HT + 14} over several turns (Heal), so a drink at 40%
     * leaves little of it to waste and the hero out of the range of a plain hit.
     */
    static int danger(org.shatterfish.api.HeroSection hero, Playbook.Goo entry) {
        return entry.danger(hero.ht());
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
     * hero stays: it dodges an announced pump when it can ({@link #dodge}); with no pump pending it
     * walks to water when caustic ooze is on it ({@link #wash}) and to ground a pump could be dodged on
     * when it stands where one could not ({@link #position}); it strikes an adjacent Goo, and otherwise
     * waits for Goo to come to it, which gives it the first blow, and goes to Goo only after
     * {@link Fight#HOLDS} waits of holding. Null when this is not that fight.
     */
    static List<RunLog.Choice> engage(Observation observation, Memory memory, List<Action> offered,
                                      List<ActorView> enemies, Playbook.Goo entry) {
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
        if (dodge == null && memory.pump() != goo.cell()) {
            RunLog.Choice position = wash(observation, memory, offered, goo.cell(), entry);
            if (position == null) {
                position = position(observation, memory, offered, goo.cell(), entry);
            }
            if (position == null) {
                position = dry(observation, memory, offered, goo, entry);
            }
            if (position != null) {
                ranked.add(position);
                return ranked;
            }
        }
        Action attack = new Action.Attack(goo.cell());
        if (!offered.contains(attack)) {
            RunLog.Choice volley = volley(observation, memory, offered, goo);
            if (volley != null) {
                ranked.add(new RunLog.Choice(volley.action(), ranked.isEmpty() ? Policies.CERTAIN : 0, volley.why()));
                return ranked;
            }
        }
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

    /**
     * A shot at Goo while it comes (story 10.38). Goo walks to the hero a cell a turn and attacks only from
     * beside it, or two cells off once pumped, and a pump starts only from beside the hero (Goo.java:141-152,
     * :178-226, Mob.java:1383-1392), so every turn it spends on the way is a turn the hero strikes for nothing:
     * the staff's bolt, the bow's arrow, or the best plain missile in the pack, in that order, at the first
     * cell the line is clear, from two cells off or more. Beside Goo nothing changes: the blow is the melee
     * one. The fight Policy used to wait for Goo instead, and the staff, the bow and the missiles were kept
     * out of the fight with Goo in view; each is the same Choice the policy of its name makes, with the reason
     * {@code goo: volley}. Null when there is no ammunition, no clear line, or Goo is beside the hero.
     */
    static RunLog.Choice volley(Observation observation, Memory memory, List<Action> offered, ActorView goo) {
        int hero = observation.hero().cell();
        if (distance(observation.map(), hero, goo.cell()) < 2 || !Ranged.clear(observation, hero, goo.cell())) {
            return null;
        }
        String why = "goo: volley ";
        RunLog.Choice shot = Zap.zapAt(observation, memory, offered, goo, why + "zap: ");
        if (shot == null) {
            shot = Shoot.shotAt(observation, offered, goo, why + "shoot: ");
        }
        if (shot == null) {
            shot = Ranged.throwAt(observation, offered, goo, why + "throw: ", (item, tier) -> true);
        }
        return shot;
    }

    private static int distance(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }
}
