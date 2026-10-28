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
import org.shatterfish.api.ValidActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shoot Policy (issue #309): a hero whose pack holds the Spirit Bow shoots the nearest awake enemy
 * in view that is not adjacent, while the fight is one it would take.
 */
class ShootPolicyTest {

    /** The bow as the inventory lists it: in the pack, never worn, its default action SHOOT. */
    private static ItemView bow() {
        return new ItemView(ItemKind.WEAPON, "spirit bow", 1, true, 0, true, false, "", EquipSlot.NONE,
                List.of("DROP", "THROW", "SHOOT"), "SHOOT");
    }

    private static Observation screen(String row, ItemView... items) {
        Observation base = FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length()));
        List<ItemView> pack = new java.util.ArrayList<>(base.inventory().items());
        pack.addAll(List.of(items));
        HeaderSection was = base.header();
        HeaderSection header = new HeaderSection(was.version(), was.upstreamTag(), was.codexVersion(),
                HeroClass.HUNTRESS, was.challenges(), was.depth(), was.branch(), was.sealed(), was.oracle(),
                was.prompt());
        Observation bare = new Observation(header, base.map(), base.actors(), base.hero(),
                new org.shatterfish.api.InventorySection(pack), base.journal(), base.log(),
                org.shatterfish.api.ActionsSection.NONE, base.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    @Test
    @DisplayName("a rat four cells off and the bow in the pack: the hero shoots it")
    void shoots_an_approaching_enemy() {
        Observation screen = screen("#@...r.#", bow());
        Brain.Decided decided = decide(screen);
        assertEquals("shoot", decided.decision().policy());
        assertEquals("shoot: bow", decided.decision().goal());
        assertEquals("shoot: spirit bow", decided.decision().chosen().why());
        Action.UseItemAt shot = (Action.UseItemAt) decided.action();
        assertEquals("SHOOT", shot.action());
        assertEquals(screen.actors().actors().get(0).cell(), shot.cell());
    }

    @Test
    @DisplayName("beside the enemy the hero fights, and with no bow it does not shoot")
    void not_beside_and_not_without_a_bow() {
        assertNotEquals("shoot", decide(screen("#@r....#", bow())).decision().policy());
        assertNotEquals("shoot", decide(screen("#@...r.#")).decision().policy());
    }

    @Test
    @DisplayName("a sleeper is left asleep, a wall on the line stops the arrow, and a Goo in view is the fight Policy's")
    void the_conditions() {
        assertNotEquals("shoot", decide(HealPolicyTest.asleep(screen("#@...r.#", bow()))).decision().policy());
        assertNotEquals("shoot", decide(screen("#@.#.r.#", bow())).decision().policy());
        assertNotEquals("shoot", decide(HealPolicyTest.named(screen("#@...r.#", bow()), "Goo")).decision().policy());
    }
}
