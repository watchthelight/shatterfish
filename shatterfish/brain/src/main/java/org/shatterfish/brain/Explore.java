package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionView;
import org.shatterfish.api.TrapView;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Uncover the floor (story 4.6, FR-31): one step at a time toward the nearest unexplored frontier;
 * when none is left, a bounded round of searches for secret doors. When those are spent the floor is
 * {@linkplain #spent spent}, and the way down is the {@link Descend} Policy's (story 4.12).
 *
 * <p>A <em>frontier</em> is a cell the Brain may walk on, known to the screen, with a cell the
 * screen has never shown ({@link Fog#UNKNOWN}) among its eight neighbours. The Brain walks over
 * cells it may walk on, found by breadth-first search from the Steps the screen offers, so its
 * first move is always one of them, and it takes one Step per Input wait: a human can take over at
 * any cell, and the next wait decides again from the new screen (FR-27, FR-28).
 *
 * <p><b>The cells it may walk on</b> are the tiles a click steps onto ({@code ValidActions}; the
 * rule is {@code docs/rules/levels.md}, "A cell a click steps onto"), less those where a click is
 * not a step. The game turns a click on a stairs cell into a floor change and a click on a chest,
 * a tomb or a pile of remains into opening it (docs/rules/game-loop.md, "{@code Hero.handle(cell)}
 * sets"), so it never paths through a transition or a heap that is not a plain one. It also avoids
 * the chasm, which jumps, the well, which drinks, a trap the screen shows armed, an enemy's cell,
 * and a cell where the game refused its Step before. An ally's or a neutral's cell is walkable past
 * the first Step: a click on it swaps places (Char.java:246-270).
 *
 * <p><b>Searching.</b> An intentional search finds every searchable secret within its radius at
 * once (docs/rules/visibility.md, "{@code Hero.search(intentional)} scans"): the square of cells
 * within one of the hero, two for the Rogue. So a wall is <em>covered</em> once a search has been
 * made from a cell within that radius of it, and a search spot is worth making only if it reaches a
 * wall no search has covered. When no frontier is reachable the Policy walks to the nearest such
 * spot -- one whose uncovered walls have cells the screen has never shown within two of them first,
 * which is where a hidden door leads somewhere -- and searches there. The next wait sees the hero
 * still on it, the {@link Memory} records it, and the Policy moves on. It makes at most
 * {@link #SEARCHES} spots per floor. A Wide Search talent reaches further than the Policy counts,
 * which only makes it search more than it needs to.
 *
 * <p><b>Descending</b> was this Policy's last plan in story 4.6, a minimal part of story 4.12 pulled
 * forward; story 4.12 moved it into the {@link Descend} Policy, which ranks above this one and also
 * decides when to leave a floor with more to do. This Policy only says when the floor is
 * {@linkplain #spent spent}: no frontier reachable and no search worth making.
 *
 * <p><b>Yielding.</b> The Policy does not enter while an enemy is in view or a Prompt is open, and it
 * yields for one wait when the hero has stood on one cell for {@link #STUCK} - 1 waits in a row: a
 * step the game refused (a forge drawn as floor, docs/rules/levels.md) would otherwise be retried
 * forever. The Memory records the cell that Step pointed at as blocked on that floor, so the next
 * plan goes round it.
 */
final class Explore implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "explore";

    /** The most search spots per floor. */
    static final int SEARCHES = 12;

    /** Waits on one cell after which the Policy yields once, and the cell its Step pointed at is blocked. */
    static final int STUCK = 3;

    /** The tiles a click steps onto, as {@code ValidActions} offers them, less the chasm and the well. */
    static final Set<Tile> WALK = EnumSet.of(Tile.EMPTY, Tile.EMPTY_SP, Tile.EMPTY_DECO,
            Tile.GRASS, Tile.HIGH_GRASS, Tile.FURROWED_GRASS, Tile.EMBERS, Tile.WATER,
            Tile.EMPTY_WELL, Tile.PEDESTAL, Tile.DOOR, Tile.OPEN_DOOR, Tile.ENTRANCE,
            Tile.ENTRANCE_SP, Tile.EXIT, Tile.UNLOCKED_EXIT);

    /** The tiles a secret door hides in: a hidden door is drawn as a wall until found (docs/rules/visibility.md). */
    private static final Set<Tile> WALLS = EnumSet.of(Tile.WALL, Tile.WALL_DECO);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "explore: floor";
    }

    /** The rooted buff's name as the HUD shows it (actors.properties:391). */
    static final String ROOTED = "rooted";

    /** The vertigo buff's name as the HUD shows it (actors.properties:435). */
    static final String VERTIGO = "vertigo";

    /** Whether the hero shows the buff named {@code name}. */
    static boolean has(Observation observation, String name) {
        for (org.shatterfish.api.BuffView buff : observation.hero().buffs()) {
            if (buff.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the hero shows it is rooted (story 4.12). A rooted hero's Step and stairs are refused
     * with no time spent (Hero.java:1822-1825, :1442-1445), and the roots wear off only as time passes
     * (Roots.java:27, a flavour buff), so the Brain offers itself neither while they hold.
     */
    static boolean rooted(Observation observation) {
        return has(observation, ROOTED);
    }

    /**
     * Whether the hero shows vertigo on a calm screen (story 4.13). A Step under vertigo goes to a
     * random neighbour (Char.java:1298-1305), and spends no time at all when the cell it aims at holds
     * a character the screen does not draw (Hero.java:1831-1834): so with nothing to flee, the Brain
     * stands still and lets the vertigo wear off, as it does for roots. With an enemy in view the fight
     * Policy still steps, and the refusals count toward a block (Beliefs).
     */
    static boolean dizzy(Observation observation) {
        return has(observation, VERTIGO) && calm(observation);
    }

    /** Whether a screen is one this Policy acts on: no Prompt open and no enemy in view. */
    static boolean calm(Observation observation) {
        if (observation.header().prompt() != PromptKind.NONE) {
            return false;
        }
        for (ActorView actor : observation.actors().actors()) {
            // An enemy the game keeps passive until provoked is scenery (story 4.7, Fight.PASSIVE).
            if (actor.alignment() == Alignment.ENEMY && !Fight.passive(actor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return calm(observation);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        if (memory.streak() == STUCK - 1) {
            return null;
        }
        return plan(observation, memory, offered);
    }

    /**
     * Whether the floor is spent (story 4.12): no frontier is reachable and no search is worth making,
     * whether around the regions the fight Policy retreated from or through them. What is left of it
     * is then nothing this Policy can uncover.
     */
    static boolean spent(Observation observation, Memory memory) {
        List<Action> offered = observation.actions().actions();
        return uncover(observation, memory, offered, walkable(observation, memory, true), searches(observation, memory), frugal(observation, memory)) == null
                && uncover(observation, memory, offered, walkable(observation, memory, false), searches(observation, memory), frugal(observation, memory)) == null;
    }

    /** Whether a frontier is reachable, around the regions avoided or through them (story 4.12). */
    static boolean frontier(Observation observation, Memory memory) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        List<Action> offered = observation.actions().actions();
        for (boolean avoiding : new boolean[]{true, false}) {
            boolean[] walk = walkable(observation, memory, avoiding);
            if (nearest(map, walk, hero, offered, cell -> frontier(map, walk, cell)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A search, or a Step toward a spot worth searching, with up to {@code limit} spots searched on this
     * floor rather than {@link #SEARCHES} (story 4.12): the descend Policy's last resort when the floor is
     * spent and no exit has been seen. Null when no spot within the limit reaches an uncovered wall.
     */
    static RunLog.Choice searchOn(Observation observation, Memory memory, List<Action> offered, int limit) {
        RunLog.Choice around = uncover(observation, memory, offered, walkable(observation, memory, true), limit, false);
        return around != null ? around
                : uncover(observation, memory, offered, walkable(observation, memory, false), limit, false);
    }

    /**
     * The plan, before the stuck rule: out of an avoided region, a rest owed, then frontier and a
     * search. They are planned around the regions the fight Policy retreated from; when that finds
     * nothing -- a region over the only corridor -- they are planned again through them, rather than
     * leave the wait to chance.
     */
    private static RunLog.Choice plan(Observation observation, Memory memory, List<Action> offered) {
        // Rooted (story 4.12): no Step is offered; a search spends the time the roots need to wear off,
        // and may find something.
        if (rooted(observation) || dizzy(observation)) {
            for (Action pass : List.of(new Action.Search(), new Action.Wait())) {
                if (offered.contains(pass)) {
                    return new RunLog.Choice(pass, Policies.CERTAIN, rooted(observation) ? "rooted" : "vertigo");
                }
            }
        }

        // Inside a region the fight Policy retreated from (story 4.7): out of it first, a Step at a
        // time, before any plan takes the hero back toward what it fled.
        RunLog.Choice away = away(observation, memory, offered);
        if (away != null) {
            return away;
        }

        // On the floor above one the hero fled by the stairs (story 4.7): rest to full health before
        // anything else, so the plan that leads back down does not return to the fight it left at
        // the health it left with.
        if (restOwed(observation, memory)) {
            for (Action rest : List.of(new Action.Rest(true), new Action.Rest(false), new Action.Search())) {
                if (offered.contains(rest)) {
                    return new RunLog.Choice(rest, Policies.CERTAIN, "rest: before-descent");
                }
            }
        }

        RunLog.Choice around = uncover(observation, memory, offered, walkable(observation, memory, true), searches(observation, memory), frugal(observation, memory));
        return around != null ? around : uncover(observation, memory, offered, walkable(observation, memory, false), searches(observation, memory), frugal(observation, memory));
    }

    /**
     * The offered Step that most increases the distance from the centre of a region the fight Policy
     * retreated from, when the hero stands inside one (story 4.7); null when it stands in none, or no
     * Step takes it farther out.
     */
    static RunLog.Choice away(Observation observation, Memory memory, List<Action> offered) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        boolean[] open = walkable(observation, memory, false);
        for (Memory.Avoid region : memory.avoided(depth, branch, memory.waits())) {
            if (region.covers(depth, branch, hero, map.width())) {
                Action.Step away = null;
                int farthest = distance(map, hero, region.cell());
                for (Action action : offered) {
                    if (action instanceof Action.Step step && step.cell() < open.length && open[step.cell()]
                            && distance(map, step.cell(), region.cell()) > farthest
                            && !map.transitions().stream().anyMatch(t -> t.cell() == step.cell())) {
                        away = step;
                        farthest = distance(map, step.cell(), region.cell());
                    }
                }
                if (away != null) {
                    return new RunLog.Choice(away, Policies.CERTAIN, "away " + farthest);
                }
            }
        }
        return null;
    }

    /**
     * Whether a rest is owed before going back down (story 4.7): the floor below was fled by the
     * stairs since the hero was last healed there, its hit points are short, it is neither hungry
     * nor starving -- a starving hero does not regenerate (Regeneration.java:56) -- and it has not
     * already rested {@link #RESTS} waits for it.
     */
    static boolean restOwed(Observation observation, Memory memory) {
        String below = below(observation);
        return Memory.count(memory.flights(), below, 0) > Memory.count(memory.flights(), below, 1)
                && observation.hero().hp() < observation.hero().ht()
                && observation.hero().hunger() == org.shatterfish.api.Hunger.NONE
                && Memory.count(memory.flights(), below, 2) < RESTS;
    }

    /** The key of the floor below this one, as the Memory counts flights. */
    static String below(Observation observation) {
        return (observation.header().depth() + 1) + ":" + observation.header().branch();
    }

    /** The most waits the hero rests before going back to a floor it fled. */
    static final int RESTS = 50;

    /**
     * Frontier, then a search, over the cells {@code walk} allows, with up to {@code limit} spots
     * searched on the floor; null when neither is left.
     */
    /**
     * The most search spots on this floor: {@link #SEARCHES}, or {@link #FRUGAL_SEARCHES} while food is
     * tight (story 4.13, {@link Larder}): a search costs two turns and four more hunger
     * (Hero.java:211-212, :2624-2629).
     */
    static int searches(Observation observation, Memory memory) {
        return frugal(observation, memory) ? FRUGAL_SEARCHES : SEARCHES;
    }

    /**
     * Whether food is tight (story 4.13): then only promising spots are searched. Not on the floor above a
     * boss floor -- every fifth depth (Dungeon.java:441-443) -- which places no food and seals behind the
     * hero: a floor spent early there would send a hungry hero down to the boss.
     */
    static boolean frugal(Observation observation, Memory memory) {
        return Larder.frugal(observation, memory) && (observation.header().depth() + 1) % 5 != 0;
    }

    /** The most search spots per floor while food is tight: only where a secret is plausible. */
    static final int FRUGAL_SEARCHES = 4;

    private static RunLog.Choice uncover(Observation observation, Memory memory, List<Action> offered, boolean[] walk,
                                         int limit, boolean promisingOnly) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        Path frontier = nearest(map, walk, hero, offered, cell -> frontier(map, walk, cell));
        if (frontier != null) {
            return new RunLog.Choice(frontier.step, Policies.CERTAIN, "frontier " + frontier.distance);
        }

        int radius = radius(observation);
        List<Memory.Spot> searched = memory.dwelt().stream().filter(spot -> spot.on(depth, branch)).toList();
        if (searched.size() < limit) {
            if (worth(map, walk, hero, radius, searched, promisingOnly)
                    && !searched.contains(new Memory.Spot(depth, branch, hero))) {
                Action search = new Action.Search();
                if (offered.contains(search)) {
                    return new RunLog.Choice(search, Policies.CERTAIN,
                            "search " + (searched.size() + 1) + "/" + limit);
                }
            }
            Path spot = nearest(map, walk, hero, offered, cell -> worth(map, walk, cell, radius, searched, true));
            if (spot == null && !promisingOnly) {
                spot = nearest(map, walk, hero, offered, cell -> worth(map, walk, cell, radius, searched, false));
            }
            if (spot != null) {
                return new RunLog.Choice(spot.step, Policies.CERTAIN, "search-spot " + spot.distance);
            }
        }
        return null;
    }

    /** The Chebyshev distance between two cells. */
    private static int distance(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }

    /** The search radius: one, two for the Rogue (Hero.java:2506). */
    static int radius(Observation observation) {
        return observation.header().heroClass() == HeroClass.ROGUE ? 2 : 1;
    }

    /**
     * Whether a search from {@code cell} reaches a wall no search on this floor has covered, and,
     * when {@code promising}, one with a cell the screen has never shown within two of it.
     */
    static boolean worth(MapSection map, boolean[] walk, int cell, int radius, List<Memory.Spot> searched,
                         boolean promising) {
        if (!walk[cell]) {
            return false;
        }
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int wx = x + dx;
                int wy = y + dy;
                if ((dx == 0 && dy == 0) || wx < 0 || wy < 0 || wx >= width || wy >= map.height()) {
                    continue;
                }
                int wall = wx + wy * width;
                if (map.fog().get(wall) == Fog.UNKNOWN || !WALLS.contains(map.tiles().get(wall))) {
                    continue;
                }
                if (covered(width, wall, radius, searched)) {
                    continue;
                }
                if (!promising || unknownNear(map, wall, 2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean covered(int width, int wall, int radius, List<Memory.Spot> searched) {
        for (Memory.Spot spot : searched) {
            if (Math.abs(spot.cell() % width - wall % width) <= radius
                    && Math.abs(spot.cell() / width - wall / width) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean unknownNear(MapSection map, int cell, int within) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -within; dy <= within; dy++) {
            for (int dx = -within; dx <= within; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < width && ny < map.height()
                        && map.fog().get(nx + ny * width) == Fog.UNKNOWN) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A first Step and how many Steps the path takes. */
    record Path(Action step, int distance) {
    }

    /** What a path search stops at. */
    interface Goal {
        boolean at(int cell);
    }

    /**
     * The nearest goal cell other than the hero's, by Steps over walkable cells, and the offered
     * Step that starts the way there; ties go to the first found, which is the order the screen
     * offers the Steps and then cell order, so the answer is a function of the screen.
     */
    static Path nearest(MapSection map, boolean[] walk, int hero, List<Action> offered, Goal goal) {
        int cells = walk.length;
        int[] first = new int[cells];
        int[] distance = new int[cells];
        java.util.Arrays.fill(distance, -1);
        distance[hero] = 0;
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
            int x = cell % width;
            int y = cell / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
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

    /** Whether {@code cell} is walkable and has a never-shown cell among its eight neighbours. */
    static boolean frontier(MapSection map, boolean[] walk, int cell) {
        if (!walk[cell]) {
            return false;
        }
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()
                        && map.fog().get(nx + ny * width) == Fog.UNKNOWN) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The cells the Policy may walk on: known, a walking tile, and not a place a click does something
     * else, nor an enemy's cell, nor a cell where the game refused this floor's Step before.
     */
    static boolean[] walkable(Observation observation, Memory memory) {
        return walkable(observation, memory, true);
    }

    /**
     * The blobs no Policy walks into, by the class names the Observation carries (story 4.10): fire
     * and the harmful gases.
     */
    static final Set<String> HARMFUL = Set.of("Fire", "ToxicGas", "CorrosiveGas", "ParalyticGas");

    /**
     * The cells the Policy may walk on, less, when {@code avoiding}, the regions the fight Policy
     * retreated from and has not yet let lapse (story 4.7), and the cells remembered clouded with fire
     * or a harmful gas (story 4.10, {@link Memory#clouds}).
     */
    static boolean[] walkable(Observation observation, Memory memory, boolean avoiding) {
        return walkable(observation, memory, avoiding, false);
    }

    /**
     * {@link #walkable(Observation, Memory, boolean)}, with the clouded cells let through when
     * {@code throughClouds}: the way out of one for a hero standing in it (story 4.10).
     */
    static boolean[] walkable(Observation observation, Memory memory, boolean avoiding, boolean throughClouds) {
        MapSection map = observation.map();
        int cells = map.tiles().size();
        boolean[] walk = new boolean[cells];
        for (int cell = 0; cell < cells; cell++) {
            walk[cell] = map.fog().get(cell) != Fog.UNKNOWN && WALK.contains(map.tiles().get(cell));
        }
        for (TransitionView transition : map.transitions()) {
            walk[transition.cell()] = false;
        }
        for (HeapView heap : map.heaps()) {
            if (heap.kind() != HeapKind.HEAP) {
                walk[heap.cell()] = false;
            }
        }
        for (TrapView trap : map.traps()) {
            if (trap.active()) {
                walk[trap.cell()] = false;
            }
        }
        if (!throughClouds) {
            for (org.shatterfish.api.BlobCell blob : map.blobs()) {
                if (blob.cell() < cells && blob.kinds().stream().anyMatch(HARMFUL::contains)) {
                    walk[blob.cell()] = false;
                }
            }
            for (Memory.Cloud cloud : memory.clouds()) {
                if (cloud.cell() < cells && memory.clouded(observation.header().depth(), observation.header().branch(),
                        cloud.cell(), memory.waits())) {
                    walk[cloud.cell()] = false;
                }
            }
        }
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                walk[actor.cell()] = false;
            }
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Spot spot : memory.blocked()) {
            if (spot.on(depth, branch) && spot.cell() < cells) {
                walk[spot.cell()] = false;
            }
        }
        // Cells a Step was refused onto in a fight, until the block lapses (story 4.12).
        for (Memory.Cloud block : memory.fleeting()) {
            if (block.depth() == depth && block.branch() == branch && block.cell() < cells
                    && block.until() >= memory.waits()) {
                walk[block.cell()] = false;
            }
        }
        if (avoiding) {
            for (Memory.Avoid region : memory.avoided(depth, branch, memory.waits())) {
                for (int cell = 0; cell < cells; cell++) {
                    if (region.covers(depth, branch, cell, map.width())) {
                        walk[cell] = false;
                    }
                }
            }
        }
        return walk;
    }
}
