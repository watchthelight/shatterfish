package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 2.8's diff held against the two pinned games, and against source written here for the
 * shapes neither tree has. The four rows of the story's matrix are each named: a name both games
 * give, a name whose numbers differ, a name only one game gives, and a fact the two write in
 * shapes that cannot be compared.
 */
class VocabularyTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;

    /** The diff, read once: it walks two source trees and the tests below all ask the same questions of it. */
    private static Codex.Vocabulary diff() {
        return Vocabulary.read(ROOT, Text.entries(ROOT), Mobs.entries(ROOT), Items.entries(ROOT));
    }

    private static Map<String, Codex.VocabularyEntry> byName(Codex.Vocabulary vocabulary) {
        Map<String, Codex.VocabularyEntry> rows = new TreeMap<>();
        for (Codex.VocabularyEntry entry : vocabulary.entries()) {
            rows.put(entry.kind() + " " + entry.name(), entry);
        }
        return rows;
    }

    @Test
    @DisplayName("a name both games give carries both sides, both classes and both citations")
    void a_shared_name_carries_both_sides() {
        Codex.VocabularyEntry rat = byName(diff()).get("mob albino rat");
        assertNotNull(rat, "both games have an albino rat");
        assertTrue(rat.shared());
        assertEquals(List.of("actors.mobs.Albino"), rat.here().classNames());
        assertEquals(List.of("actors.mobs.Albino"), rat.there().classNames());
        assertEquals("albino rat", rat.here().name());
        assertEquals("albino rat", rat.there().name());
        assertEquals(1, rat.here().citations().size());
        assertEquals(1, rat.there().citations().size());
        assertTrue(rat.there().citations().get(0).path().startsWith(Sources.VANILLA_ROOT),
                "the other game is cited in its own tree: " + rat.there().citations().get(0).reference());
        assertFalse(rat.here().citations().get(0).path().startsWith(Sources.VANILLA_ROOT),
                "and this game in this one");
    }

    @Test
    @DisplayName("a number both games state is compared, and a roll they write in different shapes is not")
    void the_numbers_are_compared_and_the_rolls_are_not() {
        Codex.VocabularyEntry rat = byName(diff()).get("mob albino rat");
        Map<String, Codex.MechanicDifference> differences = new TreeMap<>();
        for (Codex.MechanicDifference difference : rat.differences()) {
            differences.put(difference.what(), difference);
        }
        assertEquals(List.of("damageRoll", "defence", "dr", "health"), List.copyOf(differences.keySet()),
                "the facts the two games both state about an albino rat");
        assertTrue(differences.get("health").comparable(), "health is a number on both sides");
        assertEquals("12", differences.get("health").here());
        assertEquals("15", differences.get("health").there());
        assertTrue(differences.get("defence").comparable());
        assertFalse(differences.get("damageRoll").comparable(),
                "one game writes its roll as a method and the other measures it, so the two are not set against each other");
        assertFalse(differences.get("dr").comparable());
        assertFalse(differences.get("damageRoll").there().isBlank(), "and the text each states is carried");
    }

    @Test
    @DisplayName("a name only one game gives says so and cites the game that has it")
    void a_name_one_game_gives_says_so() {
        Map<String, Codex.VocabularyEntry> rows = byName(diff());
        Codex.VocabularyEntry ours = rows.get("mob crystal guardian");
        assertNotNull(ours, "this game has a crystal guardian");
        assertFalse(ours.shared());
        assertNotNull(ours.here());
        assertNull(ours.there(), "and the other game does not");
        assertTrue(ours.differences().isEmpty(), "a name one game gives has nothing to differ about");
        Codex.VocabularyEntry theirs = rows.get("item dew vial");
        assertNotNull(theirs, "the other game has a dew vial");
        assertFalse(theirs.shared());
        assertNull(theirs.here());
        assertNotNull(theirs.there());
    }

    @Test
    @DisplayName("the diff names both pinned tags and says what it is for, and nothing reads it")
    void the_diff_says_what_it_is_for() {
        Codex.Vocabulary vocabulary = diff();
        assertEquals("v4.0.0", vocabulary.tag());
        assertEquals("archive", vocabulary.vanillaTag());
        assertTrue(vocabulary.consumer().contains("epic 7"), vocabulary.consumer());
        assertTrue(vocabulary.consumer().contains("nothing reads it yet"), vocabulary.consumer());
    }

    @Test
    @DisplayName("the other game's reader takes a fact from the class that states it, walking up what a class extends")
    void the_other_game_inherits_what_it_does_not_restate() {
        Map<String, Vanilla.Named> mobs = new TreeMap<>();
        for (Vanilla.Named mob : Vanilla.mobs(ROOT)) {
            mobs.put(mob.className(), mob);
        }
        Vanilla.Named rat = mobs.get("actors.mobs.Rat");
        Vanilla.Named albino = mobs.get("actors.mobs.Albino");
        assertEquals("marsupial rat", rat.name());
        assertEquals("albino rat", albino.name());
        Map<String, Codex.Rule> facts = new TreeMap<>();
        for (Codex.Rule fact : albino.facts()) {
            facts.put(fact.what(), fact);
        }
        assertEquals("15", facts.get("health").expression(), "the albino states its own health");
        assertTrue(facts.get("health").citation().path().endsWith("Albino.java"), "and states it itself");
        assertEquals("3", facts.get("defence").expression(), "and inherits the defence it does not restate");
        assertTrue(facts.get("defence").citation().path().endsWith("Rat.java"),
                "cited to the class that states it: " + facts.get("defence").citation().reference());
    }

    @Test
    @DisplayName("the other game's reader refuses a class that names itself twice and reads no name where there is none")
    void the_other_games_reader_refuses_what_it_cannot_read() {
        assertThrows(java.io.UncheckedIOException.class, () -> Vanilla.mobs(ROOT.resolve("no-such-tree")),
                "a tree that is not there fails naming the folder rather than reading as a game with no mobs");
        assertThrows(IllegalStateException.class, () -> Sources.file(ROOT, "vanilla-src/build.gradle"),
                "a file of the other game that is not source");
        assertThrows(IllegalStateException.class, () -> Sources.under(ROOT, "somewhere-else/"),
                "a folder outside either pinned game");
    }
}
