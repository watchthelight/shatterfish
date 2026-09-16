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
 * here as class literals and held against the game's own lists read from source (the
 * equipment and consumable specials, the crystal-key specials, the potion-spawn specials, the
 * laboratory placed by its own rule, the secrets); per room, the items its painting adds to the
 * floor's spawn list (a key, a solution potion), counted from the {@code addItemToSpawn} lines at
 * the painting method's own level and resolved through the file's imports, and every draw it
 * makes as cited text; the secrets per region base and the queue rule. A room the game places
 * outside the queues is excluded by name with its reason.
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

    private static final Pattern ADD = Pattern.compile("\\baddItemToSpawn\\s*\\(\\s*new\\s+(\\w+)\\s*\\(");
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
            lists.add(list(special, name, "\\b" + name + "\\s*=\\s*new ArrayList", bySimpleName));
        }
        int laboratory = special.find("floorSpecials\\.add\\(0, LaboratoryRoom\\.class\\)");
        if (laboratory < 0) {
            throw new IllegalStateException("SpecialRoom no longer places the laboratory by its own rule");
        }
        lists.add(new Codex.RoomList("LABORATORY", List.of(Sources.name(LaboratoryRoom.class)), special.citation(laboratory)));
        lists.add(list(secret, "ALL_SECRETS", "\\bALL_SECRETS\\s*=\\s*new ArrayList", bySimpleName));
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

    /** One of the game's room lists: the {@code X.class} members of the literal, in order, each a class the generator names. */
    private static Codex.RoomList list(Sources.Body body, String name, String anchor, Map<String, Class<?>> bySimpleName) {
        int line = -1;
        Pattern pattern = Pattern.compile(anchor);
        for (int i = body.from(); i < body.to(); i++) {
            if (pattern.matcher(body.lines().get(i)).find()) {
                if (line >= 0) {
                    throw new IllegalStateException(body.path() + " declares " + name + " twice");
                }
                line = i;
            }
        }
        if (line < 0) {
            throw new IllegalStateException(body.path() + " declares no " + name);
        }
        List<String> members = new ArrayList<>();
        for (int i = line; i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            Matcher member = MEMBER.matcher(text);
            while (member.find()) {
                Class<?> type = bySimpleName.get(member.group(1));
                if (type == null) {
                    throw new IllegalStateException(name + " lists " + member.group(1) + ", which the generator does not name; add it to Rooms");
                }
                members.add(Sources.name(type));
            }
            if (text.contains(";")) {
                break;
            }
        }
        return new Codex.RoomList(name, members, body.citation(line));
    }

    /** A room's entry: the spawns its painting adds at its own level, every draw, the class's declaration. */
    static Codex.RoomEntry entry(Path root, Class<?> type, boolean secret) {
        Sources.Body body = Sources.body(root, type);
        int declaration = body.declaration(type.getSimpleName());
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> firstLine = new TreeMap<>();
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            Matcher add = ADD.matcher(text);
            if (!add.find()) {
                continue;
            }
            String previous = "";
            for (int j = i - 1; j >= body.from(); j--) {
                String before = Sources.stripComment(body.lines().get(j)).trim();
                if (!before.isEmpty()) {
                    previous = before;
                    break;
                }
            }
            if (!text.startsWith("\t\t") || text.startsWith("\t\t\t") || CONTROL.matcher(text).find()
                    || CONTROL.matcher(previous).find() && !previous.endsWith("{") && !previous.endsWith(";") && !previous.endsWith("}")) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": an item added under a condition, which the reader cannot count: " + text.trim());
            }
            String className = resolve(body, add.group(1));
            counts.merge(className, 1, Integer::sum);
            firstLine.putIfAbsent(className, i);
        }
        List<Codex.Spawn> spawns = new ArrayList<>();
        for (Map.Entry<String, Integer> spawned : counts.entrySet()) {
            spawns.add(new Codex.Spawn(spawned.getKey(), spawned.getValue(), body.citation(firstLine.get(spawned.getKey()))));
        }
        List<Codex.Draw> draws = new ArrayList<>();
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            if (text.contains("Generator.random")) {
                draws.add(new Codex.Draw(text.trim(), body.citation(i)));
            }
        }
        return new Codex.RoomEntry(Sources.name(type), secret, spawns, draws, body.citation(declaration));
    }

    /** The table name of the class {@code simpleName} the file imports. */
    private static String resolve(Sources.Body body, String simpleName) {
        Pattern imported = Pattern.compile("^import\\s+" + Pattern.quote(Sources.ROOT_PACKAGE_PREFIX) + "([\\w.]+)\\." + Pattern.quote(simpleName) + "\\s*;");
        for (String line : body.lines()) {
            Matcher m = imported.matcher(line);
            if (m.find()) {
                return m.group(1) + "." + simpleName;
            }
        }
        throw new IllegalStateException(body.path() + " adds a " + simpleName + " it does not import from the game");
    }
}
