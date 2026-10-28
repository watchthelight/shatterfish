package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.CoachSession;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coach mode in a real HUMAN Run (story 8.9, FR-47, UJ-4), a learner played by {@code ScriptedHuman} on the
 * harness's {@code EmbeddedHost} over a Warrior of the {@code standard} set's first entry (seed code DCS-SHA-XYL,
 * {@code seeds/standard.json}; never {@code goo} or {@code holdout}). Three claims, each with the Brain the real one:
 * the coach changes nothing about the Run, a sentence that arrives late is dropped and never shown against the
 * wrong wait, and the journey of a learner who took a move the coach disagreed with is in the log and on the
 * Decision log. Every wait on the Brain's worker is waited for on a condition with a deadline, never on a count of
 * frames (the E5 lesson).
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class CoachRunTest {

    /** {@code seeds/standard.json}, entry 0: WARRIOR, DCS-SHA-XYL. */
    private static final long SEED = 648_322_377_831L;
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int MOVES = 30;

    @AfterEach
    void restore() {
        PointerEvent.clearListeners();
    }

    /** Plays frames until the sentence for the wait in front is on the snapshot, or the deadline; says whether it came. */
    private static boolean untilAdvice(CoachSession person) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        while (System.nanoTime() < deadline) {
            EmbeddedRun.Snapshot snapshot = person.snapshot();
            if (snapshot.human() != null && snapshot.human().shadowCurrent() && snapshot.decision() != null) {
                return true;
            }
            if (person.frame() == EmbeddedRun.State.ENDED) {
                return false;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    /** The learner's moves, the same whatever the coach says: wait, wait, search. */
    private static void move(CoachSession person, int i) {
        if (i % 3 == 2) {
            person.pressSearch();
        } else {
            person.pressWait();
        }
    }

    private record Played(List<String> observations, List<Action> actions, Map<Long, RunLog.Decision> shadows,
                          List<RunLog.Mode> modes, Path log) {
    }

    /** The same scripted moves, with the coach on from the sixth move to the twenty-sixth, or never. */
    private static Played play(Path folder, boolean coached) throws IOException {
        return play(folder, coached, -1);
    }

    /** As above, and with the move at index {@code rest} (a control) a rest instead of the script's. */
    private static Played play(Path folder, boolean coached, int rest) throws IOException {
        try (CoachSession person = new CoachSession(SEED, HeroClass.WARRIOR, folder, false)) {
            for (int i = 0; i < MOVES && person.nextWait(); i++) {
                person.untilShadowsLanded();
                if (coached && i == 5) {
                    assertTrue(person.run().coach(true));
                }
                if (coached && i == 25) {
                    assertTrue(person.run().coach(false));
                }
                if (coached && i >= 5 && i < 25) {
                    assertTrue(untilAdvice(person), "the sentence for wait " + (i + 1) + " came before the deadline");
                    assertFalse(CoachLine.of(person.snapshot().decision(), person.snapshot().observation()).isEmpty());
                }
                if (i == rest) {
                    person.pressRest();
                } else {
                    move(person, i);
                }
            }
        }
        Path log = logIn(folder);
        RunLogReader.Log read = RunLogReader.of(log);
        Map<Long, RunLog.Decision> shadows = new TreeMap<>();
        read.records().stream().filter(RunLog.Shadow.class::isInstance).map(RunLog.Shadow.class::cast)
                .forEach(s -> shadows.put(s.k(), s.decision()));
        return new Played(read.waits().stream().map(RunLog.Wait::obs).toList(),
                read.waits().stream().map(RunLog.Wait::action).toList(), shadows,
                read.records().stream().filter(RunLog.Mode.class::isInstance).map(RunLog.Mode.class::cast).toList(), log);
    }

    private static Path logIn(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(path -> path.toString().endsWith(".jsonl")).findFirst().orElseThrow();
        }
    }

    @Test
    @DisplayName("a coached Run and an uncoached one, played with the same moves, hold the same Observation at every wait")
    void coach_changes_nothing(@TempDir Path folder) throws IOException {
        Played plain = play(Files.createDirectories(folder.resolve("plain")), false);
        Played coached = play(Files.createDirectories(folder.resolve("coached")), true);
        assertTrue(plain.observations().size() >= 20, "enough waits to mean something: " + plain.observations().size());
        assertEquals(plain.observations(), coached.observations(), "the Observation hash at every wait");
        assertEquals(plain.actions(), coached.actions(), "the person's Actions");

        // The control: the comparison can tell. One move of the script changed, and the screens part from it on.
        Played other = play(Files.createDirectories(folder.resolve("other")), false, 8);
        assertNotEquals(plain.observations(), other.observations(), "a different move shows in the Observation hashes");

        // The Brain's part is as in HUMAN: it was asked at every wait and decided the same thing at each, so a
        // hand back after a coached stretch re-plans from the same state.
        assertEquals(plain.shadows(), coached.shadows(), "the shadow Decision of each wait");

        // The only difference in the log is the switch's two Mode records, of the existing kind.
        assertEquals(1, plain.modes().size(), "the stated HUMAN Mode alone");
        assertEquals(3, coached.modes().size());
        assertEquals(new RunLog.Mode(6, "HUMAN", EmbeddedRun.COACH_SPEED), coached.modes().get(1));
        assertEquals(new RunLog.Mode(26, "HUMAN", EmbeddedRun.HUMAN_SPEED), coached.modes().get(2));
    }

    @Test
    @DisplayName("a log with coach advice is an ordinary log: it verifies, reads back and replays with every hash matching")
    void a_coached_log_replays(@TempDir Path folder) throws IOException {
        Played coached = play(Files.createDirectories(folder.resolve("coached")), true);
        RunLogVerifier.Verified verified = RunLogVerifier.of(coached.log());
        assertTrue(verified.ok(), verified.why());
        assertTrue(RunLogReader.of(coached.log()).records().stream().noneMatch(RunLog.Unsupported.class::isInstance));
        Replay.Waits replayed = Replay.waitsOf(coached.log(), "test");
        assertEquals(coached.observations().size(), replayed.waits());
        assertEquals(replayed.waits(), replayed.verified(), "every Observation hash matched");
    }

    @Test
    @DisplayName("advice that arrives after the learner moved on is dropped, and no frame shows it against another wait")
    void late_advice_is_dropped(@TempDir Path folder) throws IOException {
        try (CoachSession person = new CoachSession(SEED, HeroClass.WARRIOR, folder, true)) {
            Panel panel = new Panel();
            panel.camera = PixelScene.uiCamera;
            Game.scene().add(panel);
            panel.place(PanelLayout.of(PanelLayout.Screen.of(1600, 900, 1), false));
            panel.coach(new CoachState(on -> person.run().coach(on)));
            assertTrue(person.nextWait());
            ModeState human = ModeState.of(person.snapshot());
            panel.content(human, person.snapshot().decision(), person.snapshot().observation(), false, null, List.of(),
                    PanelDock.shadow(person.snapshot()));
            assertTrue(panel.controls().pressCoach(), "the learner turns the coach on at the first wait");
            // The Brain is held, so wait 1 has no advice: nothing shows, and the learner does not wait for it.
            assertNotEquals(true, person.snapshot().human().shadowCurrent());
            frame(panel, person);
            assertFalse(panel.card().coachRow().visible, "no sentence for a wait whose shadow has not come");
            person.pressWait();
            assertTrue(person.nextWait(), "the learner's move was taken at once and the next wait opened");
            person.pressSearch();
            assertTrue(person.nextWait());
            assertEquals(3, person.run().waitIndex());

            // Now the late advice flows in, wait 1's and 2's first. Every frame it does: the sentence is shown only
            // when it is for the wait in front.
            person.release();
            long deadline = System.nanoTime() + DEADLINE_NANOS;
            boolean shown = false;
            while (System.nanoTime() < deadline && !shown) {
                person.frame();
                EmbeddedRun.Snapshot snapshot = person.snapshot();
                frame(panel, person);
                boolean forThisWait = snapshot.decision() != null && snapshot.human().shadowCurrent()
                        && snapshot.human().shadowWait() == person.run().waitIndex();
                assertEquals(forThisWait, panel.card().coachRow().visible,
                        "the sentence shows exactly when the shadow is for the wait in front");
                if (forThisWait) {
                    assertEquals(CoachLine.of(snapshot.decision(), snapshot.observation()), panel.card().coachRow().text());
                    shown = true;
                }
            }
            assertTrue(shown, "the advice for the wait in front arrived and was shown, within the deadline");
        }
        List<RunLog.Shadow> shadows = RunLogReader.of(logIn(folder)).records().stream()
                .filter(RunLog.Shadow.class::isInstance).map(RunLog.Shadow.class::cast).toList();
        assertTrue(shadows.get(0).skipped() && shadows.get(1).skipped(), "waits 1 and 2 were taken before their advice came");
    }

    private static void frame(Panel panel, CoachSession person) {
        EmbeddedRun.Snapshot snapshot = person.snapshot();
        panel.content(ModeState.of(snapshot), snapshot.decision(), snapshot.observation(), false,
                snapshot.beliefSummary(), snapshot.history(), PanelDock.shadow(snapshot));
    }

    @Test
    @DisplayName("UJ-4: a learner takes a move the coach disagreed with, and the log and the Decision log both hold it")
    void the_learner_disagrees(@TempDir Path folder) throws IOException {
        List<String> sentences = new ArrayList<>();
        List<Action> advised = new ArrayList<>();
        try (CoachSession person = new CoachSession(SEED, HeroClass.WARRIOR, folder, false)) {
            Panel panel = new Panel();
            panel.camera = PixelScene.uiCamera;
            Game.scene().add(panel);
            panel.place(PanelLayout.of(PanelLayout.Screen.of(1600, 900, 1), false));
            panel.coach(new CoachState(on -> person.run().coach(on)));
            assertTrue(person.nextWait());
            person.untilShadowsLanded();
            frame(panel, person);
            assertTrue(panel.controls().pressCoach(), "the coach is switched on from the Panel");
            for (int i = 0; i < 4; i++) {
                assertTrue(untilAdvice(person));
                frame(panel, person);
                assertTrue(panel.card().coachRow().visible, "the sentence is on the card at wait " + (i + 1));
                String sentence = panel.card().coachRow().text();
                Action coachSays = person.snapshot().decision().chosen().action();
                sentences.add(sentence);
                advised.add(coachSays);
                // The learner disagrees: the other of the two moves a person always has.
                if (coachSays instanceof Action.Wait) {
                    person.pressSearch();
                } else {
                    person.pressWait();
                }
                assertTrue(person.nextWait());
                person.untilShadowsLanded();
            }
            EmbeddedRun.Snapshot last = person.snapshot();
            List<DecisionLogContent.Line> lines = DecisionLogContent.of(last.history());
            assertTrue(lines.stream().anyMatch(l -> l.text().equals("mode: HUMAN " + EmbeddedRun.COACH_SPEED)),
                    "the Decision log shows the coach switched on: " + lines);
            assertTrue(lines.stream().anyMatch(l -> l.greyed() && l.text().contains(DecisionLogContent.SHADOW)),
                    "and the shadow of the wait, greyed");
            assertTrue(lines.stream().anyMatch(l -> l.text().contains("human")), "and the learner's own move");
        }
        RunLogReader.Log read = RunLogReader.of(logIn(folder));
        List<RunLog.Wait> waits = read.waits();
        assertTrue(waits.size() >= 4);
        for (int i = 0; i < 4; i++) {
            RunLog.Wait taken = waits.get(i);
            assertEquals(RunLog.HUMAN, taken.actor());
            assertNotEquals(advised.get(i), taken.action(), "the learner's move is not the coach's at wait " + (i + 1));
            RunLog.Shadow shadow = read.records().stream().filter(RunLog.Shadow.class::isInstance)
                    .map(RunLog.Shadow.class::cast).filter(s -> s.k() == taken.k()).findFirst().orElseThrow();
            assertEquals(advised.get(i), shadow.decision().chosen().action(), "the log holds the Decision the coach spoke for");
            assertFalse(shadow.skipped());
            assertFalse(sentences.get(i).isEmpty());
        }
        assertNotNull(read.records().stream().filter(r -> r instanceof RunLog.Mode m
                && EmbeddedRun.COACH_SPEED.equals(m.speed())).findFirst().orElse(null), "and the coach toggle");
    }
}
