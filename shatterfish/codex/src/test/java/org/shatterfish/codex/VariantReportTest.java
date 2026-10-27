package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Results page of story 7.8 is generated from a run over the fixture and the claims and compared
 * with the committed copy, so a figure on it cannot be typed. The report reads the claims and never
 * writes one.
 */
class VariantReportTest {

    private static final Path ROOT = DocsCitations.root();

    @Test
    @DisplayName("the committed Results page is what a fresh run writes, byte for byte")
    void the_committed_page_has_not_drifted() throws IOException {
        Path page = ROOT.resolve(VariantReport.PAGE);
        assertTrue(Files.isRegularFile(page), VariantReport.PAGE + " is missing; run ./gradlew :codex:lore --args=variants");
        String committed = Files.readString(page, StandardCharsets.UTF_8);
        assertFalse(committed.contains("\r"), "a carriage return in the page");
        assertEquals(VariantReport.render(ROOT), committed,
                VariantReport.PAGE + " has drifted. Run `./gradlew :codex:lore --args=variants` and commit it.");
    }

    @Test
    @DisplayName("all 144 claims are classified, and reading them changes no file")
    void every_claim_is_classified_and_none_is_changed() {
        Path loreDir = ROOT.resolve(Lore.DIR);
        List<Path> files = Lore.files(loreDir);
        List<String> before = files.stream().map(Lore::read).toList();
        List<VariantReport.Classified> claims = VariantReport.claims(VariantClassifier.of(ROOT, Upstream.tag(ROOT)), loreDir);
        assertEquals(144, claims.size());
        assertEquals(before, files.stream().map(Lore::read).toList(), "the report writes nothing");
        for (VariantReport.Classified c : claims) {
            assertFalse(c.result().evidence().isEmpty(), c.id());
        }
    }
}
