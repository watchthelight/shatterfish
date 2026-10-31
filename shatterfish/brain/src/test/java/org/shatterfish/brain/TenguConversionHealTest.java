package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.ValidActions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drink against Tengu alone (story 10.21, issue #367). A potion of healing heals {@code 0.8 * HT + 14} in all, 62
 * for a hero of 60 (Heal.heals), so a drink at 35 of 60 wastes 37 of it at the cap, and the practice Runs drank at 15 to
 * 40 hit points (docs/results/e10-practice-kit.md). Tengu's blow is 6 to 12 and he never moves, so the hero can stand a
 * blow or two more before it drinks; the Playbook's {@code drink_per_mille} says how many.
 *
 * <p>The first test fails on {@code main}: there the heal Policy's danger against one Tengu in view is the generic
 * estimate (his expected damage over ten turns), which is more than the hero's 36 hit points here, so it drinks.
 */
class TenguConversionHealTest {

    private static final int BOSS = TenguFightTest.cell(6, 2);

    private static Brain brain(Playbook.Tengu entry) {
        Playbook reference = Playbook.reference("shatterfish");
        Playbook with = new Playbook(reference.name(), reference.version(), reference.identify(), reference.upgrade(),
                reference.kit(), reference.classes(),
                new Playbook.Bosses(reference.bosses().goo(), entry, reference.bosses().dm300()), reference.tunables());
        return new Brain(Screens.CODEX, Screens.WEIGHTS, with, 5L, Brain.Model.BASELINE);
    }

    /** The sealed arena, the hero beside Tengu at {@code hp} of 60 holding {@code potions} potions of healing. */
    private static Observation hurt(int hp, int potions) {
        Observation plain = TenguFightTest.withHero(TenguFightTest.arena(true, BOSS), TenguFightTest.cell(5, 2));
        HeroSection h = plain.hero();
        HeroSection hero = new HeroSection(h.cell(), h.name(), h.subclass(), h.ability(), h.level(), h.exp(), h.expToLevel(),
                hp, 60, h.shield(), h.strength(), h.strengthBonus(), h.gold(), h.energy(), h.hunger(), h.buffs(),
                h.talents(), h.talentPointsAvailable(), h.quickslots());
        Observation bare = new Observation(plain.header(), plain.map(), plain.actors(), hero, plain.inventory(),
                plain.journal(), plain.log(), ActionsSection.NONE, plain.prompt());
        return potions == 0 ? bare.withActions(ValidActions.of(bare))
                : EatPolicyTest.holding(bare, HealPolicyTest.potion(Heal.POTION, potions));
    }

    private static Brain.Decided decide(Brain brain, Observation screen) {
        Belief belief = brain.update(screen, null);
        return brain.decide(screen, belief);
    }

    private static boolean drinks(Brain.Decided decided) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("DRINK");
    }

    @Test
    @DisplayName("beside Tengu at 36 of 60 with a potion, the hero strikes: the drink waits for the Playbook's share")
    void holds_the_drink_until_the_share() {
        Brain brain = brain(Playbook.Tengu.REFERENCE.with(Map.of("drink_per_mille", 400L)));
        Brain.Decided high = decide(brain, hurt(36, 2));
        assertFalse(drinks(high), high.decision().toString());
        assertEquals(new Action.Attack(BOSS), high.action(), high.decision().toString());
    }

    @Test
    @DisplayName("at the share the hero drinks, and a share of a thousand drinks at once")
    void drinks_at_the_share() {
        Brain brain = brain(Playbook.Tengu.REFERENCE.with(Map.of("drink_per_mille", 400L)));
        assertTrue(drinks(decide(brain, hurt(24, 2))), "24 of 60 is 40%: at the share");
        assertTrue(drinks(decide(brain, hurt(9, 2))), "9 of 60: under it");
        Brain eager = brain(Playbook.Tengu.REFERENCE.with(Map.of("drink_per_mille", 1000L)));
        assertTrue(drinks(decide(eager, hurt(36, 2))), "a share of a thousand is every hurt turn");
    }

    @Test
    @DisplayName("with no potion the hero strikes at any health, and off the sealed floor the entry is not read")
    void controls() {
        Brain brain = brain(Playbook.Tengu.REFERENCE.with(Map.of("drink_per_mille", 400L)));
        Brain.Decided empty = decide(brain, hurt(9, 0));
        assertFalse(drinks(empty), empty.decision().toString());
        // Unsealed, the same screen is not the Tengu fight: the danger is the generic estimate whatever the entry says.
        Observation open = hurt(36, 2);
        HeaderSection h = open.header();
        HeaderSection header = new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(), h.challenges(),
                h.depth(), h.branch(), false, h.oracle(), h.prompt());
        Observation unsealed = new Observation(header, open.map(), open.actors(), open.hero(), open.inventory(), open.journal(),
                open.log(), ActionsSection.NONE, open.prompt());
        Observation off = unsealed.withActions(ValidActions.of(unsealed));
        assertEquals(Heal.danger(off, Screens.CODEX, Memory.START, Playbook.Goo.REFERENCE, Playbook.Tengu.REFERENCE,
                        org.shatterfish.api.Tunables.Vector.REFERENCE),
                Heal.danger(off, Screens.CODEX, Memory.START, Playbook.Goo.REFERENCE,
                        Playbook.Tengu.REFERENCE.with(Map.of("drink_per_mille", 1L)), org.shatterfish.api.Tunables.Vector.REFERENCE));
    }
}
