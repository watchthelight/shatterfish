package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Panel's Decision log (story 5.4, FR-38): one line per Input wait with the turn, the actor,
 * the Action and its score, newest at the bottom, on a real Panel -- and its auto-scroll, and the
 * fairness review's synthetic-tap gate, which {@code DecisionLogContentTest} cannot hold since
 * both need a real {@code ScrollPane}.
 */
class DecisionLogTest {

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

    private GameScene fullScene() {
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

    private static RunLog.Wait wait(long k, long turn, Action action, long score) {
        RunLog.Decision decision = new RunLog.Decision("explore: floor",
                new RunLog.Choice(action, score, "reason"), List.of(), List.of(), "policy");
        return new RunLog.Wait(k, turn, 1, 0, "a".repeat(64), java.util.Map.of("hero", "a".repeat(64)), action,
                true, RunLog.BOT, decision, "", List.of(), 0);
    }

    private static EmbeddedRun.Snapshot snapshotWith(List<RunLog> history) {
        RunLog.Wait last = (RunLog.Wait) history.get(history.size() - 1);
        return new EmbeddedRun.Snapshot(last.decision(), 1, 1, null, EmbeddedRun.State.PLAYING, null, history);
    }

    @Test
    @DisplayName("one line per wait, newest at the bottom, on a real Panel")
    void one_line_per_wait_on_a_real_panel() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        List<RunLog> history = List.of(wait(1, 1000, new Action.Search(), 1_000), wait(2, 2000, new Action.Wait(), 500));
        dock.frame(scene, snapshotWith(history), false);
        DecisionLog log = dock.panel().log();

        List<DecisionLogContent.Line> expected = DecisionLogContent.of(history);
        assertEquals(expected, log.lines());
        List<com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock> blocks = log.lineBlocks();
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(blocks.get(i).visible);
            assertEquals(expected.get(i).text(), blocks.get(i).text());
        }
        // Newest at the bottom: the last line's y is the greatest.
        for (int i = 1; i < expected.size(); i++) {
            assertTrue(blocks.get(i).top() > blocks.get(i - 1).top(), "line " + i + " is below line " + (i - 1));
        }
    }

    @Test
    @DisplayName("auto-scrolls to the newest line while the view was already at the bottom")
    void auto_scrolls_while_at_the_bottom() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        List<RunLog> many = manyWaits(50);
        dock.frame(scene, snapshotWith(many), false);
        DecisionLog log = dock.panel().log();
        // At the bottom already (the default, and this class's own scroll-to-bottom on the first fill).
        float maxScroll = Math.max(0, log.pane().content().height() - log.pane().height());
        assertEquals(maxScroll, log.pane().content().camera.scroll.y, 0.5f, "starts at the bottom");

        List<RunLog> withOneMore = new ArrayList<>(many);
        withOneMore.add(wait(51, 51_000, new Action.Wait(), 1));
        dock.frame(scene, snapshotWith(withOneMore), false);

        float newMaxScroll = Math.max(0, log.pane().content().height() - log.pane().height());
        assertEquals(newMaxScroll, log.pane().content().camera.scroll.y, 0.5f,
                "still at the bottom after a new line landed, since it was at the bottom before");
    }

    @Test
    @DisplayName("does not auto-scroll when the human had scrolled up to read history")
    void does_not_yank_a_scrolled_up_view_to_the_bottom() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        List<RunLog> many = manyWaits(50);
        dock.frame(scene, snapshotWith(many), false);
        DecisionLog log = dock.panel().log();

        log.pane().scrollTo(0, 0);
        float scrolledUpPosition = log.pane().content().camera.scroll.y;
        assertEquals(0, scrolledUpPosition, 0.5f);

        List<RunLog> withOneMore = new ArrayList<>(many);
        withOneMore.add(wait(51, 51_000, new Action.Wait(), 1));
        dock.frame(scene, snapshotWith(withOneMore), false);

        assertEquals(scrolledUpPosition, log.pane().content().camera.scroll.y, 0.5f,
                "the human's read position is not yanked to the bottom by the new line");
    }

    private static List<RunLog> manyWaits(int count) {
        List<RunLog> history = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            history.add(wait(i, i * 1000L, new Action.Search(), i));
        }
        return List.copyOf(history);
    }

    /**
     * The fairness review of story 5.3's Explain, applied to the Decision log's own hot area (the
     * design note "The synthetic-tap gate"): {@code ActionExecutor.press} queues a synthetic tap
     * past {@code InputLock}, so the log's {@code ScrollPane} must be inactive whenever such a tap
     * could land on it, not only invisible. Reproduced exactly as
     * {@code PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked} does, at the
     * log's own position instead of Explain's.
     */
    @Test
    @DisplayName("the Decision log's ScrollPane does not steal a synthetic tap while input is locked")
    void does_not_steal_a_synthetic_tap_while_locked() {
        PointerEvent.clearListeners();
        GameScene scene = fullScene();

        boolean[] clicked = {false};
        RedButton underlying = new RedButton("Continue") {
            @Override
            protected void onClick() {
                clicked[0] = true;
            }
        };
        underlying.camera = PixelScene.uiCamera;
        scene.add(underlying);

        PanelDock dock = new PanelDock();
        List<RunLog> history = manyWaits(10);
        dock.frame(scene, snapshotWith(history), true);   // a Run is playing: input is locked
        DecisionLog log = dock.panel().log();
        assertFalse(log.pane().active, "the log's ScrollPane is not active while input is locked");

        underlying.setRect(log.pane().left(), log.pane().top(), log.pane().width(), log.pane().height());
        press(underlying);

        assertTrue(clicked[0], "the tap reached the window's own button underneath, not the log's drag area");
    }

    /** {@code ActionExecutor.press}, reproduced: a synthetic DOWN and UP queued directly as {@code PointerEvent}s. */
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
