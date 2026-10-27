package org.shatterfish.overlay;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a seek costs (story 8.5, FR-46). A replay seeks by restarting from the header and playing to the wait, so
 * the cost of a seek is the cost of that restart. This measures it at waits 100, 500 and 1000 of a sewers log
 * (the {@code standard}-set seed of {@link ReplaySessionTest}) in the embedded game, at the Fast frame the Overlay
 * plays a Run N or a Resume at, and prints the table the results page copies. It asserts the budget the story
 * wrote down, so a change that makes a seek slower than the owner agreed fails here.
 *
 * <p>A measurement, not a benchmark: the machine runs other jobs while this does, and the results page says so.
 * Each seek is taken {@link #REPEATS} times and the median is the number.
 */
@Timeout(value = 40, unit = TimeUnit.MINUTES)
class ReplaySeekCostTest {

    /** The seek budget, in milliseconds, for the farthest wait measured (the story's reasoning is in its file). */
    static final long BUDGET_MS = 5_000;
    static final int[] WAITS = {100, 500, 1000};
    static final int REPEATS = 3;

    @TempDir
    static Path shared;

    static Path log;

    @BeforeAll
    static void aLongLog() {
        log = org.shatterfish.harness.agent.HostBridge.rigLog(shared.resolve("rig"), ReplaySessionTest.SEED,
                com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass.WARRIOR, ReplaySessionTest.SALT,
                ReplaySessionTest.TURN_CAP);
    }

    @AfterAll
    static void forget() {
        log = null;
    }

    /** Milliseconds from asking for the log to being stopped at {@code wait}, the replay's Decision shown. */
    private static long seek(int wait) {
        long started = System.nanoTime();
        try (ReplaySessionTest.Session session = new ReplaySessionTest.Session(log, ReplaySessionTest.SALT)) {
            session.untilStopped();
            session.controls.pauseResume();
            session.controls.cycleSpeed();
            session.until(() -> session.run.waitIndex() >= wait);
            session.controls.pauseResume();
            session.untilStopped();
            assertNull(session.loaded.follower().halted(), "a seek that disagrees with the log is not a seek");
            assertTrue(session.run.waitIndex() >= wait, "landed at " + session.run.waitIndex());
            return (System.nanoTime() - started) / 1_000_000;
        }
    }

    @Test
    @DisplayName("a seek to wait 100, 500 and 1000 is measured, and the farthest is within the budget")
    void seek_cost() throws IOException {
        List<String> rows = new ArrayList<>();
        long farthest = 0;
        for (int wait : WAITS) {
            List<Long> times = new ArrayList<>();
            for (int i = 0; i < REPEATS; i++) {
                times.add(seek(wait));
            }
            Collections.sort(times);
            long median = times.get(REPEATS / 2);
            rows.add(wait + "\t" + median + "\t" + times);
            farthest = median;
        }
        String table = "wait\tmedian_ms\tall_ms\n" + String.join("\n", rows) + "\n";
        System.out.println("seek cost (ms), restart from the header and play to the wait:\n" + table);
        Path out = Path.of("build", "seek-cost.tsv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table);
        assertEquals(WAITS.length, rows.size());
        assertTrue(farthest <= BUDGET_MS, "a seek to wait " + WAITS[WAITS.length - 1] + " took " + farthest
                + " ms, over the " + BUDGET_MS + " ms budget; keyframes are then due (story 8.5)");
    }
}
