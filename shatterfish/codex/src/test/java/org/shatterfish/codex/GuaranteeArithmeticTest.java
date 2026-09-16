package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.watabou.utils.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guarantee arithmetic is the game's (story 2.4): every schedule entry is held against the
 * game's own method, called with the depth and the counter set and sampled under a seeded
 * generator, exact where the table says never or always and within four standard deviations
 * otherwise; every tier row against the game's own draws by floor set; and the room lists
 * against what the game's queues hold after a Run's shuffle. The test side may set
 * {@code Dungeon}'s statics; the generator may not, which is why the schedules are mirrors.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class GuaranteeArithmeticTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;
    private static final int SAMPLES = 2000;
    private static final int TOLERANCE = 45;

    @Test
    @DisplayName("every schedule entry reproduces the game's own decision at that depth and counter, sampled under a seeded generator")
    void the_schedules_are_the_games() {
        Codex.Guarantees guarantees = Guarantees.read(ROOT);
        assertEquals(List.of(5, 10, 15, 20, 25), guarantees.bossDepths());
        for (int depth = 1; depth <= Guarantees.MAX_DEPTH; depth++) {
            assertEquals(Dungeon.bossLevel(depth), guarantees.bossDepths().contains(depth), "boss at depth " + depth);
        }
        int depthBefore = Dungeon.depth;
        try {
            for (Codex.DropSchedule drop : guarantees.drops()) {
                Dungeon.LimitedDrops counter = Dungeon.LimitedDrops.valueOf(drop.name());
                int countBefore = counter.count;
                try {
                    int states = 0;
                    for (Codex.ScheduleEntry entry : drop.entries()) {
                        Dungeon.depth = entry.depth();
                        counter.count = entry.count();
                        int hits = 0;
                        Random.pushGenerator(1_000_003L + entry.depth() * 131L + entry.count());
                        try {
                            for (int i = 0; i < SAMPLES; i++) {
                                hits += decide(drop.method()) ? 1 : 0;
                            }
                        } finally {
                            Random.popGenerator();
                        }
                        String state = drop.name() + " at depth " + entry.depth() + " with the counter at " + entry.count();
                        if (entry.neededPerMille() == 0) {
                            assertEquals(0, hits, state + " is never needed, and the game said yes");
                        } else if (entry.neededPerMille() == 1000) {
                            assertEquals(SAMPLES, hits, state + " is always needed, and the game said no");
                        } else {
                            int observed = (int) Math.round(1000.0 * hits / SAMPLES);
                            assertTrue(Math.abs(observed - entry.neededPerMille()) <= TOLERANCE,
                                    state + ": the game says " + observed + " per mille, the table " + entry.neededPerMille());
                        }
                        assertEquals(guarantees.bossDepths().contains(entry.depth()) ? 0 : entry.neededPerMille(), entry.placedPerMille(), state + " placed");
                        int noScrolls = drop.name().equals("UPGRADE_SCROLLS") && (entry.count() + 1) % 2 == 0 ? 0 : entry.placedPerMille();
                        assertEquals(noScrolls, entry.placedNoScrollsPerMille(), state + " under Forbidden Runes");
                        states++;
                    }
                    assertEquals(Guarantees.MAX_DEPTH * (maxCount(drop) + 1), states, drop.name() + " covers every depth and counter state");
                } finally {
                    counter.count = countBefore;
                }
            }
        } finally {
            Dungeon.depth = depthBefore;
        }
    }

    private static int maxCount(Codex.DropSchedule drop) {
        int max = 0;
        for (Codex.ScheduleEntry entry : drop.entries()) {
            max = Math.max(max, entry.count());
        }
        return max;
    }

    private static boolean decide(String method) {
        return switch (method) {
            case "posNeeded" -> Dungeon.posNeeded();
            case "souNeeded" -> Dungeon.souNeeded();
            case "asNeeded" -> Dungeon.asNeeded();
            case "enchStoneNeeded" -> Dungeon.enchStoneNeeded();
            case "intStoneNeeded" -> Dungeon.intStoneNeeded();
            case "trinketCataNeeded" -> Dungeon.trinketCataNeeded();
            case "labRoomNeeded" -> Dungeon.labRoomNeeded();
            default -> throw new IllegalStateException("the table mirrors a method the test does not call: " + method);
        };
    }

    @Test
    @DisplayName("every tier row reproduces the game's own armor, weapon and missile draws by floor set")
    void the_tiers_are_the_games() {
        Codex.Tiers tiers = Tiers.read(ROOT);
        assertEquals(5, tiers.rows().size());
        int samples = 20_000;
        try {
            for (Codex.TierRow row : tiers.rows()) {
                int sum = row.weights().stream().mapToInt(Integer::intValue).sum();
                int[] armors = new int[6];
                int[] weapons = new int[6];
                int[] missiles = new int[6];
                Random.pushGenerator(2_718_281L + row.floorSet());
                try {
                    for (int i = 0; i < samples; i++) {
                        Armor armor = Generator.randomArmor(row.floorSet());
                        MeleeWeapon weapon = Generator.randomWeapon(row.floorSet());
                        MissileWeapon missile = Generator.randomMissile(row.floorSet());
                        armors[armor.tier]++;
                        weapons[weapon.tier]++;
                        missiles[missile.tier]++;
                    }
                } finally {
                    Random.popGenerator();
                }
                for (int tier = 1; tier <= 5; tier++) {
                    int expected = (int) Math.round(1000.0 * row.weights().get(tier - 1) / sum);
                    for (Map.Entry<String, int[]> kind : Map.of("armor", armors, "weapon", weapons, "missile", missiles).entrySet()) {
                        int observed = (int) Math.round(1000.0 * kind.getValue()[tier] / samples);
                        String what = "floor set " + row.floorSet() + " tier " + tier + " " + kind.getKey();
                        if (expected == 0) {
                            assertEquals(0, kind.getValue()[tier], what + " is never drawn");
                        } else {
                            assertTrue(Math.abs(observed - expected) <= 25, what + ": the game draws " + observed + " per mille, the table " + expected);
                        }
                    }
                }
            }
            // The last row covers every depth past its set, since the game gates the index.
            assertEquals(Guarantees.MAX_DEPTH, tiers.rows().get(4).depthTo());
            Random.pushGenerator(99L);
            try {
                assertEquals(Generator.randomArmor(4).tier == 4 || Generator.randomArmor(4).tier == 5, true);
                for (int i = 0; i < 200; i++) {
                    assertTrue(Generator.randomArmor(9).tier >= 4, "a floor set past the table draws by the last row");
                }
            } finally {
                Random.popGenerator();
            }
        } finally {
            for (Generator.Category category : Generator.Category.values()) {
                Generator.reset(category);
            }
        }
    }

    @Test
    @DisplayName("the room lists are the game's: the crystal-key specials as the game publishes them, the queues as a Run's shuffle fills them")
    void the_room_lists_are_the_games() {
        Codex.Rooms rooms = Rooms.read(ROOT);
        Map<String, Codex.RoomList> lists = new TreeMap<>();
        for (Codex.RoomList list : rooms.lists()) {
            lists.put(list.name(), list);
        }
        assertEquals(names(SpecialRoom.CRYSTAL_KEY_SPECIALS), new TreeSet<>(lists.get("CRYSTAL_KEY_SPECIALS").members()));
        Random.pushGenerator(4_242L);
        try {
            SpecialRoom.initForRun();
            SecretRoom.initForRun();
        } finally {
            Random.popGenerator();
        }
        TreeSet<String> queued = new TreeSet<>(lists.get("EQUIP_SPECIALS").members());
        queued.addAll(lists.get("CONSUMABLE_SPECIALS").members());
        assertEquals(queued, names(SpecialRoom.runSpecials), "the special queue holds the equipment and consumable specials");
        assertEquals(new TreeSet<>(lists.get("ALL_SECRETS").members()), names(SecretRoom.runSecrets), "the secret queue holds every secret");
        TreeSet<String> all = new TreeSet<>();
        rooms.lists().forEach(l -> all.addAll(l.members()));
        TreeSet<String> table = new TreeSet<>();
        rooms.specials().forEach(r -> table.add(r.className()));
        rooms.secrets().forEach(r -> table.add(r.className()));
        assertEquals(table, all, "every room in the table is in a list and every listed room is in the table");
        assertEquals(List.of(2000, 2250, 2500, 2750, 3000), rooms.secretsPerRegionPerMille());
    }

    @Test
    @DisplayName("a mirror whose pinned text is not the method's fails the generation naming the method and both texts")
    void a_moved_mirror_fails() {
        Sources.Body dungeon = Sources.file(ROOT, Guarantees.DUNGEON);
        int line = Guarantees.pinned(dungeon, "souNeeded", "public static boolean souNeeded\\s*\\(\\s*\\)", Guarantees.SOU);
        assertTrue(line > 0);
        IllegalStateException moved = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Guarantees.pinned(dungeon, "souNeeded", "public static boolean souNeeded\\s*\\(\\s*\\)", Guarantees.SOU + " return true;"));
        assertTrue(moved.getMessage().contains("souNeeded") && moved.getMessage().contains("expected:") && moved.getMessage().contains("found:"), moved.getMessage());
        IllegalStateException missing = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Guarantees.pinned(dungeon, "noSuchNeeded", "public static boolean noSuchNeeded\\s*\\(", ""));
        assertTrue(missing.getMessage().contains("noSuchNeeded"), missing.getMessage());
        for (Guarantees.Mirror mirror : Guarantees.MIRRORS) {
            assertTrue(mirror.pinned().contains("Random.Int(") || mirror.pinned().contains("dropped()"), mirror.method() + " draws once or reads a flag");
        }
    }

    private static TreeSet<String> names(List<? extends Class<? extends Room>> types) {
        TreeSet<String> names = new TreeSet<>();
        for (Class<?> type : types) {
            names.add(Sources.name(type));
        }
        return names;
    }
}
