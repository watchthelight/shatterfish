package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.log.DebugRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The practice table's hit points lost by source (story 10.21) read the Run's debug record as the screen drew it.
 * Planted records show each source is told apart, that an ambiguous turn is counted as one, and that the controls
 * (a record with no loss, no record at all) read as nothing.
 */
class PracticeDamageTest {

    /** One wait on the sealed floor: hit points, hero cell, buffs, Tengu's distance (-1 for out of view), extras. */
    private static String wait(int hp, int cell, String buffs, int dist, String extra, String log) {
        String enemies = dist < 0 ? "[]" : "[{\"buffs\":[],\"cell\":300,\"dist\":" + dist + ",\"name\":\"Tengu\",\"pips\":6,\"water\":false}]";
        java.util.TreeMap<String, String> members = new java.util.TreeMap<>();
        members.put("depth", "10");
        members.put("enemies", enemies);
        members.put("hero_buffs", "[" + buffs + "]");
        members.put("hero_cell", Integer.toString(cell));
        members.put("hp", Integer.toString(hp));
        members.put("log", "[" + log + "]");
        members.put("sealed", "true");
        members.put("t", "\"wait\"");
        if (!extra.isEmpty()) {
            for (String pair : extra.split("(?<=\\]),")) {
                int colon = pair.indexOf(':');
                members.put(pair.substring(1, colon - 1), pair.substring(colon + 1));
            }
        }
        StringBuilder out = new StringBuilder("{");
        members.forEach((k, v) -> out.append(out.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
        return out.append('}').toString();
    }

    private static final String END = "{\"cause\":\"DEATH\",\"killer\":\"Tengu\",\"t\":\"end\"}";

    private static PracticeDamage.Loss loss(Path folder, String name, String... lines) throws IOException {
        Path log = folder.resolve(name + ".jsonl");
        Files.writeString(log, "", StandardCharsets.UTF_8);
        if (lines.length > 0) {
            Files.write(DebugRecord.beside(log), List.of(lines), StandardCharsets.UTF_8);
        }
        return PracticeDamage.of(log);
    }

    private static int of(PracticeDamage.Loss loss, PracticeDamage.Source source) {
        return loss.bySource().get(source);
    }

    @Test
    @DisplayName("a blow beside Tengu is melee, a blow from farther is his thrown attack, and the last blow takes the hit points left")
    void blows(@TempDir Path folder) throws IOException {
        PracticeDamage.Loss loss = loss(folder, "a",
                wait(60, 100, "", 1, "", ""), wait(54, 100, "", 1, "", ""),   // melee 6
                wait(54, 101, "", 4, "", ""), wait(47, 102, "", 3, "", ""),  // thrown 7
                wait(47, 102, "", 3, "", ""), END);                            // died on the last wait: 47 left to the blow
        assertEquals(6, of(loss, PracticeDamage.Source.MELEE));
        assertEquals(7 + 47, of(loss, PracticeDamage.Source.THROWN));
        assertEquals(0, loss.ambiguous());
    }

    @Test
    @DisplayName("a bomb gone from beside the hero is the bomb's, the shocker's field is the shock's, a dart trap in the log is the trap's")
    void ground(@TempDir Path folder) throws IOException {
        PracticeDamage.Loss loss = loss(folder, "b",
                wait(60, 100, "", 5, "\"bombs\":[101]", ""), wait(40, 100, "", 5, "", ""),
                wait(40, 100, "", 5, "\"shock\":[100],\"shockers\":[99]", ""), wait(28, 100, "", 5, "", ""),
                wait(28, 100, "", 5, "", ""), wait(20, 101, "", 5, "", "\"A hidden poison dart trap activates!\""));
        assertEquals(20, of(loss, PracticeDamage.Source.BOMB));
        assertEquals(12, of(loss, PracticeDamage.Source.SHOCK));
        assertEquals(8, of(loss, PracticeDamage.Source.TRAP));
        assertEquals(0, of(loss, PracticeDamage.Source.THROWN));
    }

    @Test
    @DisplayName("a burning tick is capped, the rest of a turn that also holds a blow is Tengu's, and the turn is counted as ambiguous")
    void ambiguous(@TempDir Path folder) throws IOException {
        String burning = "{\"name\":\"burning\",\"turns\":3}";
        PracticeDamage.Loss loss = loss(folder, "c",
                wait(60, 100, burning, 1, "", ""), wait(50, 100, burning, 1, "", ""),
                wait(50, 100, burning, -1, "", ""), wait(48, 100, burning, -1, "", ""),
                wait(48, 100, "", 1, "", ""));
        // 10 lost beside him while burning: a tick of at most 5 is the fire's, the other 5 the blow's; 2 lost with no
        // one in view while burning is all fire and not ambiguous.
        assertEquals(5 + 2, of(loss, PracticeDamage.Source.FIRE));
        assertEquals(5, of(loss, PracticeDamage.Source.MELEE));
        assertEquals(10, loss.ambiguous());
    }

    @Test
    @DisplayName("hit points gained are counted as healed, a loss with nothing on the screen is unknown, and a record with no loss reads as nothing")
    void controls(@TempDir Path folder) throws IOException {
        PracticeDamage.Loss quiet = loss(folder, "d", wait(30, 100, "", 1, "", ""), wait(60, 100, "", -1, "", ""),
                wait(57, 100, "", -1, "", ""));
        assertEquals(30, quiet.healed());
        assertEquals(3, of(quiet, PracticeDamage.Source.UNKNOWN));
        assertEquals(0, loss(folder, "e", wait(60, 100, "", 1, "", ""), wait(60, 100, "", 1, "", "")).total());
        assertEquals(0, loss(folder, "f").total());
    }
}
