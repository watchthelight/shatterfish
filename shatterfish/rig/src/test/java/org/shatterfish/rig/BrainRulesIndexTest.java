package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain's Rules index (story 4.4, FR-17): every row points at a Rule that exists, by the words
 * that open it on the page it links, so the claims the Brain relies on can be counted and each one
 * followed to its citations.
 */
class BrainRulesIndexTest {

    /** A row's link: the area, the quoted opening words, and the page. */
    private static final Pattern RULE = Pattern.compile("\\[([a-z-]+): \"([^\"]+)\"\\]\\(rules/([a-z-]+)\\.md\\)");

    private static final Path INDEX = SeedSetsTest.ROOT.resolve("docs/brain-rules.md");

    /** The index's table rows, without the header and the rule under it. */
    private static List<String> rows() throws IOException {
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(INDEX, StandardCharsets.UTF_8)) {
            if (line.matches("\\| [0-9]+ \\|.*")) {
                rows.add(line);
            }
        }
        return rows;
    }

    @Test
    @DisplayName("every row of the index names a Rule that opens a row of the page it links, numbered in order")
    void every_claim_is_a_rule() throws IOException {
        List<String> rows = rows();
        assertFalse(rows.isEmpty(), "the index has rows");
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            assertTrue(row.startsWith("| " + (i + 1) + " |"), "rows are numbered in order: " + row);
            Matcher rule = RULE.matcher(row);
            assertTrue(rule.find(), "a row links a Rule as [area: \"opening words\"](rules/area.md): " + row);
            assertEquals(rule.group(1), rule.group(3), "the area named is the page linked: " + row);
            Path page = SeedSetsTest.ROOT.resolve("docs/rules/" + rule.group(3) + ".md");
            assertTrue(Files.isRegularFile(page), "the page exists: " + page);
            String opening = "| " + rule.group(2);
            assertTrue(Files.readAllLines(page, StandardCharsets.UTF_8).stream().anyMatch(line -> line.startsWith(opening)),
                    "no row of " + page.getFileName() + " opens with \"" + rule.group(2) + "\"");
            assertFalse(row.split("\\|")[3].isBlank(), "a row says what uses the claim: " + row);
        }
    }
}
