package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A human's turns replay (story 5.9's acceptance criterion): a session of three human turns, played
 * with the game's own input calls on the embedded Run where the frames are the headless driver's, is
 * replayed by the headless, executor-driven Replay with every Observation hash matching.
 */
class HumanTurnReplayTest {

    static final long SEED = 31_415_926L;
    static final long SALT = 0x5A17_5A17L;

    static RunLoop.Logging logging(Path folder) {
        return new RunLoop.Logging(folder, "0".repeat(40), new RunLog.Brain("human", "0".repeat(40), "0".repeat(64)),
                "", "test");
    }

    static Path only(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> logs = files.filter(path -> path.toString().endsWith(".jsonl")).toList();
            assertEquals(1, logs.size(), "one log in " + folder + ": " + logs);
            return logs.get(0);
        }
    }

    @Test
    @DisplayName("three human turns, recorded as the executor's Actions, replay with every Observation hash matching")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void three_turns_replay(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        Action stepped;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()), logging(played), 500);
            assertTrue(host.untilOpen(20_000), "the first wait opened");
            ScriptedHuman.pressSearch();
            assertTrue(host.untilOpen(20_000), "the second wait opened");
            ScriptedHuman.pressWait();
            assertTrue(host.untilOpen(20_000), "the third wait opened");
            Observation third = run.snapshot().observation();
            Action.Step step = ScriptedHuman.offered(third, Action.Step.class);
            assertNotNull(step, "a step is offered");
            stepped = step;
            ScriptedHuman.tapCell(step.cell());
            assertTrue(host.untilOpen(20_000), "the fourth wait opened");
        }
        Path log = only(played);
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok(), verified.why());
        RunLogReader.Log read = RunLogReader.of(log);
        List<RunLog.Wait> waits = read.waits();
        assertEquals(List.of(new Action.Search(), new Action.Wait(), stepped),
                waits.stream().map(RunLog.Wait::action).toList(), "the three turns, as the executor's Actions");
        assertTrue(waits.stream().allMatch(w -> RunLog.HUMAN.equals(w.actor()) && w.applied()), "the person's");
        assertTrue(read.records().stream().anyMatch(r -> r instanceof RunLog.Mode mode && mode.mode().equals("HUMAN")),
                "the Run says it is the person's");
        assertTrue(read.records().stream().noneMatch(RunLog.Unsupported.class::isInstance), "nothing unsupported");

        Replay.Waits replayed = Replay.waitsOf(log, Files.createDirectories(folder.resolve("replay")), "test");
        assertEquals(3, replayed.waits());
        assertEquals(3, replayed.verified(), "every Observation hash matched");
        assertEquals(0, replayed.unverifiableFrom());
    }
}
