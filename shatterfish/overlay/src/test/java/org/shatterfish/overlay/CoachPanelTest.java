package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
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
import org.shatterfish.overlay.ModeState.Mode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coach mode on a real play scene (story 8.9, FR-47, UJ-4): the Controls row's Coach button and its hotkey switch
 * the coach in HUMAN and are dim, with the reason in the Mode strip, in RUNNING, PAUSED and a Run that is over; the
 * Decision card of a person's Run shows the sentence only with the coach on and only against the wait the person
 * is looking at, and a Brain's Run shows it as before.
 */
class CoachPanelTest {

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

    private static RunLog.Decision decision() {
        RunLog.Choice chosen = new RunLog.Choice(new Action.Search(), 7_800, "explore: frontier 7");
        return new RunLog.Decision("explore", chosen, List.of(), List.of(), "explore");
    }

    /** A person's Run at a wait: the shadow is for the wait in front or for one already taken. */
    private static EmbeddedRun.Snapshot human(boolean current) {
        return new EmbeddedRun.Snapshot(decision(), 14, 2, null, EmbeddedRun.State.PLAYING, null, List.of(),
                new EmbeddedRun.Human(current, 3, true, 0, "", 0));
    }

    private static EmbeddedRun.Snapshot brain() {
        return new EmbeddedRun.Snapshot(decision(), 14, 2, null, EmbeddedRun.State.PLAYING);
    }

    private static void tap(Component button) {
        Camera camera = button.camera();
        Point screen = camera.cameraToScreen(button.left() + button.width() / 2, button.top() + button.height() / 2);
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
        PointerEvent.processPointerEvents();
    }

    /** The dock with a coach that records what the Run is told. */
    private static PanelDock dock(List<Boolean> told) {
        PanelDock dock = new PanelDock();
        dock.coach(new CoachState(on -> told.add(on)));
        return dock;
    }

    @Test
    @DisplayName("in a person's Run the sentence is hidden until the coach is on, then shows against the wait in front")
    void the_sentence_follows_the_switch() {
        GameScene scene = scene();
        List<Boolean> told = new ArrayList<>();
        PanelDock dock = dock(told);
        dock.frame(scene, human(true), false);
        DecisionCard card = dock.panel().card();
        assertFalse(card.coachRow().visible, "off by default: no advice nobody asked for");
        assertFalse(dock.panel().controls().coachActs() && dock.panel().controls().coach().on());

        assertTrue(dock.press(Hotkey.COACH, false), "the hotkey switches it on in HUMAN");
        dock.frame(scene, human(true), false);
        assertTrue(card.coachRow().visible);
        assertEquals(CoachLine.of(decision(), null), card.coachRow().text(), "the sentence is the pure function's");
        assertEquals(ControlsRow.COACH_ON, dock.panel().controls().coachButton().text());

        assertTrue(dock.press(Hotkey.COACH, false));
        dock.frame(scene, human(true), false);
        assertFalse(card.coachRow().visible);
        assertEquals(ControlsRow.COACH_OFF, dock.panel().controls().coachButton().text());
        assertEquals(List.of(true, false), told, "each switch told the Run once, for the Mode record");
    }

    @Test
    @DisplayName("advice for a wait the person has already taken is never shown against the one in front")
    void late_advice_is_not_shown() {
        GameScene scene = scene();
        PanelDock dock = dock(new ArrayList<>());
        dock.frame(scene, human(true), false);
        assertTrue(dock.press(Hotkey.COACH, false));
        dock.frame(scene, human(true), false);
        assertTrue(dock.panel().card().coachRow().visible);
        // The next wait has opened and its shadow has not come: the card holds the last one, not current.
        dock.frame(scene, human(false), false);
        assertFalse(dock.panel().card().coachRow().visible, "the coach is on and the sentence is not for this wait");
        assertTrue(dock.panel().controls().coach().on(), "and the coach stays on for the wait whose advice does arrive");
        dock.frame(scene, human(true), false);
        assertTrue(dock.panel().card().coachRow().visible);
    }

    @Test
    @DisplayName("the button is the hotkey's twin: a tap switches it, and a Brain's Run keeps showing its own sentence")
    void a_tap_and_a_brains_card() {
        GameScene scene = scene();
        List<Boolean> told = new ArrayList<>();
        PanelDock dock = dock(told);
        dock.frame(scene, human(true), false);
        Component button = dock.panel().controls().coachButton();
        assertTrue(button.active);
        tap(button);
        assertEquals(List.of(true), told);
        dock.frame(scene, human(true), false);
        assertTrue(dock.panel().card().coachRow().visible);

        // A Brain's Run is not a person's: the sentence is the card's, as story 8.8 made it, and the coach is off.
        dock.frame(scene, brain(), false);
        assertTrue(dock.panel().card().coachRow().visible, "the Brain's card shows its sentence");
        assertFalse(dock.panel().controls().coach().on(), "and the switch went off with the Mode");
        assertFalse(dock.panel().controls().coachButton().active);
    }

    @Test
    @DisplayName("in RUNNING, PAUSED and a Run that is over the button is dim, a press does nothing, and the strip says why")
    void disabled_with_the_reason() {
        GameScene scene = scene();
        List<Boolean> told = new ArrayList<>();
        PanelDock dock = dock(told);
        dock.frame(scene, brain(), false);
        Panel panel = dock.panel();
        assertFalse(panel.controls().coachButton().active, "RUNNING");
        assertFalse(dock.press(Hotkey.COACH, false));
        dock.frame(scene, brain(), false);
        assertEquals(ControlEnablement.COACH_BOT_PLAYS, panel.stripText().text(), "the strip says why for a moment");
        tap(panel.controls().coachButton());
        assertTrue(told.isEmpty(), "a press on a dim button reaches nothing");

        // PAUSED and over are the Mode strip's states; the row draws them from the Mode it is given.
        for (Mode mode : new Mode[] {Mode.PAUSED, Mode.HUMAN}) {
            boolean over = mode == Mode.HUMAN;
            ModeState state = new ModeState(mode, ModeState.SpeedMode.NEXT_STEP, 1.0, 14, 2, false, over);
            panel.content(state, decision(), null, false, null, List.of());
            assertFalse(panel.controls().coachActs(), mode + (over ? " over" : ""));
            assertNotNull(panel.controls().coachReason(), "a reason in words");
        }
        assertEquals(ControlEnablement.COACH_PAUSED, ControlEnablement.coachReason(Mode.PAUSED, false));
        assertTrue(told.isEmpty());
    }

    @Test
    @DisplayName("with a window of the game's in front, the coach hotkey and button do nothing")
    void a_window_in_front() {
        GameScene scene = scene();
        List<Boolean> told = new ArrayList<>();
        PanelDock dock = dock(told);
        dock.frame(scene, human(true), true);
        assertFalse(dock.panel().controls().coachButton().active);
        assertFalse(dock.press(Hotkey.COACH, true));
        assertTrue(told.isEmpty());
    }

    @Test
    @DisplayName("the row still fits: no button runs off it, and the coach is on the row with the others")
    void the_row_fits() {
        GameScene scene = scene();
        PanelDock dock = dock(new ArrayList<>());
        dock.frame(scene, human(true), false);
        ControlsRow row = dock.panel().controls();
        assertTrue(row.coachButton().visible);
        assertTrue(row.coachButton().right() <= row.right() + 0.01f);
        assertTrue(row.rows() >= 2 && row.rows() <= 4, "rows: " + row.rows());
        assertTrue(row.bottom() <= dock.panel().bottom() + 0.01f);
    }
}
