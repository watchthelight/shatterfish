package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The journal documents (story 2.7): the guides and the lore the game hands a player a page at a
 * time, with each document's pages in the order the game keeps them and each page's title and body
 * as the bundle gives them.
 *
 * <p>Read from source. The enum's constants name sprite sheet entries, which build a texture film
 * at class initialisation, and a generator that may not boot the toolkit cannot construct one —
 * the same wall story 2.3 met at the item icons. The page order is the order the game's own static
 * block puts them in, which is what a page index means, so it is read in that order.
 *
 * <p>No page state is read. Whether a page has been found is meta-progression, saved outside a Run
 * and outside this table's subject: the Codex says what pages exist and what they say.
 */
final class Documents {

    static final String DOCUMENT = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/journal/Document.java";

    private static final Pattern CONSTANT = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*\\(\\s*([\\w.]+)\\s*,\\s*(true|false)\\s*\\)\\s*[,;]");
    private static final Pattern PAGE = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\.pagesStates\\.put\\(\\s*([\\w\"]+)\\s*,");
    private static final Pattern NAMED = Pattern.compile("static final String (\\w+)\\s*=\\s*\"([^\"]+)\"");

    private Documents() {
    }

    /** Every document, with its pages in the order the game keeps them. */
    static List<Codex.DocumentEntry> entries(Path root) {
        Sources.Body file = Sources.file(root, DOCUMENT);
        List<String> lines = Sources.stripped(file);
        Map<String, String> constants = new TreeMap<>();
        for (int i = file.from(); i < file.to(); i++) {
            Matcher named = NAMED.matcher(lines.get(i - file.from()));
            if (named.find()) {
                constants.put(named.group(1), named.group(2));
            }
        }
        Map<String, Boolean> lore = new java.util.LinkedHashMap<>();
        Map<String, Integer> declared = new TreeMap<>();
        Sources.Body body = file.block(file.declaration("Document"));
        for (int i = body.from(); i < body.to(); i++) {
            Matcher constant = CONSTANT.matcher(lines.get(i - file.from()));
            if (constant.find()) {
                lore.put(constant.group(1), Boolean.parseBoolean(constant.group(3)));
                declared.put(constant.group(1), i);
            }
            if (lines.get(i - file.from()).contains("Document(") && lines.get(i - file.from()).contains("int sprite")) {
                break;
            }
        }
        if (lore.isEmpty()) {
            throw new IllegalStateException("the game declares no journal document; the reader looks in the wrong place");
        }
        Map<String, List<String>> pages = new TreeMap<>();
        for (int i = file.from(); i < file.to(); i++) {
            Matcher page = PAGE.matcher(lines.get(i - file.from()));
            if (!page.find()) {
                continue;
            }
            String document = page.group(1);
            if (!lore.containsKey(document)) {
                throw new IllegalStateException(file.path() + ":" + (i + 1) + ": a page of " + document
                        + ", which the game declares no document for");
            }
            String named = page.group(2);
            String name = named.startsWith("\"") ? named.substring(1, named.length() - 1) : constants.get(named);
            if (name == null) {
                throw new IllegalStateException(file.path() + ":" + (i + 1) + ": a page named " + named
                        + ", which the reader cannot resolve to a page name");
            }
            pages.computeIfAbsent(document, d -> new ArrayList<>()).add(name);
        }
        List<Codex.DocumentEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Boolean> document : lore.entrySet()) {
            String name = document.getKey();
            List<String> named = pages.get(name);
            if (named == null || named.isEmpty()) {
                throw new IllegalStateException(name + " is a document with no page; the reader does not know what it holds");
            }
            String key = "journal.document." + Names.lower(name);
            Names.Named title = Names.lookup(root, key + ".title");
            // Only the documents a player has to find carry a hint; a guide is handed over, so the
            // bundle gives it none and the game would render its missing-key marker for one.
            Names.Named hint = Names.find(root, key + ".discover_hint");
            List<Codex.DocumentPage> carried = new ArrayList<>();
            for (String page : named) {
                String pageKey = key + "." + Names.lower(page);
                Names.Named pageTitle = Names.lookup(root, pageKey + ".title");
                Names.Named pageBody = Names.lookup(root, pageKey + ".body");
                carried.add(new Codex.DocumentPage(page, pageTitle.value(), pageBody.value(), pageBody.citation()));
            }
            entries.add(new Codex.DocumentEntry(name, document.getValue(), title.value(),
                    hint == null ? "" : hint.value(), carried, file.citation(declared.get(name))));
        }
        return entries;
    }
}
