package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Brain error and No valid action rows of UX-DR7 (story 5.15, FR-38): where a host recovers from a Brain's
 * trouble, as the Overlay does, a Brain that throws gets a wait in its place with a card text naming the
 * exception class, a log line, and a pause asked for; a Brain that offers nothing gets one wait and the card
 * text, and the third in a row asks for the pause too. The game does not crash and the Run does not end. A host
 * that does not recover (the default) ends the Run as it always did.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class BrainTroubleTest {

    private static final long SEED = 27_182_818L;
    private static final long SALT = 0x5A17_5A17L;

    /** Answers the call numbers in {@code script} as the letters say: T throws, N offers nothing, anything else searches. */
    private static final class Scripted implements Decider {
        private final String script;
        final AtomicInteger calls = new AtomicInteger();

        Scripted(String script) {
            this.script = script;
        }

        @Override
        public Action decide(Observation observation) {
            int call = calls.getAndIncrement();
            char what = call < script.length() ? script.charAt(call) : 'S';
            if (what == 'T') {
                throw new IllegalStateException("the Brain is broken");
            }
            return what == 'N' ? null : new Action.Search();
        }
    }

    private static RunLoop.Logging logging(Path folder) {
        return new RunLoop.Logging(folder, "0".repeat(40),
                new RunLog.Brain("scripted", "0".repeat(40), "0".repeat(64)), "", "test");
    }

    /** Plays until wait {@code k} is served and records every distinct trouble the snapshots showed, in order. */
    private static List<EmbeddedRun.Trouble> play(EmbeddedHost host, long k) {
        List<EmbeddedRun.Trouble> seen = new ArrayList<>();
        EmbeddedAttachTest.playUntil(host, () -> {
            EmbeddedRun.Trouble now = host.run.snapshot().trouble();
            if (now != null && (seen.isEmpty() || seen.get(seen.size() - 1).seq() != now.seq())) {
                seen.add(now);
            }
            return host.run.waitIndex() >= k && host.run.state() == EmbeddedRun.State.THINKING;
        });
        return seen;
    }

    @Test
    @DisplayName("a Brain that throws gets a wait, a card text naming the class, a log line and a pause; the Run goes on")
    void a_throwing_brain_waits_and_pauses(@TempDir Path folder) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.recovers = true;
            Scripted brain = new Scripted("STS");
            EmbeddedRun run = host.attach(brain, logging(folder), 5_000);
            List<EmbeddedRun.Trouble> seen = play(host, 4);

            assertNull(run.outcome(), "the game does not crash and the Run does not end");
            assertEquals(1, seen.size());
            EmbeddedRun.Trouble trouble = seen.get(0);
            assertEquals(EmbeddedRun.TroubleKind.BRAIN_ERROR, trouble.kind());
            assertEquals("brain error: IllegalStateException", trouble.text());
            assertTrue(trouble.detail().contains("the Brain is broken"), trouble.detail());
            assertTrue(trouble.pauses(), "the Overlay is asked to enter PAUSED");
            assertNull(run.snapshot().trouble(), "and the next wait that the Brain decided clears the card");
            run.close();

            RunLogReader.Log read = RunLogReader.of(run.logFile());
            List<RunLog.Wait> waits = read.waits();
            RunLog.Wait failed = waits.get(1);
            assertInstanceOf(Action.Wait.class, failed.action(), "the Decision is wait");
            assertNotNull(failed.decision());
            assertEquals("brain error: IllegalStateException", failed.decision().policy());
            assertTrue(failed.decision().chosen().why().contains("the Brain is broken"));
            assertInstanceOf(Action.Search.class, waits.get(2).action(), "the Brain is asked again at the next wait");
            List<RunLog> records = read.records();
            int at = records.indexOf(failed);
            RunLog.Mode paused = assertInstanceOf(RunLog.Mode.class, records.get(at + 1), "the log says it paused");
            assertEquals("PAUSED", paused.mode());
            assertEquals(failed.k(), paused.k());
        }
    }

    @Test
    @DisplayName("a Brain that offers nothing gets one wait and the card text; the third in a row asks for the pause and logs it")
    void no_valid_action_pauses_on_the_third(@TempDir Path folder) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.recovers = true;
            EmbeddedRun run = host.attach(new Scripted("SNNNS"), logging(folder), 5_000);
            List<EmbeddedRun.Trouble> seen = play(host, 6);

            assertNull(run.outcome());
            assertEquals(3, seen.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(EmbeddedRun.TroubleKind.NO_VALID_ACTION, seen.get(i).kind());
                assertEquals(EmbeddedRun.NO_VALID_ACTION_TEXT, seen.get(i).text());
                assertEquals(i + 1, seen.get(i).inARow());
                assertEquals(i == 2, seen.get(i).pauses(), "only the third in a row pauses: " + i);
            }
            run.close();
            List<RunLog> records = RunLogReader.of(run.logFile()).records();
            long modes = records.stream().filter(r -> r instanceof RunLog.Mode).count();
            assertEquals(1, modes, "one pause, logged once");
            List<RunLog.Wait> waits = RunLogReader.of(run.logFile()).waits();
            for (int i = 1; i <= 3; i++) {
                assertInstanceOf(Action.Wait.class, waits.get(i).action(), "wait " + i);
            }
            assertTrue(RunLogVerifier.of(run.logFile()).ok(), "the log with the Overlay's own waits chains");
        }
    }

    @Test
    @DisplayName("an answer in between resets the count: two without an Action, one with, two again is no pause")
    void a_decision_resets_the_count(@TempDir Path folder) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.recovers = true;
            EmbeddedRun run = host.attach(new Scripted("NNSNNS"), logging(folder), 5_000);
            List<EmbeddedRun.Trouble> seen = play(host, 7);
            assertEquals(4, seen.size());
            assertFalse(seen.stream().anyMatch(EmbeddedRun.Trouble::pauses));
            assertEquals(List.of(1, 2, 1, 2), seen.stream().map(EmbeddedRun.Trouble::inARow).toList());
            run.close();
        }
    }

    @Test
    @DisplayName("a host that does not recover ends the Run as a Brain error or nothing offered, as before")
    void the_default_still_ends_the_run(@TempDir Path folder) {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Scripted("N"), logging(folder), 5_000);
            host.play(5_000);
            assertEquals(RunOutcome.Cause.NOTHING_OFFERED, run.outcome().cause());
        }
    }
}
