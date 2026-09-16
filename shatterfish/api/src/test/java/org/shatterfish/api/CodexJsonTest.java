package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Codex's records refuse what a Codex cannot carry, and their JSON is the golden text
 * (story 2.1): a change here is a change every consumer sees, and the Codex version says so.
 */
class CodexJsonTest {

    private static final Codex.Citation AT = new Codex.Citation("core/src/main/java/X.java", 7);

    @Test
    @DisplayName("the manifest renders its version, its tag and its tables, keys and tables sorted")
    void the_manifest_renders() {
        Codex.Manifest manifest = new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("hero-classes.json", "challenges.json"));
        assertEquals("{\"codexVersion\":1,\"tables\":[\"challenges.json\",\"hero-classes.json\"],\"upstreamTag\":\"v4.0.0\"}\n",
                CodexJson.manifest(manifest));
    }

    @Test
    @DisplayName("a hero class entry and a challenge entry render with their citations")
    void the_entries_render() {
        Codex.HeroClassEntry warrior = new Codex.HeroClassEntry(HeroClass.WARRIOR, List.of(HeroSubclass.BERSERKER, HeroSubclass.GLADIATOR), AT);
        assertEquals("[{\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},"
                + "\"heroClass\":\"WARRIOR\",\"subclasses\":[\"BERSERKER\",\"GLADIATOR\"]}]\n", CodexJson.heroClasses(List.of(warrior)));
        Codex.ChallengeEntry darkness = new Codex.ChallengeEntry(Challenge.DARKNESS, 32, AT);
        assertEquals("[{\"challenge\":\"DARKNESS\",\"citation\":{\"line\":7,\"path\":\"core/src/main/java/X.java\"},\"mask\":32}]\n",
                CodexJson.challenges(List.of(darkness)));
        assertEquals("core/src/main/java/X.java:7", AT.reference());
    }

    @Test
    @DisplayName("the records refuse an absolute or backslashed path, a zero line, a tag that is not one, a mask of two bits, NONE as a subclass")
    void the_records_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new Codex.Citation("/abs/X.java", 1));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Citation("core\\X.java", 1));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Citation("core/X.java", 0));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(0, "v4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.Manifest(1, "4.0.0", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 3, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.ChallengeEntry(Challenge.NO_FOOD, 0, AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(HeroSubclass.NONE), AT));
        assertThrows(IllegalArgumentException.class, () -> new Codex.HeroClassEntry(HeroClass.MAGE, List.of(), null));
    }
}
