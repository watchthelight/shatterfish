package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-ply expectimax (story 6.1, ADR-0010 option 2) on constructed rooms: it attacks a weak enemy
 * it is likely to kill, retreats from one it is likely to lose to, prefers the cell the 4.7 Policy
 * prefers in a corridor, picks the same Action when the Observation is the same, and stands aside on
 * quiet turns.
 *
 * <p>The hit table here is built in the test from the closed form of the rule's two draws, since a
 * Brain's test cannot read the Codex from disk; {@code HitTableTest} in the Codex module holds the table
 * the generator writes to that closed form and to the engine's own {@code Char.hit}.
 */
class OnePlyModelTest {

    /** The table of {@code combat.json}'s grid, cell by cell, accuracy outer. */
    static Codex.HitTable table() {
        Codex.Grid accuracy = new Codex.Grid("accuracy", 0, 40, 2);
        Codex.Grid evasion = new Codex.Grid("evasion", 0, 40, 2);
        List<Codex.HitCell> cells = new ArrayList<>();
        for (int a = 0; a <= 40; a += 2) {
            for (int d = 0; d <= 40; d += 2) {
                int perMille;
                if (a == 0) {
                    perMille = d == 0 ? 1000 : 0;
                } else {
                    long numerator = a >= d ? 2L * a - d : a;
                    long denominator = a >= d ? 2L * a : 2L * d;
                    perMille = (int) ((2000L * numerator + denominator) / (2L * denominator));
                }
                cells.add(new Codex.HitCell(a, d, Codex.HitCell.EXACT, perMille));
            }
        }
        return new Codex.HitTable("Char.hit", new Codex.Citation("core/Char.java", 619), accuracy, evasion, true, "", cells);
    }

    static final Codex.Knowledge KNOWLEDGE = FightPolicyTest.KNOWLEDGE.withHit(table());

    private static Brain onePly() {
        return new Brain(KNOWLEDGE, Screens.WEIGHTS, 9L, Brain.Model.ONE_PLY);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = onePly();
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "only an Action the screen offers: " + decided.action());
        return decided;
    }

    private static int cell(Observation screen, int x, int y) {
        return x + y * screen.map().width();
    }

    @Test
    @DisplayName("an adjacent rat, which a level-one hero is likely to kill, is attacked, by the one-ply Policy")
    void attacks_a_weak_enemy() {
        Observation rat = FightPolicyTest.screen(20, false,
                "#######",
                "#.@r..#",
                "#.....#",
                "#######");
        Brain.Decided decided = decide(rat);
        assertEquals(OnePly.NAME, decided.decision().policy());
        assertEquals(new Action.Attack(cell(rat, 3, 1)), decided.action());
        assertEquals("attack: marsupial rat", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("a brute the hero is likely to lose to is not fought: the hero steps out of its reach")
    void retreats_from_a_strong_enemy() {
        Observation brute = FightPolicyTest.screen(20, false,
                "#########",
                "#.......#",
                "#...@B..#",
                "#.......#",
                "#########");
        Brain.Decided decided = decide(brute);
        assertEquals(OnePly.NAME, decided.decision().policy());
        assertTrue(decided.action() instanceof Action.Step, decided.decision().toString());
        assertTrue(decided.decision().chosen().why().startsWith("retreat "), decided.decision().chosen().why());
        int step = ((Action.Step) decided.action()).cell();
        assertTrue(Fight.chebyshev(brute.map(), step, cell(brute, 5, 2)) >= 2, "out of the brute's reach: " + step);
        // The attack is among what it weighed, and was not the best of it.
        assertFalse(decided.action() instanceof Action.Attack);
    }

    @Test
    @DisplayName("the hero is cornered by the brute: it fights, as the fight Policy does, rather than walk into the wall")
    void cornered_it_fights() {
        Observation brute = FightPolicyTest.screen(20, false,
                "#####",
                "#@B.#",
                "#####");
        Brain.Decided decided = decide(brute);
        assertEquals(new Action.Attack(cell(brute, 2, 1)), decided.action(), decided.decision().toString());
    }

    @Test
    @DisplayName("two enemies coming across a room: it steps back to the cell the 4.7 Policy chooses, the one fewest cells can reach")
    void prefers_the_corridor_cell() {
        Observation doorway = FightPolicyTest.screen(20, false,
                "############",
                "#####......#",
                "....@..r...#",
                "#####...r..#",
                "############");
        Brain.Decided decided = decide(doorway);
        assertEquals(OnePly.NAME, decided.decision().policy());
        assertEquals(new Action.Step(cell(doorway, 3, 2)), decided.action(), decided.decision().toString());
        Brain fight = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        assertEquals(fight.decide(doorway, fight.update(doorway, null)).action(), decided.action(),
                "the cell the baseline's chokepoint takes");
    }

    @Test
    @DisplayName("a lone rat at a distance is waited for, not walked away from: the first swing is the hero's")
    void holds_for_a_coming_enemy() {
        Observation rat = FightPolicyTest.screen(20, false,
                "#########",
                "#.@..r..#",
                "#.......#",
                "#########");
        Brain.Decided decided = decide(rat);
        assertEquals(OnePly.NAME, decided.decision().policy());
        assertEquals(new Action.Wait(), decided.action(), decided.decision().toString());
    }

    @Test
    @DisplayName("the same Observation gets the same Action, from any number of Brains and any seed")
    void same_screen_same_action() {
        Observation doorway = FightPolicyTest.screen(20, false,
                "############",
                "#####......#",
                "....@..r...#",
                "#####...r..#",
                "############");
        Brain.Decided first = decide(doorway);
        for (long seed : new long[] {1L, 9L, 12345L}) {
            Brain brain = new Brain(KNOWLEDGE, Screens.WEIGHTS, seed, Brain.Model.ONE_PLY);
            Brain.Decided again = brain.decide(doorway, brain.update(doorway, null));
            assertEquals(first.action(), again.action(), "seed " + seed);
            assertEquals(first.decision().chosen(), again.decision().chosen(), "seed " + seed);
        }
    }

    @Test
    @DisplayName("quiet turns stay with the Policies: no enemy in view, an enemy asleep and far, an enemy that shoots from afar")
    void quiet_turns_are_not_its() {
        Observation calm = FightPolicyTest.screen(20, false,
                "#########",
                "#.@.....#",
                "#########");
        Brain brain = onePly();
        Memory memory = Memory.of(brain.update(calm, null));
        OnePly policy = new OnePly(KNOWLEDGE);
        assertFalse(policy.enters(calm, memory));
        Observation far = FightPolicyTest.screen(20, false,
                "###########",
                "#.@.....r.#",
                "###########");
        assertFalse(policy.enters(far, Memory.of(brain.update(far, null))), "five cells away is not engaged");
        Observation near = FightPolicyTest.screen(20, false,
                "###########",
                "#.@..r....#",
                "###########");
        assertTrue(policy.enters(near, Memory.of(brain.update(near, null))));
        // The baseline Brain has no such Policy at all.
        assertFalse(new Brain(KNOWLEDGE, Screens.WEIGHTS, 9L).policies().contains(OnePly.NAME));
        assertTrue(onePly().policies().contains(OnePly.NAME));
        assertEquals(Brain.policyNames(Brain.Model.ONE_PLY).indexOf(OnePly.NAME) + 1,
                Brain.policyNames(Brain.Model.ONE_PLY).indexOf(Fight.NAME), "directly above the fight Policy");
    }

    @Test
    @DisplayName("a Codex that recorded no hit table cannot build the model")
    void needs_the_hit_table() {
        assertThrows(IllegalArgumentException.class,
                () -> new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L, Brain.Model.ONE_PLY));
        assertNotEquals(Brain.configuration(Screens.WEIGHTS), Brain.configuration(Screens.WEIGHTS, Brain.Model.ONE_PLY),
                "the model is part of the Brain's configuration");
    }
}
