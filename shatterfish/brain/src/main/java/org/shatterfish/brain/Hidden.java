package org.shatterfish.brain;

import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Tile;
import org.shatterfish.api.Tunables;

import java.util.List;

/**
 * Where the game can hide something worth a search (issue #409): the odds that a floor hides a secret
 * room, the walls and dead ends the screen shows that a hidden door could be in, and what a search
 * from a cell is expected to gain, in turns of play.
 *
 * <p><b>What can be hidden.</b> A secret room is the one find a search pays for: the Codex gives its
 * odds by depth ({@link #secretRooms}) and what it holds (the rule {@code secret-room-odds} of
 * {@code docs/brain-rules.md}). A regular door is hidden with {@code min(1, depth/20)}, and none on
 * depth 1, but on an ordinary floor the painter keeps a door open when hiding it would cut a room off
 * (RegularPainter.java:225-230, :259-261), so a hidden regular door only hides a second way into a
 * room that has another; finding it gains nothing. A hidden trap is the search ahead of a Step near a
 * trap already shown, which {@link Traps} makes. This model therefore prices only the secret rooms,
 * and leaves the no-exit search ({@link Descend#SEARCHES}) and the entrance doors the painter hides
 * on depth 2 (RegularPainter.java:266-273) to the rule that a floor with no exit seen is searched.
 *
 * <p><b>Where.</b> A secret room is at least five cells across with its walls (SpecialRoom.java:37-44),
 * three of them floor, and its door is in a wall of a room already seen. So a wall is a
 * <em>candidate</em> only if the floor beside it is known and, straight through the wall, there is a
 * block of cells the screen has never shown big enough to hold the room's floor. A dead end, a floor
 * cell with one way in and walls all round, is where a corridor to a hidden door ends, and weighs
 * {@link Tunables#EXPLORE_DEAD_END_WEIGHT} candidate walls.
 *
 * <p><b>How much.</b> A search finds every secret in reach, whatever the distance
 * (Hero.java:2570-2602), so the odds of a hidden door at a spot are the floor's odds times the spot's
 * share of the weight of all candidates. The gain is that times the worth of a secret room
 * ({@link Tunables#EXPLORE_SECRET_VALUE}); the cost is the search's {@link Larder#SEARCH_COST} and the
 * Steps to the spot, each priced by {@link Larder}.
 */
final class Hidden {

    private Hidden() {
    }

    /** Per mille a secret room is expected on a floor of {@code depth}, from {@code SecretRoom.secretsForFloor}. */
    static int secretRooms(int depth, Feeling feeling) {
        int rooms;
        if (depth <= 1) {
            rooms = 0;
        } else if (depth < 5) {
            // Two secrets over depths 2-4 (SecretRoom.java:47, :66-88).
            rooms = 667;
        } else {
            // The region's base over its five floors: 2.25, 2.5, 2.75, 3.0 for depths 5-24.
            rooms = (int) (200 * (2f + 0.25f * Math.min(depth / 5, 4)));
        }
        // A SECRETS floor adds one more (RegularLevel.java:158-160).
        return rooms + (feeling == Feeling.SECRETS && depth > 1 ? 1000 : 0);
    }

    /** The walls a hidden door could be in and the dead ends, as the screen shows them. */
    record Evidence(boolean[] wall, boolean[] deadEnd, int total) {
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The candidate walls and dead ends of {@code map}, with {@code interior} cells of floor needed behind a wall. */
    static Evidence evidence(MapSection map, int interior, int deadEndWeight) {
        int width = map.width();
        int height = map.height();
        boolean[] wall = new boolean[width * height];
        boolean[] dead = new boolean[width * height];
        int total = 0;
        for (int cell = 0; cell < wall.length; cell++) {
            int x = cell % width;
            int y = cell / width;
            if (map.fog().get(cell) == Fog.UNKNOWN) {
                continue;
            }
            Tile tile = map.tiles().get(cell);
            if (tile == Tile.WALL || tile == Tile.WALL_DECO) {
                for (int[] d : DIRECTIONS) {
                    int ix = x - d[0];
                    int iy = y - d[1];
                    if (ix < 0 || iy < 0 || ix >= width || iy >= height) {
                        continue;
                    }
                    int in = ix + iy * width;
                    if (map.fog().get(in) == Fog.UNKNOWN || !Explore.WALK.contains(map.tiles().get(in))) {
                        continue;
                    }
                    if (roomBehind(map, x + d[0], y + d[1], d[0], d[1], interior)) {
                        wall[cell] = true;
                        total++;
                        break;
                    }
                }
            } else if (deadEnd(map, cell)) {
                dead[cell] = true;
                total += deadEndWeight;
            }
        }
        return new Evidence(wall, dead, total);
    }

    /**
     * Whether a block of {@code interior} by {@code interior} cells the screen has never shown starts at
     * ({@code x}, {@code y}) and runs on in direction ({@code dx}, {@code dy}), with the door in any of the
     * columns of the room's width.
     */
    private static boolean roomBehind(MapSection map, int x, int y, int dx, int dy, int interior) {
        int width = map.width();
        int height = map.height();
        for (int shift = 0; shift < interior; shift++) {
            boolean all = true;
            for (int depth = 0; depth < interior && all; depth++) {
                for (int side = 0; side < interior && all; side++) {
                    int lateral = side - shift;
                    int cx = x + dx * depth + (dx == 0 ? lateral : 0);
                    int cy = y + dy * depth + (dy == 0 ? lateral : 0);
                    all = cx >= 1 && cy >= 1 && cx < width - 1 && cy < height - 1
                            && map.fog().get(cx + cy * width) == Fog.UNKNOWN;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** Plain floor, every neighbour shown, and exactly one of them not a wall: the end of a corridor. */
    static boolean deadEnd(MapSection map, int cell) {
        Tile tile = map.tiles().get(cell);
        if (map.fog().get(cell) == Fog.UNKNOWN
                || !(tile == Tile.EMPTY || tile == Tile.EMPTY_DECO || tile == Tile.GRASS || tile == Tile.EMBERS)) {
            return false;
        }
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        int open = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= width || ny >= map.height()) {
                    // The edge of the map is no wall the screen has shown.
                    return false;
                }
                int next = nx + ny * width;
                if (map.fog().get(next) == Fog.UNKNOWN) {
                    return false;
                }
                Tile other = map.tiles().get(next);
                if (other != Tile.WALL && other != Tile.WALL_DECO) {
                    open++;
                }
            }
        }
        return open == 1;
    }

    /**
     * The weight a search from {@code cell} reaches that no search has covered: the candidate walls within
     * {@code radius} and, when it stands on the end of an uncovered dead end, the dead end's weight.
     */
    static int reach(MapSection map, Evidence evidence, int cell, int radius, List<Memory.Spot> searched, int deadEndWeight) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        int weight = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int wx = x + dx;
                int wy = y + dy;
                if ((dx == 0 && dy == 0) || wx < 0 || wy < 0 || wx >= width || wy >= map.height()) {
                    continue;
                }
                int other = wx + wy * width;
                if (evidence.wall()[other] && !Explore.covered(width, other, radius, searched)) {
                    weight++;
                }
            }
        }
        for (int dy = -(radius - 1); dy <= radius - 1; dy++) {
            for (int dx = -(radius - 1); dx <= radius - 1; dx++) {
                int wx = x + dx;
                int wy = y + dy;
                if (wx < 0 || wy < 0 || wx >= width || wy >= map.height()) {
                    continue;
                }
                int other = wx + wy * width;
                if (evidence.deadEnd()[other] && !Explore.covered(width, other, radius - 1, searched)) {
                    weight += deadEndWeight;
                }
            }
        }
        return weight;
    }

    /**
     * The expected gain of searching from a spot of weight {@code reach}, in turns: the floor's odds of a
     * secret room (per mille) times the spot's share of the candidates times the room's worth.
     */
    static long gain(int roomsPerMille, int reach, int total, int worth) {
        if (total <= 0 || reach <= 0) {
            return 0;
        }
        return (long) roomsPerMille * worth * Math.min(reach, total) / (1000L * total);
    }
}
