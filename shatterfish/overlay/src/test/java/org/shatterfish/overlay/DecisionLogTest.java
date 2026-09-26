package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
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
import org.shatterfish.harness.agent.ActionContext;
import org.shatterfish.harness.agent.BoundedLog;
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
 * the Action and its score, newest at the bottom, on a real Panel -- and its auto-scroll, the
 * fairness review's synthetic-tap gate, and (the review round) that a row's Action reads the same
 * label the Decision card would and that no row is ever shown half clipped -- which
 * {@code DecisionLogContentTest} cannot hold since all three need a real {@code ScrollPane}.
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

    private static BoundedLog.Entry entry(RunLog.Wait wait) {
        return new BoundedLog.Entry(wait, null);
    }

    private static EmbeddedRun.Snapshot snapshotWith(List<BoundedLog.Entry> history) {
        RunLog.Wait last = (RunLog.Wait) history.get(history.size() - 1).record();
        return new EmbeddedRun.Snapshot(last.decision(), 1, 1, null, EmbeddedRun.State.PLAYING, null, history);
    }

    @Test
    @DisplayName("one line per wait, newest at the bottom, on a real Panel")
    void one_line_per_wait_on_a_real_panel() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        List<BoundedLog.Entry> history = List.of(entry(wait(1, 1000, new Action.Search(), 1_000)),
                entry(wait(2, 2000, new Action.Wait(), 500)));
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

    /**
     * Story 5.4's review round: a row whose wait carries an {@link ActionContext} reads the same
     * label the Decision card would from the equivalent Observation ({@code ActionTextTest.action_context_matches_observation}
     * holds that {@code ActionText}'s two routes agree; this holds that a real {@code DecisionLog}
     * actually threads a row's context there, not only that the pure content function would).
     */
    @Test
    @DisplayName("a row with a captured ActionContext reads a compass direction on the real component, not a raw cell")
    void row_reads_the_card_style_label_when_context_is_captured() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        ActionContext context = new ActionContext(100, 10, List.of(), List.of());
        Action.Step step = new Action.Step(100 - 10);
        RunLog.Wait waitRecord = wait(1, 1000, step, 10_000);
        List<BoundedLog.Entry> history = List.of(new BoundedLog.Entry(waitRecord, context));
        dock.frame(scene, snapshotWith(history), false);
        DecisionLog log = dock.panel().log();

        String rowText = log.lineBlocks().get(log.lineBlocks().size() - 1).text();
        assertTrue(rowText.contains("step N"), rowText);
        assertFalse(rowText.contains(String.valueOf(step.cell())), "not the raw cell: " + rowText);
    }

    @Test
    @DisplayName("auto-scrolls to the newest line while the view was already at the bottom")
    void auto_scrolls_while_at_the_bottom() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        List<BoundedLog.Entry> many = manyWaits(50);
        dock.frame(scene, snapshotWith(many), false);
        DecisionLog log = dock.panel().log();
        // At the bottom already (the default, and this class's own scroll-to-bottom on the first fill).
        float maxScroll = Math.max(0, log.pane().content().height() - log.pane().height());
        assertEquals(maxScroll, log.pane().content().camera.scroll.y, 0.5f, "starts at the bottom");

        List<BoundedLog.Entry> withOneMore = new ArrayList<>(many);
        withOneMore.add(entry(wait(51, 51_000, new Action.Wait(), 1)));
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
        List<BoundedLog.Entry> many = manyWaits(50);
        dock.frame(scene, snapshotWith(many), false);
        DecisionLog log = dock.panel().log();

        log.pane().scrollTo(0, 0);
        float scrolledUpPosition = log.pane().content().camera.scroll.y;
        assertEquals(0, scrolledUpPosition, 0.5f);

        List<BoundedLog.Entry> withOneMore = new ArrayList<>(many);
        withOneMore.add(entry(wait(51, 51_000, new Action.Wait(), 1)));
        dock.frame(scene, snapshotWith(withOneMore), false);

        assertEquals(scrolledUpPosition, log.pane().content().camera.scroll.y, 0.5f,
                "the human's read position is not yanked to the bottom by the new line");
    }

    /**
     * Story 5.4's review round, third pass: the second pass (flooring the viewport to a whole number
     * of a *measured* pitch) was still not enough, because {@code PixelScene.align} snaps each row's
     * own position to a whole device pixel, and a fractional real pitch does not predict where align
     * lands a row far down a long list -- rounding drifts row to row, not by a constant amount.
     *
     * <p>Fourth pass: scrolling to a real row's own top was not enough either, because
     * {@code ScrollPane.layout} casts the viewport height to {@code int} ({@code cs.resize((int)width,
     * (int)height)}) -- so a test that only reads {@code pane.height()} (the un-cast {@code float}
     * field this class sets) cannot see this bug at all; it must read {@code camera.height}, the
     * actual, truncated-or-not integer the render path uses to clip the newest row's own glyphs. This
     * holds what the screen actually shows at both edges: the content height is still not a whole
     * multiple of the nominal pitch (the property that exposed the third pass's own gap), the first
     * visible row is whole at the top (nothing but the row gap between the viewport's own top and
     * that row's own top), and the newest row's own real bottom is whole at the bottom (at or above
     * the viewport's real, {@code camera.height}-clipped bottom, not truncated short of it).
     */
    @Test
    @DisplayName("both edges are whole at the bottom-scrolled position: the first visible row is not clipped from above, and the newest row's real bottom is not clipped from below")
    void both_edges_are_whole_when_scrolled_to_the_bottom() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshotWith(manyWaits(50)), false);
        DecisionLog log = dock.panel().log();

        // The property that broke the first two passes' own fixes: real content, real font, and the
        // real content height this scenario produces is not a whole multiple of the merely nominal
        // pitch (SIZE + ROW_GAP) the first pass floored the viewport to.
        float nominalPitch = DecisionLog.NOMINAL_PITCH;
        float contentHeight = log.pane().content().height();
        float remainderAgainstNominal = contentHeight % nominalPitch;
        assertTrue(remainderAgainstNominal > 0.05f && remainderAgainstNominal < nominalPitch - 0.05f,
                "this scenario's real content height (" + contentHeight + ") is not a nominal-pitch (" + nominalPitch
                        + ") multiple, which is the exact case the first two passes' fixes did not cover");

        // What the screen actually shows: the real, camera.height-clipped viewport, not the un-cast
        // float field pane.height() the second pass's own tests were fooled by.
        float viewportTop = log.pane().content().camera.scroll.y;
        float viewportBottom = viewportTop + log.pane().content().camera.height;

        // The top edge: the first row at or below the viewport's own top is shown whole, not clipped
        // from above -- nothing but the row gap separates it from the viewport's own top.
        float firstVisibleTop = Float.NaN;
        for (float top : log.rowTops()) {
            if (top >= viewportTop - 0.001f) {
                firstVisibleTop = top;
                break;
            }
        }
        assertTrue(!Float.isNaN(firstVisibleTop), "some row is visible at all");
        assertTrue(firstVisibleTop >= viewportTop, "the first visible row's top (" + firstVisibleTop
                + ") is at or below the viewport's own top (" + viewportTop + "), not clipped from above");
        assertTrue(firstVisibleTop - viewportTop < DecisionLog.ROW_GAP, "nothing but the row gap separates "
                + "the viewport's own top (" + viewportTop + ") from the first visible row's own top ("
                + firstVisibleTop + ")");

        // The bottom edge: the newest row's own real bottom (top() + height(), never a nominal pitch
        // or a baseline) is at or above the viewport's own, camera.height-clipped bottom.
        RenderedTextBlock last = log.lineBlocks().get(log.lineBlocks().size() - 1);
        float lastRowBottom = last.top() + last.height();
        assertTrue(lastRowBottom <= viewportBottom + 0.001f, "the newest row's real bottom (" + lastRowBottom
                + ") is at or above the viewport's own bottom (" + viewportBottom + "), not clipped from below");
    }

    /**
     * {@link DecisionLog#viewportHeightFor} directly, against a constructed (non-uniform) set of row
     * tops -- so the algorithm itself is held without needing a real font's real, only-discoverable-
     * by-running-the-game metrics.
     */
    @Test
    @DisplayName("viewportHeightFor picks the largest room that fits, exactly one row's own top away from the content's bottom")
    void viewport_height_for_picks_an_exact_row_boundary() {
        // Four rows, non-uniform heights (9, 11, 9, 10), gaps of 2: tops 0, 11, 24, 35; bottom 45.
        List<Float> rowTops = List.of(0f, 11f, 24f, 35f);
        float contentHeight = 45f;

        // Room for everything: the earliest (topmost) row's own top.
        assertEquals(45f, DecisionLog.viewportHeightFor(rowTops, contentHeight, 100f), 0.001f);
        // Room for exactly the last three rows (11 to 45): row 1's own top is 11 short of the bottom.
        assertEquals(34f, DecisionLog.viewportHeightFor(rowTops, contentHeight, 34f), 0.001f);
        // Less room than that: falls back to the next row boundary that does fit (row 2, 21 short).
        assertEquals(21f, DecisionLog.viewportHeightFor(rowTops, contentHeight, 33f), 0.001f);
        // Less room than even the last row alone needs: shows the last row anyway (never below one).
        assertEquals(10f, DecisionLog.viewportHeightFor(rowTops, contentHeight, 1f), 0.001f);
        // No rows at all: whatever room is offered, floored to at least one pixel.
        assertEquals(50f, DecisionLog.viewportHeightFor(List.of(), contentHeight, 50f), 0.001f);
        assertEquals(1f, DecisionLog.viewportHeightFor(List.of(), contentHeight, 0f), 0.001f);
    }

    /**
     * Story 5.4's review round, fourth pass: a fractional needed height is rounded <em>up</em> to a
     * whole UI pixel, not returned exactly -- {@code ScrollPane.layout}'s own {@code (int)} cast can
     * then only ever round a whole number down to itself, never trim a fraction off the newest row.
     */
    @Test
    @DisplayName("viewportHeightFor rounds a fractional needed height up, never down")
    void viewport_height_for_rounds_a_fractional_height_up() {
        // One row, top 0, real content bottom 43.5 (a real run's own numbers): needed is fractional.
        List<Float> rowTops = List.of(0f);
        assertEquals(44f, DecisionLog.viewportHeightFor(rowTops, 43.5f, 100f), 0.001f);

        // The same, but landing on the "even the last row alone doesn't fit" fallback branch.
        assertEquals(44f, DecisionLog.viewportHeightFor(rowTops, 43.5f, 1f), 0.001f);
    }

    @Test
    @DisplayName("consecutive rows are ROW_GAP apart, not touching")
    void consecutive_rows_have_the_row_gap_between_them() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshotWith(manyWaits(5)), false);
        DecisionLog log = dock.panel().log();

        float rowHeight = log.lineBlocks().get(0).height();
        float gap = log.rowTops().get(1) - log.rowTops().get(0) - rowHeight;
        assertEquals(DecisionLog.ROW_GAP, gap, 0.5f);
    }

    @Test
    @DisplayName("with fewer than three real rows, the content is still at least three lines' worth (UX-DR2)")
    void content_height_has_a_floor_of_three_lines_worth() {
        GameScene scene = fullScene();
        PanelDock dock = new PanelDock();
        dock.frame(scene, snapshotWith(manyWaits(1)), false);
        DecisionLog log = dock.panel().log();

        float rowHeight = log.lineBlocks().get(0).height();
        float expectedMin = DecisionLog.MIN_LINES * (rowHeight + DecisionLog.ROW_GAP) - DecisionLog.ROW_GAP;
        assertEquals(expectedMin, log.pane().content().height(), 0.5f);
    }

    private static List<BoundedLog.Entry> manyWaits(int count) {
        List<BoundedLog.Entry> history = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            history.add(entry(wait(i, i * 1000L, new Action.Search(), i)));
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
        List<BoundedLog.Entry> history = manyWaits(10);
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
