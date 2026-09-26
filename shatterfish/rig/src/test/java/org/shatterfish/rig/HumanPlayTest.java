package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The readable export of a human's Run (story 5.9): one line per turn, the person beside the Brain's shadow. */
class HumanPlayTest {

    private static final String H = "0".repeat(64);

    private static RunLog.Decision decision(Action chosen) {
        return new RunLog.Decision("explore: floor", new RunLog.Choice(chosen, 10_000, "frontier"), List.of(), List.of(),
                "explore");
    }

    private static RunLog.Wait wait(long k, long turn, Action action) {
        return new RunLog.Wait(k, turn, 1, 0, H, Map.of("map", H), action, true, RunLog.HUMAN, null, "", List.of(), 0);
    }

    private static String file(List<RunLog> records) {
        StringBuilder text = new StringBuilder();
        String chain = "";
        for (RunLog record : records) {
            text.append(RunLogJson.line(chain, record)).append('\n');
            chain = RunLogJson.chain(chain, record);
        }
        return text.toString();
    }

    private static List<RunLog> session() {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc", HeroClass.WARRIOR, 0, 2000L,
                SeedSet.code(2000L), 1L, 20_000, 3, 2, 8, new RunLog.Brain("human", "abc", H), "", false, "m", "s",
                RunLog.Header.EMBEDDED, 1, 0);
        return List.of(header,
                new RunLog.Mode(0, "HUMAN", "player"),
                new RunLog.Shadow(1, decision(new Action.Search())),
                new RunLog.Note(1, "checking | the walls"),
                wait(1, 0, new Action.Search()),
                wait(2, 2_000, new Action.Step(40)),
                new RunLog.Shadow(2, decision(new Action.Step(41)), true),
                wait(3, 3_000, new Action.MoveTo(90)),
                new RunLog.Unsupported(3, "a click on cell 90"),
                new RunLog.End(3, new RunLog.Outcome(false, false, 10, 1, 9_000, "DEATH", 0), false));
    }

    @Test
    @DisplayName("one line per turn: the person, the shadow and its score, agreement, late shadows, notes and where a Replay stops")
    void one_line_per_turn() {
        RunLogReader.Log log = RunLogReader.of(file(session()));
        assertTrue(HumanPlay.human(log));
        List<String> lines = HumanPlay.render(log).lines().toList();
        assertEquals("k=1 | turn 0 | you: search | Brain: search 1.0000 | agree yes | note: checking / the walls",
                lines.get(3));
        assertEquals("k=2 | turn 2 | you: step to 40 | Brain: step to 41 1.0000 (late) | agree no", lines.get(4));
        assertEquals("k=3 | turn 3 | you: walk to 90 | Brain: (no shadow) | agree - | replay stops here: a click on cell 90",
                lines.get(5));
        assertEquals("# agreed at 1 of 2 waits with a shadow; end DEATH, depth 1, not replayable past the first mark",
                lines.get(6));
    }

    @Test
    @DisplayName(":rig:strategy writes the export beside a human's log, and not beside a Brain's")
    void written_beside(@TempDir Path folder) throws IOException {
        Path log = folder.resolve("human.jsonl");
        Files.writeString(log, file(session()), StandardCharsets.UTF_8);
        StrategyLog.main(new String[] {log.toString()});
        assertTrue(Files.exists(folder.resolve("human" + HumanPlay.SUFFIX)));
        assertTrue(Files.readString(folder.resolve("human" + HumanPlay.SUFFIX)).contains("you: walk to 90"));

        List<RunLog> bot = List.of(session().get(0), new RunLog.Wait(1, 0, 1, 0, H, Map.of("map", H), new Action.Wait(),
                true, RunLog.BOT, null, "", List.of(), 0));
        assertFalse(HumanPlay.human(RunLogReader.of(file(bot))));
    }
}
