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
 * The version record (story 2.7): every entry of the game's own changelog with the headings under
 * it, in the order the game shows them, plus the version the tree is built as and the save codes
 * it still reads.
 *
 * <p>Nothing here is constructed. A changelog entry's constructor renders its title through the
 * toolkit ({@code ChangeInfo.java:45,51}), which is the same wall story 2.3 met at the item icons
 * and story 2.5 met at the hit table, so the entries are read from the source that builds them.
 *
 * <p>A date is the game's own words. The game writes "Released September 9th, 2026" inside the text
 * of a heading, and the reader carries that phrase verbatim rather than parsing it into a calendar
 * date. No entry states a date in its own text at this tag: every entry is built with an empty
 * text, and every date the game states is in a heading. A heading's text can state several, and the
 * table carries all of them in the order they are written rather than the first of them.
 *
 * <p>A heading the game shows only under a condition carries that condition, because a table that
 * published it plainly would tell a reader the game shows something it does not.
 */
final class Changelog {

    static final String CHANGELIST = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/ui/changelist/";
    static final String MAIN = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/ShatteredPixelDungeon.java";
    static final String SCENE = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/scenes/ChangesScene.java";
    static final String BUILD = "build.gradle";

    private static final Pattern DATE = Pattern.compile("\\b([A-Z][a-z]+ \\d{1,2}(?:st|nd|rd|th)?, \\d{4})\\b");
    private static final Pattern BUNDLE = Pattern.compile("^Messages\\.get\\(\\s*([\\w.]+)\\.class\\s*,\\s*\"([^\"]+)\"\\s*\\)$");
    private static final Pattern VERSION_CODE = Pattern.compile("public static final int (v[\\w_]+)\\s*=\\s*(\\d+)\\s*;");
    private static final Pattern SHOWS = Pattern.compile("\\b(\\w+)\\.addAllChanges\\s*\\(");
    private static final Pattern GATE = Pattern.compile("^\\s*(?:\\}\\s*else\\s+)?if \\((.*)\\)\\s*\\{\\s*$");

    private Changelog() {
    }

    /**
     * The order the game shows its changelog in, read from the scene that shows it: newest first,
     * with the oldest tab holding several files. A table published in the order a file system lists
     * the package would be the changelog backwards.
     */
    static List<String> order(Path root) {
        Sources.Body scene = Sources.file(root, SCENE);
        int switches = scene.find("switch \\(changesSelected\\)");
        if (switches < 0) {
            throw new IllegalStateException("the changes scene no longer chooses a tab where the reader looks");
        }
        List<String> shown = new ArrayList<>();
        Matcher shows = SHOWS.matcher(Sources.text(scene.block(switches)));
        while (shows.find()) {
            if (!shown.contains(shows.group(1))) {
                shown.add(shows.group(1));
            }
        }
        if (shown.isEmpty()) {
            throw new IllegalStateException("the changes scene shows no changelog; the reader cannot say what order the game shows");
        }
        return shown;
    }

    /** Every entry of the changelog, in the order the game shows them. */
    static List<Codex.ChangeEntry> entries(Path root, List<Codex.StringEntry> strings) {
        Map<String, String> byKey = new TreeMap<>();
        for (Codex.StringEntry entry : strings) {
            byKey.put(entry.key(), entry.value());
        }
        List<String> shown = order(root);
        Map<String, String> paths = new TreeMap<>();
        for (String path : Sources.under(root, CHANGELIST)) {
            paths.put(path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length()), path);
        }
        List<Codex.ChangeEntry> entries = new ArrayList<>();
        for (int tab = 0; tab < shown.size(); tab++) {
            String path = paths.get(shown.get(tab));
            if (path == null) {
                throw new IllegalStateException("the changes scene shows " + shown.get(tab)
                        + ", which the changelist package does not hold");
            }
            entries.addAll(read(root, path, tab, byKey));
        }
        for (Map.Entry<String, String> held : paths.entrySet()) {
            if (!shown.contains(held.getKey()) && Sources.file(root, held.getValue()).find("public static void addAllChanges") >= 0) {
                throw new IllegalStateException(held.getValue()
                        + " writes changes the scene never shows; the reader does not know where they belong");
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("the changelist package holds no entry; the game's version record moved");
        }
        return entries;
    }

    /** Every entry one file of the package writes, with the headings that follow each. */
    private static List<Codex.ChangeEntry> read(Path root, String path, int tab, Map<String, String> byKey) {
        Sources.Body file = Sources.file(root, path);
        String className = path.substring(Sources.SOURCE_ROOT.length() + Sources.ROOT_PACKAGE_PREFIX.length(),
                path.length() - ".java".length()).replace('/', '.');
        List<String> lines = Sources.stripped(file);
        List<Codex.ChangeEntry> entries = new ArrayList<>();
        List<Codex.ChangeHeading> headings = new ArrayList<>();
        Codex.ChangeEntry open = null;
        List<String> conditions = new ArrayList<>();
        List<Integer> closes = new ArrayList<>();
        for (int i = file.from(); i < file.to(); i++) {
            while (!closes.isEmpty() && i >= closes.get(closes.size() - 1)) {
                closes.remove(closes.size() - 1);
                conditions.remove(conditions.size() - 1);
            }
            String text = lines.get(i - file.from());
            Matcher gate = GATE.matcher(text);
            if (gate.find()) {
                conditions.add(gate.group(1).trim());
                closes.add(file.block(i).to());
                continue;
            }
            // A line can open more than one construction, so every occurrence is read, not the first.
            for (int at = text.indexOf("new Change"); at >= 0; at = text.indexOf("new Change", at + 1)) {
                boolean isEntry = text.startsWith("new ChangeInfo(", at);
                if (!isEntry && !text.startsWith("new ChangeButton(", at)) {
                    continue;
                }
                String condition = String.join(" and ", conditions);
                if (isEntry) {
                    if (open != null) {
                        entries.add(withHeadings(open, headings));
                        headings = new ArrayList<>();
                    }
                    open = entry(file, lines, i, at, className, tab, condition, byKey);
                } else {
                    if (open == null) {
                        throw new IllegalStateException(file.path() + ":" + (i + 1) + ": a heading before any entry");
                    }
                    headings.add(heading(file, lines, i, at, condition, byKey));
                }
            }
        }
        if (open != null) {
            entries.add(withHeadings(open, headings));
        }
        return entries;
    }

    /** One entry with the headings that followed it. */
    private static Codex.ChangeEntry withHeadings(Codex.ChangeEntry entry, List<Codex.ChangeHeading> headings) {
        return new Codex.ChangeEntry(entry.className(), entry.tab(), entry.title(), entry.titleKey(), entry.titleExpression(),
                entry.major(), entry.text(), entry.dates(), entry.conditionExpression(), List.copyOf(headings), entry.citation());
    }

    /** One {@code ChangeInfo}: its title, whether it is a major heading, its own text and the dates that text states. */
    private static Codex.ChangeEntry entry(Sources.Body file, List<String> lines, int line, int at, String className,
                                           int tab, String condition, Map<String, String> byKey) {
        List<String> arguments = arguments(statement(file, lines, line, at, "new ChangeInfo("));
        if (arguments.size() != 3) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": an entry takes " + arguments.size()
                    + " arguments; the reader knows the title, the heading flag and the text");
        }
        String major = arguments.get(1).trim();
        if (!major.equals("true") && !major.equals("false")) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": an entry's heading flag is " + major
                    + ", which the reader cannot read");
        }
        Titled titled = titled(file, line, arguments.get(0), byKey);
        String text = text(file, line, arguments.get(2));
        return new Codex.ChangeEntry(className, tab, titled.title(), titled.key(), titled.expression(), major.equals("true"),
                text, dates(text), condition, List.of(), file.citation(line));
    }

    /**
     * One {@code ChangeButton}: its title and the dates its text states. The game's heading takes
     * its text as a varargs and several headings pass more than one string, so every argument past
     * the title is read; taking only the first dropped a third of the dates the game states.
     */
    private static Codex.ChangeHeading heading(Sources.Body file, List<String> lines, int line, int at,
                                               String condition, Map<String, String> byKey) {
        List<String> arguments = arguments(statement(file, lines, line, at, "new ChangeButton("));
        if (arguments.size() < 3) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": a heading takes " + arguments.size()
                    + " arguments; the reader knows the icon, the title and the text it shows");
        }
        Titled titled = titled(file, line, arguments.get(1), byKey);
        StringBuilder body = new StringBuilder();
        for (int i = 2; i < arguments.size(); i++) {
            body.append(body.length() == 0 ? "" : "\n").append(text(file, line, arguments.get(i)));
        }
        return new Codex.ChangeHeading(titled.title(), titled.key(), titled.expression(), dates(body.toString()),
                condition, file.citation(line));
    }

    /**
     * A title: the words themselves and the bundle key they came from, or the expression the game
     * computes them by where the reader cannot have the words without the toolkit.
     */
    private record Titled(String title, String key, String expression) {
    }

    /**
     * A title: the game writes most of them as a literal, takes a few from the bundle, and computes
     * a few from something only a running game has. The third kind is carried as its expression.
     * A bundle title carries the whole key the game's own rule builds, so the strings table can be
     * joined on it; two bundle lines that could answer to one name fail rather than the first winning.
     */
    private static Titled titled(Sources.Body file, int line, String argument, Map<String, String> byKey) {
        String text = argument.trim();
        Matcher bundle = BUNDLE.matcher(text);
        if (bundle.matches()) {
            String tail = Names.lower(bundle.group(1).replace('.', '$')) + "." + bundle.group(2);
            String found = null;
            for (Map.Entry<String, String> held : byKey.entrySet()) {
                if (held.getKey().endsWith("." + tail)) {
                    if (found != null) {
                        throw new IllegalStateException(file.path() + ":" + (line + 1) + ": two bundle lines end in " + tail
                                + ": " + found + " and " + held.getKey());
                    }
                    found = held.getKey();
                }
            }
            if (found == null) {
                throw new IllegalStateException(file.path() + ":" + (line + 1) + ": no bundle line ends in " + tail);
            }
            return new Titled(byKey.get(found), found, "");
        }
        String literal = literal(text);
        // A title the game computes (a hero class's own title) cannot be had without running the
        // game, so the table carries the expression rather than a guess at what it renders to.
        return literal == null ? new Titled("", "", text) : new Titled(literal, "", "");
    }

    /**
     * A body of text: a literal, possibly concatenated, or nothing where the game passes none. A
     * body the reader cannot read fails naming the line, since "the game passes no text" and "the
     * reader could not read this" are different facts and must not be published alike.
     */
    private static String text(Sources.Body file, int line, String argument) {
        String text = argument.trim();
        if (text.equals("null") || text.isEmpty()) {
            return "";
        }
        String literal = literal(text);
        if (literal == null) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": a text the reader cannot read: " + text);
        }
        return literal;
    }

    /** Every date a text states, as the game words it, in the order it writes them. */
    static List<String> dates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher date = DATE.matcher(text);
        while (date.find()) {
            if (!dates.contains(date.group(1))) {
                dates.add(date.group(1));
            }
        }
        return dates;
    }

    /** The value of a string literal, or of literals joined by {@code +}; null when the text is not that. */
    private static String literal(String text) {
        StringBuilder out = new StringBuilder();
        int at = 0;
        boolean any = false;
        while (at < text.length()) {
            while (at < text.length() && (Character.isWhitespace(text.charAt(at)) || text.charAt(at) == '+')) {
                at++;
            }
            if (at >= text.length()) {
                break;
            }
            if (text.charAt(at) != '"') {
                return null;
            }
            int end = at + 1;
            while (end < text.length() && text.charAt(end) != '"') {
                end += text.charAt(end) == '\\' ? 2 : 1;
            }
            if (end >= text.length()) {
                return null;
            }
            out.append(unescape(text.substring(at + 1, end)));
            any = true;
            at = end + 1;
        }
        return any ? out.toString() : null;
    }

    /** A literal's escapes as the compiler reads them, so the table carries the text the game shows. */
    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                case '\'' -> out.append('\'');
                default -> throw new IllegalStateException("an escape the reader does not know: \\" + next);
            }
        }
        return out.toString();
    }

    /**
     * One construction, from the bracket that opens it to the one that closes it. A string literal
     * and a character literal are both skipped whole, so a bracket inside either does not move the
     * depth: the module's own brace scanner does the same, and a second scanner that forgot
     * character literals would read a different file than the compiler does.
     */
    private static String statement(Sources.Body file, List<String> lines, int line, int at, String opening) {
        StringBuilder text = new StringBuilder();
        int depth = 0;
        for (int i = line; i < file.to(); i++) {
            String part = lines.get(i - file.from());
            for (int c = i == line ? at + opening.length() - 1 : 0; c < part.length(); c++) {
                char ch = part.charAt(c);
                if (ch == '"' || ch == '\'') {
                    int end = c + 1;
                    while (end < part.length() && part.charAt(end) != ch) {
                        end += part.charAt(end) == '\\' ? 2 : 1;
                    }
                    text.append(part, c, Math.min(end + 1, part.length()));
                    c = end;
                    continue;
                }
                if (ch == '(') {
                    depth++;
                    if (depth == 1) {
                        continue;
                    }
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        return text.toString();
                    }
                }
                if (depth >= 1) {
                    text.append(ch);
                }
            }
            text.append(' ');
        }
        throw new IllegalStateException(file.path() + ":" + (line + 1) + ": a construction does not close inside the file");
    }

    /** One construction's arguments, split at the commas outside a bracket, a brace and a literal. */
    private static List<String> arguments(String text) {
        List<String> arguments = new ArrayList<>();
        StringBuilder held = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < text.length() && text.charAt(end) != c) {
                    end += text.charAt(end) == '\\' ? 2 : 1;
                }
                held.append(text, i, Math.min(end + 1, text.length()));
                i = end;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(held.toString());
                held.setLength(0);
                continue;
            }
            held.append(c);
        }
        arguments.add(held.toString());
        return arguments;
    }

    /**
     * The version this tree builds as, and the save codes the game still reads. The build script is
     * opened through {@link Citations}, which is one of the few classes the gate lets read a file,
     * so the version record comes from the same door the tag does.
     */
    static Codex.VersionRecord version(Path root) {
        Citations.Found name = Citations.found(root, BUILD, "^\\s*appVersionName\\s*=\\s*'([\\w.]+)'\\s*$");
        Citations.Found code = Citations.found(root, BUILD, "^\\s*appVersionCode\\s*=\\s*(\\d+)\\s*$");
        Sources.Body main = Sources.file(root, MAIN);
        List<String> lines = Sources.stripped(main);
        List<Codex.Rule> codes = new ArrayList<>();
        for (int i = main.from(); i < main.to(); i++) {
            Matcher declared = VERSION_CODE.matcher(lines.get(i - main.from()));
            if (declared.find()) {
                codes.add(new Codex.Rule(declared.group(1), declared.group(2), main.citation(i)));
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalStateException("the game declares no save version code; the compatibility record moved");
        }
        return new Codex.VersionRecord(name.value(), Integer.parseInt(code.value()), codes, name.citation());
    }
}
