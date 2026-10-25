package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The descend and explore Policies against a floor the hero fled (issue #165), played wait by wait
 * through the Brain: a region across the only way to the exit must not hold the hero between two cells,
 * and a hero that fled a floor by the stairs must not go straight back down for being hungry.
 */
class DescendFledTest {

    private static Brain brain() {
        return new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
    }

    private static Observation corridor(int hero) {
        char[] row = "#.........>#".toCharArray();
        row[hero] = '@';
        return DescendPolicyTest.hero(ExplorePolicyTest.screen(5, "############", new String(row), "############"),
                20, 20, Hunger.NONE);
    }

    @Test
    @DisplayName("a region across the only way to the exit, the floor overstayed: the hero crosses it and leaves, "
            + "where descend's Step in and explore's Step away once undid each other until the hero starved")
    void region_across_the_only_way() {
        for (int centre = 3; centre <= 8; centre++) {
            for (int radius = 1; radius <= 3; radius++) {
                for (int start = 1; start <= 8; start++) {
                    Brain brain = brain();
                    Belief belief = DescendPolicyTest.arrivedAt(start + 12, 600, 1, 0)
                            .avoiding(new Memory.Avoid(5, 0, centre + 12, radius, 5000)).belief();
                    int hero = start;
                    List<Integer> cells = new ArrayList<>();
                    boolean left = false;
                    for (int wait = 0; wait < 40 && !left; wait++) {
                        Observation screen = corridor(hero);
                        belief = brain.update(screen, belief);
                        Brain.Decided decided = brain.decide(screen, belief);
                        belief = brain.handed(screen, belief, decided);
                        if (decided.action() instanceof Action.Descend) {
                            left = true;
                        } else if (decided.action() instanceof Action.Step step) {
                            hero = step.cell() - 12;
                            left = hero == 10;
                        }
                        cells.add(hero);
                    }
                    assertTrue(left, "centre " + centre + " radius " + radius + " from " + start + ": " + cells);
                }
            }
        }
    }

    private static Memory fled() {
        List<Memory.Spot> dwelt = new ArrayList<>();
        for (int i = 0; i < Explore.SEARCHES; i++) {
            dwelt.add(new Memory.Spot(2, 0, 100 + i));
        }
        return new Memory(10, 3, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Memory.Spot.NOWHERE, 0, false, dwelt, List.of(), "", 0, -1, -1,
                List.of(new Memory.Found("3:0", 0, 1)), List.of());
    }

    private static Observation exit(int hp, Hunger hunger) {
        return DescendPolicyTest.hero(ExplorePolicyTest.screen(2, "#######", "#..E..#", "#######"), hp, 20, hunger);
    }

    @Test
    @DisplayName("fled the floor below by the stairs and back at full health: the hero lingers LINGER waits "
            + "before going down, where it went straight back to the enemy it fled")
    void lingers_before_going_back_down() {
        Brain brain = brain();
        Belief belief = fled().belief();
        int lingered = 0;
        Brain.Decided decided;
        do {
            Observation screen = exit(20, Hunger.NONE);
            belief = brain.update(screen, belief);
            decided = brain.decide(screen, belief);
            belief = brain.handed(screen, belief, decided);
            lingered++;
        } while (!(decided.action() instanceof Action.Descend) && lingered < 100);
        assertEquals(Explore.LINGER + 1, lingered, "LINGER waits spent, then down");
    }

    @Test
    @DisplayName("fled and hurt: the rest runs to full health and then the linger")
    void hurt_rests_then_lingers() {
        Brain brain = brain();
        Belief belief = fled().belief();
        for (int wait = 0; wait < 6; wait++) {
            Observation hurt = exit(5 + wait, Hunger.NONE);
            belief = brain.update(hurt, belief);
            Brain.Decided decided = brain.decide(hurt, belief);
            assertFalse(decided.action() instanceof Action.Descend, "wait " + wait + ": " + decided.decision());
            belief = brain.handed(hurt, belief, decided);
        }
    }

    @Test
    @DisplayName("fled and hungry or starving: waiting costs food, so the hero goes down")
    void hunger_does_not_linger() {
        Brain brain = brain();
        Observation starving = exit(20, Hunger.STARVING);
        Belief belief = brain.update(starving, fled().belief());
        assertEquals(new Action.Descend(), brain.decide(starving, belief).action());
    }
}
