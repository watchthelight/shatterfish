package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.RatKingRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretArtilleryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretChestChasmRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretGardenRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretHoardRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretHoneypotRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretLaboratoryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretLarderRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretLibraryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretMazeRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRunestoneRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretSummoningRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretWellRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ArmoryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CryptRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalChoiceRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalPathRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalVaultRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.DemonSpawnerRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.GardenRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.LaboratoryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.LibraryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicWellRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.PitRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.PoolRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.RunestoneRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SacrificeRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ShopRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.StatueRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.StorageRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ToxicGasRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.TrapsRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.TreasuryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.WeakFloorRoom;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rooms (story 2.4): the special and secret rooms the game draws from its queues, named
 * here as class literals and held against the game's own lists read from source (the equipment
 * and consumable specials, the crystal-key specials, the potion-spawn specials, the laboratory
 * placed by its own rule, the secrets); per room, the items its painting puts on the floor,
 * either into the level's spawn list ({@code addItemToSpawn}) or onto its own cells
 * ({@code drop}, which the entry marks), counted from the statements at the painting method's
 * own brace level and resolved through the file's imports; and every draw the room's file makes
 * as cited text (the generator asked, a class picked by chances, an instance made by class),
 * since a prize's odds are a later table's. A statement the reader cannot read as one of those
 * shapes fails the generation naming the line, rather than being dropped. A room the game
 * places outside the queues is excluded by name with its reason.
 */
final class Rooms {

    static final String NOT_QUEUED = "placed by its level, not drawn from the special or secret queue";

    static final List<Class<? extends SpecialRoom>> SPECIALS = List.of(
            WeakFloorRoom.class, CryptRoom.class, PoolRoom.class, ArmoryRoom.class, SentryRoom.class, StatueRoom.class, CrystalVaultRoom.class,
            CrystalChoiceRoom.class, SacrificeRoom.class, RunestoneRoom.class, GardenRoom.class, LibraryRoom.class, StorageRoom.class, TreasuryRoom.class,
            MagicWellRoom.class, ToxicGasRoom.class, MagicalFireRoom.class, TrapsRoom.class, CrystalPathRoom.class, PitRoom.class, LaboratoryRoom.class);

    static final List<Class<? extends SecretRoom>> SECRETS = List.of(
            SecretGardenRoom.class, SecretLaboratoryRoom.class, SecretLibraryRoom.class, SecretLarderRoom.class, SecretWellRoom.class, SecretRunestoneRoom.class,
            SecretArtilleryRoom.class, SecretChestChasmRoom.class, SecretHoneypotRoom.class, SecretHoardRoom.class, SecretMazeRoom.class, SecretSummoningRoom.class);

    static final List<Map.Entry<Class<? extends Room>, String>> EXCLUDED = List.of(
            Map.entry(ShopRoom.class, NOT_QUEUED), Map.entry(DemonSpawnerRoom.class, NOT_QUEUED), Map.entry(RatKingRoom.class, NOT_QUEUED));

    /** What a room's painting is entered by; an item put on the floor anywhere else is not the painting's. */
    private static final Pattern PAINT = Pattern.compile("^\\s*public void paint\\s*\\(");
    private static final Pattern SPAWNS = Pattern.compile("\\baddItemToSpawn\\s*\\(");
    private static final Pattern DROPS = Pattern.compile("\\bdrop\\s*\\(\\s*new\\s+[\\w.]+\\s*\\(");
    private static final Pattern NEW_ITEM = Pattern.compile("^(?:level\\.)?(?:addItemToSpawn|drop)\\s*\\(\\s*new\\s+([\\w.]+)\\s*\\(");
    private static final Pattern DRAW = Pattern.compile("\\bGenerator\\.random\\w*\\s*\\(|\\bReflection\\.newInstance\\s*\\(|\\bRandom\\.chances\\s*\\(");
    private static final Pattern MEMBER = Pattern.compile("\\b(\\w+)\\.class\\b");
    private static final Pattern CONTROL = Pattern.compile("^\\s*(?:if|else|for|while|do|switch|case|default|try|catch|finally)\\b");
    private static final Pattern FLOAT = Pattern.compile("(\\d+(?:\\.\\d+)?)f?");

    private Rooms() {
    }

    static Codex.Rooms read(Path root) {
        Map<String, Class<?>> bySimpleName = new TreeMap<>();
        for (Class<?> type : SPECIALS) {
            bySimpleName.put(type.getSimpleName(), type);
        }
        for (Class<?> type : SECRETS) {
            bySimpleName.put(type.getSimpleName(), type);
        }
        Sources.Body special = Sources.body(root, SpecialRoom.class);
        Sources.Body secret = Sources.body(root, SecretRoom.class);
        List<Codex.RoomList> lists = new ArrayList<>();
        for (String name : List.of("EQUIP_SPECIALS", "CONSUMABLE_SPECIALS", "CRYSTAL_KEY_SPECIALS", "POTION_SPAWN_ROOMS")) {
            lists.add(list(special, name, bySimpleName));
        }
        int laboratory = special.find("floorSpecials\\.add\\(0, LaboratoryRoom\\.class\\)");
        if (laboratory < 0) {
            throw new IllegalStateException("SpecialRoom no longer places the laboratory by its own rule");
        }
        lists.add(new Codex.RoomList("LABORATORY", List.of(Sources.name(LaboratoryRoom.class)), special.citation(laboratory)));
        lists.add(list(secret, "ALL_SECRETS", bySimpleName));
        java.util.TreeSet<String> listed = new java.util.TreeSet<>();
        for (Codex.RoomList list : lists) {
            listed.addAll(list.members());
        }
        java.util.TreeSet<String> named = new java.util.TreeSet<>();
        SPECIALS.forEach(t -> named.add(Sources.name(t)));
        SECRETS.forEach(t -> named.add(Sources.name(t)));
        if (!listed.equals(named)) {
            throw new IllegalStateException("the game's room lists and the generator's literals differ: listed " + listed + " against named " + named);
        }
        List<Codex.RoomEntry> specials = new ArrayList<>();
        for (Class<?> type : SPECIALS) {
            specials.add(entry(root, type, false));
        }
        List<Codex.RoomEntry> secrets = new ArrayList<>();
        for (Class<?> type : SECRETS) {
            secrets.add(entry(root, type, true));
        }
        int base = secret.find("float\\[\\] baseRegionSecrets\\s*=");
        if (base < 0) {
            throw new IllegalStateException("SecretRoom declares no baseRegionSecrets");
        }
        String literal = Sources.stripComment(secret.lines().get(base));
        if (!literal.contains("{") || !literal.contains("}") || !literal.trim().endsWith(";")) {
            throw new IllegalStateException(secret.path() + ":" + (base + 1) + ": baseRegionSecrets is no longer one literal on its line: " + literal.trim());
        }
        literal = literal.substring(literal.indexOf('{') + 1, literal.indexOf('}'));
        List<Integer> perRegion = new ArrayList<>();
        Matcher value = FLOAT.matcher(literal);
        while (value.find()) {
            perRegion.add(Sources.thousandths(value.group(1)));
        }
        int queue = special.find("int index = Random\\.chances\\(new float\\[\\]");
        int secretQueue = secret.find("int index = Random\\.chances\\(new float\\[\\]");
        if (queue < 0 || secretQueue < 0) {
            throw new IllegalStateException("the room queues are no longer drawn by chances over the front");
        }
        String queueText = Sources.stripComment(special.lines().get(queue)).trim();
        if (!queueText.equals(Sources.stripComment(secret.lines().get(secretQueue)).trim())) {
            throw new IllegalStateException("the special and secret queues draw differently: " + queueText + " against "
                    + Sources.stripComment(secret.lines().get(secretQueue)).trim());
        }
        return new Codex.Rooms(specials, secrets, lists, perRegion, secret.citation(base), new Codex.Rule("queue", queueText, special.citation(queue)));
    }

    /** One of the game's room lists: the {@code X.class} members between the literal's own parentheses, in order, each a class the generator names. */
    static Codex.RoomList list(Sources.Body body, String name, Map<String, Class<?>> bySimpleName) {
        Pattern anchor = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*new ArrayList");
        int line = -1;
        for (int i = body.from(); i < body.to(); i++) {
            if (anchor.matcher(body.lines().get(i)).find()) {
                if (line >= 0) {
                    throw new IllegalStateException(body.path() + " declares " + name + " twice");
                }
                line = i;
            }
        }
        if (line < 0) {
            throw new IllegalStateException(body.path() + " declares no " + name);
        }
        StringBuilder statement = new StringBuilder();
        for (int i = line; i < body.to(); i++) {
            statement.append(' ').append(Sources.stripComment(body.lines().get(i)));
            if (Sources.stripComment(body.lines().get(i)).contains(";")) {
                break;
            }
        }
        String text = statement.toString();
        int open = text.indexOf("asList(");
        if (open < 0) {
            throw new IllegalStateException(body.path() + ":" + (line + 1) + ": " + name + " is no longer an Arrays.asList literal");
        }
        String members = inside(text, open + "asList".length(), body.path(), line);
        List<String> names = new ArrayList<>();
        Matcher member = MEMBER.matcher(members);
        while (member.find()) {
            Class<?> type = bySimpleName.get(member.group(1));
            if (type == null) {
                throw new IllegalStateException(name + " lists " + member.group(1) + ", which the generator does not name; add it to Rooms");
            }
            names.add(Sources.name(type));
        }
        return new Codex.RoomList(name, names, body.citation(line));
    }

    /** The text inside the parentheses that open at {@code from}; an unclosed one fails naming the line. */
    private static String inside(String text, int from, String path, int line) {
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return text.substring(from + 1, i);
                }
            }
        }
        throw new IllegalStateException(path + ":" + (line + 1) + ": the literal's parentheses do not close on its own statement");
    }

    /** A room's entry, read from its source. */
    static Codex.RoomEntry entry(Path root, Class<?> type, boolean secret) {
        Sources.Body body = Sources.body(root, type);
        return entry(body, Sources.name(type), type.getSimpleName(), secret);
    }

    /**
     * A room's entry: the items its painting puts on the floor, every draw its file makes, and
     * the class's declaration. The painting is {@code paint}; an item added to the level's spawn
     * list outside its own level, and any item put on the floor by a shape the reader cannot name,
     * fails naming the line, since a table that quietly drops one is worse than none. An item the
     * room drops on its own cells under a condition is carried and marked conditional.
     */
    static Codex.RoomEntry entry(Sources.Body body, String className, String simpleName, boolean secret) {
        int declaration = body.declaration(simpleName);
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> firstLine = new TreeMap<>();
        java.util.Set<String> dropped = new java.util.TreeSet<>();
        java.util.Set<String> conditional = new java.util.TreeSet<>();
        int paint = -1;
        for (int i = body.from(); i < body.to(); i++) {
            if (PAINT.matcher(body.lines().get(i)).find()) {
                if (paint >= 0) {
                    throw new IllegalStateException(body.path() + " declares paint twice");
                }
                paint = i;
            }
        }
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            boolean puts = SPAWNS.matcher(text).find() || DROPS.matcher(text).find();
            if (!puts) {
                continue;
            }
            if (paint < 0) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": an item is put on the floor by a room that declares no paint");
            }
            Sources.Body block = body.block(paint);
            boolean own = i >= block.from() && i < block.to() && depthIn(block, i) == 1 && !CONTROL.matcher(text).find() && !governed(body, i);
            String statement = statement(body, i);
            if (!own && SPAWNS.matcher(statement).find()) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": an item added to the floor's spawn list outside paint's own level, "
                        + "which the reader cannot count: " + statement);
            }
            Matcher item = NEW_ITEM.matcher(statement);
            if (!item.find()) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": an item put on the floor by a shape the reader does not know: " + statement);
            }
            if (statement.indexOf("addItemToSpawn", item.end()) >= 0 || statement.indexOf("drop(new", item.end()) >= 0) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": two items put on the floor in one statement: " + statement);
            }
            String name = resolve(body, item.group(1));
            boolean floorDrop = !SPAWNS.matcher(statement).find();
            String key = name + (floorDrop ? " dropped" : " spawned");
            counts.merge(key, 1, Integer::sum);
            firstLine.putIfAbsent(key, i);
            if (floorDrop) {
                dropped.add(key);
                if (!own) {
                    conditional.add(key);
                }
            }
        }
        List<Codex.Spawn> spawns = new ArrayList<>();
        for (Map.Entry<String, Integer> put : counts.entrySet()) {
            String key = put.getKey();
            spawns.add(new Codex.Spawn(key.substring(0, key.lastIndexOf(' ')), put.getValue(), dropped.contains(key), conditional.contains(key),
                    body.citation(firstLine.get(key))));
        }
        List<Codex.Draw> draws = new ArrayList<>();
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            if (DRAW.matcher(text).find()) {
                draws.add(new Codex.Draw(statement(body, i), body.citation(i)));
            }
        }
        return new Codex.RoomEntry(className, secret, spawns, draws, body.citation(declaration));
    }

    /**
     * Whether the statement at {@code line} is governed by a control line above it that opened no
     * brace (a braceless {@code if} over two lines), so that the statement runs under a condition
     * the reader does not evaluate.
     */
    private static boolean governed(Sources.Body body, int line) {
        for (int i = line - 1; i >= body.from(); i--) {
            String previous = Sources.stripComment(body.lines().get(i)).trim();
            if (previous.isEmpty()) {
                continue;
            }
            return CONTROL.matcher(previous).find() && !previous.endsWith(";") && !previous.endsWith("{") && !previous.endsWith("}");
        }
        return false;
    }

    /** The brace depth of {@code line} inside {@code block}, counting from the block's opening brace. */
    private static int depthIn(Sources.Body block, int line) {
        int depth = 0;
        for (int i = block.from(); i < line; i++) {
            depth += Sources.braces(Sources.stripComment(block.lines().get(i)));
        }
        return depth;
    }

    /** The whole statement starting at {@code line}: its lines joined to the one ending in a semicolon, comments stripped, whitespace collapsed. */
    static String statement(Sources.Body body, int line) {
        StringBuilder text = new StringBuilder();
        for (int i = line; i < body.to(); i++) {
            String part = Sources.stripComment(body.lines().get(i)).trim();
            text.append(text.length() == 0 ? "" : " ").append(part);
            if (part.endsWith(";")) {
                return text.toString().replaceAll("\\s+", " ").trim();
            }
        }
        throw new IllegalStateException(body.path() + ":" + (line + 1) + ": a statement does not end inside the class");
    }

    /**
     * The table name of the class {@code name} the file imports; a nested class is named by its
     * outer class's import and the rest of the name ({@code new Bomb.DoubleBomb()}).
     */
    private static String resolve(Sources.Body body, String name) {
        String outer = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String nested = name.substring(outer.length());
        Pattern imported = Pattern.compile("^import\\s+" + Pattern.quote(Sources.ROOT_PACKAGE_PREFIX) + "([\\w.]+)\\." + Pattern.quote(outer) + "\\s*;");
        for (String line : body.lines()) {
            Matcher m = imported.matcher(line);
            if (m.find()) {
                return m.group(1) + "." + outer + nested;
            }
        }
        throw new IllegalStateException(body.path() + " puts a " + name + " on the floor and does not import it from the game");
    }
}
