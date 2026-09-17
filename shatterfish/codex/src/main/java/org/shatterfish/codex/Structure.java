package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shape of a Run (story 2.6): what the game builds at every depth of every branch, read from
 * its own {@code newLevel} switch, which story 2.4 already reads for the main branch and this
 * table reads whole; which of those floors holds a shop and which is a boss floor, from the two
 * rules the game states; which of them seals behind the hero, read from the level class's own
 * source, since an Observation exposes that a floor is sealed; and the level feelings with the
 * text of what each one changes when the level's creation rolls it.
 *
 * <p>The room pools a floor draws from are story 2.4's table and are not repeated here.
 */
final class Structure {

    private static final Pattern BRANCH = Pattern.compile("^\\s*(?:\\}\\s*else\\s*)?if \\(branch == (\\d+)\\)");
    private static final Pattern CASE = Pattern.compile("^\\s*case (\\d+)\\s*:");
    private static final Pattern BUILT = Pattern.compile("level = new (\\w+)\\s*\\(");
    private static final Pattern FEELING = Pattern.compile("^\\s*feeling = Feeling\\.(\\w+)\\s*;");

    private Structure() {
    }

    static Codex.Structure read(Path root) {
        Sources.Body dungeon = Sources.file(root, Guarantees.DUNGEON);
        Sources.Body level = Sources.file(root, Guarantees.LEVEL);
        int newLevel = dungeon.find("public static Level newLevel\\s*\\(\\s*\\)");
        if (newLevel < 0) {
            throw new IllegalStateException("Dungeon declares no newLevel()");
        }
        int shop = Guarantees.pinned(dungeon, "shopOnLevel", "public static boolean shopOnLevel\\s*\\(\\s*\\)", SHOP);
        List<Integer> shops = new ArrayList<>();
        Matcher depths = Pattern.compile("depth == (\\d+)").matcher(SHOP);
        while (depths.find()) {
            shops.add(Integer.parseInt(depths.group(1)));
        }
        List<Integer> bosses = new ArrayList<>();
        Matcher boss = Pattern.compile("depth == (\\d+)").matcher(Guarantees.BOSS);
        while (boss.find()) {
            bosses.add(Integer.parseInt(boss.group(1)));
        }
        Guarantees.pinned(dungeon, "bossLevel", "public static boolean bossLevel\\s*\\(\\s*int depth\\s*\\)", Guarantees.BOSS);
        Map<String, Class<?>> byName = new TreeMap<>();
        for (Class<?> type : Guarantees.LEVELS) {
            byName.put(type.getSimpleName(), type);
        }
        Sources.Body body = dungeon.block(newLevel);
        List<Codex.LevelEntry> levels = new ArrayList<>();
        List<Integer> pending = new ArrayList<>();
        int branch = -1;
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            Matcher which = BRANCH.matcher(text);
            if (which.find()) {
                branch = Integer.parseInt(which.group(1));
                pending.clear();
                continue;
            }
            if (branch < 0) {
                continue;
            }
            Matcher depth = CASE.matcher(text);
            if (depth.find()) {
                pending.add(Integer.parseInt(depth.group(1)));
                continue;
            }
            Matcher make = BUILT.matcher(text);
            if (make.find()) {
                Class<?> type = byName.get(make.group(1));
                if (type == null) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": branch " + branch + " builds a " + make.group(1)
                            + ", which the generator does not name; add it to Guarantees.LEVELS");
                }
                for (int at : pending) {
                    levels.add(new Codex.LevelEntry(at, branch, Sources.name(type), branch == 0 && shops.contains(at),
                            branch == 0 && bosses.contains(at), seals(root, type), dungeon.citation(i)));
                }
                pending.clear();
            }
        }
        if (levels.isEmpty()) {
            throw new IllegalStateException("newLevel named no floor; the switch no longer reads as the generator knows");
        }
        levels.sort(java.util.Comparator.comparingInt(Codex.LevelEntry::branch).thenComparingInt(Codex.LevelEntry::depth));
        int sealed = level.find("public boolean locked");
        if (sealed < 0) {
            throw new IllegalStateException("Level declares no locked flag; the sealing rule is no longer where the reader looks");
        }
        return new Codex.Structure(levels, feelings(level), Sources.text(dungeon.block(shop)), dungeon.citation(shop), level.citation(sealed));
    }

    static final String SHOP = "return depth == 6 || depth == 11 || depth == 16;";

    /**
     * Whether a level class seals behind the hero: it overrides the base's own sealing, which is
     * how a boss floor locks its doors while a fight is on. The base declares the sealing and
     * sets the flag there for anything that calls it, so the walk stops before the base: a class
     * that neither overrides nor calls it does not seal itself.
     */
    static boolean seals(Path root, Class<?> type) {
        for (Class<?> c = type; c != null && c != Level.class && Sources.name(c).startsWith("levels."); c = c.getSuperclass()) {
            Sources.Body body = Sources.body(root, c);
            for (int i = body.from(); i < body.to(); i++) {
                String text = Sources.stripComment(body.lines().get(i));
                if (text.matches(".*\\bpublic void seal\\s*\\(.*") || text.matches("\\s*seal\\(\\);\\s*")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The level feelings and what each changes: the arm of the level's own creation that sets it,
     * as cited text, so that a feeling which also adds an item or shortens the view says so.
     */
    static List<Codex.Rule> feelings(Sources.Body level) {
        int enumLine = level.declaration("Feeling");
        Sources.Body constants = level.block(enumLine);
        List<String> names = new ArrayList<>();
        Pattern constant = Pattern.compile("^([A-Z][A-Z0-9_]*)\\s*[,;]$");
        for (int i = constants.from() + 1; i < constants.to(); i++) {
            String text = Sources.stripComment(constants.lines().get(i)).trim();
            if (text.isEmpty()) {
                continue;
            }
            Matcher one = constant.matcher(text);
            if (!one.matches()) {
                throw new IllegalStateException(level.path() + ":" + (i + 1) + ": the feelings no longer read one plain name to a line: " + text);
            }
            names.add(one.group(1));
            if (text.endsWith(";")) {
                break;
            }
        }
        List<Codex.Rule> feelings = new ArrayList<>();
        for (String name : names) {
            if (name.equals("NONE")) {
                feelings.add(new Codex.Rule(name, "the floor has no feeling", level.citation(enumLine)));
                continue;
            }
            int line = -1;
            for (int i = level.from(); i < level.to(); i++) {
                Matcher set = FEELING.matcher(Sources.stripComment(level.lines().get(i)));
                if (set.find() && set.group(1).equals(name)) {
                    if (line >= 0) {
                        throw new IllegalStateException("the level sets the " + name + " feeling in two places; the reader cannot say which is the arm");
                    }
                    line = i;
                }
            }
            if (line < 0) {
                throw new IllegalStateException("nothing in the level's creation sets the " + name + " feeling");
            }
            feelings.add(new Codex.Rule(name, arm(level, line), level.citation(line)));
        }
        return feelings;
    }

    /** The statements of the arm that sets a feeling: from the line that sets it to the break that ends it. */
    private static String arm(Sources.Body level, int line) {
        List<String> parts = new ArrayList<>();
        for (int i = line; i < level.to(); i++) {
            String text = Sources.stripComment(level.lines().get(i)).trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
            if (text.startsWith("break;") || text.equals("}")) {
                break;
            }
        }
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }
}
