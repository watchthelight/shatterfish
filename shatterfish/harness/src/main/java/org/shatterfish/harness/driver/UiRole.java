package org.shatterfish.harness.driver;

/**
 * The UI-role thread of ADR-0013: the one thread that observes and executes. Headless it is the
 * driver thread, the thread that starts a Run; in the Overlay it will be the render thread. A
 * driver claims it for the thread that starts the Run and releases it on close; a port asks it on
 * entry and fails loudly on any other thread, naming the role, its owner and the caller, before
 * it reads a single game field. The identity is the thread object, never its name, so a foreign
 * call cannot pass by being called the same thing; the messages carry each thread's id beside its
 * name for the same reason.
 *
 * <p>This is FR-12's confinement of the ports. The rest of ADR-0013's deadlock rule, that no
 * Shatterfish code takes a monitor on a game type, is {@code MonitorConfinementTest}'s, and the
 * worker holding only an immutable Observation is the Overlay's to keep.
 */
public final class UiRole {

    private static volatile Thread owner;

    private UiRole() {
    }

    /**
     * Claims the role for {@code thread} for the Run that starts. A claim by the thread that
     * already holds it is idempotent; a claim while another thread holds it is refused; a thread
     * that is not alive cannot hold it, since it could never call.
     */
    public static synchronized void claim(Thread thread) {
        if (thread == null) {
            throw new IllegalArgumentException("a thread");
        }
        if (!thread.isAlive()) {
            throw new IllegalArgumentException(describe(thread) + " is not alive and cannot hold the UI-role thread");
        }
        if (owner != null && owner != thread) {
            throw new IllegalStateException("the UI-role thread is " + describe(owner) + " until that Run closes; "
                    + describe(thread) + " cannot claim it (ADR-0013: one Run, one UI-role thread)");
        }
        owner = thread;
    }

    /**
     * Releases the role the calling thread holds. A release from any other thread is refused: it
     * would make the owner's next call fail as if no Run were live.
     */
    public static void release() {
        release(Thread.currentThread());
    }

    /**
     * Releases the role {@code claimant} holds, for the driver that claimed it on that thread and
     * closes on whichever thread closes it, a test's teardown included. Refused while another
     * thread holds the role; a no-op when nobody does.
     */
    public static synchronized void release(Thread claimant) {
        Thread holder = owner;
        if (holder != null && holder != claimant) {
            throw new IllegalStateException("only the UI-role thread, " + describe(holder) + ", may release the role; "
                    + describe(claimant) + " tried");
        }
        owner = null;
    }

    /** The thread that holds the role, or null between Runs. */
    public static Thread owner() {
        return owner;
    }

    /**
     * Fails unless the calling thread holds the role. {@code port} names the caller for the
     * message, as in {@code ActionExecutor.execute()}.
     */
    public static void require(String port) {
        Thread holder = owner;
        Thread caller = Thread.currentThread();
        if (holder == null) {
            throw new IllegalStateException(port + " ran on " + describe(caller) + ", but no Run is in progress: the"
                    + " UI-role thread is claimed by the driver that starts a Run (ADR-0013)");
        }
        if (holder != caller) {
            throw new IllegalStateException(port + " must run on the UI-role thread, which is " + describe(holder)
                    + ", the thread that started this Run; it ran on " + describe(caller) + " (ADR-0013: the"
                    + " Observer and the executor are confined to the thread that owns the game)");
        }
    }

    /** A thread by name and id, since two threads may share a name. */
    static String describe(Thread thread) {
        return "'" + thread.getName() + "' (#" + thread.threadId() + ")";
    }
}
