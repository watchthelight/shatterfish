package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.log.DebugRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The practice table's "key held" and "seal reached" columns read the Run's debug record as text (story 10.18).
 * A planted record of each shape shows the columns discriminate: none, a key only, a key and the seal, and a
 * record that is missing, which counts as neither.
 */
class PracticeReachColumnsTest {

    private static boolean[] reach(Path folder, String name, String... lines) throws IOException {
        Path log = folder.resolve(name + ".jsonl");
        Files.writeString(log, "", StandardCharsets.UTF_8);
        if (lines.length > 0) {
            Files.write(DebugRecord.beside(log), java.util.List.of(lines), StandardCharsets.UTF_8);
        }
        return PracticeRuns.reachOf(log);
    }

    @Test
    @DisplayName("a record with no key and no seal, a key alone, a key and the seal, and no record at all read as four different rows")
    void four_shapes(@TempDir Path folder) throws IOException {
        String bare = "{\"depth\":10,\"keys\":[],\"sealed\":false,\"t\":\"wait\"}";
        String keyed = "{\"depth\":10,\"keys\":[\"iron key\"],\"sealed\":false,\"t\":\"wait\"}";
        String sealed = "{\"depth\":10,\"keys\":[],\"sealed\":true,\"t\":\"wait\"}";
        assertArrayEquals(new boolean[] {false, false}, reach(folder, "a", bare));
        assertArrayEquals(new boolean[] {true, false}, reach(folder, "b", bare, keyed));
        assertArrayEquals(new boolean[] {true, true}, reach(folder, "c", bare, keyed, sealed));
        assertArrayEquals(new boolean[] {false, false}, reach(folder, "d"));
    }
}
