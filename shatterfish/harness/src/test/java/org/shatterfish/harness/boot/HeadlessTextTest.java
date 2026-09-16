package org.shatterfish.harness.boot;

import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.Game;
import com.watabou.utils.DeviceCompat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text renders headlessly through FreeType, as ADR-0015 says it does: the virtual display the
 * boot declares is the back buffer, so a text block asks for the size a desktop asks for and
 * gets a font, and the game logs no error while a Run plays (story 1.21, which found every block
 * asking for size zero under libGDX's mock graphics, and FreeType's refusal logged on every
 * render of the game log).
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class HeadlessTextTest {

    @Test
    @DisplayName("the back buffer is the virtual display, one real pixel per logical pixel")
    void the_back_buffer_is_the_display() {
        HeadlessBoot.ensure();
        assertEquals(HeadlessBoot.WIDTH, Gdx.graphics.getBackBufferWidth());
        assertEquals(HeadlessBoot.HEIGHT, Gdx.graphics.getBackBufferHeight());
        assertEquals(Game.width, Gdx.graphics.getWidth());
        assertEquals(Game.height, Gdx.graphics.getHeight());
        assertEquals(1f, DeviceCompat.getRealPixelScaleX());
        assertEquals(1f, DeviceCompat.getRealPixelScaleY());
    }

    @Test
    @DisplayName("a text block in a Run gets a font at the desktop's size, and the game logs no error over the first waits")
    void a_text_block_measures_and_nothing_is_logged() {
        HeadlessBoot.ensure();
        List<String> errors = new ArrayList<>();
        ApplicationLogger before = Gdx.app.getApplicationLogger();
        Gdx.app.setApplicationLogger(new ApplicationLogger() {
            @Override
            public void log(String tag, String message) {
            }

            @Override
            public void log(String tag, String message, Throwable exception) {
            }

            @Override
            public void error(String tag, String message) {
                errors.add(tag + ": " + message);
            }

            @Override
            public void error(String tag, String message, Throwable exception) {
                errors.add(tag + ": " + message + " (" + exception + ")");
            }

            @Override
            public void debug(String tag, String message) {
            }

            @Override
            public void debug(String tag, String message, Throwable exception) {
            }
        });
        try (HeadlessDriver driver = HeadlessDriver.start(14_142_135L, HeroClass.WARRIOR, 7L)) {
            driver.stepToInputWait();
            int size = 6 * PixelScene.defaultZoom;
            assertNotNull(Game.platform.getFont(size, "Hello", true, true), "the font at the size renderTextBlock asks for");
            RenderedTextBlock block = PixelScene.renderTextBlock("Hello", 6);
            assertTrue(block.width() > 0 && block.height() > 0, "measured " + block.width() + " by " + block.height());
            ActionExecutor executor = new ActionExecutor();
            for (int i = 0; i < 5; i++) {
                executor.execute(new Observer().observe(), new Action.Wait());
                if (driver.stepToInputWait().reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                    break;
                }
            }
        } finally {
            Gdx.app.setApplicationLogger(before);
        }
        assertTrue(errors.isEmpty(), "the game logged: " + errors);
    }
}
