package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Codex;
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
 * wall no search has covered. When no frontier is reachable by any path, around the regions the fight
 * Policy retreated from or through them (issue #183), the Policy walks to the nearest such
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

    private final Codex.Knowledge knowledge;

    /** Exploring, with {@code knowledge} telling the enemies it may explore past (the passive ones) from the rest. */
    Explore(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

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

    /** The blindness buff's name as the HUD shows it (actors.properties:131). */
    static final String BLINDED = "blinded";

    /** The shadowmeld buff's name as the HUD shows it (actors.properties:398). */
    static final String SHADOWMELDED = "shadowmelded";

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
     * Whether {@code cell} is drawn as a chasm (issue #170). A click on one asks whether to jump
     * ({@code core/.../actors/hero/Hero.java:1881-1893}; {@code core/.../levels/features/Chasm.java:57-97}),
     * and a jump takes the hero down a floor, where he lands crippled, bleeding and hurt by half his
     * health or more ({@code Chasm.java:99-121}, {@code :131-152}), with the floor he left unfinished.
     * {@link #WALK} already leaves the chasm out of every plan; the Brain leaves the offered Steps onto
     * one out too ({@code Brain.decide}), so no Policy's own pick among them, a fight's chokepoint or the
     * fallback's, can take one either. No Policy means to jump today.
     */
    static boolean chasm(Observation observation, int cell) {
        MapSection map = observation.map();
        return cell >= 0 && cell < map.tiles().size() && map.tiles().get(cell) == Tile.CHASM;
    }

    /**
     * Whether the hero shows vertigo on a calm screen (story 4.13). A Step under vertigo goes to a
     * random neighbour (Char.java:1298-1305), and spends no time at all when the cell it aims at holds
     * a character the screen does not draw (Hero.java:1831-1834): so with nothing to flee, the Brain
     * stands still and lets the vertigo wear off, as it does for roots. With an enemy in view the fight
     * Policy still steps, and the refusals count toward a block (Beliefs).
     */
    static boolean dizzy(Observation observation, Codex.Knowledge knowledge) {
        return has(observation, VERTIGO) && Calm.quiet(observation, knowledge);
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Calm.quiet(observation, knowledge);
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
        return uncover(observation, memory, observation.actions().actions(), searches(observation, memory),
                frugal(observation, memory)) == null;
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
        return uncover(observation, memory, offered, limit, false);
    }

    /**
     * The plan, before the stuck rule: out of an avoided region, a rest owed, then frontier and a
     * search ({@link #uncover(Observation, Memory, List, int, boolean)}).
     */
    private RunLog.Choice plan(Observation observation, Memory memory, List<Action> offered) {
        // Rooted (story 4.12) or dizzy (story 4.13): the time the buff needs to wear off is waited out. A
        // wait, not a search: a search costs two turns and four more hunger (Hero.java:2621-2629) and
        // counts as one of the floor's search spots, spent on a place no plan chose.
        if (rooted(observation) || dizzy(observation, knowledge)) {
            for (Action pass : List.of(new Action.Wait(), new Action.Search())) {
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

        return uncover(observation, memory, offered, searches(observation, memory), frugal(observation, memory));
    }

    /**
     * Frontier first, then a search, with up to {@code limit} spots searched on this floor (issue #183):
     * a frontier around the regions the fight Policy retreated from, then a frontier through them, and
     * only then a search spot, around them and then through them. A search is never made while a frontier
     * is reachable by any walkable path: a region laid over the only door to unexplored space made the
     * plan around it search every wall in reach, twelve spots on the owner's Run, before the plan through
     * it was tried. Null when neither is left.
     */
    static RunLog.Choice uncover(Observation observation, Memory memory, List<Action> offered, int limit,
                                 boolean promisingOnly) {
        boolean[] around = walkable(observation, memory, true);
        boolean[] through = walkable(observation, memory, false);
        RunLog.Choice choice = toFrontier(observation, offered, around);
        if (choice == null) {
            choice = toFrontier(observation, offered, through);
        }
        if (choice == null) {
            choice = search(observation, memory, offered, around, limit, promisingOnly);
        }
        if (choice == null) {
            choice = search(observation, memory, offered, through, limit, promisingOnly);
        }
        return choice;
    }

    /**
     * The offered Step that starts the shortest walk out of every region the fight Policy retreated from,
     * when the hero stands inside one (story 4.7); null when it stands in none, no walk leads out,
     * or the shortest walk out starts nearer the centre (issue #165). The
     * reason counts the Steps out. The first Step never goes nearer the centre of a region the hero
     * stands in, which is where the enemy was.
     *
     * <p>Story 4.7 stepped to the neighbour farthest from the centre of the first region covering the
     * hero, one region at a time; where two regions overlapped, the way out of one led into the other and
     * back, for as long as they lasted (issue #174). All of them count at once now.
     */
    static RunLog.Choice away(Observation observation, Memory memory, List<Action> offered) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        int width = map.width();
        List<Memory.Avoid> regions = memory.avoided(depth, branch, memory.waits());
        List<Memory.Avoid> around = regions.stream().filter(region -> region.covers(depth, branch, hero, width)).toList();
        if (around.isEmpty()) {
            return null;
        }
        boolean[] open = walkable(observation, memory, false);
        List<Action> outward = offered.stream().filter(action -> !(action instanceof Action.Step step)
                || around.stream().allMatch(region -> distance(map, step.cell(), region.cell())
                        >= distance(map, hero, region.cell()))).toList();
        Goal outside = cell -> regions.stream().noneMatch(region -> region.covers(depth, branch, cell, width));
        Path out = nearest(map, open, hero, outward, outside);
        // Issue #165: the first Step is held to the ones that never go nearer the centre, and the walk from
        // there on is not, so where the shortest way out starts nearer the centre the Step chosen can lead
        // the long way round. From the cell it reaches the shortest way out may start nearer the centre in
        // turn, back where the hero stood, and the two Steps undo each other until the hero starves (a
        // region wider than the room is high did it). Only a Step on a shortest way out is handed over; where
        // none is, the region is crossed rather than fled, as the plans through it do (issue #174).
        Path shortest = nearest(map, open, hero, offered, outside);
        if (out == null || (shortest != null && out.distance() > shortest.distance())) {
            return null;
        }
        return new RunLog.Choice(out.step(), Policies.CERTAIN, "away " + out.distance());
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
     * The most search spots on this floor: {@link #SEARCHES}, or {@link #FRUGAL_SEARCHES} while food is
     * tight (story 4.13, {@link Larder}): a search costs two turns and four more hunger
     * (Hero.java:211-212, :2624-2629).
     */
    static int searches(Observation observation, Memory memory) {
        return frugal(observation, memory) ? FRUGAL_SEARCHES : SEARCHES;
    }

    /**
     * Whether food is tight (story 4.13): then only promising spots are searched. Not on the floor above a
     * boss floor -- every fifth depth of the main branch (Dungeon.java:441-443) -- which still places its
     * food (Level.java:224-226) where the boss floor below places none and is sealed once the boss is
     * met (Goo.java:135, Level.java:657-661): the last floor to find food and items on before the boss.
     */
    static boolean frugal(Observation observation, Memory memory) {
        return Larder.frugal(observation, memory) && !beforeBoss(observation);
    }

    /** Whether this is the main-branch floor just above a boss floor (Dungeon.java:441-443). */
    static boolean beforeBoss(Observation observation) {
        return observation.header().branch() == 0 && (observation.header().depth() + 1) % 5 == 0;
    }

    /** The most search spots per floor while food is tight: only where a secret is plausible. */
    static final int FRUGAL_SEARCHES = 4;

    /** A Step toward the nearest frontier over the cells {@code walk} allows, or null when none is reachable. */
    private static RunLog.Choice toFrontier(Observation observation, List<Action> offered, boolean[] walk) {
        MapSection map = observation.map();
        Path frontier = nearest(map, walk, observation.hero().cell(), offered, cell -> frontier(map, walk, cell));
        return frontier == null ? null
                : new RunLog.Choice(frontier.step, Policies.CERTAIN, "frontier " + frontier.distance);
    }

    /**
     * A search, or a Step toward a spot worth searching, over the cells {@code walk} allows, with up to
     * {@code limit} spots searched on the floor; null when none is left.
     */
    private static RunLog.Choice search(Observation observation, Memory memory, List<Action> offered, boolean[] walk,
                                        int limit, boolean promisingOnly) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        int hero = observation.hero().cell();
        if (!searchReaches(observation)) {
            return null;
        }
        int radius = radius(observation);
        List<Memory.Spot> searched = memory.dwelt().stream().filter(spot -> spot.on(depth, branch)).toList();
        // The searches made ahead of a Step near a shown trap (Traps) are no spots of the floor's budget.
        long spots = searched.stream().filter(spot -> !Traps.near(observation, spot.cell())).count();
        if (spots < limit) {
            if (worth(map, walk, hero, radius, searched, promisingOnly)
                    && !searched.contains(new Memory.Spot(depth, branch, hero))) {
                Action search = new Action.Search();
                if (offered.contains(search)) {
                    return new RunLog.Choice(search, Policies.CERTAIN,
                            "search " + (spots + 1) + "/" + limit);
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

    /** The magical sight buff's name as the HUD shows it (actors.properties:296). */
    static final String MAGICAL_SIGHT = "magical sight";

    /**
     * Whether a search made on this screen reaches the whole of {@link #radius} around the hero (issues #205,
     * #229). The screen is the one the search is issued on, not the one after it: {@code Hero.search} runs at
     * the start of the Action, on the field of view then, and the buffs a turn later may have worn off.
     *
     * <p>{@code Hero.search} finds secrets only on cells in the hero's field of view
     * ({@code core/.../actors/hero/Hero.java:2562}). A hero blinded or shadowmelded is not sighted
     * ({@code core/.../levels/Level.java:1332-1333}) and sees only the 3x3 around it, the sense radius of one
     * ({@code Level.java:1400-1421}), which a radius of one fills and the Rogue's radius of two
     * ({@code Hero.java:2522}) does not. The Darkness challenge narrows the view to two
     * ({@code Level.java:158}), which fills the radius of two, unless the floor also feels dark, which cuts it to
     * one ({@code Level.java:274}, {@code :467}); the cells next to the hero are always in view. A search
     * has no refusal to read: it is offered only with no Prompt open ({@code ValidActions}), the executor
     * hands it straight to {@code Hero.search(true)} (ActionExecutor.java:131-133), and that spends its time
     * ({@code Hero.java:2645}) whenever the hero is alive.
     *
     * <p>Magical sight (sense 12, {@code MagicalSight.java:34}) copies the discoverable cells within its sense
     * into the field of view, sighted or not ({@code Level.java:1389-1421}), which fills the Rogue's two cells, so
     * a search under it is whole. Two more widen the search but are not read. Mind vision's sense of two
     * ({@code MindVision.java:32}) is copied by the rounded radius ({@code Level.java:1400-1419}), which leaves
     * the four corners of the Rogue's square of two out of view ({@code ShadowCaster.java:41-43}; the corner
     * patch at {@code :87-92} is in {@code castShadow} only). Foresight's scan of {@code Foresight.DISTANCE} = 8
     * ignores the view ({@code Hero.java:2525-2536}, {@code :2562}), but only the potion's buff does that
     * ({@code Hero.java:2525}), and the Talisman of Foresight's buff of the same name shows on the HUD
     * ({@code TalismanOfForesight.java:363-367}) and does not; the screen cannot tell them apart. A search under
     * either is under-credited, never over-credited. Wide Search is a Rogue talent the radius does not count (see
     * {@link #radius}).
     * The Policy that would search again without coverage waits instead while the sight is short
     * ({@link #search}), rather than repeating a search that finds nothing.
     */
    static boolean searchReaches(Observation observation) {
        if (radius(observation) <= 1) {
            return true;
        }
        if (has(observation, MAGICAL_SIGHT)) {
            return true;
        }
        // The Darkness challenge's view of two is cut to round(5*2/8) = 1 on a floor that feels dark
        // (Level.java:274, :467), which the map shows.
        boolean dim = observation.header().challenges().contains(org.shatterfish.api.Challenge.DARKNESS)
                && observation.map().feeling() == org.shatterfish.api.Feeling.DARK;
        return !(dim || has(observation, BLINDED) || has(observation, SHADOWMELDED));
    }

    /**
     * The search radius: one, two for the Rogue (Hero.java:2522). The Wide Search talent adds one more
     * (Hero.java:2523) and is not counted: with one point the scan is circular (Hero.java:2521), so a square of
     * three would credit corners it does not reach, and under-crediting only makes the Brain search again.
     */
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
    static final Set<String> HARMFUL = Set.of("Fire", "ToxicGas", "CorrosiveGas", "ParalyticGas", "EternalFire");

    /**
     * The magical fire room's flames, by the class name the Observation carries (issue #219). Unlike
     * Fire they never burn out, and they set alight a character on a flame's cell or on any of the
     * four cells beside one (MagicalFireRoom.java:205-220), so those cells are fire to stay off.
     */
    static final String ETERNAL_FIRE = "EternalFire";

    /**
     * The cells the screen shows as a flame of the magical fire room or as one of the four cells
     * beside a flame (MagicalFireRoom.java:205-220; a flame at the edge of a row has no neighbour
     * across it).
     */
    static Set<Integer> ignited(MapSection map) {
        Set<Integer> cells = new java.util.TreeSet<>();
        int width = map.width();
        int size = map.tiles().size();
        for (org.shatterfish.api.BlobCell blob : map.blobs()) {
            if (!blob.kinds().contains(ETERNAL_FIRE)) {
                continue;
            }
            int cell = blob.cell();
            cells.add(cell);
            if (cell % width > 0) {
                cells.add(cell - 1);
            }
            if (cell % width < width - 1) {
                cells.add(cell + 1);
            }
            if (cell >= width) {
                cells.add(cell - width);
            }
            if (cell + width < size) {
                cells.add(cell + width);
            }
        }
        return cells;
    }

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
        return walkable(observation, memory, avoiding ? 0 : Long.MAX_VALUE, throughClouds);
    }

    /**
     * {@link #walkable(Observation, Memory, boolean, boolean)} with only the regions that have at least
     * {@code keepFor} waits left to run kept out of (issue #183): 0 keeps all of them, a larger number only
     * the young ones. The pick-up Policy crosses a region once it has half run, not before.
     */
    static boolean[] walkable(Observation observation, Memory memory, long keepFor, boolean throughClouds) {
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
            for (int cell : ignited(map)) {
                walk[cell] = false;
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
        if (keepFor != Long.MAX_VALUE) {
            for (Memory.Avoid region : memory.avoided(depth, branch, memory.waits())) {
                if (region.until() - memory.waits() < keepFor) {
                    continue;
                }
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
