package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.Observation;
import org.shatterfish.api.TalentView;
import org.shatterfish.api.ValidActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The talents Policy (issue #219): a point to spend is spent, on the Warrior's talents in their order,
 * and a hero with no point or no talent of the list is left to the other Policies.
 */
class TalentsPolicyTest {

    private static final String[] ROOM = {"#####", "#.@.#", "#####"};

    private static List<TalentView> tierOne(int ironWill, int provokedAnger, int heartyMeal, int veterans) {
        return List.of(new TalentView(1, "Iron Will", ironWill), new TalentView(1, "Provoked Anger", provokedAnger),
                new TalentView(1, "Hearty Meal", heartyMeal), new TalentView(1, "Veteran's Intuition", veterans));
    }

    /** {@code screen} with the hero holding {@code talents} and {@code points} to spend per tier. */
    private static Observation with(Observation screen, List<TalentView> talents, List<Integer> points) {
        HeroSection hero = screen.hero();
        HeroSection changed = new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(),
                hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(), hero.strengthBonus(),
                hero.gold(), hero.energy(), hero.hunger(), hero.buffs(), talents, points, hero.quickslots());
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), changed, screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Observation room(List<TalentView> talents, List<Integer> points) {
        return with(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.NONE, ROOM), talents, points);
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Belief belief = brain.update(screen, null);
        Brain.Decided decided = brain.decide(screen, belief);
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    @Test
    @DisplayName("a hero with a first-tier point puts it in Iron Will")
    void spends_a_point() {
        Brain.Decided decided = decide(room(tierOne(0, 0, 0, 0), List.of(1, 0, 0, 0)));
        assertEquals("talents", decided.decision().policy());
        assertEquals(new Action.Talent("Iron Will"), decided.action());
        assertEquals("talents: spend", decided.decision().goal());
        assertEquals("talent: Iron Will", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("a talent holding its two points passes the next point down the order")
    void follows_the_order() {
        assertEquals(new Action.Talent("Provoked Anger"),
                decide(room(tierOne(2, 0, 0, 0), List.of(3, 0, 0, 0))).action());
        assertEquals(new Action.Talent("Hearty Meal"),
                decide(room(tierOne(2, 2, 0, 0), List.of(1, 0, 0, 0))).action());
        assertEquals(new Action.Talent("Veteran's Intuition"),
                decide(room(tierOne(2, 2, 2, 0), List.of(1, 0, 0, 0))).action());
    }

    @Test
    @DisplayName("with no point to spend the Policy does not enter")
    void no_point_no_talent() {
        Observation screen = room(tierOne(0, 0, 0, 0), List.of(0, 0, 0, 0));
        assertFalse(new Talents().enters(screen, Memory.START));
        assertNotEquals("talents", decide(screen).decision().policy());
    }

    @Test
    @DisplayName("a point in a tier the list does not name is left to the other Policies")
    void another_classes_talents_are_left_alone() {
        List<TalentView> mage = List.of(new TalentView(1, "Energizing Meal", 0), new TalentView(1, "Scholar's Intuition", 0));
        Observation screen = room(mage, List.of(1, 0, 0, 0));
        assertFalse(new Talents().enters(screen, Memory.START));
        assertNotEquals("talents", decide(screen).decision().policy());
    }

    @Test
    @DisplayName("a point is spent with an enemy in view: it costs no turn")
    void spent_in_a_fight() {
        Observation screen = with(FightPolicyTest.screen(1, 20, true, "worn shortsword", Hunger.NONE,
                "#######", "#.@.r.#", "#######"), tierOne(0, 0, 0, 0), List.of(1, 0, 0, 0));
        assertEquals("talents", decide(screen).decision().policy());
    }
}
