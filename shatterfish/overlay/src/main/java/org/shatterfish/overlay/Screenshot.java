package org.shatterfish.overlay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

import java.nio.file.Path;

/**
 * One frame of the game written to a PNG ({@code --screenshot}, story 5.2): the record of a launch
 * nobody watched, read from the game's own framebuffer through libGDX. A window drawn by OpenGL cannot
 * be captured from outside the process on every platform, so the Overlay captures itself.
 */
final class Screenshot {

    /** The frame captured: about ten seconds in at sixty frames a second, once a Run is under way. */
    static final long FRAME = 600;

    private Screenshot() {
    }

    /**
     * Writes the frame just drawn, the right way up, to {@code file}. A screenshot is a convenience: one
     * that cannot be written (a bad path, a full disk) is logged and the Run plays on, since an exception
     * out of a frame would end the game without the Run's end record.
     *
     * @return whether it was written
     */
    static boolean write(Path file) {
        try {
            capture(file);
            return true;
        } catch (RuntimeException | Error failed) {
            if (failed instanceof VirtualMachineError) {
                throw (VirtualMachineError) failed;
            }
            Gdx.app.log("shatterfish", "the screenshot to " + file + " was not written: " + failed);
            return false;
        }
    }

    private static void capture(Path file) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, width, height);
        try {
            // The framebuffer's alpha is whatever blending left there; what the window shows is opaque.
            java.nio.ByteBuffer pixels = frame.getPixels();
            for (int i = 3; i < pixels.limit(); i += 4) {
                pixels.put(i, (byte) 0xFF);
            }
            PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), frame, -1, true);
            Gdx.app.log("shatterfish", "a " + width + "x" + height + " screenshot is at " + file.toAbsolutePath());
        } finally {
            frame.dispose();
        }
    }
}
