package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Explain expansion in the Panel (story 8.2, FR-44, UX-DR15) on a real play scene: it opens inside the
 * Panel below the card, follows the Decision at each wait and stays open, closes on a second press, on the
 * Panel's collapse, on Take over and when Explain goes dim, says why when a key finds Explain disabled, and
 * takes no tap while a window is in front. What it says is {@code ExplainContentTest}'s.
 */
class ExplainPanelTest {

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

    private GameScene scene() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(1);
        Game.width = 1600;
        Game.height = 900;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    private static RunLog.Decision withTerms(String policy, long score) {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Attack(42), score, "corridor, full hp",
                List.of(new RunLog.Term("depth", score + 3_000), new RunLog.Term("enemies", -3_000)));
        RunLog.Choice alternative = new RunLog.Choice(new Action.Step(12), 5_100, "corridor");
        return new RunLog.Decision("fight: gnoll scout", chosen, List.of(alternative), List.of("hp-low"), policy);
    }

    private static RunLog.Decision withoutTerms() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Search(), 10_000, "nothing better");
        return new RunLog.Decision("search", chosen, List.of(), List.of(), "explore");
    }

    private static EmbeddedRun.Snapshot snapshot(RunLog.Decision decision) {
        return new EmbeddedRun.Snapshot(decision, 14, 2, null, EmbeddedRun.State.PLAYING);
    }

    private static final class Held {
        final ControlStateTest.FakeRun run = new ControlStateTest.FakeRun();
        final ControlState state = new ControlState(run, Capabilities.NOW);
    }

    @Test
    @DisplayName("the expansion follows the new Decision at the next frame and stays open, back at the top")
    void follows_the_decision() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(withTerms("fight-in-corridors", 7_800)), false);
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(withTerms("fight-in-corridors", 7_800)), false);
        ExplainView view = dock.panel().explain();
        assertEquals("policy: fight-in-corridors", view.lines().get(0).left());

        dock.frame(scene, snapshot(withTerms("approach", 6_000)), false);
        assertTrue(dock.panel().card().explaining(), "still expanded at the next Decision");
        assertEquals("policy: approach", view.lines().get(0).left(), "and it shows the new one");
        assertEquals("0.6000", view.lines().get(1).right());
        assertEquals(0f, view.pane().content().camera.scroll.y, "back at the top");
    }

    @Test
    @DisplayName("closes on Explain again, and on the Panel's collapse: it does not come back with the Panel")
    void closes_on_explain_and_on_collapse() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.panel().explain().shown());
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertFalse(dock.panel().explain().shown());

        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.panel().card().explaining());
        assertTrue(dock.press(Hotkey.PANEL, false));
        dock.frame(scene, snapshot(decision), false);
        assertEquals(PanelLayout.Form.STRIP, dock.panel().placed().form());
        assertFalse(dock.panel().card().explaining(), "collapsing the Panel closes the expansion");
        assertFalse(dock.panel().explain().visible);
        assertTrue(dock.press(Hotkey.PANEL, false));
        dock.frame(scene, snapshot(decision), false);
        assertEquals(PanelLayout.Form.FULL, dock.panel().placed().form());
        assertFalse(dock.panel().card().explaining(), "restored, it is still closed");
    }

    @Test
    @DisplayName("closes on Take over, as the press is made, and Explain can be opened again over the shadow")
    void closes_on_take_over() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        Held held = new Held();
        dock.controls(held.state);
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.panel().card().explaining());

        assertTrue(dock.press(Hotkey.TAKE_OVER, false));
        dock.frame(scene, snapshot(decision), false);
        assertFalse(dock.panel().card().explaining(), "closed when Take over was pressed");
        held.run.person = true;
        held.state.frame(0.016);
        dock.frame(scene, snapshot(decision), false);
        assertEquals(ModeState.Mode.HUMAN, held.state.mode());
        assertTrue(dock.press(Hotkey.EXPLAIN, false), "Explain is enabled in HUMAN");
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.panel().card().explaining(), "and stays open: the hand-over is not what closed it");
    }

    @Test
    @DisplayName("collapsed to the Mode strip, Explain is disabled and the strip says why; the words go after a moment")
    void disabled_when_collapsed() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        dock.collapsed(true);
        dock.frame(scene, snapshot(decision), false);
        Panel panel = dock.panel();
        assertEquals(ControlEnablement.EXPLAIN_PANEL_COLLAPSED, panel.explainReason());
        String line = panel.stripText().text();

        assertFalse(dock.press(Hotkey.EXPLAIN, false), "the key does nothing");
        dock.frame(scene, snapshot(decision), false);
        assertFalse(panel.card().explaining());
        assertEquals(ControlEnablement.EXPLAIN_PANEL_COLLAPSED, panel.stripText().text(), "and says why");

        panel.notice(ControlEnablement.EXPLAIN_PANEL_COLLAPSED, System.nanoTime() - 1);
        dock.frame(scene, snapshot(decision), false);
        assertEquals(line, panel.stripText().text(), "the Mode line is back once the moment has passed");
    }

    @Test
    @DisplayName("a Run that is over closes the expansion and Explain says so")
    void closes_when_the_run_is_over() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        Held held = new Held();
        dock.controls(held.state);
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.panel().card().explaining());
        held.run.ended = true;
        dock.frame(scene, snapshot(decision), false);
        assertFalse(dock.panel().card().explaining());
        assertEquals(ControlEnablement.EXPLAIN_RUN_OVER, dock.panel().explainReason());
    }

    @Test
    @DisplayName("terms are rows with the contribution in one column at the right edge; a Decision without terms has none of them")
    void terms_are_a_column() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        ExplainView view = dock.panel().explain();
        float edge = -1;
        int numbers = 0;
        for (int i = 0; i < view.lines().size(); i++) {
            if (view.lines().get(i).right().isEmpty()) {
                assertFalse(view.right(i).visible, "no number, no block: " + view.lines().get(i));
                continue;
            }
            numbers++;
            float right = view.right(i).left() + view.right(i).width();
            if (edge < 0) {
                edge = right;
            }
            assertEquals(edge, right, 0.5f, "every number ends at one edge: " + view.lines().get(i));
        }
        assertEquals(4, numbers, "two scores and two terms");

        dock.frame(scene, snapshot(withoutTerms()), false);
        assertTrue(dock.panel().card().explaining());
        assertTrue(view.lines().stream().noneMatch(line -> line.kind() == ExplainContent.Kind.TERM));
        assertEquals(List.of(ExplainContent.Kind.POLICY, ExplainContent.Kind.CHOSEN, ExplainContent.Kind.WHY,
                ExplainContent.Kind.FLAGS), view.lines().stream().map(ExplainContent.Line::kind).toList());
    }

    @Test
    @DisplayName("the expansion's drag area takes no tap while a window is in front, nor while it is closed")
    void takes_no_tap_when_it_should_not() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        RunLog.Decision decision = withTerms("fight-in-corridors", 7_800);
        dock.frame(scene, snapshot(decision), false);
        ExplainView view = dock.panel().explain();
        assertFalse(view.pane().isActive(), "closed: inactive");
        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot(decision), false);
        assertTrue(view.pane().isActive(), "open, no window: active");
        dock.frame(scene, snapshot(decision), true);
        assertFalse(view.pane().isActive(), "a window in front: inactive");
        assertFalse(dock.press(Hotkey.EXPLAIN, true), "and its key does nothing");
    }
}
