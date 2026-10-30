package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.log.DebugRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The practice table's "phase 2 reached", "dart patches drawn" and "boss pips at death" columns read the Run's
 * debug record as text (story 10.19). The record writes its keys in alphabetical order, so Tengu's name is
 * followed by his pips; planted records of each shape show the columns discriminate.
 */
class PracticeFoughtColumnsTest {

    private static PracticeRuns.Fought fought(Path folder, String name, String... lines) throws IOException {
        Path log = folder.resolve(name + ".jsonl");
        Files.writeString(log, "", StandardCharsets.UTF_8);
        if (lines.length > 0) {
            Files.write(DebugRecord.beside(log), java.util.List.of(lines), StandardCharsets.UTF_8);
        }
        return PracticeRuns.foughtOf(log);
    }

    private static String wait(int darts, int pips, String log) {
        String enemies = pips < 0 ? "[]" : "[{\"buffs\":[],\"cell\":293,\"dist\":1,\"name\":\"Tengu\",\"pips\":" + pips + ",\"water\":false}]";
        return "{\"darts\":" + darts + ",\"enemies\":" + enemies + ",\"log\":[" + log + "],\"t\":\"wait\"}";
    }

    @Test
    @DisplayName("patches are counted as a run of waits that draw some, the yell marks the second half, and the last pips seen are the boss's at death")
    void shapes(@TempDir Path folder) throws IOException {
        // Two patches (a run of two draws, a gap, one draw), no yell, the boss last seen at 7 pips.
        PracticeRuns.Fought first = fought(folder, "a", wait(0, 11, ""), wait(5, 11, ""), wait(5, 10, ""), wait(0, 9, ""),
                wait(3, 8, ""), wait(0, 7, ""));
        assertEquals(new PracticeRuns.Fought(false, 7, 2), first);

        // The yell in a wait's log is the second half; a wait that has lost sight of him leaves the pips as they were.
        PracticeRuns.Fought second = fought(folder, "b", wait(0, 6, ""),
                wait(0, 5, "\"" + PracticeRuns.PHASE_TWO_YELL + "\""), wait(0, -1, ""));
        assertEquals(new PracticeRuns.Fought(true, 5, 0), second);

        // The controls: no record, and a record of waits with none of it, read as nothing.
        assertEquals(new PracticeRuns.Fought(false, -1, 0), fought(folder, "c"));
        assertEquals(new PracticeRuns.Fought(false, -1, 0), fought(folder, "d", wait(0, -1, "")));
    }
}
