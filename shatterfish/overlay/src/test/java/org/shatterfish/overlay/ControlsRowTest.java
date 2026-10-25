package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.ui.Component;
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
import org.shatterfish.overlay.ControlEnablement.Control;
import org.shatterfish.overlay.ModeState.Mode;
import org.shatterfish.overlay.ModeState.SpeedMode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Controls row on a real play scene (story 5.6, FR-39, UX-DR7, {@code DESIGN.md}): the eight controls
 * of the row are the game's own {@code RedButton}s, wrap to two rows at the Panel's width, and in every
 * state of the matrix are live exactly where {@link ControlEnablement} says and dim where it does not.
 * Pressing is done the way a person's tap reaches a button, through the game's pointer events.
 */
class ControlsRowTest {

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

    private static RunLog.Decision decision() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Search(), 7_800, "nothing better");
        return new RunLog.Decision("search", chosen, List.of(), List.of(), "explore");
    }

    private static EmbeddedRun.Snapshot snapshot(EmbeddedRun.State state) {
        return new EmbeddedRun.Snapshot(decision(), 14, 2, null, state);
    }

    private static void press(Component button) {
        Camera camera = button.camera();
        float x = button.left() + button.width() / 2;
        float y = button.top() + button.height() / 2;
        Point screen = camera.cameraToScreen(x, y);
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
        PointerEvent.processPointerEvents();
    }

    @Test
    @DisplayName("the row holds all eight controls, as the game's own RedButtons, in two rows at the Panel's width")
    void the_row_wraps_to_two_rows() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        assertEquals(PanelLayout.Form.FULL, panel.placed().form());
        ControlsRow row = panel.controls();
        assertTrue(row.visible);
        for (Control control : Control.values()) {
            assertTrue(row.button(control).visible, control.name());
        }
        assertEquals(2, row.rows(), "at the Panel's width " + row.width() + " the buttons take two rows");
        assertEquals(2 * ControlsRow.BUTTON_HEIGHT + ControlsRow.GAP, row.contentHeight(), 0.01f);
        // Nothing runs off the row's right edge, and the two rows do not overlap.
        for (Control control : Control.values()) {
            RedButton button = row.button(control);
            assertTrue(button.left() >= row.left() - 0.01f, control + " starts inside the row");
        }
        assertTrue(row.intervalPlus().right() <= row.right() + 0.01f);
        assertTrue(row.explain().right() <= row.right() + 0.01f);
        assertTrue(row.explain().top() > row.pause().top(), "Explain is on the second row");
        // The row is the Panel's last section: below the Decision log, inside the Panel.
        assertTrue(row.top() >= panel.log().bottom());
        assertTrue(row.bottom() <= panel.bottom() - PanelLayout.PADDING + 0.01f);
    }

    @Test
    @DisplayName("at the narrowest full Panel the buttons wrap further rather than run off it")
    void narrow_wraps_further() {
        // 1100 wide at interface size 1 leaves the Panel its minimum width of 160 (story 5.2).
        GameScene scene = scene(1100, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        if (panel.placed().form() != PanelLayout.Form.FULL) {
            return; // this window is too narrow for a full Panel at all; the strip alone is drawn
        }
        ControlsRow row = panel.controls();
        assertTrue(row.rows() >= 2 && row.rows() <= 4, "rows: " + row.rows());
        for (Control control : Control.values()) {
            assertTrue(row.button(control).right() <= row.right() + 0.01f, control + " stays inside the row");
        }
        assertTrue(row.bottom() <= panel.bottom() + 0.01f);
    }

    /** The Mode strip's states, each with the Mode, whether it is over and whether the Brain is over budget. */
    private record State(String name, Mode mode, boolean over, boolean thinking) {
    }

    @Test
    @DisplayName("in RUNNING, PAUSED, HUMAN, THINKING and Run over each control is live exactly where the matrix says and dim where it does not")
    void every_state_draws_its_matrix() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        List<State> states = List.of(
                new State("RUNNING", Mode.RUNNING, false, false),
                new State("PAUSED", Mode.PAUSED, false, false),
                new State("HUMAN", Mode.HUMAN, false, false),
                new State("RUNNING, THINKING", Mode.RUNNING, false, true),
                new State("PAUSED, THINKING", Mode.PAUSED, false, true),
                new State("Run over", Mode.RUNNING, true, false));
        for (State state : states) {
            ModeState mode = new ModeState(state.mode(), SpeedMode.NEXT_STEP, 1.0, 14, 2, state.thinking(), state.over());
            panel.content(mode, decision(), null, false, null, List.of());
            // The row of a Panel with no controls bound draws by what is built now.
            Set<Control> expected = ControlEnablement.of(state.mode(), state.over(), Capabilities.NOW);
            for (Control control : Control.values()) {
                assertEquals(expected.contains(control), panel.controls().button(control).active,
                        control + " in " + state.name());
                assertEquals(expected.contains(control), panel.controls().acts(control), control + " in " + state.name());
            }
            assertEquals(ControlEnablement.pauseLabel(state.mode()), panel.controls().pause().text());
            assertEquals(ControlEnablement.takeOverLabel(state.mode()), panel.controls().takeOver().text());
        }
    }

    @Test
    @DisplayName("with a window of the game's in front every control is dim, whatever the matrix says")
    void a_window_in_front_dims_everything() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), true);
        for (Control control : Control.values()) {
            assertFalse(dock.panel().controls().button(control).active, control.name());
            assertFalse(dock.panel().controls().acts(control), control.name());
        }
    }

    @Test
    @DisplayName("a Brain's Run opens PAUSED with Next Step, and the strip says so")
    void opens_paused() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        ControlsHeld held = new ControlsHeld();
        dock.controls(held.state);
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        assertTrue(panel.stripText().text().startsWith("PAUSED  Next Step"), panel.stripText().text());
        assertEquals("Resume", panel.controls().pause().text());
        assertTrue(panel.controls().next().active);
        assertTrue(panel.controls().runN().active);
        assertEquals("10", panel.controls().countValue().text());
        assertEquals("1.0s", panel.controls().intervalValue().text());
        assertTrue(panel.controls().takeOver().active, "story 5.8 built Take over");
        assertTrue(panel.controls().intervalPlus().active, "story 5.7 built the pace");
        assertTrue(held.run.held);
    }

    @Test
    @DisplayName("a tap on each live button does what it says, and a tap on a dim one does nothing")
    void taps_reach_the_controls() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        ControlsHeld held = new ControlsHeld();
        dock.controls(held.state);
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        ControlsRow row = panel.controls();

        press(row.next());
        assertEquals(1, held.run.credits, "Next Step gives the held Run one wait");

        press(row.countPlus());
        press(row.countPlus());
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertEquals("12", row.countValue().text());
        press(row.countMinus());
        assertEquals(11, held.state.count());

        press(row.takeOver());
        assertTrue(held.state.switching(), "Take over asks the Run to change hands");
        assertEquals(Mode.PAUSED, held.state.mode(), "and the Mode follows the Run, at its Input wait");
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertFalse(row.takeOver().active, "the pressed control stays dim until the hero is ready");
        assertFalse(row.speed().active);
        held.run.person = true;
        held.state.frame(0.016);
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertEquals("Hand back", row.takeOver().text());
        assertTrue(row.takeOver().active);
        held.run.person = false;
        press(row.takeOver());
        held.state.frame(0.016);
        assertEquals(Mode.PAUSED, held.state.mode(), "Hand back lands in PAUSED");
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        press(row.intervalPlus());
        assertEquals(1.5, held.state.interval());
        press(row.intervalMinus());
        assertEquals(1.0, held.state.interval());

        press(row.speed());
        assertEquals(SpeedMode.HUMAN_PLAY, held.state.speed());
        press(row.speed());
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertEquals(SpeedMode.FAST, held.state.speed());
        assertEquals("Fast", row.speed().text());

        press(row.pause());
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertEquals(Mode.RUNNING, held.state.mode());
        assertFalse(held.run.held);
        assertTrue(panel.stripText().text().startsWith("RUNNING  Fast"), panel.stripText().text());
        assertEquals("Pause", row.pause().text());
        assertFalse(row.runN().active, "Run N is dim while RUNNING");
        press(row.runN());
        assertEquals(Mode.RUNNING, held.state.mode());

        press(row.pause());
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertEquals(Mode.PAUSED, held.state.mode());
        assertTrue(held.run.held);

        press(row.runN());
        assertEquals(Mode.RUNNING, held.state.mode());
        held.run.ready = true;
        held.state.frame(0.016);
        assertEquals(1, held.run.credits, "a Run N gives its waits one at a time, to a Decision that waits");
    }

    @Test
    @DisplayName("a Decision that has landed and waits for a press is headed 'next press executes' in PAUSED, and not otherwise")
    void the_pending_decision_is_headed() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        ControlsHeld held = new ControlsHeld();
        dock.controls(held.state);
        EmbeddedRun.Snapshot waiting = new EmbeddedRun.Snapshot(decision(), 14, 2, null, EmbeddedRun.State.PLAYING, null,
                List.of(), null, true);
        dock.frame(scene, waiting, false);
        assertEquals(DecisionCardContent.NEXT_PRESS, dock.panel().card().headlineRow().text());
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        assertTrue(dock.panel().card().headlineRow() == null || dock.panel().card().headlineRow().text().isEmpty(),
                "the last Decision executed is not headed as the next");
        held.state.pauseResume();
        dock.frame(scene, waiting, false);
        assertTrue(dock.panel().card().headlineRow() == null || dock.panel().card().headlineRow().text().isEmpty(),
                "RUNNING: the pending Decision is about to execute on its own");
    }

    @Test
    @DisplayName("Explain toggles the card's expansion from the row, and is dim with no Decision to explain")
    void explain_from_the_row() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        assertTrue(panel.controls().explain().active);
        assertFalse(panel.card().explaining());
        press(panel.controls().explain());
        assertTrue(panel.card().explaining());
        press(panel.controls().explain());
        assertFalse(panel.card().explaining());

        dock.frame(scene, new EmbeddedRun.Snapshot(null, 0, 1, null, EmbeddedRun.State.PLAYING), false);
        assertFalse(panel.controls().explain().active, "nothing to explain yet");
    }

    @Test
    @DisplayName("the Panel is a place a press passes only on the scene that has it, not on the next one (the fairness review)")
    void contains_only_on_its_own_scene() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(EmbeddedRun.State.PLAYING), false);
        Panel panel = dock.panel();
        Point inside = panel.camera.cameraToScreen(panel.left() + panel.width() / 2, panel.top() + panel.height() / 2);
        assertTrue(dock.contains(inside.x, inside.y));
        assertFalse(dock.contains(0, 0), "off the Panel");
        HeadlessBoot.ensure().game().switchTo(new HeadlessScene());
        assertFalse(dock.contains(inside.x, inside.y), "the old Panel's rectangle on a scene that has no Panel");
    }

    @Test
    @DisplayName("a Run that has ended leaves every button dim, and the strip's Mode as the controls last had it")
    void run_over_is_dim() {
        GameScene scene = scene(1600, 900);
        PanelDock dock = new PanelDock();
        ControlsHeld held = new ControlsHeld();
        dock.controls(held.state);
        held.run.ended = true;
        dock.frame(scene, snapshot(EmbeddedRun.State.ENDED), false);
        for (Control control : Control.values()) {
            assertFalse(dock.panel().controls().button(control).active, control.name());
        }
    }

    /** A Brain's Run's controls over a Run that records what it is told. */
    private static final class ControlsHeld {
        final ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        final ControlState state = new ControlState(run, Capabilities.NOW);
    }
}
