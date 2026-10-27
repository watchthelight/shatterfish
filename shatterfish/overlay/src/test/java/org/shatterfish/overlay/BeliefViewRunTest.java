package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.BeliefView;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.BoundedLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.HostBridge;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;
import org.shatterfish.overlay.ControlEnablement.Capabilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Beliefs view over a real Run (story 8.6, FR-46): a seeded Warrior of the {@code standard} set plays on
 * {@code EmbeddedHost}, and the view the Panel is handed at each wait carries the hash of the Belief the Run log
 * wrote at that wait, so the view over a log checks itself against the log. The view is read, never written, so
 * the Run's Action list is the same with the view taken at every wait as with none taken.
 *
 * <p>The seed is the one {@code SewersRunSmokeTest} plays (the {@code standard} set's Warriors under salt 1014);
 * never {@code goo} or {@code holdout}.
 */
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class BeliefViewRunTest {

    private static final long SEED = 2713922911738L;
    private static final long SALT = 1014L;
    private static final int TURN_CAP = 600;
    private static final long FRAMES = 50_000_000L;

    /** What a Run left: its log, and the view the snapshot carried at each wait, by wait index. */
    record Played(Path log, Map<Long, BeliefView> views, long mismatches) {
    }

    private static Played play(Path folder, boolean take) throws IOException {
        Path logs = Files.createDirectories(folder);
        Map<Long, BeliefView> views = new LinkedHashMap<>();
        long mismatches = 0;
        try (HostBridge host = new HostBridge(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attachBrain(logs, TURN_CAP);
            ControlState controls = new ControlState(new OverlayGame.RunSeam(run), Capabilities.NOW);
            assertTrue(controls.pauseResume(), "Resume");
            assertTrue(controls.cycleSpeed(), "Fast as it can");
            long frames = 0;
            while (true) {
                boolean waiting = host.waitingOnTheBrain();
                if (host.frame() == EmbeddedRun.State.ENDED) {
                    break;
                }
                if (waiting) {
                    Thread.onSpinWait();
                    continue;
                }
                if (++frames > FRAMES) {
                    throw new AssertionError("the Run did not end");
                }
                controls.frame(1.0 / 60.0);
                if (!take) {
                    continue;
                }
                EmbeddedRun.Snapshot snapshot = run.snapshot();
                BeliefView view = snapshot.beliefView();
                RunLog.Wait last = lastWait(snapshot.history());
                if (view != null && last != null && !snapshot.awaiting()) {
                    // The view in the snapshot is the one for the wait the last record is for.
                    if (!view.hash().equals(last.belief())) {
                        mismatches++;
                    } else {
                        views.putIfAbsent(last.k(), view);
                    }
                }
            }
        }
        try (Stream<Path> files = Files.list(logs)) {
            return new Played(files.filter(file -> file.toString().endsWith(".jsonl")).findFirst().orElseThrow(), views,
                    mismatches);
        }
    }

    private static RunLog.Wait lastWait(List<BoundedLog.Entry> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).record() instanceof RunLog.Wait wait) {
                return wait;
            }
        }
        return null;
    }

    private static List<Action> actions(Path log) {
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok() && verified.complete(), verified.why());
        List<Action> actions = new ArrayList<>();
        for (RunLog.Wait wait : RunLogReader.of(log).waits()) {
            actions.add(wait.action());
        }
        return actions;
    }

    @Test
    @DisplayName("the view at each wait of a real Run carries the Belief hash the Run log wrote at that wait, and taking it changes no Action")
    void the_view_checks_itself_against_the_log(@TempDir Path folder) throws IOException {
        Played taken = play(folder.resolve("taken"), true);
        Played untouched = play(folder.resolve("untouched"), false);

        assertEquals(0, taken.mismatches(), "a view whose hash is not its wait's Belief hash");
        assertTrue(taken.views().size() >= 3, "views were taken at three waits or more: " + taken.views().size());
        Map<Long, String> logged = new LinkedHashMap<>();
        for (RunLog.Wait wait : RunLogReader.of(taken.log()).waits()) {
            logged.put(wait.k(), wait.belief());
        }
        int checked = 0;
        for (Map.Entry<Long, BeliefView> entry : taken.views().entrySet()) {
            String hash = logged.get(entry.getKey());
            assertNotNull(hash, "the log has wait " + entry.getKey());
            assertFalse(hash.isEmpty(), "a Brain's wait records a Belief hash");
            assertEquals(hash, entry.getValue().hash(), "wait " + entry.getKey());
            checked++;
        }
        assertTrue(checked >= 3);
        assertTrue(taken.views().values().stream().map(BeliefView::hash).distinct().count() >= 3,
                "the Belief changes as the Run goes, so the views are not one view");

        assertEquals(actions(untouched.log()), actions(taken.log()),
                "the same Actions, in the same order, whether or not the view was taken");
        System.out.println("belief view: " + taken.views().size() + " views checked against the log of "
                + logged.size() + " waits");
    }
}
