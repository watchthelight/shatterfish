package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.knowledge.PlaybookValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Playbook file may say (story 7.2, FR-35): one rejecting case per rule, each checked to
 * name the field, and the accepting cases that stop the validator from passing by rejecting
 * nothing, or by rejecting everything.
 */
class PlaybookValidatorTest {

    private static final Set<String> CLASSES = Set.of("WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC");

    /** A Playbook of the head every one has and these top-level members (key, raw value, ...), in the sorted order the reader requires. */
    private static String document(String... members) {
        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.put("format", "1");
        sorted.put("name", "\"shatterfish\"");
        sorted.put("version", "1");
        for (int i = 0; i < members.length; i += 2) {
            sorted.put(members[i], members[i + 1]);
        }
        StringBuilder out = new StringBuilder("{");
        for (Map.Entry<String, String> member : sorted.entrySet()) {
            out.append(out.length() > 1 ? "," : "").append('"').append(member.getKey()).append("\":").append(member.getValue());
        }
        return out.append('}').toString();
    }

    private static List<String> problems(String... members) {
        return PlaybookValidator.problems(document(members), CLASSES);
    }

    /** Asserts exactly one problem, whose message holds every fragment in {@code named}. */
    private static void rejects(String key, String value, String... named) {
        List<String> problems = problems(key, value);
        assertEquals(1, problems.size(), key + " " + value + " -> " + problems);
        for (String part : named) {
            assertTrue(problems.get(0).contains(part), "the message names '" + part + "': " + problems.get(0));
        }
    }

    @Test
    @DisplayName("a Playbook with every section, one with none, and one with a class override are accepted")
    void accepts() {
        assertEquals(List.of(), problems(), "every section is optional");
        assertEquals(List.of(), problems("bosses", "{}", "identify", "{\"reach\":8}",
                "upgrade", "{\"tie\":\"ARMOR\",\"weapon_lead\":-1}"));
        assertEquals(List.of(), problems("classes", "{\"MAGE\":{\"identify\":{\"reach\":3}},\"ROGUE\":{}}"));
        assertEquals(List.of(), problems("identify", "{\"escape_waits\":0,\"gain_odds_per_mille\":1000,\"healing_odds_per_mille\":1,"
                + "\"inventory_odds_per_mille\":250,\"known_gains\":0,\"potion_hp_per_mille\":1000,\"reach\":40,\"rest_waits\":500,\"scroll_hp_per_mille\":1}"), "the ends of every range");
    }

    @Test
    @DisplayName("the committed Playbooks are accepted")
    void the_committed_files() throws IOException {
        Path codex = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag());
        List<Path> files;
        try (Stream<Path> walk = Files.list(SeedSetsTest.ROOT.resolve(PlaybookFile.FOLDER))) {
            files = walk.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(!files.isEmpty(), "the repository commits a Playbook");
        for (Path file : files) {
            assertEquals(List.of(), PlaybookValidator.problems(PlaybookFile.text(file), PlaybookFile.heroClasses(codex)), file.toString());
        }
    }

    @Test
    @DisplayName("the hero classes come from hero-classes.json at the pinned tag")
    void hero_classes() {
        Path codex = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag());
        assertEquals(CLASSES, PlaybookFile.heroClasses(codex));
    }

    @Test
    @DisplayName("a seed, a salt or a seed string is rejected, wherever it sits")
    void no_seed() {
        rejects("seed", "12345", "seed names a seed");
        rejects("identify", "{\"salt\":7}", "identify.salt", "names a seed");
        rejects("upgrade", "{\"tie\":\"ABC-DEF-GHI\"}", "upgrade.tie", "seed string");
        rejects("classes", "{\"MAGE\":{\"identify\":{\"rng_seed\":1}}}", "rng_seed", "names a seed");
    }

    @Test
    @DisplayName("a dungeon coordinate, a cell index or a floor layout is rejected")
    void no_place() {
        rejects("identify", "{\"cell\":412}", "identify.cell", "place in one dungeon");
        rejects("upgrade", "{\"x\":3}", "upgrade.x", "place in one dungeon");
        rejects("layout", "{\"rooms\":4}", "layout", "place in one dungeon");
        rejects("bosses", "{\"goo_pump_cell\":9}", "bosses.goo_pump_cell", "place in one dungeon");
    }

    @Test
    @DisplayName("a hero class that hero-classes.json does not list is rejected")
    void hero_class_must_exist() {
        rejects("classes", "{\"WIZARD\":{\"identify\":{\"reach\":3}}}", "classes.WIZARD", "not a hero class");
        rejects("classes", "{\"warrior\":{}}", "classes.warrior", "not a hero class");
    }

    @Test
    @DisplayName("an unknown key is rejected, at the top, in a section and in a class entry")
    void no_unknown_key() {
        rejects("tactics", "{}", "tactics", "not a key the Playbook format defines");
        rejects("identify", "{\"patience\":3}", "identify.patience", "not a key the identify section defines");
        rejects("bosses", "{\"goo\":1}", "bosses.goo", "no field yet");
        rejects("classes", "{\"MAGE\":{\"bosses\":{}}}", "classes.MAGE.bosses", "not a key a class entry defines");
        rejects("classes", "{\"MAGE\":{\"upgrade\":{\"tying\":1}}}", "classes.MAGE.upgrade.tying", "not a key");
    }

    @Test
    @DisplayName("a number outside the range its field declares, or not a whole number, is rejected naming the range")
    void ranges() {
        rejects("identify", "{\"reach\":41}", "identify.reach", "from 0 to 40");
        rejects("identify", "{\"healing_odds_per_mille\":0}", "identify.healing_odds_per_mille", "from 1 to 1000");
        rejects("identify", "{\"known_gains\":2}", "identify.known_gains", "from 0 to 1");
        rejects("identify", "{\"potion_hp_per_mille\":0}", "identify.potion_hp_per_mille", "from 1 to 1000");
        rejects("identify", "{\"scroll_hp_per_mille\":1001}", "identify.scroll_hp_per_mille", "from 1 to 1000");
        rejects("upgrade", "{\"weapon_lead\":-6}", "upgrade.weapon_lead", "from -5 to 5");
        rejects("identify", "{\"reach\":8.5}", "identify.reach", "whole number");
        rejects("identify", "{\"reach\":\"8\"}", "identify.reach", "whole number");
        rejects("classes", "{\"MAGE\":{\"identify\":{\"rest_waits\":501}}}", "classes.MAGE.identify.rest_waits", "from 0 to 500");
    }

    @Test
    @DisplayName("an enum value the field does not allow is rejected, and so is a number where a name belongs")
    void enums() {
        rejects("upgrade", "{\"tie\":\"SWORD\"}", "upgrade.tie", "[ARMOR, WEAPON]");
        rejects("upgrade", "{\"tie\":\"RING\"}", "upgrade.tie", "[ARMOR, WEAPON]");
        rejects("upgrade", "{\"tie\":3}", "upgrade.tie", "[ARMOR, WEAPON]");
    }

    @Test
    @DisplayName("the format, the name and the version are required and typed")
    void header() {
        assertEquals(1, PlaybookValidator.problems("{\"name\":\"shatterfish\",\"version\":1}", CLASSES).size());
        assertEquals(1, PlaybookValidator.problems("{\"format\":2,\"name\":\"shatterfish\",\"version\":1}", CLASSES).size());
        assertEquals(1, PlaybookValidator.problems("{\"format\":1,\"name\":\"Shatter Fish\",\"version\":1}", CLASSES).size());
        assertEquals(1, PlaybookValidator.problems("{\"format\":1,\"name\":\"shatterfish\",\"version\":0}", CLASSES).size());
        assertEquals(1, PlaybookValidator.problems("not json", CLASSES).size());
    }
}
