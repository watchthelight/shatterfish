package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Observation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two named notions of calm (issue #206), pinned side by side so they cannot drift: {@link Calm#quiet} is
 * no Prompt and no enemy in view; {@link Calm#unengaged} is no enemy within {@link Traps#ENGAGED} cells.
 */
class CalmTest {

    private static Observation screen(String... rows) {
        return ExplorePolicyTest.screen(1, rows);
    }

    @Test
    @DisplayName("no enemy in view: quiet and unengaged")
    void no_enemy() {
        Observation none = screen(
                "##############",
                "#....^.......#",
                "#...@........#",
                "##############");
        assertTrue(Calm.quiet(none, FightPolicyTest.KNOWLEDGE));
        assertTrue(Calm.unengaged(none, FightPolicyTest.KNOWLEDGE));
    }

    @Test
    @DisplayName("a far enemy (more than ENGAGED cells off): unengaged, but not quiet")
    void far_enemy() {
        Observation far = screen(
                "##############",
                "#....^.......#",
                "#...@......r.#",
                "##############");
        assertFalse(Calm.quiet(far, FightPolicyTest.KNOWLEDGE));
        assertTrue(Calm.unengaged(far, FightPolicyTest.KNOWLEDGE));
    }

    @Test
    @DisplayName("an enemy exactly ENGAGED cells off is engaged; one cell farther is not")
    void the_edge() {
        Observation at = screen(
                "##############",
                "#....^.......#",
                "#...@...r....#",
                "##############");
        Observation beyond = screen(
                "##############",
                "#....^.......#",
                "#...@....r...#",
                "##############");
        assertFalse(Calm.unengaged(at, FightPolicyTest.KNOWLEDGE));
        assertTrue(Calm.unengaged(beyond, FightPolicyTest.KNOWLEDGE));
    }

    @Test
    @DisplayName("a near enemy: neither")
    void near_enemy() {
        Observation near = screen(
                "##############",
                "#....^.......#",
                "#...@.r......#",
                "##############");
        assertFalse(Calm.quiet(near, FightPolicyTest.KNOWLEDGE));
        assertFalse(Calm.unengaged(near, FightPolicyTest.KNOWLEDGE));
    }
}
