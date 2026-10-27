package org.shatterfish.harness.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.Halting;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The follower a Replay and a replay session share (story 8.5). Waits that match and waits that differ are played
 * against a real game by {@code ReplayRoundTripTest}, {@code ReplayRefusalTest} (the strict follower, which
 * throws) and the Overlay's {@code ReplaySessionTest} (the stopping one, which halts); this one is the follower
 * with nothing to compare: a log that ends, and the rewind that a stale answer asks for.
 */
class LogFollowerTest {

    private static final long SEED = 0xC0FFEEL;
    private static final String ZERO = "0".repeat(64);

    private static RunLogReader.Log endedAtOnce(Path folder) throws IOException {
        HeadlessBoot.ensure();
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, HeadlessBoot.pinnedTag(), "abc1234",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SEED, SeedSet.code(SEED), 1L, 100, Profile.VERSION,
                ObservationCodec.SCHEMA_VERSION, 8, new RunLog.Brain("random", "def5678", ZERO), "", false,
                "a laptop", "2026-09-22T12:00:00Z");
        RunLog.End end = new RunLog.End(1, new RunLog.Outcome(false, false, 0, 1, 0, "TURN_CAP", 0), true, "");
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : List.of(header, end)) {
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Path file = folder.resolve("ended.jsonl");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return RunLogReader.of(file);
    }

    @Test
    @DisplayName("past the last wait a stopping follower waits and says the log ended; a strict one ends the Run")
    void the_end_of_the_log(@TempDir Path folder) throws IOException {
        RunLogReader.Log log = endedAtOnce(folder);
        LogFollower stopping = LogFollower.stopping(log);
        assertEquals(0, stopping.total());
        assertNull(stopping.halted());
        assertTrue(stopping.decide(null) instanceof Action.Wait, "nothing is applied past the end");
        Halting.Halt halt = stopping.halted();
        assertEquals(Halting.Kind.END, halt.kind());
        assertEquals(1, halt.at());
        assertTrue(halt.detail().contains("0 waits were applied and matched"), halt.detail());

        assertNull(LogFollower.strict(log).decide(null), "the strict follower returns nothing, which ends the loop");
    }

    @Test
    @DisplayName("a halt is kept until it is rewound, and a halted follower applies nothing more")
    void rewinding(@TempDir Path folder) throws IOException {
        LogFollower follower = LogFollower.stopping(endedAtOnce(folder));
        Object mark = follower.mark();
        follower.decide(null);
        assertEquals(Halting.Kind.END, follower.halted().kind());
        assertTrue(follower.decide(null) instanceof Action.Wait, "still waiting");
        assertEquals(Halting.Kind.END, follower.halted().kind(), "the first halt stands");
        follower.rewind(mark);
        assertNull(follower.halted(), "a stale answer's rewind takes its halt back");
    }
}
