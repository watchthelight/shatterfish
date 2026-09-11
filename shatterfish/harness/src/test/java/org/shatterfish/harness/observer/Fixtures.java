package org.shatterfish.harness.observer;

import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Feeling;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;
import org.shatterfish.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The smallest Observations a harness test can ask a question of, built by hand rather than read
 * from a game. They are not what the Observer produces and never stand in for it: the leak tests
 * read the game. These exist for the rules that live in {@code api} and are checked here because
 * only the harness can see both sides of them, the valid-Action table being the first.
 */
final class Fixtures {

    /** The width of the little floor below, and the cell beside the hero on it. */
    static final int WIDTH = 3;
    static final int HERO = 4;
    static final int BESIDE = 5;

    private Fixtures() {
    }

    /** A three-by-three floor with the hero in the middle and {@code beside} to its right. */
    static Observation oneCellFloor(Tile beside) {
        List<Tile> tiles = new ArrayList<>(Collections.nCopies(WIDTH * WIDTH, Tile.WALL));
        List<Fog> fog = new ArrayList<>(Collections.nCopies(WIDTH * WIDTH, Fog.VISIBLE));
        tiles.set(HERO, Tile.EMPTY);
        tiles.set(BESIDE, beside);
        if (beside == Tile.NONE) {
            fog.set(BESIDE, Fog.UNKNOWN);
        }
        MapSection map = new MapSection(WIDTH, WIDTH, tiles, fog, List.of(), List.of(), List.of(),
                Feeling.NONE, List.of());
        return new Observation(header(), map, new ActorsSection(List.of()), hero(), new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), ActionsSection.NONE,
                PromptSection.NONE);
    }

    private static HeaderSection header() {
        return new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v4.0.0", "", HeroClass.WARRIOR,
                List.<Challenge>of(), 1, 0, false, false, PromptKind.NONE);
    }

    private static HeroSection hero() {
        return new HeroSection(HERO, "Bash", HeroSubclass.NONE, "", 1, 0, 10, 20, 20, 0, 10, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }
}
