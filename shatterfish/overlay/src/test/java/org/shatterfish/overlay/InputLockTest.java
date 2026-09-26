package org.shatterfish.overlay;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputAdapter;
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
}
