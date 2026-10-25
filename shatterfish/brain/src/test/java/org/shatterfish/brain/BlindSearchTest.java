package org.shatterfish.brain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A search that could not reach the cells the Brain credits it with (issue #205). A blinded or shadowmelded hero
 * sees only the cells beside it ({@code Level.java:1332} leaves the field of view empty, and
 * {@code Level.java:1400-1421} fills in a radius of one), and {@code Hero.search} finds secrets only on cells in
 * the field of view ({@code Hero.java:2562}). The Rogue's search reaches two cells ({@code Hero.java:2522}), so
 * a blind Rogue's search covers the ring beside it and no more; a Warrior's reaches one cell and is whole.
 */
class BlindSearchTest {

    private static final String[] ROOM = {
        "#######",
        "#@....#",
        "#.....#",
        "#######",
    };

    private static Observation room(HeroClass heroClass, boolean blind, String buff) {
        Observation base = ExplorePolicyTest.screen(2, false, heroClass, ROOM);
        MapSection map = base.map();
        List<Fog> fog = new ArrayList<>(map.fog());
        int width = map.width();
        int hero = base.hero().cell();
        if (blind) {
            for (int cell = 0; cell < fog.size(); cell++) {
                boolean near = Math.max(Math.abs(cell % width - hero % width), Math.abs(cell / width - hero / width)) <= 1;
                fog.set(cell, near ? Fog.VISIBLE : Fog.VISITED);
            }
        }
        MapSection seen = new MapSection(width, map.height(), map.tiles(), fog, map.traps(), map.heaps(), map.blobs(),
                map.feeling(), map.transitions());
        HeroSection was = base.hero();
        List<BuffView> buffs = buff == null ? List.of() : List.of(new BuffView(buff, true, 500));
        HeroSection hero2 = new HeroSection(was.cell(), was.name(), was.subclass(), was.ability(), was.level(), was.exp(),
                was.expToLevel(), was.hp(), was.ht(), was.shield(), was.strength(), was.strengthBonus(), was.gold(),
                was.energy(), was.hunger(), buffs, was.talents(), was.talentPointsAvailable(), was.quickslots());
        return new Observation(base.header(), seen, base.actors(), hero2, base.inventory(), base.journal(), base.log(),
                base.actions(), base.prompt());
    }

    private static List<Memory.Spot> searched(Observation screen) {
        return Memory.of(Screens.drive(new Brain(Screens.CODEX, Screens.WEIGHTS, 5L), screen, screen)).dwelt();
    }

    @Test
    @DisplayName("a Rogue who searched while blinded has not covered the two cells around it")
    void blind_rogue() {
        Observation blind = room(HeroClass.ROGUE, true, "blinded");
        assertEquals(List.of(), searched(blind));
    }

    @Test
    @DisplayName("a Rogue who searched while shadowmelded has not covered the two cells around it")
    void shadowmelded_rogue() {
        Observation melded = room(HeroClass.ROGUE, true, "shadowmelded");
        assertEquals(List.of(), searched(melded));
    }

    @Test
    @DisplayName("a sighted Rogue's search covers its spot")
    void sighted_rogue() {
        Observation sighted = room(HeroClass.ROGUE, false, null);
        assertEquals(List.of(new Memory.Spot(2, 0, sighted.hero().cell())), searched(sighted));
    }

    @Test
    @DisplayName("a blinded Warrior still sees the cell beside it, which is all its search reaches")
    void blind_warrior() {
        Observation blind = room(HeroClass.WARRIOR, true, "blinded");
        assertEquals(List.of(new Memory.Spot(2, 0, blind.hero().cell())), searched(blind));
    }

    @Test
    @DisplayName("a blind Rogue is not sent to search again and again: the explore Policy waits for its sight")
    void no_repeat() {
        Observation blind = room(HeroClass.ROGUE, true, "blinded");
        Brain brain = new Brain(Screens.CODEX, Screens.WEIGHTS, 5L);
        Brain.Decided decided = brain.decide(blind, Screens.drive(brain, blind, blind));
        String why = decided.decision().chosen().why();
        org.junit.jupiter.api.Assertions.assertFalse(why.startsWith("search"), why);
    }

    @Test
    @DisplayName("a Rogue under the Darkness challenge on a floor that feels dark sees one cell: no coverage")
    void dark_floor() {
        Observation base = room(HeroClass.ROGUE, true, null);
        MapSection map = base.map();
        MapSection dark = new MapSection(map.width(), map.height(), map.tiles(), map.fog(), map.traps(), map.heaps(),
                map.blobs(), org.shatterfish.api.Feeling.DARK, map.transitions());
        org.shatterfish.api.HeaderSection h = base.header();
        org.shatterfish.api.HeaderSection header = new org.shatterfish.api.HeaderSection(h.version(), h.upstreamTag(),
                h.codexVersion(), h.heroClass(), List.of(org.shatterfish.api.Challenge.DARKNESS), h.depth(), h.branch(),
                h.sealed(), h.oracle(), h.prompt());
        Observation screen = new Observation(header, dark, base.actors(), base.hero(), base.inventory(), base.journal(),
                base.log(), base.actions(), base.prompt());
        assertEquals(List.of(), searched(screen));
    }
}
