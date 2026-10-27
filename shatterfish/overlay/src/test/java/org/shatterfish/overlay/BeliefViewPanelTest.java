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
import org.shatterfish.api.BeliefSummary;
import org.shatterfish.api.BeliefView;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Beliefs section of the Panel (story 8.6, FR-46) on the game's own toolkit: opened by the F12 hotkey and by
 * its button, in place of the Belief summary and the Decision log, available in HUMAN (and PAUSED, whose matrix
 * {@code BeliefViewContentTest} holds), closed in RUNNING, scrollable, and inert under a window of the game's.
 */
class BeliefViewPanelTest {

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

    private static BeliefView view(String hash, int appearances) {
        List<BeliefView.Appearance> list = new ArrayList<>();
        for (int i = 0; i < appearances; i++) {
            list.add(new BeliefView.Appearance("potion " + i, List.of(new BeliefView.Candidate("healing", 0.4),
                    new BeliefView.Candidate("frost", 0.35), new BeliefView.Candidate("strength", 0.25))));
        }
        return new BeliefView(hash, list, List.of("potion of invisibility (pool room)"), List.of(), List.of());
    }

    private static BeliefSummary summary() {
        return new BeliefSummary(List.of(new BeliefSummary.Item("potion 0", "healing", 0.4)), List.of(), List.of());
    }

    /** A person's Run (HUMAN), or a Brain's Run (RUNNING) when {@code human} is false, showing {@code view}. */
    private static EmbeddedRun.Snapshot snapshot(BeliefView view, boolean human) {
        return new EmbeddedRun.Snapshot(null, 14, 1, null, EmbeddedRun.State.PLAYING, summary(), List.of(),
                human ? new EmbeddedRun.Human(true, 2, true, 0, "", 0) : null, false, null, List.of(), null, null, view);
    }

    @Test
    @DisplayName("F12 opens the view in HUMAN in place of the summary and the log; F12 again closes it")
    void the_hotkey_opens_and_closes() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        EmbeddedRun.Snapshot snapshot = snapshot(view("h1", 12), true);
        dock.frame(scene, snapshot, false);
        Panel panel = dock.panel();
        assertTrue(panel.belief().visible && panel.log().visible, "closed: the summary and the log are the Panel's");
        assertFalse(panel.beliefs().visible);
        assertTrue(panel.controls().acts(ControlEnablement.Control.BELIEFS), "the button acts in HUMAN");

        assertTrue(dock.press(Hotkey.BELIEFS, false));
        dock.frame(scene, snapshot, false);

        assertTrue(panel.beliefsShown());
        assertTrue(panel.beliefs().visible);
        assertFalse(panel.belief().visible || panel.log().visible, "it takes their place");
        assertEquals(BeliefViewContent.of(snapshot.beliefView()), panel.beliefs().shown());
        assertEquals(BeliefViewContent.of(snapshot.beliefView()).lines().size(),
                panel.beliefs().lineBlocks().stream().filter(b -> b.visible).count());

        assertTrue(dock.press(Hotkey.BELIEFS, false));
        dock.frame(scene, snapshot, false);
        assertFalse(panel.beliefsShown());
        assertTrue(panel.belief().visible && panel.log().visible);
    }

    @Test
    @DisplayName("the Beliefs button does what the key does, and a window of the game's in front stops both")
    void the_button_and_the_lock() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        EmbeddedRun.Snapshot snapshot = snapshot(view("h1", 3), true);
        dock.frame(scene, snapshot, true);
        assertFalse(dock.press(Hotkey.BELIEFS, true), "locked: the hotkey does nothing");
        assertFalse(dock.panel().controls().acts(ControlEnablement.Control.BELIEFS));
        assertFalse(dock.panel().controls().beliefs().active);

        dock.frame(scene, snapshot, false);
        assertTrue(dock.panel().controls().beliefs().active);
        press(dock.panel().controls().beliefs());
        dock.frame(scene, snapshot, false);
        assertTrue(dock.panel().beliefsShown());

        dock.frame(scene, snapshot, true);
        assertTrue(dock.panel().beliefsShown(), "still on screen under a window");
        assertFalse(dock.panel().beliefs().pane().active, "but its pane takes no tap a window's button should get");
        dock.frame(scene, snapshot, false);
        assertTrue(dock.panel().beliefs().pane().active);
    }

    @Test
    @DisplayName("RUNNING closes the view, and it does not come back by itself at the next pause")
    void running_closes_it() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshot(view("h1", 3), true), false);
        dock.press(Hotkey.BELIEFS, false);
        dock.frame(scene, snapshot(view("h1", 3), true), false);
        assertTrue(dock.panel().beliefsShown());

        EmbeddedRun.Snapshot running = snapshot(view("h1", 3), false);
        dock.frame(scene, running, false);
        assertFalse(dock.panel().beliefsShown(), "the Brain moves on: no view of one wait");
        assertFalse(dock.press(Hotkey.BELIEFS, false), "and the key does nothing while RUNNING");

        dock.frame(scene, snapshot(view("h1", 3), true), false);
        assertFalse(dock.panel().beliefsShown(), "it was closed, not hidden");
    }

    @Test
    @DisplayName("long views scroll; the same Belief keeps the place, a new one starts at the top, and the end is held")
    void scrolling() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        EmbeddedRun.Snapshot first = snapshot(view("h1", 80), true);
        dock.frame(scene, first, false);
        dock.press(Hotkey.BELIEFS, false);
        dock.frame(scene, first, false);
        BeliefViewSection section = dock.panel().beliefs();
        float content = section.pane().content().height();
        float viewport = section.pane().height();
        assertTrue(content > viewport, "80 appearances do not fit: " + content + " in " + viewport);

        section.pane().scrollTo(0, 30);
        assertEquals(30, section.pane().content().camera.scroll.y, 0.01);
        dock.frame(scene, first, false);
        assertEquals(30, section.pane().content().camera.scroll.y, 0.01, "the same Belief keeps the place");

        section.pane().scrollTo(0, 100000);
        assertEquals(BeliefViewContent.maxScroll(content, viewport), section.pane().content().camera.scroll.y, 0.5,
                "the end is where it stops");

        dock.frame(scene, snapshot(view("h2", 80), true), false);
        assertEquals(0, section.pane().content().camera.scroll.y, 0.01, "a new Belief starts at the top");
    }

    @Test
    @DisplayName("the Beliefs view and the Explain expansion each take the room below the card, so opening one closes the other")
    void one_at_a_time_with_explain() {
        GameScene scene = scene();
        PanelDock dock = new PanelDock();
        org.shatterfish.api.RunLog.Decision decision = new org.shatterfish.api.RunLog.Decision("fight: gnoll",
                new org.shatterfish.api.RunLog.Choice(new org.shatterfish.api.Action.Attack(42), 7_800, "corridor"),
                List.of(), List.of(), "fight-in-corridors");
        EmbeddedRun.Snapshot snapshot = new EmbeddedRun.Snapshot(decision, 14, 1, null, EmbeddedRun.State.PLAYING,
                summary(), List.of(), new EmbeddedRun.Human(true, 2, true, 0, "", 0), false, null, List.of(), null, null,
                view("h1", 4));
        dock.frame(scene, snapshot, false);
        Panel panel = dock.panel();

        assertTrue(dock.press(Hotkey.BELIEFS, false));
        dock.frame(scene, snapshot, false);
        assertTrue(panel.beliefsShown());

        assertTrue(dock.press(Hotkey.EXPLAIN, false));
        dock.frame(scene, snapshot, false);
        assertTrue(panel.card().explaining());
        assertFalse(panel.beliefsShown(), "Explain took the room");

        assertTrue(dock.press(Hotkey.BELIEFS, false));
        dock.frame(scene, snapshot, false);
        assertTrue(panel.beliefsShown());
        assertFalse(panel.card().explaining(), "and the Beliefs view took it back");
    }

    /** {@code ActionExecutor.press}, reproduced: a synthetic DOWN and UP queued as {@code PointerEvent}s, as PanelContentTest does. */
    private static void press(Component button) {
        Camera camera = button.camera();
        float x = button.left() + button.width() / 2;
        float y = button.top() + button.height() / 2;
        Point screen = camera.cameraToScreen(x, y);
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT));
        PointerEvent.addPointerEvent(new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT));
        PointerEvent.processPointerEvents();
    }
}
