package org.shatterfish.overlay;

import com.badlogic.gdx.InputAdapter;
import com.watabou.input.InputHandler;

/**
 * Keeps the player's hands off the game while a Run plays it (story 5.1's fairness review).
 *
 * <p>The Brain plays through the executor; a click or a key of the player's, taken by the game while
 * the Brain thinks, would change the game outside the Run's log, and nothing would say so. Until
 * take-over (story 5.8) makes a human turn a thing the log records, the game's own input is closed
 * while a Run is attached: this processor sits first in the game's input multiplexer
 * ({@code SPD-classes/…/input/InputHandler.java:35-44}) and takes every key, touch, drag and scroll,
 * so nothing reaches the game's pointer and key queues. Mouse movement passes, so the cursor still
 * hovers. The window's own close button is the operating system's and still works.
 *
 * <p>What it does not cover: a gamepad. The controller handler writes the game's key queue directly
 * ({@code SPD-classes/…/input/ControllerHandler.java:122-134}), past the multiplexer. Story 5.5's
 * input-gate hook (ADR-0013 option 9: {@code CellSelector.select} and {@code processKeyHold} ask
 * {@code Hooks.inputGate}) is where every path is closed; this is the cheap half until then, and the
 * gap is written down in docs/ideas.md.
 */
final class InputLock extends InputAdapter {

    private volatile boolean locked;

    void lock() {
        locked = true;
    }

    void unlock() {
        locked = false;
    }

    boolean locked() {
        return locked;
    }

    /**
     * Puts this lock back in front of the game's input processors. Anything the game adds later goes
     * in front of it: the text-input window's stage is inserted at the head of the same multiplexer
     * ({@code SPD-classes/…/noosa/TextInput.java:73}, through {@code InputHandler.addInputProcessor},
     * {@code …/input/InputHandler.java:42-44}), and would take keys while a Run plays. The Overlay calls
     * this at the end of every frame it is locked, which is before the next frame's input is polled.
     */
    void keepFirst(InputHandler handler) {
        handler.removeInputProcessor(this);
        handler.addInputProcessor(this);
    }

    @Override
    public boolean keyDown(int keycode) {
        return locked;
    }

    @Override
    public boolean keyUp(int keycode) {
        return locked;
    }

    @Override
    public boolean keyTyped(char character) {
        return locked;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return locked;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return locked;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return locked;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return locked;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return locked;
    }
}
