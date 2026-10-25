package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rows column of {@code docs/rules/index.md} is the number of rows on the page it links (issue
 * #140). The index is written by hand, so this is what stops a count drifting when a rule is added
 * or removed, and what stops a rules page from existing without an entry.
 */
class RulesIndexCountTest {

    private static final Path RULES = DocsCitations.root().resolve("docs/rules");

    /** An index row: the link to a page, then the count. */
    private static final Pattern ENTRY = Pattern.compile("^\\| \\[[^\\]]+\\]\\(([a-z-]+\\.md)\\) \\| ([0-9]+) \\|.*");

    /** A page's rule rows: table lines, less the header (the rule under it starts with {@code |-}). */
    private static int rows(Path page) throws IOException {
        int lines = 0;
        for (String line : Files.readAllLines(page, StandardCharsets.UTF_8)) {
            if (line.startsWith("| ")) {
                lines++;
            }
        }
        return lines - 1;
    }

    @Test
    @DisplayName("every rules page is listed in the index with the count of rows it has")
    void countsMatchPages() throws IOException {
        List<String> wrong = new ArrayList<>();
        TreeSet<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(RULES.resolve("index.md"), StandardCharsets.UTF_8)) {
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            listed.add(m.group(1));
            int actual = rows(RULES.resolve(m.group(1)));
            if (actual != Integer.parseInt(m.group(2))) {
                wrong.add(m.group(1) + ": index says " + m.group(2) + ", page has " + actual);
            }
        }
        assertTrue(wrong.isEmpty(), "docs/rules/index.md row counts drifted: " + wrong);
        TreeSet<String> pages = new TreeSet<>();
        try (var s = Files.list(RULES)) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md") && !n.equals("index.md"))
                    .forEach(pages::add);
        }
        assertEquals(pages, listed, "pages in docs/rules versus pages in the index");
    }
}
