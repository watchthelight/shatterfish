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
    /**
     * The key prefixes that name no class the game compiles, each with the reason, held by the
     * completeness test against what the reader finds. The game keeps the text of classes it no
     * longer has, and keys some text to nothing at all; a count would let a live class quietly
     * stop resolving while a dead one disappeared, so the prefixes are named.
     */
    static final List<java.util.Map.Entry<String, String>> NO_CLASS_PREFIXES = List.of(
            java.util.Map.entry("actors.buffs.earthimbue", "no EarthImbue is declared in any module; the text outlived the buff"),
            java.util.Map.entry("actors.buffs.revealedchar", "no RevealedChar is declared in any module"),
            java.util.Map.entry("actors.mobs.tengu$bombability$bombblob",
                    "Tengu.BombAbility is a live class, but it declares no BombBlob; only the last segment names nothing"),
            java.util.Map.entry("items.merchantsbeacon", "no MerchantsBeacon is declared in any module"),
            java.util.Map.entry("items.quest.corpsedust&dustwraith",
                    "a key naming two things at once, which the game's own rule never builds from a class"),
            java.util.Map.entry("items.spells.magicalporter", "no MagicalPorter is declared in any module"),
            java.util.Map.entry("items.stones.stoneofdisarming", "no StoneOfDisarming is declared in any module"),
            java.util.Map.entry("items.weapon.missiles.boomerang", "no Boomerang is declared in any module"),
            java.util.Map.entry("ui.updatenotification", "no UpdateNotification is declared in any module"),
            java.util.Map.entry("ui.updatenotification$wndupdate", "nor the window it would have held"),
            java.util.Map.entry("windows.wndclass", "no WndClass is declared in any module"));

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
        // Every module that compiles a class under the game's root package, not the core alone: a
        // key naming a class of another module would otherwise be published as naming no class at
        // all, which is a wrong statement rather than a cautious one.
        for (String source : Sources.SOURCE_ROOTS) {
            String folder = source + Sources.GAME.replace('.', '/') + "/";
            if (!Sources.has(root, folder)) {
                continue;
            }
            for (String path : Sources.under(root, folder)) {
                String className = path.substring(source.length() + Sources.ROOT_PACKAGE_PREFIX.length(),
                        path.length() - ".java".length()).replace('/', '.');
                String key = Names.lower(className);
                String held = byKey.get(key);
                if (held != null && !held.equals(className)) {
                    throw new IllegalStateException("two files share the key form " + key + ": " + held + " and " + className);
                }
                byKey.put(key, className);
            }
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
    static Resolved resolve(Path root, Map<String, String> outers, String key) {
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
            // Each segment is looked for inside the one before it, not anywhere in the file: a
            // type nested under a sibling would otherwise answer for a nesting that does not exist.
            Sources.Body enclosing = Sources.file(root, sourceOf(root, outer));
            enclosing = enclosing.block(enclosing.declaration(outer.substring(outer.lastIndexOf('.') + 1)));
            StringBuilder className = new StringBuilder(outer);
            boolean whole = true;
            for (String segment : prefix.substring(dollar + 1).split("\\$")) {
                String found = nested(enclosing, segment);
                if (found == null) {
                    whole = false;
                    break;
                }
                className.append('.').append(found);
                enclosing = enclosing.block(enclosing.declaration(found));
            }
            if (whole) {
                return new Resolved(className.toString(), prefix.length());
            }
        }
        return new Resolved("", 0);
    }

    /**
     * The type declared directly inside {@code enclosing} whose name lower-cases to {@code segment},
     * or null when it declares none. Two at the same level whose names lower-case alike would make
     * a key ambiguous and fail, as two files whose names do fail in {@link #outers}.
     */
    private static String nested(Sources.Body enclosing, String segment) {
        List<String> lines = Sources.stripped(enclosing);
        String found = null;
        // The body's own lines stop at a nested type's block, declaration line and all, so the
        // walk is the same one with the declaration kept: a type directly inside this one, then
        // past its whole block, never into it.
        int i = enclosing.from() + 1;
        while (i < enclosing.to()) {
            Matcher declared = DECLARED.matcher(lines.get(i - enclosing.from()));
            if (!declared.find()) {
                i++;
                continue;
            }
            if (Names.lower(declared.group(1)).equals(segment)) {
                if (found != null && !found.equals(declared.group(1))) {
                    throw new IllegalStateException(enclosing.path() + ":" + (i + 1) + ": two types nested here lower-case to "
                            + segment + ": " + found + " and " + declared.group(1));
                }
                found = declared.group(1);
            }
            i = Math.max(i + 1, enclosing.block(i).to());
        }
        return found;
    }

    /**
     * A bundle value as the game shows it. The bundles are a properties file and the game loads
     * them through a reader that decodes the escapes, so a table that carried the raw text would
     * publish a backslash and an n where the game prints a new line — and story 2.7's changelog
     * table, which decodes, would then disagree with this one about what the game says. An escape
     * the reader does not know fails rather than being passed through.
     */
    static String decoded(String path, int line, String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'f' -> out.append('\f');
                case '\\' -> out.append('\\');
                case '=' -> out.append('=');
                case ':' -> out.append(':');
                case ' ' -> out.append(' ');
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        throw new IllegalStateException(path + ":" + (line + 1) + ": a unicode escape that does not finish");
                    }
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalStateException(path + ":" + (line + 1)
                        + ": an escape the reader does not know: \\" + next);
            }
        }
        return out.toString();
    }

    /** The file a class of the game is declared in, in whichever module compiles it. */
    static String sourceOf(Path root, String className) {
        for (String source : Sources.SOURCE_ROOTS) {
            String path = source + Sources.ROOT_PACKAGE_PREFIX.replace('.', '/') + className.replace('.', '/') + ".java";
            if (Sources.exists(root, path)) {
                return path;
            }
        }
        throw new IllegalStateException("no module of the pinned game declares " + className);
    }

    /** Every line of every English bundle, in the order the game searches the bundles and the order each is written. */
    static List<Codex.StringEntry> entries(Path root) {
        Map<String, String> outers = outers(root);
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
                Resolved resolved = resolve(root, outers, key);
                String className = resolved.className();
                String suffix = className.isEmpty() ? key : key.substring(resolved.prefix() + 1);
                entries.add(new Codex.StringEntry(key, decoded(path, i, entry.group(2).trim()), bundle, className, suffix,
                        className.isEmpty() ? NO_CLASS : "", new Codex.Citation(path, i + 1)));
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("the bundles carry no line; the game's text is no longer where the reader looks");
        }
        return entries;
    }
}
