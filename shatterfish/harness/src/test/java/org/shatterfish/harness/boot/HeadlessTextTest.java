package org.shatterfish.harness.boot;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.Game;
import com.watabou.utils.DeviceCompat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text renders headlessly through FreeType, as ADR-0015 says it does: the virtual display the
 * boot declares is the back buffer, so a text block asks for the size a desktop asks for and
 * gets a font (story 1.21, which found every block asking for size zero under libGDX's mock
 * graphics, and FreeType's refusal logged on every render of the game log).
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HeadlessTextTest {

    @Test
    @DisplayName("the back buffer is the virtual display, one real pixel per logical pixel, and no frame is ever rendered")
    void the_back_buffer_is_the_display() {
        HeadlessBoot.ensure();
        assertEquals(HeadlessBoot.WIDTH, Gdx.graphics.getBackBufferWidth());
        assertEquals(HeadlessBoot.HEIGHT, Gdx.graphics.getBackBufferHeight());
        assertEquals(Game.width, Gdx.graphics.getWidth());
        assertEquals(1f, DeviceCompat.getRealPixelScaleX());
        assertEquals(1f, DeviceCompat.getRealPixelScaleY());
        assertEquals(-1, Gdx.graphics.getFrameId(), "the backend renders nothing");
    }

    @Test
    @DisplayName("a text block in a Run gets a font at the desktop's size and measures wider than nothing")
    void a_text_block_measures() {
        try (HeadlessDriver driver = HeadlessDriver.start(14_142_135L, HeroClass.WARRIOR, 7L)) {
            driver.stepToInputWait();
            int size = 6 * PixelScene.defaultZoom;
            assertNotNull(Game.platform.getFont(size, "Hello", true, true), "the font at the size renderTextBlock asks for");
            RenderedTextBlock block = PixelScene.renderTextBlock("Hello", 6);
            assertTrue(block.width() > 0 && block.height() > 0, "measured " + block.width() + " by " + block.height());
        }
    }
}
