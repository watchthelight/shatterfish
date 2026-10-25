package org.shatterfish.overlay;

import com.badlogic.gdx.Input;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No default of the Overlay's shadows a binding the game ships (story 5.11, FR-42, UX-DR12: "banned: any
 * Overlay key that shadows a game binding"). The registration declines a key the game binds already, so a
 * conflict would not shadow anything at run time; it would silently leave an Overlay control without its
 * default. This test is what turns that into a failure at the pinned tag and after any upstream merge that
 * binds F6 to F11.
 */
class KeyBindingConflictTest {

    private static final Set<Integer> F6_TO_F11 = Set.of(Input.Keys.F6, Input.Keys.F7, Input.Keys.F8,
            Input.Keys.F9, Input.Keys.F10, Input.Keys.F11);

    @Test
    @DisplayName("the Overlay's defaults are F6 to F11, one control each")
    void the_defaults_are_f6_to_f11() {
        Set<Integer> keys = new HashSet<>();
        for (Hotkey hotkey : Hotkey.values()) {
            keys.add(hotkey.defaultKey());
        }
        assertEquals(F6_TO_F11, keys);
        assertEquals(6, Hotkey.values().length, "six controls, six distinct keys");
    }

    @Test
    @DisplayName("no Overlay default is a key the game binds: keyboard, controller or hard binding")
    void no_default_shadows_a_game_binding() {
        List<Hotkey> declined = Hotkey.register();
        assertTrue(declined.isEmpty(), "the game binds the default of " + declined + " already");

        Set<GameAction> ours = new HashSet<>();
        for (Hotkey hotkey : Hotkey.values()) {
            ours.add(hotkey.action());
        }
        LinkedHashMap<Integer, GameAction> defaults = SPDAction.getDefaults();
        for (Hotkey hotkey : Hotkey.values()) {
            assertEquals(hotkey.action(), defaults.get(hotkey.defaultKey()),
                    hotkey + " is registered at " + Input.Keys.toString(hotkey.defaultKey()));
        }
        for (var entry : defaults.entrySet()) {
            if (!ours.contains(entry.getValue())) {
                assertFalse(F6_TO_F11.contains(entry.getKey()), "the game binds " + Input.Keys.toString(entry.getKey())
                        + " to " + entry.getValue().name());
            }
        }
        for (var entry : SPDAction.getControllerDefaults().entrySet()) {
            assertFalse(F6_TO_F11.contains(entry.getKey()),
                    "the game binds controller code " + entry.getKey() + " to " + entry.getValue().name());
        }

        // The hard bindings (Android BACK and MENU, Alt+Enter) have no getter; with the configurable bindings
        // emptied, a key is bound only if it is hard-bound.
        LinkedHashMap<Integer, GameAction> keyboard = KeyBindings.getAllBindings();
        LinkedHashMap<Integer, GameAction> controller = KeyBindings.getAllControllerBindings();
        try {
            KeyBindings.setAllBindings(new LinkedHashMap<>());
            KeyBindings.setAllControllerBindings(new LinkedHashMap<>());
            for (int key : F6_TO_F11) {
                assertFalse(KeyBindings.isKeyBound(key), Input.Keys.toString(key) + " is a hard binding");
            }
            assertTrue(KeyBindings.isKeyBound(Input.Keys.ENTER), "the probe sees a hard binding when there is one");
        } finally {
            KeyBindings.setAllBindings(keyboard);
            KeyBindings.setAllControllerBindings(controller);
        }
    }

    @Test
    @DisplayName("a default on a key the game binds is declined, and changes nothing the game binds")
    void the_registration_declines_a_conflict() {
        Hotkey.register();
        GameAction ours = Hotkey.NEXT_STEP.action();
        LinkedHashMap<Integer, GameAction> before = SPDAction.getDefaults();
        for (int taken : new int[] {Input.Keys.W, Input.Keys.F1, Input.Keys.ESCAPE, Input.Keys.ENTER, Input.Keys.ALT_RIGHT}) {
            assertFalse(SPDAction.addDefaultBinding(taken, ours), Input.Keys.toString(taken) + " is the game's");
        }
        assertFalse(SPDAction.addDefaultBinding(Input.Keys.F6, ours), "and a key already registered is not registered twice");
        assertEquals(before, SPDAction.getDefaults());
        assertNotEquals(ours, SPDAction.getDefaults().get(Input.Keys.W));
    }
}
