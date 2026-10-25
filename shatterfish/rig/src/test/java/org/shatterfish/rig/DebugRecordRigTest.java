package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.log.DebugRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The debug record of a Brain's Runs through the Rig (issue #219): one file per Run log, beside it,
 * with the fights in it, and no file the Rig reads as a Run log.
 */
class DebugRecordRigTest {

    @Test
    @DisplayName("every Run the Rig plays leaves a debug record with the enemies it met and an end line")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void every_run_leaves_a_record(@TempDir Path out) throws IOException {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.SHATTERFISH);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        arguments.put(Runner.PARALLEL, "4");
        arguments.put(Runner.CAP, "400");

        Runner.run(arguments);

        List<Path> logs = Verify.logs(out);
        assertTrue(logs.size() > 0);
        int fights = 0;
        for (Path log : logs) {
            Path debug = DebugRecord.beside(log);
            assertTrue(Files.isRegularFile(debug), "a record beside " + log.getFileName());
            List<String> lines = Files.readAllLines(debug, StandardCharsets.UTF_8);
            String end = lines.get(lines.size() - 1);
            assertTrue(end.contains("\"t\":\"end\"") && end.contains("\"cause\":"), end);
            for (String line : lines) {
                if (line.contains("\"pips\":")) {
                    fights++;
                    assertTrue(line.contains("\"water\":") && line.contains("\"dist\":") && line.contains("\"name\":")
                            && line.contains("\"hp\":") && line.contains("\"why\":"), line);
                }
            }
        }
        assertTrue(fights > 0, "some wait of some Run had an enemy in view");
        try (var listed = Files.list(out)) {
            assertEquals(logs.size(), listed.filter(p -> p.getFileName().toString().endsWith(DebugRecord.SUFFIX)).count());
        }
    }
}
