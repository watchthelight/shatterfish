package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.TransitionView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Fight where only one enemy can reach the hero, or get away (story 4.7, FR-31).
 *
 * <p>The Policy enters while an enemy is in view and no Prompt is open, which is exactly when
 * {@link Explore} stands aside. It decides from the screen and the Codex's combat tables, one Action
 * per Input wait, always one the screen offers:
 *
 * <ul>
 *   <li><b>Attack</b> an adjacent enemy when the fight is favourable: the one it expects to kill
 *       soonest. The screen offers an {@code Attack} only on a cell an enemy it shows stands on
 *       ({@code ValidActions}), so the Policy never targets a character the Observation does not
 *       carry; remembered sightings are not targets.</li>
 *   <li><b>Take a chokepoint</b> when two or more enemies are in view and none is adjacent yet: step
 *       to the neighbouring cell from which the fewest enemies can engage, or hold there. A
 *       character attacks any of its eight neighbours ({@code Mob.canAttack},
 *       {@code Level.adjacent}), walls or no walls, so how many can engage a cell is how many of its
 *       eight neighbours an enemy can stand on.</li>
 *   <li><b>Approach</b> a lone enemy when the fight is favourable.</li>
 *   <li><b>Retreat</b> when it is not: toward the nearest stairs while the floor is not sealed --
 *       the boss fight's seal refuses every transition, so a sealed floor's stairs are never the
 *       plan -- and otherwise away from the enemies, preferring cells fewer of them can reach.
 *       Cornered, it fights.</li>
 * </ul>
 *
 * <p><b>The threat estimate.</b> For each enemy the Codex has figures for ({@link Codex.Threat}),
 * the Policy works out the hero's expected damage per turn against it and its expected damage per
 * turn against the hero, from the game's hit rule and the mean rolls, and takes the fight as
 * favourable when the damage the hero expects to take while killing its target is under half its
 * hit points. The inputs are what the screen shows: the hero's level, strength and hit points, the
 * names of the weapon and armour it wears and their visible upgrades, and each enemy's name and
 * health bar. See {@link #favourable} for the arithmetic and its assumptions.
 */
final class Fight implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "fight";

    /**
     * The share of the hero's hit points, in thousandths, that the damage expected while killing
     * the target must stay under for the fight to be favourable. An assumption, not a Codex fact:
     * half leaves room for the rolls the means smooth over.
     */
    static final int MARGIN_PER_MILLE = 500;

    private final Codex.Knowledge knowledge;

    Fight(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "fight: enemy";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && !enemies(observation).isEmpty();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ActorView> enemies = enemies(observation);
        if (enemies.isEmpty()) {
            return List.of();
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        List<ActorView> adjacent = new ArrayList<>();
        for (ActorView enemy : enemies) {
            if (offered.contains(new Action.Attack(enemy.cell()))) {
                adjacent.add(enemy);
            }
        }
        boolean favourable = favourable(observation, knowledge, enemies);
        RunLog.Choice attack = attack(observation, adjacent);
        RunLog.Choice retreat = retreat(observation, memory, offered, enemies);
        List<RunLog.Choice> ranked = new ArrayList<>();
        if (!adjacent.isEmpty()) {
            if (favourable || retreat == null) {
                ranked.add(retreat == null && !favourable
                        ? new RunLog.Choice(attack.action(), Policies.CERTAIN, "cornered: " + attack.why().substring("attack: ".length()))
                        : attack);
                add(ranked, retreat);
            } else {
                ranked.add(retreat);
                add(ranked, attack);
            }
            return ranked;
        }
        if (enemies.size() >= 2 && engage(map, hero) > 2) {
            RunLog.Choice choke = chokepoint(observation, offered, enemies);
            if (choke != null) {
                ranked.add(choke);
                add(ranked, favourable ? approach(observation, memory, offered, enemies) : retreat);
                return ranked;
            }
        }
        if (enemies.size() >= 2 && engage(map, hero) <= 2 && favourable && offered.contains(new Action.Wait())) {
            ranked.add(new RunLog.Choice(new Action.Wait(), Policies.CERTAIN, "hold: chokepoint"));
            add(ranked, retreat);
            return ranked;
        }
        RunLog.Choice approach = approach(observation, memory, offered, enemies);
        if (favourable && approach != null) {
            ranked.add(approach);
            add(ranked, retreat);
        } else if (retreat != null) {
            ranked.add(retreat);
            add(ranked, approach);
        } else if (approach != null) {
            ranked.add(approach);
        }
        return ranked;
    }

    private static void add(List<RunLog.Choice> ranked, RunLog.Choice choice) {
        if (choice != null && ranked.stream().noneMatch(other -> other.action().equals(choice.action()))) {
            ranked.add(new RunLog.Choice(choice.action(), 0, choice.why()));
        }
    }

    /** The enemies the screen shows. */
    static List<ActorView> enemies(Observation observation) {
        List<ActorView> enemies = new ArrayList<>();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                enemies.add(actor);
            }
        }
        return enemies;
    }

    /** The adjacent enemy the hero expects to kill soonest; ties to the lower cell. */
    private RunLog.Choice attack(Observation observation, List<ActorView> adjacent) {
        ActorView best = null;
        double soonest = Double.POSITIVE_INFINITY;
        for (ActorView enemy : adjacent) {
            double turns = turnsToKill(observation, knowledge, enemy);
            if (best == null || turns < soonest || (turns == soonest && enemy.cell() < best.cell())) {
                best = enemy;
                soonest = turns;
            }
        }
        return best == null ? null
                : new RunLog.Choice(new Action.Attack(best.cell()), Policies.CERTAIN, "attack: " + best.name());
    }

    /**
     * How many enemies could engage {@code cell}: its eight neighbours an enemy can stand on (a tile
     * a character walks on, or a cell the screen has never shown, which the Policy cannot rule out).
     */
    static int engage(MapSection map, int cell) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        int open = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= map.height()) {
                    continue;
                }
                int next = nx + ny * width;
                if (map.fog().get(next) == Fog.UNKNOWN || Explore.WALK.contains(map.tiles().get(next))) {
                    open++;
                }
            }
        }
        return open;
    }

    /** The offered Step to the neighbour fewest enemies can engage, when it is fewer than here; ties go farther from the enemies. */
    private static RunLog.Choice chokepoint(Observation observation, List<Action> offered, List<ActorView> enemies) {
        MapSection map = observation.map();
        int here = engage(map, observation.hero().cell());
        Action.Step best = null;
        int fewest = here;
        int farthest = -1;
        for (Action action : offered) {
            if (action instanceof Action.Step step && !transition(map, step.cell())) {
                int engage = engage(map, step.cell());
                int distance = nearest(map, step.cell(), enemies);
                if (engage < fewest || (best != null && engage == fewest && distance > farthest)) {
                    best = step;
                    fewest = engage;
                    farthest = distance;
                }
            }
        }
        return best == null ? null : new RunLog.Choice(best, Policies.CERTAIN, "chokepoint " + fewest);
    }

    /** The offered Step that starts the shortest walk to a cell beside the nearest enemy. */
    private static RunLog.Choice approach(Observation observation, Memory memory, List<Action> offered, List<ActorView> enemies) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory);
        Path path = walk(map, walk, offered, cell -> nearest(map, cell, enemies) == 1);
        return path == null ? null : new RunLog.Choice(path.step, Policies.CERTAIN, "approach " + path.distance);
    }

    /**
     * Away from the enemies: toward the nearest stairs when the floor is not sealed, taking them
     * when the hero stands on them, else the offered Step that most increases the distance to the
     * nearest enemy, fewer engaging first. Null when nothing gets the hero farther away.
     */
    private static RunLog.Choice retreat(Observation observation, Memory memory, List<Action> offered, List<ActorView> enemies) {
        MapSection map = observation.map();
        if (!observation.header().sealed()) {
            for (Action leave : List.of(new Action.Descend(), new Action.Ascend())) {
                if (offered.contains(leave)) {
                    return new RunLog.Choice(leave, Policies.CERTAIN, "retreat: stairs");
                }
            }
            boolean[] walk = Explore.walkable(observation, memory);
            for (TransitionView transition : map.transitions()) {
                // A Step onto the stairs is the way off the floor; the walk may end on them.
                if (map.fog().get(transition.cell()) != Fog.UNKNOWN) {
                    walk[transition.cell()] = true;
                }
            }
            Path path = walk(map, walk, offered, cell -> transition(map, cell));
            if (path != null && nearest(map, ((Action.Step) path.step).cell(), enemies) > 0) {
                return new RunLog.Choice(path.step, Policies.CERTAIN, "retreat: stairs");
            }
        }
        int hero = observation.hero().cell();
        int from = nearest(map, hero, enemies);
        Action.Step best = null;
        int farthest = from;
        int fewest = Integer.MAX_VALUE;
        for (Action action : offered) {
            if (action instanceof Action.Step step && !transition(map, step.cell())) {
                int distance = nearest(map, step.cell(), enemies);
                int engage = engage(map, step.cell());
                if (distance > farthest || (best != null && distance == farthest && engage < fewest)) {
                    best = step;
                    farthest = distance;
                    fewest = engage;
                }
            }
        }
        return best == null ? null : new RunLog.Choice(best, Policies.CERTAIN, "retreat " + farthest);
    }

    private static boolean transition(MapSection map, int cell) {
        return map.transitions().stream().anyMatch(transition -> transition.cell() == cell);
    }

    /** The Chebyshev distance from {@code cell} to the nearest enemy. */
    static int nearest(MapSection map, int cell, List<ActorView> enemies) {
        int width = map.width();
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            best = Math.min(best, Math.max(Math.abs(cell % width - enemy.cell() % width),
                    Math.abs(cell / width - enemy.cell() / width)));
        }
        return best;
    }

    private record Path(Action step, int distance) {
    }

    private interface Goal {
        boolean at(int cell);
    }

    /** Breadth-first from the offered Steps over walkable cells to the nearest goal. */
    private static Path walk(MapSection map, boolean[] walk, List<Action> offered, Goal goal) {
        int cells = walk.length;
        int[] first = new int[cells];
        int[] distance = new int[cells];
        java.util.Arrays.fill(distance, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < cells && walk[step.cell()]
                    && distance[step.cell()] < 0) {
                distance[step.cell()] = 1;
                first[step.cell()] = step.cell();
                queue.add(step.cell());
            }
        }
        int width = map.width();
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            if (goal.at(cell)) {
                return new Path(new Action.Step(first[cell]), distance[cell]);
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cell % width + dx;
                    int ny = cell / width + dy;
                    if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= map.height()) {
                        continue;
                    }
                    int next = nx + ny * width;
                    if (walk[next] && distance[next] < 0) {
                        distance[next] = distance[cell] + 1;
                        first[next] = first[cell];
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------ the threat estimate

    /**
     * The chance an attack lands: the attacker rolls uniformly below its accuracy and the defender
     * below its evasion, and the attack lands when the first is at least the second
     * (Char.java:646-678). For accuracy {@code a} and evasion {@code d}: {@code 1 - d/2a} when
     * {@code a >= d}, else {@code a/2d}.
     */
    static double hitChance(int accuracy, int evasion) {
        if (evasion <= 0) {
            return 1;
        }
        if (accuracy <= 0) {
            return 0;
        }
        return accuracy >= evasion ? 1 - evasion / (2.0 * accuracy) : accuracy / (2.0 * evasion);
    }

    /** The hero's accuracy: 10, and one more per level after the first (Hero.java:220, :2084). */
    static int heroAccuracy(HeroSection hero) {
        return 9 + Math.max(1, hero.level());
    }

    /** The hero's evasion: 5, and one more per level after the first (Hero.java:221, :2085). */
    static int heroEvasion(HeroSection hero) {
        return 4 + Math.max(1, hero.level());
    }

    /**
     * The hero's mean damage roll: the worn weapon's, as the Codex measured it at its visible
     * upgrade, or bare hands, {@code NormalIntRange(1, max(STR-8, 1))} (RingOfForce.java:105).
     */
    static double heroDamage(Observation observation, Codex.Knowledge knowledge) {
        ItemView weapon = worn(observation, EquipSlot.WEAPON);
        if (weapon != null) {
            Double mean = mean(knowledge.weapons(), weapon);
            if (mean != null) {
                return mean;
            }
        }
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        return (1 + Math.max(strength - 8, 1)) / 2.0;
    }

    /** The hero's mean damage reduction: the worn armour's, as the Codex measured it, or none. */
    static double heroArmour(Observation observation, Codex.Knowledge knowledge) {
        ItemView armour = worn(observation, EquipSlot.ARMOR);
        if (armour != null) {
            Double mean = mean(knowledge.armours(), armour);
            if (mean != null) {
                return mean;
            }
        }
        return 0;
    }

    private static ItemView worn(Observation observation, EquipSlot slot) {
        for (ItemView item : observation.inventory().items()) {
            if (item.slot() == slot) {
                return item;
            }
        }
        return null;
    }

    /** The measured mean for {@code item} at the highest measured level not above its visible one. */
    private static Double mean(List<Codex.Gear> gear, ItemView item) {
        int level = item.levelKnown() ? Math.max(0, item.visiblyUpgraded()) : 0;
        Codex.Gear best = null;
        for (Codex.Gear entry : gear) {
            if (entry.name().equals(item.name()) && entry.level() <= level
                    && (best == null || entry.level() > best.level())) {
                best = entry;
            }
        }
        return best == null ? null : best.meanPerMille() / 1000.0;
    }

    /** The enemy's hit points as its health bar shows them, rounded up. */
    static double health(Codex.Threat threat, ActorView enemy) {
        return Math.ceil((double) threat.ht() * enemy.healthPips() / ObservationCodec.MAX_HEALTH_PIPS);
    }

    /** Turns the hero expects to need to kill {@code enemy}; infinite when it cannot hurt it, zero when the Codex has no figures. */
    static double turnsToKill(Observation observation, Codex.Knowledge knowledge, ActorView enemy) {
        Codex.Threat threat = knowledge.threat(enemy.name());
        if (threat == null) {
            return 0;
        }
        // Damage lands less the defender's roll of damage reduction (Char.java:390, :486).
        double perHit = Math.max(0, heroDamage(observation, knowledge) - (threat.drMin() + threat.drMax()) / 2.0);
        double perTurn = hitChance(heroAccuracy(observation.hero()), threat.defense()) * perHit;
        return perTurn <= 0 ? Double.POSITIVE_INFINITY : health(threat, enemy) / perTurn;
    }

    /** What {@code threat} is expected to take from the hero per turn. */
    static double enemyDamage(Observation observation, Codex.Knowledge knowledge, Codex.Threat threat) {
        double perHit = Math.max(0, (threat.damageMin() + threat.damageMax()) / 2.0 - heroArmour(observation, knowledge));
        return hitChance(threat.attack(), heroEvasion(observation.hero())) * perHit;
    }

    /**
     * Whether fighting is favourable: the damage the hero expects to take while killing the enemy
     * it would kill soonest, from every enemy in view that could engage it at once, is under
     * {@link #MARGIN_PER_MILLE} of its hit points.
     *
     * <p>Assumptions, each a simplification rather than a Codex fact: every attack takes one turn
     * on both sides; the hero's accuracy and evasion are its level's, without the weapon's accuracy
     * factor, the armour's evasion factor, rings or talents; means stand in for rolls; an enemy the
     * Codex has no figures for is taken as harmless and quick to kill, so the Policy fights it rather
     * than flee from everything it does not know.
     */
    static boolean favourable(Observation observation, Codex.Knowledge knowledge, List<ActorView> enemies) {
        double soonest = Double.POSITIVE_INFINITY;
        List<Double> incoming = new ArrayList<>();
        for (ActorView enemy : enemies) {
            soonest = Math.min(soonest, turnsToKill(observation, knowledge, enemy));
            Codex.Threat threat = knowledge.threat(enemy.name());
            incoming.add(threat == null ? 0 : enemyDamage(observation, knowledge, threat));
        }
        if (soonest == Double.POSITIVE_INFINITY) {
            return false;
        }
        incoming.sort((a, b) -> Double.compare(b, a));
        int engaging = Math.min(incoming.size(), Math.max(1, engage(observation.map(), observation.hero().cell())));
        double perTurn = 0;
        for (int i = 0; i < engaging; i++) {
            perTurn += incoming.get(i);
        }
        return soonest * perTurn * 1000 < (double) observation.hero().hp() * MARGIN_PER_MILLE;
    }
}
