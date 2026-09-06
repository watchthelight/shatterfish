package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ActorsSection;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.HeroSubclass;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.InventorySection;
import org.shatterfish.api.JournalSection;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.PromptSection;
import org.shatterfish.api.QuickslotView;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Observation around the sections the Observer builds so far, with stand-ins for the sections
 * later stories own, so that the built sections can be serialized and searched. The stand-ins are
 * the test's, not the Observer's; the Observer emits no section it cannot build.
 */
final class Skeleton {

    private Skeleton() {
    }

    /** The header and the map, with no actors and a stand-in hero at its cell. */
    static Observation around(HeaderSection header, MapSection map) {
        return around(header, map, new ActorsSection(List.of()), standInHero());
    }

    /** The header, the map, the actors and the hero, the sections stories 1.8 and 1.9 build. */
    static Observation around(HeaderSection header, MapSection map, ActorsSection actors, HeroSection hero) {
        return around(header, map, actors, hero, new InventorySection(List.of()),
                new JournalSection(List.of(), List.of()), new LogSection(List.of()), PromptSection.NONE);
    }

    /** Every section the Observer builds through story 1.10, with no Actions. */
    static Observation around(HeaderSection header, MapSection map, ActorsSection actors, HeroSection hero,
                              InventorySection inventory, JournalSection journal, LogSection log,
                              PromptSection prompt) {
        return new Observation(header, map, actors, hero, inventory, journal, log, ActionsSection.NONE, prompt);
    }

    /** Every section the Observer builds, read now. */
    static Observation everything(Observer observer) {
        return around(observer.header(), observer.map(), observer.actors(), observer.hero(), observer.inventory(),
                observer.journal(), observer.log(), observer.prompt());
    }

    private static HeroSection standInHero() {
        return new HeroSection(Dungeon.hero.pos, "", HeroSubclass.NONE, "", 1, 0, 1, 1, 1, 0, 1, 0, 0, 0,
                Hunger.NONE, List.of(), List.of(), List.of(0, 0, 0, 0),
                Collections.nCopies(HeroSection.QUICKSLOTS, new QuickslotView("", false)));
    }

    /** The serialized forms of an Observation, and what they must not contain. */
    record Serialized(String json, String hex) {

        static Serialized of(Observation observation) {
            return new Serialized(observation.json(), HexFormat.of().formatHex(ObservationCodec.encode(observation)));
        }

        void assertAbsent(String text) {
            String hexOfText = HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
            assertFalse(json.contains(text), "the JSON carries " + text);
            assertFalse(hex.contains(hexOfText), "the bytes carry " + text);
        }

        void assertPresent(String text) {
            String hexOfText = HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
            assertTrue(json.contains(text), "the JSON lacks " + text);
            assertTrue(hex.contains(hexOfText), "the bytes lack " + text);
        }
    }
}
