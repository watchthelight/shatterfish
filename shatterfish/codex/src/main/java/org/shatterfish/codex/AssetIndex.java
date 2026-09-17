package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every asset the game names (story 2.7): the constants of the class that holds them, and the
 * paths loaded by a literal string somewhere else.
 *
 * <p>The asset class is the game's index of its own files, but it is not the whole index: six
 * paths are written as literals at the place that loads them, which the codebase map recorded and
 * this table reads rather than repeating. Two of them are in the toolkit module and one in the
 * desktop launcher, so the reader looks outside the core the other tables read.
 *
 * <p>Each entry says whether a file is actually there and which of the two folders the game keeps
 * assets in holds it: the core's, or the desktop launcher's own, which holds the fonts and the
 * window icons it loads. A path the game names with nothing behind it is a dead name, carried as
 * absent and held by a named list with its reason, since a consumer that tried to load it would
 * fail. Nothing the class declares is dropped: a constant whose value is not a file of the game is
 * named with its reason too, so a table that says "every asset the game names" means it.
 */
final class AssetIndex {

    static final String ASSETS = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/Assets.java";
    /** The folders the game keeps its own assets in: the core's, and the desktop launcher's. */
    static final List<String> ASSET_ROOTS = Sources.ASSET_ROOTS;

    /** The files that load an asset by a literal string rather than by a constant of the asset class. */
    static final List<String> LITERAL_LOADERS = List.of(
            Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/effects/Fireball.java",
            "SPD-classes/src/main/java/com/watabou/noosa/ui/Cursor.java",
            "SPD-classes/src/main/java/com/watabou/noosa/TextInput.java",
            "desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopPlatformSupport.java",
            "desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopLauncher.java");

    // Every constant of the class, whatever its extension: an extension list would drop an asset
    // the game names in a kind the reader had not thought of, which is how eleven splash images
    // went missing from the first draft of this table.
    private static final Pattern CONSTANT = Pattern.compile("static final String (\\w+)\\s*=\\s*\"([\\w/. -]+)\"");
    private static final Pattern LITERAL = Pattern.compile("\"([\\w/. -]+\\.(?:png|jpg|ttf|ogg|mp3|json))\"");
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:public|private|protected|static|final|abstract)[\\w\\s]*\\bclass\\s+(\\w+)");

    /**
     * The constants of the asset class whose value is not a file of the game, each with the
     * reason. The bundles are the game's own text, read as a table of their own by story 2.7's
     * strings reader, and the class names their base path rather than a file.
     */
    static final List<java.util.Map.Entry<String, String>> NOT_A_FILE = List.of(
            java.util.Map.entry("Messages", "the bundle base paths, which name a resource bundle and not a file; strings.json carries their lines"));

    private AssetIndex() {
    }

    /** Every asset the game names, the constants first and then the literals, each cited. */
    static List<Codex.AssetEntry> entries(Path root) {
        List<Codex.AssetEntry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        Sources.Body assets = Sources.file(root, ASSETS);
        List<String> lines = Sources.stripped(assets);
        // The group is the whole nesting, and it is restored when a nested class closes: the game
        // nests a group inside a group, and a reader that never popped would hand the constants
        // after it the inner name.
        List<String> nesting = new java.util.ArrayList<>();
        List<Integer> closes = new java.util.ArrayList<>();
        java.util.Set<String> skipped = new java.util.TreeSet<>();
        for (java.util.Map.Entry<String, String> named : NOT_A_FILE) {
            skipped.add(named.getKey());
        }
        for (int i = assets.from(); i < assets.to(); i++) {
            while (!closes.isEmpty() && i >= closes.get(closes.size() - 1)) {
                closes.remove(closes.size() - 1);
                nesting.remove(nesting.size() - 1);
            }
            String text = lines.get(i - assets.from());
            Matcher type = TYPE.matcher(text);
            if (type.find()) {
                if (!type.group(1).equals("Assets")) {
                    nesting.add(type.group(1));
                    closes.add(assets.block(i).to());
                }
                continue;
            }
            Matcher constant = CONSTANT.matcher(text);
            if (!constant.find()) {
                continue;
            }
            String group = String.join(".", nesting);
            if (skipped.contains(group) || skipped.contains(nesting.isEmpty() ? "" : nesting.get(0))) {
                continue;
            }
            String path = constant.group(2);
            if (!seen.add(path)) {
                throw new IllegalStateException(assets.path() + ":" + (i + 1) + ": " + path + " is named twice");
            }
            Where where = where(root, path);
            entries.add(new Codex.AssetEntry(path, group, constant.group(1), where.root(), assets.citation(i)));
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("the asset class names no asset; the game's files are no longer where the reader looks");
        }
        for (String loader : LITERAL_LOADERS) {
            Sources.Body body = Sources.file(root, loader);
            List<String> text = Sources.stripped(body);
            for (int i = body.from(); i < body.to(); i++) {
                Matcher literal = LITERAL.matcher(text.get(i - body.from()));
                while (literal.find()) {
                    String path = literal.group(1);
                    if (!seen.add(path)) {
                        continue;
                    }
                    entries.add(new Codex.AssetEntry(path, "", "", where(root, path).root(), body.citation(i)));
                }
            }
        }
        return entries;
    }

    /**
     * The paths the game names with no file behind them, each with the reason, held by the
     * completeness test against what the reader finds. A dead name is worth publishing — a
     * consumer that tried to load it would fail — but an undeclared one means either the game
     * gained a broken name or the reader looked in the wrong place, and the test says which.
     */
    static final List<java.util.Map.Entry<String, String>> ABSENT = List.of(
            java.util.Map.entry("effects/fireball.png",
                    "the fireball constant is dead: the effect loads effects/fireball-tall.png and effects/fireball-short.png by literal instead"));

    /** The asset folder that holds the file a path names, or nothing when neither does. */
    private record Where(String root) {
    }

    /**
     * Which of the game's asset folders holds a path. A bare yes would say "this file exists"
     * where what was read is "one of two folders I looked in has it", and a consumer loading from
     * the other would fail.
     */
    private static Where where(Path root, String path) {
        for (String folder : ASSET_ROOTS) {
            if (Sources.exists(root, folder + path)) {
                return new Where(folder);
            }
        }
        return new Where("");
    }
}
