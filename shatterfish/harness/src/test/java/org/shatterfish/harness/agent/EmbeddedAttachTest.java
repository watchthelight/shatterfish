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
 * <p>The floors are changed by asking the game for the floor below at a wait, the way taking the
 * stairs does, and the host serves the change the way the loading scene serves it; the Run under test
 * sees only what it would see in the desktop game, a scene destroyed and a new one created.
 */
class EmbeddedAttachTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    /**
     * A Brain whose Belief is the list of every wait it was shown, in order: if the Brain were rebuilt
     * or its Belief reset at a floor change, the list would start again and the test would see it.
     */
    static final class Remembering implements Deliberator {
        final List<Integer> depths = new ArrayList<>();
        final List<String> threads = new ArrayList<>();

        @Override
        public synchronized Action decide(Observation observation) {
            depths.add(observation.header().depth());
            threads.add(Thread.currentThread().getName());
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

            // Three waits on the first floor, then the floor below, twice.
            long[] kBefore = new long[2];
            long[] kAfter = new long[2];
            playUntilWaits(host, 3);
            for (int change = 0; change < 2; change++) {
                int depth = Dungeon.depth;
                assertEquals(EmbeddedRun.State.THINKING, run.state(), "asked for at a wait, with the hero waiting");
                kBefore[change] = run.waitIndex();
                EmbeddedHost.askForTheFloorBelow();
                playUntilWaits(host, kBefore[change] + 1);
                assertEquals(depth + 1, Dungeon.depth, "the floor below was served");
                assertEquals(change + 2, run.attachments(), "re-attached when the new play scene was created");
                kAfter[change] = run.waitIndex();
                playUntilWaits(host, kAfter[change] + 2);
            }

            // The wait index counts on across both floors, one wait at a time.
            assertEquals(kBefore[0] + 1, kAfter[0], "the first wait on floor 2 is the next wait of the Run");
            assertEquals(kBefore[1] + 1, kAfter[1], "the first wait on floor 3 is the next wait of the Run");
            assertEquals(3, Dungeon.depth);

            // The salt is the Run's, not the floor's.
            assertEquals(SALT, run.salt());
            assertEquals(SALT, host.driver.rngControl().salt());

            // The Brain is the same object and saw every wait: one Belief across three floors.
            assertSame(brain, run.brain());
            long served = run.waitIndex() - (run.state() == EmbeddedRun.State.THINKING ? 1 : 0);
            assertTrue(brain.depths.size() >= served, "every served wait was decided by the one Brain: "
                    + brain.depths.size() + " decisions, " + served + " served");
            assertTrue(brain.depths.contains(1) && brain.depths.contains(2) && brain.depths.contains(3),
                    "the one Belief holds all three floors: " + brain.depths);

            // The log is one file with one chain across both boundaries.
            Path file = run.logFile();
            run.close();
            RunLogVerifier.Verified verified = RunLogVerifier.of(file);
            assertTrue(verified.ok(), "the chain holds across both floors: " + verified.why());
            RunLogReader.Log read = RunLogReader.of(file);
            List<RunLog.Wait> waits = read.waits();
            for (int i = 1; i < waits.size(); i++) {
                assertEquals(waits.get(i - 1).k() + 1, waits.get(i).k(), "the log's waits count on: " + waits);
            }
            assertTrue(waits.stream().anyMatch(wait -> wait.depth() == 3), "the log reached floor 3");
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
            playUntilWaits(host, 2);
            for (int change = 0; change < 2; change++) {
                long k = run.waitIndex();
                EmbeddedHost.askForTheFloorBelow();
                playUntilWaits(host, k + 1);
                GLog.i("heard on floor " + Dungeon.depth);
                List<LogLine> lines = GameLogListener.INSTANCE.lines();
                assertEquals("heard on floor " + Dungeon.depth, lines.get(lines.size() - 1).text(),
                        "the Observer's listener is on the new floor's signal");
                assertEquals(2, GLog.update.numListeners(), "the new pane and the Observer's listener, once");
            }
        }
    }

    /** Plays frames until the Run has confirmed wait {@code k} and is thinking about it. */
    static void playUntilWaits(EmbeddedHost host, long k) {
        for (int frame = 0; frame < 200_000; frame++) {
            if (host.run.waitIndex() >= k && host.run.state() == EmbeddedRun.State.THINKING) {
                return;
            }
            EmbeddedRun.State state = host.frame();
            if (state == EmbeddedRun.State.ENDED) {
                throw new AssertionError("the Run ended before wait " + k + ": " + host.run.outcome());
            }
        }
        throw new AssertionError("no wait " + k + " within the frames; the Run stands at wait " + host.run.waitIndex());
    }
}
