package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
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
 * table reads whole, including the arms that build nothing but a dead end; whether each floor
 * holds a shop, read from the class that decides it rather than from the depth alone; which are
 * boss floors, from the game's own rule; which seal behind the hero, read from the level class's
 * own source, since an Observation exposes that a floor is sealed; and the level feelings with
 * the chance the game's roll gives each, the text of the arm that sets it, and every place the
 * game reads it afterwards.
 *
 * <p>The room pools a floor draws from are story 2.4's table; this one cites where the game draws
 * them rather than repeating them.
 */
final class Structure {

    private static final Pattern BRANCH = Pattern.compile("^\\s*(?:\\}\\s*)?(?:else\\s+)?if \\(branch == (\\d+)\\)");
    private static final Pattern OTHERWISE = Pattern.compile("^\\s*\\}\\s*else\\s*\\{\\s*$");
    private static final Pattern CASE = Pattern.compile("^\\s*case (\\d+)\\s*:");
    private static final Pattern DEFAULT = Pattern.compile("^\\s*default\\s*:");
    private static final Pattern BUILT = Pattern.compile("level = new (\\w+)\\s*\\(");
    private static final Pattern FEELING = Pattern.compile("^\\s*feeling = Feeling\\.(\\w+)\\s*;");
    private static final Pattern ASSIGNED = Pattern.compile("^\\s*feeling = (.+);\\s*$");
    private static final Pattern ROLL = Pattern.compile("switch\\s*\\(\\s*Random\\.Int\\(\\s*(\\d+)\\s*\\)\\s*\\)");

    static final String SHOP = "return depth == 6 || depth == 11 || depth == 16;";
    static final String LEVELS_FOLDER = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/";
    static final String REGULAR = LEVELS_FOLDER + "RegularLevel.java";

    private Structure() {
    }

    static Codex.Structure read(Path root) {
        Sources.Body dungeon = Sources.file(root, Guarantees.DUNGEON);
        Sources.Body level = Sources.file(root, Guarantees.LEVEL);
        int newLevel = dungeon.find("public static Level newLevel\\s*\\(\\s*\\)");
        if (newLevel < 0) {
            throw new IllegalStateException("Dungeon declares no newLevel()");
        }
        int shopLine = Guarantees.pinned(dungeon, "shopOnLevel", "public static boolean shopOnLevel\\s*\\(\\s*\\)", SHOP);
        int bossLine = Guarantees.pinned(dungeon, "bossLevel", "public static boolean bossLevel\\s*\\(\\s*int depth\\s*\\)", Guarantees.BOSS);
        List<Integer> shops = depths(SHOP);
        List<Integer> bosses = depths(Guarantees.BOSS);
        Map<String, Class<?>> byName = new TreeMap<>();
        for (Class<?> type : Guarantees.LEVELS) {
            byName.put(type.getSimpleName(), type);
        }
        Sources.Body body = dungeon.block(newLevel);
        List<String> lines = Sources.stripped(body);
        List<Codex.LevelEntry> levels = new ArrayList<>();
        List<Integer> pending = new ArrayList<>();
        String otherwiseClass = "";
        Codex.Citation otherwiseCitation = null;
        int branch = -1;
        boolean inDefault = false;
        for (int i = body.from(); i < body.to(); i++) {
            String text = lines.get(i - body.from());
            Matcher which = BRANCH.matcher(text);
            if (which.find()) {
                if (!pending.isEmpty()) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": branch " + branch
                            + " labels depths " + pending + " and builds nothing for them");
                }
                branch = Integer.parseInt(which.group(1));
                inDefault = false;
                continue;
            }
            if (OTHERWISE.matcher(text).find()) {
                if (!pending.isEmpty()) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": a branch labels depths " + pending
                            + " and builds nothing for them");
                }
                branch = -2;
                inDefault = true;
                continue;
            }
            if (branch == -1) {
                continue;
            }
            if (CASE.matcher(text).find()) {
                pending.add(Integer.parseInt(CASE.matcher(text).replaceAll("$1").trim()));
                inDefault = false;
                continue;
            }
            if (DEFAULT.matcher(text).find()) {
                if (!pending.isEmpty()) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": depths " + pending
                            + " fall through into the default arm; the reader does not know what they build");
                }
                inDefault = true;
                continue;
            }
            Matcher make = BUILT.matcher(text);
            if (!make.find()) {
                continue;
            }
            Class<?> type = byName.get(make.group(1));
            if (type == null) {
                throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": branch " + branch + " builds a " + make.group(1)
                        + ", which the generator does not name; add it to Guarantees.LEVELS");
            }
            if (inDefault) {
                // Every arm the switch does not label, and the branch beyond the ones it names,
                // build one and the same floor. The table says so once rather than pretending to
                // enumerate depths the game does not; a tag that builds something else here fails.
                if (!otherwiseClass.isEmpty() && !otherwiseClass.equals(Sources.name(type))) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": the unnamed depths build " + Sources.name(type)
                            + " here and " + otherwiseClass + " elsewhere; the reader cannot say what an unnamed depth builds");
                }
                otherwiseClass = Sources.name(type);
                otherwiseCitation = otherwiseCitation == null ? dungeon.citation(i) : otherwiseCitation;
                pending.clear();
                continue;
            }
            if (pending.isEmpty()) {
                throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": a floor is built for no labelled depth");
            }
            Shop shop = shop(root, type);
            Seal seal = seals(root, type);
            for (int at : pending) {
                levels.add(new Codex.LevelEntry(at, branch, Sources.name(type), shop.places() && shops.contains(at), shop.citation(),
                        bosses.contains(at), seal != null, seal == null ? "" : seal.how(), seal == null ? null : seal.citation(),
                        dungeon.citation(i)));
            }
            pending.clear();
        }
        if (levels.isEmpty()) {
            throw new IllegalStateException("newLevel named no floor; the switch no longer reads as the generator knows");
        }
        if (otherwiseClass.isEmpty()) {
            throw new IllegalStateException("newLevel names no floor for a depth it does not label; the switch no longer reads as the generator knows");
        }
        levels.sort(java.util.Comparator.comparingInt(Codex.LevelEntry::branch).thenComparingInt(Codex.LevelEntry::depth));
        int sealed = level.find("public boolean locked");
        if (sealed < 0) {
            throw new IllegalStateException("Level declares no locked flag; the sealing rule is no longer where the reader looks");
        }
        Sources.Body regular = Sources.file(root, REGULAR);
        int initRooms = regular.find("protected ArrayList<Room> initRooms\\(\\)");
        if (initRooms < 0) {
            throw new IllegalStateException("RegularLevel declares no initRooms(); the rooms a floor draws are no longer where the reader looks");
        }
        Feelings feelings = feelings(root, level);
        return new Codex.Structure(levels, feelings.entries(), Sources.text(dungeon.block(shopLine)), dungeon.citation(shopLine),
                Sources.text(dungeon.block(bossLine)), dungeon.citation(bossLine), feelings.gate(), feelings.citation(),
                feelings.others(), otherwiseClass, otherwiseCitation, level.citation(sealed), regular.citation(initRooms));
    }

    /** The depths a pinned rule of the form {@code depth == n || …} names. */
    private static List<Integer> depths(String rule) {
        List<Integer> depths = new ArrayList<>();
        Matcher at = Pattern.compile("depth == (\\d+)").matcher(rule);
        while (at.find()) {
            depths.add(Integer.parseInt(at.group(1)));
        }
        if (depths.isEmpty()) {
            throw new IllegalStateException("a pinned depth rule names no depth: " + rule);
        }
        return depths;
    }

    /** Whether a level's rooms reach the base's shop placement, and the line that decides it. */
    record Shop(boolean places, Codex.Citation citation) {
    }

    /**
     * Whether a level places a shop on a depth the shop rule names. The rule itself is about the
     * depth alone, but the placement is one line of the base's room list, and a level that builds
     * its own list decides for itself whether it ever reaches it. Most hand their list to the
     * base's and so do; the mining floors and the vault build their own and never call it, which
     * is the whole reason those depths hold no shop. Reading the depth alone would publish a shop
     * where the game places none, and reading nothing but the branch would publish a rule the
     * table's own citation does not contain.
     */
    static Shop shop(Path root, Class<?> type) {
        if (!RegularLevel.class.isAssignableFrom(type)) {
            // A floor that is not built from rooms at all (a boss arena, the last floor) never
            // reaches the placement, whatever its depth; the table cites the class that decides.
            Sources.Body body = Sources.body(root, type);
            return new Shop(false, body.citation(body.declaration(type.getSimpleName())));
        }
        for (Class<?> c = type; c != null && Sources.name(c).startsWith("levels."); ) {
            Sources.Declared rooms = Sources.declared(root, c, RegularLevel.class, "protected ArrayList<Room> initRooms\\(\\)");
            if (rooms == null) {
                throw new IllegalStateException(type.getName() + " reaches no initRooms(); the rooms a floor draws moved");
            }
            Sources.Body body = Sources.body(root, rooms.owner());
            if (rooms.owner() == RegularLevel.class) {
                return new Shop(true, body.citation(shopCall(root)));
            }
            String text = Sources.text(rooms.block());
            if (text.contains("new ShopRoom(")) {
                return new Shop(true, body.citation(rooms.line()));
            }
            if (!text.contains("super.initRooms()")) {
                return new Shop(false, body.citation(rooms.line()));
            }
            c = rooms.owner().getSuperclass();
        }
        throw new IllegalStateException(type.getName() + " builds rooms outside the levels of the game");
    }

    /** The line of the base's room list that places a shop, which every level that reaches it gets. */
    private static int shopCall(Path root) {
        Sources.Body regular = Sources.body(root, RegularLevel.class);
        int line = regular.find("if \\(Dungeon\\.shopOnLevel\\(\\)\\)");
        if (line < 0) {
            throw new IllegalStateException("RegularLevel no longer places the shop where the reader looks");
        }
        return line;
    }

    /** How a level class seals behind the hero, and the line that does it. */
    record Seal(String how, Codex.Citation citation) {
    }

    /**
     * Whether a level class seals behind the hero, and how: it either overrides the base's own
     * sealing or calls it. The base declares the sealing and sets the flag there for anything that
     * calls it, so the walk stops before the base: a class that neither overrides nor calls it does
     * not seal itself. Only the class's own lines are read, so a nested type's call is not the
     * level's.
     */
    static Seal seals(Path root, Class<?> type) {
        for (Class<?> c = type; c != null && c != Level.class && Sources.name(c).startsWith("levels."); c = c.getSuperclass()) {
            Sources.Body body = Sources.body(root, c);
            List<String> lines = Sources.stripped(body);
            for (int i : body.ownLines()) {
                String text = lines.get(i - body.from());
                if (text.matches(".*\\bpublic void seal\\s*\\(.*")) {
                    return new Seal("overrides the level's own sealing", body.citation(i));
                }
                if (text.matches(".*(?<![.\\w])seal\\s*\\(\\s*\\)\\s*;.*")) {
                    return new Seal("calls the level's own sealing", body.citation(i));
                }
            }
        }
        return null;
    }

    /** The feelings, the gate on the roll that picks one, and the assignments that are not a literal. */
    record Feelings(List<Codex.FeelingEntry> entries, String gate, Codex.Citation citation, List<Codex.Rule> others) {
    }

    /**
     * The level feelings: the game's own roll, its gate, the chance each arm carries, the text of
     * that arm, and every place the game reads the feeling afterwards, since a feeling whose arm
     * does nothing but name it still changes the floor somewhere else.
     */
    static Feelings feelings(Path root, Sources.Body level) {
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
        int create = level.find("public void create\\(\\)");
        if (create < 0) {
            throw new IllegalStateException("Level declares no create(); the feeling roll is no longer where the reader looks");
        }
        Sources.Body creation = level.block(create);
        List<String> lines = Sources.stripped(creation);
        int rollLine = -1;
        int bound = 0;
        for (int i = creation.from(); i < creation.to(); i++) {
            Matcher roll = ROLL.matcher(lines.get(i - creation.from()));
            if (roll.find()) {
                if (rollLine >= 0) {
                    throw new IllegalStateException(level.path() + ": the level's creation rolls twice; the reader cannot say which roll picks a feeling");
                }
                rollLine = i;
                bound = Integer.parseInt(roll.group(1));
            }
        }
        if (rollLine < 0 || bound <= 0) {
            throw new IllegalStateException("the level's creation no longer rolls a feeling where the reader looks");
        }
        Map<String, Integer> labels = new TreeMap<>();
        Map<String, Integer> where = new TreeMap<>();
        Sources.Body roll = level.block(rollLine);
        List<String> rollLines = Sources.stripped(roll);
        int counted = 0;
        int pending = 0;
        boolean inDefault = false;
        for (int i = roll.from(); i < roll.to(); i++) {
            String text = rollLines.get(i - roll.from());
            if (CASE.matcher(text).find()) {
                pending++;
                continue;
            }
            if (DEFAULT.matcher(text).find()) {
                inDefault = true;
                continue;
            }
            Matcher set = FEELING.matcher(text);
            if (!set.find()) {
                continue;
            }
            String name = set.group(1);
            int share = inDefault ? bound - counted - pending : pending;
            if (share <= 0 && !inDefault) {
                throw new IllegalStateException(level.path() + ":" + (i + 1) + ": the " + name + " feeling is set under no label");
            }
            if (labels.containsKey(name)) {
                throw new IllegalStateException(level.path() + ":" + (i + 1) + ": the " + name
                        + " feeling is set in two arms; the reader cannot say which is its chance");
            }
            labels.put(name, share);
            where.put(name, i);
            counted += pending;
            pending = 0;
        }
        for (String name : names) {
            if (!labels.containsKey(name)) {
                throw new IllegalStateException("no arm of the level's roll sets the " + name + " feeling");
            }
        }
        List<Codex.FeelingEntry> entries = new ArrayList<>();
        for (String name : names) {
            int line = where.get(name);
            entries.add(new Codex.FeelingEntry(name, share(labels.get(name), bound), arm(level, line), level.citation(line),
                    effects(root, name)));
        }
        List<Codex.Rule> others = new ArrayList<>();
        for (int i = creation.from(); i < creation.to(); i++) {
            String text = lines.get(i - creation.from());
            Matcher assigned = ASSIGNED.matcher(text);
            if (assigned.find() && !assigned.group(1).matches("Feeling\\.\\w+")) {
                // A feeling the game does not name as a literal: a trinket the hero carries picks
                // one. The table cannot say which without reading the trinket, so it carries the
                // assignment itself rather than leaving a reader to believe the arms are all.
                others.add(new Codex.Rule(simple(level.path()) + ":" + (i + 1), assigned.group(1).trim(), level.citation(i)));
            }
        }
        return new Feelings(entries, gate(level, creation, lines), level.citation(rollLine), others);
    }

    /** A share of the roll in thousandths, rounded half up, so no float reaches the table. */
    private static int share(int labels, int bound) {
        return (int) ((2000L * labels + bound) / (2L * bound));
    }

    /** The conditions the level's creation must pass before it rolls a feeling at all. */
    private static String gate(Sources.Body level, Sources.Body creation, List<String> lines) {
        List<String> conditions = new ArrayList<>();
        for (int i = creation.from(); i < creation.to(); i++) {
            String text = lines.get(i - creation.from()).trim();
            if (text.startsWith("if (!Dungeon.bossLevel()") || text.startsWith("if (Dungeon.depth >")) {
                conditions.add(text.endsWith("{") ? text.substring(0, text.length() - 1).trim() : text);
            }
        }
        if (conditions.size() != 2) {
            throw new IllegalStateException(level.path() + ": the level's creation no longer gates its feeling roll on a boss floor and a depth");
        }
        return String.join(" and ", conditions);
    }

    /** Every place under the levels of the game that reads one feeling, each as cited text. */
    private static List<Codex.Rule> effects(Path root, String name) {
        List<Codex.Rule> effects = new ArrayList<>();
        Pattern read = Pattern.compile("feeling\\s*[!=]=\\s*(?:Level\\.)?Feeling\\." + name + "\\b");
        for (String path : Sources.under(root, LEVELS_FOLDER)) {
            Sources.Body file = Sources.file(root, path);
            List<String> lines = Sources.stripped(file);
            for (int i = file.from(); i < file.to(); i++) {
                String text = lines.get(i - file.from()).trim();
                if (read.matcher(text).find()) {
                    effects.add(new Codex.Rule(simple(path) + ":" + (i + 1), text.replaceAll("\\s+", " "), file.citation(i)));
                }
            }
        }
        return effects;
    }

    /** The file name of a source path, for a row name a reader can place. */
    private static String simple(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * The statements of the arm that sets a feeling: from the line that sets it to the break that
     * ends it, counting braces so a braced statement inside the arm does not end it early.
     */
    private static String arm(Sources.Body level, int line) {
        List<String> parts = new ArrayList<>();
        List<String> lines = Sources.stripped(level);
        int depth = 0;
        for (int i = line; i < level.to(); i++) {
            String text = lines.get(i - level.from()).trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
            depth += Sources.braces(text);
            if (depth < 0) {
                break;
            }
            if (depth == 0 && text.startsWith("break;")) {
                break;
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException(level.path() + ":" + (line + 1) + ": a feeling's arm is empty");
        }
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }
}
