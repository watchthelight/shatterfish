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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #219, step 6: a floor with no way down on foot and a chasm in view is left by the chasm
 * ({@code Chasm.java:99-121}) instead of being wandered at random until the hero starves, and a hero
 * that goes back and forth over three cells is stopped as one that goes over two.
 */
class PlungeTest {

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    private static Observation screen(int hp, Hunger hunger, String... rows) {
        return FightPolicyTest.screen(1, hp, false, "worn shortsword", hunger, rows);
    }

    /**
     * The Decisions the Brain makes over one corridor, driven as the driver drives it: each Step moves
     * the hero a cell, and the walk ends at a Step onto the chasm or after {@code waits}. The explore
     * Policy has its searches to make first, and the floor is spent only when they are.
     */
    private static List<Brain.Decided> walked(int hp, Hunger hunger, String corridor, int waits) {
        Brain brain = brain();
        String wall = "#".repeat(corridor.length());
        String ground = corridor.replace('@', '.');
        int x = corridor.indexOf('@');
        Belief belief = null;
        List<Brain.Decided> all = new ArrayList<>();
        for (int wait = 0; wait < waits; wait++) {
            StringBuilder row = new StringBuilder(ground);
            row.setCharAt(x, '@');
            Observation screen = screen(hp, hunger, wall, row.toString(), wall);
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            all.add(decided);
            belief = brain.handed(screen, belief, decided);
            if (decided.action() instanceof Action.Step step) {
                int to = step.cell() - corridor.length();
                if (ground.charAt(to) == 'c') {
                    break;
                }
                x = to;
            }
        }
        return all;
    }

    private static List<Brain.Decided> plunges(List<Brain.Decided> decided) {
        return decided.stream().filter(d -> d.decision() != null && "plunge".equals(d.decision().policy())).toList();
    }

    @Test
    @DisplayName("a spent floor with a chasm in view and a hero fit to fall: walk to the chasm, then jump")
    void walks_then_jumps() {
        List<Brain.Decided> all = walked(20, Hunger.NONE, "#@..c.#", 150);
        List<Brain.Decided> plunge = plunges(all);
        assertTrue(!plunge.isEmpty(), all.toString());
        Brain.Decided last = all.get(all.size() - 1);
        assertEquals("plunge", last.decision().policy(), last.decision().toString());
        assertEquals("jump 11", last.decision().chosen().why());
        assertEquals(new Action.Step(7 + 4), last.action());
    }

    @Test
    @DisplayName("a hero too hurt to fall rests first, and a starving one, which cannot regenerate, jumps at half its health")
    void rests_before_it_falls() {
        List<Brain.Decided> hurt = plunges(walked(8, Hunger.NONE, "#...@c#", 150));
        assertTrue(!hurt.isEmpty());
        assertEquals("rest: chasm", hurt.get(0).decision().chosen().why());
        assertTrue(hurt.stream().noneMatch(d -> d.action().equals(new Action.Step(7 + 5))), "no jump at 8 of 20");

        assertTrue(plunges(walked(8, Hunger.STARVING, "#...@c#", 150)).isEmpty(), "8 of 20 is under half");
        List<Brain.Decided> fit = plunges(walked(16, Hunger.STARVING, "#...@c#", 150));
        assertEquals(new Action.Step(7 + 5), fit.get(fit.size() - 1).action());
    }

    @Test
    @DisplayName("a floor with ground left to explore is not left by the chasm")
    void unspent_floor_is_walked() {
        assertTrue(plunges(walked(20, Hunger.NONE, " ....@c#", 2)).isEmpty());
    }

    private static Observation at(int x) {
        StringBuilder row = new StringBuilder("#.......#");
        row.setCharAt(x, '@');
        return ExplorePolicyTest.screen(2, "#########", row.toString(), "#########");
    }

    @Test
    @DisplayName("a walk over three cells, A-B-A-C, still builds the bounce count to the limit")
    void three_cell_loop_is_counted() {
        Memory memory = Memory.START;
        int[] loop = {3, 4, 3, 2};
        for (int i = 0; i < 40; i++) {
            memory = Beliefs.fold(memory, at(loop[i % loop.length]), Screens.CODEX);
        }
        assertTrue(memory.bounces() >= Memory.BOUNCES, "bounces " + memory.bounces());
    }
}
