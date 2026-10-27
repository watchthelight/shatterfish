package org.shatterfish.harness.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 8.1 added two absent-when-empty fields to the Decision record. This holds the claim that
 * made that safe: every Run log already committed reads and writes back to the same bytes, so its
 * chain is the one it had, and a Decision with terms and a Search block survives a round trip.
 */
class DecisionRecordCompatibilityTest {

    /** The committed Run logs: the reference Runs and the published Overlay Run. */
    private static List<Path> committedLogs() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("reference"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "the repository root is found above " + Path.of("").toAbsolutePath());
        List<Path> logs = new ArrayList<>();
        for (String folder : List.of("reference", "docs/results/logs")) {
            Path directory = root.resolve(folder);
            if (Files.isDirectory(directory)) {
                try (Stream<Path> files = Files.list(directory)) {
                    files.filter(file -> file.getFileName().toString().matches(".*-[0-9a-f]{16}-[a-z0-9._]+\\.jsonl")
                                    || file.getFileName().toString().matches("[A-Za-z0-9._]+-[A-Z]+-\\d+-.*\\.jsonl"))
                            .forEach(logs::add);
                }
            }
        }
        return logs;
    }

    @Test
    @DisplayName("every committed Run log's chain verifies, and each line reads and writes back byte for byte")
    void the_committed_logs_keep_their_bytes() throws IOException {
        List<Path> logs = committedLogs();
        assertFalse(logs.isEmpty(), "there are committed Run logs to hold to this");
        for (Path file : logs) {
            String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(RunLogVerifier.of(text).ok(), file + ": " + RunLogVerifier.of(text).why());
            RunLogReader.Log log = RunLogReader.of(text);
            assertTrue(log.readable() && log.complete(), file + ": " + log.unreadable());
            String[] lines = text.split("\n");
            assertEquals(lines.length, log.records().size(), file.toString());
            String previous = "";
            for (int i = 0; i < lines.length; i++) {
                String written = RunLogJson.line(previous, log.records().get(i));
                assertEquals(lines[i], written, file + " line " + (i + 1));
                previous = RunLogJson.chain(previous, log.records().get(i));
            }
        }
    }

    @Test
    @DisplayName("terms and Search outcomes read back as written, negative contributions included")
    void a_decision_with_both_round_trips() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Step(3), 1_667, "uniform 1/6",
                List.of(new RunLog.Term("depth", 10_000), new RunLog.Term("enemies", -3_000),
                        new RunLog.Term("tier", -5_333)));
        RunLog.Choice other = new RunLog.Choice(new Action.Wait(), 0, "lower 1/1");
        RunLog.Decision decision = new RunLog.Decision("act", chosen, List.of(other), List.of("flag"), "fallback",
                List.of(new RunLog.SearchOutcome(new Action.Step(3), -250, 12), new RunLog.SearchOutcome(new Action.Wait(), 900, 7)));
        RunLog.Wait wait = new RunLog.Wait(1, 0, 0, 0, "a".repeat(64), Map.of("hero", "b".repeat(64)),
                new Action.Step(3), true, RunLog.BOT, decision, "", List.of(), 4);
        String line = RunLogJson.line("", wait);
        RunLogReader.Log log = RunLogReader.of(line + "\n");
        assertTrue(log.readable() && log.complete(), log.unreadable());
        assertEquals(wait, log.records().get(0));
        assertEquals(line, RunLogJson.line("", log.records().get(0)));
        assertTrue(RunLogVerifier.of(line + "\n").ok());
    }
}
