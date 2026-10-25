package org.shatterfish.overlay;

import com.badlogic.gdx.Input;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;
import org.shatterfish.overlay.ControlEnablement.Control;

import java.util.Arrays;
import java.util.List;

/**
 * The Overlay's controls as the game's own actions (story 5.11, FR-42, UX-DR12). Each is a
 * {@code GameAction}, so the game's key-binding screen lists it, lets the player rebind it, and saves the
 * binding with the game's own ({@code SPDAction.saveBindings}); the defaults are F6 to F11, which the pinned
 * tag leaves unbound ({@code docs/rules/ui.md}).
 *
 * <p>Registration is {@link #register}: it hands each default to {@code SPDAction.addDefaultBinding} (hook
 * row 10), which declines a key the game binds already. It must run before the game loads its bindings, in
 * {@code OverlayGame.create()} ahead of {@code super.create()}. Without it (the registration removed, or
 * not yet run) the Overlay is unchanged: every control is also a button ({@link ControlsRow}, and the Mode
 * strip for the Panel toggle), and {@link #of} answers null for every key.
 */
enum Hotkey {

    /** One Input wait. */
    NEXT_STEP("shatterfish_next_step", Input.Keys.F6, Control.NEXT_STEP),
    /** Cycles Next Step, Human, Fast. */
    SPEED("shatterfish_speed", Input.Keys.F7, Control.SPEED),
    PAUSE_RESUME("shatterfish_pause_resume", Input.Keys.F8, Control.PAUSE_RESUME),
    /** Take over while the bot plays, Hand back while the person does. */
    TAKE_OVER("shatterfish_take_over", Input.Keys.F9, Control.TAKE_OVER),
    EXPLAIN("shatterfish_explain", Input.Keys.F10, Control.EXPLAIN),
    /** Collapses the Panel to the Mode strip, or restores it. */
    PANEL("shatterfish_panel", Input.Keys.F11, null);

    /** The game's action, created with the enum so the game's list of actions holds it as soon as this loads. */
    private final GameAction action;
    private final int defaultKey;
    private final Control control;

    Hotkey(String name, int defaultKey, Control control) {
        this.action = new Action(name);
        this.defaultKey = defaultKey;
        this.control = control;
    }

    /** The Controls row's control this key presses, or null for the Panel toggle, which is not in that row. */
    Control control() {
        return control;
    }

    /** The action, which is what the key-binding screen lists and the bindings file saves by name. */
    GameAction action() {
        return action;
    }

    /** The key the action is bound to until the player rebinds it. */
    int defaultKey() {
        return defaultKey;
    }

    /** The action's name, as the bindings file and the settings screen's strings know it. */
    String actionName() {
        return action.name();
    }

    /** The game's {@code GameAction}, whose constructor is protected so that the game's own subclasses can add actions. */
    private static final class Action extends GameAction {
        Action(String name) {
            super(name);
        }
    }

    /**
     * Registers the actions' default keys with the game, once, before it loads its bindings. A key the game
     * binds already is declined ({@code SPDAction.addDefaultBinding}): that action starts unbound and the
     * player binds it in the settings screen, and {@code KeyBindingConflictTest} fails the build so the
     * default is changed.
     *
     * @return the hotkeys whose default key was declined
     */
    static synchronized List<Hotkey> register() {
        if (declined == null) {
            declined = Arrays.stream(values()).filter(h -> !SPDAction.addDefaultBinding(h.defaultKey, h.action)).toList();
        }
        return declined;
    }

    /** Set by the first {@link #register}; the game's table of defaults is global, so a second Run in the process adds nothing. */
    private static List<Hotkey> declined;

    /**
     * The hotkey {@code keycode} is bound to now, in the game's own bindings (so a key rebound in the
     * settings screen answers for its new action), or null when it is bound to anything else.
     */
    static Hotkey of(int keycode) {
        GameAction bound = KeyBindings.getActionForKey(new KeyEvent(keycode, true));
        for (Hotkey hotkey : values()) {
            if (hotkey.action == bound) {
                return hotkey;
            }
        }
        return null;
    }
}
