package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.BuffView;
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
 * The emergency scrolls (issue #411): a lost fight with no healing on hand reads the best known scroll that can end
 * it, which the heal Policy chooses beside the potion of healing.
 */
class EmergencyScrollTest {

    private static ItemView scroll(String name) {
        return new ItemView(ItemKind.SCROLL, name, 1, true, 0, true, false, "", EquipSlot.NONE, List.of("READ"), "");
    }

    /** The hero at {@code hp} of 20 on {@code row}, holding {@code items}. */
    private static Observation fight(int hp, boolean sealed, String row, ItemView... items) {
        return EatPolicyTest.holding(FightPolicyTest.screen(3, hp, sealed, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length())), items);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static boolean reads(Brain.Decided decided, String name) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("READ") && use.item().name().equals(name);
    }

    @Test
    @DisplayName("a known teleport is read at low health against two enemies, with no healing on hand")
    void teleport_at_low_health() {
        Brain.Decided decided = decide(fight(4, false, "#r@B..#", scroll("scroll of teleportation")));
        assertEquals("heal", decided.decision().policy());
        assertTrue(reads(decided, "scroll of teleportation"), decided.action().toString());
        assertTrue(decided.decision().chosen().why().endsWith("(scroll of teleportation)"), decided.decision().chosen().why());
    }

    @Test
    @DisplayName("the best of the four goes first: teleportation, terror, mirror image, lullaby")
    void best_first() {
        ItemView[] pack = {scroll("scroll of lullaby"), scroll("scroll of mirror image"), scroll("scroll of terror"),
                scroll("scroll of teleportation")};
        assertTrue(reads(decide(fight(4, false, "#r@B..#", pack)), "scroll of teleportation"));
        assertTrue(reads(decide(fight(4, false, "#r@B..#", pack[0], pack[1], pack[2])), "scroll of terror"));
        assertTrue(reads(decide(fight(4, false, "#r@B..#", pack[0], pack[1])), "scroll of mirror image"));
        assertTrue(reads(decide(fight(4, false, "#r@B..#", pack[0])), "scroll of lullaby"));
    }

    @Test
    @DisplayName("a healing potion goes before any scroll; a won fight, a sealed boss floor for terror and lullaby, and a refused read are no emergency")
    void when_not() {
        Brain.Decided potion = decide(fight(4, false, "#r@B..#", scroll("scroll of teleportation"),
                HealPolicyTest.potion("potion of healing", 1)));
        assertTrue(potion.action() instanceof Action.UseItem use && use.action().equals("DRINK"), potion.action().toString());
        assertNotEquals("heal", decide(fight(18, false, "#@r...#", scroll("scroll of teleportation"))).decision().policy(),
                "a rat at 18 of 20 is a fight to win");
        Brain.Decided sealed = decide(fight(4, true, "#r@B..#", scroll("scroll of terror"), scroll("scroll of lullaby")));
        assertTrue(!reads(sealed, "scroll of terror") && !reads(sealed, "scroll of lullaby"), sealed.action().toString());
        assertTrue(reads(decide(fight(4, true, "#r@B..#", scroll("scroll of teleportation"))), "scroll of teleportation"),
                "a teleport works on a sealed floor (ScrollOfTeleportation.java:101, :145)");
        Observation blind = EatPolicyTest.holding(fight(4, false, "#r@B..#"), scroll("scroll of teleportation"));
        org.shatterfish.api.HeroSection hero = blind.hero();
        org.shatterfish.api.HeroSection blinded = new org.shatterfish.api.HeroSection(hero.cell(), hero.name(), hero.subclass(),
                hero.ability(), hero.level(), hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(),
                hero.strength(), hero.strengthBonus(), hero.gold(), hero.energy(), hero.hunger(),
                List.of(new BuffView(TestItem.BLINDED, false, 0)), hero.talents(), hero.talentPointsAvailable(), hero.quickslots());
        Observation refused = new Observation(blind.header(), blind.map(), blind.actors(), blinded, blind.inventory(),
                blind.journal(), blind.log(), blind.actions(), blind.prompt());
        assertTrue(!reads(decide(refused), "scroll of teleportation"), "blinded: the game refuses the read");
    }
}
