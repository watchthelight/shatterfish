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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heal Policy (story 4.9): it drinks an identified potion of healing when the hero's hit points
 * are at or under the damage the enemies in view are expected to deal before the fight is over, a
 * threshold that follows the threat rather than a constant, and one potion at a time.
 */
class HealPolicyTest {

    /** A potion as the inventory lists it, identified or by its appearance (Potion.java:222-226). */
    static ItemView potion(String name, int quantity) {
        return new ItemView(ItemKind.POTION, name, quantity, true, 0, true, false, "", EquipSlot.NONE,
                List.of("DRINK", "DROP", "THROW"), "DRINK");
    }

    /** The hero at {@code hp} of 20 in a corridor with the enemies of {@code rows}, holding {@code items}. */
    private static Observation fight(int hp, String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(1, hp, false, "worn shortsword", Hunger.NONE,
                "#######", row, "#######"), items);
    }

    private static Brain brain() {
        return new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = brain();
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static boolean drinks(Brain.Decided decided) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("DRINK");
    }

    @Test
    @DisplayName("hurt beside a rat, with a potion of healing, the hero drinks before it fights")
    void drinks_under_threat() {
        Brain.Decided decided = decide(fight(3, "#..@r.#", potion("potion of healing", 1)));
        assertEquals("heal", decided.decision().policy());
        assertTrue(drinks(decided), decided.action().toString());
        assertEquals("potion of healing", ((Action.UseItem) decided.action()).item().name());
        int danger = Heal.danger(fight(3, "#..@r.#"), FightPolicyTest.KNOWLEDGE);
        assertEquals("heal 3/" + danger, decided.decision().chosen().why());
        assertEquals("heal: threat", decided.decision().goal());
        assertEquals("fight", decide(fight(3, "#..@r.#")).decision().policy(), "without the potion, the fight Policy acts");
    }

    @Test
    @DisplayName("with more hit points than a rat is expected to take, the hero fights and keeps the potion")
    void holds_when_threat_is_small() {
        Brain.Decided decided = decide(fight(12, "#..@r.#", potion("potion of healing", 1)));
        assertEquals("fight", decided.decision().policy());
    }

    @Test
    @DisplayName("the same hit points drink beside a brute and fight beside a rat: the threshold is the threat")
    void threshold_follows_the_threat() {
        int rat = Heal.danger(fight(12, "#..@r.#"), FightPolicyTest.KNOWLEDGE);
        int brute = Heal.danger(fight(12, "#..@B.#"), FightPolicyTest.KNOWLEDGE);
        assertTrue(rat < 12 && brute >= 12, "rat " + rat + ", brute " + brute);
        assertTrue(drinks(decide(fight(12, "#..@B.#", potion("potion of healing", 1)))));
        assertEquals(0, Heal.danger(fight(12, "#..@..#"), FightPolicyTest.KNOWLEDGE), "no enemy in view, no danger");
        // Two rats, one on each side of the corridor, threaten more than one.
        int two = Heal.danger(fight(12, "#.r@r.#"), FightPolicyTest.KNOWLEDGE);
        assertTrue(two > rat, "two rats " + two + ", one " + rat);
    }

    @Test
    @DisplayName("with no enemy in view the hero never drinks, however hurt")
    void no_enemy_no_drink() {
        Brain.Decided decided = decide(fight(2, "#..@..#", potion("potion of healing", 1)));
        assertNotEquals("heal", decided.decision().policy());
        assertTrue(!drinks(decided));
    }

    @Test
    @DisplayName("an unidentified potion is never drunk to heal")
    void unknown_potion() {
        Brain.Decided decided = decide(fight(3, "#..@r.#", potion("crimson potion", 1)));
        assertEquals("fight", decided.decision().policy());
    }

    @Test
    @DisplayName("a hero missing less than the first turn's heal does not drink, whatever the threat")
    void not_while_nearly_full() {
        assertEquals(8, Heal.firstTurn(20), "(int)(0.8 * 20 + 14) = 30, a quarter rounded");
        assertEquals(30, Heal.heals(20));
        Brain.Decided decided = decide(fight(13, "#..@B.#", potion("potion of healing", 1)));
        assertTrue(!drinks(decided), "missing 7 of 20: " + decided.decision());
    }

    @Test
    @DisplayName("over several waits: one potion, then none while its heal lands, then another if still in danger")
    void one_potion_at_a_time() {
        Observation hurt = fight(3, "#..@r.#", potion("potion of healing", 2));
        Observation still = fight(3, "#..@r.#", potion("potion of healing", 1));
        Brain brain = brain();
        Belief belief = null;
        List<Boolean> drank = new ArrayList<>();
        List<Observation> screens = new ArrayList<>();
        screens.add(hurt);
        for (int i = 0; i < Heal.HEALING; i++) {
            screens.add(still);
        }
        for (Observation screen : screens) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            drank.add(drinks(decided));
            belief = brain.handed(screen, belief, decided);
        }
        List<Boolean> expected = new ArrayList<>(List.of(true));
        for (int i = 1; i < Heal.HEALING; i++) {
            expected.add(false);
        }
        expected.add(true);
        assertEquals(expected, drank, "a drink, " + (Heal.HEALING - 1) + " waits of fighting, then the next");
    }

    @Test
    @DisplayName("the memory keeps the wait of the last drink, and round-trips it")
    void the_memory() {
        Brain brain = brain();
        Observation hurt = fight(3, "#..@r.#", potion("potion of healing", 1));
        Belief belief = brain.update(hurt, null);
        assertEquals(-1, Memory.of(belief).drank());
        belief = brain.handed(hurt, belief, brain.decide(hurt, belief));
        Memory memory = Memory.of(belief);
        assertEquals(memory.waits(), memory.drank());
        assertEquals(memory, Memory.of(memory.belief()));
        belief = brain.update(fight(3, "#..@r.#"), belief);
        assertEquals(1, Memory.of(belief).drank(), "the fold keeps it");
    }
}
