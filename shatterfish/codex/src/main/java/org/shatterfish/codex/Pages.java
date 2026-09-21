package org.shatterfish.codex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Codex's pages (story 2.9): the same task that writes {@code codex/<tag>/} renders the site's
 * Codex section from what it just wrote, so that a page cannot describe a table that is no longer
 * there. {@link Generate#main(String[])} writes these under {@code docs/codex/} with the same
 * flat, stale-deleting {@link Generate#write(Map, Path)} the tables get, and
 * {@code CodexSeedFreeTest} compares the committed folder with a fresh rendering the way it
 * compares the tables; {@code CodexDocsTest} holds that every page has a nav entry in
 * {@code mkdocs.yml} and every Codex nav entry a page.
 *
 * <p>A page indexes its table; it does not repeat it. Rendering the entries into Markdown would
 * double what the repository commits -- 3.1 MB of JSON, 1.7 MB of it one table of 4,976 strings --
 * to restate bytes that are already committed, diffable and cited, and would make pages no reader
 * could use. So a page answers what a reader brings to a table: what is in it, how many entries
 * there are, how a row is shaped, which files of the pinned tree it was read from, and, in full,
 * the entries whose reader had to name a reason -- the judgments a human audits -- and links the
 * JSON for the rest.
 *
 * <p>Everything here is derived from the generated text, so a table added later is given a page
 * without a new rule: only the sentence saying what the table holds is written by hand, in
 * {@link #what(String)}, and a table with none still gets its page and says so.
 */
public final class Pages {

    /** The folder the pages are written to, relative to the repository root. */
    public static final String FOLDER = "docs/codex";

    /** The section's index page; every other page is a table's. */
    public static final String INDEX = "index.md";

    /** The one command that writes the tables and these pages. */
    static final String COMMAND = "./gradlew :codex:generate";

    /** Where a committed file is read on the forge, so a page can link the JSON it indexes. */
    static final String BLOB = "https://github.com/watchthelight/shatterfish/blob/main/";

    /** The longest string value a page prints beside a field rather than naming its type alone. */
    private static final int SHORT = 60;

    private Pages() {
    }

    /**
     * Every page's Markdown, by file name, in the order they are written: the index first, then one
     * page per table in the manifest's order. {@code tables} is what {@link Generate#generate(Path)}
     * returned; {@code root} is the checkout whose {@code build.gradle} names the tag, which must be
     * the tag the manifest carries.
     */
    public static Map<String, String> pages(Path root, Map<String, String> tables) {
        String manifestText = tables.get(Generate.MANIFEST);
        if (manifestText == null) {
            throw new IllegalArgumentException("the generated files have no " + Generate.MANIFEST);
        }
        Object manifest = parse(manifestText);
        String tag = string(member(manifest, "upstreamTag"));
        long version = number(member(manifest, "codexVersion"));
        String pinned = Upstream.tag(root);
        if (!pinned.equals(tag)) {
            throw new IllegalStateException("the manifest's tag " + tag + " is not the tag " + Upstream.BUILD_SCRIPT
                    + " declares, " + pinned);
        }
        List<Summary> summaries = new ArrayList<>();
        for (Object name : elements(member(manifest, "tables"))) {
            String table = string(name);
            String text = tables.get(table);
            if (text == null) {
                throw new IllegalStateException("the manifest lists " + table + ", which was not generated");
            }
            summaries.add(summarise(table, text));
        }
        // A citation into the other pinned game names a tree this repository never commits, so it
        // is opened where it is actually read: that repository, at the commit the pin names.
        String vanilla = Vanilla.blob(root);
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put(INDEX, index(tag, version, summaries));
        for (Summary summary : summaries) {
            if (INDEX.equals(summary.page())) {
                throw new IllegalStateException("the table " + summary.table() + " would overwrite the section's index");
            }
            pages.put(summary.page(), page(tag, version, vanilla, summary));
        }
        return pages;
    }

    // ---------------------------------------------------------------- rendering

    /** The section's index: the tag, the Codex version, and every table with its entries. */
    private static String index(String tag, long version, List<Summary> summaries) {
        StringBuilder out = new StringBuilder();
        out.append("# Codex\n\n");
        stamp(out, tag, version, Generate.FOLDER + "/" + tag + "/");
        out.append("The Codex is the general game knowledge the Brain is allowed to have (FR-14 to FR-17,\n")
                .append("[ADR-0017](../adr/0017-codex-generation-and-citations.md)) and the ground truth the lore\n")
                .append("pipeline's variant classifier checks against. One task reads the pinned upstream tag -- no Run,\n")
                .append("no seed, no Profile -- and writes both the tables under `").append(Generate.FOLDER).append("/").append(tag)
                .append("/` and the pages under this\nsection; continuous integration regenerates and fails the build if either has drifted, so a page\n")
                .append("can never describe a tag it does not come from. Every value is cited to the `path:line` it was\n")
                .append("read from, which is what a claim about the game is settled by\n([Fairness](../fairness.md)).\n\n");
        out.append("These pages index the tables; they do not repeat them. The entries are already committed,\n")
                .append("diffable and cited in the JSON each page links, and one table alone holds 4,976 of them.\n")
                .append("What a page adds is what a reader brings to a table: what it holds, how many entries there\n")
                .append("are, how a row is shaped, which files of the pinned tree it was read from, and, in full, the\n")
                .append("entries whose reader had to name a reason.\n\n");
        out.append("## The tables\n\n");
        out.append("An entry is an object in one of a table's own lists: the list the file is, where the file is a\n")
                .append("list, or the lists the object at its root holds. A number a table states that is not an entry\n")
                .append("-- a threshold, an expression, a tag -- is a value, and that table's page names it.\n\n");
        out.append("| Table | Entries | Page | JSON |\n|---|---:|---|---|\n");
        for (Summary summary : summaries) {
            out.append("| `").append(summary.table()).append("` | ").append(summary.entries())
                    .append(" | [").append(summary.title()).append("](").append(summary.page()).append(") | [")
                    .append(kilobytes(summary.bytes())).append("](").append(BLOB).append(Generate.FOLDER).append("/").append(tag)
                    .append("/").append(summary.table()).append(") |\n");
        }
        out.append('\n');
        out.append("Codex version ").append(version).append(" names the shape of these tables; it is recorded in a Run\n")
                .append("log's header ([ADR-0011](../adr/0011-run-log-format.md)), so a log says which Codex a Run was\n")
                .append("played with.\n");
        return out.toString();
    }

    /** One table's page. {@code vanilla} is where a line of the other pinned game is read. */
    private static String page(String tag, long version, String vanilla, Summary summary) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(summary.title()).append("\n\n");
        stamp(out, tag, version, Generate.FOLDER + "/" + tag + "/" + summary.table());
        out.append(what(summary.table())).append("\n\n");
        out.append("[`").append(Generate.FOLDER).append("/").append(tag).append("/").append(summary.table()).append("`](")
                .append(BLOB).append(Generate.FOLDER).append("/").append(tag).append("/").append(summary.table())
                .append(") holds ").append(many(summary.entries(), "entry", "entries")).append(" in ")
                .append(kilobytes(summary.bytes()))
                .append(". The entries are not repeated here: they are committed, diffable and cited in that file.\n\n");
        if (!summary.values().isEmpty()) {
            out.append("## What the table states of itself\n\n");
            out.append("The values the table carries beside its entries, by the path each is reached at.\n\n");
            out.append("| Field | Type | Value |\n|---|---|---|\n");
            for (Field value : summary.values()) {
                out.append("| `").append(value.path()).append("` | ").append(value.type()).append(" | ")
                        .append(value.value().isEmpty() ? "--" : "`" + value.value() + "`").append(" |\n");
            }
            out.append('\n');
        }
        out.append("## How a row is shaped\n\n");
        if (summary.sections().isEmpty()) {
            out.append("The table holds no list of entries.\n\n");
        }
        for (Section section : summary.sections()) {
            if (!section.name().isEmpty()) {
                out.append("### `").append(section.name()).append("`\n\n");
            }
            out.append(many(section.entries(), "entry", "entries"))
                    .append(". Every field any entry of this list holds, by the path it is reached at\n")
                    .append("(`[]` is a list's element):\n\n");
            out.append("| Field | Type |\n|---|---|\n");
            for (Field field : section.shape()) {
                out.append("| `").append(field.path()).append("` | ").append(field.type()).append(" |\n");
            }
            out.append('\n');
        }
        out.append("## Citations\n\n");
        if (summary.citations().isEmpty()) {
            out.append("The table cites no line: it is the generator's own record, not a reading of the pinned tree.\n\n");
        } else {
            long total = 0;
            for (long[] range : summary.citations().values()) {
                total += range[0];
            }
            out.append("Every value above was read from the pinned tree at the line the entry carries. The reader\n")
                    .append("recorded ").append(many(total, "citation", "citations")).append(" in ")
                    .append(many(summary.citations().size(), "file", "files")).append(":\n\n");
            out.append("| Source | Citations | Lines |\n|---|---:|---|\n");
            for (Map.Entry<String, long[]> file : summary.citations().entrySet()) {
                long[] range = file.getValue();
                out.append("| [`").append(file.getKey()).append("`](").append(source(vanilla, file.getKey())).append(") | ")
                        .append(range[0]).append(" | ").append(range[1])
                        .append(range[1] == range[2] ? "" : "-" + range[2]).append(" |\n");
            }
            out.append('\n');
        }
        out.append("## Judgments\n\n");
        if (summary.judgments().isEmpty()) {
            out.append("The reader named no reason in this table: every entry is a fact the pinned tree states, and\n")
                    .append("nothing was left out or decided.\n");
        } else {
            out.append("Where the reader could not simply read a value it recorded why, and every such entry is here\n")
                    .append("in full -- these are the judgments a human audits, and the list is short on purpose. ")
                    .append(summary.judgments().size()).append(" of the\ntable's ").append(summary.entries())
                    .append(summary.entries() == 1 ? " entry names" : " entries name").append(" a reason:\n\n");
            out.append("| Entry | Reason |\n|---|---|\n");
            for (Judgment judgment : summary.judgments()) {
                out.append("| `").append(judgment.what()).append("` | ").append(judgment.reason()).append(" |\n");
            }
        }
        return out.toString();
    }

    /** The line every page carries: where it came from, and that it is never hand edited. */
    private static void stamp(StringBuilder out, String tag, long version, String source) {
        out.append("!!! info \"Generated\"\n\n")
                .append("    From `").append(source).append("` at upstream tag `").append(tag)
                .append("`, Codex version ").append(version).append(".\n")
                .append("    Never hand edited: run `").append(COMMAND).append("` and commit what it writes.\n\n");
    }

    /**
     * Where a cited file is read. This repository's own tree is read here; a line of the other
     * pinned game is read in its own repository at the pinned commit, since {@code vanilla-src/}
     * is fetched and never committed, and a link into it here would open nothing.
     */
    private static String source(String vanilla, String path) {
        return path.startsWith(Sources.VANILLA_ROOT)
                ? vanilla + path.substring(Sources.VANILLA_ROOT.length())
                : BLOB + path;
    }

    /** A count with the word for it, so that a page says one file rather than 1 files. */
    private static String many(long count, String one, String more) {
        return count + " " + (count == 1 ? one : more);
    }

    /** A byte count as a page says it. */
    private static String kilobytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        long tenths = (bytes * 10 + 512) / 1024;
        if (tenths < 10_240) {
            return tenths / 10 + "." + tenths % 10 + " KB";
        }
        long megaTenths = (bytes * 10 + 524_288) / 1_048_576;
        return megaTenths / 10 + "." + megaTenths % 10 + " MB";
    }

    /**
     * What a table holds, in the words of the story that added it. A table with no sentence here
     * still gets its page: the shape, the citations and the judgments below are read from the table
     * itself, and only this paragraph is written by hand.
     */
    static String what(String table) {
        return switch (table) {
            case "assets.json" -> "Every asset path the game names: the constants of its asset class and the literal strings\n"
                    + "loaded outside it, each with the group it belongs to, the asset root it is resolved against,\n"
                    + "and whether a file was there when the table was read (story 2.7).";
            case "challenges.json" -> "The nine challenge flags in the api's order, which is the declaration order of the game's\n"
                    + "`Challenges`, each with the bit the game stores it under. The count, the union and the set are\n"
                    + "checked against the game's own `MAX_CHALS`, `MAX_VALUE` and `MASKS`, so a flag the game adds\n"
                    + "stops the generation rather than going missing (story 2.1).";
            case "changelog.json" -> "Every entry of the game's changelist with the headings under it, the date where an entry or a\n"
                    + "heading states one, and the version the tree builds as beside the save codes it still reads\n"
                    + "(story 2.7).";
            case "combat.json" -> "Combat measured rather than transcribed (story 2.5): the spread of every weapon's own damage\n"
                    + "roll by level, of the engine's own absorption roll with each armour worn, and of every mob's\n"
                    + "own damage reduction, each naming the method that was run, citing it, and carrying the samples\n"
                    + "behind it. The table that decides whether an attack lands names its method and says plainly\n"
                    + "that a generator which may not boot cannot run it.";
            case "decks.json" -> "The item generator's categories with their two deck weights and their classes' weights, the\n"
                    + "three appearance-label pools, and the exotic swap with its chance (story 2.3).";
            case "documents.json" -> "The journal's guides and lore with their pages in the game's own order and the words of each\n"
                    + "(story 2.7).";
            case "guarantees.json" -> "Every limited drop the level's creation decides -- the strength potions, the upgrade scrolls,\n"
                    + "the styli, the two stones, the trinket catalyst, the laboratory -- as the game's method text and\n"
                    + "as a schedule: the exact chance, for every depth and counter state, that the drop is needed and\n"
                    + "placed, what level class each floor of the main branch is and whether it places the floor's\n"
                    + "spawn list at all, and the Forbidden Runes rule (story 2.4).";
            case "hero-classes.json" -> "The six hero classes in the game's declaration order, each with its subclasses in the order\n"
                    + "the constructor lists them (story 2.1).";
            case "items.json" -> "Every concrete item class a player can meet with its display name from the bundle, the deck\n"
                    + "that lists it, its value, its strength requirement at level 0 with the formula, and the actions\n"
                    + "it offers a fresh instance. The identifiable potions, scrolls and rings are read from source\n"
                    + "instead, since their icons need the toolkit, and each says so (story 2.3).";
            case "levels.json" -> "What every depth of every branch builds and what an unnamed depth builds, which floors place a\n"
                    + "shop and where that was decided, which are boss floors, which seal behind the hero and how, and\n"
                    + "the level feelings with their chances, their arms and every place the game reads them\n"
                    + "(story 2.6).";
            case "mobs.json" -> "Every concrete mob class of the game with its hit points, defense skill, experience, maximum\n"
                    + "level, alignment, properties, loot and the three rolls as cited expressions, and its variants by\n"
                    + "depth and by challenge -- the depth-scaled mobs and the Stronger Bosses variants (story 2.2).";
            case "recipes.json" -> "The alchemy pot's registries in the order its own method tries them, each recipe with its\n"
                    + "inputs, output and energy cost where it states them, and with the text of the methods it answers\n"
                    + "with where it does not (story 2.6).";
            case "rooms.json" -> "The special and secret rooms with the game's lists, what each puts on the floor -- keys,\n"
                    + "solution potions, a honeypot at a coin -- and what it draws (story 2.4).";
            case "spawn-rotation.json" -> "The standard spawn rotation per depth, the random families with their odds, the rare\n"
                    + "additions, the alternates and the champion rule (story 2.2).";
            case "strings.json" -> "Every line of the nine English bundles with the class the game's key rule names, and the\n"
                    + "reason where it names none (story 2.7).";
            case "tiers.json" -> "The floor-set tier table with the armor, weapon and missile rules that draw by it\n"
                    + "(story 2.4).";
            case "traps.json" -> "Every concrete trap class with the two flags a player can act on, whether the game leaves it\n"
                    + "active, the text of its own effect and whose effect that is, what else the placing class puts on\n"
                    + "the cell, and the pool each level draws from with the class that declares it, the condition that\n"
                    + "chooses it and how many traps the floor lays (story 2.6).";
            case "vocabulary.json" -> "The one table read from two pinned games: every display name either this game or vanilla Pixel\n"
                    + "Dungeon gives a mob or an item, which of them has it, the classes that carry it on each side\n"
                    + "with their citations, and the mechanics the two state differently. Nothing reads it; it is the\n"
                    + "input the variant classifier of epic 7 will use (story 2.8).";
            default -> "No sentence saying what this table holds has been written for it yet; the shape, the citations\n"
                    + "and the judgments below are read from the table itself. Add the sentence in\n"
                    + "`shatterfish/codex/src/main/java/org/shatterfish/codex/Pages.java`.";
        };
    }

    /** A table's page name: its file name with Markdown's extension. */
    static String page(String table) {
        if (!table.endsWith(".json")) {
            throw new IllegalStateException("a table is a JSON file: " + table);
        }
        return table.substring(0, table.length() - ".json".length()) + ".md";
    }

    /** A table's title: its name with the hyphens opened out and the first letter raised. */
    static String title(String table) {
        String name = table.substring(0, table.length() - ".json".length()).replace('-', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ---------------------------------------------------------------- reading a table

    /** What a page says about one table, read from the text the generator wrote. */
    private static Summary summarise(String table, String text) {
        Object value = parse(text);
        List<Section> sections = new ArrayList<>();
        List<Field> values = new ArrayList<>();
        long entries = 0;
        if (value instanceof List<?> list) {
            sections.add(new Section("", list.size(), shape(list)));
            entries += list.size();
        } else if (value instanceof Map<?, ?> object) {
            for (Map.Entry<?, ?> member : object.entrySet()) {
                String name = String.valueOf(member.getKey());
                Object held = member.getValue();
                if (held instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                    sections.add(new Section(name, list.size(), shape(list)));
                    entries += list.size();
                } else {
                    values(name, held, values);
                }
            }
        } else {
            throw new IllegalStateException(table + " is neither a list nor an object");
        }
        Map<String, long[]> citations = new TreeMap<>();
        List<Judgment> judgments = new ArrayList<>();
        walk(value, "", citations, judgments);
        return new Summary(table, page(table), title(table), entries,
                text.getBytes(StandardCharsets.UTF_8).length, sections, values, citations, judgments);
    }

    /** Every field any element of {@code list} holds, by the path it is reached at, first seen first. */
    private static List<Field> shape(List<?> list) {
        Map<String, String> types = new LinkedHashMap<>();
        for (Object element : list) {
            shape(element, "", types);
        }
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<String, String> field : types.entrySet()) {
            fields.add(new Field(field.getKey(), field.getValue(), ""));
        }
        return fields;
    }

    private static void shape(Object value, String path, Map<String, String> types) {
        if (value instanceof Map<?, ?> object) {
            if (!path.isEmpty()) {
                note(types, path, "object");
            }
            for (Map.Entry<?, ?> member : object.entrySet()) {
                String key = String.valueOf(member.getKey());
                shape(member.getValue(), path.isEmpty() ? key : path + "." + key, types);
            }
        } else if (value instanceof List<?> list) {
            note(types, path, "list");
            for (Object element : list) {
                shape(element, path + "[]", types);
            }
        } else {
            note(types, path, type(value));
        }
    }

    /** The type at {@code path}, joined where two entries shape the same path differently. */
    private static void note(Map<String, String> types, String path, String type) {
        String seen = types.get(path);
        if (seen == null) {
            types.put(path, type);
        } else if (!seen.contains(type)) {
            types.put(path, seen + " or " + type);
        }
    }

    /**
     * What the table states of itself at {@code path}: a value that is not one of the table's own
     * lists of entries, flattened to one row per leaf, with the value itself where it is short
     * enough to be read in a cell rather than only named by its type.
     */
    private static void values(String path, Object value, List<Field> into) {
        if (value instanceof Map<?, ?> object) {
            if (object.isEmpty()) {
                into.add(new Field(path, "object", ""));
                return;
            }
            if (cited(object)) {
                String file = String.valueOf(object.get("path"));
                into.add(new Field(path, "citation", file.substring(file.lastIndexOf('/') + 1) + ":" + object.get("line")));
                return;
            }
            for (Map.Entry<?, ?> member : object.entrySet()) {
                values(path + "." + member.getKey(), member.getValue(), into);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                into.add(new Field(path, "empty list", ""));
                return;
            }
            String element = type(list.get(0));
            if ("object".equals(element) || "list".equals(element)) {
                into.add(new Field(path, "list of " + element, ""));
                return;
            }
            StringBuilder joined = new StringBuilder();
            for (Object held : list) {
                String one = printed(held);
                if (one.isEmpty()) {
                    joined.setLength(0);
                    break;
                }
                joined.append(joined.length() == 0 ? "" : ", ").append(one);
            }
            String all = joined.toString();
            into.add(new Field(path, "list of " + element, all.length() <= SHORT ? all : ""));
            return;
        }
        into.add(new Field(path, type(value), printed(value)));
    }

    /** A citation as every table writes one: a path and the line it was read on, and nothing else. */
    private static boolean cited(Map<?, ?> object) {
        return object.size() == 2 && object.get("path") instanceof String && object.get("line") instanceof Long;
    }

    /** A value as a cell holds it, or nothing where it is too long or would break the table. */
    private static String printed(Object value) {
        if (value instanceof Long || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof String held && !held.isEmpty() && held.length() <= SHORT
                && held.indexOf('`') < 0 && held.indexOf('|') < 0 && held.indexOf('\n') < 0) {
            return held;
        }
        return "";
    }

    private static String type(Object value) {
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Long) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return "null";
    }

    /**
     * Walks the whole table once for the two things a page states that a shape cannot: the files
     * the reader cited, which is any object of exactly a {@code path} and a {@code line}, and the
     * entries whose reader named a reason, which is any object with a {@code reason} that is not
     * empty.
     */
    private static void walk(Object value, String where, Map<String, long[]> citations, List<Judgment> judgments) {
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                walk(list.get(i), where + "[" + i + "]", citations, judgments);
            }
            return;
        }
        if (!(value instanceof Map<?, ?> object)) {
            return;
        }
        if (cited(object)) {
            String path = String.valueOf(object.get("path"));
            long line = (Long) object.get("line");
            long[] range = citations.get(path);
            if (range == null) {
                citations.put(path, new long[] {1, line, line});
            } else {
                range[0]++;
                range[1] = Math.min(range[1], line);
                range[2] = Math.max(range[2], line);
            }
            return;
        }
        if (object.get("reason") instanceof String reason && !reason.isEmpty()) {
            judgments.add(new Judgment(where, names(object, where), cell(reason)));
        }
        for (Map.Entry<?, ?> member : object.entrySet()) {
            String key = String.valueOf(member.getKey());
            walk(member.getValue(), where.isEmpty() ? key : where + "." + key, citations, judgments);
        }
    }

    /** What an entry is called, by the first field an entry of any table is named by. */
    private static String names(Map<?, ?> object, String where) {
        for (String key : new String[] {"className", "key", "name", "constant", "method", "what", "path"}) {
            if (object.get(key) instanceof String named && !named.isEmpty()) {
                return cell(named);
            }
        }
        return cell(where);
    }

    /** Text as a Markdown table cell holds it. */
    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ").replace("`", "'");
    }

    private record Summary(String table, String page, String title, long entries, long bytes,
                           List<Section> sections, List<Field> values,
                           Map<String, long[]> citations, List<Judgment> judgments) {
    }

    private record Section(String name, long entries, List<Field> shape) {
    }

    private record Field(String path, String type, String value) {
    }

    private record Judgment(String where, String what, String reason) {
    }

    // ---------------------------------------------------------------- reading the JSON back

    /**
     * The JSON the Codex writes, read back: an object keeps the order it was written in (which
     * {@code JsonWriter} sorted), a number is an integer, and anything else is refused. Nothing
     * else in the repository reads JSON, and a page must be derived from the bytes that were
     * written rather than from a second pass over the game, or a page could agree with a table
     * that is not there.
     */
    static Object parse(String text) {
        Cursor cursor = new Cursor(text);
        cursor.spaces();
        Object value = cursor.value();
        cursor.spaces();
        if (cursor.at < text.length()) {
            throw new IllegalStateException("text after the JSON value at " + cursor.at);
        }
        return value;
    }

    private static Object member(Object object, String key) {
        if (!(object instanceof Map<?, ?> held) || !held.containsKey(key)) {
            throw new IllegalStateException("no member named " + key);
        }
        return held.get(key);
    }

    private static String string(Object value) {
        if (!(value instanceof String held)) {
            throw new IllegalStateException("not a string: " + value);
        }
        return held;
    }

    private static long number(Object value) {
        if (!(value instanceof Long held)) {
            throw new IllegalStateException("not a number: " + value);
        }
        return held;
    }

    private static List<?> elements(Object value) {
        if (!(value instanceof List<?> held)) {
            throw new IllegalStateException("not a list: " + value);
        }
        return held;
    }

    /** A place in the text being read. */
    private static final class Cursor {

        private final String text;
        private int at;

        Cursor(String text) {
            this.text = text;
        }

        void spaces() {
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
                    at++;
                } else {
                    return;
                }
            }
        }

        Object value() {
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            spaces();
            if (peek() == '}') {
                at++;
                return object;
            }
            while (true) {
                spaces();
                String key = string();
                spaces();
                expect(':');
                spaces();
                if (object.put(key, value()) != null) {
                    throw new IllegalStateException("the key " + key + " is written twice at " + at);
                }
                spaces();
                char next = peek();
                at++;
                if (next == '}') {
                    return object;
                }
                if (next != ',') {
                    throw new IllegalStateException("a comma or a brace was expected at " + (at - 1));
                }
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> array = new ArrayList<>();
            spaces();
            if (peek() == ']') {
                at++;
                return array;
            }
            while (true) {
                spaces();
                array.add(value());
                spaces();
                char next = peek();
                at++;
                if (next == ']') {
                    return array;
                }
                if (next != ',') {
                    throw new IllegalStateException("a comma or a bracket was expected at " + (at - 1));
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) {
                            throw new IllegalStateException("a short unicode escape at " + at);
                        }
                        out.append((char) Integer.parseInt(text, at, at + 4, 16));
                        at += 4;
                    }
                    default -> throw new IllegalStateException("an escape the Codex does not write at " + (at - 1));
                }
            }
        }

        private Long number() {
            int from = at;
            if (at < text.length() && text.charAt(at) == '-') {
                at++;
            }
            while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') {
                at++;
            }
            if (at == from || (at == from + 1 && text.charAt(from) == '-')) {
                throw new IllegalStateException("a value was expected at " + from);
            }
            if (at < text.length() && (text.charAt(at) == '.' || text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                throw new IllegalStateException("the Codex writes no floats, and one is at " + from);
            }
            return Long.valueOf(text.substring(from, at));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) {
                throw new IllegalStateException("a value was expected at " + at);
            }
            at += word.length();
            return value;
        }

        private char peek() {
            if (at >= text.length()) {
                throw new IllegalStateException("the JSON ends at " + at);
            }
            return text.charAt(at);
        }

        private char next() {
            char c = peek();
            at++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalStateException(c + " was expected at " + (at - 1));
            }
        }
    }
}
