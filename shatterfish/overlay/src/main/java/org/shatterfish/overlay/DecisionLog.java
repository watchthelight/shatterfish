package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.watabou.noosa.ui.Component;
import org.shatterfish.harness.agent.BoundedLog;

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
 *
 * <p><b>Review round (first pass).</b> Each {@link org.shatterfish.harness.agent.BoundedLog.Entry}
 * carries the {@link org.shatterfish.harness.agent.ActionContext} {@link ActionText} needs to label
 * its Action the way the Decision card does (a compass direction, a named target), rather than the
 * raw cell a null context falls back to.
 *
 * <p><b>Review round (second pass, real Action labels): a guessed pitch was still not enough.</b>
 * Flooring the viewport to a whole number of {@code SIZE + ROW_GAP} (a nominal pitch) did not fix
 * the clipped top row the review's own screenshot still showed, because the pitch itself was a
 * guess in two different ways: a bitmap or TTF font's real line height at a given point size is a
 * metric of the font, not necessarily the point size itself, <em>and</em> {@code PixelScene.align}
 * snaps every row's own position to a whole device pixel, so a fractional real pitch (the actually
 * measured one, not the nominal one) does not even predict where {@code align} lands a row far down
 * a long list -- rounding drifts row to row, not by a constant amount.
 *
 * <p><b>Third pass: read the real, aligned positions back, never predict them.</b> {@link #rebuild}
 * keeps {@link #rowTops} -- each row's own top, exactly as {@code PixelScene.align} actually left it
 * after positioning row {@code i} directly beneath row {@code i - 1}'s own real bottom (not at a
 * formula's {@code i * pitch}) -- and sizes the content to the last real row's own bottom, no
 * trailing gap. {@link #layout} then picks the viewport's height, not by flooring to a guessed
 * pitch, but by finding the {@link #rowTops} entry that is itself the top of whichever row would be
 * topmost within the room available, and setting the viewport to exactly the room from there to the
 * content's own bottom: a bottom scroll (content height minus viewport height) then lands on that
 * exact, already-real position by construction, not by hoping two guesses about the pitch agree.
 *
 * <p><b>Fourth pass: the height itself was still being truncated, one layer down.</b> The third
 * pass's own viewport height ({@code contentHeight - top}) is usually fractional, and the upstream
 * {@code ScrollPane.layout} casts it to {@code int} ({@code cs.resize((int)width, (int)height)}),
 * truncating toward zero -- a cast this class's own code never made, and so never noticed. A real
 * run's own numbers show it: content height 329.5, the chosen row's top 286.0, needed height
 * 43.5 -- cast to 43, one half of a UI pixel short, off the <em>bottom</em> (the scroll itself is a
 * plain {@code float} field the pane never casts, which is exactly why the third pass's fix held for
 * the top edge and not this one). {@link #viewportHeightFor} now rounds that height <em>up</em>
 * ({@code Math.ceil}) rather than returning it exactly: the {@code int} cast can then only ever
 * round a whole number down to itself, never trim a fraction away. {@link #scrollToBottom} still
 * derives its scroll from {@code contentHeight - viewportHeight}, unchanged, and {@code
 * ScrollPane.scrollTo}'s own clamp (the same subtraction, done again on its side) lands the scroll a
 * fraction of a UI pixel above the chosen row's exact top rather than exactly on it -- inside the
 * {@link #ROW_GAP} before that row, never inside the previous row's own glyphs, since a rounding
 * remainder under one UI pixel is always smaller than a two-pixel gap. Both edges follow from the
 * one number changing, not from tracking a second one to keep in step with it.
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
    /** The pitch nothing has been rendered against yet (before the first {@link #rebuild}): {@link #SIZE} plus the gap, the same guess {@code PanelLayout.MIN_PANEL_HEIGHT} makes. */
    static final float NOMINAL_PITCH = SIZE + ROW_GAP;

    private ScrollPane pane;
    private Component rows;
    private final List<RenderedTextBlock> lineBlocks = new ArrayList<>();
    private List<DecisionLogContent.Line> lastLines = List.of();
    private float lastInnerWidth = -1;
    /** Each visible row's own top, exactly as {@code PixelScene.align} left it -- read back, never predicted. */
    private final List<Float> rowTops = new ArrayList<>();

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
    void content(List<BoundedLog.Entry> history, float innerWidth, boolean inputLocked) {
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
                // A shadow line is greyed wherever it stands: it was never executed (story 5.9).
                block.hardlight(i == lines.size() - 1 && !lines.get(i).greyed() ? INK : INK_MUTED);
            } else {
                block.text("");
            }
        }
        // Row i placed directly beneath row i-1's own real, already-aligned bottom (never at a
        // formula's i * pitch): whatever PixelScene.align actually does to one row's position is
        // folded into where the next one starts, so rowTops is the ground truth, not a prediction.
        rowTops.clear();
        float rowY = 0;
        float contentBottom = 0;
        for (int i = 0; i < lines.size(); i++) {
            RenderedTextBlock block = lineBlocks.get(i);
            block.setPos(0, rowY);
            PixelScene.align(block);
            rowTops.add(block.top());
            rowY = block.top() + block.height() + ROW_GAP;
            contentBottom = block.top() + block.height();
        }
        rows.setSize(innerWidth, Math.max(minHeight(), contentBottom));
        // The framework's own layout() (called by Panel.layout(), before content() -- design note
        // "Content before layout, except for the log") sized the viewport from whatever rowTops
        // existed *before* this rebuild -- empty on the very first fill, stale on every one after.
        // Re-applied here, now that rowTops is this frame's real, freshly measured ground truth, so
        // the scrollToBottom() call right after this returns is never sized from yesterday's rows.
        resizeViewport();
    }

    /** Three lines' worth, at the best pitch estimate available (the first real row's, once there is one; the nominal guess otherwise). */
    private float minHeight() {
        float pitch = lineBlocks.isEmpty() || !lineBlocks.get(0).visible
                ? NOMINAL_PITCH : lineBlocks.get(0).height() + ROW_GAP;
        return MIN_LINES * pitch - ROW_GAP;
    }

    @Override
    protected void layout() {
        resizeViewport();
    }

    /**
     * Sizes the {@code ScrollPane}'s own viewport from this log's own outer rect ({@code x, y,
     * width, height}, whatever {@code Panel.layout()} last gave it) and its current
     * {@link #rowTops}. Called both by the framework's own {@link #layout} (an outer-rect change
     * with no new rows -- a screen resize) and again at the end of {@link #rebuild} (new rows, the
     * same outer rect): the two can happen in either order within one {@code Panel.content()} call
     * (design note "Content before layout, except for the log"), and only the one that runs *after*
     * {@link #rebuild} has this frame's real rowTops rather than the previous frame's.
     *
     * <p>Fourth pass: {@link #viewportHeightFor} now rounds up. {@link #scrollToBottom} still
     * derives the scroll from {@code rows.height() - pane.height()}, and {@code ScrollPane.scrollTo}
     * clamps that same way on its own -- but with the height rounded up, the two agree, and the
     * clamp's own arithmetic (not a second, independent number this class would have to keep in
     * step) is what lands the scroll a whisper above the chosen row's exact top: comfortably inside
     * the {@link #ROW_GAP} before it (never inside the previous row's own glyphs, since the rounding
     * involved is under one UI pixel and the gap is two), while the viewport's bottom, no longer
     * truncated short, reaches the newest row's own real bottom exactly.
     */
    private void resizeViewport() {
        if (pane == null) {
            return;
        }
        pane.setRect(x, y, width, viewportHeightFor(rowTops, rows.height(), height));
    }

    /**
     * The viewport height to give the {@code ScrollPane}, so that a bottom scroll
     * ({@code contentHeight - viewportHeight}) shows the chosen row's whole content, all the way to
     * the content's real bottom: among {@code rowTops} (ascending), the one whose distance to
     * {@code contentHeight} is the largest that still fits within {@code available} -- the topmost
     * row that can be shown whole, showing as much history as the room allows, never fewer than one
     * row's worth ({@code contentHeight - rowTops[last]}, the whole point of {@code rowTops} being
     * non-empty) -- rounded <em>up</em> to a whole UI pixel (fourth pass): the upstream
     * {@code ScrollPane.layout} casts this height to {@code int}, which truncates a fractional
     * height toward zero, and truncating down is exactly what left the newest row's own lower
     * fraction unshown. Rounding up instead can only ever add a sliver of empty room below the
     * newest row, never cut it. A pure function of the real positions {@link #rebuild} already
     * found (for {@code DecisionLogTest} to hold directly against a constructed list);
     * {@link #resizeViewport} always calls it with this log's own {@link #rowTops}, never a
     * formula's guess at them.
     */
    static float viewportHeightFor(List<Float> rowTops, float contentHeight, float available) {
        if (rowTops.isEmpty()) {
            return Math.max(1, available);
        }
        for (float top : rowTops) {
            float needed = contentHeight - top;
            if (needed <= available) {
                return Math.max(1, (float) Math.ceil(needed));
            }
        }
        return Math.max(1, (float) Math.ceil(contentHeight - rowTops.get(rowTops.size() - 1)));
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

    /** Each visible row's own real top (oldest first), for tests. */
    List<Float> rowTops() {
        return rowTops;
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
