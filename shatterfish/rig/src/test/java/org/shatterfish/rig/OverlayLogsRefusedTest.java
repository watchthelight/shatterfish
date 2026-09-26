package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rig takes no Overlay Run for one of its own (story 5.1's fairness review): a log whose header
 * says {@code driver: embedded} is refused by name on every path that reads logs to count, score,
 * calibrate, replay or show them, and a headless log beside it is read as before.
 */
class OverlayLogsRefusedTest {

    private static final String ZERO = "0".repeat(64);

    private static Path log(Path folder, long seed, String driver) throws IOException {
        // The versions this build writes, so that nothing but the driver is a reason to refuse it.
        HeadlessBoot.ensure();
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, HeadlessBoot.pinnedTag(), "abc1234",
                HeroClass.WARRIOR, 0, seed, SeedSet.code(seed), 100L + seed, 150, Profile.VERSION,
                ObservationCodec.SCHEMA_VERSION, Codex.VERSION,
                new RunLog.Brain("random", "abc1234", ZERO), "", false, "a laptop", "2026-09-26T00:00:00Z", driver);
        RunLog.Outcome died = new RunLog.Outcome(false, false, 0, 1, 1_000, "DEATH", 0);
        String text = RunLogJson.line("", header) + "\n"
                + RunLogJson.line(RunLogJson.chain("", header), new RunLog.End(0, died, true)) + "\n";
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("an Overlay log is refused when scored, read back or shown; a headless one is read")
    void refused_everywhere(@TempDir Path folder) throws IOException {
        Path headless = log(folder, 1000, "");
        Path overlay = log(folder, 2000, RunLog.Header.EMBEDDED);
        assertTrue(Files.readString(overlay).contains("\"driver\":\"embedded\""), "the log says so, chained");
        assertFalse(Files.readString(headless).contains("driver"), "a headless log's bytes are unchanged");

        // The score SPRT and the Results pages and the calibration take (Comparison.outcome).
        assertNotNull(Comparison.outcome(headless));
        OverlayLogs.Refused scored = assertThrows(OverlayLogs.Refused.class, () -> Comparison.outcome(overlay));
        assertTrue(scored.getMessage().contains("Overlay"), scored.getMessage());

        // The runner's read-back of each Run it played (LogHeader).
        LogHeader.of(headless);
        assertThrows(OverlayLogs.Refused.class, () -> LogHeader.of(overlay));

        // The death gallery, which forgives an unreadable log but not this.
        String index = "{\"class\":\"WARRIOR\",\"log\":\"" + overlay.getFileName() + "\",\"runId\":\"x\"}";
        Files.write(folder.resolve(RunIndex.RUNS), List.of(index), StandardCharsets.UTF_8);
        assertThrows(OverlayLogs.Refused.class, () -> Gallery.of(folder));

        // The Replay (`--replay`), which refuses by the header rather than reporting a Run that
        // "did not reproduce" when it was never claimed to.
        assertNull(Replay.refusal(RunLogReader.of(headless).header()), "the headless log is replayable");
        assertEquals("driver", Replay.refusal(RunLogReader.of(overlay).header()).field());
    }

    @Test
    @DisplayName("an Overlay log with an unreadable later line is refused, not counted as missing")
    void refused_by_its_header_when_a_later_line_is_corrupt(@TempDir Path folder) throws IOException {
        Path overlay = log(folder, 3000, RunLog.Header.EMBEDDED);
        Path headless = log(folder, 4000, "");
        for (Path file : List.of(overlay, headless)) {
            String header = Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
            Files.writeString(file, header + "\n{\"t\":\"wait\",\"k\":\n", StandardCharsets.UTF_8);
        }
        assertFalse(RunLogReader.of(overlay).readable(), "the second line is corrupt");

        assertThrows(OverlayLogs.Refused.class, () -> Comparison.outcome(overlay));
        assertThrows(OverlayLogs.Refused.class, () -> LogHeader.of(overlay));
        assertNull(Comparison.outcome(headless), "a corrupt headless log is missing, as before");
        LogHeader.of(headless);
    }
}
