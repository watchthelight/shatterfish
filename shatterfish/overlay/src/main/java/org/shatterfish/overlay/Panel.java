package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

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
 * <p>The Panel is placed by {@link PanelLayout} every frame. It has no pointer area, so it takes no
 * click away from the dungeon, and it lives on {@code PixelScene.uiCamera} like the game's own HUD.
 */
final class Panel extends Component {

    /** The oracle colour, reserved for the oracle's border and label ({@code DESIGN.md}: {@code #FF2020}). */
    static final int ORACLE_COLOR = 0xFF2020;
    /** How far the Panel dims while a prompt or a badge banner of the game's is over it. */
    static final float DIMMED = 0.35f;

    private NinePatch frame;
    private NinePatch strip;
    private RenderedTextBlock oracleLabel;
    private PanelLayout.Layout placed;
    private boolean dimmed;

    @Override
    protected void createChildren() {
        frame = Chrome.get(Chrome.Type.TOAST_TR_HEAVY);
        add(frame);
        strip = Chrome.get(Chrome.Type.TOAST_TR);
        add(strip);
        oracleLabel = PixelScene.renderTextBlock("ORACLE", 8);
        oracleLabel.hardlight(ORACLE_COLOR);
        oracleLabel.visible = false;
        add(oracleLabel);
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
}
