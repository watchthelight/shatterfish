package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import org.shatterfish.api.Fog;

/**
 * The fog level the fog of war paints on a cell, as a pure function of the level's arrays
 * ({@code …/tiles/FogOfWar.java:200-267}), so that the {@link Observer} and the redetermination scrubber (story 6.2)
 * decide it with one body and never two. The Observer hands it the live level's arrays; the scrubber hands it the
 * arrays of a candidate map to ask whether a scrubbed copy would paint the cell as the live one does.
 *
 * <p>A cell that cannot be discovered, or that is neither in view, visited nor mapped, is opaque
 * ({@code :200-205}). A cell that is not a wall is painted its own level: in view, visited, mapped
 * ({@code :288-299}). A wall cell is painted by the cells its face belongs to ({@code :210-267}): opaque on the
 * bottom row; a wall with a wall below it is painted in two halves, each the darkest of the cell, its side
 * neighbour and, when that neighbour is a wall, the cell below that neighbour, and opaque when the neighbour and
 * the cell below it are both walls or the cell is at the map's edge; a wall with a floor below it is the darkest of
 * the cell and the cell below. The cell's level is the lighter of its two halves, the part the player sees. So a
 * room's far wall is opaque until the corridor beyond is seen, even while in view.
 *
 * <p>Such a wall is still visited or mapped, so the examine window opens on it and draws its tile
 * ({@code …/scenes/GameScene.java:1736-1742}; {@code …/windows/WndInfoCell.java:60-112}); it is emitted as visited or
 * mapped rather than unknown, which is what the player can learn of it. The fog's own gate stays in front of that
 * step: a cell that cannot be discovered is never visited or mapped in play and reads unknown whatever the arrays
 * say. {@code FogParityTest} holds every cell to the painted texture.
 */
public final class FogModel {

    private FogModel() {
    }

    /** The fog level of {@code cell}, raised to what the examine window gives for a wall the fog paints opaque. */
    public static Fog fog(int width, int length, int[] map, boolean[] discoverable, boolean[] fov, boolean[] visited,
                          boolean[] mapped, int cell) {
        Fog painted = painted(width, length, map, discoverable, fov, visited, mapped, cell);
        if (painted == Fog.UNKNOWN && discoverable[cell] && wall(map[cell]) && (visited[cell] || mapped[cell])) {
            return visited[cell] ? Fog.VISITED : Fog.MAPPED;
        }
        return painted;
    }

    /**
     * The cells {@code Level.cleanWalls} marks discoverable ({@code …/levels/Level.java:955-974}): those with a
     * neighbour among the nine that is not a plain wall. The offsets are the width's and the loop checks only the
     * array's ends, as upstream does, so a cell on an edge reads the row beside it.
     */
    public static boolean[] discoverable(int width, int[] map) {
        boolean[] discoverable = new boolean[map.length];
        for (int i = 0; i < map.length; i++) {
            discoverable[i] = discoverableAt(width, map, i);
        }
        return discoverable;
    }

    /** {@link #discoverable} for one cell. */
    public static boolean discoverableAt(int width, int[] map, int cell) {
        int length = map.length;
        int[] offsets = {-width - 1, -width, -width + 1, -1, 0, 1, width - 1, width, width + 1};
        for (int offset : offsets) {
            int n = cell + offset;
            if (n >= 0 && n < length && map[n] != Terrain.WALL && map[n] != Terrain.WALL_DECO) {
                return true;
            }
        }
        return false;
    }

    /** The level the fog of war paints, as {@code FogOfWar.updateTexture} decides it ({@code FogOfWar.java:200-267}). */
    private static Fog painted(int width, int length, int[] map, boolean[] discoverable, boolean[] fov, boolean[] visited,
                               boolean[] mapped, int cell) {
        if (!discoverable[cell] || (!fov[cell] && !visited[cell] && !mapped[cell])) {
            return Fog.UNKNOWN;
        }
        if (!wall(map[cell])) {
            return own(fov, visited, mapped, cell);
        }
        if (cell + width >= length) {
            return Fog.UNKNOWN;
        }
        if (!wall(map[cell + width])) {
            return darker(own(fov, visited, mapped, cell), own(fov, visited, mapped, cell + width));
        }
        Fog left = Fog.UNKNOWN;
        if (cell % width != 0) {
            if (wall(map[cell - 1])) {
                left = wall(map[cell + width - 1]) ? Fog.UNKNOWN
                        : darker(own(fov, visited, mapped, cell),
                                darker(own(fov, visited, mapped, cell + width - 1), own(fov, visited, mapped, cell - 1)));
            } else {
                left = darker(own(fov, visited, mapped, cell), own(fov, visited, mapped, cell - 1));
            }
        }
        Fog right = Fog.UNKNOWN;
        if ((cell + 1) % width != 0) {
            if (wall(map[cell + 1])) {
                right = wall(map[cell + width + 1]) ? Fog.UNKNOWN
                        : darker(own(fov, visited, mapped, cell),
                                darker(own(fov, visited, mapped, cell + width + 1), own(fov, visited, mapped, cell + 1)));
            } else {
                right = darker(own(fov, visited, mapped, cell), own(fov, visited, mapped, cell + 1));
            }
        }
        return lighter(left, right);
    }

    /** A cell's own level: in view, visited, mapped, or nothing ({@code FogOfWar.java:288-299}). */
    private static Fog own(boolean[] fov, boolean[] visited, boolean[] mapped, int cell) {
        if (fov[cell]) {
            return Fog.VISIBLE;
        } else if (visited[cell]) {
            return Fog.VISITED;
        } else if (mapped[cell]) {
            return Fog.MAPPED;
        } else {
            return Fog.UNKNOWN;
        }
    }

    /** Whether the fog treats the terrain as a wall: the sheet's stitching set ({@code FogOfWar.java:284-286}). */
    public static boolean wall(int terrain) {
        return DungeonTileSheet.wallStitcheable(terrain);
    }

    private static Fog darker(Fog a, Fog b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static Fog lighter(Fog a, Fog b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
