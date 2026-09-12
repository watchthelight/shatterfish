package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Point;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The one place Shatterfish touches the game's input (AD-14; ADR-0014): it takes an Action and the
 * Observation it was chosen from, refuses anything the screen did not offer, and otherwise makes
 * the call a person's click, key or button makes. It never writes a model field and never
 * reimplements a rule, so every guard the game has still stands between the bot and the world —
 * the guards live in those methods, and this class is never past them.
 *
 * <p>Paths abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as
 * {@code …/}, at the pinned tag. Each kind reaches the game the way the story's spec records:
 *
 * <ul>
 *   <li>every Action that names a cell is {@code GameScene.handleCell(cell)}, because
 *       {@code Hero.handle} is what decides by cell whether a click is a step, an attack, an
 *       interaction, a pick-up, a purchase, a chest, an unlock or a transition
 *       ({@code …/actors/hero/Hero.java:1929-2015}; {@code …/scenes/GameScene.java:1635-1637});</li>
 *   <li>the wait and rest buttons are {@code Hero.rest} and the search button
 *       {@code Hero.search(true)} ({@code …/ui/Toolbar.java:203}, {@code :225}, {@code :313});</li>
 *   <li>a talent is {@code Hero.upgradeTalent} ({@code Hero.java:377}), as the pane's own button
 *       calls it;</li>
 *   <li>an item is {@code Item.execute(hero, action)} ({@code …/items/Item.java:157}), and where
 *       that opens a selector the target answers it in the same wait: a cell through the same
 *       {@code handleCell} a person clicks with, an item through the bag window's own selector
 *       ({@code …/windows/WndBag.java:145}, {@code :288-300});</li>
 *   <li>and a Prompt's button, which has no public method and no key of its own, takes a pointer
 *       event posted where the input system posts one
 *       ({@code SPD-classes/…/input/PointerEvent.java:57-61}, {@code :132}).</li>
 * </ul>
 *
 * <p>The executor runs on the thread that owns the scene, the same one the Observer reads on
 * (ADR-0013), and only at an Input wait, which it asserts before anything else.
 */
public final class ActionExecutor {

    public ActionExecutor() {
    }

    /**
     * Applies {@code action}, chosen from {@code observation}, or says why not. Every refusal but
     * {@link Reason#NO_SELECTOR} is decided before the game is called, so the state is untouched.
     */
    public Outcome execute(Observation observation, Action action) {
        Hero hero = Dungeon.hero;
        Window window = Windows.front();
        if (hero == null || !HeadlessDriver.waitState(hero, window)) {
            return new Outcome.Rejected(action, Reason.NOT_AT_AN_INPUT_WAIT,
                    "the hero is not waiting for input: ready=" + (hero != null && hero.ready)
                            + ", action=" + (hero == null ? "no hero" : String.valueOf(hero.curAction))
                            + ", window=" + (window == null ? "none" : window.getClass().getSimpleName()));
        }
        // The Observation's own set, which the Observer fills and a Brain chooses from; a caller
        // that hands over a bare Observation gets the same function applied to it (story 1.12).
        ActionsSection offered = observation.actions().actions().isEmpty()
                ? ValidActions.of(observation)
                : observation.actions();
        if (!offered.actions().contains(action)) {
            return new Outcome.Rejected(action, Reason.NOT_OFFERED,
                    "the Observation offers " + offered.actions().size() + " Actions and this is not one of them");
        }
        return apply(hero, action);
    }

    private Outcome apply(Hero hero, Action action) {
        if (action instanceof Action.Step step) {
            return click(action, step.cell());
        } else if (action instanceof Action.Attack attack) {
            return click(action, attack.cell());
        } else if (action instanceof Action.Interact interact) {
            return click(action, interact.cell());
        } else if (action instanceof Action.PickUp) {
            return click(action, hero.pos);
        } else if (action instanceof Action.OpenChest chest) {
            return click(action, chest.cell());
        } else if (action instanceof Action.Buy buy) {
            return click(action, buy.cell());
        } else if (action instanceof Action.Unlock unlock) {
            return click(action, unlock.cell());
        } else if (action instanceof Action.Descend || action instanceof Action.Ascend) {
            // A transition is taken by clicking the cell the hero stands on, whichever way it goes:
            // Hero.handle reads the transition there (Hero.java:1999-2006).
            return click(action, hero.pos);
        } else if (action instanceof Action.Rest rest) {
            hero.rest(rest.full());
            return applied(action);
        } else if (action instanceof Action.Wait) {
            // The wait button is the rest button without the flag (Toolbar.java:203).
            hero.rest(false);
            return applied(action);
        } else if (action instanceof Action.Search) {
            hero.search(true);
            return applied(action);
        } else if (action instanceof Action.Talent talent) {
            return upgrade(hero, action, talent.talent());
        } else if (action instanceof Action.UseItem use) {
            return useItem(hero, action, use.item(), use.action(), null, null);
        } else if (action instanceof Action.UseItemAt use) {
            return useItem(hero, action, use.item(), use.action(), use.cell(), null);
        } else if (action instanceof Action.UseItemOn use) {
            return useItem(hero, action, use.item(), use.action(), null, use.target());
        } else if (action instanceof Action.AnswerPrompt answer) {
            return answer(action, answer.option());
        } else if (action instanceof Action.DismissPrompt) {
            Window front = Windows.front();
            if (front == null) {
                return new Outcome.Rejected(action, Reason.NO_SUCH_OPTION, "no window is open to dismiss");
            }
            // What the back key does, and what a tap outside the window does
            // (…/ui/Window.java:223-225).
            front.onBackPressed();
            return applied(action);
        }
        // MoveTo is a human's click on a distant cell, which ActionsSection refuses to carry, and
        // Ability is the armour ability, which story 1.13 leaves to the ability stories.
        return new Outcome.Rejected(action, Reason.UNSUPPORTED, action.kind() + " has no path at this tag");
    }

    /** The click a person makes on a cell, through the game's own selector. */
    private Outcome click(Action action, int cell) {
        GameScene.handleCell(cell);
        return applied(action);
    }

    /**
     * The Action reached the game, and the driver is told so. An Input wait ends when the hero's
     * act announces one, when the window in front changes, or when an Action is handed over
     * (ADR-0015, story 1.5); the third is this class's to say, and saying it here means no caller
     * can forget (see {@link HeadlessDriver#actionHandedOver}).
     */
    private static Outcome applied(Action action) {
        HeadlessDriver.actionHandedOver();
        return new Outcome.Applied(action);
    }

    private Outcome upgrade(Hero hero, Action action, String name) {
        for (Map<Talent, Integer> tier : hero.talents) {
            for (Talent talent : tier.keySet()) {
                // The hero section names a talent by its own title (Observer.hero()), so the match
                // is against the hero's own talents rather than a table of ours.
                if (talent.title().equals(name)) {
                    hero.upgradeTalent(talent);
                    return applied(action);
                }
            }
        }
        return new Outcome.Rejected(action, Reason.NOT_OFFERED, "the hero has no talent called " + name);
    }

    /**
     * An item's own action, and the answer to the selector it opens. The item is resolved by
     * re-walking the belongings in the order the Observation lists them and checking the display
     * name, which is where a Replay's drift is caught rather than acted on (ADR-0014, option 11).
     */
    private Outcome useItem(Hero hero, Action action, ItemRef ref, String what, Integer cell, ItemRef target) {
        Item item = resolve(hero, ref);
        if (item == null) {
            return new Outcome.Rejected(action, Reason.ITEM_MOVED,
                    "the pack holds " + describe(hero, ref.index()) + " where the Observation listed "
                            + ref.name() + " x" + ref.quantity());
        }
        Item other = null;
        if (target != null) {
            other = resolve(hero, target);
            if (other == null) {
                return new Outcome.Rejected(action, Reason.ITEM_MOVED,
                        "the pack holds " + describe(hero, target.index()) + " where the Observation listed "
                                + target.name());
            }
        }
        item.execute(hero, what);
        if (cell != null) {
            // The item opened the game's cell selector; the human's second click answers it, and
            // the same call carries it (GameScene.java:1635-1637).
            GameScene.handleCell(cell);
        } else if (other != null) {
            WndBag bag = bagWindow();
            if (bag == null || bag.getSelector() == null) {
                return new Outcome.Rejected(action, Reason.NO_SELECTOR,
                        what + " opened no bag to answer with " + target.name());
            }
            WndBag.ItemSelector selector = bag.getSelector();
            // The window's own button hides first and then selects (WndBag.java:288-300).
            if (selector.hideAfterSelecting()) {
                bag.hide();
            }
            selector.onSelect(other);
        }
        return applied(action);
    }

    /** The item at {@code ref}'s position, if it is still the item the Observation named there. */
    private static Item resolve(Hero hero, ItemRef ref) {
        List<Item> items = new ArrayList<>();
        for (Item item : hero.belongings) {
            items.add(item);
        }
        if (ref.index() >= items.size()) {
            return null;
        }
        Item item = items.get(ref.index());
        return item.name().equals(ref.name()) ? item : null;
    }

    private static String describe(Hero hero, int index) {
        List<Item> items = new ArrayList<>();
        for (Item item : hero.belongings) {
            items.add(item);
        }
        return index < items.size() ? items.get(index).name() : "nothing at index " + index;
    }

    private static WndBag bagWindow() {
        Scene scene = Game.scene();
        if (scene == null) {
            return null;
        }
        for (Gizmo member : membersOf(scene)) {
            if (member instanceof WndBag bag) {
                return bag;
            }
        }
        return null;
    }

    /**
     * The answer to the window in front: the pointer event a person's tap on its button is. The
     * button has no public method and no key of its own, and the pointer path is public end to end,
     * so this is the human's click and not a way around one.
     */
    private Outcome answer(Action action, int option) {
        Window window = Windows.front();
        if (window == null) {
            return new Outcome.Rejected(action, Reason.NO_SUCH_OPTION, "no window is open");
        }
        List<Component> buttons = new ArrayList<>();
        collectButtons(window, buttons);
        if (option >= buttons.size()) {
            return new Outcome.Rejected(action, Reason.NO_SUCH_OPTION,
                    "the window draws " + buttons.size() + " buttons and the answer names " + option);
        }
        press(buttons.get(option));
        return applied(action);
    }

    private static void press(Component button) {
        Camera camera = button.camera();
        float x = button.left() + button.width() / 2;
        float y = button.top() + button.height() / 2;
        Point screen = camera.cameraToScreen(x, y);
        PointerEvent down = new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.DOWN, PointerEvent.LEFT);
        PointerEvent up = new PointerEvent(screen.x, screen.y, 0, PointerEvent.Type.UP, PointerEvent.LEFT);
        PointerEvent.addPointerEvent(down);
        PointerEvent.addPointerEvent(up);
    }

    private static void collectButtons(Group group, List<Component> buttons) {
        for (Gizmo member : membersOf(group)) {
            if (member instanceof com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton button) {
                buttons.add(button);
            } else if (member instanceof Group inner) {
                collectButtons(inner, buttons);
            }
        }
    }

    private static List<Gizmo> membersOf(Group group) {
        List<Gizmo> members = new ArrayList<>();
        for (Gizmo member : group.shatterfishMembers()) {
            if (member != null && member.exists && member.visible) {
                members.add(member);
            }
        }
        return members;
    }
}
