package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

/**
 * The Overlay's instrument, docked beside the dungeon (story 5.2, UX-DR1, non-negotiable 6).
 *
 * <p>Two frames from the game's own toolkit: the Panel's {@code TOAST_TR_HEAVY} nine-patch, the game's
 * translucent HUD scrim, and the Mode strip's lighter {@code TOAST_TR} ({@code core/.../Chrome.java:56-60},
 * {@code DESIGN.md} Elevation). The strip is the Panel's first line when the Panel is full and the whole
 * Panel when it is collapsed. What the strip and the Panel show is story 5.3's; this story gives them
 * their place, and the one label that cannot wait: ORACLE, in the oracle colour, set with the game's own
 * text renderer ({@code PixelScene.renderTextBlock}) at the body size 8, while the Run can see what a
 * player could not.
 *
 * <p>The Panel is placed by {@link PanelLayout} every frame, and lives on {@code PixelScene.uiCamera}
 * like the game's own HUD. The frame, the strip, the Mode strip's text, the ORACLE label and the Goal
 * line have no pointer area, so they take no click away from the dungeon; the Decision card's Explain
 * control does (story 5.3), a native {@code RedButton}, and is kept from taking one meant for the game
 * by {@link DecisionCard#content}'s own {@code inputLocked} gate (the fairness review: {@code
 * ActionExecutor.press} queues synthetic taps that bypass {@code InputLock}, so Explain's hot area must
 * be inactive whenever such a tap could land on it, not only invisible).
 */
final class Panel extends Component {

    /** The oracle colour, reserved for the oracle's border and label ({@code DESIGN.md}: {@code #FF2020}). */
    static final int ORACLE_COLOR = 0xFF2020;
    /** How far the Panel dims while a prompt or a badge banner of the game's is over it. */
    static final float DIMMED = 0.35f;
    /** Body size, the Mode strip's own text (story 5.3, {@code DESIGN.md} Typography). */
    static final int STRIP_TEXT_SIZE = 8;
    /** Sections are 6 apart, rows 2 apart ({@code DESIGN.md} Layout & Spacing spacing tokens). */
    static final float SECTION_GAP = 6;

    private NinePatch frame;
    private NinePatch strip;
    private RenderedTextBlock stripText;
    private RenderedTextBlock oracleLabel;
    private GoalLine goal;
    private DecisionCard card;
    private PanelLayout.Layout placed;
    private boolean dimmed;

    @Override
    protected void createChildren() {
        frame = Chrome.get(Chrome.Type.TOAST_TR_HEAVY);
        add(frame);
        strip = Chrome.get(Chrome.Type.TOAST_TR);
        add(strip);
        stripText = PixelScene.renderTextBlock(STRIP_TEXT_SIZE);
        add(stripText);
        oracleLabel = PixelScene.renderTextBlock("ORACLE", 8);
        oracleLabel.hardlight(ORACLE_COLOR);
        oracleLabel.visible = false;
        add(oracleLabel);
        goal = new GoalLine();
        add(goal);
        card = new DecisionCard();
        add(card);
    }

    /**
     * What the Mode strip, the Goal line and the Decision card show (story 5.3): the Mode strip is the
     * collapsed Panel too, so it is set every frame regardless of {@link #place}'s form; the Goal line
     * and the Decision card are laid out only when full, since the strip alone is drawn otherwise.
     */
    void content(ModeState mode, RunLog.Decision decision, Observation observation, boolean inputLocked) {
        stripText.text(ModeStripContent.text(mode));
        stripText.hardlight(ModeStripContent.color(mode.mode()));
        boolean full = placed != null && placed.form() == PanelLayout.Form.FULL;
        boolean nextStep = mode.speed() == ModeState.SpeedMode.NEXT_STEP;
        float inner = innerWidth();
        goal.content(full ? decision : null, inner);
        card.content(full ? decision : null, full ? observation : null, nextStep, inner, inputLocked);
        layout();
    }

    /** The Panel's own inner width, inside its padding, that the strip's text, the Goal line and the card wrap at. */
    private float innerWidth() {
        return Math.max(1, width - 2 * PanelLayout.PADDING);
    }

    /** Puts the Panel where {@code layout} says: the frame and the strip in it, or the strip alone. */
    void place(PanelLayout.Layout layout) {
        if (layout.equals(placed)) {
            return;
        }
        placed = layout;
        PanelLayout.Rect rect = layout.rect();
        setRect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    /** Shows the ORACLE label in the Mode strip, or hides it. */
    void oracle(boolean oracle) {
        oracleLabel.visible = oracle;
        layout();
    }

    /** Dims the Panel while the game's prompt or a badge banner is over it, and restores it after. */
    void dim(boolean dim) {
        if (dim == dimmed) {
            return;
        }
        dimmed = dim;
        float alpha = dim ? DIMMED : 1f;
        frame.alpha(alpha);
        strip.alpha(alpha);
        oracleLabel.alpha(alpha);
    }

    @Override
    protected void layout() {
        if (frame == null || placed == null) {
            return;
        }
        boolean full = placed.form() == PanelLayout.Form.FULL;
        frame.visible = full;
        if (full) {
            frame.x = x;
            frame.y = y;
            frame.size(width, height);
            PixelScene.align(frame);
            strip.x = x + PanelLayout.PADDING;
            strip.y = y + PanelLayout.PADDING;
            strip.size(width - 2 * PanelLayout.PADDING, PanelLayout.STRIP_HEIGHT);
        } else {
            strip.x = x;
            strip.y = y;
            strip.size(width, height);
        }
        PixelScene.align(strip);
        oracleLabel.setPos(strip.x + strip.width() - 2 - oracleLabel.width(),
                strip.y + (strip.height() - oracleLabel.height()) / 2f);
        PixelScene.align(oracleLabel);
        float stripInner = Math.max(1, strip.width() - 4 - (oracleLabel.visible ? oracleLabel.width() + 2 : 0));
        stripText.maxWidth((int) stripInner);
        stripText.setPos(strip.x + 2, strip.y + (strip.height() - stripText.height()) / 2f);
        PixelScene.align(stripText);

        card.visible = full;
        if (full) {
            float sectionX = x + PanelLayout.PADDING;
            float sectionWidth = innerWidth();
            float goalTop = strip.y + PanelLayout.STRIP_HEIGHT + SECTION_GAP;
            goal.setRect(sectionX, goalTop, sectionWidth, goal.contentHeight());
            float cardTop = (goal.visible ? goal.bottom() : goalTop) + SECTION_GAP;
            card.setRect(sectionX, cardTop, sectionWidth, card.contentHeight());
        }
    }

    /** How the Panel is placed now, or null before the first placement. */
    PanelLayout.Layout placed() {
        return placed;
    }

    /** The Panel's frame, for tests that check it is the game's scrim. */
    NinePatch frame() {
        return frame;
    }

    /** The Mode strip's frame. */
    NinePatch strip() {
        return strip;
    }

    /** The ORACLE label. */
    RenderedTextBlock oracleLabel() {
        return oracleLabel;
    }

    /** Whether the Panel is dimmed under the game's prompt or a badge banner. */
    boolean dimmed() {
        return dimmed;
    }

    /** The Mode strip's own text, for tests (story 5.3). */
    RenderedTextBlock stripText() {
        return stripText;
    }

    /** The Goal line, for tests (story 5.3). */
    GoalLine goal() {
        return goal;
    }

    /** The Decision card, for tests (story 5.3). */
    DecisionCard card() {
        return card;
    }
}
