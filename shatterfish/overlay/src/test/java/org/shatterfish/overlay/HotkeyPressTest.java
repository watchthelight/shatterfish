package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.utils.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.overlay.ControlEnablement.Capabilities;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hotkey presses its control exactly as the button would (story 5.11, FR-42, UX-DR12): on a real play
 * scene, through the dock the input lock hands the key to. A key can do nothing a press on the button could
 * not: a dim control ignores it, and so does every control while a window of the game's is in front.
 */
class HotkeyPressTest {

    private static final long SEED = 12345;
    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        PointerEvent.clearListeners();
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    private GameScene scene(int w, int h) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(1);
        Game.width = w;
        Game.height = h;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    private static EmbeddedRun.Snapshot snapshot() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Search(), 7_800, "nothing better");
        RunLog.Decision decision = new RunLog.Decision("search", chosen, List.of(), List.of(), "explore");
        return new EmbeddedRun.Snapshot(decision, 14, 2, null, EmbeddedRun.State.PLAYING);
    }

    /** A Brain's Run's controls over a Run that records what it is told. */
    private static final class Held {
        final ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        final ControlState state = new ControlState(run, Capabilities.NOW);
    }

    private static void tap(com.watabou.noosa.Gizmo area, Camera camera, float cx, float cy) {
        Point screen = camera.cameraToScreen(cx, cy);
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
        PointerEvent.processPointerEvents();
    }

    @Test
    @DisplayName("each key does what its button does: Next Step, speed, Pause and Resume, Take over and Hand back")
    void a_key_is_its_button() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        Held held = new Held();
        dock.controls(held.state);
        dock.frame(scene, snapshot(), false);

        assertTrue(dock.press(Hotkey.NEXT_STEP, false));
        assertEquals(1, held.run.credits, "F6 gives the held Run one wait, as the Next Step button does");
        assertFalse(dock.press(Hotkey.NEXT_STEP, false), "a second press before it has fired does not stack");
        assertEquals(1, held.run.credits);

        assertTrue(dock.press(Hotkey.SPEED, false));
        assertEquals(SpeedMode.HUMAN_PLAY, held.state.speed());
        assertTrue(dock.press(Hotkey.SPEED, false));
        assertEquals(SpeedMode.FAST, held.state.speed());

        assertTrue(dock.press(Hotkey.PAUSE_RESUME, false));
        assertEquals(Mode.RUNNING, held.state.mode());
        dock.frame(scene, snapshot(), false);
        assertTrue(dock.press(Hotkey.PAUSE_RESUME, false));
        assertEquals(Mode.PAUSED, held.state.mode());
        dock.frame(scene, snapshot(), false);

        assertTrue(dock.press(Hotkey.TAKE_OVER, false));
        assertTrue(held.state.switching(), "F9 asks the Run to change hands, which applies at an Input wait");
        assertTrue(held.run.calls.stream().anyMatch(call -> call.startsWith("takeOver")));
        dock.frame(scene, snapshot(), false);
        assertFalse(dock.press(Hotkey.SPEED, false), "and until it has, every control but Explain is dim, to a key as to a tap");
        assertFalse(dock.press(Hotkey.PAUSE_RESUME, false));
    }

    @Test
    @DisplayName("a dim control ignores its key: nothing a button could not do")
    void a_dim_control_ignores_its_key() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        Held held = new Held();
        dock.controls(held.state);
        held.run.ended = true;
        dock.frame(scene, snapshot(), false);
        for (Hotkey hotkey : Hotkey.values()) {
            if (hotkey != Hotkey.PANEL) {
                assertFalse(dock.press(hotkey, false), hotkey + " after the Run is over");
            }
        }
        assertEquals(List.of("hold true"), held.run.calls, "the Run was told nothing but its opening hold");
        assertEquals(Mode.PAUSED, held.state.mode());
    }

    @Test
    @DisplayName("with a window of the game's in front, no key acts, the Panel toggle included")
    void a_window_in_front_takes_no_hotkey() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        Held held = new Held();
        dock.controls(held.state);
        dock.frame(scene, snapshot(), true);
        for (Hotkey hotkey : Hotkey.values()) {
            assertFalse(dock.press(hotkey, true), hotkey.name());
        }
        assertFalse(dock.collapsed());
        assertEquals(List.of("hold true"), held.run.calls);
        // Even a key pressed with the dock's own last frame still unlocked is refused by the row.
        assertFalse(dock.panel().controls().press(ControlEnablement.Control.NEXT_STEP));
    }

    @Test
    @DisplayName("Explain's key toggles the card as its button does, and a HUMAN Run's Panel answers only Explain and the toggle")
    void explain_and_a_human_run() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(), false);
        Panel panel = dock.panel();
        assertFalse(panel.card().explaining());
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        assertTrue(panel.card().explaining());
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        assertFalse(panel.card().explaining());
        // No controls bound, as in a Run a person plays from the start: the buttons press nothing and so do the keys.
        assertFalse(dock.press(Hotkey.NEXT_STEP, false));
        assertFalse(dock.press(Hotkey.PAUSE_RESUME, false));
        assertFalse(dock.press(Hotkey.TAKE_OVER, false));
        dock.frame(scene, new EmbeddedRun.Snapshot(null, 0, 1, null, EmbeddedRun.State.PLAYING), false);
        assertFalse(dock.press(Hotkey.EXPLAIN, false), "nothing to explain yet");
    }

    @Test
    @DisplayName("the Panel toggle collapses the Panel to the Mode strip and restores it, by key and by a tap on the strip")
    void the_panel_toggle() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(), false);
        assertEquals(PanelLayout.Form.FULL, dock.panel().placed().form());

        assertTrue(dock.press(Hotkey.PANEL, false));
        dock.frame(scene, snapshot(), false);
        assertEquals(PanelLayout.Form.STRIP, dock.panel().placed().form(), "F11 collapses it");
        assertTrue(dock.press(Hotkey.PANEL, false));
        dock.frame(scene, snapshot(), false);
        assertEquals(PanelLayout.Form.FULL, dock.panel().placed().form(), "and restores it");

        // The mouse reaches it too: a tap on the Mode strip.
        Panel panel = dock.panel();
        assertTrue(panel.stripArea().active);
        tap(panel.stripArea(), panel.camera, panel.strip().x + panel.strip().width() / 2f,
                panel.strip().y + panel.strip().height() / 2f);
        dock.frame(scene, snapshot(), false);
        assertEquals(PanelLayout.Form.STRIP, dock.panel().placed().form(), "a tap on the strip collapses");
        panel = dock.panel();
        tap(panel.stripArea(), panel.camera, panel.strip().x + panel.strip().width() / 2f,
                panel.strip().y + panel.strip().height() / 2f);
        dock.frame(scene, snapshot(), false);
        assertEquals(PanelLayout.Form.FULL, dock.panel().placed().form(), "and a second restores");

        // A window in front switches the strip's area off with every other hot area.
        dock.frame(scene, snapshot(), true);
        assertFalse(dock.panel().stripArea().active);
    }
}
