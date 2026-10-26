package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ranged Policy (issue #219, step 7): the hero throws the plain missiles it holds at an awake enemy
 * coming for it, from two to six cells away, while the fight is one it would take, before the fight Policy
 * walks up to it.
 */
class RangedPolicyTest {

    /** A missile as the inventory lists it: unidentified, throwable. */
    private static ItemView missile(String name, int quantity) {
        return new ItemView(ItemKind.MISSILE, name, quantity, false, 0, false, false, "", EquipSlot.NONE,
                List.of("DROP", "THROW"), "THROW");
    }

    private static Observation screen(String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length())), items);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static int cellOfRat(Observation screen) {
        return screen.actors().actors().get(0).cell();
    }

    @Test
    @DisplayName("a rat four cells off and a stack of throwing stones: the hero throws one at it")
    void throws_at_an_approaching_enemy() {
        Observation screen = screen("#@...r.#", missile("throwing stone", 3));
        Brain.Decided decided = decide(screen);
        assertEquals("ranged", decided.decision().policy());
        assertEquals("ranged: throw", decided.decision().goal());
        assertEquals("throw: throwing stone", decided.decision().chosen().why());
        assertEquals(new Action.UseItemAt(((Action.UseItemAt) decided.action()).item(), "THROW", cellOfRat(screen)),
                decided.action());
        assertEquals("throwing stone", ((Action.UseItemAt) decided.action()).item().name());
    }

    @Test
    @DisplayName("the same rat with nothing to throw is the fight Policy's")
    void nothing_to_throw() {
        assertEquals("fight", decide(screen("#@...r.#")).decision().policy());
        assertEquals("fight", decide(screen("#@...r.#", HealPolicyTest.potion("potion of healing", 1))).decision().policy());
    }

    @Test
    @DisplayName("beside the enemy the hero fights, and out of reach it does not throw")
    void only_between_two_and_six() {
        assertEquals("fight", decide(screen("#@r....#", missile("throwing stone", 3))).decision().policy());
        assertEquals("ranged", decide(screen("#@.....r#", missile("throwing stone", 3))).decision().policy());
        assertNotEquals("ranged", decide(screen("#@......r#", missile("throwing stone", 3))).decision().policy());
    }

    @Test
    @DisplayName("a sleeping enemy is left asleep")
    void not_a_sleeper() {
        Observation sleeping = HealPolicyTest.asleep(screen("#@...r.#", missile("throwing stone", 3)));
        assertNotEquals("ranged", decide(sleeping).decision().policy());
    }

    @Test
    @DisplayName("a wall on the line stops the throw")
    void the_line_must_be_clear() {
        assertNotEquals("ranged", decide(screen("#@.#.r.#", missile("throwing stone", 3))).decision().policy());
    }

    @Test
    @DisplayName("a fight the hero would not take is retreated from, not thrown into")
    void only_a_favourable_fight() {
        Observation brute = screen("#.@..B.#", missile("throwing stone", 3));
        assertTrue(!Fight.favourable(brute, FightPolicyTest.KNOWLEDGE, Fight.enemies(brute, FightPolicyTest.KNOWLEDGE)));
        assertNotEquals("ranged", decide(brute).decision().policy());
    }

    @Test
    @DisplayName("the highest tier the hero's strength carries within one point is thrown first")
    void the_best_missile() {
        // Strength 10 carries tier 2 (requires 11) and not tier 3 (requires 13).
        Brain.Decided decided = decide(screen("#@...r.#", missile("throwing stone", 3), missile("bolas", 2),
                missile("throwing club", 1)));
        assertEquals("throw: throwing club", decided.decision().chosen().why());
        assertEquals("throw: throwing stone",
                decide(screen("#@...r.#", missile("throwing stone", 3), missile("bolas", 2))).decision().chosen().why());
        assertNotEquals("ranged", decide(screen("#@...r.#", missile("incendiary dart", 3))).decision().policy());
    }

    @Test
    @DisplayName("a missile shown cursed or shown upgraded is kept, and a Goo in view is the fight Policy's to dodge")
    void kept_and_goo() {
        ItemView cursed = new ItemView(ItemKind.MISSILE, "throwing stone", 3, true, 0, true, true, "", EquipSlot.NONE,
                List.of("DROP", "THROW"), "THROW");
        ItemView upgraded = new ItemView(ItemKind.MISSILE, "throwing stone", 3, true, 1, false, false, "", EquipSlot.NONE,
                List.of("DROP", "THROW"), "THROW");
        assertNotEquals("ranged", decide(screen("#@...r.#", cursed)).decision().policy());
        assertNotEquals("ranged", decide(screen("#@...r.#", upgraded)).decision().policy());
        assertNotEquals("ranged", decide(HealPolicyTest.named(screen("#@...r.#", missile("throwing stone", 3)), "Goo"))
                .decision().policy());
    }
}
