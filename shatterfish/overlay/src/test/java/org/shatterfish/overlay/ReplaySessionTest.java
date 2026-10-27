package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.HostBridge;
import org.shatterfish.harness.agent.Halting;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Replay session, in the embedded game and played by a script (story 8.5, FR-46): a log written by the Rig's
 * own loop on a seed of the {@code standard} set is loaded and played back at the Overlay's interface size, one
 * Input wait at a time, by the Controls row's own state ({@link ControlState#replaying}) over the game's frames
 * ({@code EmbeddedHost} at a sixtieth of a second). Nothing here opens a window.
 *
 * <p>The seed is the one {@code SewersRunSmokeTest} plays (an entry of the {@code standard} set's Warriors under
 * salt 1014); never {@code goo} or {@code holdout}. The cap is long enough to leave the first floor and to hold
 * more than a thousand waits, which the seek-cost measurement of the same story reads.
 */
@Timeout(value = 25, unit = TimeUnit.MINUTES)
class ReplaySessionTest {

    static final long SEED = 2713922911738L;
    static final long SALT = 1014L;
    static final int TURN_CAP = 2200;
    private static final long FRAMES = 300_000L;

    @TempDir
    static Path shared;

    static Path rigLog;
    /** A shorter Run, ended by its turn cap, for the tests that play a log to its end. */
    static final int SHORT_CAP = 500;
    static Path shortLog;

    @BeforeAll
    static void theRigPlaysTheRun() {
        rigLog = HostBridge.rigLog(shared.resolve("rig"), SEED, HeroClass.WARRIOR, SALT, TURN_CAP);
        shortLog = HostBridge.rigLog(shared.resolve("short"), SEED, HeroClass.WARRIOR, SALT, SHORT_CAP);
    }

    @AfterAll
    static void forget() {
        rigLog = null;
    }

    /** A replay session's game: the host, the Run, and the controls a person's presses go through. */
    static final class Session implements AutoCloseable {
        final ReplaySession loaded;
        final HostBridge host;
        final EmbeddedRun run;
        final ControlState controls;

        Session(Path log, long hostSalt) {
            loaded = ReplaySession.load(log);
            host = new HostBridge(loaded.header().seed(), HeroClass.valueOf(loaded.header().heroClass().name()), hostSalt)
                    .likeTheOverlay().interfaceSize(loaded.interfaceSize());
            run = host.attachReplay(loaded.follower(), loaded.header().cap());
            controls = ControlState.replaying(new OverlayGame.RunSeam(run), Capabilities.NOW, loaded.waits());
        }

        /** Frames until {@code done}, as the Overlay gives them: the game's, then the controls'. */
        void until(BooleanSupplier done) {
            long frames = 0;
            while (!done.getAsBoolean()) {
                boolean waiting = host.waitingOnTheBrain();
                if (host.frame() == EmbeddedRun.State.ENDED) {
                    return;
                }
                if (waiting) {
                    Thread.onSpinWait();
                    continue;
                }
                if (++frames > FRAMES) {
                    throw new AssertionError("the replay did not get there");
                }
                host.frameTime(controls.unbounded() ? ControlState.FAST_FRAME : 1f / 60f);
                controls.frame(1.0 / 60.0);
            }
        }

        /** Until the wait in front has its Decision shown and waits for a press (or the replay is over). */
        void untilStopped() {
            until(run::withheld);
        }

        int applied() {
            return (int) (run.waitIndex() - 1);
        }

        @Override
        public void close() {
            host.close();
        }
    }

    private static RunLog.End endOf(Path log) {
        return RunLogReader.of(log).end();
    }

    @Test
    @DisplayName("the whole log plays: every wait matches, and the replay ends in the log's recorded outcome")
    void plays_the_whole_log() {
        try (Session session = new Session(shortLog, SALT)) {
            assertTrue(session.loaded.waits() > 100, "a Run long enough to mean something: " + session.loaded.waits());
            assertTrue(session.controls.pauseResume(), "Resume");
            assertTrue(session.controls.cycleSpeed(), "Fast as it can");
            session.until(() -> false);

            assertEquals(EmbeddedRun.State.ENDED, session.run.state());
            assertNull(session.loaded.follower().halted(), "zero mismatches");
            assertEquals(session.loaded.waits(), session.loaded.follower().verified(),
                    "every wait of the log was seen as the log saw it");
            RunLog.End recorded = endOf(shortLog);
            assertEquals(recorded.outcome().cause(), session.run.outcome().cause().name(),
                    "the recorded outcome: " + session.run.outcome());
            assertEquals(recorded.outcome().turns(), session.run.outcome().turns() * 1000L, "the log counts turns in thousandths");
            assertEquals(recorded.outcome().depth(), session.run.outcome().depth());
            System.out.println("replay session: " + session.loaded.waits() + " waits, ended " + session.run.outcome().cause()
                    + " on floor " + session.run.outcome().depth() + " after " + session.run.outcome().turns() + " turns");
        }
    }

    @Test
    @DisplayName("Next Step applies exactly one recorded wait, Run N applies N, Resume plays, and each lands stopped")
    void steps_by_one_and_by_n() {
        try (Session session = new Session(rigLog, SALT)) {
            session.untilStopped();
            assertEquals(0, session.applied(), "nothing is applied until a press");
            assertEquals(Mode.REPLAY, session.controls.view(new ModeState(Mode.RUNNING, ModeState.SpeedMode.NORMAL, 0, 0, 1,
                    false)).mode());

            assertTrue(session.controls.nextStep());
            session.until(() -> session.applied() == 1);
            session.untilStopped();
            assertEquals(1, session.applied(), "one wait");
            assertEquals(2, session.run.waitIndex(), "stopped at the next");

            assertTrue(session.controls.countDown());
            assertTrue(session.controls.runN());
            session.until(() -> session.applied() >= 10);
            session.untilStopped();
            assertEquals(1 + 9, session.applied(), "N more");
            assertEquals(Mode.PAUSED, session.controls.mode(), "and stopped");
            assertFalse(session.controls.view(new ModeState(Mode.RUNNING, ModeState.SpeedMode.NORMAL, 0, 0, 1, false))
                    .replayPlaying());

            // Resume plays at the speed mode and Pause lands on a wait, with the Decision for it shown and nothing past.
            assertTrue(session.controls.pauseResume(), "Resume");
            assertTrue(session.controls.cycleSpeed(), "Fast");
            session.until(() -> session.applied() >= 60);
            assertTrue(session.controls.pauseResume(), "Pause");
            session.untilStopped();
            int stoppedAt = session.applied();
            assertTrue(stoppedAt >= 60, "it played on: " + stoppedAt);
            assertEquals(stoppedAt + 1L, session.run.waitIndex(), "stopped at the wait after the last applied");
            session.until(() -> false || session.applied() != stoppedAt || session.run.state() == EmbeddedRun.State.ENDED
                    || framesPassed(session, 600));
            assertEquals(stoppedAt, session.applied(), "and a stopped replay applies nothing more");
            assertNull(session.loaded.follower().halted());
        }
    }

    private static int framesSeen;

    private static boolean framesPassed(Session session, int count) {
        return ++framesSeen % count == 0;
    }

    @Test
    @DisplayName("the hero's game input is closed in REPLAY and Take over does nothing: the log is the only player")
    void nobody_plays() {
        try (Session session = new Session(rigLog, SALT)) {
            session.untilStopped();
            assertFalse(session.controls.takeOverHandBack());
            assertFalse(session.run.human());
            assertFalse(session.run.humanInput(), "a click on the dungeon is shut out, as in PAUSED");
            assertFalse(session.controls.enabled().contains(ControlEnablement.Control.TAKE_OVER));
        }
    }

    @Test
    @DisplayName("a wait the game does not show as the log did stops the replay at it, names the section, and applies nothing past")
    void a_changed_wait_stops_the_replay_and_names_the_section(@TempDir Path folder) throws IOException {
        int wait = 300;
        Path forged = forge(rigLog, folder.resolve("forged.jsonl"), wait);
        RunLog.Wait changed = RunLogReader.of(forged).waits().get(wait - 1);
        String section = changed.sections().keySet().iterator().next();

        try (Session session = new Session(forged, SALT)) {
            assertTrue(session.controls.pauseResume(), "Resume");
            assertTrue(session.controls.cycleSpeed());
            session.until(() -> false);

            assertEquals(EmbeddedRun.State.ENDED, session.run.state(), "the replay ended at the wait");
            Halting.Halt halt = session.loaded.follower().halted();
            assertNotNull(halt, "and said why");
            assertEquals(Halting.Kind.MISMATCH, halt.kind());
            assertEquals(changed.k(), halt.at());
            assertTrue(halt.detail().contains(section), halt.detail());
            assertTrue(halt.why().contains("wait " + changed.k()) && halt.why().contains(section), halt.why());
            assertEquals(wait - 1, session.applied(), "the waits before it were applied, and the wrong one was not");
            String over = ModeStripContent.text(session.controls.view(new ModeState(Mode.RUNNING,
                    ModeState.SpeedMode.NORMAL, 0, 0, 1, false, true, session.run.snapshot().ending())));
            assertTrue(over.startsWith("RUN OVER: replay stopped: the Observation differs at wait " + changed.k()), over);
            assertTrue(over.contains(section), over);
        }
    }

    @Test
    @DisplayName("a log with one byte flipped does not verify and is refused, with no game started")
    void a_flipped_byte_is_refused(@TempDir Path folder) throws IOException {
        String text = Files.readString(rigLog, StandardCharsets.UTF_8);
        int at = text.indexOf("\"obs\":\"");
        char was = text.charAt(at + 7);
        Path flipped = folder.resolve("flipped.jsonl");
        Files.writeString(flipped, text.substring(0, at + 7) + (was == 'a' ? 'b' : 'a') + text.substring(at + 8),
                StandardCharsets.UTF_8);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> ReplaySession.load(flipped));
        assertTrue(refused.getMessage().contains("does not verify"), refused.getMessage());
    }

    @Test
    @DisplayName("the session leaves no log of its own and no file in the profile's save slot")
    void leaves_nothing_behind() throws IOException {
        Path profile = HeadlessBoot.ensure().profile();
        Path slot = profile.resolve(GamesInProgress.gameFolder(GamesInProgress.curSlot));
        try (Session session = new Session(rigLog, SALT)) {
            session.untilStopped();
            // What starting the game writes is the game's own; the property is that playing the log adds nothing to it.
            Map<String, Long> before = slotContents(slot);
            for (int step = 1; step <= 5; step++) {
                assertTrue(session.controls.nextStep());
                int want = step;
                session.until(() -> session.applied() >= want);
                session.untilStopped();
            }
            assertEquals(5, session.applied());
            assertNull(session.run.logFile(), "a replay writes no Run log");
            assertFalse(session.run.resumable(), "and cannot be saved and quit into one");
            assertEquals(before, slotContents(slot), "and the slot holds what it held before the first press");
            assertFalse(Files.exists(profile.resolve("suspended")), "no suspended-save marker");
        }
    }

    /** The slot's files with their sizes and times, or an empty map. */
    private static Map<String, Long> slotContents(Path slot) throws IOException {
        Map<String, Long> files = new LinkedHashMap<>();
        if (Files.isDirectory(slot)) {
            try (var list = Files.list(slot)) {
                for (Path file : list.sorted().toList()) {
                    files.put(file.getFileName().toString(), Files.size(file) * 31 + Files.getLastModifiedTime(file).toMillis());
                }
            }
        }
        return files;
    }

    /** The log with the Observation of wait {@code k} changed in one section and the chain written again: a forged, valid log. */
    static Path forge(Path from, Path to, int k) throws IOException {
        List<RunLog> records = RunLogReader.of(from).records();
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            if (record instanceof RunLog.Wait wait && wait.k() == k) {
                Map<String, String> sections = new LinkedHashMap<>(wait.sections());
                String key = sections.keySet().iterator().next();
                sections.put(key, sections.get(key).startsWith("f") ? "e".repeat(64) : "f".repeat(64));
                record = new RunLog.Wait(wait.k(), wait.turn(), wait.depth(), wait.branch(), wait.obs().startsWith("f")
                        ? "e".repeat(64) : "f".repeat(64), sections, wait.action(), wait.applied(), wait.actor(),
                        wait.decision(), wait.belief(), wait.highlights(), wait.thinkMs());
            }
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Files.writeString(to, out.toString(), StandardCharsets.UTF_8);
        return to;
    }
}
