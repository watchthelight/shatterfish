package org.shatterfish.overlay;

import com.watabou.noosa.Camera;

/**
 * The Overlay's horizontal camera offset (story 5.2, UX-DR1).
 *
 * <p>The play scene's own offset is vertical only and conditional, and {@code GameScene.layoutTags}
 * sets it to {@code (0, y)} whenever it runs ({@code core/.../scenes/GameScene.java:993-999}); only its
 * vertical part is read anywhere else ({@code GameScene.java:1155}, {@code :1594},
 * {@code PixelScene.java:386}). So the Overlay owns the horizontal part and puts it back after every
 * update, keeping the game's vertical part as it is. {@code Camera.setCenterOffset} moves the scroll by
 * the change ({@code SPD-classes/.../noosa/Camera.java:241-249}), so restoring the horizontal part
 * after a reset is no jump: the reset and the restore cancel within one update, before the next draw.
 */
final class PanelCamera {

    /** A difference smaller than this is no change, so an unchanged offset is never re-set. */
    static final float EPSILON = 0.001f;

    private PanelCamera() {
    }

    /**
     * The offset in world units: UI pixels times the UI zoom are window pixels, and window pixels
     * over the world camera's zoom are world units.
     */
    static float world(float offsetUi, float uiZoom, float worldZoom) {
        return offsetUi * uiZoom / worldZoom;
    }

    /**
     * Sets {@code camera}'s horizontal offset to {@code x}, keeping its vertical offset.
     *
     * @return whether anything changed
     */
    static boolean apply(Camera camera, float x) {
        if (Math.abs(camera.centerOffset.x - x) < EPSILON) {
            return false;
        }
        camera.setCenterOffset(x, camera.centerOffset.y);
        return true;
    }
}
