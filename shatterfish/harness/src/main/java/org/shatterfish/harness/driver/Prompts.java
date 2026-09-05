package org.shatterfish.harness.driver;

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

import java.util.List;

/**
 * Which windows are Prompts: windows the game opens on its own and waits for the player to answer,
 * so that a hero waiting under one is at an Input wait (AD-5) and the answer is an Action
 * (ADR-0014, {@code AnswerPrompt}). Every other window is one the player opened and can close, or
 * one the game shows without asking anything; a hero waiting under one of those is not at an
 * Input wait, and a driver that finds one there fails rather than guesses.
 *
 * <p>The list is the windows among ADR-0006's kinds that the game opens on its own: the quest
 * dialogues of the ghost, the wandmaker, the imp and the blacksmith's first meeting, the shop's
 * trade window, the subclass choice, the chasm jump and the harmful-potion confirmation (both
 * {@code WndOptions}), and the ankh's resurrection window, which ADR-0013 makes an answer like
 * any other. Talents are never forced open and alchemy is a scene, so neither is here; the
 * blacksmith's later windows ({@code WndBlacksmith.WndSmith}, {@code WndReforge}) are plain
 * {@code Window}s met in the caves and are story 1.10's, as are the kinds and their options for
 * the Observer. This is the list story 1.5 needs to confirm a wait in the sewers.
 */
public final class Prompts {

    private static final List<Class<? extends Window>> RECOGNISED = List.of(
            WndOptions.class, WndResurrect.class, WndQuest.class, WndSadGhost.class, WndWandmaker.class,
            WndImp.class, WndBlacksmith.class, WndTradeItem.class, WndChooseSubclass.class);

    private Prompts() {
    }

    /** Whether {@code window} is a Prompt; false for null. */
    public static boolean isRecognised(Window window) {
        if (window == null) {
            return false;
        }
        for (Class<? extends Window> kind : RECOGNISED) {
            if (kind.isInstance(window)) {
                return true;
            }
        }
        return false;
    }

    /** The window's kind for a diagnostic: its class, and whether it is a Prompt. */
    public static String describe(Window window) {
        if (window == null) {
            return "no window";
        }
        String name = window.getClass().getName();
        int dollar = name.indexOf('$');
        String base = dollar > 0 ? name.substring(0, dollar) : name;
        return base.substring(base.lastIndexOf('.') + 1)
                + (dollar > 0 ? " (an anonymous subclass)" : "")
                + (isRecognised(window) ? ", a Prompt" : ", not a Prompt");
    }
}
