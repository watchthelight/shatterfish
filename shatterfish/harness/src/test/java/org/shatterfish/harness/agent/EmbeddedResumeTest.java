package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.driver.RunLogResume;
import org.shatterfish.harness.driver.SuspendedSave;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Save and quit, resume, and the Run's ending (story 5.14, ADR-0013): the boundary the log ends at, the
 * Run that continues it, the seal a save opened elsewhere breaks, and the cause the game's own record gives.
 */
class EmbeddedResumeTest {

    private static final long SEED = 31_415_926L;
    private static final long SALT = 0x5A17_5A17L;

    /**
     * A Decider with no memory: the first Step offered, else a Search, and an offered confirm for a window.
     * What it does is a function of the screen alone, so a Run that crosses a save decides as one that
     * does not.
     */
    private static final Decider WALKER = observation -> {
        List<Action> offered = observation.actions().actions();
        if (!observation.prompt().kind().name().equals("NONE") && !offered.isEmpty()) {
            return offered.get(0);
        }
        for (Action action : offered) {
            if (action instanceof Action.Step) {
                return action;
            }
        }
        return new Action.Search();
    };

    @AfterEach
    void theLoadingSceneModeBack() {
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
    }

    private static RunLoop.Logging logging(Path folder) {
        return new RunLoop.Logging(folder, "0".repeat(40),
                new RunLog.Brain("walker", "0".repeat(40), "0".repeat(64)), "", "test");
    }

    private static List<RunLog.Wait> waits(Path log) {
        return RunLogReader.of(log).waits();
    }

    /** Plays to wait {@code k}, saves the way the game does at a wait, and saves and quits; returns the boundary. */
    private static RunLog.Boundary saveAndQuit(EmbeddedHost host, long k) throws IOException {
        EmbeddedAttachTest.playUntilWaits(host, k);
        Dungeon.saveAll();
        return host.run.suspend();
    }

    @Test
    @DisplayName("save and quit writes a boundary with the wait, the salt and the chain, and no end record")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void suspend_writes_the_boundary(@TempDir Path folder) throws IOException {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder), 5_000);
            RunLog.Boundary boundary = saveAndQuit(host, 4);
            assertNotNull(boundary);
            assertEquals(SALT, boundary.salt());
            assertEquals(RunOutcome.Cause.SUSPENDED, run.outcome().cause());
            assertEquals(EmbeddedRun.State.ENDED, run.state());
            run.close();

            RunLogReader.Log read = RunLogReader.of(run.logFile());
            List<RunLog> records = read.records();
            assertEquals(boundary, records.get(records.size() - 1), "the log ends at the boundary");
            assertTrue(records.stream().noneMatch(record -> record instanceof RunLog.End), "no end record");
            assertEquals(boundary.k(), waits(run.logFile()).get(waits(run.logFile()).size() - 1).k(),
                    "the boundary names the last wait the log holds");
            RunLogVerifier.Verified verified = RunLogVerifier.of(run.logFile());
            assertTrue(verified.ok() && verified.complete(), String.valueOf(verified.why()));
            RunLogResume.Suspended suspended = RunLogResume.read(run.logFile());
            assertEquals(boundary, suspended.boundary());
        }
    }

    @Test
    @DisplayName("a resumed Run continues the log: one chain, every wait once, no gap, and the same turns as a Run never saved")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void a_resume_continues_the_log_and_the_game(@TempDir Path folder) throws IOException {
        Path log;
        long cut;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder.resolve("saved")), 5_000);
            RunLog.Boundary boundary = saveAndQuit(host, 6);
            cut = boundary.k();
            log = run.logFile();
            int slot = GamesInProgress.curSlot;
            run.close();

            host.driver.resumeFromSave(slot, cut);
            EmbeddedRun resumed = host.attachResumed(WALKER, log, 5_000);
            EmbeddedAttachTest.playUntilWaits(host, cut + 1);
            EmbeddedAttachTest.playUntilWaits(host, cut + 6);
            assertNull(resumed.outcome(), "the resumed Run plays on");
            resumed.close();
        }
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok() && verified.complete(), String.valueOf(verified.why()));
        List<RunLog> records = RunLogReader.of(log).records();
        assertEquals(1, records.stream().filter(record -> record instanceof RunLog.Header).count(), "one header");
        assertEquals(1, records.stream().filter(record -> record instanceof RunLog.Boundary).count(), "one boundary");
        RunLog last = records.get(records.size() - 1);
        assertTrue(last instanceof RunLog.End end && !end.verifiable(), "a resumed Run's end says a Replay stops: " + last);

        List<RunLog.Wait> held = waits(log);
        for (int i = 0; i < held.size(); i++) {
            assertEquals(held.get(i).k(), i + 1L, "every wait once, in order");
        }
        assertTrue(held.size() >= cut + 6 - 1, "waits after the resume: " + held.size());

        // The same Run never saved: the hero is where he was, the turns are the same.
        List<long[]> straight = new ArrayList<>();
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder.resolve("straight")), 5_000);
            EmbeddedAttachTest.playUntilWaits(host, cut + 6);
            run.close();
            for (RunLog.Wait wait : waits(run.logFile())) {
                straight.add(new long[] {wait.k(), wait.turn(), wait.depth()});
            }
        }
        int compared = 0;
        for (int i = 0; i < Math.min(held.size(), straight.size()); i++) {
            RunLog.Wait wait = held.get(i);
            assertEquals(straight.get(i)[1], wait.turn(), "the turn at wait " + wait.k());
            assertEquals(straight.get(i)[2], wait.depth(), "the floor at wait " + wait.k());
            compared++;
        }
        assertTrue(compared >= cut + 5, "compared " + compared + " waits across the save");
    }

    @Test
    @DisplayName("a log that ended, or was tampered with, is not continued")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_log_that_is_not_suspended_is_refused(@TempDir Path folder) throws IOException {
        Path ended;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder.resolve("ended")), 5_000);
            EmbeddedAttachTest.playUntilWaits(host, 2);
            run.close();
            ended = run.logFile();
        }
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> RunLogResume.read(ended));
        assertTrue(refused.getMessage().contains("boundary"), refused.getMessage());

        Path suspended;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder.resolve("suspended")), 5_000);
            saveAndQuit(host, 2);
            run.close();
            suspended = run.logFile();
        }
        assertNotNull(RunLogResume.read(suspended));
        String text = Files.readString(suspended);
        Files.writeString(suspended, text.replaceFirst("\"cap\":5000", "\"cap\":5001"));
        assertThrows(IllegalStateException.class, () -> RunLogResume.read(suspended));
    }

    @Test
    @DisplayName("the seal breaks when the save is touched: a save opened without the launcher is not an Overlay Run")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_touched_save_breaks_the_seal(@TempDir Path folder) throws IOException {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, logging(folder.resolve("log")), 5_000);
            RunLog.Boundary boundary = saveAndQuit(host, 3);
            Path profile = host.driver.headlessBoot().profile();
            int slot = GamesInProgress.curSlot;
            String seal = SuspendedSave.digest(profile, slot);
            assertFalse(seal.isEmpty());
            assertEquals(seal, SuspendedSave.digest(profile, slot), "a seal is stable");
            Path marker = folder.resolve("marker");
            Files.createDirectories(marker);
            SuspendedSave held = new SuspendedSave(run.logFile(), boundary.k(), slot, seal, "random", true);
            held.write(marker);
            assertEquals(held, SuspendedSave.read(marker));
            assertEquals(held, SuspendedSave.claim(marker));
            assertThrows(IllegalStateException.class, () -> SuspendedSave.claim(marker), "claimed once");
            SuspendedSave.unclaim(marker);
            assertTrue(SuspendedSave.present(marker));

            Path save = profile.resolve(GamesInProgress.gameFolder(slot));
            Path file;
            try (var files = Files.list(save)) {
                file = files.filter(Files::isRegularFile).sorted().findFirst().orElseThrow();
            }
            byte[] bytes = Files.readAllBytes(file);
            bytes[bytes.length / 2] ^= 1;
            Files.write(file, bytes);
            assertFalse(seal.equals(SuspendedSave.digest(profile, slot)), "one changed byte changes the seal");
            run.close();
        }
    }

    @Test
    @DisplayName("a death is worded by the game's own record")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_death_reads_the_games_record() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, null, 20_000);
            EmbeddedAttachTest.playUntilWaits(host, 2);
            Buff.affect(Dungeon.hero, Poison.class).set(100f * Dungeon.hero.HT);
            assertEquals(EmbeddedRun.State.ENDED, host.play(100_000));
            assertEquals(RunOutcome.Cause.DEATH, run.outcome().cause());
            RunEnding ending = run.snapshot().ending();
            assertNotNull(ending);
            Rankings.Record record = Rankings.INSTANCE.records.get(Rankings.INSTANCE.records.size() - 1);
            assertEquals(RunEnding.lowerFirst(record.desc().strip()), ending.cause());
            assertFalse(ending.cause().equals("died"), "the record said something: " + ending.cause());
            assertEquals(run.outcome().turns(), ending.turn());
            assertEquals(run.outcome().depth(), ending.floor());
        }
    }

    @Test
    @DisplayName("an Ankh's window is a Prompt the Run answers, and the loading scene that follows is not the end")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void an_ankh_is_a_prompt_not_the_end() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.servesResurrection = true;
            EmbeddedRun run = host.attach(WALKER, null, 20_000);
            EmbeddedAttachTest.playUntilWaits(host, 2);
            assertTrue(new Ankh().collect(Dungeon.hero.belongings.backpack));
            Buff.affect(Dungeon.hero, Poison.class).set(100f * Dungeon.hero.HT);
            EmbeddedAttachTest.playUntil(host, () -> WndResurrect.instance != null
                    && run.state() == EmbeddedRun.State.THINKING);
            assertNull(run.outcome(), "the hero is dead and the Run is not over");
            // The Brain answers the window; the game then asks for the loading scene in its resurrection mode.
            EmbeddedAttachTest.playUntil(host, () -> InterlevelScene.mode == InterlevelScene.Mode.RESURRECT);
            host.frame();
            assertNull(run.outcome(), "a resurrection is served by the game, not an end: " + run.outcome());
        }
    }

    @Test
    @DisplayName("a host that does not serve the resurrection ends the Run as unserved, as the Rig's loop does")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_headless_loop_still_ends_at_a_resurrection() {
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            EmbeddedRun run = host.attach(WALKER, null, 20_000);
            EmbeddedAttachTest.playUntilWaits(host, 2);
            assertTrue(new Ankh().collect(Dungeon.hero.belongings.backpack));
            Buff.affect(Dungeon.hero, Poison.class).set(100f * Dungeon.hero.HT);
            assertEquals(EmbeddedRun.State.ENDED, host.play(100_000));
            assertEquals(RunOutcome.Cause.UNSERVED_SCENE, run.outcome().cause());
        }
    }
}
