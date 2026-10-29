package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

/**
 * Why a Run log was not played (story 8.5): a frame with the reason in it, drawn by the game's own toolkit (the
 * Chrome nine-patch and {@code PixelScene.renderTextBlock}) over whatever scene is in front. A log that does not
 * verify, or that this build cannot play, starts no Run, so there is no Panel to put the reason in; this is the
 * Panel's place for it, and it says so in the danger colour the Decision card uses for a Brain that failed.
 */
final class RefusalNotice extends Component {

    /** The text size, as the Mode strip's. */
    static final int TEXT_SIZE = 8;
    /** The danger colour of DESIGN.md. */
    static final int DANGER = 0xFF5555;
    /** The space between the frame and its text, and from the screen's edge. */
    static final float PADDING = 4;

    private NinePatch frame;
    private RenderedTextBlock text;
    private String said = "";

    @Override
    protected void createChildren() {
        frame = Chrome.get(Chrome.Type.TOAST_TR_HEAVY);
        add(frame);
        text = PixelScene.renderTextBlock(TEXT_SIZE);
        text.hardlight(DANGER);
        add(text);
    }

    /** Shows {@code reason} within {@code available} UI pixels of width, at the top left of the screen. */
    void show(String reason, float available) {
        float width = Math.max(40, available - 2 * PADDING);
        text.maxWidth((int) (width - 2 * PADDING));
        if (!reason.equals(said)) {
            said = reason;
            text.text(reason);
        }
        setRect(PADDING, PADDING, width, text.height() + 2 * PADDING);
    }

    /** The words on screen. */
    String said() {
        return said;
    }

    RenderedTextBlock text() {
        return text;
    }

    NinePatch frame() {
        return frame;
    }

    @Override
    protected void layout() {
        if (frame == null) {
            return;
        }
        frame.x = x;
        frame.y = y;
        frame.size(width, height);
        PixelScene.align(frame);
        text.setPos(x + PADDING, y + PADDING);
        PixelScene.align(text);
    }
}
