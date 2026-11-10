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
 * Story 10.51: the hero drinks its waterskin. A death audit found 106 of 114 losing heroes holding dew and no Policy
 * drinking it. The first tests here fail on main: a hurt hero with a filled skin and an enemy near never drinks it.
 */
class DrinkPolicyTest {

    private static ItemView skin(String status) {
        return new ItemView(ItemKind.OTHER, "waterskin", 1, false, 0, false, false, status, EquipSlot.NONE,
                List.of("DRINK", "DROP"), "DRINK");
    }

    private static Observation fight(int hp, String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(1, hp, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length())), items);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static boolean drinksSkin(Brain.Decided decided) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("DRINK")
                && use.item().name().equals("waterskin");
    }

    @Test
    @DisplayName("hurt beside a rat with a skin of 10 drops, the hero drinks the skin, where main only fought")
    void drinks_the_skin_in_a_fight() {
        Brain.Decided decided = decide(fight(6, "#@r...#", skin("10/20")));
        assertTrue(drinksSkin(decided), decided.action().toString());
        assertEquals("drink", decided.decision().policy());
        assertEquals("drink 6/20 +10 fight", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("the skin goes before a known potion of healing")
    void the_skin_before_the_potion() {
        Brain.Decided decided = decide(fight(3, "#@r...#", skin("12/20"), HealPolicyTest.potion("potion of healing", 1)));
        assertTrue(drinksSkin(decided), decided.action().toString());
    }

    @Test
    @DisplayName("an empty skin, a hero at full health and a drink too small to matter are left alone")
    void no_waste() {
        assertNotEquals("drink", decide(fight(6, "#@r...#", skin("0/20"))).decision().policy());
        assertNotEquals("drink", decide(fight(20, "#@r...#", skin("20/20"))).decision().policy());
        // 19 of 20 with a full skin heals 1 of 20, under the least share.
        assertNotEquals("drink", decide(fight(19, "#@r...#", skin("20/20"))).decision().policy());
        assertNotEquals("drink", decide(fight(17, "#@.....#", skin("20/20"))).decision().policy());
    }

    @Test
    @DisplayName("a healthy hero beside a rat keeps the skin")
    void healthy_fight_keeps_the_skin() {
        assertNotEquals("drink", decide(fight(15, "#@r...#", skin("20/20"))).decision().policy());
    }

    @Test
    @DisplayName("on a calm screen the hero drinks when under 60% and not above it")
    void calm_drink() {
        Brain.Decided hurt = decide(fight(8, "#@.....#", skin("10/20")));
        assertTrue(drinksSkin(hurt), hurt.action().toString());
        assertEquals("drink 8/20 +10 calm", hurt.decision().chosen().why());
        assertNotEquals("drink", decide(fight(13, "#@.....#", skin("10/20"))).decision().policy());
    }

    @Test
    @DisplayName("the gain is the drops needed, at least one, at most the skin holds, 5% of the maximum each")
    void the_gain() {
        assertEquals(10, Dew.gain(fight(6, "#@.....#", skin("10/20"))));
        assertEquals(14, Dew.gain(fight(6, "#@.....#", skin("20/20"))));
        assertEquals(1, Dew.gain(fight(19, "#@.....#", skin("20/20"))));
        assertEquals(0, Dew.gain(fight(20, "#@.....#", skin("20/20"))));
        assertEquals(0, Dew.gain(fight(6, "#@.....#", skin("0/20"))));
    }

    @Test
    @DisplayName("on the stairs of a spent floor, a hero a little hurt drinks the skin instead of resting")
    void descent_drink() {
        Observation on = DescendPolicyTest.hero(ExplorePolicyTest.screen(2, "#######", "#..E..#", "#######"), 16, 20,
                Hunger.NONE, skin("10/20"));
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
        Brain.Decided decided = brain.decide(on, DescendPolicyTest.spent(2, 10, 1, 0).belief());
        assertTrue(drinksSkin(decided), decided.action().toString());
        assertEquals("drink: descent +4", decided.decision().chosen().why());
    }
}
