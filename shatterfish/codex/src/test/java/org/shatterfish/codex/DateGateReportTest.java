package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Results page of story 7.9 is what a fresh run writes, and the site's navigation names it. */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DateGateReportTest {

    private static final Path ROOT = DocsCitations.root();

    @Test
    @DisplayName("the committed Results page is what a fresh run writes, byte for byte")
    void the_committed_page_has_not_drifted() throws IOException {
        Path page = ROOT.resolve(DateGateReport.PAGE);
        assertTrue(Files.isRegularFile(page), DateGateReport.PAGE + " is missing; run ./gradlew :codex:lore --args=gate");
        String committed = Files.readString(page, StandardCharsets.UTF_8);
        assertFalse(committed.contains("\r"), "carriage return in the page");
        assertEquals(DateGateReport.render(ROOT), committed,
                DateGateReport.PAGE + " has drifted. Run `./gradlew :codex:lore --args=gate` and commit it.");
    }

    @Test
    @DisplayName("the page opens with the finding, and the site's navigation and the results index name it")
    void the_finding_comes_first_and_the_page_is_linked() throws IOException {
        String committed = Files.readString(ROOT.resolve(DateGateReport.PAGE), StandardCharsets.UTF_8);
        assertTrue(committed.indexOf("## The finding") < committed.indexOf("## The release table"));
        assertTrue(committed.contains("### The five to read first"));
        String name = DateGateReport.PAGE.substring(DateGateReport.PAGE.lastIndexOf('/') + 1);
        List<String> nav = Files.readAllLines(ROOT.resolve("mkdocs.yml"), StandardCharsets.UTF_8);
        assertTrue(nav.stream().anyMatch(l -> l.contains(name)), "mkdocs.yml lists " + name);
        assertTrue(Files.readString(ROOT.resolve("docs/results/index.md"), StandardCharsets.UTF_8).contains(name),
                "docs/results/index.md lists " + name);
    }
}
