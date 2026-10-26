package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor a red sentry guards (issue #219, step 6): which cells it covers, that it is remembered
 * past the sentry's sight and across the Belief's bytes, and that a hero already standing on it is
 * not walled in.
 *
 * <pre>
 *   y0:  W   W   W   [S] W   W   W
 *   y1:  SP  SP  SP  SP  SP  SP  SP       the special floor, the sentry's pedestal above it at cell 3
 *   y2:  E   E   E   E   E   E   E        plain floor, as the strip along the entrance wall
 *   y3:  W   W   W   W   W   SP  W        a second room's special floor at cell 26, not joined to the first
 * </pre>
 */
class SentryZoneTest {

    private static final int WIDTH = 7;

    private static final int SENTRY = 3;

    private static Observation room(int depth, int hero, boolean sentryInView) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(WIDTH * 4, Tile.WALL));
        tiles.set(SENTRY, Tile.PEDESTAL);
        for (int x = 0; x < WIDTH; x++) {
            tiles.set(WIDTH + x, Tile.EMPTY_SP);
            tiles.set(2 * WIDTH + x, Tile.EMPTY);
        }
        tiles.set(3 * WIDTH + 5, Tile.EMPTY_SP);
        Observation bare = Screens.grid(depth, WIDTH, tiles, List.of());
        List<ActorView> actors = sentryInView
                ? List.of(new ActorView(SENTRY, Sentry.RED, Alignment.NEUTRAL, 1, false, Emote.NONE, List.of()))
                : List.of();
        Observation placed = new Observation(bare.header(), bare.map(), new ActorsSection(actors),
                Screens.heroAt(hero, 10, 200, 200), bare.inventory(), bare.journal(), bare.log(), bare.actions(),
                bare.prompt());
        return placed.withActions(ValidActions.of(placed));
    }

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 3L);
    }

    @Test
    @DisplayName("the zone is the special floor reached from the sentry's cell over special floor, and no plain floor")
    void the_zone_is_the_special_floor_around_the_sentry() {
        Observation seen = room(2, 2 * WIDTH, true);
        Memory memory = Memory.of(brain().update(seen, null));
        boolean[] zone = Sentry.zone(seen, memory);
        assertNotNull(zone);
        for (int x = 0; x < WIDTH; x++) {
            assertTrue(zone[WIDTH + x], "special floor at x " + x);
            assertFalse(zone[2 * WIDTH + x], "plain floor at x " + x + " is safe from the sentry");
        }
        assertFalse(zone[3 * WIDTH + 5], "special floor not joined to the sentry's is another room's");
        assertFalse(zone[SENTRY], "the pedestal is the sentry's, not floor");
    }

    @Test
    @DisplayName("the zone is taken out of what the Policies may walk on, from outside it")
    void the_zone_is_not_walkable_from_outside() {
        Observation seen = room(2, 2 * WIDTH, true);
        Memory memory = Memory.of(brain().update(seen, null));
        boolean[] walk = Explore.walkable(seen, memory, true);
        for (int x = 0; x < WIDTH; x++) {
            assertFalse(walk[WIDTH + x]);
            assertTrue(walk[2 * WIDTH + x]);
        }
    }

    @Test
    @DisplayName("a hero already on the zone keeps the walk it had: nothing walls it in")
    void a_hero_inside_is_not_walled_in() {
        Observation inside = room(2, WIDTH + 1, true);
        Memory memory = Memory.of(brain().update(inside, null));
        boolean[] walk = Explore.walkable(inside, memory, true);
        assertTrue(walk[WIDTH + 2]);
        assertTrue(walk[2 * WIDTH + 1]);
    }

    @Test
    @DisplayName("the sentry is remembered once seen, on its floor only, and survives the Belief's bytes")
    void the_sentry_is_remembered() {
        Brain brain = brain();
        Belief seen = brain.update(room(2, 2 * WIDTH, true), null);
        Observation away = room(2, 2 * WIDTH, false);
        Belief later = brain.update(away, seen);
        Memory memory = Memory.of(later);
        assertEquals(List.of(new Memory.Spot(2, 0, SENTRY)), memory.sentries());
        assertNotNull(Sentry.zone(away, memory), "out of sight, the zone is still known");
        assertEquals(Memory.of(later), Memory.of(new Belief(later.version(), later.bytes())));

        Observation deeper = room(3, 2 * WIDTH, false);
        assertNull(Sentry.zone(deeper, memory), "another floor has no zone");
        assertArrayEquals(Explore.walkable(deeper, Memory.START, true), Explore.walkable(deeper, memory, true));
    }

    @Test
    @DisplayName("a name that is not the red sentry's is not remembered")
    void another_figure_is_not_a_sentry() {
        Observation bare = room(2, 2 * WIDTH, false);
        Observation other = new Observation(bare.header(), bare.map(), new ActorsSection(List.of(
                new ActorView(SENTRY, "scanning sentry", Alignment.NEUTRAL, 1, false, Emote.NONE, List.of()))),
                bare.hero(), bare.inventory(), bare.journal(), bare.log(), bare.actions(), bare.prompt());
        assertTrue(Memory.of(brain().update(other, null)).sentries().isEmpty());
    }
}
