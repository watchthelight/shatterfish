package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.ui.InventoryPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.overlay.PanelLayout.Form;
import org.shatterfish.overlay.PanelLayout.Layout;
import org.shatterfish.overlay.PanelLayout.Rect;
import org.shatterfish.overlay.PanelLayout.Screen;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the Panel goes, as a function of the screen (story 5.2, UX-DR1, UX-DR2): the docking, the
 * width, the collapse thresholds, the camera offset, and "never over the HUD" over a grid of screens.
 */
class PanelLayoutTest {

    private static final float EPS = 1e-4f;

    @Test
    @DisplayName("with room, the Panel is 200 wide, left of the inventory pane, above the toolbar")
    void docks_left_of_the_inventory() {
        Screen screen = Screen.of(1000, 500, 2);
        Layout layout = PanelLayout.of(screen, false);
        assertEquals(Form.FULL, layout.form());
        Rect rect = layout.rect();
        assertEquals(200, rect.width(), EPS, "the target width, 200 UI pixels (UX-DR2)");
        assertEquals(1000 - InventoryPane.WIDTH - PanelLayout.GAP, rect.right(), EPS, "right side at the pane's left, less the gap");
        assertEquals(screen.toolbarTop() - PanelLayout.GAP, rect.bottom(), EPS, "bottom at the toolbar's top");
        assertEquals(screen.top() + PanelLayout.GAP, rect.y(), EPS, "clear of the boss bar, so at the top");
        // Narrower, the Panel's columns reach the centred boss bar, and it starts below it.
        Screen narrower = Screen.of(640, 360, 2);
        Rect under = PanelLayout.of(narrower, false).rect();
        assertTrue(under.x() < PanelLayout.boss(narrower).right(), "the columns overlap here");
        assertEquals(PanelLayout.boss(narrower).bottom() + PanelLayout.GAP, under.y(), EPS);
    }

    @Test
    @DisplayName("with no inventory pane, the Panel is left of the tag column and below the menu pane")
    void docks_left_of_the_tags() {
        Screen screen = Screen.of(480, 270, 1);
        Layout layout = PanelLayout.of(screen, false);
        assertEquals(Form.FULL, layout.form());
        Rect rect = layout.rect();
        assertEquals(480 - Tag.SIZE - PanelLayout.GAP, rect.right(), EPS);
        assertTrue(rect.y() >= PanelLayout.menu(screen).bottom(), "the menu pane and its danger tag share its columns");
        assertTrue(rect.bottom() <= 270 - PanelLayout.STATUS_LARGE, "above the large status pane, which stands above the toolbar here");
    }

    @Test
    @DisplayName("tags on the left free the right edge")
    void tags_on_the_left() {
        Screen screen = new Screen(480, 270, 0, 0, 0, 0, 1, true, true);
        assertEquals(480 - PanelLayout.GAP, PanelLayout.of(screen, false).rect().right(), EPS);
    }

    @Test
    @DisplayName("the width shrinks from 200 to 160 to keep 200 of map, and below that the Panel collapses")
    void width_then_collapse() {
        // With interface size 2 the right side is W - 187 - 2, so the width is W - 389 until it reaches 200.
        assertEquals(200, PanelLayout.of(Screen.of(589, 400, 2), false).rect().width(), EPS);
        assertEquals(199, PanelLayout.of(Screen.of(588, 400, 2), false).rect().width(), EPS);
        assertEquals(160, PanelLayout.of(Screen.of(549, 400, 2), false).rect().width(), EPS);
        assertEquals(Form.FULL, PanelLayout.of(Screen.of(549, 400, 2), false).form());
        assertEquals(Form.STRIP, PanelLayout.of(Screen.of(548.9f, 400, 2), false).form(), "below 160 wide it would leave under 200 of map");
        for (float w = 549; w < 1400; w += 3) {
            Layout layout = PanelLayout.of(Screen.of(w, 400, 2), false);
            float uncovered = layout.rect().x();
            assertTrue(uncovered >= PanelLayout.MIN_UNCOVERED - EPS, "at least 200 of map at width " + w);
            assertTrue(layout.rect().width() >= PanelLayout.MIN_WIDTH && layout.rect().width() <= PanelLayout.TARGET_WIDTH);
        }
    }

    @Test
    @DisplayName("a view shorter than 200 collapses the Panel, and 200 does not")
    void view_height() {
        assertEquals(Form.STRIP, PanelLayout.of(Screen.of(700, 199, 1), false).form());
        assertEquals(Form.FULL, PanelLayout.of(Screen.of(700, 200, 1), false).form());
    }

    @Test
    @DisplayName("a full Panel too short for the strip and three log lines collapses")
    void too_short() {
        // Interface size 2 at the minimum height with a bottom inset of 10: the toolbar's top is
        // 200 - 108 - 10 = 82, and the boss bar, whose columns the Panel shares, ends at 37.
        Screen screen = new Screen(900, 200, 0, 0, 0, 10, 2, false, true);
        assertTrue(screen.toolbarTop() - PanelLayout.GAP - (PanelLayout.boss(screen).bottom() + PanelLayout.GAP)
                < PanelLayout.MIN_PANEL_HEIGHT);
        assertEquals(Form.STRIP, PanelLayout.of(screen, false).form());
    }

    @Test
    @DisplayName("the mobile layout and the human's toggle collapse it")
    void mobile_and_toggle() {
        assertEquals(Form.STRIP, PanelLayout.of(Screen.of(1400, 800, 0), false).form());
        assertEquals(Form.STRIP, PanelLayout.of(Screen.of(1400, 800, 1), true).form());
        assertEquals(Form.FULL, PanelLayout.of(Screen.of(1400, 800, 1), false).form());
    }

    @Test
    @DisplayName("the camera offset puts the hero at the middle of the uncovered map, and is 0 when collapsed")
    void offset() {
        Screen screen = Screen.of(1000, 500, 2);
        Layout layout = PanelLayout.of(screen, false);
        float middleOfMap = (0 + layout.rect().x()) / 2f;
        assertEquals(1000 / 2f - middleOfMap, layout.offsetUi(), EPS);
        assertTrue(layout.offsetUi() > 0, "the camera looks right of the hero, so the hero is drawn left of centre");
        Screen inset = new Screen(1000, 500, 20, 0, 10, 0, 2, false, true);
        Layout withInsets = PanelLayout.of(inset, false);
        assertEquals(500 - (20 + withInsets.rect().x()) / 2f, withInsets.offsetUi(), EPS);
        assertEquals(0, PanelLayout.of(Screen.of(400, 300, 2), false).offsetUi(), EPS);
        assertEquals(0, PanelLayout.of(screen, true).offsetUi(), EPS);
    }

    @Test
    @DisplayName("the collapsed strip is 160 wide, left of the menu pane, below the mobile status pane")
    void the_strip() {
        Screen mobile = Screen.of(420, 700, 0);
        Rect strip = PanelLayout.of(mobile, false).rect();
        assertEquals(PanelLayout.STRIP_WIDTH, strip.width(), EPS);
        assertEquals(PanelLayout.STRIP_HEIGHT, strip.height(), EPS);
        assertEquals(420 - PanelLayout.MENU_WIDTH - PanelLayout.GAP, strip.right(), EPS);
        assertTrue(strip.y() >= mobile.top() + PanelLayout.STATUS_SMALL, "below the status pane that spans the top");
    }

    @Test
    @DisplayName("over a grid of screens, the Panel stays on screen and never covers the HUD")
    void never_over_the_hud() {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        float[][] insets = {{0, 0, 0, 0}, {8, 0, 6, 4}, {0, 12, 0, 0}};
        for (int size = 0; size <= 2; size++) {
            for (boolean flip : new boolean[]{false, true}) {
                for (float[] inset : insets) {
                    for (float w = 240; w <= 1400; w += 11) {
                        for (float h = 160; h <= 900; h += 17) {
                            if (size > 0 && (w < 360 || h < 200)) {
                                continue;   // the game forces the mobile layout below 360 by 200 (SPDSettings.java:140-146)
                            }
                            Screen screen = new Screen(w, h, inset[0], inset[1], inset[2], inset[3], size, flip, size > 0 || w > h);
                            for (boolean collapsed : new boolean[]{false, true}) {
                                Layout layout = PanelLayout.of(screen, collapsed);
                                checked++;
                                if (!layout.rect().within(w, h)) {
                                    failures.add(screen + " -> off screen " + layout);
                                }
                                if (layout.form() == Form.FULL
                                        && layout.rect().x() - screen.insetLeft() < PanelLayout.MIN_UNCOVERED - EPS) {
                                    failures.add(screen + " -> leaves under 200 of map " + layout);
                                }
                                for (Rect hud : PanelLayout.hud(screen)) {
                                    if (layout.rect().intersects(hud)) {
                                        failures.add(screen + " -> " + layout + " covers " + hud);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10_000, "the grid is large: " + checked);
        assertTrue(failures.isEmpty(), failures.size() + " placements cover the HUD or leave the screen, e.g. "
                + failures.subList(0, Math.min(5, failures.size())));
    }

    @Test
    @DisplayName("a Rect intersects by area, not by touching edges")
    void rect_intersection() {
        Rect a = new Rect(0, 0, 10, 10);
        assertTrue(a.intersects(new Rect(9, 9, 5, 5)));
        assertFalse(a.intersects(new Rect(10, 0, 5, 5)), "touching edges share no area");
        assertFalse(a.intersects(new Rect(0, 10, 5, 5)));
    }
}
