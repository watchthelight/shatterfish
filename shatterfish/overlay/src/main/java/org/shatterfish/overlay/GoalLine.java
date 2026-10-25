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
        text.hardlight(COLOR);
        text.text(goal, Math.max(1, (int) innerWidth));
    }

    /**
     * Shows {@code line} in place of a Goal, in the Panel's plain ink, wrapped at {@code innerWidth}
     * (story 5.14: a Run that is over names its Run log here). The text block breaks only at spaces, so a
     * path is cut into rows by {@link #wrap} first.
     */
    void notice(String line, float innerWidth) {
        visible = line != null && !line.isEmpty();
        text.hardlight(NOTICE_COLOR);
        int width = Math.max(1, (int) innerWidth);
        // The pixel font is about 4 UI pixels per glyph at title size, a safe under-estimate of a row's room.
        text.text(visible ? wrap(line, Math.max(8, width / 5)) : "", width);
    }

    /** {@code DESIGN.md} ink: the Run-over notice is not a Goal. */
    static final int NOTICE_COLOR = 0xF0F0F0;

    /** Breaks {@code line} with real newlines into rows of at most {@code columns} characters. */
    static String wrap(String line, int columns) {
        if (columns < 1) {
            throw new IllegalArgumentException("a row holds at least one character: " + columns);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i += columns) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(line, i, Math.min(line.length(), i + columns));
        }
        return out.toString();
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
