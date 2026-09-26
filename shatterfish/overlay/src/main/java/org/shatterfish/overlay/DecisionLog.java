package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Panel's Decision log (story 5.4, FR-38): one line per Input wait, with the turn, the actor,
 * the Action and its score ({@link DecisionLogContent}), newest at the bottom, inside the game's
 * own {@code ScrollPane} ({@code DESIGN.md} Components: "a {@code ScrollPane} with the newest line
 * at the bottom"). It auto-scrolls to the newest line only while the view was already at the
 * bottom before this call -- a human who scrolled up to read history is not yanked back down by
 * the next wait landing.
 *
 * <p><b>The synthetic-tap gate (the fairness review of story 5.3's Explain, applied here too).</b>
 * {@code ActionExecutor.press} queues a synthetic tap as a {@code PointerEvent} directly, past
 * {@code InputLock} entirely; a {@code ScrollPane}'s drag area ({@code ScrollPane.PointerController},
 * a {@code PointerArea}) is exactly the kind of hot area the review named as needing the same
 * gate Explain got, not a special case of its own. This pane's own {@code active} is set from
 * {@code inputLocked} every {@link #content}; {@code Gizmo.isActive()} walks the parent chain, so
 * the controller (this pane's child) reads inactive whenever this does, and its {@code PointerArea}
 * (default {@code blockLevel} is {@code BLOCK_WHEN_ACTIVE}) then lets any tap fall through rather
 * than consuming it ({@code PointerArea.java:58-62}). {@code DecisionLogTest} holds this the same
 * way {@code PanelContentTest.explain_does_not_steal_a_synthetic_tap_while_locked} holds Explain's.
 */
final class DecisionLog extends Component {

    /** Small size ({@code DESIGN.md} Typography: "small 6 (the decision log and probabilities)"). */
    static final int SIZE = 6;
    /** {@code DESIGN.md} colors.ink: the current (newest) line. */
    static final int INK = 0xF0F0F0;
    /** {@code DESIGN.md} colors.ink-muted: every earlier line. */
    static final int INK_MUTED = 0x9C9C9C;
    static final float ROW_GAP = 2;
    /** UX-DR2 / {@code PanelLayout.MIN_PANEL_HEIGHT}: this pane is never given less room than three lines. */
    static final int MIN_LINES = 3;

    private ScrollPane pane;
    private Component rows;
    private final List<RenderedTextBlock> lineBlocks = new ArrayList<>();
    private List<DecisionLogContent.Line> lastLines = List.of();
    private float lastInnerWidth = -1;

    @Override
    protected void createChildren() {
        rows = new Component();
        pane = new ScrollPane(rows);
        add(pane);
    }

    /**
     * Sets this log from {@code history} ({@link DecisionLogContent#of}), wrapped at
     * {@code innerWidth}; active (its {@code ScrollPane} can be dragged or scrolled) only when
     * {@code inputLocked} is false.
     */
    void content(List<RunLog> history, float innerWidth, boolean inputLocked) {
        pane.active = !inputLocked;
        List<DecisionLogContent.Line> lines = DecisionLogContent.of(history);
        if (lines.equals(lastLines) && innerWidth == lastInnerWidth) {
            return;
        }
        boolean wasAtBottom = lastLines.isEmpty() || atBottom();
        lastInnerWidth = innerWidth;
        rebuild(lines, innerWidth);
        lastLines = lines;
        if (wasAtBottom) {
            scrollToBottom();
        }
    }

    private void rebuild(List<DecisionLogContent.Line> lines, float innerWidth) {
        while (lineBlocks.size() < lines.size()) {
            RenderedTextBlock block = PixelScene.renderTextBlock(SIZE);
            rows.add(block);
            lineBlocks.add(block);
        }
        int wrapAt = (int) Math.max(1, innerWidth);
        for (int i = 0; i < lineBlocks.size(); i++) {
            RenderedTextBlock block = lineBlocks.get(i);
            boolean shown = i < lines.size();
            block.visible = shown;
            if (shown) {
                block.text(lines.get(i).text(), wrapAt);
                block.hardlight(i == lines.size() - 1 ? INK : INK_MUTED);
            } else {
                block.text("");
            }
        }
        float rowY = 0;
        for (int i = 0; i < lines.size(); i++) {
            RenderedTextBlock block = lineBlocks.get(i);
            block.setPos(0, rowY);
            PixelScene.align(block);
            rowY += block.height() + ROW_GAP;
        }
        float contentHeight = lines.isEmpty() ? 0 : rowY - ROW_GAP;
        rows.setSize(innerWidth, Math.max(minHeight(), contentHeight));
    }

    /** Three lines' worth at the small size, rows two apart (matches {@code PanelLayout.MIN_PANEL_HEIGHT}). */
    private float minHeight() {
        return MIN_LINES * (SIZE + ROW_GAP) - ROW_GAP;
    }

    @Override
    protected void layout() {
        if (pane == null) {
            return;
        }
        pane.setRect(x, y, width, height);
    }

    /** Whether the view was scrolled all the way down before the lines this call is about to show. */
    private boolean atBottom() {
        float max = Math.max(0, rows.height() - pane.height());
        return rows.camera == null || rows.camera.scroll.y >= max - 0.5f;
    }

    private void scrollToBottom() {
        pane.scrollTo(0, Math.max(0, rows.height() - pane.height()));
    }

    /** The {@code ScrollPane}, for tests. */
    ScrollPane pane() {
        return pane;
    }

    /** The line blocks (index order = display order), for tests. */
    List<RenderedTextBlock> lineBlocks() {
        return lineBlocks;
    }

    /** The lines last shown, for tests. */
    List<DecisionLogContent.Line> lines() {
        return lastLines;
    }
}
