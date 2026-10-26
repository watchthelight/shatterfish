package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.Reflection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.shatterfish.api.Fog;
import org.shatterfish.harness.observer.FogModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The rows about the map: the cells the screen does not show, and the secrets on the cells it does.
 *
 * <p>A cell is unknown when the Observation paints it so ({@link Fog#UNKNOWN}); the scrubbed copy makes each such
 * cell solid, because the player cannot say what it is and a solid cell cannot hide a mob, an item, a trap, a gas or
 * a way out. Which solid is the question {@link CellSolver} answers. A secret door the screen draws as a wall becomes
 * a plain wall, and a secret trap the screen draws as floor becomes plain floor, unless the cells around a door need
 * it to be a way through for the fog to paint as it did, in which case the screen itself says there is one.
 */
final class CellRows {

    private CellRows() {
    }

    static void apply(Ctx c) {
        boolean unknownOn = c.on(ScrubRow.UNKNOWN_CELLS);
        boolean secretOn = c.on(ScrubRow.SECRET_TRAPS_AND_DOORS);
        if (!unknownOn && !secretOn) {
            return;
        }
        int[] original = Ctx.ints(c.level.getJSONArray("map"));
        boolean[] visited = Ctx.booleans(c.level.getJSONArray("visited"));
        boolean[] mapped = Ctx.booleans(c.level.getJSONArray("mapped"));
        verifyFogModel(c, original, visited, mapped);

        int[] terrain = original.clone();
        List<Integer> vars = new ArrayList<>();
        List<int[]> domains = new ArrayList<>();
        int secrets = 0;
        int unknown = 0;
        if (secretOn) {
            for (JSONObject trap : Ctx.objects(c.level.optJSONArray("traps"))) {
                int pos = trap.optInt("pos", -1);
                if (pos >= 0 && pos < c.length && c.known(pos) && !trap.optBoolean("visible")) {
                    secrets++;
                }
            }
        }
        for (int cell = 0; cell < c.length; cell++) {
            if (c.known(cell)) {
                if (secretOn && original[cell] == Terrain.SECRET_DOOR) {
                    secrets++;
                    vars.add(cell);
                    domains.add(new int[] {Terrain.WALL, Terrain.SECRET_DOOR});
                } else if (secretOn && original[cell] == Terrain.SECRET_TRAP) {
                    terrain[cell] = Terrain.EMPTY;
                }
            } else if (unknownOn) {
                unknown++;
                terrain[cell] = Terrain.WALL;
                visited[cell] = false;
                mapped[cell] = false;
                if (c.fov[cell]) {
                    // The hero sees this cell and the fog paints it opaque: it is a solid that is stitched to
                    // its neighbours, since an open cell in view would be painted.
                    vars.add(cell);
                    domains.add(new int[] {Terrain.WALL, Terrain.SECRET_DOOR});
                } else {
                    vars.add(cell);
                    domains.add(new int[] {Terrain.WALL, Terrain.SECRET_DOOR, Terrain.BARRICADE});
                }
            }
        }
        int[] varCell = vars.stream().mapToInt(Integer::intValue).toArray();
        if (varCell.length > 0) {
            Fog[] expected = c.fog;
            CellSolver solver = new CellSolver(c.width, terrain, c.fov, visited, mapped, expected, varCell,
                    domains.toArray(new int[0][]));
            try {
                solver.solve();
            } catch (CellSolver.Unsolvable e) {
                throw new Scrub.Refused(e.getMessage());
            }
        }

        int changedUnknown = 0;
        int changedSecret = 0;
        for (int cell = 0; cell < c.length; cell++) {
            if (terrain[cell] != original[cell]) {
                if (c.known(cell)) {
                    changedSecret++;
                } else {
                    changedUnknown++;
                }
            }
        }
        c.level.put("map", Ctx.array(terrain));
        c.level.put("visited", Ctx.array(visited));
        c.level.put("mapped", Ctx.array(mapped));
        if (secretOn) {
            JSONArray traps = new JSONArray();
            int removedTraps = 0;
            for (JSONObject trap : Ctx.objects(c.level.optJSONArray("traps"))) {
                int pos = trap.optInt("pos", -1);
                boolean hiddenOnKnown = pos >= 0 && pos < c.length && c.known(pos) && !trap.optBoolean("visible");
                if (hiddenOnKnown) {
                    removedTraps++;
                } else {
                    traps.put(trap);
                }
            }
            c.level.put("traps", traps);
            c.held(ScrubRow.SECRET_TRAPS_AND_DOORS, secrets);
            c.changed(ScrubRow.SECRET_TRAPS_AND_DOORS, changedSecret + removedTraps);
        }
        if (unknownOn) {
            removeFromUnknown(c);
            c.held(ScrubRow.UNKNOWN_CELLS, unknown);
            c.changed(ScrubRow.UNKNOWN_CELLS, changedUnknown);
        }
    }

    /**
     * The fog model must reproduce the fog the Observer painted from the live arrays, or the search would be solving
     * for a different screen; if it does not, the scrub refuses rather than guess.
     */
    private static void verifyFogModel(Ctx c, int[] map, boolean[] visited, boolean[] mapped) {
        boolean[] discoverable = FogModel.discoverable(c.width, map);
        for (int cell = 0; cell < c.length; cell++) {
            Fog fog = FogModel.fog(c.width, c.length, map, discoverable, c.fov, visited, mapped, cell);
            if (fog != c.fog[cell]) {
                throw new Scrub.Refused("the fog model paints cell " + cell + " " + fog + " where the Observation shows "
                        + c.fog[cell] + ": the snapshot's view of the map cannot be rebuilt");
            }
        }
    }

    /** Removes what stood on a cell the copy made solid: heaps, traps, plants, gas, ways out, rooms, decorations. */
    private static void removeFromUnknown(Ctx c) {
        c.level.put("heaps", filterByPos(c, c.level.optJSONArray("heaps"), "pos"));
        c.level.put("traps", filterByPos(c, c.level.optJSONArray("traps"), "pos"));
        c.level.put("plants", filterByPos(c, c.level.optJSONArray("plants"), "pos"));
        c.level.put("transitions", filterByPos(c, c.level.optJSONArray("transitions"), "center"));
        c.level.put("blobs", blobs(c, c.level.optJSONArray("blobs")));
        JSONArray rooms = new JSONArray();
        for (JSONObject room : Ctx.objects(c.level.optJSONArray("rooms"))) {
            if (wholly(c, room.optInt("left"), room.optInt("top"), room.optInt("right"), room.optInt("bottom"))) {
                rooms.put(room);
            }
        }
        if (c.level.has("rooms")) {
            c.level.put("rooms", rooms);
        }
        for (String key : List.of("customTiles", "customWalls", "customTerrain")) {
            if (!c.level.has(key)) {
                continue;
            }
            JSONArray kept = new JSONArray();
            for (JSONObject tile : Ctx.objects(c.level.optJSONArray(key))) {
                int x = tile.optInt("tileX");
                int y = tile.optInt("tileY");
                if (!tile.has("tileX") || wholly(c, x, y, x + tile.optInt("tileW") - 1, y + tile.optInt("tileH") - 1)) {
                    kept.put(tile);
                }
            }
            c.level.put(key, kept);
        }
        JSONArray targeted = c.level.optJSONArray("targeted_cells");
        if (targeted != null) {
            JSONArray kept = new JSONArray();
            for (int i = 0; i < targeted.length(); i++) {
                int cell = targeted.optInt(i, -1);
                if (cell >= 0 && cell < c.length && c.known(cell)) {
                    kept.put(cell);
                }
            }
            c.level.put("targeted_cells", kept);
        }
    }

    private static boolean wholly(Ctx c, int left, int top, int right, int bottom) {
        int height = c.length / c.width;
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(c.width - 1, right); x++) {
                if (!c.known(y * c.width + x)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static JSONArray filterByPos(Ctx c, JSONArray array, String key) {
        JSONArray kept = new JSONArray();
        for (JSONObject object : Ctx.objects(array)) {
            int pos = object.optInt(key, -1);
            if (pos >= 0 && pos < c.length && c.known(pos)) {
                kept.put(object);
            }
        }
        return kept;
    }

    /**
     * Gas the screen does not draw is hidden: a blob keeps its volume only on cells in view, and an always-visible
     * blob (a well, the alchemy pot) keeps it on every known cell, as the emitter and the cell's description show it
     * there. The volume of a blob that has none left is dropped with its window, as the game writes a blob with none.
     */
    private static JSONArray blobs(Ctx c, JSONArray array) {
        JSONArray out = new JSONArray();
        for (JSONObject blob : Ctx.objects(array)) {
            if (!blob.has("cur")) {
                out.put(blob);
                continue;
            }
            Class<?> cls = ItemRows.classOf(Ctx.className(blob));
            boolean always = false;
            if (cls != null && Blob.class.isAssignableFrom(cls)) {
                Object made = Reflection.newInstance(cls);
                always = made instanceof Blob b && b.alwaysVisible;
            }
            int total = blob.optInt("length");
            int[] cur = new int[total];
            int start = blob.optInt("start");
            int[] data = Ctx.ints(blob.getJSONArray("cur"));
            for (int i = 0; i < data.length && start + i < total; i++) {
                cur[start + i] = data[i];
            }
            boolean any = false;
            int first = -1;
            int last = -1;
            for (int cell = 0; cell < total; cell++) {
                boolean keep = cell < c.length && (always ? c.known(cell) : c.fog[cell] == Fog.VISIBLE);
                if (!keep) {
                    cur[cell] = 0;
                }
                if (cur[cell] > 0) {
                    any = true;
                    if (first < 0) {
                        first = cell;
                    }
                    last = cell;
                }
            }
            JSONObject rebuilt = Ctx.copy(blob);
            if (any) {
                rebuilt.put("start", first);
                rebuilt.put("cur", Ctx.array(Arrays.copyOfRange(cur, first, last + 1)));
            } else {
                rebuilt.remove("start");
                rebuilt.remove("length");
                rebuilt.remove("cur");
            }
            out.put(rebuilt);
        }
        return out;
    }
}
