package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.IconButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.RectF;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The options the stone of intuition's guess window draws (story 4.11): the guess button once a
 * guess is chosen, then one option per item type the window shows as an icon, each named.
 *
 * <p>The window draws the unidentified types of the item's category as icons, with no text
 * ({@code core/.../items/stones/StoneOfIntuition.java:191-218}); a tap on one shows the guess button
 * labelled with that type's name ({@code :193-201}), and the guess button guesses
 * ({@code :113-142}). An icon is the type's own picture, the one the catalog and every identified
 * item of that type show a player, so naming it is general game knowledge and not a read of
 * anything hidden: the name given here is the very label the guess button shows after the tap
 * ({@code :198}). The icon is matched by the frame the window drew it with
 * ({@code :203-206}), against the frame of each potion, scroll and ring type the game can generate
 * and each exotic one; the table is built once, from classes, and reads no Run's state.
 *
 * <p>The icons come in the order of {@code Potion.getUnknown()} and its kin, which is a
 * {@code HashSet} of classes ({@code core/.../items/potions/Potion.java:407-415};
 * {@code …/items/scrolls/Scroll.java:269}; {@code …/items/rings/Ring.java:284}), whose order follows
 * identity hash codes and differs from one process to the next. The screen's order would make the
 * same Run's Observation differ between two processes, which non-negotiable 5 forbids, so the icons
 * are listed by name instead; which icon is where on the screen tells a player nothing a name does
 * not. The guess button, when it shows, is listed first.
 */
public final class GuessOptions {

    private GuessOptions() {
    }

    /** One option: the label a Brain sees and the button a tap on it presses. */
    public record Option(String label, Component button) {
    }

    /** Whether {@code window} is the guess window. */
    public static boolean is(Window window) {
        return window instanceof StoneOfIntuition.WndGuess;
    }

    /**
     * The guess window's options: the guess button first when it shows, then the icons by name. A
     * member that does not exist or is not visible is not drawn and is not an option, as
     * {@link Windows} has it.
     */
    public static List<Option> of(Window window) {
        List<Option> styled = new ArrayList<>();
        List<Option> icons = new ArrayList<>();
        walk(window, styled, icons);
        icons.sort(Comparator.comparing(Option::label));
        List<Option> options = new ArrayList<>(styled);
        options.addAll(icons);
        return List.copyOf(options);
    }

    /** The labels of {@link #of}, in the same order. */
    public static List<String> labels(Window window) {
        return of(window).stream().map(Option::label).toList();
    }

    private static void walk(Group group, List<Option> styled, List<Option> icons) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (member == null || !member.exists || !member.visible) {
                continue;
            }
            if (member instanceof StyledButton button) {
                String label = button.text();
                styled.add(new Option(label == null ? "" : label, button));
            } else if (member instanceof IconButton button) {
                icons.add(new Option(name(button.icon()), button));
            } else if (member instanceof Group child) {
                walk(child, styled, icons);
            }
        }
    }

    /** The name of the item type an icon pictures, or empty when it pictures none of them. */
    static String name(Image icon) {
        if (icon == null) {
            return "";
        }
        RectF frame = icon.frame();
        for (Map.Entry<Integer, String> entry : table().entrySet()) {
            RectF known = ItemSpriteSheet.Icons.film.get(entry.getKey());
            if (known != null && same(known, frame)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static boolean same(RectF a, RectF b) {
        return b != null && a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }

    private static Map<Integer, String> table;

    /** Forgets the table, so a test can watch it being built. */
    static synchronized void forget() {
        table = null;
    }

    /** Builds the table now, if it is not built, so a test can watch what building it touches. */
    static void build() {
        table();
    }

    /**
     * Every potion, scroll and ring type with a picture, by its icon, named as the guess button names
     * it ({@code StoneOfIntuition.java:198}). The window's own loop makes an instance of each type to
     * read its icon ({@code :204}), and so does this: an item's icon is set when it is made and never
     * again.
     */
    private static synchronized Map<Integer, String> table() {
        if (table == null) {
            Map<Integer, String> built = new LinkedHashMap<>();
            List<Class<?>> types = new ArrayList<>();
            types.addAll(List.of(Generator.Category.POTION.classes));
            types.addAll(List.of(Generator.Category.SCROLL.classes));
            types.addAll(List.of(Generator.Category.RING.classes));
            types.addAll(ExoticPotion.regToExo.values());
            types.addAll(ExoticScroll.regToExo.values());
            for (Class<?> type : types) {
                Object made = Reflection.newInstance(type);
                if (made instanceof Item item && item.icon >= 0) {
                    built.putIfAbsent(item.icon, Messages.titleCase(Messages.get(type, "name")));
                }
            }
            table = built;
        }
        return table;
    }
}
