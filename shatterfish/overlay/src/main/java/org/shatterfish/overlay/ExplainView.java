package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Explain expansion, drawn (story 8.2, FR-44, UX-DR15): the rows of {@link ExplainContent} in the game's
 * own {@code ScrollPane}, each a {@code PixelScene.renderTextBlock}, in the room the Panel gives it below the
 * Decision card. No window and no asset of its own.
 *
 * <p>The words of a row are on the left, wrapped at whatever the number column leaves; the numbers (scores,
 * contributions, outcomes) are blocks of their own, right-aligned at one edge by position, never padded with
 * characters (UX-DR5). A Decision other than the one shown last puts the view back at the top; the same
 * Decision at a new width keeps the place it was read to.
 *
 * <p><b>The synthetic-tap gate.</b> The pane's drag area is a hot area like the Decision log's, so it is
 * active only when no window of the game's is in front and the expansion is open ({@link #show}); a view that
 * is closed takes no tap. {@code ExplainViewTest} holds both.
 */
final class ExplainView extends Component {

    static final int BODY_SIZE = 8;
    static final int SMALL_SIZE = 6;
    static final float ROW_GAP = 2;
    /** How far a reason, a term and a Search row sit in from the headings above them. */
    static final float INDENT = 4;
    /** Between the words and the number column. */
    static final float COLUMN_GAP = 4;

    static final int POLICY_COLOR = 0x9C9C9C;
    static final int CHOSEN_COLOR = DecisionCard.CHOSEN_COLOR;
    static final int ALTERNATIVE_COLOR = DecisionCard.ALTERNATIVE_COLOR;
    static final int INK_MUTED = 0x9C9C9C;
    static final int INK = 0xF0F0F0;

    private ScrollPane pane;
    private Component rows;
    private final List<RenderedTextBlock> lefts = new ArrayList<>();
    private final List<RenderedTextBlock> rights = new ArrayList<>();
    private List<ExplainContent.Line> lines = List.of();
    private float innerWidth = -1;
    private boolean shown;

    @Override
    protected void createChildren() {
        rows = new Component();
        pane = new ScrollPane(rows);
        add(pane);
        show(false);
    }

    /** Opens or closes the expansion: a closed one is invisible and takes no tap, scroll or drag. */
    void show(boolean shown) {
        this.shown = shown;
        visible = shown;
        active = shown && active; // opening never activates it: content() decides, with the input lock in hand
    }

    boolean shown() {
        return shown;
    }

    /**
     * Sets the rows, wrapped at {@code innerWidth}; the pane can be dragged only while {@code inputLocked} is
     * false and the expansion is open.
     */
    void content(List<ExplainContent.Line> next, float width, boolean inputLocked) {
        active = shown && !inputLocked;
        boolean sameLines = next.equals(lines);
        if (sameLines && width == innerWidth) {
            return;
        }
        lines = next;
        innerWidth = width;
        rebuild();
        if (!sameLines) {
            pane.scrollTo(0, 0);
        }
    }

    /**
     * Rebuilds every row. A text block's size is fixed when it is made, and a row's size follows its kind,
     * so the blocks are made afresh; this runs once per Decision or width, never per frame.
     */
    private void rebuild() {
        for (RenderedTextBlock block : lefts) {
            rows.remove(block);
        }
        for (RenderedTextBlock block : rights) {
            rows.remove(block);
        }
        lefts.clear();
        rights.clear();
        float widest = 0;
        for (ExplainContent.Line line : lines) {
            RenderedTextBlock left = block(line);
            RenderedTextBlock right = block(line);
            right.text(line.right());
            right.visible = !line.right().isEmpty();
            if (right.visible) {
                widest = Math.max(widest, right.width());
            }
            lefts.add(left);
            rights.add(right);
        }
        // Every number ends at one right edge; the words wrap in what that column leaves them.
        float numbers = widest > 0 ? widest + COLUMN_GAP : 0;
        float rowY = 0;
        float bottom = 0;
        for (int i = 0; i < lines.size(); i++) {
            ExplainContent.Line line = lines.get(i);
            float indent = indent(line.kind());
            RenderedTextBlock left = lefts.get(i);
            RenderedTextBlock right = rights.get(i);
            left.text(line.left(), (int) Math.max(1, innerWidth - indent - numbers));
            left.setPos(indent, rowY);
            PixelScene.align(left);
            float height = left.height();
            if (right.visible) {
                right.setPos(innerWidth - right.width(), rowY);
                PixelScene.align(right);
                height = Math.max(height, right.height());
            }
            bottom = rowY + height;
            rowY = bottom + ROW_GAP;
        }
        rows.setSize(innerWidth, Math.max(1, bottom));
        resizePane();
    }

    private RenderedTextBlock block(ExplainContent.Line line) {
        RenderedTextBlock block = PixelScene.renderTextBlock(size(line.kind()));
        block.hardlight(color(line.kind()));
        rows.add(block);
        return block;
    }

    @Override
    protected void layout() {
        resizePane();
    }

    private void resizePane() {
        if (pane != null) {
            pane.setRect(x, y, Math.max(1, width), Math.max(1, height));
        }
    }

    private static float indent(ExplainContent.Kind kind) {
        return switch (kind) {
            case WHY, TERM, OUTCOME -> INDENT;
            default -> 0;
        };
    }

    private static int size(ExplainContent.Kind kind) {
        return switch (kind) {
            case WHY, TERM, OUTCOME -> SMALL_SIZE;
            default -> BODY_SIZE;
        };
    }

    private static int color(ExplainContent.Kind kind) {
        return switch (kind) {
            case CHOSEN -> CHOSEN_COLOR;
            case ALTERNATIVE -> ALTERNATIVE_COLOR;
            case POLICY, FLAGS, SEARCH -> INK;
            case WHY, TERM, OUTCOME -> INK_MUTED;
        };
    }

    /** The {@code ScrollPane}, for tests. */
    ScrollPane pane() {
        return pane;
    }

    /** The rows last given, for tests. */
    List<ExplainContent.Line> lines() {
        return lines;
    }

    /** The words of row {@code i}, for tests. */
    RenderedTextBlock left(int i) {
        return lefts.get(i);
    }

    /** The number of row {@code i}, for tests. */
    RenderedTextBlock right(int i) {
        return rights.get(i);
    }

    /** The scrolled content's height, for tests. */
    float contentHeight() {
        return rows.height();
    }
}
