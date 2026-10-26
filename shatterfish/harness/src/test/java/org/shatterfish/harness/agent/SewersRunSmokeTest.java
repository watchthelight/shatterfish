package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The epic's demonstration, headless (story 5.16, SM-6): a seeded Warrior played by the real Brain from the first
 * floor to the Goo fight, the Panel's Decision present at every Input wait, a person taking over in the middle of a
 * fight for three turns and handing back, and the Run log replaying exactly afterwards, the person's turns
 * included.
 *
 * <p>The person is {@link ScriptedHuman}: it makes the calls the game's own input makes, so the game, the hooks
 * and the Run's recording hear what a real press would make them hear. The render thread is {@link EmbeddedHost},
 * at the desktop's sixtieth of a second a frame. The Run is held, as the Overlay's PAUSED mode holds it, and the
 * test is the Next Step button: it looks at the Decision the Panel would show, then presses.
 *
 * <p>The seed is a Warrior of the {@code standard} (tuning) set whose Run with this Brain reaches Goo on Profile 5;
 * it was found by the Rig's direction check, never from the {@code goo} or {@code holdout} sets. A change to the
 * Brain, the Codex or the weights can move this Run off Goo, and then the seed is chosen again from the same set
 * and the new one is written on the Results page; that is a decision about the test, not about the Brain.
 */
@Timeout(value = 30, unit = TimeUnit.MINUTES)
class SewersRunSmokeTest {

    /** Entry SEED_INDEX of the standard set's Warriors (the tuning set's), under salt SALT_BASE + SEED_INDEX. */
    static final long SEED = 2713922911738L;
    static final long SALT = 1014L;
    private static final int TURN_CAP = 20_000;
    private static final long FRAMES = 200_000;
    static final int HUMAN_TURNS = 3;
    /** The floor from which the first fight is the person's; the Goo floor is the fifth. */
    static final int TAKEOVER_DEPTH = 2;
    /**
     * The wait from which the second scenario's takeover may come. With this seed and Brain the first fight on the
     * second floor or deeper is at wait 257 and the first one after wait 500 is at wait 519; a Run taken over there
     * parted from its replay at wait 691 (issue #245: the badge banner's shine drew from the Run's stream on the
     * render thread, at a frame the replay's frames did not share).
     */
    static final long LATER_TAKEOVER_FROM = 500;

    /** What a run of the scenario found, for the assertions and for the Results page. */
    record Found(long waits, long botWaits, long windowAnswers, long humanWaits, int deepest, int gooWaits, long takeoverWait,
                 long handBackWait, int decisionsSeen, String ending, Path log) {
    }

    @Test
    @DisplayName("a seeded Warrior from depth one to the Goo fight, a takeover of three turns mid-fight, a hand back, and a log that replays")
    void the_full_sewers_run_with_a_takeover(@TempDir Path folder) throws IOException {
        Found found = play(folder);
        assertTrue(found.deepest >= 5, "the Run reached the Goo floor: " + found);
        assertTrue(found.gooWaits >= 2, "Goo was in view at two waits or more, a fight: " + found);
        assertTrue(found.decisionsSeen >= found.botWaits - found.windowAnswers,
                "the Panel held a Decision before every turn the Brain played (a window's answer costs no credit, EmbeddedRun.owed): " + found);
        assertTrue(found.humanWaits >= HUMAN_TURNS, "the person played their turns: " + found);
    }

    @Test
    @DisplayName("the same Run taken over at a later fight, 500 waits in, replays too (issue #245)")
    void a_later_takeover_replays(@TempDir Path folder) throws IOException {
        Found found = play(folder, LATER_TAKEOVER_FROM);
        assertTrue(found.takeoverWait >= LATER_TAKEOVER_FROM, "the person took a fight after wait " + LATER_TAKEOVER_FROM + ": " + found);
        assertTrue(found.humanWaits >= HUMAN_TURNS, "the person played their turns: " + found);
    }

    /** Plays the scenario into {@code folder} and checks everything about the log; returns what it found. */
    static Found play(Path folder) throws IOException {
        return play(folder, 0);
    }

    /** As {@link #play(Path)}, with the person taking the first fight at wait {@code takeoverFrom} or later. */
    static Found play(Path folder, long takeoverFrom) throws IOException {
        Path played = Files.createDirectories(folder.resolve("played"));
        long takeoverWait;
        long handBackWait;
        int decisions = 0;
        java.util.Set<Long> ks = new java.util.TreeSet<>();
        int deepest = 0;
        int gooWaits = 0;
        String landedHash;
        RunLog.Decision landedDecision;
        String takeoverHash;
        try (EmbeddedHost host = new EmbeddedHost(SEED, HeroClass.WARRIOR, SALT)) {
            host.frameTime = 1f / 60f;
            // The desktop's: the frames go on while the worker thinks. A window the Brain opened is answered only
            // once it is old enough (EmbeddedRun.tooEarlyForTheWindow), which takes game time, so a host that stops
            // the game while the Brain thinks never gets there.
            host.stepWhileThinking = true;
            EmbeddedRun run = host.attach(RealBrain.decider(), new RunLoop.Logging(played, "0".repeat(40),
                    new RunLog.Brain("shatterfish", "0".repeat(40), "0".repeat(64)), "", "overlay"), TURN_CAP);
            run.hold(true);

            // Phase 1: the Brain's, floor by floor, until a fight on the second floor or deeper.
            long seen = -1;
            long frames = 0;
            takeoverHash = null;
            takeoverWait = 0;
            while (takeoverWait == 0) {
                boolean waiting = host.waitingOnTheBrain();
                if (host.frame() == EmbeddedRun.State.ENDED) {
                    throw new AssertionError("the Run ended before a fight to take over: " + run.outcome());
                }
                if (waiting) {
                    Thread.onSpinWait();
                    continue;
                }
                if (++frames > 50_000_000L) {
                    throw new AssertionError("no fight to take over");
                }
                if (run.withheld() && run.waitIndex() != seen) {
                    seen = run.waitIndex();
                    EmbeddedRun.Snapshot shown = run.snapshot();
                    assertTrue(shown.decision() != null && shown.awaiting(), "a Decision on the Panel at wait " + seen);
                    decisions++;
                    ks.add(seen);
                    Observation obs = shown.observation();
                    deepest = Math.max(deepest, obs.header().depth());
                    if (goo(obs)) {
                        gooWaits++;
                    }
                    if (obs.header().depth() >= TAKEOVER_DEPTH && seen >= takeoverFrom && ScriptedHuman.offered(obs, Action.Attack.class) != null) {
                        takeoverHash = obs.hash();
                        takeoverWait = seen;
                        run.takeOver("Human");
                    } else {
                        run.advance(1);
                    }
                }
            }
            assertTrue(host.untilOpen(FRAMES), "the person's wait opened");
            assertTrue(run.human() && run.humanInput());
            assertEquals(takeoverWait, run.waitIndex(), "the person took the wait the Brain had decided");

            // Phase 2: the person's. Three turns: attack what is attackable, else wait.
            for (int turn = 0; turn < HUMAN_TURNS; turn++) {
                Observation obs = run.snapshot().observation();
                deepest = Math.max(deepest, obs.header().depth());
                Action.Attack attack = ScriptedHuman.offered(obs, Action.Attack.class);
                if (attack != null) {
                    ScriptedHuman.tapCell(attack.cell());
                } else {
                    ScriptedHuman.pressWait();
                }
                assertTrue(host.untilOpen(FRAMES), "wait " + (takeoverWait + turn + 1) + " opened");
            }

            // Phase 3: the Brain's again, from where the person left the game.
            run.handBack("Human");
            EmbeddedAttachTest.playUntil(host, () -> !run.human() && run.withheld());
            EmbeddedRun.Snapshot landed = run.snapshot();
            handBackWait = run.waitIndex();
            assertTrue(landed.awaiting() && landed.decision() != null, "the Panel shows the Decision the next press executes");
            landedHash = landed.observation().hash();
            landedDecision = landed.decision();
            assertNotEquals(takeoverHash, landedHash, "re-planned from the screen after the person's turns, not the one before");
            assertEquals(takeoverWait + HUMAN_TURNS, handBackWait, "the Brain resumed at the wait after the person's last turn");

            seen = handBackWait;
            decisions++;
                    ks.add(seen);
            run.advance(1);
            while (true) {
                boolean waiting = host.waitingOnTheBrain();
                if (host.frame() == EmbeddedRun.State.ENDED) {
                    break;
                }
                if (waiting) {
                    Thread.onSpinWait();
                    continue;
                }
                if (++frames > 50_000_000L) {
                    throw new AssertionError("the Run did not end");
                }
                if (run.withheld() && run.waitIndex() != seen) {
                    seen = run.waitIndex();
                    EmbeddedRun.Snapshot shown = run.snapshot();
                    assertTrue(shown.decision() != null && shown.awaiting(), "a Decision on the Panel at wait " + seen);
                    decisions++;
                    ks.add(seen);
                    Observation obs = shown.observation();
                    deepest = Math.max(deepest, obs.header().depth());
                    if (goo(obs)) {
                        gooWaits++;
                    }
                    run.advance(1);
                }
            }
            assertEquals(EmbeddedRun.State.ENDED, run.state());
        }

        // The log: it verifies, it is the whole Run, it says who played what, and it replays.
        Path log = HumanTurnReplayTest.only(played);
        RunLogVerifier.Verified verified = RunLogVerifier.of(log);
        assertTrue(verified.ok() && verified.complete(), verified.why());
        RunLogReader.Log read = RunLogReader.of(log);
        assertTrue(read.header().embedded(), "an Overlay log says which driver played it");
        assertTrue(read.records().stream().noneMatch(RunLog.Unsupported.class::isInstance), "nothing unsupported");
        List<RunLog.Wait> waits = read.waits();
        List<String> actors = waits.stream().map(RunLog.Wait::actor).toList();
        long human = actors.stream().filter(RunLog.HUMAN::equals).count();
        long bot = actors.stream().filter(RunLog.BOT::equals).count();
        List<Long> humanKs = new ArrayList<>();
        for (RunLog.Wait wait : waits) {
            if (RunLog.HUMAN.equals(wait.actor())) {
                humanKs.add(wait.k());
            }
        }
        assertEquals(List.of(takeoverWait, takeoverWait + 1, takeoverWait + 2), humanKs, "the person's three turns, in a row");
        assertTrue(actors.indexOf(RunLog.HUMAN) > 0 && actors.lastIndexOf(RunLog.HUMAN) < actors.size() - 1,
                "the Brain played before and after them");
        List<RunLog.Mode> modes = read.records().stream().filter(RunLog.Mode.class::isInstance).map(RunLog.Mode.class::cast).toList();
        assertEquals(List.of("HUMAN", "PAUSED"), modes.stream().map(RunLog.Mode::mode).toList());
        assertEquals(List.of(takeoverWait, handBackWait), modes.stream().map(RunLog.Mode::k).toList(), "the changes of hands, at their waits");
        RunLog.Wait resumed = waits.get((int) handBackWait - 1);
        assertEquals(handBackWait, resumed.k());
        assertEquals(RunLog.BOT, resumed.actor());
        assertEquals(landedHash, resumed.obs(), "the Brain's first wait after the hand back is the Decision the Panel showed");
        assertEquals(landedDecision, resumed.decision());

        System.out.println("sewers: log " + log + " takeover at " + takeoverWait + " hand back at " + handBackWait
                + " waits " + waits.size() + " deepest " + deepest + " goo waits " + gooWaits + " ending "
                + read.end().outcome());
        long windowAnswers = 0;
        for (RunLog.Wait wait : waits) {
            if (!RunLog.BOT.equals(wait.actor())) {
                continue;
            }
            assertTrue(wait.decision() != null, "every wait the Brain played carries its Decision: wait " + wait.k());
            if (wait.action() instanceof Action.AnswerPrompt) {
                windowAnswers++;
            } else {
                assertTrue(ks.contains(wait.k()), "the Decision for wait " + wait.k() + " was on the Panel before the press");
            }
        }
        // Kept beside the build output, where the Results page's log is taken from.
        Path kept = Files.createDirectories(Path.of("build", "sewers-smoke"));
        Files.copy(log, kept.resolve(log.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Replay.Waits replayed = Replay.waitsOf(log, "test");
        assertEquals(waits.size(), replayed.waits(), "every wait of the log was replayed");
        assertEquals(replayed.waits(), replayed.verified(), "every Observation hash matched, the person's turns included: " + replayed);
        assertEquals(0L, replayed.unverifiableFrom(), "nothing in the log is beyond a Replay");
        return new Found(waits.size(), bot, windowAnswers, human, deepest, gooWaits, takeoverWait, handBackWait, decisions,
                read.end().outcome().cause(), log);
    }

    /** Whether Goo is in view, as the Panel's Observation shows it. */
    static boolean goo(Observation obs) {
        return obs.header().depth() == 5 && obs.actors().actors().stream()
                .anyMatch(actor -> actor.alignment() == Alignment.ENEMY && actor.name().equals("Goo"));
    }
}
