package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Heuristic;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Tengu's second half: the cells the screen shows are about to be struck, and the walk that keeps beside
 * him through them (story 10.20, issue #366).
 *
 * <p><b>What the game throws.</b> Once his hit points are at or under half ({@code Tengu.java:451-453}) he
 * spends a turn or two on an ability instead of a blow, one more for every jump he has made
 * ({@code :450-489}, {@code :492-558}); all three aim at the hero, and none can be thrown while he is out of
 * the hero's sight, since he only casts in his hunting turn ({@code :385-397}).
 *
 * <ul>
 *   <li><b>The smoke bomb</b> lands on a cell beside the hero as a heap that cannot be picked up
 *       ({@code :566-605}, {@code :709-727}) and counts "3...", "2...", "1..." over it, one a turn, before it
 *       goes off on the cells within two steps of it that are not solid, for 15 to 30 less the hero's armour
 *       at depth 10 ({@code :607-662}, {@code :635}). The heap is the one thing the Observation holds of it;
 *       the number floats over the cell and the Observation does not carry it, so the whole of the bomb's
 *       life is treated as the turn it lands (a few turns in the game).</li>
 *   <li><b>The fire</b> is a cone of steam laid from the hero's cell outward, three cells wide at each
 *       step: each cell holds the blob for two ticks and then burns, setting alight whoever stands on it
 *       ({@code :766-929}, {@code :846-911}). The blob is drawn, so the cells that hold it are the cells that
 *       burn in a tick or two; the cells the cone will reach after them are not drawn yet and are not on the map.
 *       Burning is 1 to 5 a turn for eight turns ({@code Burning.java:54}, {@code :105}).</li>
 *   <li><b>The shocker</b> lands on a cell beside the hero and not beside Tengu, stays for the rest of the
 *       fight, and strikes its eight neighbours and its own cell in turn, the four beside it and then the four
 *       across, 2 + depth = 12 each time ({@code :932-1074}, {@code :1069}). Its blob is drawn on the cells it is about to strike.</li>
 * </ul>
 *
 * <p><b>What the Brain reads.</b> The Observation's heaps (a heap of a smoke bomb or a shocker, by the name the
 * sprite is drawn with) and blobs (the fire's and the shocker's, by class name; the Observer carries both
 * since both are drawn). It reads no cell number, no countdown and no hit point of Tengu's: a threat that is not
 * drawn is not on the map, and two screens that differ only in the seed or in what is hidden give the same map
 * ({@code TenguThreatLeakTest}).
 *
 * <p><b>What it does with the map.</b> {@link #choose} keeps the hero beside Tengu and strikes him, and leaves
 * a cell only when the map says staying costs more than going: each cell reachable in a few steps is priced as
 * what the map says it costs plus the strikes not made on the way, and the cheapest plan is taken; with every
 * cell beside him priced, the least bad is accepted. The costs are the Playbook's (hit points, {@code tengu-threat}).
 * A hero that stands clear while the only cells beside him are under a blast or the fire holds there for it to pass,
 * since both are over in a few turns; the shocker does not pass, so the hero never holds for it.
 */
final class Threats {

    /** The smoke bomb as the heap's sprite names it (actors.properties: {@code actors.mobs.tengu$bombability$bombitem.name}). */
    static final String BOMB = "Smoke Bomb";

    /** The shocker as the heap's sprite names it ({@code actors.mobs.tengu$shockerability$shockeritem.name}). */
    static final String SHOCKER = "Shocker";

    /** Tengu's fire blob, by class name ({@code Tengu.java:846}). */
    static final String FIRE = "FireBlob";

    /** Tengu's shocker blob, by class name ({@code Tengu.java:1041}). */
    static final String SHOCK = "ShockerBlob";

    /** The bomb's blast radius, in steps over cells that are not solid ({@code Tengu.java:630}). */
    static final int BOMB_RADIUS = 2;

    /** The most steps a plan to leave a cell may take; a bomb's blast reaches two, so three gets clear of it. */
    @Heuristic("tengu-threat")
    static final int HORIZON = 4;

    /** Tiles the blast does not cross ({@code Level.solid}); doors count as open, the safe way, as in Chests. */
    private static final Set<Tile> SOLID = EnumSet.of(Tile.WALL, Tile.WALL_DECO, Tile.LOCKED_DOOR, Tile.CRYSTAL_DOOR,
            Tile.LOCKED_EXIT, Tile.BARRICADE, Tile.BOOKSHELF, Tile.STATUE, Tile.STATUE_SP, Tile.REGION_DECO,
            Tile.REGION_DECO_ALT);

    private Threats() {
    }

    /**
     * The hit points each cell is expected to cost, by what threatens it. {@code bomb} lasts a few turns and so
     * does {@code fire}; {@code shock} lasts the fight. All three are zero on a cell nothing drawn threatens.
     */
    record Map(int[] bomb, int[] fire, int[] shock) {

        /** The total cost of standing on {@code cell}. */
        int cost(int cell) {
            return bomb[cell] + fire[cell] + shock[cell];
        }

        /** Whether anything is drawn that threatens any cell. */
        boolean any() {
            for (int cell = 0; cell < bomb.length; cell++) {
                if (cost(cell) > 0) {
                    return true;
                }
            }
            return false;
        }

        /** The part of the cost that is over within a few turns. */
        int passing(int cell) {
            return bomb[cell] + fire[cell];
        }
    }

    /**
     * The threat map of {@code observation} at the Playbook's costs: empty off the sealed prison boss floor.
     * Only the effects the screen draws are on it.
     */
    static Map map(Observation observation, Playbook.Tengu costs) {
        MapSection map = observation.map();
        int cells = map.tiles().size();
        int[] bomb = new int[cells];
        int[] fire = new int[cells];
        int[] shock = new int[cells];
        Map threats = new Map(bomb, fire, shock);
        if (!Darts.here(observation)) {
            return threats;
        }
        for (HeapView heap : map.heaps()) {
            if (heap.item().equals(BOMB)) {
                blast(map, heap.cell(), bomb, costs.bombCost());
            } else if (heap.item().equals(SHOCKER)) {
                for (int cell : around(map, heap.cell())) {
                    shock[cell] = costs.shockCost();
                }
            }
        }
        for (BlobCell blob : map.blobs()) {
            if (blob.kinds().contains(FIRE)) {
                fire[blob.cell()] = costs.fireCost();
            }
            if (blob.kinds().contains(SHOCK)) {
                shock[blob.cell()] = costs.shockCost();
            }
        }
        return threats;
    }

    /** The cells within {@link #BOMB_RADIUS} steps of {@code centre} over cells that are not solid, each priced at {@code cost}. */
    private static void blast(MapSection map, int centre, int[] into, int cost) {
        int cells = map.tiles().size();
        int[] distance = new int[cells];
        Arrays.fill(distance, -1);
        distance[centre] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(centre);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            into[cell] += cost;
            if (distance[cell] == BOMB_RADIUS) {
                continue;
            }
            for (int next : neighbours(map, cell)) {
                if (distance[next] < 0 && !SOLID.contains(map.tiles().get(next))
                        && map.fog().get(next) != Fog.UNKNOWN) {
                    distance[next] = distance[cell] + 1;
                    queue.add(next);
                }
            }
        }
    }

    /** {@code centre} and its eight neighbours that are not solid. */
    private static List<Integer> around(MapSection map, int centre) {
        List<Integer> cells = new java.util.ArrayList<>();
        cells.add(centre);
        for (int next : neighbours(map, centre)) {
            if (!SOLID.contains(map.tiles().get(next)) && map.fog().get(next) != Fog.UNKNOWN) {
                cells.add(next);
            }
        }
        return cells;
    }

    private static List<Integer> neighbours(MapSection map, int cell) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        List<Integer> out = new java.util.ArrayList<>(8);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < map.height()) {
                    out.add(nx + ny * width);
                }
            }
        }
        return out;
    }

    /**
     * The hero's choice on a screen whose map holds a threat, or null when no plan is made (the caller walks as it
     * did before). A plan is priced in hit points, the Playbook's:
     *
     * <ul>
     *   <li>striking where it stands costs what the map says its cell costs;</li>
     *   <li>walking to a cell within {@link #HORIZON} steps costs a strike lost for every step ({@code adjacent_cost}),
     *       what the fire and the shocker take of every cell it enters, and what the map says the cell it lands on
     *       costs; a cell not beside him costs besides the strikes lost to get beside him from there and the
     *       cheapest cell beside him, since a walk that only moves away from the cost only puts it off;</li>
     *   <li>holding where it stands, clear of everything, costs a strike lost and the walk in later; it is a plan
     *       only while the cheapest cell beside him is under something that passes (a bomb about to go off, the
     *       fire), never under the shocker, which does not.</li>
     * </ul>
     *
     * The cheapest plan is taken. Ties go to the strike, then to the shorter walk, then to the lower cell, so
     * the answer is a function of the screen.
     */
    static RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, ActorView boss,
                                Map threats, Playbook.Tengu costs) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        int lost = costs.adjacentCost();
        boolean[] walk = Explore.walkable(observation, memory, false);
        int cells = walk.length;
        boolean beside = Tengu.adjacent(map, hero, boss.cell());
        Action attack = new Action.Attack(boss.cell());

        // The cheapest cell beside him, whatever the way there: what any walk will have to pay in the end.
        long floor = Long.MAX_VALUE;
        for (int cell : neighbours(map, boss.cell())) {
            if (walk[cell] || cell == hero) {
                floor = Math.min(floor, threats.cost(cell));
            }
        }
        if (floor == Long.MAX_VALUE) {
            return null;
        }

        long best = Long.MAX_VALUE;
        int bestSteps = 0;
        int bestCell = -1;
        RunLog.Choice choice = null;
        if (beside && offered.contains(attack)) {
            best = threats.cost(hero);
            choice = new RunLog.Choice(attack, Policies.CERTAIN, "tengu: attack");
        }

        // Cheapest ways to every cell within the horizon: what each cell entered costs of the fire and the shock, and the strikes lost.
        long[] paid = new long[cells];
        int[] steps = new int[cells];
        int[] first = new int[cells];
        Arrays.fill(paid, Long.MAX_VALUE);
        PriorityQueue<long[]> queue = new PriorityQueue<>((x, y) -> x[0] != y[0] ? Long.compare(x[0], y[0])
                : x[1] != y[1] ? Long.compare(x[1], y[1]) : Long.compare(x[2], y[2]));
        for (Action action : offered) {
            if (action instanceof Action.Step step && step.cell() < cells && walk[step.cell()]) {
                int cell = step.cell();
                long cost = lost + threats.fire[cell] + threats.shock[cell];
                if (cost < paid[cell]) {
                    paid[cell] = cost;
                    steps[cell] = 1;
                    first[cell] = cell;
                    queue.add(new long[] {cost, 1, cell});
                }
            }
        }
        boolean[] done = new boolean[cells];
        int nearest = beside ? 0 : -1;
        long bestBesideCost = Long.MAX_VALUE;
        int bestBesidePassing = 0;
        while (!queue.isEmpty()) {
            long[] top = queue.poll();
            int cell = (int) top[2];
            if (done[cell]) {
                continue;
            }
            done[cell] = true;
            boolean next = Tengu.adjacent(map, cell, boss.cell());
            long total = paid[cell] + threats.bomb[cell];
            if (!next) {
                total += (long) lost * (chebyshev(map, cell, boss.cell()) - 1) + floor;
            } else {
                if (nearest < 0 || steps[cell] < nearest) {
                    nearest = steps[cell];
                }
                if (total < bestBesideCost) {
                    bestBesideCost = total;
                    bestBesidePassing = threats.passing(cell);
                }
            }
            if (total < best || (total == best && choice != null && !choice.action().equals(attack)
                    && (steps[cell] < bestSteps || (steps[cell] == bestSteps && cell < bestCell)))) {
                best = total;
                bestSteps = steps[cell];
                bestCell = cell;
                choice = new RunLog.Choice(new Action.Step(first[cell]), Policies.CERTAIN,
                        (threats.cost(hero) > 0 ? "tengu: dodge " : "tengu: approach ") + steps[cell]);
            }
            if (steps[cell] < HORIZON) {
                for (int onward : neighbours(map, cell)) {
                    if (walk[onward] && !done[onward]) {
                        long cost = paid[cell] + lost + threats.fire[onward] + threats.shock[onward];
                        if (cost < paid[onward]) {
                            paid[onward] = cost;
                            steps[onward] = steps[cell] + 1;
                            first[onward] = first[cell];
                            queue.add(new long[] {cost, steps[onward], onward});
                        }
                    }
                }
            }
        }

        // Holding: clear where it stands, the cells beside him under what passes, and waiting cheaper than walking in.
        if (threats.cost(hero) == 0 && !beside && nearest > 0 && bestBesidePassing > 0
                && (long) lost * (1 + nearest) < bestBesideCost) {
            for (Action rest : List.of(new Action.Wait(), new Action.Search())) {
                if (offered.contains(rest)) {
                    return new RunLog.Choice(rest, Policies.CERTAIN, "tengu: hold");
                }
            }
        }
        return choice;
    }

    /** The greater of the two distances between the cells, in columns and in rows. */
    private static int chebyshev(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }
}
