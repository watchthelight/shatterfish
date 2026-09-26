package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

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
    private long waitIndex;
    private Window lastConfirmedWindow;
    private Window lastSeenWindow;
    private int windowFramesShown;

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
        if ((notified || acted || window != lastConfirmedWindow)
                && (window == null || windowFramesShown >= 2) && HeadlessDriver.waitState(hero, window)) {
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
        lastConfirmedWindow = null;
        lastSeenWindow = null;
        windowFramesShown = 0;
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
