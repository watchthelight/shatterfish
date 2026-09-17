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
 * <p>Each entry says whether a file is actually there, looked for in both folders the game keeps
 * assets in: the core's, and the desktop launcher's own, which holds the fonts and the window
 * icons it loads. A path the game names with nothing behind it is a dead name, carried as absent
 * and held by a named list with its reason, since a consumer that tried to load it would fail.
 */
final class AssetIndex {

    static final String ASSETS = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/Assets.java";
    /** The folders the game keeps its own assets in: the core's, and the desktop launcher's. */
    static final List<String> ASSET_ROOTS = List.of("core/src/main/assets/", "desktop/src/main/assets/");

    /** The files that load an asset by a literal string rather than by a constant of the asset class. */
    static final List<String> LITERAL_LOADERS = List.of(
            Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/effects/Fireball.java",
            "SPD-classes/src/main/java/com/watabou/noosa/ui/Cursor.java",
            "SPD-classes/src/main/java/com/watabou/noosa/TextInput.java",
            "desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopPlatformSupport.java",
            "desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopLauncher.java");

    private static final Pattern CONSTANT = Pattern.compile(
            "static final String (\\w+)\\s*=\\s*\"([\\w/. -]+\\.(?:png|ttf|ogg|mp3|json|dat))\"");
    private static final Pattern LITERAL = Pattern.compile("\"([\\w/. -]+\\.(?:png|ttf|ogg|mp3|json|dat))\"");
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:public|private|protected|static|final|abstract)[\\w\\s]*\\bclass\\s+(\\w+)");

    private AssetIndex() {
    }

    /** Every asset the game names, the constants first and then the literals, each cited. */
    static List<Codex.AssetEntry> entries(Path root) {
        List<Codex.AssetEntry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        Sources.Body assets = Sources.file(root, ASSETS);
        List<String> lines = Sources.stripped(assets);
        String group = "";
        for (int i = assets.from(); i < assets.to(); i++) {
            String text = lines.get(i - assets.from());
            Matcher type = TYPE.matcher(text);
            if (type.find()) {
                group = type.group(1).equals("Assets") ? "" : type.group(1);
                continue;
            }
            Matcher constant = CONSTANT.matcher(text);
            if (!constant.find()) {
                continue;
            }
            String path = constant.group(2);
            if (!seen.add(path)) {
                throw new IllegalStateException(assets.path() + ":" + (i + 1) + ": " + path + " is named twice");
            }
            entries.add(new Codex.AssetEntry(path, group, constant.group(1), exists(root, path), assets.citation(i)));
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
                    entries.add(new Codex.AssetEntry(path, "", "", exists(root, path), body.citation(i)));
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

    /** Whether either of the game's own asset folders holds the file a path names. */
    private static boolean exists(Path root, String path) {
        return ASSET_ROOTS.stream().anyMatch(folder -> Sources.exists(root, folder + path));
    }
}
