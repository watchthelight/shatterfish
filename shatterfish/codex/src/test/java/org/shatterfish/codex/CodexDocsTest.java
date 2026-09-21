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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    private static final Pattern ENTRY = Pattern.compile("(?m)^\\s*-\\s*(?:([^:\\n]+):\\s*)?[\"']?("
            + Pages.FOLDER.substring("docs/".length()) + "/[A-Za-z0-9._/-]+\\.md)[\"']?\\s*(?:#.*)?$");

    private static TreeSet<String> committedPages() throws IOException {
        TreeSet<String> pages = new TreeSet<>();
        try (Stream<Path> files = Files.list(ROOT.resolve(Pages.FOLDER))) {
            files.filter(Files::isRegularFile).forEach(f -> pages.add(f.getFileName().toString()));
        }
        return pages;
    }

    private static TreeSet<String> navigated() throws IOException {
        TreeSet<String> named = new TreeSet<>();
        Matcher entries = ENTRY.matcher(codexSection());
        while (entries.find()) {
            named.add(entries.group(2).substring("codex/".length()));
        }
        return named;
    }

    /**
     * The Codex section of the nav alone. Matching the whole file counted a page named anywhere
     * as navigated, so a generated page filed under Rules would have satisfied both directions
     * of the set comparison while the site's navigation said something else.
     */
    private static String codexSection() throws IOException {
        List<String> lines = Files.readAllLines(ROOT.resolve(NAV), StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("- Codex:")) {
                assertEquals(-1, start, NAV + " has one Codex section");
                start = i;
            }
        }
        assertTrue(start >= 0, NAV + " has a Codex section in its nav");
        int indent = lines.get(start).indexOf('-');
        StringBuilder out = new StringBuilder(lines.get(start)).append('\n');
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && line.indexOf('-') <= indent && !line.strip().startsWith("#")) {
                break;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** What the nav calls each page of the Codex section. */
    private static Map<String, String> navTitles() throws IOException {
        Map<String, String> titles = new TreeMap<>();
        Matcher entries = ENTRY.matcher(codexSection());
        while (entries.find()) {
            titles.put(entries.group(2).substring("codex/".length()), entries.group(1) == null ? "" : entries.group(1).strip());
        }
        return titles;
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
        // A count the renderer derived, checked against a count this test derives: the index says
        // how many entries mobs.json holds, and mobs.json is a list, so its length is the answer
        // and nothing about Pages decides it. Stories 2.7 and 2.8 each shipped a wrong table
        // behind an assertion that recomputed the reader's own predicate.
        Object mobs = Pages.parse(Files.readString(
                ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT)).resolve("mobs.json"), StandardCharsets.UTF_8));
        long counted = ((List<?>) mobs).size();
        assertTrue(index.contains("| `mobs.json` | " + counted + " |"),
                Pages.INDEX + " says mobs.json holds " + counted + " entries; run " + Pages.COMMAND);
        assertTrue(page("mobs.md").contains("holds " + counted + " entries"),
                "the mobs page says how many entries the table holds; run " + Pages.COMMAND);
        // And one page name written out, so that the set above is not Pages.page() twice over.
        assertTrue(pages.contains("mobs.md"), "the mobs table has a page");
    }

    @Test
    @DisplayName("the nav calls each page what the page calls itself, and no page ships asking to be written")
    void the_nav_names_the_pages_and_no_page_is_a_placeholder() throws IOException {
        Map<String, String> titles = navTitles();
        for (Object table : tables()) {
            String name = String.valueOf(table);
            assertEquals(Pages.title(name), titles.get(Pages.page(name)),
                    NAV + " calls " + Pages.page(name) + " what its page is titled");
        }
        // A table whose sentence nobody wrote still gets a page, and that page tells the reader
        // to go and edit a Java file. That is a build failure, not a published one.
        for (String name : committedPages()) {
            assertFalse(page(name).contains("No sentence saying what this table holds"),
                    name + " is a placeholder page: write the table's sentence in Pages.what()");
        }
    }

    @Test
    @DisplayName("the site still renders what the pages are written in, and the pages link what they say they link")
    void the_pages_and_the_site_agree() throws IOException {
        String nav = Files.readString(ROOT.resolve(NAV), StandardCharsets.UTF_8);
        // The provenance stamp is an admonition and every page is mostly tables; without these
        // the pages still pass every assertion here and render as literal markup on the site.
        assertTrue(nav.contains("- admonition"), NAV + " keeps the extension the pages' stamp is written in");
        assertTrue(nav.contains("- tables"), NAV + " keeps the extension the pages' tables are written in");
        // Where a page links this repository is where the site says the repository is.
        Matcher repository = Pattern.compile("(?m)^repo_url:\\s*(\\S+)\\s*$").matcher(nav);
        assertTrue(repository.find(), NAV + " names the repository");
        assertTrue(Pages.BLOB.startsWith(repository.group(1)),
                "the pages link " + Pages.BLOB + " and the site says the repository is " + repository.group(1));
        // And the command every page names is the task that writes them.
        String build = Files.readString(ROOT.resolve("shatterfish/codex/build.gradle"), StandardCharsets.UTF_8);
        assertTrue(build.contains("tasks.register('" + Pages.COMMAND.substring(Pages.COMMAND.lastIndexOf(':') + 1) + "'"),
                "the pages name `" + Pages.COMMAND + "`, which has to be the task that writes them");
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
    @DisplayName("every file a page links is a file that is there: this repository's own, or the other pinned game's at the commit the pin names")
    void every_linked_file_is_there() throws IOException {
        // A page that linked a path nothing holds would send a reader to a missing page, and the
        // second pinned game is the one tree that is read and never committed: its citations have
        // to be opened in its own repository at the commit vanilla.pin names.
        String vanilla = Vanilla.blob(ROOT);
        Pattern link = Pattern.compile("\\(((?:https://github\\.com/|\\.\\./)[^)\\s]+)\\)");
        int checked = 0;
        for (String name : committedPages()) {
            Matcher links = link.matcher(page(name));
            while (links.find()) {
                String target = links.group(1);
                if (target.startsWith("../")) {
                    Path relative = ROOT.resolve(Pages.FOLDER).resolve(target.split("#", 2)[0]).normalize();
                    assertTrue(Files.exists(relative), name + " links " + target + ", which is not there");
                } else if (target.startsWith(Pages.BLOB)) {
                    String path = target.substring(Pages.BLOB.length());
                    assertTrue(Files.exists(ROOT.resolve(path)), name + " links " + path + ", which this repository does not hold");
                } else if (target.startsWith(vanilla)) {
                    String path = target.substring(vanilla.length());
                    assertTrue(Files.exists(ROOT.resolve(Sources.VANILLA_ROOT).resolve(path)),
                            name + " links " + path + " of the other pinned game, which is not in the fetched tree");
                } else {
                    fail(name + " links " + target + ", which is neither this repository nor the pinned game the pin names");
                }
                checked++;
            }
        }
        assertTrue(checked > 0, "the pages link the sources they were read from");
    }

    @Test
    @DisplayName("a table's page indexes the table rather than repeating it: the pages are a fraction of the JSON they describe")
    void the_pages_index_rather_than_repeat() throws IOException {
        // A ratio of each page to its own table is not the rule, and asserting it would be a
        // false statement about small tables: a page carries a stamp, headings and prose whatever
        // its table holds, so the page of a 1 KB table is legitimately larger than the table. The
        // rule is that a page does not enumerate entries -- it holds a row per value, per field
        // of a row's shape, and per judgment, and never one per entry.
        Path folder = ROOT.resolve(Generate.FOLDER).resolve(Upstream.tag(ROOT));
        long pages = 0;
        long json = 0;
        for (Object table : tables()) {
            String name = String.valueOf(table);
            long page = Files.size(ROOT.resolve(Pages.FOLDER).resolve(Pages.page(name)));
            assertTrue(page < CEILING, Pages.page(name) + " is " + page + " bytes; a page is a map of its table"
                    + " and a map does not grow past " + CEILING + " (ADR-0017)");
            pages += page;
            json += Files.size(folder.resolve(name));
        }
        assertTrue(pages * 4 < json, "the pages are " + pages + " bytes against the tables' " + json
                + "; a page that approaches its table's size is repeating it rather than indexing it (ADR-0017)");
        // The table nothing could index by enumeration: 4,976 entries, and a page that states
        // them one per row would carry thousands. This is the assertion a page that started
        // repeating its table would fail first, and it is derived from the JSON, not the page.
        Object strings = Pages.parse(Files.readString(folder.resolve("strings.json"), StandardCharsets.UTF_8));
        long entries = ((List<?>) strings).size();
        long rows = page("strings.md").lines().filter(line -> line.startsWith("|")).count();
        assertTrue(rows * 10 < entries, "strings.md holds " + rows + " table rows for " + entries
                + " entries; a page that states an entry per row is repeating its table (ADR-0017)");
    }

    /** The largest a page may be: far under the smallest table a page could be repeating. */
    private static final long CEILING = 128 * 1024;
}
