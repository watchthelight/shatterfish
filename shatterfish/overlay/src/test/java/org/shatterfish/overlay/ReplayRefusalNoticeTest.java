package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.scene.HeadlessScene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusal notice (story 8.5, FR-46): a log that is not played starts no Run, so the reason is drawn by the
 * game's own toolkit (a Chrome frame, a text block in the danger colour) over whatever scene is in front. Built over
 * the headless boot the way the other Panel fixtures are; the live look is the product owner's checklist.
 */
class ReplayRefusalNoticeTest {

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

    private HeadlessScene scene() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        width = Game.width;
        height = Game.height;
        interfaceSize = SPDSettings.interfaceSize();
        boot.game().destroy();
        Game.width = 1600;
        Game.height = 900;
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return scene;
    }

    @Test
    @DisplayName("the notice says the reason on the scene in front, in the danger colour, inside the screen, and only once")
    void the_notice_is_drawn() {
        HeadlessScene scene = scene();
        PanelDock dock = new PanelDock();
        assertNull(dock.notice(), "nothing is said before a refusal");
        String reason = "REPLAY REFUSED: the log does not verify: line 3 does not follow line 2";
        dock.refusal(reason);
        dock.frame(scene, null, true);
        RefusalNotice notice = dock.notice();
        assertNotNull(notice);
        assertSame(scene, notice.parent, "on the scene in front");
        assertEquals(reason, notice.said());
        assertEquals(RefusalNotice.DANGER, 0xFF5555);
        assertNotNull(notice.frame(), "a Chrome frame, the game's own");
        assertTrue(notice.width() > 0 && notice.height() > 0);
        assertTrue(notice.right() <= PixelScene.uiCamera.width, "inside the screen's width");

        dock.frame(scene, null, true);
        assertSame(notice, dock.notice(), "the same notice on the next frame, not another");
    }
}
