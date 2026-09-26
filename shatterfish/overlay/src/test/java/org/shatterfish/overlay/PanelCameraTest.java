package org.shatterfish.overlay;

import com.watabou.noosa.Camera;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Overlay's horizontal camera offset (story 5.2): the units, and the game's vertical part kept. */
class PanelCameraTest {

    @Test
    @DisplayName("UI pixels times the UI zoom over the world zoom are world units")
    void units() {
        assertEquals(50f, PanelCamera.world(100, 2, 4), 1e-5f);
        assertEquals(150f, PanelCamera.world(100, 3, 2), 1e-5f);
        assertEquals(0f, PanelCamera.world(0, 3, 2), 1e-5f);
    }

    @Test
    @DisplayName("the horizontal offset is set, the vertical one kept, and an unchanged offset is left alone")
    void keeps_the_vertical_part() {
        Camera camera = new Camera(0, 0, 400, 300, 2);
        camera.setCenterOffset(0, 7);
        float scrollBefore = camera.scroll.x;
        assertTrue(PanelCamera.apply(camera, 12));
        assertEquals(12, camera.centerOffset.x, 1e-5f);
        assertEquals(7, camera.centerOffset.y, 1e-5f, "the game's vertical part is kept");
        assertEquals(scrollBefore + 12, camera.scroll.x, 1e-5f, "the scroll moves by the change, as setCenterOffset does");
        assertFalse(PanelCamera.apply(camera, 12), "the same offset again changes nothing");
        assertFalse(PanelCamera.apply(camera, 12 + PanelCamera.EPSILON / 2));
    }
}
