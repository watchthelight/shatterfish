package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #175: an Overlay Run closed while it plays ends with a {@code CLOSED} record, so a closed
 * window and a freeze no longer look the same in the log; and a Run that serves no wait for a while
 * has its threads written down, and is otherwise left alone.
 */
class EmbeddedCloseTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;
    private static final Decider SEARCHES = observation -> new Action.Search();

    private static RunLoop.Logging logging(Path folder) {
        return new RunLoop.Logging(folder, "0".repeat(40), new RunLog.Brain("searching", "0".repeat(40), "0".repeat(64)),
                "", "test");
    }

    @Test
    @DisplayName("a Run closed mid-play ends as CLOSED at the wait it was closed at, and the log reads and verifies")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void closed_mid_run(@TempDir Path folder) {
        Path file;
        long k;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, logging(folder), 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 3);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            file = run.logFile();
            k = run.waitIndex();
            run.close();
            assertNotNull(run.outcome(), "the closed Run has an outcome");
            assertEquals(RunOutcome.Cause.CLOSED, run.outcome().cause());
            run.close();
        }
        RunLogReader.Log read = RunLogReader.of(file);
        assertTrue(read.complete(), "the log ends with an end record");
        RunLog.End end = read.end();
        assertEquals("CLOSED", end.outcome().cause());
        assertEquals(k, end.k(), "at the wait it was closed at");
        assertEquals("closed at wait " + k, end.detail());
        assertFalse(end.verifiable(), "nothing can be reproduced from a Run a person closed");
        assertEquals(1, read.records().stream().filter(record -> record instanceof RunLog.End).count(), "one end record");
        assertTrue(RunLogVerifier.of(file).ok(), "the chain holds: " + RunLogVerifier.of(file).why());
        // The waits before the close are still checked, as a killed Run's are: the CLOSED ending claims
        // nothing about them.
        org.shatterfish.harness.log.Replay.Waits replayed = org.shatterfish.harness.log.Replay.waitsOf(file, "test");
        assertEquals(read.waits().size(), replayed.verified(), "every wait before the close replays");
        assertEquals(0, replayed.unverifiableFrom());
    }

    @Test
    @DisplayName("a Run that already ended is closed without a second end record")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void closed_after_its_end(@TempDir Path folder) {
        Path file;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(SEARCHES, logging(folder), 3);
            assertEquals(EmbeddedRun.State.ENDED, host.play(20_000));
            assertEquals(RunOutcome.Cause.TURN_CAP, run.outcome().cause());
            file = run.logFile();
            run.close();
            assertEquals(RunOutcome.Cause.TURN_CAP, run.outcome().cause(), "closing does not rewrite an ending");
        }
        RunLogReader.Log read = RunLogReader.of(file);
        assertEquals(List.of("TURN_CAP"), read.records().stream().filter(record -> record instanceof RunLog.End)
                .map(record -> ((RunLog.End) record).outcome().cause()).toList());
        assertTrue(RunLogVerifier.of(file).ok());
    }

    @Test
    @DisplayName("a HUMAN Run closed while the person plays ends as CLOSED too")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_human_run_closed(@TempDir Path folder) {
        Path file;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attachHuman(SEARCHES, logging(folder), 5_000);
            assertTrue(host.untilOpen(20_000), "a wait opened for the person");
            assertNull(run.watchdog(), "a person's pace is no freeze: a HUMAN Run has no watchdog");
            file = run.logFile();
            run.close();
            assertEquals(RunOutcome.Cause.CLOSED, run.outcome().cause());
        }
        RunLog.End end = RunLogReader.of(file).end();
        assertNotNull(end, "the log ends with an end record");
        assertEquals("CLOSED", end.outcome().cause());
        assertTrue(RunLogVerifier.of(file).ok());
    }

    @Test
    @DisplayName("no wait served for the limit: the threads are written to the side log once, and nothing is stopped")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_watchdog_writes_the_threads_down(@TempDir Path folder) throws Exception {
        CountDownLatch stuck = new CountDownLatch(1);
        Decider hangs = observation -> {
            try {
                stuck.await();
            } catch (InterruptedException closed) {
                Thread.currentThread().interrupt();
            }
            return new Action.Search();
        };
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.stepWhileThinking = true;
            EmbeddedRun run = host.attach(hangs, logging(folder), 5_000);
            EmbeddedAttachTest.playUntil(host, () -> run.state() == EmbeddedRun.State.THINKING);
            Watchdog watchdog = run.watchdog();
            assertNotNull(watchdog);
            long now = System.nanoTime();
            assertFalse(watchdog.check(now), "no report before the limit");
            long late = now + (Watchdog.SECONDS + 1) * 1_000_000_000L;
            assertTrue(watchdog.check(late), "past the limit with no wait served, a report");
            assertFalse(watchdog.check(late + 5_000_000_000L), "once per stall");
            assertEquals(1, watchdog.reports());

            Path side = Watchdog.sideLogOf(run.logFile());
            assertEquals(run.logFile().getFileName().toString().replace(".jsonl", Watchdog.SUFFIX),
                    side.getFileName().toString(), "beside the Run log, named after it");
            String report = Files.readString(side, StandardCharsets.UTF_8);
            assertTrue(report.contains("no Input wait served for " + (Watchdog.SECONDS + 1) + " s"), report);
            assertTrue(report.contains("render \"" + Thread.currentThread().getName() + "\" RUNNABLE"), report);
            assertTrue(report.contains("brain \"shatterfish-brain\" WAITING"), report);
            assertTrue(report.contains("CountDownLatch.await"), "with its stack: " + report);

            // It never kills anything: the Brain is still waiting, and the Run still plays.
            assertEquals(EmbeddedRun.State.THINKING, run.frame());
            assertNull(run.outcome());
            assertTrue(run.decidedOn().isAlive(), "the Brain's thread is alive");

            // A served wait starts the clock again; the next stall is reported again.
            stuck.countDown();
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() >= 2);
            long after = System.nanoTime() + (Watchdog.SECONDS + 1) * 1_000_000_000L;
            assertTrue(watchdog.check(after), "a new stall, a new report");

            // A Run that has ended, or been closed, is not watched.
            run.close();
            assertFalse(watchdog.check(after + 60_000_000_000L), "a closed Run is not watched");
        }
    }

    @Test
    @DisplayName("the watchdog with no side log prints to the console alone, and a Run no longer playing is not reported")
    void the_watchdog_on_its_own() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        boolean[] playing = {true};
        Watchdog watchdog = new Watchdog(null, console, Thread.currentThread(), () -> null, () -> 7, () -> playing[0], 2);
        long start = System.nanoTime();
        assertFalse(watchdog.check(start + 1_000_000_000L));
        playing[0] = false;
        assertFalse(watchdog.check(start + 3_000_000_000L), "an ended Run is not reported");
        playing[0] = true;
        assertTrue(watchdog.check(start + 3_000_000_000L));
        String out = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("last wait confirmed 7"), out);
        assertTrue(out.contains("render \"" + Thread.currentThread().getName() + "\""), out);
        assertNull(Watchdog.sideLogOf(null));
        assertEquals("run-1.watchdog.txt", Watchdog.sideLogOf(Path.of("dir", "run-1.jsonl")).getFileName().toString());

        // A served wait restarts the clock: a second after it is no stall, though the watchdog is older.
        Watchdog fresh = new Watchdog(null, console, Thread.currentThread(), () -> null, () -> 7, () -> true, 1);
        try {
            Thread.sleep(1_200);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        fresh.served();
        assertFalse(fresh.check(System.nanoTime()), "the clock started again at the served wait");
        watchdog.close();
        assertFalse(watchdog.check(start + 60_000_000_000L), "a closed watchdog looks at nothing");
    }
}
