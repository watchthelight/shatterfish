package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.ValidActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunge Policy (issue #375): the Duelist lunges with the rapier it wears at an awake enemy exactly two cells
 * away, while the weapon shows a charge. The first test failed on {@code main}, where no Policy used the ability.
 */
class LungePolicyTest {

    /** The rapier as the inventory shows it: worn, its charges on the status, the ability among its actions. */
    private static ItemView weapon(String name, String status, List<String> actions) {
        return new ItemView(ItemKind.WEAPON, name, 1, true, 0, true, false, status, EquipSlot.WEAPON, actions, "ABILITY");
    }

    private static ItemView rapier(String status) {
        return weapon("rapier", status, List.of("ABILITY", "DROP", "THROW"));
    }

    /** The screen with the hero as a Duelist wearing {@code worn}. */
    private static Observation screen(String row, ItemView worn) {
        Observation base = FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length()));
        HeaderSection was = base.header();
        HeaderSection header = new HeaderSection(was.version(), was.upstreamTag(), was.codexVersion(), HeroClass.DUELIST,
                was.challenges(), was.depth(), was.branch(), was.sealed(), was.oracle(), was.prompt());
        Observation bare = new Observation(header, base.map(), base.actors(), base.hero(),
                new org.shatterfish.api.InventorySection(List.of(worn)), base.journal(),
                base.log(), org.shatterfish.api.ActionsSection.NONE, base.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, Playbook.reference("shatterfish"), 9L, Brain.Model.BASELINE);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    @Test
    @DisplayName("a Duelist lunges at an awake enemy two cells off while the rapier shows a charge")
    void lunges_at_two_cells() {
        Observation screen = screen("#@.r..#", rapier("2/2"));
        Brain.Decided decided = decide(screen);
        assertEquals("lunge", decided.decision().policy());
        assertEquals("lunge: rapier", decided.decision().goal());
        assertEquals("lunge: rapier", decided.decision().chosen().why());
        Action.UseItemAt lunge = (Action.UseItemAt) decided.action();
        assertEquals("ABILITY", lunge.action());
        assertEquals(screen.actors().actors().get(0).cell(), lunge.cell());
        assertEquals("rapier", lunge.item().name());
    }

    @Test
    @DisplayName("only at two cells: beside the enemy or farther off it is left to the other Policies")
    void only_at_two_cells() {
        assertNotEquals("lunge", decide(screen("#@r...#", rapier("2/2"))).decision().policy());
        assertNotEquals("lunge", decide(screen("#@..r.#", rapier("2/2"))).decision().policy());
    }

    @Test
    @DisplayName("no charge, an unknown charge, another weapon, an unworn rapier and a sleeper are left alone")
    void the_conditions() {
        assertNotEquals("lunge", decide(screen("#@.r..#", rapier("0/2"))).decision().policy());
        assertNotEquals("lunge", decide(screen("#@.r..#", rapier("?/2"))).decision().policy());
        assertNotEquals("lunge", decide(screen("#@.r..#", rapier(""))).decision().policy());
        assertNotEquals("lunge", decide(screen("#@.r..#", weapon("mace", "2/2", List.of("ABILITY", "DROP", "THROW")))).decision().policy());
        ItemView carried = new ItemView(ItemKind.WEAPON, "rapier", 1, true, 0, true, false, "2/2", EquipSlot.NONE,
                List.of("ABILITY", "DROP", "EQUIP"), "EQUIP");
        assertNotEquals("lunge", decide(screen("#@.r..#", carried)).decision().policy());
        assertNotEquals("lunge", decide(HealPolicyTest.asleep(screen("#@.r..#", rapier("2/2")))).decision().policy());
    }

    @Test
    @DisplayName("a wall that leaves no cell beside the enemy to land on leaves the lunge to the game's refusal: none is chosen")
    void no_landing_cell() {
        assertNotEquals("lunge", decide(screen("#@#r..#", rapier("2/2"))).decision().policy());
    }
}
