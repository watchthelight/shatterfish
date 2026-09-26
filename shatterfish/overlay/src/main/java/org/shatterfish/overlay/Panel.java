package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

/**
 * The Overlay's instrument, docked beside the dungeon (story 5.2, UX-DR1, non-negotiable 6).
 *
 * <p>Two frames from the game's own toolkit and nothing else: the Panel's {@code TOAST_TR_HEAVY}
 * nine-patch, the game's translucent HUD scrim, and the Mode strip's lighter {@code TOAST_TR}
 * (`core/.../Chrome.java:56-60`, {@code DESIGN.md} Elevation). The strip is the Panel's first line
 * when the Panel is full and the whole Panel when it is collapsed. What the strip and the Panel show
 * is story 5.3's; this story gives them their place.
 *
 * <p>The Panel is placed by {@link PanelLayout} every frame. It has no pointer area, so it takes no
 * click away from the dungeon, and it lives on {@code PixelScene.uiCamera} like the game's own HUD.
 */
final class Panel extends Component {

    private NinePatch frame;
    private NinePatch strip;
    private PanelLayout.Layout placed;

    @Override
    protected void createChildren() {
        frame = Chrome.get(Chrome.Type.TOAST_TR_HEAVY);
        add(frame);
        strip = Chrome.get(Chrome.Type.TOAST_TR);
        add(strip);
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
}
