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

    /**
     * What a HUMAN Run hears from the lock (story 5.9): the raw tap and key the lock passed to the game,
     * before the game has them, so the record can read a tap against a window's buttons; and the one
     * key the Overlay keeps for itself, the notes key.
     */
    interface Human {
        /** A tap released at a screen point, which the lock is passing to the game. */
        void pointerUp(int screenX, int screenY);

        /** A key pressed, which the lock is passing to the game. */
        void keyDown(int keycode);

        /** Any other press the lock is passing to the game. */
        void input();

        /** Whether {@code keycode} is the Overlay's own key, which the game then never sees. */
        boolean overlayKey(int keycode);
    }

    private volatile boolean locked;
    /** Set for a HUMAN Run: the lock then holds only presses, and tells the Run what it passes. */
    private Human human;
    /** Pointers whose press was passed, so their release is passed too and no other release is. */
    private final java.util.Set<Integer> passed = new java.util.HashSet<>();
    /** Keys the Overlay took for itself, whose release is kept from the game too. */
    private final java.util.Set<Integer> taken = new java.util.HashSet<>();

    /**
     * Makes this the lock of a HUMAN Run (story 5.9): while {@link #locked()}, a press is held back and a
     * release still passes, so a key held down across the end of a wait is never left stuck in the
     * game; while open, every press passes and the Run is told of it.
     */
    void human(Human human) {
        this.human = human;
    }

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
        if (human == null) {
            return locked;
        }
        if (human.overlayKey(keycode)) {
            taken.add(keycode);
            return true;
        }
        if (locked) {
            return true;
        }
        human.keyDown(keycode);
        human.input();
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (human == null) {
            return locked;
        }
        // A release always passes, save the Overlay's own key's: a key held across the end of a wait
        // would otherwise stay held in the game and move the hero on its own.
        return taken.remove(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        return locked;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (human == null || locked) {
            return locked;
        }
        passed.add(pointer);
        human.input();
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (human == null) {
            return locked;
        }
        // Passed exactly when its press was: a release on its own would be read by the game as a tap.
        if (!passed.remove(pointer)) {
            return true;
        }
        human.pointerUp(screenX, screenY);
        human.input();
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        if (human == null) {
            return locked;
        }
        return !passed.remove(pointer);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (human == null) {
            return locked;
        }
        return !passed.contains(pointer);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        // A HUMAN Run's scroll zooms the camera, which changes nothing the record holds.
        return human == null && locked;
    }
}
