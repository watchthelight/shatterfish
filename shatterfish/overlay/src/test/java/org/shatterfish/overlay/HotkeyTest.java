package org.shatterfish.overlay;

import com.badlogic.gdx.Input;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndKeyBindings;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Overlay's controls are the game's own actions (story 5.11, FR-42, UX-DR12): they are in the game's list
 * of actions, which is what its key-binding screen lists, they have the strings that screen shows, a key
 * rebound there answers for its action, and the game's own bindings file keeps the rebinding.
 */
class HotkeyTest {

    private LinkedHashMap<Integer, GameAction> keyboard;
    private LinkedHashMap<Integer, GameAction> controller;

    @BeforeEach
    void register() {
        HeadlessBoot.ensure();
        Hotkey.register();
        keyboard = KeyBindings.getAllBindings();
        controller = KeyBindings.getAllControllerBindings();
        KeyBindings.setAllBindings(SPDAction.getDefaults());
    }

    @AfterEach
    void restore() {
        KeyBindings.setAllBindings(keyboard);
        KeyBindings.setAllControllerBindings(controller);
    }

    @Test
    @DisplayName("F6 to F11 are the controls' keys by default, and any other key is nothing of the Overlay's")
    void the_defaults_answer() {
        assertEquals(Hotkey.NEXT_STEP, Hotkey.of(Input.Keys.F6));
        assertEquals(Hotkey.SPEED, Hotkey.of(Input.Keys.F7));
        assertEquals(Hotkey.PAUSE_RESUME, Hotkey.of(Input.Keys.F8));
        assertEquals(Hotkey.TAKE_OVER, Hotkey.of(Input.Keys.F9));
        assertEquals(Hotkey.EXPLAIN, Hotkey.of(Input.Keys.F10));
        assertEquals(Hotkey.PANEL, Hotkey.of(Input.Keys.F11));
        // The game's own keys, and keys bound to nothing, are not the Overlay's.
        for (int key : new int[] {Input.Keys.W, Input.Keys.SPACE, Input.Keys.F1, Input.Keys.F5, Input.Keys.ESCAPE,
                Input.Keys.N, Input.Keys.F12, Input.Keys.P}) {
            assertNull(Hotkey.of(key), Input.Keys.toString(key));
        }
    }

    @Test
    @DisplayName("every control is one of the game's actions, listed by the key-binding screen, with no key of the game's")
    void the_game_lists_them() {
        List<GameAction> all = GameAction.allActions();
        for (Hotkey hotkey : Hotkey.values()) {
            assertTrue(all.contains(hotkey.action()), hotkey + " is in the game's list of actions");
            // The screen leaves out code 0 (NONE) and, outside controller mode, the mouse clicks only.
            assertTrue(hotkey.action().code() > 0);
            assertNotEquals(GameAction.LEFT_CLICK, hotkey.action());
            assertEquals(List.of(hotkey.defaultKey()), KeyBindings.getKeyboardKeysForAction(hotkey.action()),
                    "bound by default to its one key, as the screen shows it");
        }
        long names = java.util.Arrays.stream(Hotkey.values()).map(Hotkey::actionName).distinct().count();
        assertEquals(Hotkey.values().length, names, "the bindings file keeps an action by its name, so names are distinct");
        for (GameAction game : all) {
            if (java.util.Arrays.stream(Hotkey.values()).noneMatch(h -> h.action() == game)) {
                assertFalse(game.name().startsWith("shatterfish_"), "only the Overlay uses the prefix: " + game.name());
            }
        }
    }

    @Test
    @DisplayName("the screen has a string for each control, in the game's own windows file")
    void the_screen_has_strings() {
        for (Hotkey hotkey : Hotkey.values()) {
            String label = Messages.get(WndKeyBindings.class, hotkey.actionName());
            assertNotEquals(Messages.NO_TEXT_FOUND, label, hotkey.actionName());
            assertTrue(label.startsWith("Shatterfish: "), label);
        }
    }

    @Test
    @DisplayName("a key rebound in the key-binding screen answers for its action, and the old key no longer does")
    void a_rebinding_is_followed() {
        LinkedHashMap<Integer, GameAction> rebound = SPDAction.getDefaults();
        rebound.remove(Input.Keys.F6);
        rebound.put(Input.Keys.P, Hotkey.NEXT_STEP.action());
        KeyBindings.setAllBindings(rebound);
        assertEquals(Hotkey.NEXT_STEP, Hotkey.of(Input.Keys.P));
        assertNull(Hotkey.of(Input.Keys.F6));
        // A key rebound onto a game action is that action's, not the Overlay's.
        rebound = SPDAction.getDefaults();
        rebound.put(Input.Keys.F9, SPDAction.INVENTORY);
        KeyBindings.setAllBindings(rebound);
        assertNull(Hotkey.of(Input.Keys.F9));
    }

    @Test
    @DisplayName("the game's bindings file keeps a rebinding, and a file that knows nothing of the Overlay gives the defaults")
    void the_bindings_file_keeps_it() {
        LinkedHashMap<Integer, GameAction> rebound = SPDAction.getDefaults();
        rebound.remove(Input.Keys.F6);
        rebound.put(Input.Keys.P, Hotkey.NEXT_STEP.action());
        KeyBindings.setAllBindings(rebound);
        SPDAction.saveBindings();

        KeyBindings.setAllBindings(new LinkedHashMap<>());
        SPDAction.loadBindings();
        assertEquals(Hotkey.NEXT_STEP, Hotkey.of(Input.Keys.P), "the rebinding survived a save and a load");
        assertNull(Hotkey.of(Input.Keys.F6));
        assertEquals(Hotkey.PANEL, Hotkey.of(Input.Keys.F11), "and the controls it did not change keep their defaults");
        assertEquals(SPDAction.INVENTORY, KeyBindings.getActionForKey(new com.watabou.input.KeyEvent(Input.Keys.I, true)));
    }
}
