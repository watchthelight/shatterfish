package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Codex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * shapes neither tree has. The rows of the story's matrix are each named: a name both games give,
 * a name whose numbers differ, a name only one game gives, one name carried by two classes of one
 * game, a fact the two write in shapes that cannot be compared, and a name a class states in a
 * shape the reader cannot read — which fails rather than passing quietly.
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
                "the two games write their rolls as different methods, so the two are not set against each other");
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
    @DisplayName("one name carried by two classes of one game names and cites both")
    void one_name_two_classes() {
        Codex.VocabularyEntry imp = byName(diff()).get("mob ambitious imp");
        assertNotNull(imp, "the other game has two classes it calls an ambitious imp");
        assertEquals(List.of("actors.mobs.npcs.Imp", "actors.mobs.npcs.ImpShopkeeper"), imp.there().classNames());
        assertEquals(2, imp.there().citations().size(), "both are cited, since the row is about the name");
        for (Codex.Citation citation : imp.there().citations()) {
            assertTrue(citation.path().startsWith(Sources.VANILLA_ROOT), citation.reference());
        }
    }

    @Test
    @DisplayName("the diff names both pinned tags and says what it is for")
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
        for (Vanilla.Named mob : Vanilla.named(ROOT)) {
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
    @DisplayName("a class that names itself with a condition contributes every name a player sees")
    void a_conditional_name_contributes_both_names() {
        List<String> named = new ArrayList<>();
        for (Vanilla.Named mob : Vanilla.named(ROOT)) {
            if (mob.className().equals("actors.mobs.Goo")) {
                named.add(mob.name());
            }
        }
        assertEquals(List.of("Goo", "spawn of Goo"), named,
                "the other game's Goo names itself one thing or the other and a player sees both");
        Map<String, Codex.VocabularyEntry> rows = byName(diff());
        for (String name : List.of("mob goo", "mob tengu", "mob dm-300", "mob yog-dzewa", "mob king of dwarves")) {
            assertNotNull(rows.get(name).there(), name + " is a mob the other game has");
        }
    }

    @Test
    @DisplayName("a thing one game declares inside another class is read, and what it is decides its kind")
    void a_nested_class_is_read_and_typed_by_what_it_extends() {
        Map<String, Codex.VocabularyEntry> rows = byName(diff());
        Codex.VocabularyEntry fist = rows.get("mob rotting fist");
        assertNotNull(fist.there(), "the other game declares its rotting fist inside its Yog-Dzewa");
        assertEquals(List.of("actors.mobs.Yog.RottingFist"), fist.there().classNames());
        Codex.VocabularyEntry sheep = rows.get("mob sheep");
        assertNotNull(sheep.there(), "and its sheep inside a wand, which is a mob all the same");
        assertEquals(List.of("items.wands.WandOfFlock.Sheep"), sheep.there().classNames(),
                "a class is what it extends, not the folder its file sits in");
        assertNull(rows.get("item sheep"), "so the sheep is not also an item");
    }

    @Test
    @DisplayName("a number is taken from a class that states it and never from a field's declaration")
    void a_declaration_is_not_a_statement() {
        Map<String, Codex.VocabularyEntry> rows = byName(diff());
        Codex.VocabularyEntry statue = rows.get("mob animated statue");
        Codex.Rule defence = statue.there().facts().stream()
                .filter(f -> f.what().equals("defence")).findFirst().orElseThrow();
        assertEquals("4 + Dungeon.depth", defence.expression(),
                "the other game's statue states its defence as an expression, not as the zero its base class declares");
        assertTrue(defence.citation().path().endsWith("Statue.java"), defence.citation().reference());
        assertEquals(0, statue.differences().stream().filter(Codex.MechanicDifference::comparable).count(),
                "a number against an expression is not a difference the table resolves");
    }

    @Test
    @DisplayName("a number this game does not state at construction is not published as a fact")
    void a_stat_set_later_is_not_a_fact() {
        Codex.VocabularyEntry bee = byName(diff()).get("mob golden bee");
        assertNotNull(bee.here());
        assertEquals(0, bee.here().facts().stream().filter(f -> f.what().equals("health")).count(),
                "this game's bee takes its health from the hero who summons it, so it states none");
    }

    @Test
    @DisplayName("every difference the table states is a difference, and no row states one about itself")
    void a_difference_differs() {
        for (Codex.VocabularyEntry entry : diff().entries()) {
            for (Codex.MechanicDifference difference : entry.differences()) {
                assertFalse(difference.here().equals(difference.there()),
                        entry.kind() + " " + entry.name() + " states " + difference.what()
                                + " the same in both games and the table calls it a difference");
            }
        }
    }

    @Test
    @DisplayName("the other game's reader refuses a name it cannot read, and a class that names itself twice")
    void the_other_games_reader_refuses_what_it_cannot_read(@TempDir Path fixture) throws IOException {
        pinned(fixture);
        mob(fixture, "Mob", "public class Mob {\n\tprotected int defenseSkill = 0;\n}\n");
        item(fixture, "Dew", "public class Dew {\n\t{\n\t\tname = \"dew\";\n\t}\n}\n");
        mob(fixture, "Named", "public class Named extends Mob {\n\t{\n\t\tname = \"a mob\";\n\t\tHT = 9;\n\t}\n}\n");
        List<Vanilla.Named> read = Vanilla.named(fixture);
        assertEquals(1, read.stream().filter(n -> n.className().equals("actors.mobs.Named")).count(),
                "the fixture reads before the shapes below are added to it");

        mob(fixture, "Unreadable", "public class Unreadable extends Mob {\n\t{\n\t\tname = someName();\n\t}\n}\n");
        IllegalStateException unreadable = assertThrows(IllegalStateException.class, () -> Vanilla.named(fixture));
        assertTrue(unreadable.getMessage().contains("a shape the reader cannot read"), unreadable.getMessage());
        assertTrue(unreadable.getMessage().contains("Unreadable.java"), unreadable.getMessage());
        Files.delete(mobPath(fixture, "Unreadable"));

        mob(fixture, "Twice", "public class Twice extends Mob {\n\t{\n\t\tname = \"one\";\n\t\tname = \"two\";\n\t}\n}\n");
        List<String> both = Vanilla.named(fixture).stream()
                .filter(n -> n.className().equals("actors.mobs.Twice")).map(Vanilla.Named::name).toList();
        assertEquals(List.of("one", "two"), both, "a class that states two names carries both");
    }

    @Test
    @DisplayName("the second pinned source being absent or at another commit fails naming the script that fetches it")
    void the_second_source_must_be_fetched(@TempDir Path fixture) throws IOException {
        Files.writeString(fixture.resolve(Vanilla.PIN), pin("6fffc0768905b5b1f167a05df7274acc10a7ae34"),
                StandardCharsets.UTF_8);
        IllegalStateException absent = assertThrows(IllegalStateException.class, () -> Vanilla.named(fixture));
        assertTrue(absent.getMessage().contains(Vanilla.FETCH), absent.getMessage());

        Files.createDirectories(fixture.resolve(Sources.VANILLA_ROOT));
        IllegalStateException unmarked = assertThrows(IllegalStateException.class, () -> Vanilla.named(fixture));
        assertTrue(unmarked.getMessage().contains(Vanilla.FETCH), unmarked.getMessage());

        Files.writeString(fixture.resolve(Sources.VANILLA_ROOT + Vanilla.MARKER), "0123456789abcdef",
                StandardCharsets.UTF_8);
        IllegalStateException elsewhere = assertThrows(IllegalStateException.class, () -> Vanilla.named(fixture));
        assertTrue(elsewhere.getMessage().contains("0123456789abcdef"), elsewhere.getMessage());
        assertTrue(elsewhere.getMessage().contains(Vanilla.FETCH), elsewhere.getMessage());
    }

    @Test
    @DisplayName("the pinned tree the table cites is the commit the pin names")
    void the_tree_read_is_the_tree_pinned() throws IOException {
        String marked = Files.readString(ROOT.resolve(Sources.VANILLA_ROOT + Vanilla.MARKER), StandardCharsets.UTF_8);
        assertEquals(Vanilla.commit(ROOT), marked.trim(),
                "the fetched tree is at the commit " + Vanilla.PIN + " names, so its line numbers mean something");
    }

    @Test
    @DisplayName("the readers refuse a file that is not source and a folder of neither pinned game")
    void the_readers_refuse_what_is_not_a_pinned_source() {
        assertThrows(IllegalStateException.class, () -> Sources.file(ROOT, "vanilla-src/build.gradle"),
                "a file of the other game that is not source");
        assertThrows(IllegalStateException.class, () -> Sources.under(ROOT, "somewhere-else/"),
                "a folder outside either pinned game");
        assertThrows(IllegalStateException.class, () -> Sources.under(ROOT, "vanilla-src/../"),
                "and a folder that climbs out of one");
    }

    /** A fixture tree the reader will open: a pin, a marker at the pinned commit, and nothing else. */
    private static void pinned(Path fixture) throws IOException {
        String commit = "6fffc0768905b5b1f167a05df7274acc10a7ae34";
        Files.writeString(fixture.resolve(Vanilla.PIN), pin(commit), StandardCharsets.UTF_8);
        Files.createDirectories(fixture.resolve(Sources.VANILLA_ROOT));
        Files.writeString(fixture.resolve(Sources.VANILLA_ROOT + Vanilla.MARKER), commit, StandardCharsets.UTF_8);
    }

    private static String pin(String commit) {
        return "repository=https://example.invalid/pixel-dungeon.git\ntag=archive\ncommit=" + commit
                + "\nfolder=vanilla-src\n";
    }

    private static Path mobPath(Path fixture, String name) {
        return fixture.resolve(Sources.VANILLA_ROOT + Vanilla.MOBS + name + ".java");
    }

    private static void mob(Path fixture, String name, String body) throws IOException {
        write(mobPath(fixture, name), body);
    }

    private static void item(Path fixture, String name, String body) throws IOException {
        write(fixture.resolve(Sources.VANILLA_ROOT + Vanilla.ITEMS + name + ".java"), body);
    }

    private static void write(Path path, String body) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "package com.watabou.pixeldungeon;\n\n" + body, StandardCharsets.UTF_8);
    }
}
