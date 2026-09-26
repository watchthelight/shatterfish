package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.ui.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.overlay.PanelLayout.Layout;
import org.shatterfish.overlay.PanelLayout.Rect;
import org.shatterfish.overlay.PanelLayout.Screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Panel against a real play scene (story 5.2): the HUD model of {@link PanelLayout} holds the game's
 * own HUD components as they are laid out, the Panel added to the scene misses them, its frames are the
 * game's nine-patches, and the camera offset survives the scene's own layout pass.
 *
 * <p>The scene is the game's {@code GameScene} ({@code HeadlessScene} adds nothing to its create), built
 * over the headless boot at the interface sizes the Overlay can play on and at common window sizes.
 */
class PanelHudTest {

    private static final long SEED = 12345;
    private static final String[] HUD_FIELDS = {"menu", "status", "toolbar", "inventory", "boss", "log",
            "attack", "loot", "action", "resume"};
    private static final int[][] WINDOWS = {{1280, 720}, {1600, 900}, {1920, 1080}, {2560, 1440}, {800, 600}};

    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    /** A play scene at {@code size} in a window of {@code w} by {@code h}. */
    private GameScene scene(int size, int w, int h) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(size);
        Game.width = w;
        Game.height = h;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    /** The game's HUD components that are shown, as rectangles clipped to the screen. */
    private static List<Rect> components(GameScene scene) throws ReflectiveOperationException {
        float w = PixelScene.uiCamera.width;
        float h = PixelScene.uiCamera.height;
        List<Rect> out = new ArrayList<>();
        for (String name : HUD_FIELDS) {
            Field field = GameScene.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(scene);
            if (!(value instanceof Component component) || !component.visible || !((Gizmo) component).exists) {
                continue;
            }
            float left = Math.max(0, component.left());
            float top = Math.max(0, component.top());
            float right = Math.min(w, component.right());
            float bottom = Math.min(h, component.bottom());
            if (right <= left && bottom <= top) {
                continue;   // never laid out: a tag that has not appeared
            }
            out.add(new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
        }
        return out;
    }

    private static boolean inside(Rect inner, Rect outer) {
        float e = 0.01f;
        return inner.x() >= outer.x() - e && inner.y() >= outer.y() - e
                && inner.right() <= outer.right() + e && inner.bottom() <= outer.bottom() + e;
    }

    @Test
    @DisplayName("the HUD model holds the game's own HUD components, laid out, at interface sizes 0, 1 and 2")
    void the_model_holds_the_hud() throws ReflectiveOperationException {
        int scenes = 0;
        for (int size = 0; size <= 2; size++) {
            for (int[] window : WINDOWS) {
                GameScene scene = scene(size, window[0], window[1]);
                GameScene.layoutTags();
                Screen screen = PanelLayout.current();
                assertEquals(size, screen.interfaceSize(), "the game kept the interface size at " + window[0] + "x" + window[1]);
                List<Rect> model = PanelLayout.hud(screen);
                for (Rect real : components(scene)) {
                    boolean held = model.stream().anyMatch(m -> inside(real, m));
                    assertTrue(held, "size " + size + " at " + window[0] + "x" + window[1] + " (UI " + screen.width() + "x"
                            + screen.height() + "): the game's " + real + " is in no modelled HUD rectangle " + model);
                }
                scenes++;
            }
        }
        assertEquals(3 * WINDOWS.length, scenes);
    }

    @Test
    @DisplayName("the Panel added to a real play scene misses every HUD component, full or collapsed")
    void the_panel_misses_the_hud() throws ReflectiveOperationException {
        int full = 0;
        for (int size = 0; size <= 2; size++) {
            for (int[] window : WINDOWS) {
                GameScene scene = scene(size, window[0], window[1]);
                GameScene.layoutTags();
                PanelDock dock = new PanelDock();
                for (boolean collapsed : new boolean[]{false, true}) {
                    dock.collapsed(collapsed);
                    dock.frame(scene);
                    Panel panel = dock.panel();
                    assertSame(scene, panel.parent, "the Panel is on the play scene");
                    assertSame(PixelScene.uiCamera, panel.camera, "on the UI camera, like the game's own HUD");
                    Rect placed = panel.placed().rect();
                    assertTrue(placed.within(PixelScene.uiCamera.width, PixelScene.uiCamera.height), "on screen: " + placed);
                    for (Rect real : components(scene)) {
                        assertTrue(!placed.intersects(real), "size " + size + " at " + window[0] + "x" + window[1]
                                + ": the Panel " + placed + " covers the game's " + real);
                    }
                    if (panel.placed().form() == PanelLayout.Form.FULL) {
                        full++;
                    }
                }
            }
        }
        assertTrue(full >= 3, "the Panel is shown in full on the larger windows: " + full);
    }

    @Test
    @DisplayName("the Panel is the game's own nine-patches: the heavy toast scrim and the light one for the strip")
    void the_frames_are_the_games() {
        GameScene scene = scene(1, 1920, 1080);
        PanelDock dock = new PanelDock();
        dock.frame(scene);
        Panel panel = dock.panel();
        assertNotNull(panel.frame());
        assertSame(TextureCache.get(Assets.Interfaces.CHROME), panel.frame().texture, "the Panel's frame is cut from the game's chrome");
        assertSame(TextureCache.get(Assets.Interfaces.CHROME), panel.strip().texture, "and so is the Mode strip's");
        assertEquals(PanelLayout.Form.FULL, panel.placed().form());
        assertTrue(panel.frame().visible);
        dock.collapsed(true);
        dock.frame(scene);
        assertTrue(!panel.frame().visible && panel.strip().visible, "collapsed, only the Mode strip shows");
    }

    @Test
    @DisplayName("a new play scene gets its own Panel from the same dock, on the new UI camera")
    void a_panel_per_scene() {
        PanelDock dock = new PanelDock();
        GameScene first = scene(1, 1920, 1080);
        dock.frame(first);
        Panel before = dock.panel();
        GameScene second = scene(1, 1600, 900);   // a resize or a floor change builds a new play scene
        dock.frame(second);
        assertSame(second, dock.panel().parent, "the Panel is on the scene in front");
        assertTrue(dock.panel() != before, "a new Panel, not the old scene's");
        assertSame(PixelScene.uiCamera, dock.panel().camera, "on the new scene's UI camera");
    }

    @Test
    @DisplayName("the horizontal offset survives the scene's own layout pass, and the vertical part stays the game's")
    void offset_survives_the_layout_pass() {
        GameScene scene = scene(1, 1920, 1080);
        PanelDock dock = new PanelDock();
        dock.frame(scene);
        Layout layout = dock.panel().placed();
        assertEquals(PanelLayout.Form.FULL, layout.form());
        float expected = PanelCamera.world(layout.offsetUi(), PixelScene.uiCamera.zoom, Camera.main.zoom);
        assertTrue(expected > 0);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f, "the Overlay's offset is applied");

        GameScene.layoutTags();   // the game's own layout pass: (0, y) (GameScene.java:993-999)
        assertEquals(0, Camera.main.centerOffset.x, 1e-4f, "the game's pass reset it, as the story says it does");
        dock.frame(scene);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f, "and the Overlay put it back");

        Camera.main.setCenterOffset(0, 9);   // a vertical offset the game might have set
        dock.frame(scene);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f);
        assertEquals(9, Camera.main.centerOffset.y, 1e-4f, "the game's vertical part is kept");
    }
}
