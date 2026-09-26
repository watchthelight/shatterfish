package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ValidActions;

import java.util.List;

/**
 * A person at the window, played by a test (story 5.9): each method makes the call the game's own
 * input makes once its input handler has turned a tap or a key into a target, so everything after it
 * is the game's and the hooks hear what a real input would make them hear. Paths abbreviate
 * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}.
 */
final class ScriptedHuman {

    private ScriptedHuman() {
    }

    /** A tap on a cell of the map: {@code CellSelector.onClick} resolves the cell and calls {@code select} ({@code …/scenes/CellSelector.java:86-125}). */
    static void tapCell(int cell) {
        GameScene.handleCell(cell);
    }

    /** The wait button ({@code …/ui/Toolbar.java:198-204}). */
    static void pressWait() {
        if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
            Dungeon.hero.rest(false);
        }
    }

    /** The rest key ({@code …/ui/Toolbar.java:223-226}). */
    static void pressRest() {
        if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
            Dungeon.hero.rest(true);
        }
    }

    /** A long press on the examine button, which searches ({@code …/ui/Toolbar.java:310-314}). */
    static void pressSearch() {
        Dungeon.hero.search(true);
    }

    /** An item window's button ({@code …/windows/WndUseItem.java}): the item's action. */
    static void useItem(Item item, String action) {
        item.execute(Dungeon.hero, action);
    }

    /** The talent button's upgrade ({@code …/ui/TalentButton.java:251}). */
    static void upgrade(Talent talent) {
        Dungeon.hero.upgradeTalent(talent);
    }

    /** The first offered Action of {@code kind} at the wait {@code observation} was made at, or null. */
    static <A extends Action> A offered(Observation observation, Class<A> kind) {
        ActionsSection offered = observation.actions().actions().isEmpty()
                ? ValidActions.of(observation) : observation.actions();
        List<Action> actions = offered.actions();
        for (Action action : actions) {
            if (kind.isInstance(action)) {
                return kind.cast(action);
            }
        }
        return null;
    }

    /**
     * What a person following the Brain's shadow does with their own hands: the input that makes the
     * shadow's Action, through the calls above; a Decision with nothing to follow is the wait button.
     */
    static void follow(EmbeddedRun run, org.shatterfish.api.RunLog.Decision decision, Observation observation) {
        Action action = decision == null ? new Action.Wait() : decision.chosen().action();
        int hero = observation.hero().cell();
        switch (action) {
            case Action.Step a -> tapCell(a.cell());
            case Action.Attack a -> tapCell(a.cell());
            case Action.Interact a -> tapCell(a.cell());
            case Action.OpenChest a -> tapCell(a.cell());
            case Action.Buy a -> tapCell(a.cell());
            case Action.Unlock a -> tapCell(a.cell());
            case Action.PickUp a -> tapCell(hero);
            case Action.Descend a -> tapCell(hero);
            case Action.Ascend a -> tapCell(hero);
            case Action.Search a -> pressSearch();
            case Action.Rest a -> {
                if (a.full()) {
                    pressRest();
                } else {
                    pressWait();
                }
            }
            case Action.UseItem a -> useItem(held(a.item().index()), a.action());
            case Action.UseItemAt a -> {
                useItem(held(a.item().index()), a.action());
                tapCell(a.cell());
            }
            case Action.Talent a -> {
                for (java.util.LinkedHashMap<Talent, Integer> tier : Dungeon.hero.talents) {
                    for (Talent talent : tier.keySet()) {
                        if (talent.title().equals(a.talent())) {
                            upgrade(talent);
                            return;
                        }
                    }
                }
            }
            case Action.AnswerPrompt a -> {
                com.watabou.noosa.ui.Component button = org.shatterfish.harness.executor.ActionExecutor
                        .optionButtons(org.shatterfish.harness.driver.Windows.front()).get(a.option());
                com.watabou.utils.Point screen = button.camera().cameraToScreen(button.left() + button.width() / 2,
                        button.top() + button.height() / 2);
                run.pointerUp(screen.x, screen.y);
                com.watabou.input.PointerEvent.addPointerEvent(new com.watabou.input.PointerEvent(screen.x, screen.y, 0,
                        com.watabou.input.PointerEvent.Type.DOWN, com.watabou.input.PointerEvent.LEFT));
                com.watabou.input.PointerEvent.addPointerEvent(new com.watabou.input.PointerEvent(screen.x, screen.y, 0,
                        com.watabou.input.PointerEvent.Type.UP, com.watabou.input.PointerEvent.LEFT));
            }
            default -> pressWait();
        }
    }

    private static Item held(int index) {
        int i = 0;
        for (Item item : Dungeon.hero.belongings) {
            if (i++ == index) {
                return item;
            }
        }
        throw new IllegalStateException("nothing held at " + index);
    }
}
