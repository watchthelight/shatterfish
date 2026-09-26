package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.ui.Component;
import org.shatterfish.api.RunLog;

/**
 * The Panel's Goal line (story 5.3, FR-38, UX-DR3): one Goal in plain words from the Decision, title
 * size, in the goal colour ({@code DESIGN.md} Components: "normally one line, never ellipsized,
 * wrapping to two lines at most"). Hidden until the first Decision names a goal.
 */
final class GoalLine extends Component {

    /** Title size ({@code DESIGN.md} Typography). */
    static final int SIZE = 9;
    /** {@code DESIGN.md} colors.goal. */
    static final int COLOR = 0xFFE97F;

    private RenderedTextBlock text;

    @Override
    protected void createChildren() {
        text = PixelScene.renderTextBlock(SIZE);
        text.hardlight(COLOR);
        add(text);
    }

    /** Sets the Goal from {@code decision}, wrapped at {@code innerWidth} UI pixels; hides when null. */
    void content(RunLog.Decision decision, float innerWidth) {
        String goal = decision == null ? "" : decision.goal();
        visible = !goal.isEmpty();
        text.text(goal, Math.max(1, (int) innerWidth));
    }

    /** The height {@link #content} needs at the width it was given, before this is positioned. */
    float contentHeight() {
        return visible ? text.height() : 0;
    }

    @Override
    protected void layout() {
        if (text == null) {
            return;
        }
        text.setPos(x, y);
    }

    /** The Goal's own text block, for tests. */
    RenderedTextBlock text() {
        return text;
    }
}
