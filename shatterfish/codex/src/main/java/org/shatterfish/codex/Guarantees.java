package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Stylus;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.LaboratoryRoom;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * each mirror to the game's own method sampled under a seeded generator. The placement is the
 * level's: no boss floor places any, and under Forbidden Runes every second upgrade scroll is
 * withheld after its counter moved. {@code Dungeon} is read as source only, never as a class.
 */
final class Guarantees {

    static final String DUNGEON = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/Dungeon.java";
    static final String LEVEL = Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/Level.java";
    static final int MAX_DEPTH = Mobs.MAX_DEPTH;

    /** One draw's outcome as a chance in thousandths: {@code hits} of {@code n} outcomes say yes; {@code n <= 0} draws zero. */
    private static int chance(int hits, int n) {
        if (n <= 0) {
            return hits > 0 ? 1000 : 0;
        }
        return Sources.thousandths(Math.min(hits, n) + "/" + n);
    }

    /** The chance in thousandths a drop is needed at {@code depth} with its counter at {@code count}. */
    interface Schedule {
        int neededPerMille(int depth, int count);
    }

    /** A mirror of one of the game's methods: the counter, the item, the method, its shape, the pinned text and the arithmetic. */
    record Mirror(String name, String item, String method, boolean once, int perSet, int maxCount, String pinned, Schedule schedule) {
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

    static final List<Mirror> MIRRORS = List.of(
            new Mirror("STRENGTH_POTIONS", Sources.name(PotionOfStrength.class), "posNeeded", false, 2, 12, POS, Guarantees::pos),
            new Mirror("UPGRADE_SCROLLS", Sources.name(ScrollOfUpgrade.class), "souNeeded", false, 3, 18, SOU, Guarantees::sou),
            new Mirror("ARCANE_STYLI", Sources.name(Stylus.class), "asNeeded", false, 1, 6, AS, Guarantees::stylus),
            new Mirror("ENCH_STONE", Sources.name(StoneOfEnchantment.class), "enchStoneNeeded", true, 1, 1, ENCH, Guarantees::enchantment),
            new Mirror("INT_STONE", Sources.name(StoneOfIntuition.class), "intStoneNeeded", true, 1, 1, INT, Guarantees::intuition),
            new Mirror("TRINKET_CATA", Sources.name(TrinketCatalyst.class), "trinketCataNeeded", true, 1, 1, CATA, Guarantees::catalyst),
            new Mirror("LAB_ROOM", Sources.name(LaboratoryRoom.class), "labRoomNeeded", false, 1, 6, LAB, Guarantees::laboratory));

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
        int n = 5 - depth % 5;
        return chance(left, n);
    }

    /** {@code asNeeded}: one per set; the chance is styli left over floors left. */
    static int stylus(int depth, int count) {
        int left = 1 - (count - depth / 5);
        if (left <= 0) {
            return 0;
        }
        int n = 5 - depth % 5;
        return chance(left, n);
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

    static Codex.Guarantees read(Path root) {
        Sources.Body dungeon = Sources.file(root, DUNGEON);
        Sources.Body level = Sources.file(root, LEVEL);
        int bossLine = pinned(dungeon, "bossLevel", "public static boolean bossLevel\\s*\\(\\s*int depth\\s*\\)", BOSS);
        List<Integer> bossDepths = new ArrayList<>();
        Matcher boss = Pattern.compile("depth == (\\d+)").matcher(BOSS);
        while (boss.find()) {
            bossDepths.add(Integer.parseInt(boss.group(1)));
        }
        int placement = level.find("^\\s*if \\(!Dungeon\\.bossLevel\\(\\) && Dungeon\\.branch == 0\\)");
        int noScrolls = level.find("Challenges\\.NO_SCROLLS");
        if (placement < 0 || noScrolls < 0) {
            throw new IllegalStateException("Level.create no longer gates the guaranteed drops as the reader knows: placement " + placement + ", Forbidden Runes " + noScrolls);
        }
        String noScrollsExpression = Sources.stripComment(level.lines().get(noScrolls)).trim();
        int enumLine = dungeon.declaration("LimitedDrops");
        Sources.Body enumBody = dungeon.block(enumLine);
        List<String> counters = new ArrayList<>();
        Pattern constant = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*[,;]");
        for (int i = enumBody.from() + 1; i < enumBody.to(); i++) {
            Matcher m = constant.matcher(Sources.stripComment(enumBody.lines().get(i)));
            if (m.find()) {
                counters.add(m.group(1));
            }
            if (Sources.stripComment(enumBody.lines().get(i)).contains(";")) {
                break;
            }
        }
        List<Codex.DropSchedule> drops = new ArrayList<>();
        for (Mirror mirror : MIRRORS) {
            int line = pinned(dungeon, mirror.method(), "public static boolean " + mirror.method() + "\\s*\\(\\s*\\)", mirror.pinned());
            int placed = level.find("^\\s*if\\s*\\(\\s*Dungeon\\." + mirror.method() + "\\(\\)\\s*\\)");
            if (placed < 0) {
                placed = Sources.file(root, Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/rooms/special/SpecialRoom.java")
                        .find("^\\s*if\\s*\\(\\s*Dungeon\\." + mirror.method() + "\\(\\)\\s*\\)");
                if (placed < 0) {
                    throw new IllegalStateException("nothing places what " + mirror.method() + " decides");
                }
            }
            Sources.Body placer = level.find("^\\s*if\\s*\\(\\s*Dungeon\\." + mirror.method() + "\\(\\)\\s*\\)") >= 0 ? level
                    : Sources.file(root, Sources.SOURCE_ROOT + Sources.GAME.replace('.', '/') + "/levels/rooms/special/SpecialRoom.java");
            List<Codex.ScheduleEntry> entries = new ArrayList<>();
            for (int depth = 1; depth <= MAX_DEPTH; depth++) {
                for (int count = 0; count <= mirror.maxCount(); count++) {
                    int needed = mirror.schedule().neededPerMille(depth, count);
                    int placedPerMille = bossDepths.contains(depth) ? 0 : needed;
                    int noScrollsPerMille = mirror.name().equals("UPGRADE_SCROLLS") && (count + 1) % 2 == 0 ? 0 : placedPerMille;
                    entries.add(new Codex.ScheduleEntry(depth, count, needed, placedPerMille, noScrollsPerMille));
                }
            }
            drops.add(new Codex.DropSchedule(mirror.name(), mirror.item(), mirror.method(), mirror.once(), mirror.perSet(),
                    Sources.text(dungeon.block(line)), dungeon.citation(line), placer.citation(placed), entries));
        }
        return new Codex.Guarantees(counters, dungeon.citation(enumLine), bossDepths, dungeon.citation(bossLine), level.citation(placement),
                noScrollsExpression, level.citation(noScrolls), drops);
    }

    /** The line declaring {@code method}, whose block reads exactly as {@code pinned}; a text that moved fails naming the method. */
    static int pinned(Sources.Body body, String method, String anchor, String pinned) {
        int line = body.find(anchor);
        if (line < 0) {
            throw new IllegalStateException(body.path() + " declares no " + method);
        }
        String actual = Sources.text(body.block(line));
        if (!actual.equals(pinned)) {
            throw new IllegalStateException("the mirror of " + method + " was written for other text; re-read " + body.path() + ":" + (line + 1)
                    + "\nexpected: " + pinned + "\nfound:    " + actual);
        }
        return line;
    }
}
