package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.Observation;
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
 *
 * <p><b>Real columns, not a padded string (the review after the first launch).</b> The pixel font is
 * not monospace, so a single {@code RenderedTextBlock} per row with the score space-padded inside its
 * text does not actually line up on screen (UX-DR5, {@code DESIGN.md}: "alignment is by column
 * position, never by padding with characters"). Each of the chosen row and the alternatives' rows is
 * three {@code RenderedTextBlock}s -- the Action's words, its score, its reason -- and
 * {@link #refresh()} measures every action and score block's real width (after its text is set) to
 * find the widest of each, which fixes the score column's right edge and the reason column's left edge
 * for every row alike; {@link #layout()} then right-aligns each score block to that edge
 * ({@code x = columnRight − block.width()}) and left-aligns every reason block to the other.
 */
final class DecisionCard extends Component {

    /** Body size ({@code DESIGN.md} Typography); the Explain expansion's Policy and flags use small size. */
    static final int BODY_SIZE = 8;
    static final int SMALL_SIZE = 6;
    static final int CHOSEN_COLOR = 0xFFB347;
    static final int ALTERNATIVE_COLOR = 0x7FB8FF;
    static final int INK_MUTED = 0x9C9C9C;
    /** The notice that a Replay stops at a wait (story 5.9): the paused amber, a caution, never the only signal. */
    static final int NOTICE_COLOR = ModeStripContent.PAUSED_COLOR;
    static final float ROW_GAP = 2;
    /** Between the action column and the score column, and between the score column and the reason column. */
    static final float COLUMN_GAP = 4;
    static final float BUTTON_HEIGHT = 11;
    static final float BUTTON_WIDTH = 48;

    /** The chosen Action's row, plus up to three alternatives ({@link RunLog.Decision#ALTERNATIVES}). */
    static final int TABLE_ROWS = 1 + RunLog.Decision.ALTERNATIVES;

    private RenderedTextBlock empty;
    private RenderedTextBlock headline;
    private RenderedTextBlock[] actionBlocks;
    private RenderedTextBlock[] scoreBlocks;
    private RenderedTextBlock[] reasonBlocks;
    private RenderedTextBlock policyLine;
    private RenderedTextBlock flagsLine;
    /** A HUMAN Run's word that a Replay stops at a wait (story 5.9); hidden otherwise. */
    private RenderedTextBlock noticeLine;
    private RedButton explainButton;
    private DecisionCardContent.Shadow shadow;

    private RunLog.Decision decision;
    private Observation observation;
    private boolean nextStep;
    private boolean explain;
    private boolean inputLocked;
    private float innerWidth = 1;
    /** Table rows shown by the last {@link #refresh()}: the chosen row, plus 0 to 3 alternatives'. */
    private int rows;
    /** The score column's right edge and the reason column's left edge, from this component's own x. */
    private float scoreColumnRight;
    private float reasonColumnX;

    @Override
    protected void createChildren() {
        empty = row(BODY_SIZE, INK_MUTED);
        headline = row(BODY_SIZE, INK_MUTED);
        actionBlocks = new RenderedTextBlock[TABLE_ROWS];
        scoreBlocks = new RenderedTextBlock[TABLE_ROWS];
        reasonBlocks = new RenderedTextBlock[TABLE_ROWS];
        for (int i = 0; i < TABLE_ROWS; i++) {
            int color = i == 0 ? CHOSEN_COLOR : ALTERNATIVE_COLOR;
            actionBlocks[i] = row(BODY_SIZE, color);
            scoreBlocks[i] = row(BODY_SIZE, color);
            reasonBlocks[i] = row(BODY_SIZE, INK_MUTED);
        }
        policyLine = row(SMALL_SIZE, INK_MUTED);
        flagsLine = row(SMALL_SIZE, INK_MUTED);
        noticeLine = row(SMALL_SIZE, NOTICE_COLOR);
        explainButton = new RedButton("Explain", SMALL_SIZE) {
            @Override
            protected void onClick() {
                toggleExplain();
            }
        };
        add(explainButton);
    }

    private RenderedTextBlock row(int size, int color) {
        RenderedTextBlock block = PixelScene.renderTextBlock(size);
        block.hardlight(color);
        add(block);
        return block;
    }

    /**
     * Sets the Decision this card shows, the Observation it was made on (for {@code ActionText}; may
     * be null), whether the Mode strip's speed mode is Next Step (the headline shows what the next
     * press will execute), the width to wrap at, and whether the game's own input is closed right now
     * ({@code InputLock}) -- Explain is active only when it is not, since {@code ActionExecutor.press}
     * queues synthetic taps that bypass the lock, and a live hot area could steal one meant for a
     * window button drawn under it (the fairness review).
     */
    void content(RunLog.Decision decision, Observation observation, boolean nextStep, float innerWidth,
                 boolean inputLocked) {
        content(decision, observation, nextStep, innerWidth, inputLocked, null);
    }

    /**
     * As above, for a HUMAN Run (story 5.9): {@code shadow} non-null makes the card the Brain's shadow,
     * every row greyed and a headline saying it was not executed, with the Replay notice over it.
     */
    void content(RunLog.Decision decision, Observation observation, boolean nextStep, float innerWidth,
                 boolean inputLocked, DecisionCardContent.Shadow shadow) {
        this.shadow = shadow;
        this.decision = decision;
        this.observation = observation;
        this.nextStep = nextStep;
        this.innerWidth = Math.max(1, innerWidth);
        this.inputLocked = inputLocked;
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
        DecisionCardContent.Content content = DecisionCardContent.of(decision, nextStep, explain, shadow);
        int width = (int) innerWidth;
        noticeLine.visible = content.notice() != null;
        noticeLine.text(content.notice() == null ? "" : content.notice(), width);
        empty.visible = !content.present();
        empty.text(content.present() ? "" : DecisionCardContent.NO_DECISION_YET, width);

        headline.visible = content.present() && content.headline() != null;
        headline.text(headline.visible ? content.headline() : "", width);

        List<DecisionCardContent.Row> tableRows = new ArrayList<>();
        if (content.present()) {
            tableRows.add(content.chosen());
            tableRows.addAll(content.alternatives());
        }
        rows = tableRows.size();

        // Each action and score block measured at its natural (unwrapped) width, so the widest of
        // each fixes one shared column for every row -- the property the review's test holds.
        float maxActionWidth = 0;
        float maxScoreWidth = 0;
        for (int i = 0; i < TABLE_ROWS; i++) {
            boolean shown = i < rows;
            actionBlocks[i].visible = shown;
            scoreBlocks[i].visible = shown;
            reasonBlocks[i].visible = shown;
            if (shown) {
                // A shadow is greyed, every row alike: it was never executed (story 5.9); the headline says so in words.
                int color = content.shadow() ? INK_MUTED : i == 0 ? CHOSEN_COLOR : ALTERNATIVE_COLOR;
                actionBlocks[i].hardlight(color);
                scoreBlocks[i].hardlight(color);
                DecisionCardContent.Row tableRow = tableRows.get(i);
                actionBlocks[i].text(ActionText.of(tableRow.action(), observation));
                scoreBlocks[i].text(tableRow.score());
                maxActionWidth = Math.max(maxActionWidth, actionBlocks[i].width());
                maxScoreWidth = Math.max(maxScoreWidth, scoreBlocks[i].width());
            } else {
                actionBlocks[i].text("");
                scoreBlocks[i].text("");
                reasonBlocks[i].text("");
            }
        }
        scoreColumnRight = maxActionWidth + COLUMN_GAP + maxScoreWidth;
        reasonColumnX = scoreColumnRight + COLUMN_GAP;
        int reasonWidth = (int) Math.max(1, innerWidth - reasonColumnX);
        for (int i = 0; i < rows; i++) {
            reasonBlocks[i].text(tableRows.get(i).reason(), reasonWidth);
        }

        boolean explaining = content.present() && content.explain() != null;
        policyLine.visible = explaining;
        policyLine.text(explaining ? content.explain().policyLine() : "", width);
        flagsLine.visible = explaining;
        flagsLine.text(explaining ? content.explain().flagsLine() : "", width);

        explainButton.visible = content.present();
        // Visible whenever there is a Decision to explain, but active -- so its hot area can take a
        // tap at all (Gizmo.isActive() walks the parent chain) -- only when the game's own input is
        // not locked: a Run playing means ActionExecutor.press may queue a synthetic tap that bypasses
        // InputLock, and a live hot area sitting over a window's own button would steal it (the
        // fairness review). Explain becomes clickable once the Run has ended and the lock releases.
        explainButton.active = content.present() && !inputLocked;
    }

    /** The taller of the three blocks in table row {@code i}. */
    private float tableRowHeight(int i) {
        return Math.max(actionBlocks[i].height(), Math.max(scoreBlocks[i].height(), reasonBlocks[i].height()));
    }

    /** The height {@link #content} needs at the width it was given, before this is positioned. */
    float contentHeight() {
        float notice = noticeLine.visible ? noticeLine.height() + ROW_GAP : 0;
        if (empty.visible) {
            return notice + empty.height();
        }
        float height = notice;
        if (headline.visible) {
            height += headline.height() + ROW_GAP;
        }
        for (int i = 0; i < rows; i++) {
            height += tableRowHeight(i) + ROW_GAP;
        }
        if (policyLine.visible) {
            height += policyLine.height() + ROW_GAP;
        }
        if (flagsLine.visible) {
            height += flagsLine.height() + ROW_GAP;
        }
        if (explainButton.visible) {
            height += BUTTON_HEIGHT + ROW_GAP;
        }
        return height > 0 ? height - ROW_GAP : 0;
    }

    @Override
    protected void layout() {
        if (empty == null) {
            return;
        }
        float rowY = y;
        if (noticeLine.visible) {
            noticeLine.setPos(x, rowY);
            PixelScene.align(noticeLine);
            rowY += noticeLine.height() + ROW_GAP;
        }
        if (empty.visible) {
            empty.setPos(x, rowY);
            PixelScene.align(empty);
            return;
        }
        if (headline.visible) {
            headline.setPos(x, rowY);
            PixelScene.align(headline);
            rowY += headline.height() + ROW_GAP;
        }
        for (int i = 0; i < rows; i++) {
            actionBlocks[i].setPos(x, rowY);
            PixelScene.align(actionBlocks[i]);
            scoreBlocks[i].setPos(x + scoreColumnRight - scoreBlocks[i].width(), rowY);
            PixelScene.align(scoreBlocks[i]);
            reasonBlocks[i].setPos(x + reasonColumnX, rowY);
            PixelScene.align(reasonBlocks[i]);
            rowY += tableRowHeight(i) + ROW_GAP;
        }
        if (policyLine.visible) {
            policyLine.setPos(x, rowY);
            PixelScene.align(policyLine);
            rowY += policyLine.height() + ROW_GAP;
        }
        if (flagsLine.visible) {
            flagsLine.setPos(x, rowY);
            PixelScene.align(flagsLine);
            rowY += flagsLine.height() + ROW_GAP;
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

    /** The table rows' Action blocks: index 0 the chosen Action, 1 to 3 the alternatives, for tests. */
    RenderedTextBlock[] actionBlocks() {
        return actionBlocks;
    }

    /** The table rows' score blocks, same indexing as {@link #actionBlocks()}, for tests. */
    RenderedTextBlock[] scoreBlocks() {
        return scoreBlocks;
    }

    /** The table rows' reason blocks, same indexing as {@link #actionBlocks()}, for tests. */
    RenderedTextBlock[] reasonBlocks() {
        return reasonBlocks;
    }

    /** The Explain expansion's Policy row, for tests. */
    RenderedTextBlock policyRow() {
        return policyLine;
    }

    /** The Explain expansion's Safety flags row, for tests. */
    RenderedTextBlock flagsRow() {
        return flagsLine;
    }

    /** The Replay notice row, for tests (story 5.9). */
    RenderedTextBlock noticeRow() {
        return noticeLine;
    }

    /** The Explain control, for tests. */
    RedButton explainButton() {
        return explainButton;
    }
}
