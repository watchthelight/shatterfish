package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Burning (issue #219, step 6). Burning ends only on water (Burning.java:98-99) and is renewed by the
 * fire a burning hero seeds on flammable ground (Burning.java:171-173), so a burning hero walks to
 * water close by, else off the grass, and never rests, picks up or tests while it stands on burning
 * ground. Mystery meat sets the hero alight one time in five (MysteryMeat.java:54-59), so it is
 * eaten on water when water is in sight.
 */
class DouseTest {

    private static final List<BuffView> BURNING = List.of(new BuffView(TestItem.BURNING, true, 800));

    private static List<Tile> row(Tile... tiles) {
        return List.of(tiles);
    }

    /** A one-row floor with the hero on {@code hero}, hunger {@code hunger}, holding {@code items}, enemies {@code actors}. */
    private static Observation floor(List<Tile> tiles, int hero, boolean burning, Hunger hunger, List<ItemView> items,
                                     List<ActorView> actors) {
        HeroSection base = Screens.heroAt(hero, 20, 20, burning ? BURNING : List.of());
        HeroSection hungry = new HeroSection(base.cell(), base.name(), base.subclass(), base.ability(), base.level(),
                base.exp(), base.expToLevel(), base.hp(), base.ht(), base.shield(), base.strength(),
                base.strengthBonus(), base.gold(), base.energy(), hunger, base.buffs(), base.talents(),
                base.talentPointsAvailable(), base.quickslots());
        Observation lab = Screens.lab(1, tiles.size(), tiles, List.of(), hungry, items, actors, List.of());
        Observation bare = new Observation(lab.header(), lab.map(), lab.actors(), lab.hero(),
                new InventorySection(items), lab.journal(), lab.log(), ActionsSection.NONE, lab.prompt());
        return bare.withActions(ValidActions.of(bare));
    }

    private static Brain.Decided decide(Observation screen) {
        Brain brain = new Brain(FightPolicyTest.KNOWLEDGE, Screens.WEIGHTS, 9L);
        Brain.Decided decided = brain.decide(screen, brain.update(screen, null));
        assertTrue(screen.actions().actions().contains(decided.action()), "offered: " + decided.action());
        return decided;
    }

    @Test
    @DisplayName("burning on grass with no water near: off the grass, toward the nearest bare ground")
    void off_the_grass() {
        Observation screen = floor(row(Tile.EMPTY, Tile.GRASS, Tile.GRASS, Tile.GRASS, Tile.GRASS, Tile.GRASS), 3, true,
                Hunger.NONE, List.of(), List.of());
        Brain.Decided decided = decide(screen);
        assertEquals("douse", decided.decision().policy());
        assertEquals(new Action.Step(2), decided.action());
        assertEquals("douse: bare ground", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("burning with water in a few steps: toward the water, even past bare ground")
    void to_the_water() {
        Observation screen = floor(row(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.WATER), 2, true,
                Hunger.NONE, List.of(), List.of());
        Brain.Decided decided = decide(screen);
        assertEquals("douse", decided.decision().policy());
        assertEquals(new Action.Step(3), decided.action());
        assertEquals("douse: water", decided.decision().chosen().why());
    }

    @Test
    @DisplayName("burning on bare ground with the water too far: the Policy stands aside")
    void stands_aside() {
        List<Tile> far = new ArrayList<>(java.util.Collections.nCopies(12, Tile.EMPTY));
        far.set(11, Tile.WATER);
        Observation screen = floor(far, 1, true, Hunger.NONE, List.of(), List.of());
        Douse douse = new Douse(FightPolicyTest.KNOWLEDGE);
        assertTrue(douse.enters(screen, Memory.START), "burning and off water");
        assertEquals(null, douse.choose(screen, Memory.START, screen.actions().actions(), null),
                "but the water is beyond " + Douse.REACH + " steps and the ground does not burn");
        assertNotEquals("douse", decide(screen).decision().policy());
    }

    @Test
    @DisplayName("not burning on grass, or burning on water, or burning with an enemy beside: no douse")
    void other_screens() {
        Douse douse = new Douse(FightPolicyTest.KNOWLEDGE);
        List<Tile> grass = row(Tile.EMPTY, Tile.GRASS, Tile.GRASS, Tile.GRASS);
        assertTrue(!douse.enters(floor(grass, 2, false, Hunger.NONE, List.of(), List.of()), Memory.START));
        assertTrue(!douse.enters(floor(row(Tile.EMPTY, Tile.WATER, Tile.GRASS), 1, true, Hunger.NONE, List.of(),
                List.of()), Memory.START));
        assertTrue(!douse.enters(floor(grass, 2, true, Hunger.NONE, List.of(),
                List.of(Screens.enemy("marsupial rat", 3))), Memory.START), "the fight Policy goes first");
    }

    private static ItemView meat(String name) {
        return EatPolicyTest.food(name, 1);
    }

    @Test
    @DisplayName("starving with only mystery meat: walk to the water to eat it, and eat it on the water")
    void meat_on_the_water() {
        List<Tile> tiles = row(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.WATER);
        Brain.Decided walking = decide(floor(tiles, 1, false, Hunger.STARVING, List.of(meat("mystery meat")), List.of()));
        assertEquals("eat", walking.decision().policy());
        assertEquals(new Action.Step(2), walking.action());
        assertEquals("eat: water", walking.decision().chosen().why());

        Brain.Decided eating = decide(floor(tiles, 4, false, Hunger.STARVING, List.of(meat("mystery meat")), List.of()));
        assertEquals("eat: mystery meat", eating.decision().chosen().why());
    }

    @Test
    @DisplayName("starving with only mystery meat and no water in sight: eat it where it stands; with other food, no walk")
    void meat_without_water() {
        List<Tile> dry = row(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY);
        assertEquals("eat: mystery meat",
                decide(floor(dry, 1, false, Hunger.STARVING, List.of(meat("mystery meat")), List.of())).decision().chosen().why());
        List<Tile> wet = row(Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.WATER);
        assertEquals("eat: dungeon berry", decide(floor(wet, 1, false, Hunger.STARVING,
                List.of(meat("mystery meat"), meat("dungeon berry")), List.of())).decision().chosen().why());
    }

    @Test
    @DisplayName("a levitating hero is not put out by water: it does not walk to it, but still leaves the grass")
    void levitating_skips_the_water() {
        List<BuffView> buffs = List.of(new BuffView(TestItem.BURNING, true, 800), new BuffView(SafeTest.LEVITATING, true, 10));
        List<Tile> tiles = row(Tile.EMPTY, Tile.GRASS, Tile.GRASS, Tile.GRASS, Tile.WATER);
        Observation base = floor(tiles, 3, true, Hunger.NONE, List.of(), List.of());
        HeroSection hero = base.hero();
        HeroSection flying = new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(),
                hero.exp(), hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(),
                hero.strengthBonus(), hero.gold(), hero.energy(), hero.hunger(), buffs, hero.talents(),
                hero.talentPointsAvailable(), hero.quickslots());
        Observation bare = new Observation(base.header(), base.map(), base.actors(), flying, base.inventory(),
                base.journal(), base.log(), ActionsSection.NONE, base.prompt());
        Observation screen = bare.withActions(ValidActions.of(bare));
        Brain.Decided decided = decide(screen);
        assertEquals("douse", decided.decision().policy());
        assertEquals("douse: bare ground", decided.decision().chosen().why());
        assertEquals(new Action.Step(2), decided.action());
    }
}
