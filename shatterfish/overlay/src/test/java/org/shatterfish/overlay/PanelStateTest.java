package org.shatterfish.overlay;

import com.watabou.noosa.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every state of UX-DR7 reached in a script, with the Panel's content and its enablement matrix asserted
 * (story 5.15, FR-38): RUNNING, PAUSED, HUMAN, THINKING, Brain error, Hero busy, No valid action, Run over,
 * No Run, Save and resume, Oracle and Panel collapsed. The Run is a list of calls ({@code ControlStateTest}'s
 * fake), so the Overlay's own part is what is held; the Run's part of Brain error and No valid action (the
 * wait it chooses, the count of three, the log line) is held against a real game by the harness's
 * {@code BrainTroubleTest}.
 */
class PanelStateTest {

    private static final Set<Control> EVERY = EnumSet.allOf(Control.class);
    private static final Set<Control> RUNNING = EnumSet.of(Control.PAUSE_RESUME, Control.NEXT_STEP, Control.SPEED,
            Control.INTERVAL, Control.TAKE_OVER, Control.EXPLAIN);
    private static final Set<Control> HUMAN = EnumSet.of(Control.TAKE_OVER, Control.EXPLAIN);

    private static ControlState running(ControlStateTest.FakeRun run) {
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(controls.pauseResume(), "Resume");
        assertEquals(Mode.RUNNING, controls.mode());
        return controls;
    }

    private static String strip(ControlState controls, boolean thinking) {
        return ModeStripContent.text(controls.view(new ModeState(controls.mode(), controls.speed(), 1.0, 12, 3, thinking)));
    }

    private static RunLog.Decision waitIn(String why) {
        return new RunLog.Decision("waiting", new RunLog.Choice(new Action.Wait(), 0, why), List.of(), List.of(), "overlay");
    }

    @Test
    @DisplayName("RUNNING: the strip says so in green, and Pause, Next Step, the speed selector, Take over and Explain act")
    void running() {
        ControlState controls = running(new ControlStateTest.FakeRun());
        assertTrue(strip(controls, false).startsWith("RUNNING"), strip(controls, false));
        assertEquals(ModeStripContent.RUNNING_COLOR, ModeStripContent.color(Mode.RUNNING));
        assertEquals(RUNNING, controls.enabled());
    }

    @Test
    @DisplayName("PAUSED: a Run starts there with Next Step, held, and every control acts")
    void paused() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(strip(controls, false).startsWith("PAUSED  Next Step"), strip(controls, false));
        assertEquals(EVERY, controls.enabled());
        assertTrue(run.held);
    }

    @Test
    @DisplayName("HUMAN: after a takeover applies, only Hand back and Explain act, and the person's own pace is the speed")
    void human() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(controls.takeOverHandBack());
        run.person = true;
        controls.frame(0.016);
        assertEquals(Mode.HUMAN, controls.mode());
        assertTrue(strip(controls, false).startsWith("HUMAN  player"), strip(controls, false));
        assertEquals(HUMAN, controls.enabled());
    }

    @Test
    @DisplayName("THINKING: a suffix on the strip, and the matrix of the Mode it is in, so a Next Step pressed then is queued")
    void thinking() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        run.thinking = true;
        assertTrue(strip(controls, true).endsWith("THINKING"), strip(controls, true));
        assertFalse(strip(controls, false).contains("THINKING"));
        assertEquals(EVERY, controls.enabled());
        assertTrue(controls.nextStep(), "queued: the credit waits for the Decision");
        assertEquals(1, run.credits);
    }

    @Test
    @DisplayName("Brain error: the card reads brain error with the class in danger colour over a wait, and the Overlay enters PAUSED")
    void brain_error() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = running(run);
        DecisionCardContent.Content card = DecisionCardContent.of(waitIn("java.lang.IllegalStateException: boom"),
                false, false, null, "brain error: IllegalStateException");
        assertEquals("brain error: IllegalStateException", card.headline());
        assertTrue(card.troubled());
        assertInstanceOfWait(card.chosen().action());
        assertEquals(0xFF5555, DecisionCard.DANGER);

        controls.brainTrouble(1, true);
        assertEquals(Mode.PAUSED, controls.mode());
        assertEquals(SpeedMode.NEXT_STEP, controls.speed());
        assertTrue(run.held, "the Run is held: it does not play on over a Brain that cannot decide");
        assertEquals(EVERY, controls.enabled());
        assertTrue(strip(controls, false).startsWith("PAUSED  Next Step"));
        controls.brainTrouble(1, true);
        assertTrue(controls.pauseResume(), "the person resumes; the same trouble does not pause it again");
        controls.frame(0.016);
        assertEquals(Mode.RUNNING, controls.mode());
    }

    @Test
    @DisplayName("No valid action: the card says so and the Run waits once; only the third in a row asks for PAUSED")
    void no_valid_action() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = running(run);
        DecisionCardContent.Content card = DecisionCardContent.of(waitIn("no valid action; waiting"), false, false, null,
                "no valid action; waiting");
        assertEquals("no valid action; waiting", card.headline());
        assertInstanceOfWait(card.chosen().action());

        controls.brainTrouble(1, false);
        controls.brainTrouble(2, false);
        assertEquals(Mode.RUNNING, controls.mode(), "once and twice it only waits");
        controls.brainTrouble(3, true);
        assertEquals(Mode.PAUSED, controls.mode(), "three in a row enter PAUSED");
        assertEquals(EVERY, controls.enabled());
        assertEquals(3, EmbeddedRunConstants.IN_A_ROW);
    }

    @Test
    @DisplayName("a trouble never pauses over a hand-over under way, a person's turn or a Run that is over")
    void trouble_is_ignored_when_it_cannot_pause() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        assertTrue(controls.takeOverHandBack());
        controls.brainTrouble(1, true);
        assertTrue(controls.switching(), "the takeover is still queued");
        run.person = true;
        controls.frame(0.016);
        controls.brainTrouble(2, true);
        assertEquals(Mode.HUMAN, controls.mode());
        ControlStateTest.FakeRun other = new ControlStateTest.FakeRun();
        ControlState done = running(other);
        other.ended = true;
        done.brainTrouble(1, true);
        assertEquals(Mode.RUNNING, done.mode());
    }

    @Test
    @DisplayName("Hero busy: a Take over pressed mid-animation dims every control but Explain until the hero is ready, then applies")
    void hero_busy() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = running(run);
        assertTrue(controls.takeOverHandBack());
        assertTrue(controls.switching());
        assertEquals(EnumSet.of(Control.EXPLAIN), controls.enabled(), "the pressed control stays dim");
        assertFalse(controls.pauseResume(), "and a press on a dim control does nothing");
        assertEquals(Mode.RUNNING, controls.mode(), "the Mode has not changed yet");
        controls.frame(0.016);
        assertEquals(Mode.RUNNING, controls.mode(), "the hero is still busy");
        run.person = true;
        controls.frame(0.016);
        assertFalse(controls.switching());
        assertEquals(Mode.HUMAN, controls.mode(), "the change applies when the hero is ready");
        assertEquals(HUMAN, controls.enabled());
    }

    @Test
    @DisplayName("Run over: the strip reads RUN OVER, in neutral ink, and no control acts")
    void run_over() {
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState controls = new ControlState(run, Capabilities.NOW);
        run.ended = true;
        ModeState over = controls.view(new ModeState(Mode.PAUSED, SpeedMode.NEXT_STEP, 1.0, 40, 2, false));
        assertTrue(ModeStripContent.text(over).startsWith(ModeStripContent.RUN_OVER));
        assertEquals(ModeStripContent.OVER_COLOR, ModeStripContent.color(over));
        assertEquals(EnumSet.noneOf(Control.class), controls.enabled());
    }

    @Test
    @DisplayName("No Run: the title screen, menus and loading draw no Panel; a play scene with a hero does")
    void no_run() {
        assertFalse(PanelDock.attaches(null, false), "no scene yet");
        assertFalse(PanelDock.attaches(new Scene(), true), "a scene that is not the play scene: title, menus, loading");
        assertFalse(PanelDock.attaches(new com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene(), false),
                "a play scene before there is a hero to play");
        assertTrue(PanelDock.attaches(new com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene(), true));
    }

    @Test
    @DisplayName("Save and resume: a resumed Run starts PAUSED with Next Step, held, and the launcher takes a profile to resume")
    void save_and_resume() {
        assertTrue(LaunchOptions.KNOWN.contains("--resume"));
        ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        ControlState resumed = new ControlState(run, Capabilities.NOW);
        assertEquals(Mode.PAUSED, resumed.mode());
        assertEquals(SpeedMode.NEXT_STEP, resumed.speed());
        assertTrue(run.held);
        assertEquals(EVERY, resumed.enabled());
    }

    @Test
    @DisplayName("Oracle: the flag is the launcher's and fixed for the Run, and it changes no control's enablement")
    void oracle() {
        PanelDock dock = new PanelDock();
        dock.oracle(true);
        dock.oracle(true);
        assertThrows(IllegalStateException.class, () -> dock.oracle(false));
        assertEquals("oracle: ", OracleMarkContent.PREFIX);
        for (Mode mode : Mode.values()) {
            assertEquals(ControlEnablement.matrix(mode, false), ControlEnablement.of(mode, false, Capabilities.NOW));
        }
    }

    @Test
    @DisplayName("Panel collapsed: the Mode strip alone, a hotkey toggles it, and every control keeps its enablement")
    void panel_collapsed() {
        PanelLayout.Screen screen = PanelLayout.Screen.of(1000, 500, 2);
        PanelDock dock = new PanelDock();
        assertFalse(dock.collapsed());
        assertTrue(dock.press(Hotkey.PANEL, false));
        assertTrue(dock.collapsed());
        assertEquals(PanelLayout.Form.STRIP, PanelLayout.of(screen, dock.collapsed()).form());
        assertFalse(dock.press(Hotkey.PANEL, true), "nothing acts while a window of the game's is in front");
        assertTrue(dock.collapsed());
        ControlState controls = new ControlState(new ControlStateTest.FakeRun(), Capabilities.NOW);
        assertEquals(EVERY, controls.enabled(), "collapsing hides the row and changes nothing in it");
        assertTrue(dock.press(Hotkey.PANEL, false));
        assertEquals(PanelLayout.Form.FULL, PanelLayout.of(screen, dock.collapsed()).form());
    }

    private static void assertInstanceOfWait(Action action) {
        assertTrue(action instanceof Action.Wait, String.valueOf(action));
    }

    /** The harness's constant, read here so the Overlay's count and the Run's cannot drift apart. */
    private static final class EmbeddedRunConstants {
        static final int IN_A_ROW = org.shatterfish.harness.agent.EmbeddedRun.NO_VALID_ACTIONS_IN_A_ROW;
    }
}
