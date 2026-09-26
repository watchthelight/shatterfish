package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Panel's Decision card (story 5.3, FR-38, FR-39, UX-DR3, UX-DR5, UX-DR14): the chosen Action with
 * its score, up to three alternatives with scores and one-line reasons, and the Explain control, which
 * expands the card in place to the Policy that fired and the Safety flags that applied ({@code
 * DESIGN.md} Components: "the card is the Explain expansion's anchor"). This is FR-39's v1 Explain
 * control, not the v2 Explain view (UX-DR15).
 *
 * <p>Content ({@link DecisionCardContent}) is kept apart from drawing on purpose: the content tests
 * hold what the card says against a constructed Decision without booting the game, and this class only
 * turns that into rows of the game's own text renderer.
 */
final class DecisionCard extends Component {

    /** Body size ({@code DESIGN.md} Typography); the Explain expansion's Policy and flags use small size. */
    static final int BODY_SIZE = 8;
    static final int SMALL_SIZE = 6;
    static final int CHOSEN_COLOR = 0xFFB347;
    static final int ALTERNATIVE_COLOR = 0x7FB8FF;
    static final int INK_MUTED = 0x9C9C9C;
    static final float ROW_GAP = 2;
    static final float BUTTON_HEIGHT = 11;
    static final float BUTTON_WIDTH = 48;

    private RenderedTextBlock empty;
    private RenderedTextBlock headline;
    private RenderedTextBlock chosen;
    private RenderedTextBlock[] alternatives;
    private RenderedTextBlock policyLine;
    private RenderedTextBlock flagsLine;
    private RedButton explainButton;

    private RunLog.Decision decision;
    private boolean nextStep;
    private boolean explain;
    private float innerWidth = 1;

    @Override
    protected void createChildren() {
        empty = row(INK_MUTED);
        headline = row(INK_MUTED);
        chosen = row(CHOSEN_COLOR);
        alternatives = new RenderedTextBlock[RunLog.Decision.ALTERNATIVES];
        for (int i = 0; i < alternatives.length; i++) {
            alternatives[i] = row(ALTERNATIVE_COLOR);
        }
        policyLine = row(SMALL_SIZE, INK_MUTED);
        flagsLine = row(SMALL_SIZE, INK_MUTED);
        explainButton = new RedButton("Explain", SMALL_SIZE) {
            @Override
            protected void onClick() {
                toggleExplain();
            }
        };
        add(explainButton);
    }

    private RenderedTextBlock row(int color) {
        return row(BODY_SIZE, color);
    }

    private RenderedTextBlock row(int size, int color) {
        RenderedTextBlock block = PixelScene.renderTextBlock(size);
        block.hardlight(color);
        add(block);
        return block;
    }

    /**
     * Sets the Decision this card shows, whether the Mode strip's speed mode is Next Step (the headline
     * shows what the next press will execute), and the width to wrap at.
     */
    void content(RunLog.Decision decision, boolean nextStep, float innerWidth) {
        this.decision = decision;
        this.nextStep = nextStep;
        this.innerWidth = Math.max(1, innerWidth);
        refresh();
    }

    /** Expands the card to the Explain view, or collapses it back; a second press of the same control does the other. */
    void toggleExplain() {
        explain = !explain;
        refresh();
        if (width > 0) {
            layout();
        }
    }

    boolean explaining() {
        return explain;
    }

    private void refresh() {
        DecisionCardContent.Content content = DecisionCardContent.of(decision, nextStep, explain);
        int width = (int) innerWidth;
        empty.visible = !content.present();
        empty.text(content.present() ? "" : DecisionCardContent.NO_DECISION_YET, width);

        headline.visible = content.present() && content.headline() != null;
        headline.text(headline.visible ? content.headline() : "", width);

        chosen.visible = content.present();
        chosen.text(content.present() ? content.chosen().line() : "", width);

        for (int i = 0; i < alternatives.length; i++) {
            boolean shown = content.present() && i < content.alternatives().size();
            alternatives[i].visible = shown;
            alternatives[i].text(shown ? content.alternatives().get(i).line() : "", width);
        }

        boolean explaining = content.present() && content.explain() != null;
        policyLine.visible = explaining;
        policyLine.text(explaining ? content.explain().policyLine() : "", width);
        flagsLine.visible = explaining;
        flagsLine.text(explaining ? content.explain().flagsLine() : "", width);

        explainButton.visible = content.present();
    }

    /** The rows shown now, top to bottom, in the order {@link #layout} stacks them. */
    private List<RenderedTextBlock> rows() {
        List<RenderedTextBlock> rows = new ArrayList<>();
        if (empty.visible) {
            rows.add(empty);
            return rows;
        }
        if (headline.visible) {
            rows.add(headline);
        }
        rows.add(chosen);
        for (RenderedTextBlock alternative : alternatives) {
            if (alternative.visible) {
                rows.add(alternative);
            }
        }
        if (policyLine.visible) {
            rows.add(policyLine);
        }
        if (flagsLine.visible) {
            rows.add(flagsLine);
        }
        return rows;
    }

    /** The height {@link #content} needs at the width it was given, including the Explain button when shown. */
    float contentHeight() {
        List<RenderedTextBlock> rows = rows();
        float height = 0;
        for (RenderedTextBlock row : rows) {
            height += row.height() + ROW_GAP;
        }
        if (explainButton.visible) {
            height += BUTTON_HEIGHT + ROW_GAP;
        }
        return height > 0 ? height - ROW_GAP : 0;
    }

    @Override
    protected void layout() {
        if (alternatives == null) {
            return;
        }
        float rowY = y;
        for (RenderedTextBlock row : rows()) {
            row.setPos(x, rowY);
            rowY += row.height() + ROW_GAP;
        }
        if (explainButton.visible) {
            explainButton.setRect(x, rowY, Math.min(BUTTON_WIDTH, width), BUTTON_HEIGHT);
        }
    }

    /** The "no decision yet" row, for tests. */
    RenderedTextBlock emptyRow() {
        return empty;
    }

    /** The Next Step headline row, for tests. */
    RenderedTextBlock headlineRow() {
        return headline;
    }

    /** The chosen Action's row, for tests. */
    RenderedTextBlock chosenRow() {
        return chosen;
    }

    /** The alternatives' rows (up to {@link RunLog.Decision#ALTERNATIVES}), for tests. */
    RenderedTextBlock[] alternativeRows() {
        return alternatives;
    }

    /** The Explain expansion's Policy row, for tests. */
    RenderedTextBlock policyRow() {
        return policyLine;
    }

    /** The Explain expansion's Safety flags row, for tests. */
    RenderedTextBlock flagsRow() {
        return flagsLine;
    }

    /** The Explain control, for tests. */
    RedButton explainButton() {
        return explainButton;
    }
}
