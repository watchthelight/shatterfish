package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Panel's Safety flags row (story 5.4, FR-38): the Decision's flags as chips, each the game's
 * own {@code TAG} nine-patch tinted by {@link SafetyFlagVerdict} (the same tinting {@code
 * Tag.java:44-46} uses for the game's own HP and EXP tags), with the flag's own text in ink on top
 * -- the colour is never the only signal (UX-DR14, {@code DESIGN.md} Components: "the text always
 * states the flag, the color never carries meaning alone"). Absent when there are none (the epic's
 * own acceptance criterion, unlike the Decision card and the Belief summary, which always show
 * something).
 */
final class SafetyFlagsRow extends Component {

    /** Small size ({@code DESIGN.md} Typography). */
    static final int SIZE = 6;
    static final int INK = 0xF0F0F0;
    static final float PAD_X = 3;
    static final float PAD_Y = 1;
    /** Between chips: the UI-pixel grid's 2 ({@code DESIGN.md} spacing.2). */
    static final float GAP = 2;

    private final List<NinePatch> backgrounds = new ArrayList<>();
    private final List<RenderedTextBlock> labels = new ArrayList<>();
    private List<SafetyFlagsContent.Chip> chips = List.of();

    /** Sets the chips from {@code flags} (may be null or empty); hides this row when there are none. */
    void content(List<String> flags) {
        chips = SafetyFlagsContent.of(flags);
        while (backgrounds.size() < chips.size()) {
            NinePatch bg = Chrome.get(Chrome.Type.TAG);
            add(bg);
            backgrounds.add(bg);
            RenderedTextBlock label = PixelScene.renderTextBlock(SIZE);
            label.hardlight(INK);
            add(label);
            labels.add(label);
        }
        for (int i = 0; i < backgrounds.size(); i++) {
            boolean shown = i < chips.size();
            backgrounds.get(i).visible = shown;
            labels.get(i).visible = shown;
            if (shown) {
                SafetyFlagsContent.Chip chip = chips.get(i);
                labels.get(i).text(chip.text());
                backgrounds.get(i).hardlight(chip.verdict().color);
            }
        }
        visible = !chips.isEmpty();
    }

    /** The height {@link #content} needs at any width; 0 with no flags, so the section below closes the gap. */
    float contentHeight() {
        return chips.isEmpty() ? 0 : SIZE + 2 * PAD_Y;
    }

    @Override
    protected void layout() {
        if (chips.isEmpty()) {
            return;
        }
        float rowX = x;
        float rowHeight = contentHeight();
        for (int i = 0; i < chips.size(); i++) {
            RenderedTextBlock label = labels.get(i);
            NinePatch bg = backgrounds.get(i);
            float chipWidth = label.width() + 2 * PAD_X;
            bg.x = rowX;
            bg.y = y;
            bg.size(chipWidth, rowHeight);
            PixelScene.align(bg);
            label.setPos(rowX + PAD_X, y + PAD_Y);
            PixelScene.align(label);
            rowX += chipWidth + GAP;
        }
    }

    /** The chip backgrounds, for tests: index order matches {@link #labels()}. */
    List<NinePatch> backgrounds() {
        return backgrounds;
    }

    /** The chip labels, for tests. */
    List<RenderedTextBlock> labels() {
        return labels;
    }

    /** The chips this row was last given, for tests. */
    List<SafetyFlagsContent.Chip> chips() {
        return chips;
    }
}
