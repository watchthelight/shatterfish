package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.brain.SafeTest;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worst-case check's table is hand-written (story 4.3), keyed by item class. At an upstream
 * upgrade a class could be renamed, and an identity the table no longer names would score as
 * harmless without a word. This holds every class the table scores to the Codex's item table at the
 * pinned tag, so the upgrade's regeneration fails here instead.
 */
class SafeTestCodexTest {

    @Test
    @DisplayName("every class the worst-case table scores is an item class in the pinned Codex")
    void the_table_names_real_classes() throws IOException {
        String tag = HeadlessBoot.pinnedTag();
        Path items = SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag).resolve("items.json");
        Set<String> classes = new HashSet<>();
        for (String raw : Json.array(CodexKnowledge.compact(Files.readString(items, StandardCharsets.UTF_8)))) {
            classes.add(Json.string(Json.required(Json.object(raw), "className", "item")));
        }
        for (String scored : SafeTest.CLASSES) {
            assertTrue(classes.contains(scored), scored + " is not an item class in the Codex for " + tag);
        }
    }
}
