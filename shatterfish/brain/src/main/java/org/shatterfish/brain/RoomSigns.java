package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;

import java.util.List;

/**
 * What the screen draws of the five special rooms that queue a fixed potion on their floor, besides the
 * pool room ({@link Beliefs#implied} reads that one): the toxic gas room, the magical fire room, the
 * sentry room, the storage room and the traps room (issue #219; docs/rules/generation.md, the
 * potion-room-inference rows).
 *
 * <p>Each sign is something a player sees: a revealed trap with its name, a blob drawn in view, an actor in
 * view, a tile. Nothing here reads a hidden trap, an item's identity or the layout beyond the map section.
 * A sign can be seen without its room, so the Brain weighs the evidence by {@link Beliefs#ROOM_MISS} and a
 * sign that is too common to mean the room (one revealed trap) is not accepted.
 */
final class RoomSigns {

    /** The toxic gas room's vents, revealed and inactive (ToxicGasRoom.java:71-73), by the name the screen shows. */
    static final String TOXIC_VENT = "toxic vent";

    /** The magical fire room's wall of flames, by the blob class's simple name (MagicalFireRoom.java:68-92). */
    static final String ETERNAL_FIRE = "EternalFire";

    /** The fewest vents that are the toxic gas room's: it places min(width - 2, height - 2) of them, at least five. */
    static final int VENTS = 2;

    /** The fewest flame cells that are the magical fire room's wall: it spans the room's inside. */
    static final int FLAMES = 3;

    /**
     * The fewest revealed traps of one kind within {@link #TRAP_SPREAD} cells of each other that are the traps room's
     * field (TrapsRoom.java:104-109 reveals every one): a revealed trap or two is a search's find.
     */
    static final int FIELD = 6;
    static final int TRAP_SPREAD = 4;

    private RoomSigns() {
    }

    static boolean toxicGas(MapSection map) {
        return map.traps().stream().filter(trap -> trap.kind().equals(TOXIC_VENT)).count() >= VENTS;
    }

    static boolean magicalFire(MapSection map) {
        return map.blobs().stream().filter(blob -> blob.kinds().contains(ETERNAL_FIRE)).map(BlobCell::cell).distinct().count() >= FLAMES;
    }

    static boolean sentry(Observation observation) {
        for (ActorView actor : observation.actors().actors()) {
            if (actor.name().equals(Sentry.RED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A barricade set in a wall (walls on both sides across it) with the special room's floor beside it
     * (StorageRoom.java:35-58): the room's door is a barricade and its inside is the special floor.
     */
    static boolean storage(MapSection map) {
        int width = map.width();
        List<Tile> tiles = map.tiles();
        for (int cell = 0; cell < tiles.size(); cell++) {
            if (tiles.get(cell) != Tile.BARRICADE) {
                continue;
            }
            int x = cell % width;
            int y = cell / width;
            boolean acrossX = wall(map, x - 1, y) && wall(map, x + 1, y);
            boolean acrossY = wall(map, x, y - 1) && wall(map, x, y + 1);
            if (!acrossX && !acrossY) {
                continue;
            }
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < width && ny < map.height() && tiles.get(nx + ny * width) == Tile.EMPTY_SP) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean traps(MapSection map) {
        List<TrapView> traps = map.traps();
        int width = map.width();
        for (TrapView trap : traps) {
            if (trap.kind().equals(TOXIC_VENT)) {
                continue;
            }
            int near = 0;
            for (TrapView other : traps) {
                if (other.kind().equals(trap.kind())
                        && Math.max(Math.abs(other.cell() % width - trap.cell() % width),
                        Math.abs(other.cell() / width - trap.cell() / width)) <= TRAP_SPREAD) {
                    near++;
                }
            }
            if (near >= FIELD) {
                return true;
            }
        }
        return false;
    }

    private static boolean wall(MapSection map, int x, int y) {
        if (x < 0 || y < 0 || x >= map.width() || y >= map.height()) {
            return false;
        }
        Tile tile = map.tiles().get(x + y * map.width());
        return tile == Tile.WALL || tile == Tile.WALL_DECO;
    }
}
