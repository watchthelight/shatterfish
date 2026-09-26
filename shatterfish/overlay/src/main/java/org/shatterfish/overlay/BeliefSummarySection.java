package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.BeliefSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * The Panel's Belief summary section (story 5.4, FR-38): unknown items with their top candidate
 * and probability, then floor facts, then chapter counters, one line each; always shows something
 * while the Panel is full, the same discipline the Decision card holds (UX-DR14: a state stated in
 * words, never a blank space) -- {@link BeliefSummaryContent#NOTHING_YET} before the first Decision
 * or when the Brain currently has nothing to report.
 *
 * <p>Every line is one {@code RenderedTextBlock}, body size, ink: no pixel columns (unlike the
 * Decision card's rows), since this story's own acceptance criteria do not ask for one and each
 * line's probability is already the last thing on it, not a value compared down a column of other
 * rows the way the Decision card's scores are (UX-DR5 is about numbers stacked in one column; here
 * every row's number is at a different position because the words before it differ in length,
 * which is fine for a value nobody is meant to compare row to row).
 */
final class BeliefSummarySection extends Component {

    /** Body size ({@code DESIGN.md} Typography: "body 8 (the decision card, controls, beliefs)"). */
    static final int SIZE = 8;
    static final int INK = 0xF0F0F0;
    static final float ROW_GAP = 2;

    private final List<RenderedTextBlock> lineBlocks = new ArrayList<>();
    private BeliefSummaryContent.Content content = BeliefSummaryContent.of(null);
    private float innerWidth = 1;

    /** Sets this section from {@code summary} (may be null), wrapped at {@code innerWidth}. */
    void content(BeliefSummary summary, float innerWidth) {
        this.innerWidth = Math.max(1, innerWidth);
        content = BeliefSummaryContent.of(summary);
        List<String> lines = allLines();
        while (lineBlocks.size() < lines.size()) {
            RenderedTextBlock block = PixelScene.renderTextBlock(SIZE);
            block.hardlight(INK);
            add(block);
            lineBlocks.add(block);
        }
        int width = (int) this.innerWidth;
        for (int i = 0; i < lineBlocks.size(); i++) {
            boolean shown = i < lines.size();
            lineBlocks.get(i).visible = shown;
            lineBlocks.get(i).text(shown ? lines.get(i) : "", width);
        }
    }

    /** {@code content}'s lines, in the order this section shows them: items, then floor, then chapters. */
    private List<String> allLines() {
        if (!content.present()) {
            return List.of(BeliefSummaryContent.NOTHING_YET);
        }
        List<String> lines = new ArrayList<>(content.items());
        lines.addAll(content.floor());
        lines.addAll(content.chapters());
        return lines;
    }

    /**
     * The height {@link #content} needs at the width it was given, before this is positioned. Only
     * as many lines as {@link #content} has already built blocks for: {@code Panel.place} lays out
     * the Panel once before the first {@link #content} call ever runs (its own first frame), and
     * this must not throw on that call.
     */
    float contentHeight() {
        List<String> lines = allLines();
        int shown = Math.min(lines.size(), lineBlocks.size());
        float height = 0;
        for (int i = 0; i < shown; i++) {
            height += lineBlocks.get(i).height() + ROW_GAP;
        }
        return height > 0 ? height - ROW_GAP : 0;
    }

    @Override
    protected void layout() {
        if (lineBlocks.isEmpty()) {
            return;
        }
        float rowY = y;
        List<String> lines = allLines();
        int shown = Math.min(lines.size(), lineBlocks.size());
        for (int i = 0; i < shown; i++) {
            RenderedTextBlock block = lineBlocks.get(i);
            block.setPos(x, rowY);
            PixelScene.align(block);
            rowY += block.height() + ROW_GAP;
        }
    }

    /** This section's own content, for tests. */
    BeliefSummaryContent.Content shown() {
        return content;
    }

    /** The line blocks actually shown (visible), in order, for tests. */
    List<RenderedTextBlock> lineBlocks() {
        return lineBlocks;
    }
}
