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
 * The game's own words (story 2.7): every key of the nine English bundles with its value, the
 * bundle it came from, and the class the game's own key rule names.
 *
 * <p>The game builds a key from a class's fully qualified name with the root package removed,
 * lower-cased, the {@code $} of a nested class kept, plus the suffix the caller asked for
 * ({@code Messages.java:125-133}). This reads that rule backwards: the longest prefix of a key
 * that is a class the game compiles is that key's class, and the rest is the suffix. Plenty of
 * text is keyed to no class at all — a window, a button, a scene label — and is carried with the
 * reason rather than attached to whatever class happens to share a prefix.
 *
 * <p>Only the English bundles are read. The Codex is language-free, and story 2.2's live-Run test
 * already proves a generation under another language is byte for byte the same.
 */
final class Text {

    static final String BUNDLES = "core/src/main/assets/messages/";
    static final String MESSAGES = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/messages/Messages.java";
    static final String ASSETS = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/Assets.java";
    // Some keys name no class because the game keys the text to nothing (a window, a scene label);
    // others name a class the game no longer compiles and keep its text. The reader cannot tell
    // those apart, so it states what it read rather than guessing which of the two this is.
    static final String NO_CLASS = "no class the game compiles has this key's name";

    // A key is everything before the first equals sign. The game's keys are mostly a lower-cased
    // class name and a suffix, but not all of them are: a sheep says "baa!" under a key that ends
    // in one, so a reader that demanded word characters refused a line the game reads fine.
    private static final Pattern ENTRY = Pattern.compile("^([^=]+)=(.*)$");
    private static final Pattern CONSTANT = Pattern.compile("\\bAssets\\.Messages\\.(\\w+)\\b");
    private static final Pattern DECLARED = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|abstract|final|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|enum|interface|record)\\s+(\\w+)");

    private Text() {
    }

    /**
     * The bundles the game searches, in the order it searches them, read from the array it walks
     * rather than named here: a bundle a later tag adds would otherwise be missed in silence.
     */
    static List<String> bundles(Path root) {
        return bundles(Sources.file(root, MESSAGES), Sources.file(root, ASSETS));
    }

    /**
     * The same, from two bodies. The game's nine bundles are in alphabetical order as it happens,
     * so a reader that sorted them would read the same list here and no test could tell: the order
     * is held on source written for the test instead.
     */
    static List<String> bundles(Sources.Body messages, Sources.Body assets) {
        int line = messages.find("private static String\\[\\] prop_files\\s*=");
        if (line < 0) {
            throw new IllegalStateException("Messages declares no prop_files; the bundles the game searches are no longer where the reader looks");
        }
        List<String> paths = new ArrayList<>();
        Matcher named = CONSTANT.matcher(Sources.text(messages.block(line)));
        while (named.find()) {
            // The constants live in a nested class, so the whole file is scanned: a body's own
            // lines stop at a nested type, and a bundle named inside one would read as missing.
            Pattern declared = Pattern.compile("String " + named.group(1) + "\\s*=\\s*\"(messages/[\\w/]+)\"");
            String path = null;
            List<String> lines = Sources.stripped(assets);
            for (int i = assets.from(); i < assets.to(); i++) {
                Matcher found = declared.matcher(lines.get(i - assets.from()));
                if (found.find()) {
                    if (path != null) {
                        throw new IllegalStateException("Assets names the bundle " + named.group(1) + " twice");
                    }
                    path = found.group(1) + ".properties";
                }
            }
            if (path == null) {
                throw new IllegalStateException("Assets names no messages bundle " + named.group(1));
            }
            paths.add(path);
        }
        if (paths.isEmpty()) {
            throw new IllegalStateException("Messages walks no bundle; the game's text is no longer where the reader looks");
        }
        return paths;
    }

    /**
     * Every class the game compiles that a file is named for, by the lower-cased name a key is
     * built from. The file system carries the spelling exactly, so nothing is inferred here.
     */
    static Map<String, String> outers(Path root) {
        Map<String, String> byKey = new TreeMap<>();
        for (String path : Sources.under(root, Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/")) {
            String className = path.substring(Sources.SOURCE_ROOT.length() + Sources.ROOT_PACKAGE_PREFIX.length(),
                    path.length() - ".java".length()).replace('/', '.');
            String key = Names.lower(className);
            String held = byKey.get(key);
            if (held != null && !held.equals(className)) {
                throw new IllegalStateException("two files share the key form " + key + ": " + held + " and " + className);
            }
            byKey.put(key, className);
        }
        if (byKey.size() < 500) {
            throw new IllegalStateException("the game compiles more classes than the reader found: " + byKey.size());
        }
        return byKey;
    }

    /** A key's class as the Codex names it, and the length of the key that named it. */
    record Resolved(String className, int prefix) {
    }

    /**
     * The class a key names, as the game's own rule builds it: the longest prefix that is a class
     * the game compiles. A nested class is written with a {@code $}, and its spelling is the
     * declaration in the outer class's own file whose name lower-cases to the key's segment; a
     * segment no declaration matches means the prefix is not a class, and a shorter one is tried.
     * A key that matches nothing names no class, which is true of plenty of the game's text.
     */
    static Resolved resolve(Path root, Map<String, String> outers, Map<String, List<String>> declared, String key) {
        for (int dot = key.lastIndexOf('.'); dot > 0; dot = key.lastIndexOf('.', dot - 1)) {
            String prefix = key.substring(0, dot);
            int dollar = prefix.indexOf('$');
            String head = dollar < 0 ? prefix : prefix.substring(0, dollar);
            String outer = outers.get(head);
            if (outer == null) {
                continue;
            }
            if (dollar < 0) {
                return new Resolved(outer, prefix.length());
            }
            List<String> names = declared.computeIfAbsent(outer, c -> declarations(root, c));
            StringBuilder className = new StringBuilder(outer);
            boolean whole = true;
            for (String segment : prefix.substring(dollar + 1).split("\\$")) {
                String found = null;
                for (String name : names) {
                    if (Names.lower(name).equals(segment)) {
                        found = name;
                        break;
                    }
                }
                if (found == null) {
                    whole = false;
                    break;
                }
                className.append('.').append(found);
            }
            if (whole) {
                return new Resolved(className.toString(), prefix.length());
            }
        }
        return new Resolved("", 0);
    }

    /** Every type declared in one class's file, in the order the file writes them. */
    private static List<String> declarations(Path root, String className) {
        Sources.Body file = Sources.file(root, Sources.SOURCE_ROOT + Sources.ROOT_PACKAGE_PREFIX.replace('.', '/')
                + className.replace('.', '/') + ".java");
        List<String> lines = Sources.stripped(file);
        List<String> names = new ArrayList<>();
        for (int i = file.from(); i < file.to(); i++) {
            Matcher declared = DECLARED.matcher(lines.get(i - file.from()));
            if (declared.find() && !names.contains(declared.group(1))) {
                names.add(declared.group(1));
            }
        }
        return names;
    }

    /** Every line of every English bundle, in the order the game searches the bundles and the order each is written. */
    static List<Codex.StringEntry> entries(Path root) {
        Map<String, String> outers = outers(root);
        Map<String, List<String>> declared = new TreeMap<>();
        List<Codex.StringEntry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (String bundle : bundles(root)) {
            String path = BUNDLES.substring(0, BUNDLES.length() - "messages/".length()) + bundle;
            List<String> lines = Names.lines(root, path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                Matcher entry = ENTRY.matcher(line);
                if (!entry.matches()) {
                    throw new IllegalStateException(path + ":" + (i + 1) + ": a bundle line the reader cannot read: " + line);
                }
                String key = entry.group(1);
                if (!seen.add(key)) {
                    throw new IllegalStateException(path + ":" + (i + 1) + ": " + key + " is given twice");
                }
                Resolved resolved = resolve(root, outers, declared, key);
                String className = resolved.className();
                String suffix = className.isEmpty() ? key : key.substring(resolved.prefix() + 1);
                entries.add(new Codex.StringEntry(key, entry.group(2).trim(), bundle, className, suffix,
                        className.isEmpty() ? NO_CLASS : "", new Codex.Citation(path, i + 1)));
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("the bundles carry no line; the game's text is no longer where the reader looks");
        }
        return entries;
    }
}
