package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The docs page of lore claims (story 7.7) is built from the files and compared with the committed
 * page, the way the Codex pages are (story 2.9): a claim added, edited or retired without the page
 * rebuilt fails the build and names the command.
 */
class LoreDocsTest {

    private static final Path ROOT = DocsCitations.root();

    @TempDir
    Path temp;

    @Test
    @DisplayName("docs/lore/index.md is what the claim files generate, byte for byte")
    void the_committed_page_has_not_drifted() throws IOException {
        String expected = LoreDocs.render(ROOT.resolve(Lore.DIR));
        Path page = ROOT.resolve(LoreDocs.PAGE);
        assertTrue(Files.isRegularFile(page), LoreDocs.PAGE + " is missing; run ./gradlew :codex:lore --args=docs");
        String committed = Files.readString(page, StandardCharsets.UTF_8);
        assertFalse(committed.contains("\r"), LoreDocs.PAGE + " has a carriage return");
        assertEquals(expected, committed, LoreDocs.PAGE + " has drifted from lore/. Run `./gradlew :codex:lore --args=docs` and commit it.");
    }

    @Test
    @DisplayName("the page lists every claim once, by topic and Tier, and counts them")
    void the_page_lists_every_claim() {
        String page = LoreDocs.render(ROOT.resolve(Lore.DIR));
        long files = Lore.files(ROOT.resolve(Lore.DIR)).size();
        long listed = page.lines().filter(l -> l.matches("\\| `[a-z-]+-\\d+` \\|.*")).count();
        assertEquals(files, listed, "one table row per claim file");
        assertTrue(page.contains("## Count by topic and Tier"));
        assertTrue(page.contains("| bestiary | sewers | "), "a row per group");
        assertTrue(page.contains("### Tier 1: the code confirms it"));
        assertTrue(page.contains("### Tier F: the code contradicts it"));
        assertTrue(page.contains("### Tier 3: not settled by the code"));
        assertTrue(page.contains("| **all** |  | "), "and a total");
    }

    @Test
    @DisplayName("a cell cannot be broken out of: a pipe, a bracket or a tag in a claim is escaped")
    void cells_are_escaped() throws IOException {
        Path file = temp.resolve("lore/bestiary/sewers-1.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                ---
                id: sewers-1
                topic: bestiary
                group: sewers
                source_url: https://example.org/a
                source_type: wiki
                date: unknown
                retrieved: 2026-10-26
                variant: spd
                version_claimed: "unspecified"
                tier: 3
                graded_against: v4.0.2
                status: current
                claim: "a | b <script> [x](y) *z*"
                cites: []
                implemented_in: []
                ---

                ## Verdict against `v4.0.2`

                None.
                """, StandardCharsets.UTF_8);
        String page = LoreDocs.render(temp.resolve("lore"));
        assertTrue(page.contains("a \\| b \\<script\\> \\[x\\](y) \\*z\\*"), page);
    }

    @Test
    @DisplayName("the site's navigation names the page")
    void the_nav_names_the_page() throws IOException {
        List<String> nav = Files.readAllLines(ROOT.resolve("mkdocs.yml"), StandardCharsets.UTF_8);
        assertTrue(nav.stream().anyMatch(l -> l.contains("lore/index.md")), "mkdocs.yml lists lore/index.md");
    }
}
