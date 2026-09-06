package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * What a window draws, read through hook row 4's accessor on {@code Group}
 * ({@code SPD-classes/…/noosa/Group.java}, {@code shatterfishMembers()}): the window in front of
 * the scene, its text blocks and its buttons' labels, each in drawing order. A window is a tree
 * of groups whose member lists are protected; the accessor returns a copy of each, and this
 * class walks it. Nothing here reads a field that is not drawn: a text block's text is what the
 * font renders ({@code …/ui/RenderedTextBlock.java:96}), a styled button's text is its label
 * ({@code …/ui/StyledButton.java:124}), and an icon button, an item slot or a health bar has no
 * text and is not an option.
 */
public final class Windows {

    private Windows() {
    }

    /**
     * The window in front of the current scene, or null: the last window among the scene's members,
     * which is the one the scene shows on top ({@code …/scenes/GameScene.java:1352-1373}, {@code :1382-1390}).
     */
    public static Window front() {
        Scene scene = Game.scene();
        if (scene == null) {
            return null;
        }
        Window front = null;
        for (Gizmo member : scene.shatterfishMembers()) {
            if (member instanceof Window window) {
                front = window;
            }
        }
        return front;
    }

    /** The text of every text block under {@code group}, in drawing order; a button's label is not among them. */
    public static List<String> texts(Group group) {
        List<String> texts = new ArrayList<>();
        walk(group, texts, new ArrayList<>());
        return texts;
    }

    /** The label of every styled button under {@code group}, in drawing order. */
    public static List<String> buttons(Group group) {
        List<String> buttons = new ArrayList<>();
        walk(group, new ArrayList<>(), buttons);
        return buttons;
    }

    private static void walk(Group group, List<String> texts, List<String> buttons) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (member instanceof StyledButton button) {
                String label = button.text();
                buttons.add(label == null ? "" : label);
            } else if (member instanceof RenderedTextBlock block) {
                String text = block.text();
                texts.add(text == null ? "" : text);
            } else if (member instanceof Group child) {
                walk(child, texts, buttons);
            }
        }
    }
}
