package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ideas folder, held so that it cannot drift. An idea is one file, {@code docs/ideas/<yyyy-mm-dd>-<slug>.md},
 * whose first line is its {@code # Title}; the folder's index page carries a marker that
 * {@code docs/hooks/ideas_index.py} replaces with the list when the site is built, so no committed list
 * exists to go stale and two pull requests that each add an idea touch no common line. The old single
 * {@code docs/ideas.md} must not come back, because appending to it is what conflicted.
 */
class IdeasIndexTest {

    private static final Path FOLDER = SeedSetsTest.ROOT.resolve("docs/ideas");

    private static final Pattern NAME = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-[a-z0-9]+(-[a-z0-9]+)*\\.md");

    private static final String MARKER = "<!-- ideas-list -->";

    private static List<Path> ideas() throws IOException {
        try (Stream<Path> files = Files.list(FOLDER)) {
            return files.filter(file -> !file.getFileName().toString().equals("index.md")).sorted().toList();
        }
    }

    @Test
    @DisplayName("every idea is a dated, slugged file that starts with its title")
    void every_idea_is_well_formed() throws IOException {
        List<Path> ideas = ideas();
        assertFalse(ideas.isEmpty(), "the folder holds ideas");
        List<String> problems = new ArrayList<>();
        for (Path idea : ideas) {
            String name = idea.getFileName().toString();
            var match = NAME.matcher(name);
            if (!match.matches()) {
                problems.add(name + ": not <yyyy-mm-dd>-<kebab-slug>.md");
                continue;
            }
            int month = Integer.parseInt(match.group(2));
            int day = Integer.parseInt(match.group(3));
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                problems.add(name + ": not a date");
            }
            List<String> lines = Files.readAllLines(idea, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).startsWith("# ") || lines.get(0).length() < 4) {
                problems.add(name + ": the first line is not a '# Title'");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("the index page carries the marker the site build fills, and the hook and nav are wired")
    void index_is_built_not_written() throws IOException {
        String index = Files.readString(FOLDER.resolve("index.md"), StandardCharsets.UTF_8);
        assertTrue(index.contains(MARKER), "docs/ideas/index.md holds " + MARKER);
        assertFalse(index.contains("](20"), "the list is built, so the page links no idea by hand");
        String mkdocs = Files.readString(SeedSetsTest.ROOT.resolve("mkdocs.yml"), StandardCharsets.UTF_8);
        assertTrue(mkdocs.contains("docs/hooks/ideas_index.py"), "mkdocs.yml registers the hook");
        assertTrue(mkdocs.contains("Ideas: ideas/index.md"), "the nav points at the index");
        assertTrue(Files.isRegularFile(SeedSetsTest.ROOT.resolve("docs/hooks/ideas_index.py")));
        assertFalse(Files.exists(SeedSetsTest.ROOT.resolve("docs/ideas.md")),
                "docs/ideas.md is split into docs/ideas/: add a file there instead");
    }
}
