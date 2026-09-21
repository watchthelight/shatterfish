package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated documentation and the site's shape agree (story 2.9). The nav in
 * {@code mkdocs.yml} is the whole site's shape and stays human-owned -- a generator rewriting it
 * would make every unrelated nav edit a generated-file conflict -- so it is held by a test in both
 * directions instead: a generated page has a nav entry, and a nav entry has a page. That matters
 * because {@code mkdocs build --strict} fails a page no nav names, and this test names exactly
 * what to add before the docs build gets there.
 *
 * <p>The committed pages are read rather than rendered again: {@code CodexSeedFreeTest} already
 * holds that what is committed is what the generator writes, so reading the folder here is reading
 * the generator's output without generating a second time.
 */
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexDocsTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;
    private static final String NAV = "mkdocs.yml";

    /** Every page the site's nav names under the Codex section, in the order it names them. */
    private static final Pattern ENTRY = Pattern.compile("(?m)^\\s*-\\s*(?:[^:\\n]+:\\s*)?(" + Pages.FOLDER.substring("docs/".length())
            + "/[A-Za-z0-9._-]+\\.md)\\s*$");

    private static TreeSet<String> committedPages() throws IOException {
        TreeSet<String> pages = new TreeSet<>();
        try (Stream<Path> files = Files.list(ROOT.resolve(Pages.FOLDER))) {
            files.filter(Files::isRegularFile).forEach(f -> pages.add(f.getFileName().toString()));
        }
        return pages;
    }

    private static TreeSet<String> navigated() throws IOException {
        String nav = Files.readString(ROOT.resolve(NAV), StandardCharsets.UTF_8);
        assertTrue(nav.contains("- Codex:"), NAV + " has a Codex section in its nav");
        TreeSet<String> named = new TreeSet<>();
        Matcher entries = ENTRY.matcher(nav);
        while (entries.find()) {
            named.add(entries.group(1).substring("codex/".length()));
        }
        return named;
    }

    private static String page(String name) throws IOException {
        return Files.readString(ROOT.resolve(Pages.FOLDER).resolve(name), StandardCharsets.UTF_8);
    }

    /** The tables the committed manifest lists, read back from the bytes the generator wrote. */
    private static List<?> tables() throws IOException {
        Path manifest = ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT)).resolve(Generate.MANIFEST);
        Object read = Pages.parse(Files.readString(manifest, StandardCharsets.UTF_8));
        assertTrue(read instanceof Map<?, ?>, manifest + " is an object");
        Object listed = ((Map<?, ?>) read).get("tables");
        assertTrue(listed instanceof List<?>, manifest + " lists its tables");
        return (List<?>) listed;
    }

    @Test
    @DisplayName("every generated page has a nav entry in mkdocs.yml and every Codex nav entry has a generated page")
    void the_nav_and_the_pages_are_the_same_set() throws IOException {
        TreeSet<String> pages = committedPages();
        TreeSet<String> named = navigated();
        assertTrue(pages.contains(Pages.INDEX), Pages.FOLDER + "/" + Pages.INDEX + " is generated");
        TreeSet<String> unnamed = new TreeSet<>(pages);
        unnamed.removeAll(named);
        assertEquals(new TreeSet<String>(), unnamed,
                "these generated pages have no nav entry; add a line per page under `- Codex:` in " + NAV);
        TreeSet<String> missing = new TreeSet<>(named);
        missing.removeAll(pages);
        assertEquals(new TreeSet<String>(), missing,
                "the Codex nav in " + NAV + " names these, which the generator does not write; remove them or run "
                        + Pages.COMMAND);
    }

    @Test
    @DisplayName("a page per table the manifest lists, and the index links every one of them")
    void the_index_lists_every_table() throws IOException {
        String index = page(Pages.INDEX);
        TreeSet<String> pages = committedPages();
        TreeSet<String> expected = new TreeSet<>();
        expected.add(Pages.INDEX);
        for (Object table : tables()) {
            String name = String.valueOf(table);
            expected.add(Pages.page(name));
            assertTrue(index.contains("(" + Pages.page(name) + ")"),
                    Pages.INDEX + " links the page of " + name + "; run " + Pages.COMMAND);
            assertTrue(index.contains("`" + name + "`"), Pages.INDEX + " names " + name + "; run " + Pages.COMMAND);
            assertTrue(index.contains(Pages.BLOB + Generate.FOLDER + "/" + Upstream.tag(ROOT) + "/" + name),
                    Pages.INDEX + " links the JSON of " + name + "; run " + Pages.COMMAND);
        }
        assertEquals(expected, pages, "a page per table the manifest lists, and the section's index; run " + Pages.COMMAND);
    }

    @Test
    @DisplayName("every generated page states the tag and the Codex version it came from, and that it is never hand edited")
    void every_page_states_where_it_came_from() throws IOException {
        String tag = Upstream.tag(ROOT);
        Map<String, String> read = new TreeMap<>();
        for (String name : committedPages()) {
            read.put(name, page(name));
        }
        for (Map.Entry<String, String> named : read.entrySet()) {
            String text = named.getValue();
            assertTrue(text.contains("at upstream tag `" + tag + "`"), named.getKey() + " states the tag it came from");
            assertTrue(text.contains("Codex version " + Codex.VERSION + "."),
                    named.getKey() + " states the Codex version it came from");
            assertTrue(text.contains("Never hand edited: run `" + Pages.COMMAND + "`"),
                    named.getKey() + " says it is never hand edited and names the command that writes it");
        }
    }

    @Test
    @DisplayName("a table's page indexes the table rather than repeating it: the pages are a fraction of the JSON they describe")
    void the_pages_index_rather_than_repeat() throws IOException {
        long pages = 0;
        for (String name : committedPages()) {
            pages += Files.size(ROOT.resolve(Pages.FOLDER).resolve(name));
        }
        long json = 0;
        Path folder = ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT));
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                json += Files.size(file);
            }
        }
        assertTrue(pages * 2 < json, "the pages are " + pages + " bytes against the tables' " + json
                + "; a page that approaches its table's size is repeating it rather than indexing it (ADR-0017)");
    }
}
