package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Keep out of the floor a sentry guards (issue #219, step 6).
 *
 * <p>A sentry room paints its whole floor as special floor (SentryRoom.java:60), then clears a strip
 * along the entrance wall and a block at the treasure's end back to plain floor
 * (SentryRoom.java:78-84, :96-102, :114-120, :132-138). The red sentry fires only at a hero that stands on
 * special floor inside its room and in its sight (SentryRoom.java:253-256), after a charge that restarts
 * whenever the hero is not there (SentryRoom.java:258-267, :279), and then once a turn
 * (SentryRoom.java:269-272) for {@code NormalIntRange(2 + depth / 2, 4 + depth)} (SentryRoom.java:292). It
 * stands on a pedestal and never moves (SentryRoom.java:150, :229), takes no damage (SentryRoom.java:323),
 * and a statue line down the room's middle keeps the walk to the treasure on the special floor
 * (SentryRoom.java:87-93, :105-111, :123-129, :141-147), {@code dangerDist} long there and back, with a
 * charge of a third of it (SentryRoom.java:155). On the screen that is a figure named "red sentry"
 * (levels.properties:19) and a floor drawn as {@link Tile#EMPTY_SP} around it.
 *
 * <p>The Runs this was written for died on that floor at depths 2 to 4: the explore Policy's frontier and
 * search spots, and the chest Policy's walk, all crossed it while the sentry fired, and none turned back
 * with the health gone. A careful player does not cross a red sentry's floor for a chest at thirty hit
 * points: the walk costs about two thirds of {@code dangerDist} shots of 3 to 7 at depth 3. So once a
 * sentry has been seen on a floor, the special floor it guards is not walked by any Policy
 * ({@link Explore#walkable}). The zone is the special floor reached from the sentry's own cell over special
 * floor, eight ways as the hero walks; the plain floor beside it is not part of it and the sentry cannot
 * fire at a hero there.
 *
 * <p>The sentry is drawn neutral, so the fight Policy never sees it; its cell is kept in
 * {@link Memory#sentries} for the rest of the floor, since the hero leaves its sight and must not walk back
 * in on the strength of not seeing it. A hero that already stands in the zone is left to the Policies as
 * before: they plan from where it stands, and the zone is not taken from under it.
 */
final class Sentry {

    /** The sentry's name on the screen (levels.properties:19). */
    static final String RED = "red sentry";

    /** The most cells of special floor followed from one sentry: a room is at most a few hundred. */
    private static final int MAX_ZONE = 1024;

    private Sentry() {
    }

    /**
     * The sentries remembered on this floor after seeing {@code observation}: those already remembered,
     * and any red sentry in view now.
     */
    static List<Memory.Spot> remember(Memory memory, Observation observation) {
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<Memory.Spot> spots = memory.sentries();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.name().equals(RED)) {
                spots = Memory.with(spots, new Memory.Spot(depth, branch, actor.cell()));
            }
        }
        return spots;
    }

    /** The cells of the special floor a remembered sentry guards on this floor, or null when none is remembered. */
    static boolean[] zone(Observation observation, Memory memory) {
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        MapSection map = observation.map();
        boolean[] zone = null;
        for (Memory.Spot spot : memory.sentries()) {
            if (!spot.on(depth, branch) || spot.cell() >= map.tiles().size()) {
                continue;
            }
            if (zone == null) {
                zone = new boolean[map.tiles().size()];
            }
            follow(map, spot.cell(), zone);
        }
        return zone;
    }

    /** Marks the special floor reached from {@code origin} over special floor, eight ways as the hero walks. */
    private static void follow(MapSection map, int origin, boolean[] zone) {
        int width = map.width();
        int height = map.height();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[zone.length];
        queue.add(origin);
        queued[origin] = true;
        int marked = 0;
        while (!queue.isEmpty() && marked < MAX_ZONE) {
            int cell = queue.poll();
            int x = cell % width;
            int y = cell / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    int next = nx + ny * width;
                    if (queued[next] || map.fog().get(next) == Fog.UNKNOWN || map.tiles().get(next) != Tile.EMPTY_SP) {
                        continue;
                    }
                    queued[next] = true;
                    zone[next] = true;
                    marked++;
                    queue.add(next);
                }
            }
        }
    }

    /**
     * Takes the zone out of {@code walk}, when a sentry is remembered on this floor and the hero stands
     * outside the zone.
     */
    static void block(boolean[] walk, Observation observation, Memory memory) {
        if (memory.sentries().isEmpty()) {
            return;
        }
        boolean[] zone = zone(observation, memory);
        if (zone == null || zone[observation.hero().cell()]) {
            return;
        }
        for (int cell = 0; cell < zone.length; cell++) {
            if (zone[cell]) {
                walk[cell] = false;
            }
        }
    }
}
