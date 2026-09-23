package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The strategy log (story 4.4, FR-36, NFR-9): a Run log as plain text, one line per wait. */
class StrategyLogTest {

    private static final String ZERO = "0".repeat(64);

    /** A log with a Brain's wait, a wait that said nothing, and a refused human wait. */
    private static Path log(Path folder) throws IOException {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR,
                0, 1L, "AAA-AAA-AAB", 7L, 20_000, 3, 2, 8,
                new RunLog.Brain("shatterfish", "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        RunLog.Decision decision = new RunLog.Decision("act: nothing better applies",
                new RunLog.Choice(new Action.Step(5), 1_667, "uniform 1/6"),
                List.of(new RunLog.Choice(new Action.Wait(), 1_667, "uniform 1/6"),
                        new RunLog.Choice(new Action.Search(), 1_667, "uniform 1/6")),
                List.of("hp-low", "enemy-in-view"), "fallback");
        List<RunLog> records = List.of(header,
                new RunLog.Wait(1, 1_000, 1, 0, ZERO, Map.of("map", ZERO), new Action.Step(5), true, RunLog.BOT,
                        decision, ZERO, List.of(5), 3),
                new RunLog.Wait(2, 2_000, 1, 0, ZERO, Map.of("map", ZERO), new Action.Search(), true, RunLog.BOT,
                        null, "", List.of(), 1),
                new RunLog.Wait(3, 2_000, 2, 0, ZERO, Map.of("map", ZERO), new Action.Descend(), false,
                        RunLog.HUMAN, null, "", List.of(), 1),
                new RunLog.End(3, new RunLog.Outcome(false, false, 0, 2, 2_000, "DEATH", 0), true));
        StringBuilder text = new StringBuilder();
        String chain = "";
        for (RunLog record : records) {
            text.append(RunLogJson.line(chain, record)).append('\n');
            chain = RunLogJson.chain(chain, record);
        }
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("each wait is one line: the Policy, the goal, the choice with its score, the alternatives, the flags, the cells")
    void one_line_per_wait(@TempDir Path folder) throws IOException {
        Path written = StrategyLog.written(log(folder));

        assertTrue(written.getFileName().toString().endsWith(StrategyLog.SUFFIX), written.toString());
        List<String> lines = Files.readAllLines(written, StandardCharsets.UTF_8);
        assertEquals(6, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(0).startsWith("# v4.0.0-"), lines.get(0));
        assertEquals("# brain shatterfish, warrior, tag v4.0.0", lines.get(1));
        assertEquals("k=1 d=1 | fallback | act: nothing better applies | Step[cell=5] 0.1667 uniform 1/6"
                + " | alt Wait[] 0.1667 uniform 1/6 | alt Search[] 0.1667 uniform 1/6"
                + " | flags hp-low,enemy-in-view | cells 5", lines.get(2));
        assertEquals("k=2 d=1 | Search[]", lines.get(3), "a wait whose decider said nothing prints its Action alone");
        assertEquals("k=3 d=2 human | Descend[] | refused", lines.get(4));
        assertEquals("# end DEATH, depth 2", lines.get(5));
    }

    @Test
    @DisplayName("a score prints as a fraction of one, and only a .jsonl file is a Run log")
    void scores_and_names(@TempDir Path folder) {
        assertEquals("Wait[] 1.0000 decline: No", StrategyLog.choice(new RunLog.Choice(new Action.Wait(), 10_000, "decline: No")));
        assertEquals("Wait[] 0.0000", StrategyLog.choice(new RunLog.Choice(new Action.Wait(), 0, "")));
        assertThrows(IllegalArgumentException.class, () -> StrategyLog.written(folder.resolve("run.txt")));
    }
}
