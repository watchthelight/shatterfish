package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.DangerIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.InventoryPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.MenuPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.noosa.Game;
import com.watabou.utils.PlatformSupport;
import com.watabou.utils.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Panel goes on a screen, as a pure function of the screen (story 5.2, UX-DR1, UX-DR2,
 * {@code DESIGN.md} Layout &amp; Spacing).
 *
 * <p>Every length is in UI pixels, the units of {@code PixelScene.uiCamera}. The Panel is a single
 * column at the right edge of the dungeon view: its right side is the inventory pane's left (interface
 * size 2) or the tag column's (interface size 1, tags on the right), its bottom the top of the toolbar
 * or of the large status pane, whichever is higher, and its top below every top HUD element whose
 * columns it shares. Its width is 200 when that leaves 200
 * of uncovered map, shrinks to 160 to keep 200, and below that the Panel collapses to the Mode strip;
 * it also collapses when the view is shorter than 200, in the mobile layout (interface size 0), when
 * the human has collapsed it, and when it would be too short for the Mode strip and three log lines.
 *
 * <p>The game's HUD is modelled here from the game's own layout code, so that the rule "never over the
 * HUD" is a computation and a test ({@code PanelLayoutTest}), and the model is held against the real
 * components of a real play scene ({@code PanelHudTest}). The citations are to the pinned tag.
 */
public final class PanelLayout {

    /** Target width (UX-DR2, {@code DESIGN.md}). */
    public static final float TARGET_WIDTH = 200;
    /** Minimum width: the Mode strip needs about 35 characters of body text (UX-DR2). */
    public static final float MIN_WIDTH = 160;
    /** Narrower uncovered map than this collapses the Panel (UX-DR2). */
    public static final float MIN_UNCOVERED = 200;
    /** A view shorter than this collapses the Panel (UX-DR2). */
    public static final float MIN_VIEW_HEIGHT = 200;
    /** The Mode strip: body text 8 and a padding of 2 above and below ({@code DESIGN.md}, the mockups). */
    public static final float STRIP_HEIGHT = 12;
    /** The collapsed Mode strip's width: the Panel's minimum. */
    public static final float STRIP_WIDTH = MIN_WIDTH;
    /** Padding inside the Panel's frame ({@code DESIGN.md}: "Panel padding is 4"). */
    public static final float PADDING = 4;
    /** Space kept between the Panel and any HUD element (the UI-pixel grid's 2). */
    public static final float GAP = 2;
    /**
     * The shortest full Panel (story 5.2's own deferred item, resolved in story 5.4 once the sections
     * below the Decision card existed to arbitrate room with): its padding, the Mode strip, a section
     * gap, the Decision card's own shortest form (one line at the body size 8 -- it always shows
     * something, its own "no decision yet," so unlike the Goal line there is no all-the-way-collapsed
     * case for it to arbitrate room with), a second section gap, and the Decision log's three lines at
     * the small size 6 with rows 2 apart, which UX-DR2 says the log never goes below. The Goal line,
     * the Safety flags row and the Belief summary can each still take zero of this floor's own room
     * (the Goal line hides with no goal; the flags row is absent with no flags; the Belief summary
     * shows its one-line placeholder, which this floor's own Decision-card line already stands in for
     * at the same size) -- this is the worst case those three collapsing to nothing leaves, not a
     * promise that every section fits inside it with real content in all of them.
     */
    public static final float MIN_PANEL_HEIGHT = PADDING + STRIP_HEIGHT + 6 + (8 + 2) + 6 + 3 * (6 + 2) + PADDING;

    // The game's HUD, in UI pixels (docs/rules/ui.md, Tier 1 rows for the full and mobile layouts).
    /** The menu pane's background, 31 by 21 ({@code MenuPane.java:72}, {@code :78}). */
    static final float MENU_WIDTH = MenuPane.WIDTH;
    /** The menu pane with the danger tag below it: 21, 1, then 16 ({@code MenuPane.java:210}, {@code DangerIndicator.java:46}). */
    static final float MENU_HEIGHT = 21 + 1 + DangerIndicator.HEIGHT;
    /** The depth and challenge icons left of the menu pane's background: 7 each ({@code MenuPane.java:186}, {@code :198}). */
    static final float MENU_ICONS = 14;
    /** The large status pane, at the bottom with interface size 1 or 2 ({@code StatusPane.java:191}, {@code GameScene.java:487}). */
    static final float STATUS_LARGE = 39;
    /**
     * The small status pane at the top in the mobile layout, with what it draws below itself: the pane is
     * 38 high ({@code StatusPane.java:191}), and its busy indicator, the 8-pixel busy icon, is placed 37
     * below its top ({@code :283-284}), so the whole reaches 45 (held against the real pane by {@code PanelHudTest}).
     */
    static final float STATUS_SMALL = 45;
    /** The toolbar: one button high ({@code Toolbar.java:87}; 26 per docs/rules/ui.md). */
    static final float TOOLBAR_HEIGHT = 26;
    /** The game log with interface size 1 or 2: 160 wide, left ({@code GameScene.java:1018-1024}). */
    static final float LOG_WIDTH = 160;
    /** The mobile game log's height above the toolbar: 3 lines at size 6 ({@code GameLog.java:59}, {@code :98}), rounded up. */
    static final float LOG_MOBILE_HEIGHT = 30;
    /** At most four tags stack in the tag column ({@code GameScene.java:1031-1052}). */
    static final int TAGS = 4;

    private PanelLayout() {
    }

    /**
     * The screen as the play scene lays it out.
     *
     * @param width         {@code uiCamera.width}
     * @param height        {@code uiCamera.height}
     * @param insetLeft     the blocking inset on the left ({@code PixelScene.getCommonInsets})
     * @param screenTop     the large top inset, the play scene's {@code screentop} ({@code GameScene.java:248}, {@code :459})
     * @param insetRight    the blocking inset on the right
     * @param insetBottom   the bottom inset
     * @param interfaceSize {@code SPDSettings.interfaceSize()}: 0 mobile, 1 mixed, 2 full
     * @param tagsOnLeft    {@code SPDSettings.flipTags()}
     * @param landscape     {@code PixelScene.landscape()}
     */
    public record Screen(float width, float height, float insetLeft, float screenTop, float insetRight,
                         float insetBottom, int interfaceSize, boolean tagsOnLeft, boolean landscape) {
        public Screen {
            if (interfaceSize < 0 || interfaceSize > 2) {
                throw new IllegalArgumentException("interface size is 0, 1 or 2: " + interfaceSize);
            }
        }

        /** A desktop screen with no insets. */
        public static Screen of(float width, float height, int interfaceSize) {
            return new Screen(width, height, 0, 0, 0, 0, interfaceSize, false, interfaceSize > 0 || width > height);
        }

        /** The toolbar's top: above the inventory pane with interface size 2 ({@code GameScene.java:553-556}). */
        public float toolbarTop() {
            float below = interfaceSize == 2 ? InventoryPane.HEIGHT : 0;
            return height - TOOLBAR_HEIGHT - below - insetBottom;
        }

        /** The play scene's {@code screentop}: one pixel above the view in the mobile layout with no top inset ({@code GameScene.java:459-462}). */
        float top() {
            return screenTop == 0 && interfaceSize == 0 ? -1 : screenTop;
        }
    }

    /** A rectangle in UI pixels. */
    public record Rect(float x, float y, float width, float height) {
        public float right() {
            return x + width;
        }

        public float bottom() {
            return y + height;
        }

        /** Whether the two share any area. */
        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        /** Whether the columns {@code [x, right)} of the two overlap. */
        boolean sharesColumns(Rect other) {
            return x < other.right() && other.x < right();
        }

        /** Whether this lies within {@code [0, width) × [0, height)}. */
        public boolean within(float screenWidth, float screenHeight) {
            return x >= 0 && y >= 0 && right() <= screenWidth && bottom() <= screenHeight;
        }
    }

    /** The Panel in full, or collapsed to the Mode strip. */
    public enum Form {
        FULL, STRIP
    }

    /**
     * Where the Panel goes.
     *
     * @param form     full or collapsed
     * @param rect     the Panel's rectangle when full, the Mode strip's when collapsed
     * @param offsetUi the horizontal camera offset, in UI pixels, that puts the hero at the middle of
     *                 the uncovered map; 0 when collapsed
     */
    public record Layout(Form form, Rect rect, float offsetUi) {
    }

    /** The screen of the running play scene, read from the same places the scene reads it. */
    public static Screen current() {
        float zoom = PixelScene.defaultZoom;
        RectF all = Game.platform.getSafeInsets(PlatformSupport.INSET_ALL);
        RectF blocking = Game.platform.getSafeInsets(PlatformSupport.INSET_BLK);
        float top = Game.platform.getSafeInsets(PlatformSupport.INSET_LRG).top / zoom;
        return new Screen(PixelScene.uiCamera.width, PixelScene.uiCamera.height, blocking.left / zoom, top,
                blocking.right / zoom, all.bottom / zoom, SPDSettings.interfaceSize(), SPDSettings.flipTags(),
                PixelScene.landscape());
    }

    /** The game's HUD on {@code screen}, each element's largest extent. */
    public static List<Rect> hud(Screen screen) {
        float w = screen.width();
        float h = screen.height();
        float top = screen.top();
        List<Rect> hud = new ArrayList<>();
        hud.add(menu(screen));
        hud.add(boss(screen));
        float toolbarTop = screen.toolbarTop();
        hud.add(new Rect(screen.insetLeft(), toolbarTop, w - screen.insetLeft() - screen.insetRight(), TOOLBAR_HEIGHT));
        float tagWidth = Tag.SIZE + (screen.tagsOnLeft() ? screen.insetLeft() : screen.insetRight());
        float tagLeft = screen.tagsOnLeft() ? 0 : w - tagWidth;
        if (screen.interfaceSize() > 0) {
            float statusTop = h - STATUS_LARGE - screen.insetBottom();
            hud.add(new Rect(screen.insetLeft(), statusTop, w - screen.insetLeft() - screen.insetRight(), STATUS_LARGE));
            // The log's column, from the top of the view to the status pane: it grows upward.
            hud.add(new Rect(screen.insetLeft(), top, LOG_WIDTH - screen.insetLeft(), statusTop - top));
            if (screen.interfaceSize() == 2) {
                hud.add(new Rect(w - InventoryPane.WIDTH - screen.insetRight(), h - InventoryPane.HEIGHT - screen.insetBottom(),
                        InventoryPane.WIDTH, InventoryPane.HEIGHT));
            }
            // Tags stack up from the toolbar, or from the status pane when on the left (GameScene.java:1026-1029).
            float base = screen.tagsOnLeft() ? statusTop : toolbarTop;
            hud.add(new Rect(tagLeft, base - TAGS * Tag.SIZE, tagWidth, TAGS * Tag.SIZE));
        } else {
            hud.add(new Rect(screen.insetLeft(), top, w - screen.insetLeft() - screen.insetRight(), STATUS_SMALL));
            hud.add(new Rect(screen.insetLeft(), toolbarTop - 2 - LOG_MOBILE_HEIGHT,
                    w - screen.insetLeft() - screen.insetRight(), LOG_MOBILE_HEIGHT));
            hud.add(new Rect(tagLeft, toolbarTop - TAGS * Tag.SIZE, tagWidth, TAGS * Tag.SIZE));
        }
        return hud;
    }

    /**
     * The menu pane with its danger tag, at the top right ({@code GameScene.java:403}, {@code :464-466}),
     * and the depth and challenge icons it draws left of its background, 7 each
     * ({@code MenuPane.java:186}, {@code :198}).
     */
    static Rect menu(Screen screen) {
        float width = MENU_WIDTH + MENU_ICONS;
        return new Rect(screen.width() - screen.insetRight() - width, screen.top(), width, MENU_HEIGHT);
    }

    /**
     * The boss bar and the boss's buff rows below it, centred at the top by the bar's own width: large
     * with interface size 1 or 2, 7 below the top in landscape and 26 in portrait
     * ({@code GameScene.java:500-502}, {@code BossHealthBar.java:78-80}). The buffs are laid out from the
     * health bar's corner (x+30, y+2 large; x+15, y+3 small), at (+1, +12) 102 by 34 when large and at
     * (0, +5) 47 by 16 when small ({@code BossHealthBar.java:145-146}, {@code :157-163}), so the whole
     * reaches x+133, y+48 large and x+64, y+24 small.
     */
    static Rect boss(Screen screen) {
        boolean large = screen.interfaceSize() != 0;
        float barWidth = large ? 128 : 64;
        float width = large ? 133 : 64;
        float height = large ? 48 : 24;
        float y = screen.top() + (screen.landscape() ? 7 : 26);
        return new Rect((screen.width() - barWidth) / 2, y, width, height);
    }

    /**
     * Where the Panel goes on {@code screen}.
     *
     * @param collapsed whether the human has collapsed the Panel
     */
    public static Layout of(Screen screen, boolean collapsed) {
        if (collapsed || screen.interfaceSize() == 0 || screen.height() < MIN_VIEW_HEIGHT) {
            return strip(screen);
        }
        float reservedRight = screen.interfaceSize() == 2 ? InventoryPane.WIDTH : (screen.tagsOnLeft() ? 0 : Tag.SIZE);
        float right = screen.width() - screen.insetRight() - reservedRight - GAP;
        float width = Math.min(TARGET_WIDTH, right - screen.insetLeft() - MIN_UNCOVERED);
        if (width < MIN_WIDTH) {
            return strip(screen);
        }
        float x = right - width;
        float top = below(screen, x, right, screen.top() + GAP);
        // Above the toolbar and the large status pane, whichever is higher: with interface size 1 the
        // status pane (39 high) stands above the toolbar (26), both at the bottom (GameScene.java:487, :555).
        float statusTop = screen.height() - STATUS_LARGE - screen.insetBottom();
        float bottom = Math.min(screen.toolbarTop(), statusTop) - GAP;
        if (bottom - top < MIN_PANEL_HEIGHT) {
            return strip(screen);
        }
        // The camera looks this far right of the hero, so the hero is drawn at the middle of the
        // uncovered map [insetLeft, x) rather than at the middle of the view.
        float offset = screen.width() / 2f - (screen.insetLeft() + x) / 2f;
        return new Layout(Form.FULL, new Rect(x, top, width, bottom - top), offset);
    }

    /**
     * The collapsed Panel: the Mode strip alone, left of the menu pane and below whatever top HUD
     * shares its columns (the boss bar; the status pane in the mobile layout).
     */
    static Layout strip(Screen screen) {
        float right = screen.width() - screen.insetRight() - MENU_WIDTH - MENU_ICONS - GAP;
        // Right of the tag column when the tags are on the left, which on a short screen reaches up to
        // the strip's row; on a narrow screen the strip is then narrower than 160 (docs/ideas.md, story 5.2).
        float left = screen.insetLeft() + (screen.tagsOnLeft() ? Tag.SIZE : 0) + GAP;
        if (screen.interfaceSize() > 0) {
            // And right of the game log's column, 160 wide at the left (GameScene.java:1018-1024).
            left = Math.max(left, LOG_WIDTH + GAP);
        }
        float width = Math.min(STRIP_WIDTH, right - left);
        float x = right - width;
        float top = below(screen, x, right, screen.top() + GAP);
        return new Layout(Form.STRIP, new Rect(x, top, width, STRIP_HEIGHT), 0);
    }

    /** {@code floor}, or just below the lowest top HUD element whose columns {@code [left, right)} overlap. */
    private static float below(Screen screen, float left, float right, float floor) {
        Rect column = new Rect(left, 0, right - left, 1);
        float top = floor;
        List<Rect> above = new ArrayList<>(List.of(menu(screen), boss(screen)));
        if (screen.interfaceSize() == 0) {
            above.add(new Rect(screen.insetLeft(), screen.top(), screen.width() - screen.insetLeft() - screen.insetRight(), STATUS_SMALL));
        }
        for (Rect element : above) {
            if (element.sharesColumns(column)) {
                top = Math.max(top, element.bottom() + GAP);
            }
        }
        return top;
    }
}
