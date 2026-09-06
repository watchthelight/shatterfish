package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Signal;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.LogSection;
import org.shatterfish.api.LogTone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The Observer's listener on the game's message signal (ADR-0005, the log; ADR-0006, Log): every
 * message {@code GLog} dispatches ({@code …/utils/GLog.java:32-60}), kept as the signal carries
 * it, with the tone the log pane would give it from its prefix and the prefix removed
 * ({@code …/ui/GameLog.java:72-87}), the new-line marker dropped ({@code :66-69}), and the newest
 * {@link LogSection#MAX_LINES} kept. The pane is a view of the same signal that merges, wraps and
 * trims by screen size on the render thread; this is the source the pane reads.
 *
 * <p>The pane's constructor replaces every listener on the signal with itself
 * ({@code GameLog.java:47}; {@code SPD-classes/…/utils/Signal.java:56-59}), on every scene
 * creation, so this listener is re-added through hook row 3, the scene seam in
 * {@code GameScene.create()}, right after the pane and before the messages the scene emits as it
 * is created. The pane's handler returns false ({@code GameLog.java:149-154}), so a listener
 * after it hears every message; the signal ignores a second add of the same listener
 * ({@code Signal.java:36-44}).
 *
 * <p>Messages arrive on the thread that emits them, the actor thread for nearly all, and the
 * Observer reads on the driver thread at an Input wait, when the actor thread is parked; both go
 * through this object's lock regardless.
 */
public final class GameLogListener implements Signal.Listener<String>, Hooks.LogReplaced {

    /** The one listener: a Run has one log, and the Observer one door. */
    public static final GameLogListener INSTANCE = new GameLogListener();

    private final ArrayDeque<LogLine> lines = new ArrayDeque<>();

    private GameLogListener() {
    }

    /**
     * Arms the seam: the next scene creation, and every one after it, re-adds this listener to
     * the signal. The driver calls it before it creates a Run's first scene.
     */
    public static void install() {
        Hooks.logReplaced = INSTANCE;
    }

    /** Disarms the seam and leaves the signal; the lines stay until {@link #reset()}. */
    public static void uninstall() {
        if (Hooks.logReplaced == INSTANCE) {
            Hooks.logReplaced = null;
        }
        GLog.update.remove(INSTANCE);
    }

    /** Forgets every line: a new Run starts with an empty log, as the pane's wipe gives it. */
    public synchronized void reset() {
        lines.clear();
    }

    @Override
    public void onLogReplaced() {
        GLog.update.add(this);
    }

    @Override
    public boolean onSignal(String text) {
        if (text == null || text.equals(GLog.NEW_LINE)) {
            return false;
        }
        LogTone tone = LogTone.PLAIN;
        String body = text;
        if (text.startsWith(GLog.POSITIVE)) {
            tone = LogTone.POSITIVE;
            body = text.substring(GLog.POSITIVE.length());
        } else if (text.startsWith(GLog.NEGATIVE)) {
            tone = LogTone.NEGATIVE;
            body = text.substring(GLog.NEGATIVE.length());
        } else if (text.startsWith(GLog.WARNING)) {
            tone = LogTone.WARNING;
            body = text.substring(GLog.WARNING.length());
        } else if (text.startsWith(GLog.HIGHLIGHT)) {
            tone = LogTone.HIGHLIGHT;
            body = text.substring(GLog.HIGHLIGHT.length());
        }
        LogLine line = new LogLine(tone, body);
        synchronized (this) {
            lines.addLast(line);
            while (lines.size() > LogSection.MAX_LINES) {
                lines.removeFirst();
            }
        }
        return false;
    }

    /** The lines kept, oldest first. */
    public synchronized List<LogLine> lines() {
        return new ArrayList<>(lines);
    }
}
