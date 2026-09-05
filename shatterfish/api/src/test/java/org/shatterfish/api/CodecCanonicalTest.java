package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Every list has a canonical order fixed by the schema, never the order an input arrived in
 * (ADR-0005): each set-like list of the corpus is given to its record reversed, and the section
 * hashes, the records and the bytes come out the same. The two positional lists, tiles and fog,
 * are the exception the schema names: their order is the map.
 */
class CodecCanonicalTest {

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        assertNotEquals(list, copy, "a list of one cannot be reordered; give the corpus two");
        return copy;
    }

    @Test
    @DisplayName("the header's challenges in any order are the same header")
    void challenges_in_any_order() {
        HeaderSection base = Corpus.header();
        HeaderSection shuffled = new HeaderSection(base.version(), base.upstreamTag(), base.codexVersion(),
                base.heroClass(), reversed(base.challenges()), base.depth(), base.branch(), base.sealed(), base.oracle(),
                base.prompt());
        assertEquals(base, shuffled);
        assertEquals(hashes(base, Corpus.map(), Corpus.actorsSection()),
                hashes(shuffled, Corpus.map(), Corpus.actorsSection()));
    }

    @Test
    @DisplayName("the map's traps, heaps, blobs, blob kinds and transitions in any order are the same map")
    void map_lists_in_any_order() {
        MapSection base = Corpus.map();
        List<BlobCell> blobs = new ArrayList<>();
        for (BlobCell blob : reversed(base.blobs())) {
            blobs.add(blob.kinds().size() > 1 ? new BlobCell(blob.cell(), reversed(blob.kinds())) : blob);
        }
        MapSection shuffled = new MapSection(base.width(), base.height(), base.tiles(), base.fog(),
                reversed(base.traps()), reversed(base.heaps()), blobs, base.feeling(), reversed(base.transitions()));
        assertEquals(base, shuffled);
        assertEquals(hashes(Corpus.header(), base, Corpus.actorsSection()),
                hashes(Corpus.header(), shuffled, Corpus.actorsSection()));
    }

    @Test
    @DisplayName("the actors and each actor's buffs in any order are the same actors")
    void actors_in_any_order() {
        ActorsSection base = Corpus.actorsSection();
        List<ActorView> actors = new ArrayList<>();
        for (ActorView actor : reversed(base.actors())) {
            actors.add(actor.buffs().size() > 1
                    ? new ActorView(actor.cell(), actor.name(), actor.alignment(), actor.healthPips(), actor.invisible(),
                            actor.emote(), reversed(actor.buffs()))
                    : actor);
        }
        ActorsSection shuffled = new ActorsSection(actors);
        assertEquals(base, shuffled);
        assertEquals(hashes(Corpus.header(), Corpus.map(), base), hashes(Corpus.header(), Corpus.map(), shuffled));
    }

    @Test
    @DisplayName("tiles and fog are positional: two cells swapped is a different map")
    void the_positional_lists_are_not_reordered() {
        MapSection base = Corpus.map();
        List<Tile> tiles = new ArrayList<>(base.tiles());
        Collections.swap(tiles, 0, 1);
        MapSection swapped = new MapSection(base.width(), base.height(), tiles, base.fog(), base.traps(), base.heaps(),
                base.blobs(), base.feeling(), base.transitions());
        assertNotEquals(base, swapped);
        assertNotEquals(hashes(Corpus.header(), base, Corpus.actorsSection()).get(ObservationCodec.MAP),
                hashes(Corpus.header(), swapped, Corpus.actorsSection()).get(ObservationCodec.MAP));
    }

    private static Map<String, String> hashes(HeaderSection header, MapSection map, ActorsSection actors) {
        return new Observation(header, map, actors).sectionHashes();
    }
}
