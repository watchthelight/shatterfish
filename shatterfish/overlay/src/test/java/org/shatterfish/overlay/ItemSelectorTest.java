package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.Prompts;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.scene.HeadlessScene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The interface the Overlay plays on keeps the executor's item selectors (story 5.2, ADR-0013): at
 * {@link OverlayGame#INTERFACE_SIZE} an item selector is a {@code WndBag} window on the scene, where the
 * executor finds it to answer a targeted item Action ({@code ActionExecutor.bagWindow}), drawing what a
 * headless Run's (interface size 0) draws; the executor answers it, not the Observer, which is why it is
 * no Prompt. Only the full
 * interface (2) hands the selector to the inventory pane, which no Action names
 * ({@code core/.../scenes/GameScene.java:547-556}, {@code :1668-1684}; {@code docs/ideas.md}, story 5.2).
 *
 * <p>The selector is the one an item scroll opens when read ({@code InventoryScroll} and the scroll of
 * upgrade call {@code GameScene.selectItem}): pick the armour.
 */
class ItemSelectorTest {

    private static final long SEED = 12345;

    private int width;
    private int height;
    private int interfaceSize;

    @AfterEach
    void restore() {
        HeadlessBoot.ensure().game().destroy();
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(interfaceSize);
        }
    }

    private static final class OntoArmour extends WndBag.ItemSelector {
        @Override
        public String textPrompt() {
            return "Select an item";
        }

        @Override
        public boolean itemSelectable(Item item) {
            return item instanceof Armor;
        }

        @Override
        public void onSelect(Item item) {
        }
    }

    /** A play scene at {@code size}, with the armour selector asked for; the window it opened, or null. */
    private WndBag select(int size) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            interfaceSize = SPDSettings.interfaceSize();
        }
        boot.game().destroy();
        HeadlessDriver.newGame(SEED, HeroClass.WARRIOR);
        SPDSettings.interfaceSize(size);
        Game.width = 1600;
        Game.height = 900;
        boot.game().switchTo(new HeadlessScene());
        return GameScene.selectItem(new OntoArmour());
    }

    /** The item selector window on the scene, as the executor finds it to answer (ActionExecutor.bagWindow). */
    private static WndBag bagOnScene() {
        for (Gizmo member : Game.scene().shatterfishMembers()) {
            if (member instanceof WndBag bag) {
                return bag;
            }
        }
        return null;
    }

    @Test
    @DisplayName("the Overlay's interface is not the full one, whose selector no Action can answer")
    void not_the_full_interface() {
        assertNotEquals(2, OverlayGame.INTERFACE_SIZE,
                "interface size 2 hands item selectors to the inventory pane; see docs/ideas.md, story 5.2");
    }

    @Test
    @DisplayName("at the Overlay's interface a selector is a WndBag the executor answers, as at a headless Run's")
    void the_selector_is_a_window() {
        WndBag headless = select(0);
        assertNotNull(headless, "at interface size 0 the selector is a window");
        assertSame(headless, bagOnScene(), "on the scene, where the executor finds it (ActionExecutor.java:286, :355-365)");
        Windows.Read atZero = Windows.read(headless);

        WndBag overlay = select(OverlayGame.INTERFACE_SIZE);
        assertNotNull(overlay, "at the Overlay's interface size the selector is still a window");
        assertSame(overlay, bagOnScene(), "on the scene, where the executor finds it");
        assertInstanceOf(OntoArmour.class, overlay.getSelector(), "carrying the selector the item asked with");
        assertEquals(Prompts.kind(headless), Prompts.kind(overlay), "the same kind of window to the Observer");
        assertEquals(atZero, Windows.read(overlay), "drawing the same title, text and buttons");
    }

    @Test
    @DisplayName("at the full interface the selector goes to the inventory pane, and no window opens")
    void the_full_interface_takes_it() {
        assertNull(select(2), "interface size 2 gives the selector to the inventory pane (GameScene.java:1673-1675)");
        assertNull(bagOnScene(), "and puts no window on the scene for the executor to answer");
    }
}
