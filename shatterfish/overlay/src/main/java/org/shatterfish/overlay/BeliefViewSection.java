package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Panel's Beliefs view (story 8.6, FR-46): every line of {@link BeliefViewContent}, one
 * {@code RenderedTextBlock} each, inside the game's own {@code ScrollPane}, most ambiguous appearance first.
 * It takes the place of the Belief summary and the Decision log while it is open (the Panel decides), and
 * keeps the place the person scrolled to for as long as the Belief it shows is the same one.
 *
 * <p>The synthetic-tap gate of the Decision log applies here too ({@link DecisionLog}): the pane's own
 * {@code active} follows {@code inputLocked}, so its drag area takes no tap the executor queued for a
 * window's button, and it is inactive whenever the section is not shown.
 */
final class BeliefViewSection extends Component {

    /** Small size ({@code DESIGN.md} Typography: "small 6 (the decision log and probabilities)"). */
    static final int SIZE = 6;
    static final int INK = 0xF0F0F0;
    /** {@code DESIGN.md} colors.ink-muted: the headings. */
    static final int INK_MUTED = 0x9C9C9C;
    static final float ROW_GAP = 2;

    private ScrollPane pane;
    private Component rows;
    private final List<RenderedTextBlock> lineBlocks = new ArrayList<>();
    private BeliefViewContent.Content content = BeliefViewContent.of(null);
    private float innerWidth = -1;

    @Override
    protected void createChildren() {
        rows = new Component();
        pane = new ScrollPane(rows);
        add(pane);
    }

    /**
     * Sets this section from {@code next}, wrapped at {@code innerWidth}; its pane can be dragged or scrolled
     * only when {@code live} (shown, and no window of the game's in front).
     */
    void content(BeliefViewContent.Content next, float innerWidth, boolean live) {
        pane.active = live;
        if (next.equals(content) && innerWidth == this.innerWidth && !lineBlocks.isEmpty()) {
            return;
        }
        boolean keep = BeliefViewContent.keepsPlace(content.hash(), next.hash()) && innerWidth == this.innerWidth;
        float scroll = rows.camera == null ? 0 : rows.camera.scroll.y;
        content = next;
        this.innerWidth = innerWidth;
        rebuild();
        // The same Belief keeps its place (held in range); a new one starts at the top.
        pane.scrollTo(0, keep ? BeliefViewContent.clamp(scroll, rows.height(), pane.height()) : 0);
    }

    /** Not shown: the pane takes no scroll, tap or key. */
    void idle() {
        pane.active = false;
    }

    private void rebuild() {
        List<BeliefViewContent.Line> lines = content.lines();
        while (lineBlocks.size() < lines.size()) {
            RenderedTextBlock block = PixelScene.renderTextBlock(SIZE);
            rows.add(block);
            lineBlocks.add(block);
        }
        int wrapAt = (int) Math.max(1, innerWidth);
        float rowY = 0;
        float bottom = 0;
        for (int i = 0; i < lineBlocks.size(); i++) {
            RenderedTextBlock block = lineBlocks.get(i);
            boolean shown = i < lines.size();
            block.visible = shown;
            if (!shown) {
                block.text("");
                continue;
            }
            block.text(lines.get(i).text(), wrapAt);
            block.hardlight(lines.get(i).heading() ? INK_MUTED : INK);
            block.setPos(0, rowY);
            PixelScene.align(block);
            rowY = block.top() + block.height() + ROW_GAP;
            bottom = block.top() + block.height();
        }
        rows.setSize(Math.max(1, innerWidth), Math.max(1, bottom));
        resizeViewport();
    }

    @Override
    protected void layout() {
        resizeViewport();
    }

    private void resizeViewport() {
        if (pane != null) {
            pane.setRect(x, y, width, Math.max(1, height));
        }
    }

    /** The {@code ScrollPane}, for tests. */
    ScrollPane pane() {
        return pane;
    }

    /** The content last shown, for tests. */
    BeliefViewContent.Content shown() {
        return content;
    }

    /** The line blocks (index order = display order), for tests. */
    List<RenderedTextBlock> lineBlocks() {
        return lineBlocks;
    }
}
