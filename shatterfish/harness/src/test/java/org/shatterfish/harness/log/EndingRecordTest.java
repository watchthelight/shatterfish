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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ending record: its round trip, its absence, and what a real death leaves (E10 W1). */
class EndingRecordTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final String ZERO = "0".repeat(40);

    @Test
    @DisplayName("an ending written beside a log reads back as written, and is not named like a log")
    void round_trip(@TempDir Path folder) {
        Path log = folder.resolve("run-1.jsonl");
        EndingRecord.Ending ending = new EndingRecord.Ending("Skeleton", 7, 12, 40, List.of("Goo"));
        EndingRecord.write(log, ending);
        assertEquals(ending, EndingRecord.read(log));
        assertFalse(EndingRecord.beside(log).getFileName().toString().endsWith(".jsonl"));
        assertEquals("run-1.ending.json", EndingRecord.beside(log).getFileName().toString());
    }

    @Test
    @DisplayName("a log with no ending record, or a torn one, reads as not recorded and not as an error")
    void absent_and_torn_read_as_none(@TempDir Path folder) throws IOException {
        Path log = folder.resolve("old.jsonl");
        assertNull(EndingRecord.read(log), "an old log has no record");
        Files.writeString(EndingRecord.beside(log), "{\"killer\":", StandardCharsets.UTF_8);
        assertNull(EndingRecord.read(log), "a torn record is not an error either");
    }

    @Test
    @DisplayName("the bosses are named in the order of Statistics.bossScores")
    void the_boss_order() {
        assertEquals(List.of("Goo", "Tengu", "DM-300", "Dwarf King", "Yog-Dzewa"), EndingRecord.BOSSES);
    }

    @Test
    @DisplayName("a death leaves a record naming the killer, the floor and the health last seen")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_death_names_its_killer(@TempDir Path folder) {
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(SEED),
                RunLoop.TURN_CAP, new RunLoop.Logging(folder, ZERO, new RunLog.Brain("random", ZERO, "0".repeat(64)),
                        "", "test"));
        assertEquals(RunOutcome.Cause.DEATH, outcome.cause(), outcome.toString());
        Path log = folder.resolve(RunLog.fileName(RunLog.runId("v4.0.2", org.shatterfish.api.HeroClass.WARRIOR, 0,
                SeedSet.code(SEED), SALT, "random")));
        EndingRecord.Ending ending = EndingRecord.read(log);
        assertNotNull(ending, "the record is there without any switch");
        assertEquals("Hunger", ending.killer(), "this seed starves (as DebugRecordTest holds)");
        assertTrue(ending.depth() >= 1, ending.toString());
        assertTrue(ending.lastHp() >= 0 && ending.lastHp() <= ending.ht(), ending.toString());
        assertEquals(List.of(), ending.bossKills());
    }
}
