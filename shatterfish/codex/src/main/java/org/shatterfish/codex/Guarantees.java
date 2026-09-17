package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Stylus;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.levels.CavesBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.CavesLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.CityBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.CityLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.DeadEndLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.LastLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.LastShopLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.LaboratoryRoom;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guarantees (story 2.4): every limited drop the level's creation decides, as the game's
 * method text and as a schedule, the exact chance in thousandths for every depth and every
 * counter state. The game decides each with one uniform draw ({@code Random.Int(n)}, which is
 * zero for {@code n <= 0}), so the chance is the count of outcomes that say yes over {@code n};
 * the arithmetic is mirrored here and every mirror is pinned to the exact text of the method it
 * mirrors, comments stripped and whitespace collapsed, so that a generation fails naming the
 * method when the pinned source no longer reads so, and {@code GuaranteeArithmeticTest} holds
 * each mirror to the game's own method sampled under a seeded generator.
 *
 * <p>Deciding is not placing. The six items enter the floor's spawn list inside one gate of
 * {@code Level.create} (the main branch, never a boss floor), and the laboratory enters the
 * floor's special queue in {@code SpecialRoom.initForFloor}; each of those blocks is pinned
 * here too, so the Forbidden Runes rule (every second upgrade scroll withheld, the counter
 * already moved) is the source's and not a rule repeated in two places. What finally puts a
 * spawn-list item on a floor is {@code RegularLevel.createItems}, so a depth whose level class
 * is not a regular level places nothing however the counter stands: the boss floors, and the
 * amulet floor at the bottom, which runs the gate, moves the counters and drops none of it.
 * The level class of every depth is read from the game's own {@code newLevel} switch and
 * carried, so the placed column is derivable rather than asserted. {@code Dungeon} is read as
 * source only, never as a class.
 */
final class Guarantees {

    static final String DUNGEON = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/Dungeon.java";
    static final String LEVEL = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/Level.java";
    static final String SPECIAL_ROOM = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/rooms/special/SpecialRoom.java";

    /** The deepest floor the main branch builds, which the spawn rotation counts to as well. */
    static final int MAX_DEPTH = Mobs.MAX_DEPTH;

    /** The level classes the main branch's switch may name, by simple name; a class it names that is not here fails the generation. */
    static final List<Class<? extends Level>> LEVELS = List.of(
            SewerLevel.class, SewerBossLevel.class, PrisonLevel.class, PrisonBossLevel.class, CavesLevel.class, CavesBossLevel.class,
            CityLevel.class, CityBossLevel.class, HallsLevel.class, HallsBossLevel.class, LastLevel.class, LastShopLevel.class,
            DeadEndLevel.class, MiningLevel.class, VaultLevel.class);

    /** One draw's outcome as a chance in thousandths: {@code hits} of {@code n} outcomes say yes; {@code n <= 0} draws zero. */
    private static int chance(int hits, int n) {
        if (n <= 0) {
            return hits > 0 ? 1000 : 0;
        }
        if (hits >= n) {
            return 1000;
        }
        return Sources.thousandths(hits + "/" + n);
    }

    /** The chance in thousandths a drop is needed at {@code depth} with its counter at {@code count}. */
    interface Schedule {
        int neededPerMille(int depth, int count);
    }

    /**
     * A mirror of one of the game's methods: the counter, the item, the method deciding, its
     * shape, the pinned text of that method, the file and the pinned text of the block that
     * places what it decides, and the arithmetic.
     */
    record Mirror(String name, String item, String method, boolean once, int perSet, int maxCount, String pinned, String placer, String placedPinned,
                  Schedule schedule) {
    }

    static final String POS = "int posLeftThisSet = 2 - (LimitedDrops.STRENGTH_POTIONS.count - (depth / 5) * 2); if (posLeftThisSet <= 0) return false; "
            + "int floorThisSet = (depth % 5); int targetPOSLeft = 2 - floorThisSet/2; if (floorThisSet % 2 == 1 && Random.Int(2) == 0) targetPOSLeft --; "
            + "if (targetPOSLeft < posLeftThisSet) return true; else return false;";
    static final String SOU = "int souLeftThisSet; souLeftThisSet = 3 - (LimitedDrops.UPGRADE_SCROLLS.count - (depth / 5) * 3); if (souLeftThisSet <= 0) return false; "
            + "int floorThisSet = (depth % 5); return Random.Int(5 - floorThisSet) < souLeftThisSet;";
    static final String AS = "int asLeftThisSet = 1 - (LimitedDrops.ARCANE_STYLI.count - (depth / 5)); if (asLeftThisSet <= 0) return false; "
            + "int floorThisSet = (depth % 5); return Random.Int(5 - floorThisSet) < asLeftThisSet;";
    static final String ENCH = "if (!LimitedDrops.ENCH_STONE.dropped()){ int region = 1+depth/5; if (region > 1){ int floorsVisited = depth - 5; "
            + "if (floorsVisited > 4) floorsVisited--; return Random.Int(9-floorsVisited) == 0; } } return false;";
    static final String INT = "return depth < 5 && !LimitedDrops.INT_STONE.dropped() && Random.Int(4-depth) == 0;";
    static final String CATA = "return depth < 5 && !LimitedDrops.TRINKET_CATA.dropped() && Random.Int(4-depth) == 0;";
    static final String LAB = "int region = 1+depth/5; if (region > LimitedDrops.LAB_ROOM.count){ int floorThisRegion = depth%5; "
            + "if (floorThisRegion >= 4 || (floorThisRegion == 3 && Random.Int(2) == 0)){ return true; } } return false;";
    static final String BOSS = "return depth == 5 || depth == 10 || depth == 15 || depth == 20 || depth == 25;";

    static final String PLACE_POS = "Dungeon.LimitedDrops.STRENGTH_POTIONS.count++; addItemToSpawn( new PotionOfStrength() );";
    static final String PLACE_SOU = "Dungeon.LimitedDrops.UPGRADE_SCROLLS.count++; if (!Dungeon.isChallenged(Challenges.NO_SCROLLS) "
            + "|| Dungeon.LimitedDrops.UPGRADE_SCROLLS.count%2 != 0){ addItemToSpawn(new ScrollOfUpgrade()); }";
    static final String PLACE_AS = "Dungeon.LimitedDrops.ARCANE_STYLI.count++; addItemToSpawn( new Stylus() );";
    static final String PLACE_ENCH = "Dungeon.LimitedDrops.ENCH_STONE.drop(); addItemToSpawn( new StoneOfEnchantment() );";
    static final String PLACE_INT = "Dungeon.LimitedDrops.INT_STONE.drop(); addItemToSpawn( new StoneOfIntuition() );";
    static final String PLACE_CATA = "Dungeon.LimitedDrops.TRINKET_CATA.drop(); addItemToSpawn( new TrinketCatalyst());";
    static final String PLACE_LAB = "Dungeon.LimitedDrops.LAB_ROOM.count++; floorSpecials.add(0, LaboratoryRoom.class);";

    static final List<Mirror> MIRRORS = List.of(
            new Mirror("STRENGTH_POTIONS", Sources.name(PotionOfStrength.class), "posNeeded", false, 2, 12, POS, LEVEL, PLACE_POS, Guarantees::pos),
            new Mirror("UPGRADE_SCROLLS", Sources.name(ScrollOfUpgrade.class), "souNeeded", false, 3, 18, SOU, LEVEL, PLACE_SOU, Guarantees::sou),
            new Mirror("ARCANE_STYLI", Sources.name(Stylus.class), "asNeeded", false, 1, 6, AS, LEVEL, PLACE_AS, Guarantees::stylus),
            new Mirror("ENCH_STONE", Sources.name(StoneOfEnchantment.class), "enchStoneNeeded", true, 1, 1, ENCH, LEVEL, PLACE_ENCH, Guarantees::enchantment),
            new Mirror("INT_STONE", Sources.name(StoneOfIntuition.class), "intStoneNeeded", true, 1, 1, INT, LEVEL, PLACE_INT, Guarantees::intuition),
            new Mirror("TRINKET_CATA", Sources.name(TrinketCatalyst.class), "trinketCataNeeded", true, 1, 1, CATA, LEVEL, PLACE_CATA, Guarantees::catalyst),
            new Mirror("LAB_ROOM", Sources.name(LaboratoryRoom.class), "labRoomNeeded", false, 1, 6, LAB, SPECIAL_ROOM, PLACE_LAB, Guarantees::laboratory));

    private Guarantees() {
    }

    /** {@code posNeeded}: two per set; on an odd floor of the set a coin halves the target. */
    static int pos(int depth, int count) {
        int left = 2 - (count - (depth / 5) * 2);
        if (left <= 0) {
            return 0;
        }
        int floor = depth % 5;
        int target = 2 - floor / 2;
        if (floor % 2 == 1) {
            int hits = (target - 1 < left ? 1 : 0) + (target < left ? 1 : 0);
            return chance(hits, 2);
        }
        return target < left ? 1000 : 0;
    }

    /** {@code souNeeded}: three per set; the chance is scrolls left over floors left. */
    static int sou(int depth, int count) {
        int left = 3 - (count - (depth / 5) * 3);
        if (left <= 0) {
            return 0;
        }
        return chance(left, 5 - depth % 5);
    }

    /** {@code asNeeded}: one per set; the chance is styli left over floors left. */
    static int stylus(int depth, int count) {
        int left = 1 - (count - depth / 5);
        if (left <= 0) {
            return 0;
        }
        return chance(left, 5 - depth % 5);
    }

    /** {@code enchStoneNeeded}: once, from region 2, one in the floors left of two regions with floor 10 skipped. */
    static int enchantment(int depth, int dropped) {
        if (dropped != 0) {
            return 0;
        }
        int region = 1 + depth / 5;
        if (region > 1) {
            int floorsVisited = depth - 5;
            if (floorsVisited > 4) {
                floorsVisited--;
            }
            return chance(1, 9 - floorsVisited);
        }
        return 0;
    }

    /** {@code intStoneNeeded}: once on floors 1-4, one in the floors left before floor 5. */
    static int intuition(int depth, int dropped) {
        return depth < 5 && dropped == 0 ? chance(1, 4 - depth) : 0;
    }

    /** {@code trinketCataNeeded}: the same shape as the intuition stone's. */
    static int catalyst(int depth, int dropped) {
        return depth < 5 && dropped == 0 ? chance(1, 4 - depth) : 0;
    }

    /** {@code labRoomNeeded}: one per region, on its fourth floor at a coin or its fifth for certain, while the count is below the region. */
    static int laboratory(int depth, int count) {
        int region = 1 + depth / 5;
        if (region > count) {
            int floor = depth % 5;
            if (floor >= 4) {
                return 1000;
            }
            if (floor == 3) {
                return 500;
            }
        }
        return 0;
    }

    /**
     * What the main branch builds at every depth, read from the game's own {@code newLevel}
     * switch: the level class of each case, and whether that class empties the floor's spawn
     * list (a regular level does; a boss floor and the amulet floor do not). A class the switch
     * names that {@link #LEVELS} does not know fails the generation.
     */
    /**
     * Whether a level class empties the floor's spawn list: the nearest {@code createItems} up
     * its hierarchy is {@code RegularLevel}'s, which is the one method that places
     * {@code itemsToSpawn}, or one that defers to it. A boss floor's own {@code createItems}
     * drops its bones and its reward and never the list, and the amulet floor is not a regular
     * level at all.
     */
    static boolean placesSpawnList(Path root, Class<?> type) {
        for (Class<?> c = type; c != null && Level.class.isAssignableFrom(c); c = c.getSuperclass()) {
            Sources.Body body = Sources.body(root, c);
            int line = body.find("protected void createItems\\s*\\(\\s*\\)");
            if (line < 0) {
                continue;
            }
            if (c == RegularLevel.class) {
                return true;
            }
            if (!Sources.text(body.block(line)).contains("super.createItems()")) {
                return false;
            }
        }
        return false;
    }

    static List<Codex.Placement> placements(Path root, Sources.Body dungeon) {
        int line = dungeon.find("public static Level newLevel\\s*\\(\\s*\\)");
        if (line < 0) {
            throw new IllegalStateException("Dungeon declares no newLevel()");
        }
        Sources.Body body = dungeon.block(line);
        Map<String, Class<?>> byName = new TreeMap<>();
        for (Class<?> type : LEVELS) {
            byName.put(type.getSimpleName(), type);
        }
        Pattern branch = Pattern.compile("^\\s*if \\(branch == 0\\)");
        Pattern otherBranch = Pattern.compile("^\\s*\\}\\s*else if \\(branch ==");
        Pattern caseLine = Pattern.compile("^\\s*case (\\d+)\\s*:");
        Pattern built = Pattern.compile("level = new (\\w+)\\s*\\(");
        TreeMap<Integer, Codex.Placement> byDepth = new TreeMap<>();
        List<Integer> pending = new ArrayList<>();
        boolean inMainBranch = false;
        for (int i = body.from(); i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i));
            if (branch.matcher(text).find()) {
                inMainBranch = true;
                continue;
            }
            if (!inMainBranch) {
                continue;
            }
            if (otherBranch.matcher(text).find()) {
                break;
            }
            Matcher depth = caseLine.matcher(text);
            if (depth.find()) {
                pending.add(Integer.parseInt(depth.group(1)));
                continue;
            }
            Matcher make = built.matcher(text);
            if (make.find()) {
                Class<?> type = byName.get(make.group(1));
                if (type == null) {
                    throw new IllegalStateException(dungeon.path() + ":" + (i + 1) + ": the main branch builds a " + make.group(1)
                            + ", which the generator does not name; add it to Guarantees.LEVELS");
                }
                boolean places = placesSpawnList(root, type);
                for (int at : pending) {
                    if (byDepth.put(at, new Codex.Placement(at, Sources.name(type), places, dungeon.citation(i))) != null) {
                        throw new IllegalStateException("the main branch builds depth " + at + " twice");
                    }
                }
                pending.clear();
            }
        }
        if (byDepth.isEmpty()) {
            throw new IllegalStateException("the main branch's switch named no depth; newLevel no longer reads as the generator knows");
        }
        List<Codex.Placement> placements = new ArrayList<>();
        for (int depth = 1; depth <= byDepth.lastKey(); depth++) {
            Codex.Placement placement = byDepth.get(depth);
            if (placement == null) {
                throw new IllegalStateException("the main branch names no level for depth " + depth);
            }
            placements.add(placement);
        }
        return placements;
    }

    static Codex.Guarantees read(Path root) {
        Sources.Body dungeon = Sources.file(root, DUNGEON);
        Sources.Body level = Sources.file(root, LEVEL);
        Sources.Body specialRoom = Sources.file(root, SPECIAL_ROOM);
        int bossLine = pinned(dungeon, "bossLevel", "public static boolean bossLevel\\s*\\(\\s*int depth\\s*\\)", BOSS);
        List<Integer> bossDepths = new ArrayList<>();
        Matcher boss = Pattern.compile("depth == (\\d+)").matcher(BOSS);
        while (boss.find()) {
            bossDepths.add(Integer.parseInt(boss.group(1)));
        }
        List<Codex.Placement> placements = placements(root, dungeon);
        int maxDepth = placements.size();
        if (maxDepth != MAX_DEPTH) {
            throw new IllegalStateException("the main branch builds " + maxDepth + " floors and the tables count to " + MAX_DEPTH);
        }
        for (int depth : bossDepths) {
            if (depth <= maxDepth && placements.get(depth - 1).placesSpawnList()) {
                throw new IllegalStateException("depth " + depth + " is a boss floor and its level empties the spawn list; the gate and the level classes disagree");
            }
        }
        int gate = level.find("^\\s*if \\(!Dungeon\\.bossLevel\\(\\) && Dungeon\\.branch == 0\\)");
        if (gate < 0) {
            throw new IllegalStateException("Level.create no longer gates the guaranteed drops as the generator knows");
        }
        Sources.Body gateBlock = level.block(gate);
        String gateExpression = Sources.stripComment(level.lines().get(gate)).trim();
        int noScrolls = level.find("Challenges\\.NO_SCROLLS");
        if (noScrolls < 0) {
            throw new IllegalStateException("Level.create no longer withholds an upgrade scroll under Forbidden Runes");
        }
        String noScrollsExpression = Sources.stripComment(level.lines().get(noScrolls)).trim();
        List<String> counters = counters(dungeon);
        int enumLine = dungeon.declaration("LimitedDrops");
        List<Codex.DropSchedule> drops = new ArrayList<>();
        for (Mirror mirror : MIRRORS) {
            int decides = pinned(dungeon, mirror.method(), "public static boolean " + mirror.method() + "\\s*\\(\\s*\\)", mirror.pinned());
            Sources.Body placer = mirror.placer().equals(LEVEL) ? level : specialRoom;
            int places = pinned(placer, mirror.method() + "'s placement", "^\\s*if\\s*\\(\\s*Dungeon\\." + mirror.method() + "\\(\\)\\s*\\)",
                    mirror.placedPinned());
            if (placer == level && (places < gateBlock.from() || places >= gateBlock.to())) {
                throw new IllegalStateException("what " + mirror.method() + " decides is placed at " + placer.path() + ":" + (places + 1)
                        + ", outside the gate at " + (gateBlock.from() + 1) + "-" + gateBlock.to() + "; the placed column would be wrong");
            }
            List<Codex.ScheduleEntry> entries = new ArrayList<>();
            for (int depth = 1; depth <= maxDepth; depth++) {
                boolean placed = placements.get(depth - 1).placesSpawnList();
                for (int count = 0; count <= mirror.maxCount(); count++) {
                    int needed = mirror.schedule().neededPerMille(depth, count);
                    int placedPerMille = placed ? needed : 0;
                    int noScrollsPerMille = mirror.name().equals("UPGRADE_SCROLLS") && (count + 1) % 2 == 0 ? 0 : placedPerMille;
                    entries.add(new Codex.ScheduleEntry(depth, count, needed, placedPerMille, noScrollsPerMille));
                }
            }
            drops.add(new Codex.DropSchedule(mirror.name(), mirror.item(), mirror.method(), mirror.once(), mirror.perSet(),
                    Sources.text(dungeon.block(decides)), dungeon.citation(decides), placer.citation(places), entries));
        }
        return new Codex.Guarantees(counters, dungeon.citation(enumLine), bossDepths, dungeon.citation(bossLine), gateExpression, level.citation(gate),
                noScrollsExpression, level.citation(noScrolls), placements, drops);
    }

    /**
     * The counters the game keeps, in the enum's order: every constant of {@code LimitedDrops}
     * up to the semicolon that ends them. A line inside the constants the reader cannot read as
     * one plain constant fails the generation rather than dropping it.
     */
    static List<String> counters(Sources.Body dungeon) {
        Sources.Body body = dungeon.block(dungeon.declaration("LimitedDrops"));
        List<String> counters = new ArrayList<>();
        Pattern constant = Pattern.compile("^([A-Z][A-Z0-9_]*)\\s*[,;]$");
        for (int i = body.from() + 1; i < body.to(); i++) {
            String text = Sources.stripComment(body.lines().get(i)).trim();
            if (text.isEmpty()) {
                continue;
            }
            Matcher one = constant.matcher(text);
            if (!one.matches()) {
                throw new IllegalStateException(body.path() + ":" + (i + 1) + ": the LimitedDrops constants no longer read one plain name to a line: " + text);
            }
            counters.add(one.group(1));
            if (text.endsWith(";")) {
                return counters;
            }
        }
        throw new IllegalStateException(body.path() + ": the LimitedDrops constants do not end in a semicolon");
    }

    /** The line declaring {@code what}, whose block reads exactly as {@code pinned}; a text that moved fails naming it. */
    static int pinned(Sources.Body body, String what, String anchor, String pinned) {
        int line = body.find(anchor);
        if (line < 0) {
            throw new IllegalStateException(body.path() + " declares no " + what);
        }
        String actual = Sources.text(body.block(line));
        if (!actual.equals(pinned)) {
            throw new IllegalStateException("the mirror of " + what + " was written for other text; re-read " + body.path() + ":" + (line + 1)
                    + "\nexpected: " + pinned + "\nfound:    " + actual);
        }
        return line;
    }
}
