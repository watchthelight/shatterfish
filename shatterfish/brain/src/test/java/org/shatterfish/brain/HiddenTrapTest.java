package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hidden traps beside the ones the screen shows (issue #184), played wait by wait through the Brain: a hidden
 * trap is not drawn until a search within one cell of it finds it (Hero.java:2587-2589), so the Brain does not
 * Step onto a cell near a trap it can see until a search has covered that cell, and, with an enemy in view,
 * does not Step onto such a cell at all. The world here knows where its hidden trap is; the Brain is only
 * ever shown the screen.
 *
 * <p>Worlds are drawn as text, as in {@link ExplorePolicyTest}: {@code ^} a trap the screen shows, a space a
 * cell never seen.
 */
class HiddenTrapTest {

    private static final int WIDTH = 14;

    /** A corridor two cells wide with a shown trap at x 5 on its top row and unseen space beyond its end. */
    private static final String[] LAYOUT = {
        "##############",
        "#....^.......#",
        "#............ ",
        "##############",
    };

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 5L);
    }

    private static int cell(int x, int y) {
        return x + y * WIDTH;
    }

    /** The screen with the hero at {@code hero}, the hidden trap drawn only when {@code found}. */
    private static Observation render(int hero, int hidden, boolean found) {
        String[] rows = LAYOUT.clone();
        if (found) {
            rows[hidden / WIDTH] = swap(rows[hidden / WIDTH], hidden % WIDTH, '^');
        }
        rows[hero / WIDTH] = swap(rows[hero / WIDTH], hero % WIDTH, '@');
        return ExplorePolicyTest.screen(1, rows);
    }

    private static String swap(String row, int x, char c) {
        return row.substring(0, x) + c + row.substring(x + 1);
    }

    /** Within two cells of the shown trap at x 5, y 1. */
    private static boolean nearShown(int cell) {
        return Math.max(Math.abs(cell % WIDTH - 5), Math.abs(cell / WIDTH - 1)) <= 2;
    }

    private static boolean near(int a, int b) {
        return Math.max(Math.abs(a % WIDTH - b % WIDTH), Math.abs(a / WIDTH - b / WIDTH)) <= 1;
    }

    /** What the Brain did: the cells it stood on, whether it stepped on the hidden trap, its actions. */
    private record Walk(List<Integer> cells, boolean fell, List<Action> actions, int searches) {
    }

    private static Walk walk(int start, int hidden, int waits) {
        Brain brain = brain();
        int hero = start;
        boolean found = false;
        Belief belief = null;
        List<Integer> cells = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        int searches = 0;
        for (int i = 0; i < waits; i++) {
            Observation observation = render(hero, hidden, found);
            belief = brain.update(observation, belief);
            Brain.Decided decided = brain.decide(observation, belief);
            actions.add(decided.action());
            belief = brain.handed(observation, belief, decided);
            if (decided.action() instanceof Action.Step step) {
                hero = step.cell();
                cells.add(hero);
                if (hero == hidden) {
                    return new Walk(cells, true, actions, searches);
                }
            } else if (decided.action() instanceof Action.Search) {
                searches++;
                found |= near(hero, hidden);
            }
        }
        return new Walk(cells, false, actions, searches);
    }

    @Test
    @DisplayName("a hidden trap beside a shown one: the Brain searches before it steps into the shown trap's "
            + "reach, finds the hidden one, and crosses without stepping on it")
    void searches_before_stepping_near_a_shown_trap() {
        for (int hidden : new int[] {cell(6, 2), cell(6, 1), cell(7, 2), cell(4, 2), cell(3, 2)}) {
            Walk walk = walk(cell(1, 1), hidden, 80);
            assertFalse(walk.fell(), "hidden at " + hidden + ": " + walk.cells() + " " + walk.actions());
            assertTrue(walk.cells().contains(cell(12, 1)) || walk.cells().contains(cell(12, 2)),
                    "it crosses to the far end, hidden at " + hidden + ": " + walk.cells() + " " + walk.actions());
            assertTrue(walk.searches() > 0, "it searched, hidden at " + hidden);
        }
    }

    @Test
    @DisplayName("with no trap on the screen it does not search on the way: a hidden trap is not looked for everywhere")
    void no_trap_no_search() {
        Observation bare = ExplorePolicyTest.screen(1,
                "##############",
                "#@...........#",
                "#............ ",
                "##############");
        Brain.Decided decided = brain().decide(bare, Screens.drive(brain(), bare));
        assertEquals(Explore.NAME, decided.decision().policy());
        assertTrue(decided.action() instanceof Action.Step, decided.action().toString());
    }

    @Test
    @DisplayName("with an enemy in view the Brain takes no Step onto a cell near a shown trap that no search has covered")
    void no_unsearched_step_in_a_fight() {
        Observation fight = ExplorePolicyTest.screen(1,
                "##############",
                "#....^.......#",
                "#...@.....r..#",
                "##############");
        Brain.Decided decided = brain().decide(fight, Screens.drive(brain(), fight));
        assertFalse(decided.action() instanceof Action.Step step && nearShown(step.cell()),
                "no Step near the shown trap: " + decided.action() + " " + decided.decision());
    }
}
