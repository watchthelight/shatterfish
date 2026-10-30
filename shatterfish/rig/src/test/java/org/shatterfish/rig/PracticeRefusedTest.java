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
import org.shatterfish.harness.practice.KitProfiles;
import org.shatterfish.harness.practice.Practice;
import org.shatterfish.harness.practice.PracticeBoss;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A practice Run is never ranked (E10, ADR-0024): every path that reads logs to count, compare or
 * score refuses one by name, no Registration can hold one, and the practice runner reads no set a
 * Registration is kept for.
 */
class PracticeRefusedTest {

    private static final String ZERO = "0".repeat(64);

    private static Path log(Path folder, long seed, String practice) throws IOException {
        HeadlessBoot.ensure();
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, HeadlessBoot.pinnedTag(), "abc1234",
                HeroClass.WARRIOR, 0, seed, SeedSet.code(seed), 100L + seed, 150, Profile.VERSION,
                ObservationCodec.SCHEMA_VERSION, Codex.VERSION,
                new RunLog.Brain("random", "abc1234", ZERO), "", false, "a laptop", "2026-09-26T00:00:00Z", "", -1, -1,
                practice);
        RunLog.Outcome died = new RunLog.Outcome(false, false, 0, 1, 1_000, "DEATH", 0);
        String text = RunLogJson.line("", header) + "\n"
                + RunLogJson.line(RunLogJson.chain("", header), new RunLog.End(0, died, true, "")) + "\n";
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("a practice log is refused when scored, read back or used as a direction baseline; an ordinary one is read")
    void refused_everywhere(@TempDir Path folder) throws IOException {
        String stamp = new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD).stamp(HeroClass.WARRIOR);
        Path ordinary = log(folder, 1000, "");
        Path practice = log(folder, 2000, stamp);
        assertTrue(Files.readString(practice).contains("\"practice\":\"" + stamp + "\""), "the log says so, chained");
        assertTrue(!Files.readString(ordinary).contains("practice"), "an ordinary log's bytes are unchanged");

        assertNotNull(Comparison.outcome(ordinary));
        OverlayLogs.Refused scored = assertThrows(OverlayLogs.Refused.class, () -> Comparison.outcome(practice));
        assertTrue(scored.getMessage().contains("practice"), scored.getMessage());

        LogHeader.of(ordinary);
        assertThrows(OverlayLogs.Refused.class, () -> LogHeader.of(practice));

        // A folder holding one is not a direction baseline and not a direction candidate.
        Path mixed = folder.resolve("mixed");
        try {
            Files.createDirectories(mixed);
            Files.copy(practice, mixed.resolve(practice.getFileName()));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertThrows(OverlayLogs.Refused.class, () -> DirectionMetrics.read(mixed));
    }

    @Test
    @DisplayName("a header cannot be both a practice Run and a registered one")
    void no_registration() {
        String stamp = new Practice(PracticeBoss.GOO, KitProfiles.STANDARD).stamp(HeroClass.WARRIOR);
        HeadlessBoot.ensure();
        assertThrows(RuntimeException.class, () -> new RunLog.Header(RunLog.VERSION, HeadlessBoot.pinnedTag(), "abc1234",
                HeroClass.WARRIOR, 0, 1, SeedSet.code(1), 1, 150, Profile.VERSION,
                ObservationCodec.SCHEMA_VERSION, Codex.VERSION,
                new RunLog.Brain("random", "abc1234", ZERO), "reg-1", false, "m", "2026-09-26T00:00:00Z", "", -1, -1, stamp));
    }

    @Test
    @DisplayName("the practice runner plays development sets only and takes no Registration or baseline")
    void the_runner_refuses_what_it_must() {
        PracticeRuns.allow("standard");
        PracticeRuns.allow("smoke");
        for (String set : new String[] {"goo", "holdout", "bosses", "nosuch"}) {
            assertThrows(IllegalArgumentException.class, () -> PracticeRuns.allow(set), set);
        }
        assertThrows(IllegalArgumentException.class, () -> PracticeRuns.arguments(new String[] {"--registration", "x"}));
        assertThrows(IllegalArgumentException.class, () -> PracticeRuns.arguments(new String[] {"--baseline", "main"}));
        Map<String, String> ok = PracticeRuns.arguments(new String[] {"--boss", "tengu", "--n", "3"});
        assertEquals("tengu", ok.get("--boss"));
    }

    @Test
    @DisplayName("the Wilson interval is the textbook one")
    void wilson() {
        double[] w = PracticeRuns.wilson(2, 2);
        assertEquals(0.342, w[0], 0.001);
        assertEquals(1.0, w[1], 0.001);
        assertTrue(Double.isNaN(PracticeRuns.wilson(0, 0)[0]));
    }
}
