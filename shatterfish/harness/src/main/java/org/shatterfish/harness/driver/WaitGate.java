package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Game;

/**
 * Where an Input wait is confirmed and numbered, for both drivers (ADR-0013, ADR-0015).
 *
 * <p>The headless driver owns its loop and steps its own frames; the Overlay's embedded Run is given
 * frames by the render thread, one at a time, and cannot loop. What the two share is the question
 * asked between two frames, "is this a new Input wait?", and the answer has to be the same code in
 * both or a Replay of an Overlay Run is not the code a Rig Run ran (ADR-0013's third decision
 * driver). So the state that answers it lives here, and each driver hands it one frame's facts:
 * the hero, the window in front, and whether the render thread's queue holds anything.
 *
 * <p>A wait is new when the game announced one (hook row 5's notification from {@code Hero.act()},
 * {@link #noticed()}), when an Action was handed to the game ({@link #handedOver()}), or when the
 * window in front changed; and it is a wait when AD-5's condition holds, a window counting from its
 * second frame in front, with nothing queued for the render thread. The reasons for each part are
 * in {@link HeadlessDriver}'s class comment, which is where they were found.
 *
 * <p>Threads: {@link #noticed()} runs on the game's actor thread and is one volatile write;
 * {@link #handedOver()} runs on the UI-role thread, from the executor; everything else runs on the
 * UI-role thread between frames. Nothing here takes a lock.
 */
public final class WaitGate {

    /**
     * The gate of the Run being driven in this process, which the executor's announcement reaches
     * without being handed one. One process hosts one Run (ADR-0007); a driver installs its gate
     * when it starts and removes it, if it is still its own, when it ends.
     */
    private static volatile WaitGate live;

    /** Written by the actor thread only, inside {@code Hero.act()}; read between frames. */
    private volatile long notifications;
    private long seenNotifications;
    private long dropped;
    private volatile boolean acted;
    private volatile long waitIndex;
    private Window lastConfirmedWindow;
    private Window lastSeenWindow;
    private int windowFramesShown;
    /** The hero was ready for a wait but his sprite was still playing an animation (story 5.13). */
    private boolean carried;
    /** Game seconds the hero has been ready with his sprite still playing, since the last wait. */
    private float unsettled;

    /**
     * The longest, in game seconds, a wait is held for the hero's sprite to finish the animation it is
     * playing. No animation the hero plays lasts a tenth of this; it is a bound on a sprite that never
     * finishes, so that a stuck animation costs a delay and never the Run.
     */
    static final float SETTLE_MOST = 3f;

    /** Makes this the gate the executor's announcement reaches. */
    public void install() {
        live = this;
    }

    /** Stops being the gate the announcement reaches, if it still is. */
    public void uninstall() {
        if (live == this) {
            live = null;
        }
    }

    /** The gate installed in this process, or null. */
    public static WaitGate live() {
        return live;
    }

    /** Hook row 5's listener: on the actor thread, one volatile write and nothing else. */
    public void noticed() {
        notifications++;
    }

    /** An Action was handed to the game, which makes the next wait a new one (ADR-0015 as story 1.5 amended it). */
    public void handedOver() {
        acted = true;
    }

    /**
     * The hero holds an action the caller handed over since the last wait: a move or an attack is in
     * the hero's hand until his next act, which may begin and end ready in one go and announce
     * nothing, so holding one is the same as an announcement.
     */
    public void heroHolds(Hero hero) {
        if (hero != null && (hero.curAction != null || hero.resting)) {
            acted = true;
        }
    }

    /**
     * One frame's answer: the index of the wait this frame confirms, or 0 when it confirms none.
     * Call it once per frame, after the frame, with what the frame left: the hero, the window in
     * front, and whether anything is queued for the render thread. A frame with something queued
     * changes nothing here, because the queue runs first in the next frame and can put a window in
     * front of the hero before anyone could observe or click.
     */
    public long frame(Hero hero, Window window, boolean runnablesPending) {
        if (runnablesPending) {
            return 0;
        }
        if (window != lastSeenWindow) {
            lastSeenWindow = window;
            windowFramesShown = 1;
        } else {
            windowFramesShown++;
        }
        boolean notified = notifications != seenNotifications;
        seenNotifications = notifications;
        if ((notified || acted || carried || window != lastConfirmedWindow)
                && (window == null || windowFramesShown >= 2) && HeadlessDriver.waitState(hero, window)) {
            if (window == null && !settled(hero)) {
                // Story 5.13. A search, a read or a zap leaves the hero ready while his sprite still plays
                // the animation, and the game clears the action held in his hand when that animation ends
                // (Hero.onOperateComplete, Hero.java:2505). So whether an Action handed over in that gap
                // survived depended on how long the frames were: a headless frame is a fifth of a second
                // and a desktop one a sixtieth, and the same Run played two ways. The wait is confirmed
                // once the animation is over, which is a wait the game would take no Action at before
                // either, so no Run loses a turn it could have had.
                carried = true;
                unsettled += Game.elapsed;
                return 0;
            }
            carried = false;
            unsettled = 0;
            waitIndex++;
            lastConfirmedWindow = window;
            acted = false;
            return waitIndex;
        }
        if (notified && !HeadlessDriver.heroWaits(hero) && !HeadlessDriver.resurrecting()) {
            dropped++;
        }
        return 0;
    }

    /**
     * A new scene was created for the Run: whatever was announced or handed over belongs to the floor
     * that is gone, and the new floor's first wait is announced by the hero's first act on it.
     */
    public void sceneChanged() {
        acted = false;
        carried = false;
        unsettled = 0;
        seenNotifications = notifications;
    }

    /** Puts the gate back so that the next wait confirmed is {@code k}, in front of no window (story 1.20). */
    public void restoreTo(long k) {
        sceneChanged();
        waitIndex = k - 1;
        lastConfirmedWindow = null;
        lastSeenWindow = null;
        windowFramesShown = 0;
    }

    /**
     * Takes back the confirmation of wait {@code k}, so that the next wait confirmed is {@code k} again,
     * from whatever is in front then: the embedded Run's answer to it went stale while the Brain thought
     * (story 5.1). Unlike {@link #restoreTo}, what has been announced since is kept, and the next frame
     * that finds the hero waiting confirms, whether or not anything new is announced.
     */
    public void reconfirm(long k) {
        waitIndex = k - 1;
        acted = true;
        carried = false;
        unsettled = 0;
        lastConfirmedWindow = null;
        lastSeenWindow = null;
        windowFramesShown = 0;
    }

    /**
     * Whether the hero's sprite has finished the animation it was playing: it is on a looping one (the
     * idle and the run), is paused (frozen, paralysed), or has been held {@link #SETTLE_MOST} game
     * seconds. A hero with no sprite is settled.
     */
    private boolean settled(Hero hero) {
        if (hero == null || hero.sprite == null || hero.sprite.paused || hero.sprite.looping()) {
            return true;
        }
        return unsettled >= SETTLE_MOST;
    }

    /** The index of the last wait confirmed; 0 before the first. */
    public long waitIndex() {
        return waitIndex;
    }

    /** Times hook row 5 has notified: acts of the hero that began unready. */
    public long notifications() {
        return notifications;
    }

    /** Notifications that found the hero mid-action. */
    public long dropped() {
        return dropped;
    }

    /** Whether an Action has been handed over and no wait confirmed since. */
    public boolean acted() {
        return acted;
    }

    /** Whether nothing has been announced since the last frame read the count. */
    public boolean quiet() {
        return notifications == seenNotifications;
    }

    /** The window in front at the last wait confirmed, or null. */
    public Window lastConfirmedWindow() {
        return lastConfirmedWindow;
    }
}
