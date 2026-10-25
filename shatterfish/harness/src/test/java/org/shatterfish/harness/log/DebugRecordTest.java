package org.shatterfish.harness.log;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the debug record holds for a short Run that ends in a death (issue #219). */
class DebugRecordTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final String ZERO = "0".repeat(40);

    @org.junit.jupiter.api.BeforeEach
    void on() {
        System.setProperty(DebugRecord.PROPERTY, "true");
    }

    @org.junit.jupiter.api.AfterEach
    void off() {
        System.clearProperty(DebugRecord.PROPERTY);
    }

    @Test
    @DisplayName("the record is off by default: a Run writes no sidecar")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void off_by_default_writes_nothing(@TempDir Path folder) throws IOException {
        System.clearProperty(DebugRecord.PROPERTY);
        org.junit.jupiter.api.Assumptions.assumeFalse(DebugRecord.enabled(), "the environment turns it on");
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(SEED), 150,
                new RunLoop.Logging(folder, ZERO, new RunLog.Brain("random", ZERO, "0".repeat(64)), "", "test"));
        try (var listed = Files.list(folder)) {
            assertEquals(List.of(), listed.filter(p -> p.getFileName().toString().endsWith(DebugRecord.SUFFIX)).toList());
        }
    }

    @Test
    @DisplayName("the debug file sits beside a Run log, is not a log, and says who killed the hero")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_death_leaves_a_debug_record(@TempDir Path folder) throws IOException {
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(SEED),
                RunLoop.TURN_CAP, new RunLoop.Logging(folder, ZERO, new RunLog.Brain("random", ZERO, "0".repeat(64)),
                        "", "test"));
        assertEquals(RunOutcome.Cause.DEATH, outcome.cause(), outcome.toString());

        Path log = folder.resolve(RunLog.fileName(RunLog.runId("v4.0.2", org.shatterfish.api.HeroClass.WARRIOR, 0,
                SeedSet.code(SEED), SALT, "random")));
        Path debug = DebugRecord.beside(log);
        assertTrue(Files.isRegularFile(log), "the Run log is written as ever");
        assertTrue(Files.isRegularFile(debug), "and the debug record beside it: " + debug);
        assertFalse(debug.getFileName().toString().endsWith(".jsonl"),
                "a folder of Run logs lists only logs, so the debug record is not named like one");
        try (var listed = Files.list(folder)) {
            assertEquals(1, listed.filter(p -> p.getFileName().toString().endsWith(".jsonl")).count());
        }

        List<String> lines = Files.readAllLines(debug, StandardCharsets.UTF_8);
        assertTrue(lines.size() >= 2, "a wait or more, and the end");
        String waits = String.join("\n", lines.subList(0, lines.size() - 1));
        assertTrue(waits.contains("\"t\":\"wait\""), "wait lines");
        for (String key : List.of("\"hp\":", "\"ht\":", "\"hero_water\":", "\"hero_buffs\":", "\"enemies\":",
                "\"log\":", "\"potions\":", "\"hunger\":", "\"depth\":")) {
            assertTrue(waits.contains(key), "a wait line holds " + key);
        }
        String end = lines.get(lines.size() - 1);
        assertTrue(end.contains("\"t\":\"end\"") && end.contains("\"cause\":\"DEATH\""), end);
        assertTrue(end.contains("\"depth\":" + outcome.depth()), end);
        assertFalse(end.contains("\"killer\":\"unknown\""), "the game names what killed the hero: " + end);
        assertFalse(end.contains("\"killer\":\"\""), end);
        assertTrue(end.contains("\"killer\":\"Hunger\""), "this seed starves: " + end);
        assertTrue(end.contains("\"last_hp\":") && end.contains("\"hero_buffs\":"), end);
    }

    @Test
    @DisplayName("the new log lines of a wait are those the last wait did not show")
    void fresh_lines_are_the_ones_after_the_overlap() {
        assertEquals(List.of("c", "d"), DebugRecord.fresh(List.of("a", "b"), List.of("a", "b", "c", "d")));
        assertEquals(List.of("e"), DebugRecord.fresh(List.of("a", "b", "c"), List.of("b", "c", "e")));
        assertEquals(List.of("x", "y"), DebugRecord.fresh(List.of(), List.of("x", "y")));
        assertEquals(List.of(), DebugRecord.fresh(List.of("a"), List.of("a")));
        assertNotEquals(List.of(), DebugRecord.fresh(List.of("a"), List.of("z")));
    }
}
