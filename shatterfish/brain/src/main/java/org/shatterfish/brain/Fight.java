package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Fight where only one enemy can reach the hero, or get away (story 4.7, FR-31).
 *
 * <p>The Policy enters while an enemy is in view and no Prompt is open, which is exactly when
 * {@link Explore} stands aside, and once it enters it always returns an Action the screen offers,
 * one per Input wait:
 *
 * <ul>
 *   <li><b>Attack</b> an adjacent enemy when the fight is favourable: the one it expects to kill
 *       soonest. The Policy attacks only an enemy the actors section carries, on its cell, so it
 *       never targets a character the Observation does not show; remembered sightings are not
 *       targets.</li>
 *   <li>With two or more mobile enemies in view, none adjacent, and the enemies closing in: <b>take
 *       a chokepoint</b> -- a neighbouring cell at most two enemies can engage, when the hero's own
 *       cell lets more in -- and <b>hold</b> it, at most {@link #HOLDS} waits in a row. Enemies that
 *       are not coming (asleep, rooted, shooting from afar) are approached instead of waited for, so
 *       neither the hold nor the chokepoint can loop against them. A character attacks
 *       any of its eight neighbours ({@code Mob.canAttack}, {@code Level.adjacent}), so how many can
 *       engage a cell is how many of its neighbours an enemy can stand on.</li>
 *   <li><b>Approach</b> an enemy when the fight is favourable; and when the enemy it approached drops out
 *       of view, <b>chase</b>: walk on to beside the cell the screen last showed it on, for at most
 *       {@link #CHASE_WAITS} waits (issue #174). The Policy enters for that on a screen with no enemy in
 *       view, which is the one case it does.</li>
 *   <li><b>Retreat</b> when it is not: by the regular stairs while the floor is not sealed -- the
 *       boss fight's seal refuses every transition -- and not along a path beside an enemy, and
 *       otherwise away from the enemies. The surface and branch stairs are not a retreat: the
 *       surface opens a window without leaving (SewerLevel.java:146-156) and a branch can refuse.
 *       A floor already fled twice is not fled by the stairs again. Cornered, it fights.</li>
 *   <li><b>Speed decides whether it can run</b> (the bestiary's lever 1): a mob's step costs 1/speed
 *       and its attack a whole turn (Mob.java:1353-1355, :753-757; Char.java:770-783), so from an
 *       awake enemy the bestiary tags faster and not outrunnable (the sewer crab, the vampire bat) the
 *       hero never retreats on foot, unless the stairs are the next cell; it <b>stands off</b> instead,
 *       one step back when the enemy is two cells away, so the enemy's whole turn goes into closing,
 *       and <b>awaits</b> it farther off. From an awake enemy as fast as the hero and not outrunnable it
 *       retreats only by the stairs, along a path the enemy cannot cut across, and otherwise fights.</li>
 * </ul>
 *
 * <p>Enemies the game keeps passive until provoked ({@link #passive}, from the bestiary's tags) are
 * neither fought nor fled: the Policy treats them as scenery, and so does {@link Explore}.
 *
 * <p><b>The threat estimate.</b> For each enemy, the Codex's figures ({@link Codex.Threat}), or, for
 * an enemy the Codex has none for, a pessimistic figure scaled by depth ({@link #assumed}). The
 * Policy works out the hero's expected damage per turn against the enemy and the enemy's against the
 * hero from the game's hit rule and the exact expectation of the damage roll less the
 * damage-reduction roll, and takes the fight as favourable when the damage the hero expects to take
 * while killing its target is under half its hit points, and never while its health is low. The
 * inputs are what the screen shows. See {@link #favourable} for the assumptions.
 */
final class Fight implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "fight";

    /**
     * The share of the hero's hit points, in thousandths, that the damage expected while killing
     * the target must stay under for the fight to be favourable. An assumption, not a Codex fact.
     */
    static final int MARGIN_PER_MILLE = 500;

    /** The most waits in a row the Policy holds a chokepoint before it goes to meet the enemy. */
    static final int HOLDS = 4;

    /** The most times the hero leaves one floor by the stairs to get away. */
    static final int FLIGHTS = 2;

    /**
     * Whether {@code actor} is an enemy the game keeps passive until provoked ({@link Bestiary#passive}:
     * the animated and armored statues and the gnoll exile) and still looks untouched: its health bar
     * full, no buff drawn on it and no alert over it. A statue a trap or a gas has hurt is hunting
     * (Statue.java:128-136), and a debuffed exile turns (GnollExile.java:137-146); what the screen shows
     * of either is the bar, the buff icons and the alert, so any of them ends the truce.
     */
    static boolean passive(Codex.Knowledge knowledge, int depth, ActorView actor) {
        return Bestiary.passive(knowledge, depth, actor.name()) && actor.healthPips() == ObservationCodec.MAX_HEALTH_PIPS
                && actor.buffs().isEmpty() && actor.emote() != org.shatterfish.api.Emote.ALERT;
    }

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

    /**
     * The most waits after an approach the Policy walks on toward where the enemy stood once it is out of
     * view (issue #174). An enemy seen from one cell and not from the next -- round a corner, across a
     * doorway -- made the Policy approach from the first and the Policy below it walk back from the
     * second, for hundreds of turns. An assumption: a sleeping enemy stays where it lies (row 70), and an
     * awake one that walked off is not followed further than a room's width.
     */
    static final int CHASE_WAITS = 10;

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE
                && (!enemies(observation, knowledge).isEmpty() || chasing(observation, memory, knowledge));
    }

    /**
     * Whether the Policy walks on toward an enemy it approached that is out of view (issue #174): no
     * enemy in view, and the Memory's chase on this floor, handed over at most {@link #CHASE_WAITS} waits
     * ago, and not yet reached. The chase is where the screen showed the enemy, never where it is.
     */
    static boolean chasing(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        Memory.Spot chase = memory.chase();
        return chase.cell() >= 0 && enemies(observation, knowledge).isEmpty()
                && chase.on(observation.header().depth(), observation.header().branch())
                && memory.waits() - memory.chased() <= CHASE_WAITS
                && chase.cell() < observation.map().tiles().size()
                && chebyshev(observation.map(), observation.hero().cell(), chase.cell()) > 1;
    }

    /**
     * How many waits a sighting of an enemy now out of view still counts toward whether a fight is
     * favourable (issue #174). An assumption: a sleeping enemy stays where it lies (row 70), and an
     * awake one out of view is not assumed gone for this long.
     */
    static final int RECALL_WAITS = 20;

    /**
     * The enemies a fight is weighed against (issue #174): those in view, and those seen on this floor in
     * the last {@link #RECALL_WAITS} waits and out of view now, on a cell the screen does not show, at full
     * health. Weighed against the enemies in view alone, a fight turned favourable as the hero stepped
     * where one of two enemies was hidden, and unfavourable as it stepped back where both showed, and
     * the Policy approached and retreated between the two cells for hundreds of turns. A cell the screen
     * shows empty says the enemy is not there, which is how a killed one stops counting.
     */
    static List<ActorView> threats(Observation observation, Memory memory, List<ActorView> enemies,
                                   Codex.Knowledge knowledge) {
        List<ActorView> threats = new ArrayList<>(enemies);
        MapSection map = observation.map();
        for (Memory.Seen seen : memory.monsters()) {
            if (seen.depth() == observation.header().depth() && seen.at() < memory.waits()
                    && memory.waits() - seen.at() <= RECALL_WAITS && !Bestiary.passive(knowledge, seen.depth(), seen.name())
                    && seen.cell() < map.tiles().size() && map.fog().get(seen.cell()) != Fog.VISIBLE) {
                threats.add(new ActorView(seen.cell(), seen.name(), Alignment.ENEMY, ObservationCodec.MAX_HEALTH_PIPS,
                        false, org.shatterfish.api.Emote.NONE, List.of()));
            }
        }
        return threats;
    }

    /** The Chebyshev distance between two cells of {@code map}. */
    static int chebyshev(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }

    /** The offered Step that starts the shortest walk to beside the cell of the Memory's chase, or null. */
    private static RunLog.Choice chase(Observation observation, Memory memory, List<Action> offered) {
        MapSection map = observation.map();
        int target = memory.chase().cell();
        Path path = walk(map, Explore.walkable(observation, memory, false), observation.hero().cell(), offered,
                cell -> chebyshev(map, cell, target) <= 1);
        return path == null ? null : new RunLog.Choice(path.step, Policies.CERTAIN, "chase " + path.distance);
    }

    /**
     * The cell of the enemy an approach handed over on {@code observation} goes for: the nearest in view,
     * ties to the lower cell; -1 with none (issue #174).
     */
    static int quarry(Observation observation, Codex.Knowledge knowledge) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        int best = -1;
        int nearest = Integer.MAX_VALUE;
        for (ActorView enemy : enemies(observation, knowledge)) {
            int distance = chebyshev(map, hero, enemy.cell());
            if (distance < nearest || (distance == nearest && enemy.cell() < best)) {
                nearest = distance;
                best = enemy.cell();
            }
        }
        return best;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<ActorView> enemies = enemies(observation, knowledge);
        if (enemies.isEmpty()) {
            RunLog.Choice chase = chasing(observation, memory, knowledge) ? chase(observation, memory, offered) : null;
            return chase == null ? List.of() : List.of(chase);
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        List<ActorView> adjacent = new ArrayList<>();
        for (ActorView enemy : enemies) {
            if (offered.contains(new Action.Attack(enemy.cell()))) {
                adjacent.add(enemy);
            }
        }
        boolean favourable = favourable(observation, knowledge, threats(observation, memory, enemies, knowledge));
        RunLog.Choice retreat = retreat(observation, memory, offered, enemies, knowledge);
        List<RunLog.Choice> ranked = new ArrayList<>();
        // Goo's pump-up, announced in the log: out of its reach first, which makes it step and drop the
        // pump (story 4.13, Goo).
        RunLog.Choice dodge = Goo.dodge(observation, memory, offered);
        if (dodge != null) {
            ranked.add(dodge);
        }
        RunLog.Choice standOff = adjacent.isEmpty() ? standOff(observation, memory, offered, enemies, knowledge) : null;
        if (standOff != null) {
            ranked.add(standOff);
        }
        if (!adjacent.isEmpty()) {
            RunLog.Choice attack = attack(observation, adjacent);
            if (favourable || retreat == null) {
                ranked.add(retreat == null && !favourable
                        ? new RunLog.Choice(attack.action(), Policies.CERTAIN,
                                "cornered: " + attack.why().substring("attack: ".length()))
                        : attack);
                add(ranked, retreat);
            } else {
                ranked.add(retreat);
                add(ranked, attack);
            }
            return ranked;
        }
        if (!favourable) {
            add(ranked, retreat);
            idle(ranked, offered, memory);
            return ranked;
        }
        int depth = observation.header().depth();
        List<ActorView> mobile = enemies.stream().filter(enemy -> !Bestiary.immobile(knowledge, depth, enemy.name())).toList();
        int here = engage(map, hero);
        boolean held = memory.holds() >= HOLDS;
        // Closing: the enemies stand nearer the hero's cell than they stood a wait ago (Memory.before
        // is measured from where the hero stands now, so the hero's own steps do not count).
        boolean closing = memory.before() < 0 || (memory.near() >= 0 && memory.near() < memory.before());
        if (mobile.size() >= 2 && !held && closing) {
            if (here > 2) {
                add(ranked, chokepoint(observation, offered, enemies, here));
            }
            if (ranked.isEmpty() && here <= 2 && offered.contains(new Action.Wait())) {
                ranked.add(new RunLog.Choice(new Action.Wait(), Policies.CERTAIN, "hold: chokepoint"));
            }
        }
        add(ranked, approach(observation, memory, offered, enemies));
        add(ranked, retreat);
        idle(ranked, offered, memory);
        return ranked;
    }

    /** Add {@code choice} at the next rank (its score 0 below the first), once. */
    private static void add(List<RunLog.Choice> ranked, RunLog.Choice choice) {
        if (choice != null && ranked.stream().noneMatch(other -> other.action().equals(choice.action()))) {
            ranked.add(ranked.isEmpty() ? choice : new RunLog.Choice(choice.action(), 0, choice.why()));
        }
    }

    /**
     * End the ranking with a turn spent in place, for at most {@link #HOLDS} waits in a row: an enemy
     * that neither comes nor can be reached or fled (asleep across a chasm, from a dead end) would
     * otherwise hold the hero forever. Past that the Policy offers nothing more, and the Policies
     * below it take the wait.
     */
    private static void idle(List<RunLog.Choice> ranked, List<Action> offered, Memory memory) {
        if (memory.holds() >= HOLDS) {
            return;
        }
        for (Action action : List.of(new Action.Wait(), new Action.Search())) {
            if (offered.contains(action)) {
                add(ranked, new RunLog.Choice(action, Policies.CERTAIN, "hold: no-way"));
                return;
            }
        }
    }

    /** The enemies the screen shows, less those the game keeps passive until provoked. */
    static List<ActorView> enemies(Observation observation, Codex.Knowledge knowledge) {
        List<ActorView> enemies = new ArrayList<>();
        int depth = observation.header().depth();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY && !passive(knowledge, depth, actor)) {
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
        return new RunLog.Choice(new Action.Attack(best.cell()), Policies.CERTAIN, "attack: " + best.name());
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

    /**
     * The offered Step to the neighbour fewest enemies can engage, when at most two can and fewer than
     * {@code here}; ties go farther from the enemies. Null when there is none.
     */
    private static RunLog.Choice chokepoint(Observation observation, List<Action> offered, List<ActorView> enemies,
                                            int here) {
        MapSection map = observation.map();
        Action.Step best = null;
        int fewest = Math.min(here, 3);
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
    private static RunLog.Choice approach(Observation observation, Memory memory, List<Action> offered,
                                          List<ActorView> enemies) {
        MapSection map = observation.map();
        boolean[] walk = Explore.walkable(observation, memory, false);
        Path path = walk(map, walk, observation.hero().cell(), offered, cell -> nearest(map, cell, enemies) == 1);
        return path == null ? null : new RunLog.Choice(path.step, Policies.CERTAIN, "approach " + path.distance);
    }

    /**
     * Away from the enemies: by the regular stairs while the floor is not sealed and has not been fled
     * {@link #FLIGHTS} times, taking them when the hero stands on them and walking there along cells
     * no enemy stands beside, never by a first Step closer to an enemy; else the offered Step that
     * most increases the distance to the nearest enemy, fewer engaging first. Null when nothing gets
     * the hero farther away.
     *
     * <p>Speed (the bestiary's lever 1, docs/bestiary/index.md, the speed rule): with an awake enemy
     * in view that the bestiary tags faster than the hero and not outrunnable, only stairs underfoot
     * or one Step away; with one as fast as the hero and not outrunnable, only the stairs, along a
     * path whose j-th cell is more than j cells from each such enemy, so it cannot cut across; never
     * the Step away, which gains nothing on either. Null otherwise, and the hero fights.
     *
     * <p>The stairs down count only when they do not lead onto a boss floor, where the boss seals the
     * floor behind the hero (Goo.java:134-136, Level.java:657-661), and not while the hero is hurt and
     * the descend Policy is taking it down (story 4.12): it would arrive on the harder floor hurt, the
     * very thing the descend Policy rests beside the exit to avoid. Elsewhere a hurt hero may still flee
     * down: the stairs down are often the only way out of a fight it is losing, and refusing them
     * whenever the hero is hurt measured worse on `smoke` (median turns survived 1,044 to 828, mean
     * deepest floor 2.52 to 2.12, story 4.12's review).
     */
    static RunLog.Choice retreat(Observation observation, Memory memory, List<Action> offered, List<ActorView> enemies,
                                 Codex.Knowledge knowledge) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        int from = nearest(map, hero, enemies);
        String floor = observation.header().depth() + ":" + observation.header().branch();
        // Lever 1: the enemies the hero cannot walk away from, awake (a sleeper chases nothing).
        List<ActorView> faster = faster(observation, enemies, knowledge);
        List<ActorView> abreast = abreast(observation, enemies, knowledge);
        if (!observation.header().sealed() && Memory.count(memory.flights(), floor, 0) < FLIGHTS) {
            boolean down = down(observation, memory, knowledge);
            java.util.function.Predicate<TransitionView> stairs = transition -> transition.kind()
                    == TransitionKind.REGULAR_ENTRANCE || (down && transition.kind() == TransitionKind.REGULAR_EXIT);
            for (TransitionView transition : map.transitions()) {
                if (transition.cell() != hero) {
                    continue;
                }
                Action leave = !stairs.test(transition) ? null
                        : transition.kind() == TransitionKind.REGULAR_EXIT ? new Action.Descend() : new Action.Ascend();
                if (leave != null && offered.contains(leave)) {
                    return new RunLog.Choice(leave, Policies.CERTAIN, "retreat: stairs");
                }
            }
            if (!faster.isEmpty()) {
                // A faster enemy closes two cells per hero step: only stairs one Step away are reached
                // before it has struck more than once (docs/bestiary/index.md, speed rule).
                for (Action action : offered) {
                    if (action instanceof Action.Step step && map.transitions().stream()
                            .anyMatch(t -> t.cell() == step.cell() && stairs.test(t))) {
                        return new RunLog.Choice(step, Policies.CERTAIN, "retreat: stairs");
                    }
                }
                return null;
            }
            boolean[] walk = Explore.walkable(observation, memory, false);
            for (TransitionView transition : map.transitions()) {
                if (stairs.test(transition) && map.fog().get(transition.cell()) != Fog.UNKNOWN) {
                    walk[transition.cell()] = true;
                }
            }
            for (int cell = 0; cell < walk.length; cell++) {
                if (walk[cell] && nearest(map, cell, enemies) <= 1) {
                    walk[cell] = false;
                }
            }
            List<Action> away = offered.stream().filter(action -> !(action instanceof Action.Step step)
                    || nearest(map, step.cell(), enemies) >= from).toList();
            // An enemy as fast as the hero is outwalked only where it cannot cut across: after the
            // hero's j-th step it has made j - 1 moves, so it can strike the j-th cell only when that
            // cell is at most j cells from where it stands now.
            Path path = walk(map, walk, hero, away, cell -> map.transitions().stream()
                    .anyMatch(t -> t.cell() == cell && stairs.test(t)),
                    (cell, steps) -> abreast.isEmpty() || nearest(map, cell, abreast) > steps);
            if (path != null) {
                return new RunLog.Choice(path.step, Policies.CERTAIN, "retreat: stairs");
            }
        }
        if (!faster.isEmpty() || !abreast.isEmpty()) {
            // On foot the hero gains nothing on an enemy at least as fast, and a faster one strikes as it follows.
            return null;
        }
        // Away, onto a cell the hero may walk on: not a chasm, which jumps, a well, which drinks, an
        // armed trap, or a cell a Step was refused onto.
        boolean[] open = Explore.walkable(observation, memory, false);
        Action.Step best = null;
        int farthest = from;
        int fewest = Integer.MAX_VALUE;
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < open.length && open[step.cell()]
                    && !transition(map, step.cell())) {
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

    /** The awake enemies of {@code enemies} the bestiary tags faster than the hero and not outrunnable. */
    static List<ActorView> faster(Observation observation, List<ActorView> enemies, Codex.Knowledge knowledge) {
        int depth = observation.header().depth();
        return enemies.stream().filter(enemy -> enemy.emote() != Emote.SLEEP
                && Bestiary.faster(knowledge, depth, enemy.name())).toList();
    }

    /** The awake enemies of {@code enemies} the bestiary tags as fast as the hero and not outrunnable. */
    static List<ActorView> abreast(Observation observation, List<ActorView> enemies, Codex.Knowledge knowledge) {
        int depth = observation.header().depth();
        return enemies.stream().filter(enemy -> enemy.emote() != Emote.SLEEP
                && Bestiary.abreast(knowledge, depth, enemy.name())).toList();
    }

    /** The distance, in cells, a faster enemy is kept at so that closing it spends its whole turn. */
    static final int STAND_OFF = 3;

    /**
     * Against an awake faster enemy coming at the hero, none adjacent (the bestiary's lever 1): a
     * speed-2 mob's step costs half a turn and its attack a whole one (Mob.java:1353-1355,
     * :753-757; Char.java:770-783), so one that starts its turn two cells off steps once and strikes,
     * and one that starts it {@link #STAND_OFF} or more cells off spends it all closing and the hero
     * strikes first (docs/bestiary/sewers.md#crab; docs/bestiary/index.md, the speed rule). Two
     * cells off: the offered Step to a cell at least {@link #STAND_OFF} from every faster enemy and
     * beside no enemy, fewest engaging first, then farther. Farther off: a turn in place, the enemy's
     * to spend. Null when no faster enemy is closing, or no such Step or wait is offered; the wait
     * counts among the {@link #HOLDS}, so one that never arrives does not hold the hero.
     */
    static RunLog.Choice standOff(Observation observation, Memory memory, List<Action> offered, List<ActorView> enemies,
                                  Codex.Knowledge knowledge) {
        List<ActorView> faster = faster(observation, enemies, knowledge);
        boolean closing = memory.before() < 0 || (memory.near() >= 0 && memory.near() < memory.before());
        if (faster.isEmpty() || !closing) {
            return null;
        }
        MapSection map = observation.map();
        int distance = nearest(map, observation.hero().cell(), faster);
        if (distance >= STAND_OFF) {
            return memory.holds() < HOLDS && offered.contains(new Action.Wait())
                    ? new RunLog.Choice(new Action.Wait(), Policies.CERTAIN, "hold: stand off") : null;
        }
        boolean[] open = Explore.walkable(observation, memory, false);
        Action.Step best = null;
        int fewest = Integer.MAX_VALUE;
        int farthest = -1;
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < open.length && open[step.cell()]
                    && !transition(map, step.cell()) && nearest(map, step.cell(), faster) >= STAND_OFF
                    && nearest(map, step.cell(), enemies) > 1) {
                int engage = engage(map, step.cell());
                int away = nearest(map, step.cell(), enemies);
                if (engage < fewest || (engage == fewest && away > farthest)) {
                    best = step;
                    fewest = engage;
                    farthest = away;
                }
            }
        }
        return best == null ? null : new RunLog.Choice(best, Policies.CERTAIN, "stand off " + STAND_OFF);
    }

    /**
     * Whether a retreat may take the stairs down: not onto a boss floor, and not while the hero is hurt
     * and the descend Policy is taking it down (see {@link #retreat}).
     */
    static boolean down(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        if (Descend.bossNext(observation, knowledge)) {
            return false;
        }
        return observation.hero().hp() >= observation.hero().ht() || Descend.leaving(observation, memory, knowledge) == null;
    }

    private static boolean transition(MapSection map, int cell) {
        return map.transitions().stream().anyMatch(transition -> transition.cell() == cell);
    }

    /** The Chebyshev distance from {@code cell} to the nearest enemy, or -1 with none. */
    static int nearest(MapSection map, int cell, List<ActorView> enemies) {
        int width = map.width();
        int best = Integer.MAX_VALUE;
        for (ActorView enemy : enemies) {
            best = Math.min(best, Math.max(Math.abs(cell % width - enemy.cell() % width),
                    Math.abs(cell / width - enemy.cell() / width)));
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private record Path(Action step, int distance) {
    }

    private interface Goal {
        boolean at(int cell);
    }

    /** Whether the walk may stand on {@code cell} after {@code steps} Steps. */
    private interface Safe {
        boolean at(int cell, int steps);
    }

    /** Breadth-first from the offered Steps over walkable cells to the nearest goal, never through the hero's own cell. */
    private static Path walk(MapSection map, boolean[] walk, int hero, List<Action> offered, Goal goal) {
        return walk(map, walk, hero, offered, goal, (cell, steps) -> true);
    }

    /**
     * {@link #walk(MapSection, boolean[], int, List, Goal)} over the cells {@code safe} allows at the
     * number of Steps the walk reaches them in. Breadth-first reaches each cell first by its fewest
     * Steps, and a safety that only tightens with more Steps loses nothing by that.
     */
    private static Path walk(MapSection map, boolean[] walk, int hero, List<Action> offered, Goal goal, Safe safe) {
        int cells = walk.length;
        int[] first = new int[cells];
        int[] distance = new int[cells];
        java.util.Arrays.fill(distance, -1);
        distance[hero] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < cells && walk[step.cell()]
                    && distance[step.cell()] < 0 && safe.at(step.cell(), 1)) {
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
                    if (walk[next] && distance[next] < 0 && safe.at(next, distance[cell] + 1)) {
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
     * The chance of each value of {@code Random.NormalIntRange(min, max)}, which is
     * {@code min + (int)((Float() + Float()) * (max - min + 1) / 2)} (Random.java:138-140): the
     * floor of a triangular variable on [0, n) with n = max - min + 1. Index 0 is {@code min}.
     */
    static double[] normal(int min, int max) {
        int n = Math.max(0, max - min) + 1;
        double[] p = new double[n];
        for (int k = 0; k < n; k++) {
            p[k] = triangular(2.0 * (k + 1) / n) - triangular(2.0 * k / n);
        }
        return p;
    }

    /** The distribution function of the sum of two uniform variables on [0, 1). */
    private static double triangular(double s) {
        if (s <= 0) {
            return 0;
        }
        if (s >= 2) {
            return 1;
        }
        return s <= 1 ? s * s / 2 : 1 - (2 - s) * (2 - s) / 2;
    }

    /**
     * The expected damage of one landed hit: {@code E[max(0, X - Y)]} for X the damage roll,
     * {@code NormalIntRange(damageMin, damageMax)}, and Y the damage-reduction roll,
     * {@code NormalIntRange(drMin, drMax)} (Char.java:390, :486). Exact, not the difference of the
     * means, which understates it whenever the ranges overlap.
     */
    static double landed(int damageMin, int damageMax, int drMin, int drMax) {
        double[] x = normal(damageMin, damageMax);
        double[] y = normal(drMin, drMax);
        double expected = 0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                expected += x[i] * y[j] * Math.max(0, (damageMin + i) - (drMin + j));
            }
        }
        return expected;
    }

    /**
     * The measured figures for the item worn in {@code slot}, at the highest measured level not above
     * its visible upgrade: the measured name the shown name contains, longest first, so an enchantment
     * or glyph in the name ("blazing worn shortsword", Weapon.java:411-418; "cloth armor of flow",
     * Armor.java:573-580) still finds its item. A mage's staff shows the name of the wand it holds
     * ("staff of magic missile", MagesStaff.java:338-345), which the Codex measures as the mage's
     * staff. An item worn that the Codex cannot name is taken at the weakest item measured, never as
     * nothing worn. Null with nothing in the slot.
     */
    static Codex.Gear worn(Observation observation, List<Codex.Gear> gear, EquipSlot slot) {
        ItemView item = null;
        for (ItemView candidate : observation.inventory().items()) {
            if (candidate.slot() == slot) {
                item = candidate;
            }
        }
        if (item == null) {
            return null;
        }
        String name = measured(item.name(), gear);
        int level = item.levelKnown() ? Math.max(0, item.visiblyUpgraded()) : 0;
        Codex.Gear best = null;
        Codex.Gear weakest = null;
        for (Codex.Gear entry : gear) {
            if (entry.level() == 0 && (weakest == null || entry.meanPerMille() < weakest.meanPerMille())) {
                weakest = entry;
            }
            if (entry.name().equals(name) && entry.level() <= level && (best == null || entry.level() > best.level())) {
                best = entry;
            }
        }
        return best != null ? best : weakest;
    }

    /**
     * The measured name a shown item name stands for, or null: the measured name it contains as
     * whole words, longest first, so an enchantment or glyph in the name ("blazing worn shortsword",
     * Weapon.java:411-418; "cloth armor of flow", Armor.java:573-580) still finds its item, and the
     * mage's staff for any "staff of <wand>" (MagesStaff.java:338-345). Shared by the fight and equip
     * Policies (story 4.8).
     */
    static String measured(String shown, List<Codex.Gear> gear) {
        // "staff of <wand>" as whole words anywhere in the name: an enchantment or the holy weapon
        // wraps it ("blazing staff of magic missile", MagesStaff.java:341-342; Weapon.java:411-414).
        if (contains(shown, "staff of")) {
            return MAGES_STAFF;
        }
        String name = null;
        for (Codex.Gear entry : gear) {
            if (contains(shown, entry.name()) && (name == null || entry.name().length() > name.length())) {
                name = entry.name();
            }
        }
        return name;
    }

    /** The mage's staff, as the Codex names it (items.properties, MagesStaff). */
    static final String MAGES_STAFF = "mage's staff";

    /** Whether {@code shown} contains {@code name} as whole words. */
    private static boolean contains(String shown, String name) {
        int at = shown.indexOf(name);
        while (at >= 0) {
            boolean start = at == 0 || shown.charAt(at - 1) == ' ';
            int end = at + name.length();
            if (start && (end == shown.length() || shown.charAt(end) == ' ')) {
                return true;
            }
            at = shown.indexOf(name, at + 1);
        }
        return false;
    }

    /** The hero's damage-roll range: the worn weapon's measured range, or bare hands, {@code 1..max(STR-8,1)}. */
    static int[] heroDamage(Observation observation, Codex.Knowledge knowledge) {
        Codex.Gear weapon = worn(observation, knowledge.weapons(), EquipSlot.WEAPON);
        if (weapon != null) {
            return new int[] {weapon.min(), weapon.max()};
        }
        // Bare hands, without a ring of force: Hero.heroDamageIntRange(1, max(STR - 8, 1))
        // (RingOfForce.java:103-106), which is NormalIntRange unless the thirteen-leaf clover
        // alters it (Hero.java:706-712).
        int strength = observation.hero().strength() + observation.hero().strengthBonus();
        return new int[] {1, Math.max(strength - 8, 1)};
    }

    /** The hero's damage-reduction range: the worn armour's measured range, or none. */
    static int[] heroArmour(Observation observation, Codex.Knowledge knowledge) {
        Codex.Gear armour = worn(observation, knowledge.armours(), EquipSlot.ARMOR);
        return armour == null ? new int[] {0, 0} : new int[] {armour.min(), armour.max()};
    }

    /**
     * The figures an enemy the Codex has none for is taken at: pessimistic, and scaled by depth. An
     * assumption, not a Codex fact: hit points 10 + 5 per floor, accuracy 10 + 2 per floor, evasion
     * 3 + 1 per floor, damage 1 to 4 + 2 per floor, damage reduction 0 to 1 + 1 per two floors --
     * above the sewers' own figures (a rat: 8, 8, 2, 1-4, 0-1) at every depth.
     */
    static Codex.Threat assumed(String name, int depth) {
        int d = Math.max(1, depth);
        return new Codex.Threat(name, 10 + 5 * d, 10 + 2 * d, 3 + d, 1, 4 + 2 * d, 0, 1 + d / 2);
    }

    /**
     * The enemy's figures: the Codex's where it read them, and the assumed ones for the rest. An
     * evasion that is the enemy's own rule takes the higher of the Codex's figure and the assumed
     * one. With no Codex entry at all, the assumed figures throughout.
     */
    static Codex.Threat threat(Observation observation, Codex.Knowledge knowledge, ActorView enemy) {
        Codex.Threat guess = assumed(enemy.name(), observation.header().depth());
        Codex.Threat threat = knowledge.threat(enemy.name());
        if (threat == null) {
            return guess;
        }
        if (threat.unknown().isEmpty()) {
            return threat;
        }
        boolean attack = threat.unknown().contains("attack");
        boolean damage = threat.unknown().contains("damage");
        boolean dr = threat.unknown().contains("dr");
        int defense = threat.unknown().contains("defense") ? Math.max(threat.defense(), guess.defense()) : threat.defense();
        return new Codex.Threat(threat.name(), threat.ht(), attack ? guess.attack() : threat.attack(), defense,
                damage ? guess.damageMin() : threat.damageMin(), damage ? guess.damageMax() : threat.damageMax(),
                dr ? guess.drMin() : threat.drMin(), dr ? guess.drMax() : threat.drMax());
    }

    /** The enemy's hit points as its health bar shows them, rounded up. */
    static double health(Codex.Threat threat, ActorView enemy) {
        return Math.ceil((double) threat.ht() * enemy.healthPips() / ObservationCodec.MAX_HEALTH_PIPS);
    }

    /** Turns the hero expects to need to kill {@code enemy}; infinite when it cannot hurt it. */
    static double turnsToKill(Observation observation, Codex.Knowledge knowledge, ActorView enemy) {
        Codex.Threat threat = threat(observation, knowledge, enemy);
        int[] damage = heroDamage(observation, knowledge);
        double perTurn = hitChance(heroAccuracy(observation.hero()), threat.defense())
                * landed(damage[0], damage[1], threat.drMin(), threat.drMax());
        return perTurn <= 0 ? Double.POSITIVE_INFINITY : health(threat, enemy) / perTurn;
    }

    /** What {@code threat} is expected to take from the hero per turn. */
    static double enemyDamage(Observation observation, Codex.Knowledge knowledge, Codex.Threat threat) {
        int[] armour = heroArmour(observation, knowledge);
        return hitChance(threat.attack(), heroEvasion(observation.hero()))
                * landed(threat.damageMin(), threat.damageMax(), armour[0], armour[1]);
    }

    /**
     * Whether fighting is favourable: not while the hero's health is low (the status pane's warning,
     * {@link Safety}), and otherwise when the damage the hero expects to take while killing the enemy
     * it would kill soonest, from the enemies that could engage it at once, is under
     * {@link #MARGIN_PER_MILLE} of its hit points.
     *
     * <p>Assumptions, each a simplification rather than a Codex fact: every attack takes one turn on
     * both sides; the hero's accuracy and evasion are its level's, without the weapon's accuracy
     * factor, the armour's evasion factor, rings or talents; the weapon's measured range stands for
     * the hero's roll; special attacks, ranged attacks and speed are not modelled.
     */
    static boolean favourable(Observation observation, Codex.Knowledge knowledge, List<ActorView> enemies) {
        HeroSection hero = observation.hero();
        if (1000L * hero.hp() < 334L * hero.ht()) {
            return false;
        }
        MapSection map = observation.map();
        double soonest = Double.POSITIVE_INFINITY;
        List<Double> incoming = new ArrayList<>();
        for (ActorView enemy : enemies) {
            soonest = Math.min(soonest, turnsToKill(observation, knowledge, enemy));
            incoming.add(enemyDamage(observation, knowledge, threat(observation, knowledge, enemy)));
        }
        if (soonest == Double.POSITIVE_INFINITY) {
            return false;
        }
        incoming.sort((a, b) -> Double.compare(b, a));
        int engaging = Math.min(incoming.size(), Math.max(1, engage(map, hero.cell())));
        double perTurn = 0;
        for (int i = 0; i < engaging; i++) {
            perTurn += incoming.get(i);
        }
        return soonest * perTurn * 1000 < (double) hero.hp() * MARGIN_PER_MILLE;
    }
}
