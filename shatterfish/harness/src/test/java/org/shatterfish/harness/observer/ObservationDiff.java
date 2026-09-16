package org.shatterfish.harness.observer;

import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The names of the sections, and for the map the cells, in which two Observations differ, so that
 * a test can hold a change to an exact set rather than to "something changed". A toggle test says
 * which parts of the screen an effect may move and holds the rest still; this is what makes "the
 * rest" a checkable list.
 *
 * <p>The Actions are not compared: they are a function of the other sections
 * ({@code ValidActions.of}), which {@code ObserveTest} holds, so a difference there is never news
 * of its own.
 */
final class ObservationDiff {

    private ObservationDiff() {
    }

    /** The parts in which {@code a} and {@code b} differ, by name, in a fixed order. */
    static Set<String> of(Observation a, Observation b) {
        Set<String> diff = new TreeSet<>();
        add(diff, "header", a.header(), b.header());
        MapSection ma = a.map();
        MapSection mb = b.map();
        add(diff, "map.size", List.of(ma.width(), ma.height()), List.of(mb.width(), mb.height()));
        add(diff, "map.tiles", ma.tiles(), mb.tiles());
        add(diff, "map.fog", ma.fog(), mb.fog());
        add(diff, "map.traps", ma.traps(), mb.traps());
        add(diff, "map.heaps", ma.heaps(), mb.heaps());
        add(diff, "map.blobs", ma.blobs(), mb.blobs());
        add(diff, "map.feeling", ma.feeling(), mb.feeling());
        add(diff, "map.transitions", ma.transitions(), mb.transitions());
        add(diff, "actors", a.actors(), b.actors());
        add(diff, "hero", withoutBuffs(a.hero()), withoutBuffs(b.hero()));
        add(diff, "hero.buffs", a.hero().buffs(), b.hero().buffs());
        add(diff, "inventory", a.inventory(), b.inventory());
        add(diff, "journal.notes", a.journal().notes(), b.journal().notes());
        add(diff, "journal.known", a.journal().known(), b.journal().known());
        add(diff, "log", a.log(), b.log());
        add(diff, "prompt", a.prompt(), b.prompt());
        return diff;
    }

    /** The cells whose fog differs between the two maps, ascending. */
    static List<Integer> fogCells(Observation a, Observation b) {
        return cells(a.map().fog(), b.map().fog());
    }

    /** The cells whose tile differs between the two maps, ascending. */
    static List<Integer> tileCells(Observation a, Observation b) {
        return cells(a.map().tiles(), b.map().tiles());
    }

    /** The cells the map paints at {@code level}. */
    static List<Integer> cellsAt(Observation observation, Fog level) {
        List<Fog> fog = observation.map().fog();
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < fog.size(); cell++) {
            if (fog.get(cell) == level) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** The cells the map draws no tile on. */
    static List<Integer> blankCells(Observation observation) {
        List<Tile> tiles = observation.map().tiles();
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < tiles.size(); cell++) {
            if (tiles.get(cell) == Tile.NONE) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static <T> List<Integer> cells(List<T> a, List<T> b) {
        List<Integer> cells = new ArrayList<>();
        int shared = Math.min(a.size(), b.size());
        for (int cell = 0; cell < shared; cell++) {
            if (!Objects.equals(a.get(cell), b.get(cell))) {
                cells.add(cell);
            }
        }
        for (int cell = shared; cell < Math.max(a.size(), b.size()); cell++) {
            cells.add(cell);
        }
        return cells;
    }

    private static HeroSection withoutBuffs(HeroSection hero) {
        return new HeroSection(hero.cell(), hero.name(), hero.subclass(), hero.ability(), hero.level(), hero.exp(),
                hero.expToLevel(), hero.hp(), hero.ht(), hero.shield(), hero.strength(), hero.strengthBonus(),
                hero.gold(), hero.energy(), hero.hunger(), List.of(), hero.talents(), hero.talentPointsAvailable(),
                hero.quickslots());
    }

    private static void add(Set<String> diff, String name, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            diff.add(name);
        }
    }
}
