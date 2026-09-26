package org.shatterfish.overlay;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Overlay counts what the game posts to the render thread and nothing else (story 5.1).
 *
 * <p>The first launch of the Overlay confirmed no wait at all: the desktop backend's controller monitor
 * re-posts itself on every frame, so a count of everything never reached zero. A wait is held back
 * only for what the game itself posts, a window after the hero's act, which is also all the headless
 * backend ever holds.
 */
class RenderQueueTest {

    @Test
    @DisplayName("what the game posts through Game.runOnRenderThread is counted; the backend's own runnables are not")
    void only_the_games_runnables_count() {
        List<Runnable> posted = new ArrayList<>();
        Application before = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[] {Application.class}, (proxy, method, args) -> {
                    if (method.getName().equals("postRunnable")) {
                        posted.add((Runnable) args[0]);
                    }
                    return null;
                });
        try {
            Game.runOnRenderThread(() -> {
            });
        } finally {
            Gdx.app = before;
        }
        assertEquals(1, posted.size());
        assertTrue(OverlayApplication.postedByTheGame(posted.get(0)),
                "the game's own runnable, " + posted.get(0).getClass().getName() + ", holds a wait back");
        Runnable theBackends = () -> {
        };
        assertFalse(OverlayApplication.postedByTheGame(theBackends),
                "a runnable of anyone else's, like the controller monitor's, does not");
    }
}
