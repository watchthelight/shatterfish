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
}
