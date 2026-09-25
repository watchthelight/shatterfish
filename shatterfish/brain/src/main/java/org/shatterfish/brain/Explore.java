package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.api.TransitionView;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Uncover the floor (story 4.6, FR-31): one step at a time toward the nearest unexplored frontier,
 * and when none is left, a bounded round of searches for secret doors.
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
 * sets"), so it never paths through a transition or a heap that is not a plain one: travelling or
 * opening is another Policy's decision. It also avoids the chasm, which jumps, the well, which
 * drinks, a trap the screen shows armed, and a cell a character stands on. Cells the screen has
 * never shown are not walked on.
 *
 * <p><b>Searching.</b> An intentional search finds every searchable secret within its radius at
 * once (docs/rules/visibility.md, "{@code Hero.search(intentional)} scans"), so searching twice
 * from one cell finds nothing more. When no frontier is reachable the Policy walks to the nearest
 * cell beside a wall that the hero has not yet stood on at two waits in a row, and searches there;
 * the next wait sees the hero still on it, the {@link Memory} records it as dwelt on, and the
 * Policy moves on. It makes at most {@link #SEARCHES} such spots per floor and then gives up the
 * floor, leaving the wait to the Policies below it. That the hero stood still is what the screen
 * shows; whether it searched is not, and the Memory does not claim it did.
 *
 * <p><b>Yielding.</b> The Policy does not enter while an enemy is in view, while a Prompt is open,
 * or when the hero has stood on one cell for {@link #STUCK} waits in a row outside a search: a step
 * the game refused (a forge drawn as floor, docs/rules/levels.md) would otherwise be retried
 * forever, and the Policies below it break the loop.
 */
final class Explore implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "explore";

    /** The most search spots per floor. */
    static final int SEARCHES = 12;

    /** Waits in a row on one cell, outside a search, after which the Policy yields. */
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

    @Override
    public boolean enters(Observation observation, Memory memory) {
        if (observation.header().prompt() != PromptKind.NONE) {
            return false;
        }
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int hero = observation.hero().cell();
        boolean[] walk = walkable(observation);

        // Standing on a search spot not yet dwelt on: search here, once.
        boolean searchable = spot(map, walk, hero) && !memory.dwelt().contains(new Memory.Spot(depth, hero));
        if (!searchable && memory.streak() >= STUCK - 1) {
            return null;
        }

        Path frontier = nearest(map, walk, hero, offered, cell -> frontier(map, walk, cell));
        if (frontier != null) {
            return new RunLog.Choice(frontier.step, Policies.CERTAIN, "frontier " + frontier.distance);
        }
        int searched = (int) memory.dwelt().stream()
                .filter(spot -> spot.depth() == depth && spot.cell() < walk.length && spot(map, walk, spot.cell()))
                .count();
        if (searched >= SEARCHES) {
            return null;
        }
        if (searchable) {
            Action search = new Action.Search();
            return offered.contains(search)
                    ? new RunLog.Choice(search, Policies.CERTAIN, "search " + (searched + 1) + "/" + SEARCHES)
                    : null;
        }
        Path spot = nearest(map, walk, hero, offered,
                cell -> spot(map, walk, cell) && !memory.dwelt().contains(new Memory.Spot(depth, cell)));
        return spot == null ? null : new RunLog.Choice(spot.step, Policies.CERTAIN, "search-spot " + spot.distance);
    }

    /** A first Step and how many Steps the path takes. */
    private record Path(Action step, int distance) {
    }

    private interface Goal {
        boolean at(int cell);
    }

    /**
     * The nearest goal cell other than the hero's, by Steps over walkable cells, and the offered
     * Step that starts the way there; ties go to the first found, which is the order the screen
     * offers the Steps and then cell order, so the answer is a function of the screen.
     */
    private static Path nearest(MapSection map, boolean[] walk, int hero, List<Action> offered, Goal goal) {
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
        return walk[cell] && around(map, cell, Fog.UNKNOWN, null);
    }

    /** Whether {@code cell} is walkable and has a wall among its eight neighbours: somewhere a search can find a door. */
    static boolean spot(MapSection map, boolean[] walk, int cell) {
        return walk[cell] && around(map, cell, null, WALLS);
    }

    /** Whether a neighbour of {@code cell} has the fog {@code fog} or, when it is null, a tile in {@code tiles}. */
    private static boolean around(MapSection map, int cell, Fog fog, Set<Tile> tiles) {
        int width = map.width();
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
                if (fog != null ? map.fog().get(next) == fog
                        : map.fog().get(next) != Fog.UNKNOWN && tiles.contains(map.tiles().get(next))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The cells the Policy may walk on: known, a walking tile, and not a place a click does something else. */
    static boolean[] walkable(Observation observation) {
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
        for (ActorView actor : observation.actors().actors()) {
            walk[actor.cell()] = false;
        }
        return walk;
    }
}
