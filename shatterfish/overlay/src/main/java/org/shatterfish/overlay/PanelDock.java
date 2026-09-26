package org.shatterfish.overlay;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Scene;

/**
 * Keeps the Panel on the play scene and the camera offset the Panel needs (story 5.2).
 *
 * <p>{@link #frame(Scene)} runs on the render thread after the game's own update, in which the play
 * scene lays out its tags and resets the camera's offset ({@code SPD-classes/.../noosa/Game.java:269-283},
 * {@code core/.../scenes/GameScene.java:931-958}, {@code :993-999}), and before the next frame is drawn
 * ({@code Game.java:150-171}). Each play scene gets its own Panel, added in front of the HUD the scene
 * built; windows the game shows later are added in front of it ({@code GameScene.java:1420-1441}).
 */
final class PanelDock {

    private Panel panel;
    private boolean collapsed;

    /** The Panel on {@code scene} if it is a play scene, placed for this frame, and the offset set. */
    void frame(Scene scene) {
        if (!(scene instanceof GameScene)) {
            return;
        }
        if (panel == null || panel.parent != scene) {
            panel = new Panel();
            panel.camera = PixelScene.uiCamera;
            scene.add(panel);
        }
        PanelLayout.Layout layout = PanelLayout.of(PanelLayout.current(), collapsed);
        if (!layout.equals(panel.placed()) && Gdx.app != null) {
            // Where the Panel went, once per change: the record of a launch nobody watched.
            Gdx.app.log("shatterfish", "the Panel: " + layout.form() + " at " + layout.rect() + " on a UI view of "
                    + PixelScene.uiCamera.width + "x" + PixelScene.uiCamera.height + " (interface size "
                    + SPDSettings.interfaceSize() + ", camera offset " + layout.offsetUi() + " UI px)");
        }
        panel.place(layout);
        Camera world = Camera.main;
        PanelCamera.apply(world, PanelCamera.world(layout.offsetUi(), PixelScene.uiCamera.zoom, world.zoom));
    }

    /** Collapses the Panel to the Mode strip, or restores it (UX-DR2; the hotkey is story 5.11's). */
    void collapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    boolean collapsed() {
        return collapsed;
    }

    /** The Panel on the current play scene, or null before the first one. */
    Panel panel() {
        return panel;
    }
}
