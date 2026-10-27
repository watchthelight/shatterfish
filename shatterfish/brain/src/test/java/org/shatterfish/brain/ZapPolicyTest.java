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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zap Policy (story 7.6): the Mage zaps the staff of magic missile it wears at an awake enemy in
 * view, as far as its class's {@code kit.zap_reach} says, and a class whose reach is zero never does.
 */
class ZapPolicyTest {

    /** A Playbook in which only the Mage zaps, to {@code reach}. */
    private static Playbook playbook(int reach) {
        Playbook base = Playbook.reference("shatterfish");
        return new Playbook("shatterfish", 2, base.identify(), base.upgrade(), base.kit(), List.of(new Playbook.ClassEntry(
                HeroClass.MAGE, base.identify(), base.kit().with(Map.of("zap_reach", (long) reach)), base.upgrade())));
    }

    /** The staff as the inventory shows it: worn, named for its wand, its charges on the status. */
    private static ItemView staff(String name, String status) {
        return new ItemView(ItemKind.WEAPON, name, 1, true, 0, true, false, status, EquipSlot.WEAPON,
                List.of("IMBUE", "ZAP"), "ZAP");
    }

    /** The screen with the hero as {@code heroClass} wearing {@code worn}. */
    private static Observation screen(HeroClass heroClass, String row, ItemView worn) {
        Observation base = FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE,
                "#".repeat(row.length()), row, "#".repeat(row.length()));
        HeaderSection was = base.header();
        HeaderSection header = new HeaderSection(was.version(), was.upstreamTag(), was.codexVersion(), heroClass,
                was.challenges(), was.depth(), was.branch(), was.sealed(), was.oracle(), was.prompt());
        Observation bare = new Observation(header, base.map(), base.actors(), base.hero(),
                new org.shatterfish.api.InventorySection(List.of(worn)), base.journal(),
                base.log(), org.shatterfish.api.ActionsSection.NONE, base.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Playbook playbook, Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, playbook, 9L, Brain.Model.BASELINE);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    private static final String MISSILE = "staff of magic missile";

    @Test
    @DisplayName("a Mage whose class has a reach zaps the nearest awake enemy in view with the staff")
    void zaps_an_approaching_enemy() {
        Observation screen = screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "3/3"));
        Brain.Decided decided = decide(playbook(6), screen);
        assertEquals("zap", decided.decision().policy());
        assertEquals("zap: staff", decided.decision().goal());
        assertEquals("zap: " + MISSILE, decided.decision().chosen().why());
        Action.UseItemAt zap = (Action.UseItemAt) decided.action();
        assertEquals("ZAP", zap.action());
        assertEquals(screen.actors().actors().get(0).cell(), zap.cell());
        assertEquals(MISSILE, zap.item().name());
    }

    @Test
    @DisplayName("the reach is the playbook's: beside the enemy it zaps, past the reach it does not")
    void the_reach() {
        assertEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@r....#", staff(MISSILE, "3/3"))).decision().policy());
        assertEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@.....r#", staff(MISSILE, "3/3"))).decision().policy());
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@......r#", staff(MISSILE, "3/3"))).decision().policy());
        assertNotEquals("zap", decide(playbook(2), screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "3/3"))).decision().policy());
    }

    @Test
    @DisplayName("reach zero is the Brain before the story: the base, a class without an entry and a Mage with 0 never zap")
    void zero_is_never() {
        Observation mage = screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "3/3"));
        assertNotEquals("zap", decide(playbook(0), mage).decision().policy());
        assertNotEquals("zap", decide(Playbook.reference("shatterfish"), mage).decision().policy());
        Observation warrior = screen(HeroClass.WARRIOR, "#@...r.#", staff(MISSILE, "3/3"));
        assertNotEquals("zap", decide(playbook(6), warrior).decision().policy(), "the entry is the Mage's alone");
    }

    @Test
    @DisplayName("no charge, an unknown charge, another wand, an unworn staff and a staff that cannot zap are left to the swing")
    void only_a_charged_staff_of_magic_missile() {
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "0/3"))).decision().policy());
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "?/3"))).decision().policy());
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, ""))).decision().policy());
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@...r.#", staff("staff of firebolt", "2/2"))).decision().policy());
        ItemView carried = new ItemView(ItemKind.WEAPON, MISSILE, 1, true, 0, true, false, "3/3", EquipSlot.NONE,
                List.of("IMBUE", "ZAP"), "ZAP");
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@...r.#", carried)).decision().policy());
        assertTrue(Zap.charged("1/3"));
        assertFalse(Zap.charged("0/3"));
    }

    @Test
    @DisplayName("a sleeper is left asleep, a wall on the line stops the bolt, and a Goo in view is the fight Policy's to dodge")
    void the_conditions() {
        Observation sleeping = HealPolicyTest.asleep(screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "3/3")));
        assertNotEquals("zap", decide(playbook(6), sleeping).decision().policy());
        assertNotEquals("zap", decide(playbook(6), screen(HeroClass.MAGE, "#@.#.r.#", staff(MISSILE, "3/3"))).decision().policy());
        assertNotEquals("zap", decide(playbook(6), HealPolicyTest.named(screen(HeroClass.MAGE, "#@...r.#", staff(MISSILE, "3/3")), "Goo"))
                .decision().policy());
    }
}
