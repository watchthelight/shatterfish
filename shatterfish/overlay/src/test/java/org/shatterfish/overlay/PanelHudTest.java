package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.BossHealthBar;
import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Visual;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Panel against a real play scene (story 5.2): the HUD model of {@link PanelLayout} holds the game's
 * own HUD as it is laid out (each component with every child it draws, a boss assigned so the boss bar
 * and its buff rows are there, and every tag laid out), the Panel added to the scene misses it, its
 * frames are the game's nine-patches, the offset it sets is the one the next frame draws with, the
 * scene's fade stays in front of it, it dims under the game's prompt, and it carries the oracle label.
 *
 * <p>The scene is the game's {@code GameScene} ({@code HeadlessScene} adds nothing to its create), built
 * over the headless boot at every interface size and at common window sizes.
 */
class PanelHudTest {

    private static final long SEED = 12345;
    private static final String[] HUD_FIELDS = {"menu", "status", "toolbar", "inventory", "boss", "log",
            "attack", "loot", "action", "resume"};
    /** Laid out whether shown or not: tags appear and go, and the boss bar shows while a boss lives. */
    private static final Set<String> LAID_OUT_HIDDEN = Set.of("boss", "attack", "loot", "action", "resume");
    private static final String[] TAG_FLAGS = {"tagAttack", "tagLoot", "tagAction", "tagResume"};
    private static final int[][] WINDOWS = {{1280, 720}, {1600, 900}, {1920, 1080}, {2560, 1440}, {800, 600}};

    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        boot.game().destroy();
        BossHealthBar.assignBoss(null);
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    /** A play scene at {@code size} in a window of {@code w} by {@code h}; with a boss, its bar is built. */
    private GameScene scene(int size, int w, int h, boolean boss) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        BossHealthBar.assignBoss(null);
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(size);
        Game.width = w;
        Game.height = h;
        if (boss) {
            // Before the scene: the bar a scene builds for a live boss has the boss's buff rows
            // (BossHealthBar.java:63-66, :120-124).
            BossHealthBar.assignBoss(new Rat());
        }
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    private GameScene scene(int size, int w, int h) {
        return scene(size, w, h, false);
    }

    /** Every tag laid out, as when all four show at once (GameScene.java:1031-1052). */
    private static void allTags(GameScene scene) throws ReflectiveOperationException {
        for (String flag : TAG_FLAGS) {
            Field field = GameScene.class.getDeclaredField(flag);
            field.setAccessible(true);
            field.setBoolean(scene, true);
        }
        GameScene.layoutTags();
    }

    /** The extent of a HUD component: its own rectangle and every shown child it draws, clipped to the screen. */
    private static Rect extent(Gizmo gizmo, float w, float h) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        if (gizmo instanceof Component component && component.width() > 0 && component.height() > 0) {
            grow(box, component.left(), component.top(), component.right(), component.bottom());
        }
        collect(gizmo, box);
        float left = Math.max(0, box[0]);
        float top = Math.max(0, box[1]);
        float right = Math.min(w, box[2]);
        float bottom = Math.min(h, box[3]);
        if (right <= left || bottom <= top) {
            return null;   // nothing drawn on screen: a tag that has not appeared, an empty log
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static void collect(Gizmo gizmo, float[] box) {
        if (!(gizmo instanceof Group group)) {
            return;
        }
        for (Gizmo child : group.shatterfishMembers()) {
            if (child == null || !child.visible || !child.exists) {
                continue;
            }
            if (child instanceof Visual visual && visual.width() > 0 && visual.height() > 0) {
                grow(box, visual.x, visual.y, visual.x + visual.width(), visual.y + visual.height());
            }
            if (child instanceof Component component && component.width() > 0 && component.height() > 0) {
                grow(box, component.left(), component.top(), component.right(), component.bottom());
            }
            collect(child, box);
        }
    }

    private static void grow(float[] box, float left, float top, float right, float bottom) {
        box[0] = Math.min(box[0], left);
        box[1] = Math.min(box[1], top);
        box[2] = Math.max(box[2], right);
        box[3] = Math.max(box[3], bottom);
    }

    /** The game's HUD components, each as its extent; a hidden one only if it is laid out regardless. */
    private static List<Rect> components(GameScene scene) throws ReflectiveOperationException {
        float w = PixelScene.uiCamera.width;
        float h = PixelScene.uiCamera.height;
        List<Rect> out = new ArrayList<>();
        for (String name : HUD_FIELDS) {
            Field field = GameScene.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(scene);
            if (!(value instanceof Gizmo gizmo) || !gizmo.exists || (!gizmo.visible && !LAID_OUT_HIDDEN.contains(name))) {
                continue;
            }
            Rect extent = extent(gizmo, w, h);
            if (extent != null) {
                out.add(extent);
            }
        }
        return out;
    }

    private static boolean inside(Rect inner, Rect outer) {
        float e = 0.01f;
        return inner.x() >= outer.x() - e && inner.y() >= outer.y() - e
                && inner.right() <= outer.right() + e && inner.bottom() <= outer.bottom() + e;
    }

    @Test
    @DisplayName("the HUD model holds the game's own HUD, each component with every child, a boss and all tags")
    void the_model_holds_the_hud() throws ReflectiveOperationException {
        int scenes = 0;
        boolean sawBossBuffs = false;
        for (int size = 0; size <= 2; size++) {
            for (int[] window : WINDOWS) {
                GameScene scene = scene(size, window[0], window[1], true);
                allTags(scene);
                Screen screen = PanelLayout.current();
                assertEquals(size, screen.interfaceSize(), "the game kept the interface size at " + window[0] + "x" + window[1]);
                List<Rect> model = PanelLayout.hud(screen);
                for (Rect real : components(scene)) {
                    boolean held = model.stream().anyMatch(m -> inside(real, m));
                    assertTrue(held, "size " + size + " at " + window[0] + "x" + window[1] + " (UI " + screen.width() + "x"
                            + screen.height() + "): the game's " + real + " is in no modelled HUD rectangle " + model);
                }
                Rect bossModel = PanelLayout.boss(screen);
                Field bossField = GameScene.class.getDeclaredField("boss");
                bossField.setAccessible(true);
                Rect bossReal = extent((Gizmo) bossField.get(scene), screen.width(), screen.height());
                assertNotNull(bossReal, "the boss bar is laid out");
                if (bossReal.bottom() > bossModel.y() + (size == 0 ? 16 : 30)) {
                    sawBossBuffs = true;   // the buff rows reach below the bar itself
                }
                scenes++;
            }
        }
        assertEquals(3 * WINDOWS.length, scenes);
        assertTrue(sawBossBuffs, "the boss's buff rows were part of what the model held");
    }

    @Test
    @DisplayName("the Panel added to a real play scene misses every HUD component, full or collapsed")
    void the_panel_misses_the_hud() throws ReflectiveOperationException {
        int full = 0;
        for (int size = 0; size <= 2; size++) {
            for (int[] window : WINDOWS) {
                GameScene scene = scene(size, window[0], window[1], true);
                allTags(scene);
                PanelDock dock = new PanelDock();
                for (boolean collapsed : new boolean[]{false, true}) {
                    dock.collapsed(collapsed);
                    dock.frame(scene, null);
                    Panel panel = dock.panel();
                    assertSame(scene, panel.parent, "the Panel is on the play scene");
                    assertSame(PixelScene.uiCamera, panel.camera, "on the UI camera, like the game's own HUD");
                    Rect placed = panel.placed().rect();
                    assertTrue(placed.within(PixelScene.uiCamera.width, PixelScene.uiCamera.height), "on screen: " + placed);
                    for (Rect real : components(scene)) {
                        assertFalse(placed.intersects(real), "size " + size + " at " + window[0] + "x" + window[1]
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
        dock.frame(scene, null);
        Panel panel = dock.panel();
        assertNotNull(panel.frame());
        assertSame(TextureCache.get(Assets.Interfaces.CHROME), panel.frame().texture, "the Panel's frame is cut from the game's chrome");
        assertSame(TextureCache.get(Assets.Interfaces.CHROME), panel.strip().texture, "and so is the Mode strip's");
        assertEquals(PanelLayout.Form.FULL, panel.placed().form());
        assertTrue(panel.frame().visible);
        dock.collapsed(true);
        dock.frame(scene, null);
        assertTrue(!panel.frame().visible && panel.strip().visible, "collapsed, only the Mode strip shows");
    }

    @Test
    @DisplayName("a new play scene gets its own Panel from the same dock, on the new UI camera")
    void a_panel_per_scene() {
        PanelDock dock = new PanelDock();
        GameScene first = scene(1, 1920, 1080);
        dock.frame(first, null);
        Panel before = dock.panel();
        GameScene second = scene(1, 1600, 900);   // a resize or a floor change builds a new play scene
        dock.frame(second, null);
        assertSame(second, dock.panel().parent, "the Panel is on the scene in front");
        assertTrue(dock.panel() != before, "a new Panel, not the old scene's");
        assertSame(PixelScene.uiCamera, dock.panel().camera, "on the new scene's UI camera");
    }

    @Test
    @DisplayName("the horizontal offset survives the scene's own layout pass, and the vertical part stays the game's")
    void offset_survives_the_layout_pass() {
        GameScene scene = scene(1, 1920, 1080);
        PanelDock dock = new PanelDock();
        dock.frame(scene, null);
        Layout layout = dock.panel().placed();
        assertEquals(PanelLayout.Form.FULL, layout.form());
        float expected = PanelCamera.world(layout.offsetUi(), PixelScene.uiCamera.zoom, Camera.main.zoom);
        assertTrue(expected > 0);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f, "the Overlay's offset is applied");

        GameScene.layoutTags();   // the game's own layout pass: (0, y) (GameScene.java:993-999)
        assertEquals(0, Camera.main.centerOffset.x, 1e-4f, "the game's pass reset it, as the story says it does");
        dock.frame(scene, null);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f, "and the Overlay put it back");

        Camera.main.setCenterOffset(0, 9);   // a vertical offset the game might have set
        dock.frame(scene, null);
        assertEquals(expected, Camera.main.centerOffset.x, 1e-4f);
        assertEquals(9, Camera.main.centerOffset.y, 1e-4f, "the game's vertical part is kept");
    }

    @Test
    @DisplayName("the frame drawn after the update has the Panel's offset: the matrix is built after it is set")
    void the_offset_is_drawn_this_frame() {
        GameScene scene = scene(1, 1920, 1080);
        PanelDock dock = new PanelDock();
        float elapsed = Game.elapsed;
        Game.elapsed = 0;   // no pan and no shake: the camera moves only by the offset
        try {
            // The Overlay's order: the scene's update (its layout pass stands in for it), the Panel, the matrices.
            dock.step(scene, GameScene::layoutTags, null);
            assertTrue(Math.abs(Camera.main.centerOffset.x) > 1, "an offset is set");
            float drawn = Camera.main.matrix[12];
            Camera.main.update();   // the matrix the camera's state now gives
            assertEquals(Camera.main.matrix[12], drawn, 1e-6f, "the matrix drawn next already has the offset");

            // The game's own order with the Panel after it would draw the reset offset for a frame.
            GameScene.layoutTags();
            Camera.updateAll();
            dock.frame(scene, null);
            float late = Camera.main.matrix[12];
            Camera.main.update();
            assertNotEquals(Camera.main.matrix[12], late, 1e-6f, "placing the Panel after the matrices is a frame late");
        } finally {
            Game.elapsed = elapsed;
        }
    }

    @Test
    @DisplayName("the scene's fade from black stays in front of the Panel")
    void the_fade_stays_in_front() {
        GameScene scene = scene(1, 1920, 1080);
        Gizmo fader = null;
        for (Gizmo member : scene.shatterfishMembers()) {
            if (member != null && member.getClass().getName().equals(PanelDock.FADER)) {
                fader = member;
            }
        }
        assertNotNull(fader, "a new play scene fades in from black (GameScene.java:784)");
        PanelDock dock = new PanelDock();
        dock.frame(scene, null);
        assertTrue(scene.indexOf(fader) > scene.indexOf(dock.panel()),
                "the fade is drawn after the Panel, so the Panel does not show on black while it fades");
    }

    @Test
    @DisplayName("the Panel dims while the game's cell prompt is showing, which is drawn over it")
    void dims_under_the_prompt() {
        GameScene scene = scene(1, 1600, 900);
        PanelDock dock = new PanelDock();
        dock.frame(scene, null);
        assertFalse(dock.panel().dimmed(), "nothing of the game's is over it yet");
        GameScene.selectCell(new CellSelector.Listener() {
            @Override
            public void onSelect(Integer cell) {
            }

            @Override
            public String prompt() {
                return "Choose a location to target";
            }
        });
        dock.frame(scene, null);
        assertTrue(PanelDock.covered(scene), "the prompt is a toast on the scene (GameScene.java:1126-1149)");
        assertTrue(dock.panel().dimmed(), "and the Panel dims under it");
        assertEquals(Panel.DIMMED, dock.panel().frame().alpha(), 1e-6f);
    }

    @Test
    @DisplayName("an oracle Run's Mode strip carries the ORACLE label in the oracle colour, full or collapsed")
    void the_oracle_label() {
        GameScene scene = scene(1, 1920, 1080);
        PanelDock dock = new PanelDock();
        dock.frame(scene, null);
        assertFalse(dock.panel().oracleLabel().visible, "a fair Run has no label");
        dock.oracle(true);
        assertTrue(dock.panel().oracleLabel().visible, "an oracle Run's does");
        dock.collapsed(true);
        dock.frame(scene, null);
        assertTrue(dock.panel().oracleLabel().visible, "and keeps it when the Panel is collapsed");
        Rect strip = dock.panel().placed().rect();
        assertTrue(dock.panel().oracleLabel().left() >= strip.x() && dock.panel().oracleLabel().right() <= strip.right() + 0.01f,
                "inside the Mode strip");
        PanelDock another = new PanelDock();
        another.oracle(true);
        another.frame(scene(1, 1600, 900), null);
        assertTrue(another.panel().oracleLabel().visible, "a Panel made after the flag was set carries it too");
    }
}
