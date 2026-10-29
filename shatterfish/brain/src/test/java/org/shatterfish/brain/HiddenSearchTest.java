package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tunables;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Search only where the game can hide something (issue #409): the expected-value model of {@link Hidden}
 * and how {@link Explore} uses it once the way down is seen. The screens are drawn as in
 * {@link ExplorePolicyTest}.
 */
class HiddenSearchTest {

    private static String[] padded(int margin, String... rows) {
        int width = rows[0].length() + 2 * margin;
        String blank = " ".repeat(width);
        String side = " ".repeat(margin);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < margin; i++) {
            out.add(blank);
        }
        for (String row : rows) {
            out.add(side + row + side);
        }
        for (int i = 0; i < margin; i++) {
            out.add(blank);
        }
        return out.toArray(new String[0]);
    }

    /** A hall with its exit seen and unseen rock all round it: every outer wall faces space. */
    private static final String[] HALL = padded(5,
            "##########",
            "#@......>#",
            "#........#",
            "##########");

    /** A corridor that ends in a dead end, off a hall with the exit. */
    private static final String[] DEAD_END = {
            "###########",
            "#@.......>#",
            "#.........#",
            "#####.#####",
            "    #.#    ",
            "    #.#    ",
            "    ###    "};

    private static Memory searched(List<Memory.Spot> dwelt) {
        return new Memory(1, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1, List.of(), List.of());
    }

    private static Observation feeling(Observation screen, Feeling feeling) {
        MapSection map = screen.map();
        MapSection felt = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), map.heaps(),
                map.blobs(), feeling, map.transitions());
        return new Observation(screen.header(), felt, screen.actors(), screen.hero(), screen.inventory(),
                screen.journal(), screen.log(), screen.actions(), screen.prompt());
    }

    private static Tunables.Vector with(String name, long value) {
        return Tunables.Vector.REFERENCE.with(Map.of(name, value));
    }

    @Test
    @DisplayName("a spent depth-1 floor is not searched: the painter hides no door there and there is no secret room")
    void depth_one_is_not_searched() {
        Observation hall = ExplorePolicyTest.screen(1, HALL);
        // The control: the plain rule of the descend Policy, which was every floor's rule, searches this hall.
        RunLog.Choice plain = Explore.searchOn(hall, Memory.START, hall.actions().actions(), Explore.SEARCHES);
        assertNotNull(plain, "the walls of this hall face unseen space, so the old rule searched it");
        assertTrue(Explore.spent(hall, Memory.START), "no search is worth making on depth 1");
        assertNull(Explore.uncover(hall, Memory.START, hall.actions().actions(), Explore.SEARCHES, false));
        assertEquals(0, Hidden.secretRooms(1, Feeling.NONE));
        assertEquals(0, Hidden.secretRooms(1, Feeling.SECRETS), "a SECRETS floor is never depth 1 (Level.java:259)");
    }

    @Test
    @DisplayName("a depth-6 dead end is searched: the hero walks to it and searches there")
    void depth_six_dead_end_is_searched() {
        Observation far = ExplorePolicyTest.screen(6, DEAD_END);
        RunLog.Choice walk = Explore.uncover(far, Memory.START, far.actions().actions(), Explore.SEARCHES, false);
        assertNotNull(walk, "the corridor ends in a wall, where a hidden door may be");
        assertTrue(walk.action() instanceof Action.Step, walk.action().toString());
        assertTrue(walk.why().startsWith("search-spot "), walk.why());

        // The same corridor on depth 1 is nothing to search.
        Observation first = ExplorePolicyTest.screen(1, DEAD_END);
        assertNull(Explore.uncover(first, Memory.START, first.actions().actions(), Explore.SEARCHES, false));

        String[] there = DEAD_END.clone();
        there[1] = "#........>#";
        there[5] = "    #@#    ";
        Observation end = ExplorePolicyTest.screen(6, there);
        RunLog.Choice search = Explore.uncover(end, Memory.START, end.actions().actions(), Explore.SEARCHES, false);
        assertEquals(new Action.Search(), search.action());
        assertEquals("search 1/" + Explore.SEARCHES, search.why());

        // Once searched from the end, the dead end is covered and nothing is left to search.
        Memory done = searched(List.of(new Memory.Spot(6, 0, end.hero().cell())));
        assertTrue(Explore.spent(end, done), "a covered dead end is not searched again");
    }

    @Test
    @DisplayName("a closed room with nothing unseen behind its walls is not searched on any depth")
    void no_evidence_no_search() {
        Observation closed = ExplorePolicyTest.screen(8,
                "#######",
                "#@...>#",
                "#.....#",
                "#######");
        assertTrue(Explore.spent(closed, Memory.START), "no wall faces space big enough for a room");
    }

    @Test
    @DisplayName("a wall that faces a gap too small for a room is not a place; one that faces a room's worth of space is")
    void space_big_enough() {
        Observation small = ExplorePolicyTest.screen(4,
                "#########",
                "#@.....>#",
                "#.......#",
                "#########",
                "## ######",
                "#########");
        assertTrue(Explore.spent(small, Memory.START), "one unseen cell holds no room");
        Observation big = ExplorePolicyTest.screen(4, HALL);
        assertFalse(Explore.spent(big, Memory.START), "the hall's walls face rock a room could be in");
        assertNotNull(Explore.uncover(big, Memory.START, big.actions().actions(), Explore.SEARCHES, false));
    }

    @Test
    @DisplayName("the odds of a secret room follow SecretRoom.secretsForFloor, and a SECRETS floor adds one")
    void odds_by_depth() {
        assertEquals(0, Hidden.secretRooms(1, Feeling.NONE));
        assertEquals(667, Hidden.secretRooms(2, Feeling.NONE));
        assertEquals(667, Hidden.secretRooms(4, Feeling.NONE));
        assertEquals(450, Hidden.secretRooms(6, Feeling.NONE));
        assertEquals(500, Hidden.secretRooms(10, Feeling.NONE));
        assertEquals(600, Hidden.secretRooms(30, Feeling.NONE));
        assertEquals(1667, Hidden.secretRooms(2, Feeling.SECRETS));
    }

    @Test
    @DisplayName("the worth of a secret room, and what food is worth, move the verdict")
    void price() {
        Observation hall = ExplorePolicyTest.screen(4, HALL);
        List<Action> offered = hall.actions().actions();
        assertNotNull(Explore.uncover(hall, Memory.START, offered, Explore.SEARCHES, false));
        assertNull(Explore.uncover(hall, Memory.START, offered, Explore.SEARCHES, false, with("explore.secret_value", 0)),
                "a secret room worth nothing is not searched for");
        assertNull(Explore.uncover(hall, Memory.START, offered, 0, false), "the floor's search budget still caps it");
        // Food tight: a search costs three times as much, so the likeliest spot of a long hall no longer pays.
        assertNotNull(Explore.uncover(hall, Memory.START, offered, Explore.SEARCHES, false,
                with("explore.secret_value", 100)), "cheap enough with food to spare");
        assertNull(Explore.uncover(hall, Memory.START, offered, Explore.FRUGAL_SEARCHES, true,
                with("explore.secret_value", 100)), "too dear with food tight");
    }

    @Test
    @DisplayName("a SECRETS floor is searched where an ordinary one of the same depth is not worth it")
    void secrets_floor() {
        Observation hall = ExplorePolicyTest.screen(2, HALL);
        Tunables.Vector cheap = with("explore.secret_value", 40);
        List<Action> offered = hall.actions().actions();
        assertNull(Explore.uncover(hall, Memory.START, offered, Explore.SEARCHES, false, cheap));
        Observation secrets = feeling(hall, Feeling.SECRETS);
        assertNotNull(Explore.uncover(secrets, Memory.START, secrets.actions().actions(), Explore.SEARCHES, false, cheap));
    }

    @Test
    @DisplayName("with no way down seen the floor may be sealed, and any uncovered wall is searched as before")
    void no_exit_rule() {
        Observation sealed = ExplorePolicyTest.screen(1,
                "       ",
                " ##### ",
                " #@..# ",
                " ##### ",
                "       ");
        assertNotNull(Explore.uncover(sealed, Memory.START, sealed.actions().actions(), Explore.SEARCHES, false),
                "the entrance room of depth 2 can be sealed (RegularPainter.java:266-273); depth 1 is only tested here");
        assertNotNull(Explore.searchOn(sealed, Memory.START, sealed.actions().actions(), Descend.SEARCHES));
        assertEquals(36, Descend.SEARCHES);
    }

    @Test
    @DisplayName("a search made for a shown trap covers the walls here, and the search ahead of a Step is not made twice")
    void trap_search_is_not_repeated() {
        Observation near = ExplorePolicyTest.screen(4,
                "#########",
                "#@.^...>#",
                "#########");
        int stand = near.hero().cell() + 1;
        int step = stand + 1;
        Memory none = Memory.START;
        assertTrue(Traps.unsearched(near, none, step), "no search covers the cell beside the trap yet");
        Memory made = searched(List.of(new Memory.Spot(4, 0, stand)));
        assertFalse(Traps.unsearched(near, made, step), "a search from the next cell covers it");
        assertTrue(Traps.covered(near, made, step));
        // The model counts the search made ahead of a trap as covering walls, and not as one of the spots.
        Observation hall = ExplorePolicyTest.screen(4, HALL);
        int beside = hall.hero().cell() + 3;
        Memory ahead = searched(List.of(new Memory.Spot(4, 0, beside)));
        boolean[] walk = Explore.walkable(hall, ahead);
        assertTrue(Explore.covered(hall.map().width(), beside - hall.map().width(), 1, ahead.dwelt()));
        assertTrue(walk[beside]);
    }
}
