package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.RatKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.levels.CavesLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.CityLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.TalentsPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBlacksmith;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndChooseSubclass;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndImp;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndQuest;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndSadGhost;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTradeItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndWandmaker;
import org.shatterfish.api.PromptKind;

import java.util.List;

/**
 * Which windows are Prompts, and of which kind: windows the game opens on its own and waits for
 * the player to answer, so that a hero waiting under one is at an Input wait (AD-5) and the
 * answer is an Action (ADR-0014, {@code AnswerPrompt}). Every other window is one the player
 * opened and can close, or one the game shows without asking anything; a hero waiting under one
 * of those is not at an Input wait, and a driver that finds one there fails rather than guesses,
 * as does the Observer (ADR-0006, Prompt).
 *
 * <p>The windows are ADR-0006's kinds as the game opens them at the tag: the quest dialogues of
 * the ghost, the wandmaker, the imp and the blacksmith's first meeting, the shop's trade window,
 * the subclass choice, the ankh's resurrection window, which ADR-0013 makes an answer like any
 * other, and every window of options. An options window is classified by the class that opened
 * it, which is the enclosing class of the anonymous subclass every opener at the tag declares
 * ({@link #optionsKind}); the chasm's jump ({@code …/levels/features/Chasm.java:57-96}), a
 * potion's harmful-drink warning ({@code …/items/potions/Potion.java:238-252}), an item's
 * confirmation or choice, the talents pane's random-talent confirmation
 * ({@code …/ui/TalentsPane.java:189-192}), the quest givers' and the shopkeeper's follow-ups, and
 * {@link PromptKind#OTHER} for the rest, the amulet's ascent, the examine chooser, the warp
 * beacon, whose labels the Observer still carries. Talents are never forced open and alchemy is a
 * scene, so {@link PromptKind#TALENT} is only that confirmation and {@link PromptKind#ALCHEMY} is
 * never produced; the blacksmith's later windows ({@code WndBlacksmith.WndSmith},
 * {@code WndReforge}) are plain windows, protected inside their class, and stay unrecognised
 * until the story that answers them.
 */
public final class Prompts {

    private Prompts() {
    }

    /** Whether {@code window} is a Prompt; false for null. */
    public static boolean isRecognised(Window window) {
        return kind(window) != PromptKind.NONE;
    }

    /**
     * The kind of Prompt {@code window} is, or {@link PromptKind#NONE} for null and for any window
     * the game does not open on its own.
     */
    public static PromptKind kind(Window window) {
        if (window == null) {
            return PromptKind.NONE;
        }
        if (window instanceof WndChooseSubclass) {
            return PromptKind.SUBCLASS;
        }
        if (window instanceof WndResurrect) {
            return PromptKind.RESURRECTION;
        }
        if (window instanceof WndTradeItem) {
            return PromptKind.SHOP;
        }
        if (window instanceof WndQuest || window instanceof WndSadGhost || window instanceof WndWandmaker
                || window instanceof WndImp || window instanceof WndBlacksmith) {
            return PromptKind.QUEST;
        }
        if (window instanceof WndOptions options) {
            return optionsKind(options);
        }
        return PromptKind.NONE;
    }

    /**
     * The kind of an options window by the class that opened it. Every opener at the tag declares
     * an anonymous subclass to receive the answer, so the nearest named class enclosing it names the
     * origin without reading the question; the chasm declares its window inside an anonymous
     * callback ({@code …/levels/features/Chasm.java:59-62}), so the walk passes every anonymous class
     * on the way out. A plain {@code WndOptions} has no enclosing class and is {@link PromptKind#OTHER}. A
     * potion opens two: the harmful-drink warning, whose title is the one the Observer's prompt
     * section carries, and the beneficial-throw confirmation
     * ({@code …/items/potions/Potion.java:264-280}), an item's confirmation like any other.
     */
    static PromptKind optionsKind(WndOptions window) {
        Class<?> origin = window.getClass().getEnclosingClass();
        while (origin != null && origin.isAnonymousClass()) {
            origin = origin.getEnclosingClass();
        }
        if (origin == null) {
            return PromptKind.OTHER;
        }
        if (origin == Chasm.class) {
            return PromptKind.CHASM_JUMP;
        }
        if (Potion.class.isAssignableFrom(origin)) {
            List<String> texts = Windows.texts(window);
            boolean harmful = !texts.isEmpty() && texts.get(0).equals(Messages.get(Potion.class, "harmful"));
            return harmful ? PromptKind.HARMFUL_POTION : PromptKind.ITEM;
        }
        if (Item.class.isAssignableFrom(origin)) {
            return PromptKind.ITEM;
        }
        if (origin == TalentsPane.class) {
            return PromptKind.TALENT;
        }
        if (origin == WndChooseSubclass.class) {
            return PromptKind.SUBCLASS;
        }
        if (origin == WndResurrect.class) {
            return PromptKind.RESURRECTION;
        }
        if (origin == WndTradeItem.class || origin == Shopkeeper.class) {
            return PromptKind.SHOP;
        }
        if (origin == WndBlacksmith.class || origin == WndSadGhost.class || origin == WndWandmaker.class
                || origin == WndImp.class || origin == RatKing.class || origin == CavesLevel.class
                || origin == MiningLevel.class || origin == CityLevel.class) {
            return PromptKind.QUEST;
        }
        return PromptKind.OTHER;
    }

    /** The window's kind for a diagnostic: its class, and whether it is a Prompt. */
    public static String describe(Window window) {
        if (window == null) {
            return "no window";
        }
        String name = window.getClass().getName();
        int dollar = name.indexOf('$');
        String base = dollar > 0 ? name.substring(0, dollar) : name;
        PromptKind kind = kind(window);
        return base.substring(base.lastIndexOf('.') + 1)
                + (dollar > 0 ? " (an anonymous subclass)" : "")
                + (kind != PromptKind.NONE ? ", a Prompt of kind " + kind : ", not a Prompt");
    }
}
