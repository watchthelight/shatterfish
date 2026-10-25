package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking over and handing back mid-Run (story 5.8, FR-40, UX-DR9) on the embedded Run itself: a Brain's
 * Run changes hands at an Input wait. A takeover keeps the Decision the Brain was making as the shadow of
 * the wait the person then takes; a hand back lands the Run held with the Decision for the wait in front,
 * made from the Observation after the person's last turn; the log says who played which wait.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class TakeoverHandbackTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;
    private static final long FRAMES = 20_000;

    /** A Brain that always waits, and remembers every Observation it was handed, and the Decision it left each time. */
    static class Recorder implements Deliberator {
        final AtomicInteger calls = new AtomicInteger();
        final List<Object> hashes = Collections.synchronizedList(new ArrayList<>());
        /** Closed to hold the Brain mid-thought. */
        final CountDownLatch open = new CountDownLatch(1);

        Recorder() {
            open.countDown();
        }

        @Override
        public Action decide(Observation observation) {
            try {
                open.await();
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(stop);
            }
            hashes.add(observation.hash());
            calls.incrementAndGet();
            return new Action.Wait();
        }

        @Override
        public RunLog.Decision lastDecision() {
            return new RunLog.Decision("call " + calls.get(), new RunLog.Choice(new Action.Wait(), 10_000, "only option"),
                    List.of(), List.of(), "recorder");
        }

        @Override
        public Belief belief() {
            return null;
        }
    }

    /** A plain Decider: it says nothing of why, so a hand back has no Decision of its own to reuse. */
    static final class Plain implements Decider {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Action decide(Observation observation) {
            calls.incrementAndGet();
            return new Action.Wait();
        }
    }

    private static Path logOf(Path folder) throws IOException {
        return HumanTurnReplayTest.only(folder);
    }

    private static List<RunLog.Mode> modes(RunLogReader.Log read) {
        return read.records().stream().filter(RunLog.Mode.class::isInstance).map(RunLog.Mode.class::cast).toList();
    }

    private static List<Long> shadowWaits(RunLogReader.Log read) {
        return read.records().stream().filter(RunLog.Shadow.class::isInstance).map(RunLog.Shadow.class::cast)
                .map(RunLog.Shadow::k).toList();
    }

    @Test
    @DisplayName("take over at the wait the Brain decided, play two turns, hand back: the Run lands held with a Decision made after the person's last turn")
    void a_takeover_and_a_hand_back(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        Action stepped;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            EmbeddedAttachTest.playUntil(host, run::withheld);
            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1 && run.withheld());
            assertEquals(2, run.waitIndex(), "the Brain executed wait 1 and has decided wait 2, which waits for a press");
            assertEquals(2, brain.calls.get());
            assertFalse(run.human());
            assertFalse(run.humanInput(), "PAUSED: a person's input does not reach the hero");

            // Take over: it applies at the next Input wait, which is the one in front.
            run.takeOver("Human");
            assertTrue(run.switching());
            assertTrue(host.untilOpen(FRAMES), "the person's wait opened");
            assertTrue(run.human());
            assertFalse(run.switching());
            assertTrue(run.humanInput(), "the person's hand is on the game");
            assertEquals(2, run.waitIndex(), "the person took the wait the Brain had decided");
            assertEquals(2, brain.calls.get(), "and the Brain was not asked twice about it");
            assertTrue(run.snapshot().human() != null, "the snapshot says the person plays");
            assertEquals(0, run.credits(), "the Decision that was waiting for a press is not executed");

            // The turns here are Waits, not Searches: a search animation clears a tap made in the frames after
            // it (Hero.onOperateComplete), and how many frames pass is the driver's, which a replay cannot match.
            ScriptedHuman.pressWait();
            assertTrue(host.untilOpen(FRAMES), "wait 3 opened");
            Action.Step step = ScriptedHuman.offered(run.snapshot().observation(), Action.Step.class);
            assertTrue(step != null, "a step is offered");
            stepped = step;
            ScriptedHuman.tapCell(step.cell());
            assertTrue(host.untilOpen(FRAMES), "wait 4 opened");
            assertEquals(4, run.waitIndex());

            // Hand back: it applies at the open wait once the Brain has answered what it was shown.
            run.handBack("Human");
            assertTrue(run.switching());
            EmbeddedAttachTest.playUntil(host, () -> !run.human() && run.withheld());
            assertFalse(run.switching());
            assertFalse(run.humanInput(), "the hero is shut to a person's input again");
            assertFalse(run.inputOpen());
            assertEquals(4, run.waitIndex());
            assertEquals(4, brain.calls.get(), "the shadow of wait 4 is the Decision: not asked a second time");
            EmbeddedRun.Snapshot landed = run.snapshot();
            assertTrue(landed.awaiting(), "the card shows the Decision the next press executes");
            assertEquals("call 4", landed.decision().goal());
            assertEquals(brain.hashes.get(3), landed.observation().hash(),
                    "the Decision was made from the Observation of wait 4, after the person's last turn");
            assertNotEquals(brain.hashes.get(1), brain.hashes.get(3), "which is not the screen the person started from");
            assertNotEquals(brain.hashes.get(2), brain.hashes.get(3), "nor the one before their step moved the hero");
            assertEquals(0, run.credits());

            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() == 5 && run.withheld());
            assertEquals(5, brain.calls.get());
        }
        Path log = logOf(played);
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok(), verified.why());
        RunLogReader.Log read = RunLogReader.of(log);
        assertEquals(List.of(RunLog.BOT, RunLog.HUMAN, RunLog.HUMAN, RunLog.BOT),
                read.waits().stream().map(w -> w.actor()).toList(), "who played which wait");
        assertEquals(List.of(1L, 2L, 3L, 4L), read.waits().stream().map(RunLog.Wait::k).toList());
        assertEquals(new Action.Wait(), read.waits().get(1).action(), "the person's Wait, as the executor's Action");
        assertEquals(stepped, read.waits().get(2).action());
        assertEquals("call 4", read.waits().get(3).decision().goal(), "the Brain's wait 4 is the Decision made after the person's turns");
        assertEquals(List.of("HUMAN", "PAUSED"), modes(read).stream().map(RunLog.Mode::mode).toList());
        assertEquals(List.of(2L, 4L), modes(read).stream().map(RunLog.Mode::k).toList(), "the changes of hands, at their waits");
        assertEquals(List.of(2L, 3L, 4L), shadowWaits(read).stream().sorted().toList(), "the Brain shadowed every wait the person had");
        assertTrue(read.records().stream().noneMatch(RunLog.Unsupported.class::isInstance), "nothing unsupported");
        org.shatterfish.harness.log.Replay.Waits replayed = org.shatterfish.harness.log.Replay.waitsOf(log, "test");
        assertEquals(4, replayed.waits());
        assertEquals(4, replayed.verified(), "the Brain's waits and the person's replay together, every Observation hash matching");
        assertEquals(0, replayed.unverifiableFrom());
    }

    @Test
    @DisplayName("a takeover asked for while the Brain is still deciding applies when its answer lands, which becomes the shadow; nothing is asked twice")
    void a_takeover_waits_for_a_thinking_brain(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        CountDownLatch release = new CountDownLatch(1);
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder() {
                @Override
                public Action decide(Observation observation) {
                    try {
                        release.await();
                    } catch (InterruptedException stop) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(stop);
                    }
                    return super.decide(observation);
                }
            };
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            EmbeddedAttachTest.playUntilWaits(host, 1);
            run.takeOver("Next Step");
            for (int frame = 0; frame < 50; frame++) {
                host.frame();
                assertFalse(run.human(), "the Brain has not answered, so the wait is not the person's yet");
                assertTrue(run.switching());
            }
            release.countDown();
            // By the clock and not a frame count: the worker answers on its own thread, and under a loaded
            // machine a few thousand frames can pass before it is scheduled.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            while (!run.human() && System.nanoTime() < deadline) {
                host.frame();
                Thread.onSpinWait();
            }
            assertTrue(run.human(), "the answer landed and the person has the wait");
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(1, run.waitIndex());
            assertEquals(1, brain.calls.get());
        }
        RunLogReader.Log read = RunLogReader.of(logOf(played));
        assertEquals(List.of(), read.waits(), "the Brain executed nothing");
        assertEquals(List.of(1L), shadowWaits(read));
        assertEquals(List.of("HUMAN"), modes(read).stream().map(RunLog.Mode::mode).toList());
        assertEquals("Next Step", modes(read).get(0).speed());
    }

    @Test
    @DisplayName("a takeover asked for with no wait in front applies at the next Input wait, which the Brain only shadows")
    void a_takeover_with_no_wait_in_front(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            run.takeOver("Human");
            assertFalse(run.human(), "the hero is not at an Input wait yet");
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(1, run.waitIndex());
            assertTrue(run.human());
            ScriptedHuman.pressSearch();
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(2, run.waitIndex());
        }
        RunLogReader.Log read = RunLogReader.of(logOf(played));
        assertEquals(List.of(RunLog.HUMAN), read.waits().stream().map(w -> w.actor()).toList());
        assertEquals(1L, modes(read).get(0).k());
    }

    @Test
    @DisplayName("a hand back asked for while the hero is busy applies at the next wait the person has open")
    void a_hand_back_waits_for_an_open_wait(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            run.takeOver("Human");
            assertTrue(host.untilOpen(FRAMES));
            ScriptedHuman.pressSearch();
            run.handBack("Human");
            EmbeddedAttachTest.playUntil(host, () -> !run.human() && run.withheld());
            assertEquals(2, run.waitIndex(), "wait 1 was the person's turn; the hand back applied at wait 2");
            assertEquals("call 2", run.snapshot().decision().goal());
        }
        RunLogReader.Log read = RunLogReader.of(logOf(played));
        assertEquals(List.of(RunLog.HUMAN), read.waits().stream().map(w -> w.actor()).toList());
        assertEquals(List.of(1L, 2L), modes(read).stream().map(RunLog.Mode::k).toList());
        assertEquals(List.of("HUMAN", "PAUSED"), modes(read).stream().map(RunLog.Mode::mode).toList());
    }

    @Test
    @DisplayName("a Brain that gives no Decision of its own is asked afresh at the hand back, from the Observation then")
    void a_plain_brain_is_asked_afresh(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Plain brain = new Plain();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            EmbeddedAttachTest.playUntil(host, run::withheld);
            run.takeOver("Human");
            assertTrue(host.untilOpen(FRAMES));
            ScriptedHuman.pressWait();
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(2, run.waitIndex());
            run.handBack("Human");
            EmbeddedAttachTest.playUntil(host, () -> !run.human() && run.withheld());
            assertEquals(3, brain.calls.get(), "wait 1 decided, wait 2 shadowed, and wait 2 asked afresh for the Run to execute");
            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.waitIndex() == 3 && run.withheld());
        }
        RunLogReader.Log read = RunLogReader.of(logOf(played));
        assertEquals(List.of(RunLog.HUMAN, RunLog.BOT), read.waits().stream().map(w -> w.actor()).toList());
    }

    @Test
    @DisplayName("a takeover with a game controller connected marks the Run unverifiable from that wait, not from the start")
    void a_controller_marks_from_the_takeover(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            run.hold(true);
            EmbeddedAttachTest.playUntil(host, run::withheld);
            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1 && run.withheld());
            host.controller = true;
            run.takeOver("Human");
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(2, run.snapshot().human().unverifiableFrom(), "the bot's wait 1 still replays");
        }
        RunLogReader.Log read = RunLogReader.of(logOf(played));
        assertEquals(List.of(2L), read.records().stream().filter(RunLog.Unsupported.class::isInstance)
                .map(RunLog.Unsupported.class::cast).map(RunLog.Unsupported::k).toList());
    }

    @Test
    @DisplayName("the unverifiable mark outlives a hand back: the snapshot still says where the Replay stops, and the Run has a watchdog (issue #220)")
    void the_mark_outlives_a_hand_back(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            Recorder brain = new Recorder();
            EmbeddedRun run = host.attach(brain, HumanTurnReplayTest.logging(played), 500);
            assertTrue(run.watchdog() != null, "a Brain's Run is guarded");
            run.hold(true);
            EmbeddedAttachTest.playUntil(host, run::withheld);
            run.advance(1);
            EmbeddedAttachTest.playUntil(host, () -> run.snapshot().history().size() == 1 && run.withheld());
            assertNull(run.snapshot().unverifiable(), "every wait so far replays");
            host.controller = true;
            run.takeOver("Human");
            assertTrue(host.untilOpen(FRAMES));
            assertEquals(2, run.snapshot().unverifiable().from(), "while the person plays, the mark is on the snapshot too");
            run.handBack("Human");
            EmbeddedAttachTest.playUntil(host, () -> !run.human() && run.withheld());
            EmbeddedRun.Snapshot handed = run.snapshot();
            assertNull(handed.human(), "the Brain plays again");
            assertEquals(2, handed.unverifiable().from(), "and the Panel still says the Replay stops at wait 2");
            assertFalse(handed.unverifiable().why().isEmpty());
            assertTrue(run.watchdog() != null);
        }
        assertFalse(RunLogReader.of(logOf(played)).end().verifiable(), "the end record agrees");
    }

    @Test
    @DisplayName("a Run that began as a person's has a watchdog, which a hand back restarts (issue #220)")
    void a_human_run_is_watched_after_a_hand_back(@TempDir Path folder) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attachHuman(new Plain(), HumanTurnReplayTest.logging(played), 5_000);
            assertTrue(host.untilOpen(FRAMES));
            assertTrue(run.watchdog() != null, "created for every Run");
            long late = System.nanoTime() + (Watchdog.SECONDS + 5) * 1_000_000_000L;
            assertFalse(run.watchdog().check(late), "a person's long wait is no freeze");
        }
    }
}
