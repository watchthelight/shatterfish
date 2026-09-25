package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Belief;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eat Policy (story 4.9): it eats at the hungry icon a food the hunger takes whole, at the
 * starving icon the food that wastes least, never at no icon, never with an enemy in view, and never
 * a food it does not know.
 */
class EatPolicyTest {

    /** A known room with the hero in it and nothing to explore beyond. */
    private static final String[] ROOM = {"#####", "#.@.#", "#####"};

    /** A food the inventory lists, with the actions its window offers (Food.java:64-67). */
    static ItemView food(String name, int quantity) {
        return new ItemView(ItemKind.FOOD, name, quantity, true, 0, true, false, "", EquipSlot.NONE,
                List.of("DROP", "EAT", "THROW"), "EAT");
    }

    /** {@code screen} with {@code items} in the pack as well, and its Actions worked out again. */
    static Observation holding(Observation screen, ItemView... items) {
        List<ItemView> pack = new ArrayList<>(screen.inventory().items());
        pack.addAll(List.of(items));
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), screen.hero(),
                new InventorySection(pack), screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    /** {@code screen} with the hero's hunger icon at {@code hunger}. */
    static Observation hungry(Observation screen, Hunger hunger) {
        HeroSection hero = screen.hero();
        HeroSection changed = new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(),
                hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(), hero.strengthBonus(),
                hero.gold(), hero.energy(), hunger, hero.buffs(), hero.talents(), hero.talentPointsAvailable(),
                hero.quickslots());
        Observation bare = new Observation(screen.header(), screen.map(), screen.actors(), changed, screen.inventory(),
                screen.journal(), screen.log(), ActionsSection.NONE, screen.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    /** The room, the hero at full health and {@code hunger}, holding {@code items}. */
    private static Observation room(Hunger hunger, ItemView... items) {
        return holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", hunger, ROOM), items);
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

    /** The name of the item {@code decided} eats, or null when it eats nothing. */
    private static String eaten(Brain.Decided decided) {
        return decided.action() instanceof Action.UseItem use && use.action().equals("EAT") ? use.item().name() : null;
    }

    @Test
    @DisplayName("at the hungry icon, holding a ration, the hero eats it")
    void hungry_eats() {
        Brain.Decided decided = decide(room(Hunger.HUNGRY, food("ration of food", 2)));
        assertEquals("eat", decided.decision().policy());
        assertEquals("ration of food", eaten(decided));
        assertEquals("eat: ration of food", decided.decision().chosen().why());
        assertEquals("eat: hunger", decided.decision().goal());
    }

    @Test
    @DisplayName("with no hunger icon the hero never eats, whatever it holds")
    void fed_never_eats() {
        Brain.Decided decided = decide(room(Hunger.NONE, food("ration of food", 1), food("dungeon berry", 3)));
        assertNotEquals("eat", decided.decision().policy());
        assertEquals(null, eaten(decided));
        for (var alternative : decided.decision().alternatives()) {
            assertFalse(alternative.action() instanceof Action.UseItem use && use.action().equals("EAT"),
                    "not even as an alternative: " + alternative);
        }
    }

    @Test
    @DisplayName("the icon gates the Policy twice: it does not enter at no icon, and ranks nothing there if asked")
    void the_icon_gates_both() {
        Eat eat = new Eat();
        Observation fed = room(Hunger.NONE, food("ration of food", 1));
        Observation hungry = room(Hunger.HUNGRY, food("ration of food", 1));
        assertFalse(eat.enters(fed, Memory.START));
        assertTrue(eat.enters(hungry, Memory.START));
        assertTrue(eat.ranked(fed, Memory.START, fed.actions().actions(), Stream.at(1L, 0)).isEmpty(),
                "asked anyway, it would eat nothing at no icon");
        assertEquals(1, eat.ranked(hungry, Memory.START, hungry.actions().actions(), Stream.at(1L, 0)).size());
    }

    @Test
    @DisplayName("hungry, the hero eats the largest food the hunger takes whole, and keeps a pasty for starving")
    void no_waste_when_hungry() {
        Brain.Decided decided = decide(room(Hunger.HUNGRY, food("pasty", 1), food("dungeon berry", 2),
                food("small food ration", 1), food("meat pie", 1)));
        assertEquals("small food ration", eaten(decided), "150 is the largest at most 300");
        Brain.Decided pastyOnly = decide(room(Hunger.HUNGRY, food("pasty", 1), food("meat pie", 1)));
        assertNotEquals("eat", pastyOnly.decision().policy(), "a pasty at hungry would lose up to 150");
        assertEquals(null, eaten(pastyOnly));
    }

    @Test
    @DisplayName("starving, the hero eats the largest food that wastes nothing, and a meat pie last")
    void starving_prefers_the_largest_that_fits() {
        Brain.Decided decided = decide(room(Hunger.STARVING, food("ration of food", 1), food("meat pie", 1),
                food("pasty", 1)));
        assertEquals("pasty", eaten(decided), "450 fits exactly");
        // The eat Policy's own next ranks come first among the alternatives; later Policies' follow.
        List<String> alternatives = decided.decision().alternatives().stream()
                .filter(choice -> choice.action() instanceof Action.UseItem).limit(2)
                .map(choice -> ((Action.UseItem) choice.action()).item().name()).toList();
        assertEquals(List.of("ration of food", "meat pie"), alternatives, "then the ration, and the pie wastes 450");
    }

    @Test
    @DisplayName("mystery meat is eaten only while starving, and only when nothing else is held")
    void mystery_meat_last() {
        assertEquals(null, eaten(decide(room(Hunger.HUNGRY, food("mystery meat", 1)))));
        assertEquals("dungeon berry", eaten(decide(room(Hunger.STARVING, food("mystery meat", 1), food("dungeon berry", 1)))));
        assertEquals("mystery meat", eaten(decide(room(Hunger.STARVING, food("mystery meat", 1)))));
    }

    @Test
    @DisplayName("a raw blandfruit, or a food the table does not know, is never eaten")
    void never_unknown_food() {
        assertEquals(null, eaten(decide(room(Hunger.STARVING, food("blandfruit", 1)))));
        assertEquals(null, eaten(decide(room(Hunger.STARVING, food("sorrowfruit", 1)))));
        // A ration's name on something that is not food is not food.
        ItemView notFood = new ItemView(ItemKind.OTHER, "ration of food", 1, true, 0, true, false, "", EquipSlot.NONE,
                List.of("DROP", "EAT"), "");
        assertEquals(null, eaten(decide(room(Hunger.STARVING, notFood))));
    }

    @Test
    @DisplayName("with an enemy in view the fight Policy takes the wait, not eating")
    void never_under_attack() {
        Observation rat = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#####", "#.@r#", "#####"), food("ration of food", 1));
        Brain.Decided decided = decide(rat);
        assertEquals("fight", decided.decision().policy());
    }

    @Test
    @DisplayName("starving with an enemy in view that is not beside it, the Policy enters; beside it, or hungry, it does not")
    void starving_with_an_enemy_far() {
        Eat eat = new Eat();
        Observation far = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#######", "#@...L#", "#######"), food("ration of food", 1));
        Observation near = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#######", "#@L...#", "#######"), food("ration of food", 1));
        Observation hungry = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.HUNGRY,
                "#######", "#@...L#", "#######"), food("ration of food", 1));
        assertTrue(eat.enters(far, Memory.START));
        assertFalse(eat.enters(near, Memory.START), "beside the hero, the meal's three turns are free hits");
        assertFalse(eat.enters(hungry, Memory.START), "hungry, it waits for a calm screen");
        // Beside the hero counts even on a screen that offers no Attack on it (a Prompt just closed,
        // an Action set a wait old): both halves of the test stand on their own.
        Observation unoffered = near.withActions(new ActionsSection(near.actions().actions().stream()
                .filter(action -> !(action instanceof Action.Attack)).toList()));
        assertTrue(Eat.pressed(unoffered, Memory.START));
        assertFalse(Eat.pressed(far, Memory.START));
    }

    @Test
    @DisplayName("an awake enemy a few steps off, or one that shoots, presses; asleep, far, or a passive statue, not")
    void what_presses() {
        Observation twoOff = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "########", "#@..r..#", "########"), food("ration of food", 1));
        Observation fourOff = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "########", "#@...r.#", "########"), food("ration of food", 1));
        assertTrue(Eat.pressed(twoOff, Memory.START), "three steps from the hero");
        assertFalse(Eat.pressed(HealPolicyTest.asleep(twoOff), Memory.START), "asleep");
        assertFalse(Eat.pressed(fourOff, Memory.START), "four steps off");
        assertTrue(Eat.pressed(HealPolicyTest.named(fourOff, "evil eye"), Memory.START), "an eye shoots from there");
        Observation walled = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#######", "#@.#r.#", "#######"), food("ration of food", 1));
        assertFalse(Eat.pressed(walled, Memory.START), "no way round the wall");
        Observation statue = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#######", "#@T...#", "#######"), food("ration of food", 1));
        assertTrue(statue.actions().actions().contains(new Action.Attack(statue.actors().actors().get(0).cell())), "the screen offers an attack on the statue");
        assertFalse(Eat.pressed(statue, Memory.START), "an attack on a passive statue is no pressure");
        assertTrue(new Eat().enters(statue, Memory.START));
    }

    @Test
    @DisplayName("over several waits: starving before a lasher that never comes, the hero holds, then eats")
    void starving_before_an_immovable_enemy() {
        Observation screen = holding(FightPolicyTest.screen(1, 20, false, "worn shortsword", Hunger.STARVING,
                "#######", "#@...L#", "#######"), food("ration of food", 1));
        Brain brain = brain();
        Belief belief = null;
        List<String> policies = new ArrayList<>();
        String ate = null;
        for (int i = 0; i < Fight.HOLDS + 2 && ate == null; i++) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            policies.add(decided.decision().policy());
            ate = eaten(decided);
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals("ration of food", ate, "the hero eats before it starves: " + policies);
        assertEquals("eat", policies.get(policies.size() - 1));
    }

    @Test
    @DisplayName("over several waits: the hero eats berries until the icon clears, then walks on")
    void eats_until_fed() {
        Observation first = room(Hunger.HUNGRY, food("dungeon berry", 3));
        Observation second = room(Hunger.HUNGRY, food("dungeon berry", 2));
        Observation fed = room(Hunger.NONE, food("dungeon berry", 1));
        Brain brain = brain();
        Belief belief = null;
        List<String> eaten = new ArrayList<>();
        for (Observation screen : List.of(first, second, fed)) {
            belief = brain.update(screen, belief);
            Brain.Decided decided = brain.decide(screen, belief);
            eaten.add(eaten(decided));
            belief = brain.handed(screen, belief, decided);
        }
        assertEquals(java.util.Arrays.asList("dungeon berry", "dungeon berry", null), eaten);
        assertEquals(0, Memory.of(belief).streak(), "eating in place is not a refused Step");
    }

    @Test
    @DisplayName("the food table names every food once, with the game's energies")
    void the_table() {
        assertEquals(300, Eat.ENERGY.get("ration of food"));
        assertEquals(450, Eat.ENERGY.get("pasty"));
        assertEquals(150, Eat.ENERGY.get("mystery meat"));
        assertEquals(900, Eat.ENERGY.get("meat pie"));
        assertEquals(Eat.HUNGRY, 300);
        assertEquals(Eat.STARVING, 450);
        assertTrue(Eat.UNEATEN.stream().noneMatch(Eat.ENERGY::containsKey));
        assertEquals(Eat.ENERGY, Brain.foods());
        assertEquals(Eat.UNEATEN, Brain.uneaten());
    }
}
