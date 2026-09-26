package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.brain.Brain;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eat Policy's food table is hand-written (story 4.9), keyed by the name the inventory shows. At
 * an upstream upgrade a food could be added or renamed, and a food the table does not name would
 * never be eaten without a word. This holds the table to the Codex at the pinned tag in both
 * directions: every food the Codex lists with an eat action is in the table or named as never eaten,
 * and every name in the table is a Codex food or one of the names the pasty takes on a holiday.
 */
class FoodCodexTest {

    private static Path codex(String file) {
        return SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(HeadlessBoot.pinnedTag()).resolve(file);
    }

    @Test
    @DisplayName("every food the Codex lists is in the table or never eaten, and the table names nothing else")
    void the_table_is_the_codex() throws IOException {
        Set<String> foods = new TreeSet<>();
        for (String raw : Json.array(CodexKnowledge.compact(Files.readString(codex("items.json"), StandardCharsets.UTF_8)))) {
            Map<String, String> item = Json.object(raw);
            String className = Json.string(Json.required(item, "className", "item"));
            if (className.startsWith("items.food.") && Json.array(Json.required(item, "actions", "item")).stream()
                    .map(Json::string).anyMatch("EAT"::equals)) {
                foods.add(Json.string(Json.required(item, "name", "item")));
            }
        }
        // The names the pasty shows on a holiday (Pasty.java:175-198), from the strings table.
        Set<String> holiday = new TreeSet<>();
        for (String raw : Json.array(CodexKnowledge.compact(Files.readString(codex("strings.json"), StandardCharsets.UTF_8)))) {
            Map<String, String> string = Json.object(raw);
            if (Json.string(Json.required(string, "className", "string")).equals("items.food.Pasty")
                    && Json.string(Json.required(string, "suffix", "string")).endsWith("_name")) {
                holiday.add(Json.string(Json.required(string, "value", "string")));
            }
        }
        assertEquals(9, holiday.size(), "the pasty's holiday names: " + holiday);
        assertTrue(foods.size() >= 14, "the Codex lists the foods: " + foods);

        for (String food : foods) {
            assertTrue(Brain.foods().containsKey(food) || Brain.uneaten().contains(food),
                    food + " is a food the Codex lists, and the eat Policy neither knows its energy nor refuses it");
        }
        for (String name : Brain.foods().keySet()) {
            assertTrue(foods.contains(name) || holiday.contains(name),
                    name + " is in the food table but is no food the Codex lists at " + HeadlessBoot.pinnedTag());
        }
        for (String name : Brain.uneaten()) {
            assertTrue(foods.contains(name), name + " is refused but is no food the Codex lists");
        }
    }
}
