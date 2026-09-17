package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 2.7's readers held against what the pinned tree cannot show them: a key whose class is
 * nested, a key that names nothing, a date the game states and one it does not, and the refusals
 * that guard each. A refusal no input can reach is a claim nobody checks, so the ones the tree
 * cannot produce are produced here.
 */
class TextReaderTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;

    @Test
    @DisplayName("a key's class is the longest prefix that is a class, so a nested class beats the file it lives in")
    void a_key_resolves_to_the_longest_class() {
        Map<String, String> outers = Text.outers(ROOT);
        assertEquals("items.armor.PlateArmor", Text.resolve(ROOT, outers, "items.armor.platearmor.name").className());
        assertEquals("actors.buffs.ChampionEnemy.Blazing",
                Text.resolve(ROOT, outers, "actors.buffs.championenemy$blazing.name").className(),
                "a nested class is named with a dollar in the key and with dots in the table");
        assertEquals("actors.hero.Talent.FollowupStrikeTracker",
                Text.resolve(ROOT, outers, "actors.hero.talent$followupstriketracker.name").className(),
                "a nested class the outer file declares far from its own head still resolves");
        assertEquals("actors.buffs.MonkEnergy.MonkAbility.Flurry",
                Text.resolve(ROOT, outers, "actors.buffs.monkenergy$monkability$flurry.name").className(),
                "a class nested two deep is resolved inside each enclosing declaration in turn");
        assertEquals("", Text.resolve(ROOT, outers, "windows.wndclass.title").className(),
                "a key naming a class the game no longer compiles names no class");
        assertEquals("", Text.resolve(ROOT, outers, "actors.mobs.tengu$bombability$bombblob.name").className(),
                "a key whose last segment names nothing names no class, though its first segments do");
        assertEquals("", Text.resolve(ROOT, outers, "nosuchthing.at.all").className());
        Text.Resolved resolved = Text.resolve(ROOT, outers, "items.armor.platearmor.name");
        assertEquals("items.armor.platearmor".length(), resolved.prefix(), "the suffix is what the class did not take");
    }

    @Test
    @DisplayName("the bundles are the ones the game searches, in the order it searches them")
    void the_bundles_are_the_games() {
        assertEquals(List.of("messages/actors/actors.properties", "messages/items/items.properties",
                        "messages/journal/journal.properties", "messages/levels/levels.properties",
                        "messages/misc/misc.properties", "messages/plants/plants.properties",
                        "messages/scenes/scenes.properties", "messages/ui/ui.properties",
                        "messages/windows/windows.properties"),
                Text.bundles(ROOT));
    }

    @Test
    @DisplayName("the longest prefix wins, which the pinned keys cannot show because none has two class prefixes")
    void the_longest_prefix_wins() {
        Map<String, String> outers = new java.util.TreeMap<>(Map.of("a.b", "a.B", "a.b.c", "a.b.C"));
        assertEquals("a.b.C", Text.resolve(ROOT, outers, "a.b.c.name").className(),
                "a key whose shorter prefix is also a class belongs to the longer one");
        assertEquals("a.B", Text.resolve(ROOT, outers, "a.b.other.name").className(),
                "and a key the longer prefix does not cover falls back to the shorter");
    }

    @Test
    @DisplayName("the bundles keep the game's own order, which the pinned nine cannot show because they are alphabetical already")
    void the_bundles_keep_the_games_order() {
        Sources.Body messages = body("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/messages/Made.java",
                "class Made {",
                "\tprivate static String[] prop_files = new String[]{",
                "\t\t\tAssets.Messages.WINDOWS,",
                "\t\t\tAssets.Messages.ACTORS",
                "\t};",
                "}");
        Sources.Body assets = body("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Made.java",
                "class Made {",
                "\tpublic static class Messages {",
                "\t\tpublic static final String ACTORS = \"messages/actors/actors\";",
                "\t\tpublic static final String WINDOWS = \"messages/windows/windows\";",
                "\t}",
                "}");
        assertEquals(List.of("messages/windows/windows.properties", "messages/actors/actors.properties"),
                Text.bundles(messages, assets), "the order the game's own array walks them, not the order they sort in");
    }

    /** A body of made-up source, as the readers see a file. */
    private static Sources.Body body(String path, String... lines) {
        List<String> all = List.of(lines);
        return new Sources.Body(path, all, 0, all.size(), "Made");
    }

    @Test
    @DisplayName("a bundle line the reader cannot read fails naming it, and a key given twice fails")
    void the_bundle_reader_refuses_what_it_cannot_read() {
        assertThrows(java.io.UncheckedIOException.class, () -> Names.lines(ROOT, "core/src/main/assets/messages/none/none.properties"),
                "a bundle that is not there fails naming the file rather than reading as empty");
        assertThrows(IllegalStateException.class, () -> Names.lines(ROOT, "build.gradle"),
                "a file that is not a bundle of the game");
    }

    @Test
    @DisplayName("a date is the phrase the game writes, and a text that states none carries none")
    void a_date_is_the_games_own_words() {
        assertEquals(List.of("September 9th, 2026"), Changelog.dates("**-** Released September 9th, 2026 and more text"));
        assertEquals(List.of("December 4th, 2025"), Changelog.dates("Released December 4th, 2025"));
        assertEquals(List.of(), Changelog.dates("no date at all in this one"));
        assertEquals(List.of(), Changelog.dates("v4.0.1 is coming in 2026 sometime"), "a year alone is not a date the game states");
        assertEquals(List.of("December 1st, 2015", "September 4th, 2015"),
                Changelog.dates("**v1.9.2:** (December 1st, 2015) and **v1.9.1:** (September 4th, 2015)"),
                "a text that states several dates carries all of them, in the order it writes them");
    }

    @Test
    @DisplayName("a bundle value is the text the game shows, with its escapes decoded as the game decodes them")
    void a_value_is_what_the_game_shows() {
        assertEquals("one\ntwo", Text.decoded("b.properties", 0, "one\\ntwo"));
        assertEquals("a\tb", Text.decoded("b.properties", 0, "a\\tb"));
        assertEquals("100%", Text.decoded("b.properties", 0, "100%"));
        assertEquals("a=b", Text.decoded("b.properties", 0, "a\\=b"));
        assertEquals("\u00e9", Text.decoded("b.properties", 0, "\\u00e9"));
        assertThrows(IllegalStateException.class, () -> Text.decoded("b.properties", 0, "a\\qb"),
                "an escape the reader does not know fails rather than being passed through");
        List<Codex.StringEntry> strings = Text.entries(ROOT);
        assertTrue(strings.stream().noneMatch(e -> e.value().contains("\\n")),
                "no value still carries an escape the game would have decoded");
    }

    @Test
    @DisplayName("the game names one asset with no file behind it, and the table says which")
    void an_asset_with_no_file_is_named() {
        List<Codex.AssetEntry> assets = AssetIndex.entries(ROOT);
        java.util.TreeSet<String> absent = new java.util.TreeSet<>();
        for (Codex.AssetEntry asset : assets) {
            if (!asset.present()) {
                absent.add(asset.path());
            }
        }
        java.util.TreeSet<String> named = new java.util.TreeSet<>();
        for (Map.Entry<String, String> entry : AssetIndex.ABSENT) {
            assertFalse(entry.getValue().isBlank(), entry.getKey() + " is named with a reason");
            named.add(entry.getKey());
        }
        assertEquals(named, absent, "the paths the game names with no file behind them, against AssetIndex.ABSENT");
        assertTrue(assets.stream().anyMatch(a -> a.path().equals("gdx/textfield.json") && a.present()),
                "a file the toolkit module loads by a literal is there");
    }
}
