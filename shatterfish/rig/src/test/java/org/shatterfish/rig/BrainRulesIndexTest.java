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
    private static final Pattern NEEDS_REVIEW = Pattern.compile("^Rows resting on a needs-review Rule: (none|[a-z0-9, -]+)\\.$");

    /** A row's key: a stable kebab-case slug, which is also the anchor the row carries. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** A row's first cell: the anchor, then the slug in backticks. */
    private static final Pattern KEY = Pattern.compile("^\\| <a id=\"([^\"]+)\"></a>`([^`]+)` \\|.*");

    /** A reference to a row from elsewhere in the docs or the source: a link to its anchor on this page. */
    private static final Pattern REFERENCE = Pattern.compile("brain-rules\\.md#([A-Za-z0-9_-]+)");

    /** A reference from within this page itself. */
    private static final Pattern LOCAL_REFERENCE = Pattern.compile("\\]\\(#([A-Za-z0-9_-]+)\\)");

    /** The old numbering, which the slug replaced: "row 25", "rows 18 to 23". */
    private static final Pattern NUMBERED = Pattern.compile("(?i)\\brows? [0-9]+");

    private static final Path INDEX = SeedSetsTest.ROOT.resolve("docs/brain-rules.md");

    /** The index's table rows, without the header and the rule under it. */
    private static List<String> rows() throws IOException {
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(INDEX, StandardCharsets.UTF_8)) {
            if (KEY.matcher(line).matches() || line.matches("\\| [0-9]+ \\|.*")) {
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
    @DisplayName("every row of the index is keyed by a unique slug and names a Rule that opens a row of the page it links")
    void every_claim_is_a_rule() throws IOException {
        List<String> rows = rows();
        assertFalse(rows.isEmpty(), "the index has rows");
        TreeSet<String> needsReview = new TreeSet<>();
        TreeSet<String> seen = new TreeSet<>();
        for (String row : rows) {
            Matcher key = KEY.matcher(row);
            assertTrue(key.matches(), "a row starts with its anchor and slug, <a id=\"slug\"></a>`slug`: " + row);
            String slug = key.group(2);
            assertEquals(slug, key.group(1), "the anchor is the slug: " + row);
            assertTrue(SLUG.matcher(slug).matches(), "a slug is kebab-case: " + slug);
            assertTrue(seen.add(slug), "a slug names one row only: " + slug);
            Matcher rule = RULE.matcher(row);
            assertTrue(rule.find(), "a row links a Rule as [area: \"opening words\"](rules/area.md): " + row);
            assertEquals(rule.group(1), rule.group(3), "the area named is the page linked: " + row);
            Path page = SeedSetsTest.ROOT.resolve("docs/rules/" + rule.group(3) + ".md");
            assertTrue(Files.isRegularFile(page), "the page exists: " + page);
            Optional<String> ruleRow = ruleRow(page, rule.group(2));
            assertTrue(ruleRow.isPresent(), "no row of " + page.getFileName() + " opens with \"" + rule.group(2) + "\"");
            assertFalse(row.split("\\|")[3].isBlank(), "a row says what uses the claim: " + row);
            if (tier(ruleRow.get()).equals("needs-review")) {
                needsReview.add(slug);
            }
        }
        // Reported, not refused: a Rule flips to needs-review at an upgrade, not in a Brain story.
        // The page has to say which of its rows rest on one, and say it truly, so a flip shows up
        // here as a named list of Brain behaviour to re-check rather than a silent staleness.
        String stated = Files.readAllLines(INDEX, StandardCharsets.UTF_8).stream()
                .map(NEEDS_REVIEW::matcher).filter(Matcher::matches).map(matcher -> matcher.group(1))
                .findFirst().orElseThrow(() -> new AssertionError("the index states the rows resting on a needs-review Rule"));
        TreeSet<String> said = new TreeSet<>();
        if (!stated.equals("none")) {
            for (String slug : stated.split(",")) {
                said.add(slug.strip());
            }
        }
        assertEquals(needsReview, said, "the rows resting on a needs-review Rule, as the Rule pages say them");
    }

    @Test
    @DisplayName("every reference to a row by slug, in the docs and the source, names a row; no row is cited by number")
    void every_reference_names_a_row() throws IOException {
        TreeSet<String> slugs = new TreeSet<>();
        for (String row : rows()) {
            Matcher key = KEY.matcher(row);
            assertTrue(key.matches(), row);
            slugs.add(key.group(2));
        }
        assertFalse(slugs.isEmpty());
        int checked = 0;
        List<Path> files = new ArrayList<>();
        for (Path folder : List.of(SeedSetsTest.ROOT.resolve("docs"), SeedSetsTest.ROOT.resolve("shatterfish"))) {
            try (var walk = Files.walk(folder)) {
                walk.filter(Files::isRegularFile)
                        .filter(file -> !file.toString().replace('\\', '/').contains("/build/"))
                        .filter(file -> file.toString().endsWith(".md") || file.toString().endsWith(".java"))
                        .forEach(files::add);
            }
        }
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher reference = REFERENCE.matcher(text);
            while (reference.find()) {
                checked++;
                assertTrue(slugs.contains(reference.group(1)),
                        file + " refers to a Brain Rules row that does not exist: " + reference.group(1));
            }
            if (file.equals(INDEX)) {
                Matcher local = LOCAL_REFERENCE.matcher(text);
                while (local.find()) {
                    checked++;
                    assertTrue(slugs.contains(local.group(1)), "the index refers to a row that does not exist: " + local.group(1));
                }
                assertFalse(NUMBERED.matcher(text).find(), "the index cites a row by number; use its slug");
            }
        }
        assertTrue(checked > 0, "the index and the source refer to its rows by slug somewhere");
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
