package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A piranha never leaves the water and loses a hero it has no water beside (Piranha.java:72-77, :139, :150, :171-200), so
 * the Brain neither approaches nor fights one: it steps out of reach, and a piranha that cannot reach the hero is scenery
 * (issue #219, step 6). Screens are drawn as in {@link FightPolicyTest}: {@code w} water, {@code Q} a piranha in it.
 */
class WaterBoundTest {

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        return brain.decide(screen, belief);
    }

    private static Observation shore(int hp) {
        return FightPolicyTest.screen(3, hp, false, "worn shortsword",
                "##########",
                "#........#",
                "#..@wwQw.#",
                "#........#",
                "##########");
    }

    @Test
    @DisplayName("a hero on the shore with a piranha swimming toward it steps out of reach instead of fighting")
    void leaves_the_shore() {
        Observation screen = shore(20);
        Brain.Decided decided = decide(screen);
        assertEquals(Fight.NAME, decided.decision().policy());
        assertTrue(decided.decision().chosen().why().startsWith("retreat: leave water"), decided.decision().toString());
        int x = ((Action.Step) decided.action()).cell() % screen.map().width();
        assertTrue(x <= 2, "a Step away from the water, not along it: " + decided.action());
    }

    @Test
    @DisplayName("a piranha is not approached from dry ground, at full health or not")
    void is_not_approached() {
        Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#..........#",
                "#.@..wwwwQw#",
                "#..........#",
                "############");
        assertTrue(Fight.enemies(screen, FightPolicyTest.KNOWLEDGE).isEmpty(), "out of reach: scenery");
        assertTrue(Calm.quiet(screen, FightPolicyTest.KNOWLEDGE));
        assertNotEquals(Fight.NAME, decide(screen).decision().policy());
    }

    @Test
    @DisplayName("the shore of a piranha's water is planned around: with room elsewhere the hero never steps beside it")
    void shore_is_planned_around() {
        Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "#############",
                "#...........#",
                "#...@.wwwwQw#",
                "#...........#",
                "#############");
        boolean[] swum = Fight.swum(screen, FightPolicyTest.KNOWLEDGE);
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = null;
        for (int round = 0; round < 4; round++) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            if (decided.action() instanceof Action.Step step && round > 0) {
                assertFalse(Fight.exposed(screen.map(), step.cell(), swum), "a Step to the shore: " + decided.decision());
            }
            belief = brain.handed(screen, belief, decided);
        }
        assertFalse(Memory.of(belief).avoided(3, 0, Memory.of(belief).waits()).isEmpty(), "the shore is remembered as avoided");
    }

    /**
     * The only way on is along the shore (issue #288, Refs #219). The plan crosses the shore because no way around is
     * left; the Step into it lifts the cell's region, and the shore the Brain sets every wait must not set it again, or the
     * explore Policy's "away" walks the hero straight back out and the plan walks it in again, beside piranhas that close
     * in at every wait.
     */
    @Test
    @DisplayName("a shore that is the only way on is crossed, not stepped into and out of again")
    void forced_crossing_does_not_oscillate() {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = null;
        int width = 14;
        int hero = 1 + width;
        java.util.List<Integer> walked = new java.util.ArrayList<>();
        for (int round = 0; round < 14; round++) {
            StringBuilder row = new StringBuilder("#...........  ");
            row.setCharAt(hero % width, '@');
            Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                    "##############",
                    row.toString(),
                    "#..wwwwwwwQw##",
                    "##############");
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            assertFalse(decided.decision().chosen().why().startsWith("away"),
                    "walked out of the shore the plan just crossed into at round " + round + " " + walked + ": "
                            + decided.decision());
            belief = brain.handed(screen, belief, decided);
            if (decided.action() instanceof Action.Step step) {
                hero = step.cell();
            }
            walked.add(hero % width);
        }
        assertTrue(hero % width >= 9, "the shore was crossed: " + walked);
    }

    /**
     * Issue #219, the piranha deaths on the tuning slice: a hero that has walked out of a piranha's reach once is shown no
     * piranha again, and the plan "through" the shore walked three piranhas' length of water for a frontier and searched in
     * place beside them (28 of 32 health in six waits). With the exit in view the shore is left alone: nothing past it is worth it.
     */
    @Test
    @DisplayName("with the exit in view, a frontier past a piranha's shore is not walked to")
    void known_exit_leaves_a_shore_frontier() {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = null;
        int width = 14;
        int hero = 1 + width;
        for (int round = 0; round < 6; round++) {
            StringBuilder row = new StringBuilder("#...........  ");
            row.setCharAt(hero % width, '@');
            Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                    "##############",
                    row.toString(),
                    "#>.wwwwwwwQw##",
                    "##############");
            boolean[] swum = Fight.swum(screen, FightPolicyTest.KNOWLEDGE);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            if (decided.action() instanceof Action.Step step && round > 0) {
                assertFalse(Fight.exposed(screen.map(), step.cell(), swum), "a Step to the shore: " + decided.decision());
            }
            belief = brain.handed(screen, belief, decided);
            if (decided.action() instanceof Action.Step step) {
                hero = step.cell();
            }
        }
    }

    @Test
    @DisplayName("water the piranha cannot swim to is no reason to leave it")
    void separate_water() {
        Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#..........#",
                "#..@w..wwQw#",
                "#..........#",
                "############");
        assertTrue(Fight.enemies(screen, FightPolicyTest.KNOWLEDGE).isEmpty());
        assertFalse(decide(screen).decision().chosen().why().startsWith("retreat: leave water"));
    }

    @Test
    @DisplayName("a rat beside the shore is still fought, and the piranha that cannot reach the hero does not count against it")
    void other_enemies_unaffected() {
        Observation screen = FightPolicyTest.screen(3, 20, false, "worn shortsword",
                "############",
                "#..........#",
                "#.@r.wwwwQw#",
                "#..........#",
                "############");
        assertEquals(1, Fight.enemies(screen, FightPolicyTest.KNOWLEDGE).size());
        Brain.Decided decided = decide(screen);
        assertEquals(new Action.Attack(3 + 2 * 12), decided.action());
        assertFalse(decided.decision().chosen().why().startsWith("retreat: leave water"));
    }
}
