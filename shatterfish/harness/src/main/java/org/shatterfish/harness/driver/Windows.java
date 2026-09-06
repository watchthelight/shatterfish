package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.IconTitle;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * What a window draws, read through hook row 4's accessor on {@code Group}
 * ({@code SPD-classes/…/noosa/Group.java}, {@code shatterfishMembers()}): the window in front of
 * the scene, its icon title's label, its other text blocks and its buttons' labels, each in
 * drawing order. The icon title is read by type because a titled message brings its title bar to
 * the front after laying it out ({@code …/windows/WndTitledMessage.java:67}), so the drawing
 * order puts a title last while the screen shows it first. A window is a tree
 * of groups whose member lists are protected; the accessor returns a copy of each, and this
 * class walks it. Nothing here reads a field that is not drawn: a text block's text is what the
 * font renders ({@code …/ui/RenderedTextBlock.java:96}), a styled button's text is its label
 * ({@code …/ui/StyledButton.java:124}), and an icon button or a health bar has no text and is not
 * an option. What the screen does not draw is not read: a member that does not exist or is not
 * visible is skipped, as the group skips it when it draws ({@code Group.java:72-79};
 * {@code …/noosa/Gizmo.java:26-29}), and an item slot's texts, the status, the strength and the
 * level of the item it shows ({@code …/ui/ItemSlot.java:220-300}), are the slot's decorations
 * and not a window's words.
 */
public final class Windows {

    private Windows() {
    }

    /**
     * The window in front of the current scene, or null: the last window among the scene's members,
     * which is the one the scene shows on top ({@code …/scenes/GameScene.java:1352-1373}, {@code :1376-1384}).
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

    /**
     * What a group draws: the label of the first icon title under it, or null when it has none; the
     * text of every other text block, in drawing order; and the label of every styled button, in
     * drawing order.
     */
    public record Read(String iconTitle, List<String> texts, List<String> buttons) {
    }

    /** One read of everything {@code group} draws; see {@link Read}. */
    public static Read read(Group group) {
        List<String> texts = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        String[] iconTitle = new String[1];
        walk(group, iconTitle, texts, buttons);
        return new Read(iconTitle[0], List.copyOf(texts), List.copyOf(buttons));
    }

    private static void walk(Group group, String[] iconTitle, List<String> texts, List<String> buttons) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (member == null || !member.exists || !member.visible || member instanceof ItemSlot) {
                continue;
            }
            if (member instanceof StyledButton button) {
                String label = button.text();
                buttons.add(label == null ? "" : label);
            } else if (member instanceof RenderedTextBlock block) {
                String text = block.text();
                texts.add(text == null ? "" : text);
            } else if (member instanceof IconTitle title && iconTitle[0] == null) {
                List<String> label = new ArrayList<>();
                walk(title, new String[1], label, new ArrayList<>());
                iconTitle[0] = label.isEmpty() ? "" : label.get(0);
            } else if (member instanceof Group child) {
                walk(child, iconTitle, texts, buttons);
            }
        }
    }
}
