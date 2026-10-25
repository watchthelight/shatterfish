package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;

/**
 * The gate a person's input to the hero passes (story 5.5; ADR-0013 option 9; hook row 9).
 *
 * <p>{@code CellSelector.select} and {@code CellSelector.processKeyHold} ask {@link #open()} before they
 * act. It answers false while {@link #humanInput} is off -- PAUSED, and every Mode but HUMAN -- so a click
 * or a held direction key changes nothing the Run's log would have to explain. The held-key path matters
 * most: it bypasses every cell listener and forces the hero ready itself
 * ({@code CellSelector.java:469-486} at the pinned tag).
 *
 * <p>The Run's own {@link ActionExecutor} makes the same {@code GameScene.handleCell} call a click makes,
 * and so reaches the same {@code select}; it opens the gate for the length of its own call
 * ({@link #executing}) and for nothing longer, on the thread that owns the scene, so a person's click
 * handled on another frame is still shut out.
 *
 * <p>What the gate does not cover, and what does: the toolbar, the quickslots and the inventory pane call
 * {@code Hero} and {@code Item} directly and never reach the selector. A mouse or a key reaches them only
 * through the game's input multiplexer, where the Overlay's {@code InputLock} stands first. A gamepad
 * button on one of those widgets writes the key queue past the lock and is not gated (docs/ideas/).
 */
public final class HeroInputGate implements Hooks.InputGate {

    /** Depth of the executor's own calls on this thread; the gate is open to them. */
    private static final ThreadLocal<int[]> EXECUTING = ThreadLocal.withInitial(() -> new int[1]);

    private volatile boolean humanInput;

    /** A gate with a person's input shut out, as every Run but a HUMAN one has it. */
    public HeroInputGate() {
    }

    /** Whether a person's input reaches the hero: true for a HUMAN Run, false while PAUSED or RUNNING. */
    public void humanInput(boolean allowed) {
        this.humanInput = allowed;
    }

    public boolean humanInput() {
        return humanInput;
    }

    @Override
    public boolean open() {
        return humanInput || EXECUTING.get()[0] > 0;
    }

    /** Marks the start of the executor's own call on this thread; pair with {@link #executed()} in a finally. */
    static void executing() {
        EXECUTING.get()[0]++;
    }

    /** Marks the end of the executor's own call on this thread. */
    static void executed() {
        EXECUTING.get()[0]--;
    }
}
