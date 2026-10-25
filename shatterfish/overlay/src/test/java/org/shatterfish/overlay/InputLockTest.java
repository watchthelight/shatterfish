package org.shatterfish.overlay;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.watabou.input.InputHandler;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The player's input does not reach the game while a Run plays it (story 5.1's fairness review): the
 * lock sits first in a multiplexer, as it does in the game's, and takes every key and touch while
 * locked, and none once the Run is over.
 */
class InputLockTest {

    @Test
    @DisplayName("locked, nothing reaches the game; unlocked, everything does; the cursor always moves")
    void locked_and_unlocked() {
        AtomicInteger reached = new AtomicInteger();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                reached.incrementAndGet();
                return true;
            }
        };
        InputLock lock = new InputLock();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);

        lock.lock();
        multiplexer.keyDown(66);
        multiplexer.touchDown(10, 10, 0, 0);
        multiplexer.touchUp(10, 10, 0, 0);
        multiplexer.scrolled(0, 1);
        assertEquals(0, reached.get(), "a locked game hears no key, click or scroll");
        multiplexer.mouseMoved(20, 20);
        assertEquals(1, reached.get(), "the cursor still hovers");

        lock.unlock();
        assertFalse(lock.locked());
        multiplexer.keyDown(66);
        multiplexer.touchDown(10, 10, 0, 0);
        assertEquals(3, reached.get(), "an unlocked game hears everything");
    }

    @Test
    @DisplayName("locked, a press on the Panel passes with its drag and release and reaches no key; a press elsewhere is held (story 5.6)")
    void the_panel_is_the_one_place_a_press_passes() {
        AtomicInteger reached = new AtomicInteger();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }
        };
        InputLock lock = new InputLock();
        boolean[] windowInFront = {false};
        // The Panel is the right-hand 100 pixels; a window in front takes it away, as the Overlay's hit test does.
        lock.panel((x, y) -> !windowInFront[0] && x >= 900);
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);
        lock.lock();

        multiplexer.touchDown(100, 100, 0, 0);
        multiplexer.touchUp(100, 100, 0, 0);
        assertEquals(0, reached.get(), "a press off the Panel is held back, as before");

        multiplexer.touchDown(950, 100, 0, 0);
        multiplexer.touchDragged(950, 120, 0);
        multiplexer.touchUp(950, 120, 0, 0);
        assertEquals(3, reached.get(), "a press on the Panel reaches the Panel's controls, with its drag and release");

        multiplexer.touchDown(950, 100, 0, 0);
        multiplexer.touchDragged(100, 100, 0);
        multiplexer.touchUp(100, 100, 0, 0);
        assertEquals(6, reached.get(), "and the same pointer's moves and release pass wherever it goes, so no press is left half-made");

        multiplexer.keyDown(66);
        assertEquals(6, reached.get(), "a key still reaches nothing: the Panel's hotkeys are story 5.11's");

        windowInFront[0] = true;
        multiplexer.touchDown(950, 100, 0, 0);
        multiplexer.touchUp(950, 100, 0, 0);
        assertEquals(6, reached.get(), "with a window of the game's in front the Panel is not a place a press passes");
    }

    @Test
    @DisplayName("a locked game with no Panel set holds back every press, as before story 5.6")
    void no_panel_no_exemption() {
        AtomicInteger reached = new AtomicInteger();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }
        };
        InputLock lock = new InputLock();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);
        lock.lock();
        multiplexer.touchDown(950, 100, 0, 0);
        assertEquals(0, reached.get());
    }

    @Test
    @DisplayName("a key or press the person made while they played is released into the game after a hand back, so none is left held (story 5.8)")
    void a_hand_back_leaves_no_key_held() {
        java.util.List<String> reached = new java.util.ArrayList<>();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.add("down " + keycode);
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                reached.add("up " + keycode);
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.add("touch down " + pointer);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                reached.add("touch up " + pointer);
                return true;
            }
        };
        InputLock lock = new InputLock();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);
        lock.human(new InputLock.Human() {
            @Override
            public void pointerUp(int screenX, int screenY) {
            }

            @Override
            public void keyDown(int keycode) {
            }

            @Override
            public void input() {
            }

            @Override
            public boolean overlayKey(int keycode) {
                return false;
            }
        });
        multiplexer.keyDown(21);
        multiplexer.touchDown(10, 10, 1, 0);
        // The Run is handed back: the lock closes and loses its Human.
        lock.human(null);
        lock.lock();
        multiplexer.keyUp(21);
        multiplexer.touchUp(10, 10, 1, 0);
        assertEquals(java.util.List.of("down 21", "touch down 1", "up 21", "touch up 1"), reached,
                "the release of what the person pressed passes");
        multiplexer.keyDown(21);
        multiplexer.keyUp(21);
        multiplexer.touchDown(10, 10, 1, 0);
        multiplexer.touchUp(10, 10, 1, 0);
        assertEquals(4, reached.size(), "and a press made now, or its release, does not");
    }

    @Test
    @DisplayName("a processor the game puts in front later (a text input's stage) is put behind the lock again")
    void kept_first() {
        // The game's own input handler over a stand-in for libGDX's Input, which hands the handler's
        // multiplexer to whoever sets it: the one place key events enter.
        InputProcessor[] entry = new InputProcessor[1];
        Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setInputProcessor")) {
                        entry[0] = (InputProcessor) args[0];
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
        InputHandler handler = new InputHandler(input);
        InputLock lock = new InputLock();
        lock.lock();
        handler.addInputProcessor(lock);

        AtomicInteger typed = new AtomicInteger();
        InputAdapter stage = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                typed.incrementAndGet();
                return true;
            }
        };
        // What TextInput does when a window with a text field opens (SPD-classes/…/noosa/TextInput.java:73).
        handler.addInputProcessor(stage);
        entry[0].keyDown(66);
        assertEquals(1, typed.get(), "added in front of the lock, the stage takes keys");

        lock.keepFirst(handler);
        entry[0].keyDown(66);
        assertEquals(1, typed.get(), "the lock is first again, and takes them");
        lock.keepFirst(handler);
        entry[0].keyDown(66);
        assertEquals(1, typed.get(), "keeping it first twice leaves one lock in front");
    }

    @Test
    @DisplayName("story 5.5: a quickslot key, a toolbar tap and an inventory tap never reach the game while a Run plays it or is paused")
    void quickslot_toolbar_and_inventory_inputs_stop_here() {
        // The quickslots, the toolbar and the inventory pane call Hero and Item directly and never reach the
        // cell selector's input gate, so this lock is what keeps a mouse or a key from them (PausedInputTest
        // holds the gate's half). The lock is the same whether the Run is RUNNING or PAUSED.
        AtomicInteger reached = new AtomicInteger();
        InputAdapter game = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                reached.incrementAndGet();
                return true;
            }
        };
        InputLock lock = new InputLock();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(game);
        multiplexer.addProcessor(0, lock);
        lock.lock();

        // The default quickslot keys, 1 to 6 and Q, W and E among them, and the bottom-left and bottom-right
        // of a 1280 by 720 window where the toolbar's slots and the inventory button sit.
        int[] keys = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3, Input.Keys.Q, Input.Keys.W,
                Input.Keys.E, Input.Keys.I};
        for (int key : keys) {
            multiplexer.keyDown(key);
            multiplexer.keyUp(key);
        }
        int[][] taps = {{640, 700}, {700, 700}, {1200, 700}, {80, 700}};
        for (int[] tap : taps) {
            multiplexer.touchDown(tap[0], tap[1], 0, 0);
            multiplexer.touchUp(tap[0], tap[1], 0, 0);
        }
        assertEquals(0, reached.get(), "nothing a person presses reaches the widgets");
    }

    /** A game that records the keys it is given. */
    private static InputAdapter recording(java.util.List<String> reached) {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                reached.add("down " + keycode);
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                reached.add("up " + keycode);
                return true;
            }
        };
    }

    @Test
    @DisplayName("a key bound to an Overlay action is the Overlay's alone, locked or open, and its release is kept from the game too (story 5.11)")
    void a_hotkey_never_reaches_the_game() {
        java.util.List<String> reached = new java.util.ArrayList<>();
        java.util.List<Integer> pressed = new java.util.ArrayList<>();
        InputLock lock = new InputLock();
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(recording(reached));
        multiplexer.addProcessor(0, lock);
        lock.hotkeys(keycode -> {
            if (keycode == Input.Keys.F6) {
                pressed.add(keycode);
                return true;
            }
            return false;
        });

        lock.lock();
        multiplexer.keyDown(Input.Keys.F6);
        multiplexer.keyUp(Input.Keys.F6);
        lock.unlock();
        multiplexer.keyDown(Input.Keys.F6);
        multiplexer.keyUp(Input.Keys.F6);
        assertEquals(java.util.List.of(Input.Keys.F6, Input.Keys.F6), pressed, "the Overlay heard both presses");
        assertEquals(java.util.List.of(), reached, "and the game neither the press nor the release, whatever the lock was doing");

        multiplexer.keyDown(Input.Keys.W);
        multiplexer.keyUp(Input.Keys.W);
        assertEquals(java.util.List.of("down " + Input.Keys.W, "up " + Input.Keys.W), reached,
                "a key that is not the Overlay's still reaches an open game");
        lock.lock();
        multiplexer.keyDown(Input.Keys.W);
        assertEquals(2, reached.size(), "and none reaches a closed one");
    }

    @Test
    @DisplayName("a person's turn is not made by a hotkey: with a HUMAN Run the Run is told of the game's keys and never of the Overlay's (story 5.11)")
    void a_hotkey_is_no_human_input() {
        java.util.List<String> told = new java.util.ArrayList<>();
        InputLock lock = new InputLock();
        lock.human(new InputLock.Human() {
            @Override
            public void pointerUp(int screenX, int screenY) {
            }

            @Override
            public void keyDown(int keycode) {
                told.add("key " + keycode);
            }

            @Override
            public void input() {
                told.add("input");
            }

            @Override
            public boolean overlayKey(int keycode) {
                return false;
            }
        });
        lock.hotkeys(keycode -> keycode == Input.Keys.F9);
        lock.unlock();
        assertEquals(true, lock.keyDown(Input.Keys.F9), "kept from the game");
        assertEquals(java.util.List.of(), told, "and not the Run's record of what the person did");
        assertEquals(true, lock.keyUp(Input.Keys.F9));
        assertFalse(lock.keyDown(Input.Keys.W), "a game key passes an open lock");
        assertEquals(java.util.List.of("key " + Input.Keys.W, "input"), told);
    }
}
