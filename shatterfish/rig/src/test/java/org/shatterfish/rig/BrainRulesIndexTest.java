package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.brain.Brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain's Rules index (story 4.4, FR-17), held in both directions: every row points at a Rule
 * that exists, by the words that open it on the page it links; every Policy but the fallback and
 * every Safety flag is accounted for by some row; and the page's own list of rows resting on a
 * needs-review Rule is the list the Rule pages say.
 */
class BrainRulesIndexTest {

    /** A row's link: the area, the quoted opening words, and the page. */
    private static final Pattern RULE = Pattern.compile("\\[([a-z-]+): \"([^\"]+)\"\\]\\(rules/([a-z-]+)\\.md\\)");

    /** The page's statement of which rows rest on a needs-review Rule. */
    private static final Pattern NEEDS_REVIEW = Pattern.compile("^Rows resting on a needs-review Rule: (none|[0-9, ]+)\\.$");

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

    /** The row of {@code page} that opens with {@code opening}, if there is one. */
    private static Optional<String> ruleRow(Path page, String opening) throws IOException {
        return Files.readAllLines(page, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("| " + opening)).findFirst();
    }

    /** The Tier column of a Rule row: the third cell from the end. */
    private static String tier(String ruleRow) {
        String[] cells = ruleRow.split("\\|");
        return cells[cells.length - 2].strip();
    }

    @Test
    @DisplayName("every row of the index names a Rule that opens a row of the page it links, numbered in order")
    void every_claim_is_a_rule() throws IOException {
        List<String> rows = rows();
        assertFalse(rows.isEmpty(), "the index has rows");
        TreeSet<Integer> needsReview = new TreeSet<>();
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            assertTrue(row.startsWith("| " + (i + 1) + " |"), "rows are numbered in order: " + row);
            Matcher rule = RULE.matcher(row);
            assertTrue(rule.find(), "a row links a Rule as [area: \"opening words\"](rules/area.md): " + row);
            assertEquals(rule.group(1), rule.group(3), "the area named is the page linked: " + row);
            Path page = SeedSetsTest.ROOT.resolve("docs/rules/" + rule.group(3) + ".md");
            assertTrue(Files.isRegularFile(page), "the page exists: " + page);
            Optional<String> ruleRow = ruleRow(page, rule.group(2));
            assertTrue(ruleRow.isPresent(), "no row of " + page.getFileName() + " opens with \"" + rule.group(2) + "\"");
            assertFalse(row.split("\\|")[3].isBlank(), "a row says what uses the claim: " + row);
            if (tier(ruleRow.get()).equals("needs-review")) {
                needsReview.add(i + 1);
            }
        }
        // Reported, not refused: a Rule flips to needs-review at an upgrade, not in a Brain story.
        // The page has to say which of its rows rest on one, and say it truly, so a flip shows up
        // here as a named list of Brain behaviour to re-check rather than a silent staleness.
        String stated = Files.readAllLines(INDEX, StandardCharsets.UTF_8).stream()
                .map(NEEDS_REVIEW::matcher).filter(Matcher::matches).map(matcher -> matcher.group(1))
                .findFirst().orElseThrow(() -> new AssertionError("the index states the rows resting on a needs-review Rule"));
        TreeSet<Integer> said = new TreeSet<>();
        if (!stated.equals("none")) {
            for (String number : stated.split(",")) {
                said.add(Integer.parseInt(number.strip()));
            }
        }
        assertEquals(needsReview, said, "the rows resting on a needs-review Rule, as the Rule pages say them");
    }

    @Test
    @DisplayName("every Policy but the fallback, and every Safety flag, is used by some row")
    void every_heuristic_is_indexed() throws IOException {
        List<String> used = new ArrayList<>();
        for (String row : rows()) {
            used.add(row.split("\\|")[3]);
        }
        List<String> names = new ArrayList<>(Brain.policyNames());
        assertTrue(names.remove("fallback"), "the fallback is a Policy, and relies on no mechanic");
        names.addAll(Brain.safetyFlags());
        assertFalse(names.isEmpty());
        for (String name : names) {
            assertTrue(used.stream().anyMatch(column -> column.contains("`" + name + "`")),
                    "no row of the Brain's Rules index is used by `" + name + "`");
        }
    }
}
