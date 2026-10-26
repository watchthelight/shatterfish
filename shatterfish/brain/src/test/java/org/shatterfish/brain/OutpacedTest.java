package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Observation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who outpaces the hero (issue #219, step 6): an awake enemy tagged {@code speed: fast} within
 * {@link Fight#OUTPACED_RANGE}, other than one the hero leaves by leaving the water. The retreat that
 * follows from it is {@link FightPolicyTest}'s.
 */
class OutpacedTest {

    @Test
    @DisplayName("an awake crab near outpaces the hero, and one past the range does not")
    void awake_crab() {
        Observation crab = FightPolicyTest.screen(3, 8, false, "worn shortsword",
                "##############",
                "#>...........#",
                "#........@C..#",
                "##############");
        assertTrue(Fight.outpaced(crab, FightPolicyTest.KNOWLEDGE, Fight.enemies(crab, FightPolicyTest.KNOWLEDGE)));
        Observation far = FightPolicyTest.screen(3, 8, false, "worn shortsword",
                "##########################",
                "#>.......................#",
                "#.@..................C...#",
                "##########################");
        assertFalse(Fight.outpaced(far, FightPolicyTest.KNOWLEDGE, Fight.enemies(far, FightPolicyTest.KNOWLEDGE)),
                "past the range, the hero has the room to act");
    }

    @Test
    @DisplayName("a crab the screen does not show does not outpace the hero: only the enemies in view count")
    void unseen_crab() {
        Observation none = FightPolicyTest.screen(3, 8, false, "worn shortsword",
                "##############",
                "#>...........#",
                "#........@...#",
                "##############");
        assertFalse(Fight.outpaced(none, FightPolicyTest.KNOWLEDGE, Fight.enemies(none, FightPolicyTest.KNOWLEDGE)));
    }

    @Test
    @DisplayName("a crab asleep, and a piranha the hero leaves by leaving the water, do not make the hero outpaced")
    void asleep_and_water_bound_do_not_outpace() {
        Observation asleep = FightPolicyTest.screen(3, 8, false, "worn shortsword",
                "##############",
                "#>...........#",
                "#...Z....@B..#",
                "##############");
        assertFalse(Fight.outpaced(asleep, FightPolicyTest.KNOWLEDGE, Fight.enemies(asleep, FightPolicyTest.KNOWLEDGE)));
        Observation piranha = FightPolicyTest.screen(3, 8, false, "worn shortsword",
                "##############",
                "#>...........#",
                "#........@P..#",
                "##############");
        assertFalse(Fight.outpaced(piranha, FightPolicyTest.KNOWLEDGE, Fight.enemies(piranha, FightPolicyTest.KNOWLEDGE)));
    }
}
