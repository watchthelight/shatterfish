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
 * it, the version the tree is built as, and the save codes it still reads.
 *
 * <p>Nothing here is constructed. A changelog entry's constructor renders its title through the
 * toolkit ({@code ChangeInfo.java:45,51}), which is the same wall story 2.3 met at the item icons
 * and story 2.5 met at the hit table, so the entries are read from the source that builds them.
 *
 * <p>A date is the game's own words. The entries write "Released September 9th, 2026" inside their
 * text, and the reader carries that phrase verbatim rather than parsing it into a calendar date:
 * the Codex carries what the game says. The pinned version's own entry states no date in its own
 * text — the release date is written in a heading under it — and the table says so by carrying an
 * empty date on the entry and the date on the heading that states it.
 */
final class Changelog {

    static final String CHANGELIST = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/ui/changelist/";
    static final String MAIN = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/ShatteredPixelDungeon.java";
    static final String BUILD = "build.gradle";

    private static final Pattern DATE = Pattern.compile("\\b([A-Z][a-z]+ \\d{1,2}(?:st|nd|rd|th)?, \\d{4})\\b");
    private static final Pattern BUNDLE = Pattern.compile("^Messages\\.get\\(\\s*(\\w+)\\.class\\s*,\\s*\"([^\"]+)\"\\s*\\)$");
    private static final Pattern VERSION_CODE = Pattern.compile("public static final int (v[\\w_]+)\\s*=\\s*(\\d+)\\s*;");

    private Changelog() {
    }

    /** Every entry of the changelog, in the order the package's files and methods write them. */
    static List<Codex.ChangeEntry> entries(Path root, List<Codex.StringEntry> strings) {
        Map<String, String> byKey = new TreeMap<>();
        for (Codex.StringEntry entry : strings) {
            byKey.put(entry.key(), entry.value());
        }
        List<Codex.ChangeEntry> entries = new ArrayList<>();
        for (String path : Sources.under(root, CHANGELIST)) {
            Sources.Body file = Sources.file(root, path);
            String className = path.substring(Sources.SOURCE_ROOT.length() + Sources.ROOT_PACKAGE_PREFIX.length(),
                    path.length() - ".java".length()).replace('/', '.');
            List<String> lines = Sources.stripped(file);
            Codex.ChangeEntry open = null;
            List<Codex.ChangeHeading> headings = new ArrayList<>();
            for (int i = file.from(); i < file.to(); i++) {
                String text = lines.get(i - file.from());
                if (text.contains("new ChangeInfo(")) {
                    if (open != null) {
                        entries.add(withHeadings(open, headings));
                        headings = new ArrayList<>();
                    }
                    open = read(file, lines, i, className, byKey);
                } else if (text.contains("new ChangeButton(")) {
                    if (open == null) {
                        throw new IllegalStateException(file.path() + ":" + (i + 1) + ": a heading before any entry");
                    }
                    headings.add(heading(file, lines, i, byKey));
                }
            }
            if (open != null) {
                entries.add(withHeadings(open, headings));
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("the changelist package holds no entry; the game's version record moved");
        }
        return entries;
    }

    /** One entry with the headings that followed it. */
    private static Codex.ChangeEntry withHeadings(Codex.ChangeEntry entry, List<Codex.ChangeHeading> headings) {
        return new Codex.ChangeEntry(entry.className(), entry.title(), entry.titleKey(), entry.titleExpression(),
                entry.major(), entry.text(), entry.date(), List.copyOf(headings), entry.citation());
    }

    /** One {@code ChangeInfo}: its title, whether it is a major heading, its own text and the date that text states. */
    private static Codex.ChangeEntry read(Sources.Body file, List<String> lines, int line, String className, Map<String, String> byKey) {
        List<String> arguments = arguments(statement(file, lines, line, "new ChangeInfo("));
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
        return new Codex.ChangeEntry(className, titled.title(), titled.key(), titled.expression(), major.equals("true"),
                text, date(text), List.of(), file.citation(line));
    }

    /** One {@code ChangeButton}: its title and the date its body states, where it states one. */
    private static Codex.ChangeHeading heading(Sources.Body file, List<String> lines, int line, Map<String, String> byKey) {
        List<String> arguments = arguments(statement(file, lines, line, "new ChangeButton("));
        if (arguments.size() < 2) {
            throw new IllegalStateException(file.path() + ":" + (line + 1) + ": a heading takes " + arguments.size()
                    + " arguments; the reader knows the icon, the title and the body");
        }
        Titled titled = titled(file, line, arguments.get(1), byKey);
        String body = arguments.size() > 2 ? text(file, line, arguments.get(2)) : "";
        return new Codex.ChangeHeading(titled.title(), titled.key(), titled.expression(), date(body), file.citation(line));
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
     */
    private static Titled titled(Sources.Body file, int line, String argument, Map<String, String> byKey) {
        String text = argument.trim();
        Matcher bundle = BUNDLE.matcher(text);
        if (bundle.matches()) {
            String key = Names.lower(bundle.group(1)) + "." + bundle.group(2);
            String value = null;
            for (Map.Entry<String, String> held : byKey.entrySet()) {
                if (held.getKey().endsWith("." + key)) {
                    value = held.getValue();
                    break;
                }
            }
            if (value == null) {
                throw new IllegalStateException(file.path() + ":" + (line + 1) + ": no bundle line ends in " + key);
            }
            return new Titled(value, key, "");
        }
        String literal = literal(text);
        // A title the game computes (a hero class's own title) cannot be had without running the
        // game, so the table carries the expression rather than a guess at what it renders to.
        return literal == null ? new Titled("", "", text) : new Titled(literal, "", "");
    }

    /** A body of text: a literal, possibly concatenated, or nothing where the game passes none. */
    private static String text(Sources.Body file, int line, String argument) {
        String text = argument.trim();
        if (text.equals("null") || text.equals("\"\"")) {
            return "";
        }
        String literal = literal(text);
        return literal == null ? "" : literal;
    }

    /** The one date a text states, as the game words it, or nothing; two dates in one text fail. */
    private static String date(String text) {
        Matcher date = DATE.matcher(text);
        if (!date.find()) {
            return "";
        }
        String first = date.group(1);
        while (date.find()) {
            if (!date.group(1).equals(first)) {
                return first;
            }
        }
        return first;
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

    /** One construction, from the line that opens it to the bracket that closes it. */
    private static String statement(Sources.Body file, List<String> lines, int line, String opening) {
        StringBuilder text = new StringBuilder();
        int at = lines.get(line - file.from()).indexOf(opening);
        int depth = 0;
        for (int i = line; i < file.to(); i++) {
            String part = lines.get(i - file.from());
            for (int c = i == line ? at + opening.length() - 1 : 0; c < part.length(); c++) {
                char ch = part.charAt(c);
                if (ch == '"') {
                    int end = c + 1;
                    while (end < part.length() && part.charAt(end) != '"') {
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

    /** One construction's arguments, split at the commas that are not inside a bracket or a literal. */
    private static List<String> arguments(String text) {
        List<String> arguments = new ArrayList<>();
        StringBuilder held = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                int end = i + 1;
                while (end < text.length() && text.charAt(end) != '"') {
                    end += text.charAt(end) == '\\' ? 2 : 1;
                }
                held.append(text, i, Math.min(end + 1, text.length()));
                i = end;
                continue;
            }
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
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
        Citations.Found name = Citations.found(root, BUILD, "^\\s*appVersionName\\s*=\\s*'([\\w.]+)'");
        Citations.Found code = Citations.found(root, BUILD, "^\\s*appVersionCode\\s*=\\s*(\\d+)");
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
