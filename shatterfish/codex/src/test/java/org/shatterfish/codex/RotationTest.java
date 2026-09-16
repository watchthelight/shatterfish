package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rotation reader on fixtures (story 2.2): every shape it relies on fails loudly when the
 * source stops having it, rather than dropping a rule.
 */
class RotationTest {

    private static final Map<String, String> CANONICAL = Map.of("Rat", "actors.mobs.Rat", "Snake", "actors.mobs.Snake",
            "Thief", "actors.mobs.Thief");

    private static Sources.Body spawner(String... rotationLines) {
        StringBuilder text = new StringBuilder("public class MobSpawner {\n    private static ArrayList<Class<? extends Mob>> standardMobRotation( int depth ){\n        switch(depth){\n");
        for (String line : rotationLines) {
            text.append(line).append('\n');
        }
        text.append("        }\n    }\n    public static void addRareMobs( int depth, ArrayList<Class<?extends Mob>> rotation ){\n        switch (depth){\n"
                + "            default: return;\n            case 4:\n                if (Random.Float() < 0.025f) rotation.add(Thief.class);\n                return;\n        }\n    }\n"
                + "    private static void swapMobAlts(ArrayList<Class<?extends Mob>> rotation) {\n        float altChance = 1 / 50f * RatSkull.exoticChanceMultiplier();\n    }\n}\n");
        List<String> lines = List.of(text.toString().split("\n"));
        return new Sources.Body("core/src/main/java/MobSpawner.java", lines, 0, lines.size(), "MobSpawner");
    }

    private static String[] everyDepth() {
        StringBuilder cases = new StringBuilder("            case 1: default:\n");
        for (int d = 2; d <= Mobs.MAX_DEPTH; d++) {
            cases.append("            case ").append(d).append(":\n");
        }
        cases.append("                return new ArrayList<>(Arrays.asList(Rat.class, Rat.class,\n                        Snake.class));");
        return cases.toString().split("\n");
    }

    @Test
    @DisplayName("the depths are read with their counts, the default arm reported, a case group over several lines cited to its first")
    void depths_are_read() {
        int[] defaultDepth = new int[1];
        List<Codex.RotationDepth> depths = Rotation.depths(spawner(everyDepth()), new TreeSet<>(), CANONICAL, defaultDepth);
        assertEquals(Mobs.MAX_DEPTH, depths.size());
        assertEquals(1, defaultDepth[0]);
        assertEquals(List.of("actors.mobs.Rat", "actors.mobs.Snake"), depths.get(0).entries().stream().map(Codex.RotationEntry::className).toList());
        assertEquals(List.of(2, 1), depths.get(0).entries().stream().map(Codex.RotationEntry::count).toList());
        assertEquals(depths.get(0).citation(), depths.get(25).citation(), "one group, one citation");
        assertEquals(4, depths.get(0).citation().line());
    }

    @Test
    @DisplayName("a missing depth, a literal that is not Arrays.asList, a class the table lacks and a family not read each fail naming the trouble")
    void the_shapes_fail_loudly() {
        String[] missingSeven = everyDepth();
        missingSeven[6] = "            case 27:";
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> Rotation.depths(spawner(missingSeven), new TreeSet<>(), CANONICAL, new int[1]));
        assertTrue(missing.getMessage().contains("depth 27") || missing.getMessage().contains("depth 7"), missing.getMessage());
        String[] notAsList = everyDepth();
        notAsList[26] = "                return new ArrayList<>(List.of(Rat.class,";
        IllegalStateException list = assertThrows(IllegalStateException.class,
                () -> Rotation.depths(spawner(notAsList), new TreeSet<>(), CANONICAL, new int[1]));
        assertTrue(list.getMessage().contains("Arrays.asList"), list.getMessage());
        String[] unknown = everyDepth();
        unknown[27] = "                        Dragon.class));";
        IllegalStateException stranger = assertThrows(IllegalStateException.class,
                () -> Rotation.depths(spawner(unknown), new TreeSet<>(), CANONICAL, new int[1]));
        assertTrue(stranger.getMessage().contains("Dragon"), stranger.getMessage());
        String[] family = everyDepth();
        family[27] = "                        Shaman.random()));";
        IllegalStateException unread = assertThrows(IllegalStateException.class,
                () -> Rotation.depths(spawner(family), new TreeSet<>(), CANONICAL, new int[1]));
        assertTrue(unread.getMessage().contains("Shaman"), unread.getMessage());
    }

    @Test
    @DisplayName("the rare mobs are read with their chance, and an add the reader cannot read fails by count")
    void rare_mobs_are_counted() {
        Sources.Body ok = spawner(everyDepth());
        List<Codex.RareMob> rare = Rotation.rareMobs(ok, CANONICAL);
        assertEquals(1, rare.size());
        assertEquals(4, rare.get(0).depth());
        assertEquals(25, rare.get(0).perMille());
        assertEquals("actors.mobs.Thief", rare.get(0).className());
        assertEquals(20, Rotation.alternateChance(ok));
        assertEquals("1 / 50f * RatSkull.exoticChanceMultiplier()", Rotation.alternateChanceExpression(ok));
        List<String> lines = new java.util.ArrayList<>(ok.lines());
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("rotation.add(Thief.class)")) {
                lines.set(i, "                if (Random.Float() < 0.025f) { rotation.add(Thief.class); }");
            }
        }
        Sources.Body braced = new Sources.Body(ok.path(), lines, 0, lines.size(), "MobSpawner");
        IllegalStateException uncounted = assertThrows(IllegalStateException.class, () -> Rotation.rareMobs(braced, CANONICAL));
        assertTrue(uncounted.getMessage().contains("adds 1 times and the reader read 0"), uncounted.getMessage());
    }
}
