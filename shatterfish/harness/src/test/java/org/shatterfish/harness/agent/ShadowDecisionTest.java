package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shadow Decision (story 5.9, ADR-0013): at every wait of a HUMAN Run the Brain is shown the
 * Observation and nothing else, updates its Belief and decides; the Decision is written as a
 * {@code shadow} record and shown, and never executed. One that lands after its wait stopped being
 * the person's is written as skipped. The game never waits for it. A note is written at the wait
 * that is open.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ShadowDecisionTest {

    /** The real Brain behind a gate the test opens, counting the Observations it is shown. */
    static final class Held implements Deliberator {
        final BrainDecider brain = new BrainDecider(EmbeddedDeterminismTest.brain());
        final CountDownLatch open = new CountDownLatch(1);
        final List<Observation> shown = new ArrayList<>();
        final AtomicInteger decided = new AtomicInteger();

        @Override
        public Action decide(Observation observation) {
            try {
                open.await();
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(stop);
            }
            synchronized (shown) {
                shown.add(observation);
            }
            Action chosen = brain.decide(observation);
            decided.incrementAndGet();
            return chosen;
        }

        @Override
        public RunLog.Decision lastDecision() {
            return brain.lastDecision();
        }

        @Override
        public Belief belief() {
            return brain.belief();
        }

        @Override
        public String beliefHash() {
            return brain.beliefHash();
        }

        @Override
        public List<Integer> lastHighlights() {
            return brain.lastHighlights();
        }
    }

    @Test
    @DisplayName("a shadow in time is written for its wait, shown as current, and not executed")
    void in_time(@TempDir Path folder) throws IOException {
        RunLog.Decision shown;
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                    HumanTurnReplayTest.logging(folder), 2_000);
            host.stepWhileThinking = false;
            assertTrue(host.untilOpen(20_000));
            while (run.state() == EmbeddedRun.State.THINKING) {
                host.frame();
                Thread.onSpinWait();
            }
            EmbeddedRun.Snapshot snapshot = run.snapshot();
            assertTrue(snapshot.human().shadowCurrent(), "the shadow is for the wait in front");
            assertEquals(1, snapshot.human().shadowWait());
            shown = snapshot.decision();
            assertNotNull(shown);
            assertTrue(run.note("I would\nsearch here first"), "a note is written");
            ScriptedHuman.pressSearch();
            host.untilOpen(20_000);
            assertFalse(run.snapshot().human().shadowCurrent() && run.snapshot().human().shadowWait() == 1,
                    "once the person has acted, wait 1's shadow is no longer current");
            // The Decision log's source (story 5.4) holds the same records: the mode, the shadow with the
            // Observation its Action is read against, the person's wait, and the note.
            List<BoundedLog.Entry> history = run.snapshot().history();
            assertTrue(history.stream().anyMatch(e -> e.record() instanceof RunLog.Mode m && m.mode().equals("HUMAN")));
            assertTrue(history.stream().anyMatch(e -> e.record() instanceof RunLog.Shadow s && s.k() == 1
                    && e.context() != null));
            assertTrue(history.stream().anyMatch(e -> e.record() instanceof RunLog.Wait w && w.k() == 1
                    && RunLog.HUMAN.equals(w.actor()) && e.context() != null));
            assertTrue(history.stream().anyMatch(e -> e.record() instanceof RunLog.Note));
        }
        List<RunLog> records = RunLogReader.of(HumanTurnReplayTest.only(folder)).records();
        int shadow = indexOf(records, r -> r instanceof RunLog.Shadow s && s.k() == 1);
        int wait = indexOf(records, r -> r instanceof RunLog.Wait w && w.k() == 1);
        assertTrue(shadow >= 0 && shadow < wait, "the shadow came before the person's Action: " + records);
        RunLog.Shadow written = (RunLog.Shadow) records.get(shadow);
        assertFalse(written.skipped());
        assertEquals(shown, written.decision(), "the card showed what the log says");
        assertEquals(new Action.Search(), ((RunLog.Wait) records.get(wait)).action(), "the person's Action, not the Brain's");
        RunLog.Note note = (RunLog.Note) records.stream().filter(RunLog.Note.class::isInstance).findFirst().orElseThrow();
        assertEquals(1, note.k());
        assertEquals("I would search here first", note.text());
    }

    @Test
    @DisplayName("a shadow that lands after the person acted is written as skipped, and the game never waited for it")
    void too_late(@TempDir Path folder) throws IOException {
        Held held = new Held();
        try (EmbeddedHost host = new EmbeddedHost(HumanTurnReplayTest.SEED, HeroClass.WARRIOR, HumanTurnReplayTest.SALT)) {
            EmbeddedRun run = host.attachHuman(held, HumanTurnReplayTest.logging(folder), 2_000);
            assertTrue(host.untilOpen(20_000));
            ScriptedHuman.pressWait();
            assertTrue(host.untilOpen(20_000), "the next wait opened with the Brain still held");
            ScriptedHuman.pressSearch();
            long before = System.nanoTime();
            assertTrue(host.untilOpen(20_000), "and the one after");
            assertTrue(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(30), "no frame waited on the Brain");
            assertEquals(0, held.decided.get(), "the Brain has not answered yet");
            held.open.countDown();
            while (held.decided.get() < 3 || run.state() == EmbeddedRun.State.THINKING) {
                host.frame();
                Thread.onSpinWait();
            }
            assertEquals(3, held.shown.size(), "the Brain was shown every wait's Observation, in order");
        }
        List<RunLog> records = RunLogReader.of(HumanTurnReplayTest.only(folder)).records();
        List<RunLog.Shadow> shadows = records.stream().filter(RunLog.Shadow.class::isInstance)
                .map(RunLog.Shadow.class::cast).toList();
        assertEquals(List.of(1L, 2L, 3L), shadows.stream().map(RunLog.Shadow::k).toList());
        assertTrue(shadows.get(0).skipped() && shadows.get(1).skipped(), "waits 1 and 2 were already taken");
        assertFalse(shadows.get(2).skipped(), "wait 3 was still the person's");
        assertEquals(List.of(new Action.Wait(), new Action.Search()),
                records.stream().filter(RunLog.Wait.class::isInstance).map(r -> ((RunLog.Wait) r).action()).toList(),
                "only the person's Actions were taken");
    }

    private static int indexOf(List<RunLog> records, java.util.function.Predicate<RunLog> test) {
        for (int i = 0; i < records.size(); i++) {
            if (test.test(records.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
