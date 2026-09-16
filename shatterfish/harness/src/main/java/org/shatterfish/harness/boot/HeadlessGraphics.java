package org.shatterfish.harness.boot;

import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;

/**
 * The headless backend's graphics, reporting the virtual display the boot declares rather than
 * no display at all.
 *
 * <p>libGDX's {@code MockGraphics} answers zero for every size. The game divides its back buffer
 * width by {@code Game.width} to find the real pixels per logical pixel
 * ({@code SPD-classes/…/utils/DeviceCompat.java:64-71}) and multiplies every text block's font
 * size by it ({@code core/…/scenes/PixelScene.java:337-339}), so under the mock every text block
 * asked FreeType for a size of zero, FreeType refused it, the refusal was logged as a stack trace
 * on every render of the game log, and the block was zoomed by one over zero. Story 1.21's
 * benchmark found the traces, thirty thousand of them in two hundred Runs, in the time it was
 * measuring. With the sizes below the scale is one, as on a desktop with no HiDPI, and text
 * renders through FreeType as ADR-0015 says it does. The frame id stays at minus one: the backend
 * renders nothing, and {@code HeadlessBootTest} holds that.
 */
final class HeadlessGraphics extends MockGraphics {

    @Override
    public int getWidth() {
        return HeadlessBoot.WIDTH;
    }

    @Override
    public int getHeight() {
        return HeadlessBoot.HEIGHT;
    }

    @Override
    public int getBackBufferWidth() {
        return HeadlessBoot.WIDTH;
    }

    @Override
    public int getBackBufferHeight() {
        return HeadlessBoot.HEIGHT;
    }
}
