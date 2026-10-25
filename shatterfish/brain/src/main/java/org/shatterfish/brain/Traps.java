package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.TrapView;

import java.util.List;

/**
 * The ground near a trap the screen shows (issue #184). A trap is drawn only once it is revealed, and the
 * rooms that hold traps hold hidden ones beside the shown: a burned room fills each cell of its patch
 * with one of five things alike, floor, embers, a shown trap, a hidden trap or a spent one
 * (BurnedRoom.java:86-113), so where burning traps are drawn, hidden ones are as likely as the live ones.
 * The Brain cannot see which cell they are on, and does not try to: it knows only that the cells near a
 * trap it can see may hold one it cannot, and that an intentional search finds every trap within one cell
 * of the hero, bar the kinds that cannot be searched for (Hero.java:2522-2523, :2584-2589); one search is made from a cell, found or not, so a trap that cannot be found costs one search and no loop.
 *
 * <p>So a cell within {@link #REACH} of a shown trap is <em>unsearched</em> until a search was made from
 * a cell within one of it. A calm hero searches before a Step onto an unsearched cell
 * ({@link Brain#decide}); a hero with an enemy in view, for whom two turns are dear, takes no Step onto
 * one, which refuses a retreat over ground the hero has no reason to trust. Both read the screen and the
 * searches the Brain made ({@link Memory#dwelt}), never the cell a hidden trap is on.
 */
final class Traps {

    /**
     * How far from a shown trap a cell may hold a hidden one the Brain looks for, in cells. An assumption:
     * the patch of a burned room is clustered (BurnedRoom.java:53-55), so a hidden trap is seldom more than
     * two cells from a shown one, and wider would search whole rooms for one trap in a corridor.
     */
    static final int REACH = 2;

    /** How far a search finds a trap: one cell (Hero.java:2522-2523; the Rogue's two is not counted on). */
    static final int FOUND = 1;

    /**
     * How near an enemy must be, in cells, for the hero to be in a fight that has no turns to spare for a
     * search. An assumption: farther off, two turns searching cost less than a Step the fight Policy
     * keeps being refused (issue #184).
     */
    static final int ENGAGED = 4;

    /** What the search the Brain makes ahead of a Step is called in its record. */
    static final String AHEAD = "search ahead";

    private Traps() {
    }

    /** Whether {@code cell} is within {@link #REACH} of a trap the screen shows, armed or spent. */
    static boolean near(Observation observation, int cell) {
        MapSection map = observation.map();
        for (TrapView trap : map.traps()) {
            if (distance(map, trap.cell(), cell) <= REACH) {
                return true;
            }
        }
        return false;
    }

    /** Whether a search was made on this floor from a cell within {@link #FOUND} of {@code cell}. */
    static boolean covered(Observation observation, Memory memory, int cell) {
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Spot spot : memory.dwelt()) {
            if (spot.on(depth, branch) && distance(map, spot.cell(), cell) <= FOUND) {
                return true;
            }
        }
        return false;
    }

    /** Whether a Step onto {@code cell} is onto ground near a shown trap that no search has covered. */
    static boolean unsearched(Observation observation, Memory memory, int cell) {
        return near(observation, cell) && !covered(observation, memory, cell)
                && observation.map().transitions().stream().noneMatch(transition -> transition.cell() == cell);
    }

    /** {@code offered} less the Steps onto unsearched ground; all of it if that would leave nothing. */
    static List<Action> withheld(Observation observation, Memory memory, List<Action> offered) {
        if (observation.map().traps().isEmpty()) {
            return offered;
        }
        List<Action> kept = offered.stream().filter(action -> !(action instanceof Action.Step step
                && unsearched(observation, memory, step.cell()))).toList();
        return kept.isEmpty() ? offered : kept;
    }

    private static int distance(MapSection map, int a, int b) {
        int width = map.width();
        return Math.max(Math.abs(a % width - b % width), Math.abs(a / width - b / width));
    }
}
