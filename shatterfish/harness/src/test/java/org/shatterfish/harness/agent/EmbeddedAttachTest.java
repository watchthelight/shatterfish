package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;
import org.shatterfish.harness.driver.UiRole;
import org.shatterfish.harness.driver.WaitGate;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;
import org.shatterfish.harness.observer.GameLogListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded driver attaches at scene creation and re-attaches after every floor change, and the
 * Run's wait index, salt, Belief and log cross the boundary (story 5.1, FR-37, ADR-0015 "Scene
 * lifetime").
 *
 * <p>The hero takes the stairs himself: he is put beside the exit and the Brain steps onto it, which
 * the game turns into a transition and a request for the loading scene from the actor thread. The host
 * serves that the way the loading scene does, including its request for the play scene from inside its
 * own frame, which the Run sees before the play scene is served (story 5.1's review, F1).
 */
class EmbeddedAttachTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    /**
     * A Brain that takes the stairs whenever a Step onto the floor's regular exit is offered, and
     * otherwise searches; its Belief is the list of every depth it was shown, in order, so a Brain
     * rebuilt or a Belief reset at a floor change starts the list again and the test sees it.
     */
    static final class Remembering implements Deliberator {
        final List<Integer> depths = new ArrayList<>();
        final List<String> threads = new ArrayList<>();

        @Override
        public synchronized Action decide(Observation observation) {
            depths.add(observation.header().depth());
            threads.add(Thread.currentThread().getName());
            // Standing on the exit with an enemy in view, the click on it moved the hero there rather
            // than taking the stairs (…/actors/hero/Hero.java:1999-2006); the descent is then offered.
            if (observation.actions().actions().contains(new Action.Descend())) {
                return new Action.Descend();
            }
            for (TransitionView transition : observation.map().transitions()) {
                if (transition.kind() == TransitionKind.REGULAR_EXIT) {
                    Action step = new Action.Step(transition.cell());
                    if (observation.actions().actions().contains(step)) {
                        return step;
                    }
                }
            }
            return new Action.Search();
        }

        @Override
        public RunLog.Decision lastDecision() {
            return null;
        }

        @Override
        public synchronized Belief belief() {
            return new Belief(1, depths.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Plays the Run through two floor changes the hero makes himself: beside the exit at the first
     * wait of a floor, then a Step onto it, which the game turns into the transition, a request for the
     * loading scene from the actor thread, the loading scene's floor work and its request for the play
     * scene from inside its frame (EmbeddedHost), and the new play scene. Returns the Run standing at
     * the first wait of floor 3.
     */
    private static void downTwoFloors(EmbeddedHost host, EmbeddedRun run) {
        for (int floor = 1; floor <= 2; floor++) {
            assertEquals(floor, Dungeon.depth);
            EmbeddedHost.standBesideTheExit();
            long k = run.waitIndex();
            playUntilWaits(host, k + 1);
            // The Search for the wait the hero was moved at, then the Step the next wait offers.
            int next = floor + 1;
            playUntil(host, () -> Dungeon.depth == next && run.state() == EmbeddedRun.State.THINKING);
        }
        assertEquals(3, Dungeon.depth);
        assertEquals(2, host.loadingFrames, "two floor changes, each through the loading scene's frame");
    }

    @Test
    @DisplayName("attached at scene creation and re-attached after two floor changes, with k, the salt, the Belief and the log kept")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void attaches_across_two_floor_changes(@TempDir Path folder) {
        Remembering brain = new Remembering();
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(brain, new RunLoop.Logging(folder, "0".repeat(40),
                    new RunLog.Brain("remembering", "0".repeat(40), "0".repeat(64)), "", "test"), 5_000);

            assertEquals(1, run.attachments(), "attached to the play scene in front at the attach");
            assertEquals(host.driver.rngControl().salt(), run.salt());
            assertNotNull(WaitGate.live(), "the Run's gate is the one the executor's announcement reaches");

            playUntilWaits(host, 1);
            downTwoFloors(host, run);
            assertEquals(EmbeddedRun.State.THINKING, run.state(), "standing at the first wait of floor 3");
            assertEquals(3, run.attachments(), "re-attached at each new play scene, and at nothing else");

            // The salt is the Run's, not the floor's.
            assertEquals(SALT, run.salt());
            assertEquals(SALT, host.driver.rngControl().salt());
            assertSame(brain, run.brain());
            assertEquals(0, run.staleAnswers(), "no answer went stale: nothing moved while the Brain thought "
                    + "but the test's own planting, which changes nothing the gate watches");

            // Let the pending decision be served, so every wait the Brain saw is in the log.
            playUntil(host, () -> run.state() == EmbeddedRun.State.PLAYING);
            Path file = run.logFile();
            run.close();
            // The Run takes back what it registered when it closes, before the host's own teardown
            // clears everything anyway: a listener of a detached Run must not hear the next scene.
            assertNull(Hooks.logReplaced, "the Run took back the scene seam at its close");
            assertNull(Hooks.inputWait, "the Run took back the Input-wait notification at its close");
            assertNull(WaitGate.live(), "the Run's gate is no longer the executor's");

            RunLogVerifier.Verified verified = RunLogVerifier.of(file);
            assertTrue(verified.ok(), "the chain holds across both floors: " + verified.why());
            RunLogReader.Log read = RunLogReader.of(file);
            assertTrue(read.header().embedded(), "the log says the Overlay's driver played it");
            // The screen it played on (story 5.2): the interface size this process plays on, and no controller.
            assertEquals(com.shatteredpixel.shatteredpixeldungeon.SPDSettings.interfaceSize(), read.header().interfaceSize(),
                    "the log states the interface size the Run played on");
            assertEquals(0, read.header().controller(), "and that no controller was connected");
            List<RunLog.Wait> waits = read.waits();
            // One wait per index, from 1, across both floors: the index survives the scenes.
            for (int i = 0; i < waits.size(); i++) {
                assertEquals(i + 1, waits.get(i).k(), "the log's waits count on from 1: " + waits);
            }
            // Exactly one decision per logged wait, by the one Brain, all on its worker.
            assertEquals(waits.size(), brain.depths.size(), "every logged wait was decided once, and nothing else");
            assertEquals(run.waitIndex(), waits.size(), "every confirmed wait was served and logged");
            for (int i = 0; i < waits.size(); i++) {
                assertEquals(waits.get(i).depth(), brain.depths.get(i), "the Brain saw the floor the log says");
            }
            assertTrue(brain.threads.stream().allMatch("shatterfish-brain"::equals), brain.threads.toString());
            assertEquals(List.of(1, 2, 3), brain.depths.stream().distinct().toList(), "three floors, in order");
            // The Steps onto the exits are the Actions logged at the waits before each floor change.
            for (int i = 1; i < waits.size(); i++) {
                if (waits.get(i).depth() != waits.get(i - 1).depth()) {
                    Action took = waits.get(i - 1).action();
                    assertTrue(took instanceof Action.Step || took instanceof Action.Descend,
                            "the hero took the stairs himself: " + took);
                }
            }
            // Each wait's logged Belief is every depth seen up to it: never reset at a floor.
            for (int i = 0; i < waits.size(); i++) {
                String expected = new Belief(1, brain.depths.subList(0, i + 1).toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).hash();
                assertEquals(expected, waits.get(i).belief(), "the Belief at wait " + waits.get(i).k()
                        + " is the one Brain's, carried across the floors");
            }
        }
        assertNull(Hooks.inputWait, "nothing left registered");
        assertNull(Hooks.logReplaced, "nothing left registered");
        assertNull(WaitGate.live(), "no gate left live");
        assertNull(UiRole.owner(), "the UI role is free again");
    }

    @Test
    @DisplayName("the log listener is re-added on every floor, so the new floor's first lines are heard")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_log_is_heard_on_every_floor() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Remembering(), null, 5_000);
            playUntilWaits(host, 1);
            for (int floor = 1; floor <= 2; floor++) {
                EmbeddedHost.standBesideTheExit();
                int next = floor + 1;
                playUntil(host, () -> Dungeon.depth == next && run.state() == EmbeddedRun.State.THINKING);
                GLog.i("heard on floor " + Dungeon.depth);
                List<LogLine> lines = GameLogListener.INSTANCE.lines();
                assertEquals("heard on floor " + Dungeon.depth, lines.get(lines.size() - 1).text(),
                        "the Observer's listener is on the new floor's signal");
                assertEquals(2, GLog.update.numListeners(), "the new pane and the Observer's listener, once");
            }
        }
    }

    @Test
    @DisplayName("a play scene rebuilt on the same floor keeps the wait the hero already announced")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_rebuilt_scene_keeps_the_wait() {
        // The desktop game rebuilds its play scene when its window changes size, once at start-up
        // among others; the first launch of story 5.1 lost the hero's first wait to that rebuild and
        // then waited forever for a wait that had already come.
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(new Remembering(), null, 5_000);
            host.heldQueue = 1;
            for (int frame = 0; frame < 2_000 && !(run.hookNotifications() > 0 && Dungeon.hero.ready); frame++) {
                host.frame();
            }
            assertTrue(run.hookNotifications() > 0 && Dungeon.hero.ready, "the hero announced his first wait");
            assertEquals(0, run.waitIndex(), "which the held queue kept from being confirmed");

            int depth = Dungeon.depth;
            host.rebuildTheScene();
            assertEquals(2, run.attachments(), "re-attached to the rebuilt scene");
            assertEquals(depth, Dungeon.depth, "on the same floor");

            host.heldQueue = 0;
            for (int frame = 0; frame < 2_000 && run.waitIndex() == 0; frame++) {
                host.frame();
            }
            assertEquals(1, run.waitIndex(), "the wait announced before the rebuild is confirmed after it");
        }
    }

    /** Plays frames until {@code done} holds; the bound is on game frames ({@link EmbeddedHost#waitingOnTheBrain}). */
    static void playUntil(EmbeddedHost host, java.util.function.BooleanSupplier done) {
        for (int frame = 0; frame < 200_000; ) {
            if (done.getAsBoolean()) {
                return;
            }
            boolean waiting = host.waitingOnTheBrain();
            if (host.frame() == EmbeddedRun.State.ENDED) {
                throw new AssertionError("the Run ended: " + host.run.outcome());
            }
            if (waiting) {
                Thread.onSpinWait();
            } else {
                frame++;
            }
        }
        throw new AssertionError("the condition never held; the Run stands at wait " + host.run.waitIndex()
                + " on floor " + Dungeon.depth);
    }

    /** Plays frames until the Run has confirmed wait {@code k} and is thinking about it. */
    static void playUntilWaits(EmbeddedHost host, long k) {
        for (int frame = 0; frame < 200_000; ) {
            if (host.run.waitIndex() >= k && host.run.state() == EmbeddedRun.State.THINKING) {
                return;
            }
            boolean waiting = host.waitingOnTheBrain();
            EmbeddedRun.State state = host.frame();
            if (state == EmbeddedRun.State.ENDED) {
                throw new AssertionError("the Run ended before wait " + k + ": " + host.run.outcome());
            }
            if (waiting) {
                Thread.onSpinWait();
            } else {
                frame++;
            }
        }
        throw new AssertionError("no wait " + k + " within the frames; the Run stands at wait " + host.run.waitIndex());
    }
}
