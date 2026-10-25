package org.shatterfish.overlay;

import com.badlogic.gdx.InputAdapter;
import com.watabou.input.InputHandler;

/**
 * Keeps the player's hands off the game while a Run plays it (story 5.1's fairness review).
 *
 * <p>The Brain plays through the executor; a click or a key of the player's, taken by the game while
 * the Brain thinks, would change the game outside the Run's log, and nothing would say so. The game's
 * own input is closed while the Brain plays (and, until a person takes over in story 5.8, always; when
 * they do, {@link #human} opens it and the log records their turns): this processor sits first in the game's input multiplexer
 * ({@code SPD-classes/…/input/InputHandler.java:35-44}) and takes every key, touch, drag and scroll,
 * so nothing reaches the game's pointer and key queues. Mouse movement passes, so the cursor still
 * hovers. The window's own close button is the operating system's and still works.
 *
 * <p>What it does not cover: a gamepad. The controller handler writes the game's key queue directly
 * ({@code SPD-classes/…/input/ControllerHandler.java:122-134}), past the multiplexer. The
 * input-gate hook (ADR-0013 option 9: {@code CellSelector.select} and {@code processKeyHold} ask
 * {@code Hooks.inputGate}), story 5.5, shuts out every click and every key or stick move; the toolbar,
 * quickslots and inventory pane are still reached only through this lock, so the gamepad gap stays
 * (docs/ideas/2026-10-25-gate-hero-and-item-entry-points.md).
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

    /** Where the Panel is, in the screen pixels input arrives in (story 5.6). */
    interface PanelHit {
        /** Whether a press at this point lands on the Panel's controls; false while a window of the game's is in front. */
        boolean at(int screenX, int screenY);
    }

    private volatile boolean locked;
    /** Set once the Panel has controls: a press on it passes while the lock holds, and reaches nothing under it. */
    private PanelHit panel = (x, y) -> false;
    /** Pointers whose press landed on the Panel and was passed: their moves and release pass, and are the Panel's alone. */
    private final java.util.Set<Integer> onPanel = new java.util.HashSet<>();
    /** Set for a HUMAN Run: the lock then holds only presses, and tells the Run what it passes. */
    private Human human;
    /** Pointers whose press was passed, so their release is passed too and no other release is. */
    private final java.util.Set<Integer> passed = new java.util.HashSet<>();
    /** Keys passed to the game and not yet released, so a hand back leaves none stuck down (story 5.8). */
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();
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

    /**
     * Lets a person's press on the Panel through while the lock holds (story 5.6, ADR-0013's amendment):
     * the Panel's controls are the one thing a person may touch while a Run plays, and a press on them
     * reaches the Panel's own hot areas, newest first, and nothing under the Panel (its blocker takes what
     * the controls do not). Keys and every press elsewhere stay held back. The executor's synthetic taps do
     * not come through here; they are kept from the controls by the controls' own gate
     * ({@code ControlsRow}: off while a window is in front).
     */
    void panel(PanelHit panel) {
        this.panel = panel;
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
        heldKeys.add(keycode);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (human == null) {
            // A key the person pressed while they played and still holds when the Run is handed back (story
            // 5.8) is released into the game all the same, or it would stay held and move the hero by itself.
            return locked && !heldKeys.remove(keycode);
        }
        heldKeys.remove(keycode);
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
        if (locked && panel.at(screenX, screenY)) {
            onPanel.add(pointer);
            return false;
        }
        if (human == null || locked) {
            return locked;
        }
        passed.add(pointer);
        human.input();
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (onPanel.remove(pointer)) {
            return false;
        }
        if (human == null) {
            // A press passed while the person played is released into the game, and the input gate (story 5.5)
            // keeps the tap from the hero (story 5.8).
            return locked && !passed.remove(pointer);
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
        if (onPanel.remove(pointer)) {
            return false;
        }
        if (human == null) {
            return locked;
        }
        return !passed.remove(pointer);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (onPanel.contains(pointer)) {
            return false;
        }
        if (human == null) {
            return locked;
        }
        return !passed.contains(pointer);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (locked && com.badlogic.gdx.Gdx.input != null
                && panel.at(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY())) {
            // The Decision log's scroll under the pointer (story 5.6: the Panel's own hot areas work while a Run plays).
            return false;
        }
        // A HUMAN Run's scroll zooms the camera, which changes nothing the record holds.
        return human == null && locked;
    }
}
