package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.HostBridge;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.PauseOn.Condition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feature is shown to change when the person is asked and not what the bot does (story 8.3, FR-45): a
 * seeded Warrior of the {@code standard} set plays with every Pause-on condition enabled and is resumed each
 * time it stops, and its Action list equals the same Run with none enabled. The person is the test, pressing
 * Resume as the Controls row does; the game is {@code EmbeddedHost} at the desktop's sixtieth of a second a
 * frame. Both Runs play to the turn cap, so both logs are whole, verify, and are compared on their waits.
 *
 * <p>The seed is the one {@code SewersRunSmokeTest} plays (entry of the {@code standard} set's Warriors under
 * salt 1014); never {@code goo} or {@code holdout}.
 */
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class PauseOnRunTest {

    private static final long SEED = 2713922911738L;
    private static final long SALT = 1014L;
    /** Long enough to leave the first floor, meet monsters and use items; short enough to be a test. */
    private static final int TURN_CAP = 1500;
    private static final long FRAMES = 50_000_000L;

    /** What a Run found: its log, how often the Overlay paused it, and for what. */
    record Played(Path log, List<String> reasons) {
    }

    private static Played play(Path folder, OverlaySettings settings) throws IOException {
        Path logs = Files.createDirectories(folder);
        List<String> reasons = new ArrayList<>();
        try (HostBridge host = new HostBridge(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attachBrain(logs, TURN_CAP);
            ControlState controls = new ControlState(new OverlayGame.RunSeam(run), Capabilities.NOW, settings,
                    BossFloors.read(root().resolve("codex").resolve(org.shatterfish.harness.boot.HeadlessBoot.pinnedTag())));
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
                if (controls.mode() == Mode.PAUSED) {
                    String reason = controls.pausedFor();
                    assertTrue(reason != null, "a Run in PAUSED here was paused by a condition");
                    reasons.add(reason);
                    assertTrue(controls.pauseResume(), "Resume");
                }
            }
        }
        try (Stream<Path> files = Files.list(logs)) {
            return new Played(files.filter(file -> file.toString().endsWith(".jsonl")).findFirst().orElseThrow(), reasons);
        }
    }

    /** The repository root: the nearest folder above the test's working directory that holds {@code vanilla.pin}. */
    private static Path root() {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.exists(at.resolve("vanilla.pin"))) {
                return at;
            }
        }
        throw new IllegalStateException("no repository root above " + Path.of("").toAbsolutePath());
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
    @DisplayName("a Run with every condition enabled and resumed each time plays the Action list of the Run with none enabled")
    void the_feature_asks_and_does_not_change_play(@TempDir Path folder) throws IOException {
        Played quiet = play(folder.resolve("quiet"), OverlaySettings.DEFAULTS);
        Played asked = play(folder.resolve("asked"), OverlaySettings.all());

        assertEquals(List.of(), quiet.reasons(), "nothing enabled, nothing asked");
        assertFalse(asked.reasons().isEmpty(), "the Run stopped to ask at least once");
        List<Action> expected = actions(quiet.log());
        List<Action> actual = actions(asked.log());
        assertTrue(expected.size() > 100, "the Run is a real stretch of play: " + expected.size() + " waits");
        assertEquals(expected, actual, "the same Actions, in the same order, whether or not the person was asked");

        List<RunLog.Mode> modes = RunLogReader.of(asked.log()).records().stream()
                .filter(RunLog.Mode.class::isInstance).map(RunLog.Mode.class::cast).toList();
        assertEquals(asked.reasons().size(), modes.size(), "every pause is a Mode record, and nothing else is");
        assertTrue(modes.stream().allMatch(mode -> mode.mode().equals("PAUSED")), modes.toString());
        assertTrue(RunLogReader.of(quiet.log()).records().stream().noneMatch(RunLog.Mode.class::isInstance),
                "a Run nobody paused writes no Mode record");
        System.out.println("pause-on: " + expected.size() + " waits, paused " + asked.reasons().size() + " times for "
                + asked.reasons().stream().distinct().sorted().toList());
    }
}
