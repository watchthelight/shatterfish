package org.shatterfish.harness.driver;

/**
 * The UI-role thread of ADR-0013: the one thread that observes and executes. Headless it is the
 * driver thread, the thread that starts a Run; in the Overlay it will be the render thread. A
 * driver claims it for the thread that starts the Run and releases it on close; a port asks it on
 * entry and fails loudly on any other thread, naming the role, its owner and the caller, before
 * it reads a single game field. The identity is the thread object, never a name, so a foreign
 * call cannot pass by being called the same thing.
 *
 * <p>This is FR-12's confinement, and the reason the Overlay cannot deadlock the way the game once
 * did: the actor thread never runs Shatterfish code, the worker holds only an immutable
 * Observation, and everything that touches the game runs on the one thread that owns it.
 */
public final class UiRole {

    private static volatile Thread owner;

    private UiRole() {
    }

    /** Claims the role for {@code thread} for the Run that starts; refuses while another Run holds it. */
    public static synchronized void claim(Thread thread) {
        if (thread == null) {
            throw new IllegalArgumentException("a thread");
        }
        if (owner != null && owner != thread) {
            throw new IllegalStateException("the UI-role thread is '" + owner.getName() + "' until that Run closes;"
                    + " '" + thread.getName() + "' cannot claim it (ADR-0013: one Run, one UI-role thread)");
        }
        owner = thread;
    }

    /** Releases the role when the Run that claimed it closes. */
    public static synchronized void release() {
        owner = null;
    }

    /** The thread that holds the role, or null between Runs. */
    public static Thread owner() {
        return owner;
    }

    /**
     * Fails unless the calling thread holds the role. {@code port} names the caller for the
     * message, as in {@code Observer.observe()}.
     */
    public static void require(String port) {
        Thread holder = owner;
        Thread caller = Thread.currentThread();
        if (holder == null) {
            throw new IllegalStateException(port + " ran on '" + caller.getName() + "', but no Run is in progress: the"
                    + " UI-role thread is claimed by the driver that starts a Run (ADR-0013)");
        }
        if (holder != caller) {
            throw new IllegalStateException(port + " must run on the UI-role thread, which is '" + holder.getName()
                    + "', the thread that started this Run; it ran on '" + caller.getName() + "' (ADR-0013: the"
                    + " Observer and the executor are confined to the thread that owns the game)");
        }
    }
}
