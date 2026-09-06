package org.shatterfish.api;

import java.util.ArrayList;
import java.util.List;

/**
 * A representative Observation for the codec tests: a 6 by 4 floor with every fog level, traps,
 * heaps of every shape the schema allows, blobs, a feeling, two transitions and three actors with
 * buffs; and, for the test that varies every component, a sample of each record and the extra
 * elements a list can take.
 */
final class Corpus {

    static final int WIDTH = 6;
    static final int HEIGHT = 4;

    private Corpus() {
    }

    static HeaderSection header() {
        return new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v3.3.8", "", HeroClass.WARRIOR,
                List.of(Challenge.NO_FOOD, Challenge.DARKNESS), 3, 0, false, false, PromptKind.NONE);
    }

    static List<Tile> tiles() {
        return List.of(
                Tile.ENTRANCE, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.GRASS, Tile.EMPTY,
                Tile.WATER, Tile.EMPTY, Tile.HIGH_GRASS, Tile.EMPTY_SP, Tile.PEDESTAL, Tile.EMPTY,
                Tile.WALL, Tile.DOOR, Tile.EMPTY, Tile.WALL_DECO, Tile.EMPTY, Tile.EXIT,
                Tile.WALL, Tile.EMPTY, Tile.EMPTY, Tile.NONE, Tile.NONE, Tile.NONE);
    }

    static List<Fog> fog() {
        List<Fog> fog = new ArrayList<>();
        for (int cell = 0; cell < WIDTH * HEIGHT; cell++) {
            fog.add(cell < 12 ? Fog.VISIBLE : cell < 18 ? Fog.VISITED : cell < 21 ? Fog.MAPPED : Fog.UNKNOWN);
        }
        return List.copyOf(fog);
    }

    static List<TrapView> traps() {
        return List.of(new TrapView(2, "Toxic gas trap", true), new TrapView(7, "Alarm trap", false));
    }

    static List<HeapView> heaps() {
        return List.of(
                new HeapView(3, HeapKind.HEAP, false, "Ration of Food", 0, ""),
                new HeapView(9, HeapKind.FOR_SALE, false, "Potion of Healing", 50, ""),
                new HeapView(10, HeapKind.CRYSTAL_CHEST, false, "", 0, "wand"),
                new HeapView(14, HeapKind.CHEST, true, "", 0, ""));
    }

    static List<BlobCell> blobs() {
        return List.of(new BlobCell(5, List.of("Fire")), new BlobCell(6, List.of("Fire", "ToxicGas")));
    }

    static List<TransitionView> transitions() {
        return List.of(new TransitionView(0, TransitionKind.REGULAR_ENTRANCE),
                new TransitionView(17, TransitionKind.REGULAR_EXIT));
    }

    static MapSection map() {
        return new MapSection(WIDTH, HEIGHT, tiles(), fog(), traps(), heaps(), blobs(), Feeling.GRASS, transitions());
    }

    static List<BuffView> ghostBuffs() {
        return List.of(new BuffView("Invisibility", true, 1200), new BuffView("Levitating", false, 0));
    }

    static List<ActorView> actors() {
        return List.of(
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of()),
                new ActorView(8, "Albino rat", Alignment.ENEMY, 6, false, Emote.SLEEP,
                        List.of(new BuffView("Poisoned", true, 350))),
                new ActorView(11, "Ghost", Alignment.NEUTRAL, 11, true, Emote.INVESTIGATE, ghostBuffs()));
    }

    static ActorsSection actorsSection() {
        return new ActorsSection(actors());
    }

    static Observation observation() {
        return new Observation(header(), map(), actorsSection());
    }

    /** The corpus's instances of each record of the schema, every shape the corpus has. */
    static List<Object> samples(Class<?> record) {
        if (record == Observation.class) {
            return List.of(observation());
        } else if (record == HeaderSection.class) {
            return List.of(header());
        } else if (record == MapSection.class) {
            return List.of(map());
        } else if (record == ActorsSection.class) {
            return List.of(actorsSection());
        } else if (record == TrapView.class) {
            return List.copyOf(traps());
        } else if (record == HeapView.class) {
            return List.copyOf(heaps());
        } else if (record == BlobCell.class) {
            return List.copyOf(blobs());
        } else if (record == TransitionView.class) {
            return List.copyOf(transitions());
        } else if (record == ActorView.class) {
            return List.copyOf(actors());
        } else if (record == BuffView.class) {
            List<Object> buffs = new ArrayList<>(ghostBuffs());
            buffs.add(new BuffView("Poisoned", true, 350));
            return buffs;
        }
        throw new IllegalArgumentException("no sample for " + record.getName());
    }

    /** Elements a list of {@code elementType} can gain in the corpus without breaking any record's rules. */
    static List<Object> extras(Class<?> elementType) {
        if (elementType == TrapView.class) {
            return List.of(new TrapView(19, "Blade trap", true));
        } else if (elementType == HeapView.class) {
            return List.of(new HeapView(16, HeapKind.CHEST, false, "", 0, ""));
        } else if (elementType == BlobCell.class) {
            return List.of(new BlobCell(1, List.of("Web")));
        } else if (elementType == TransitionView.class) {
            return List.of(new TransitionView(20, TransitionKind.BRANCH_EXIT));
        } else if (elementType == ActorView.class) {
            return List.of(new ActorView(1, "Sewer snake", Alignment.ENEMY, 3, false, Emote.ALERT, List.of()));
        } else if (elementType == BuffView.class) {
            return List.of(new BuffView("Weakness", true, 100));
        } else if (elementType == String.class) {
            return List.of("zz");
        } else if (elementType.isEnum()) {
            return List.of((Object[]) elementType.getEnumConstants());
        }
        throw new IllegalArgumentException("no extras for " + elementType.getName());
    }
}
