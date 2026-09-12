package org.shatterfish.harness.rng;

import com.watabou.utils.Random;

/**
 * Owns the game's generator stack for one Run, so that what the game draws is a function of the
 * Run's tuple and nothing else (ADR-0007).
 *
 * <p>At every Input wait the harness puts a generator seeded {@link Mix#mix} on top of the stack and
 * every draw until the next wait comes from it. The salt is the Run's, drawn by whoever ran it and
 * written down; the index is the wait's. Two Runs that declare the same tuple therefore draw the
 * same numbers, which is the whole of reproducibility here — there is no counting of draws and no
 * dependence on how many any version of the game happens to make.
 *
 * <p>It reseeds and never reads. The stack's deque is private upstream and there is no accessor for
 * its depth, so this cannot assert that the stack is as deep as it left it, which is what ADR-0007's
 * pre-mortem asks for. The check that replaces it is stronger and needs no hook: at wait {@code k}
 * the numbers drawn must be exactly those of a generator seeded {@code mix(salt, k)}, and a
 * generator the game pushed and did not pop would change them. {@code MixTestVectorTest} computes
 * the expected draws itself and compares.
 *
 * <p>One of these belongs to one Run. It is not thread-safe and does not need to be: it is driven
 * from the UI-role thread at a wait, which is the only thread Shatterfish acts on (AD-8).
 */
public final class RngControl {

    private final long salt;

    /** Whether this Run has a generator of its own on the stack, to be replaced at the next wait. */
    private boolean pushed;

    /** The wait this Run last reseeded for, for the message when something asks out of order. */
    private long lastWait = -1;

    public RngControl(long salt) {
        this.salt = salt;
    }

    /** The salt this Run declares. It is written down with the Run and never observed. */
    public long salt() {
        return salt;
    }

    /** The wait this Run has reseeded for, or -1 before the first one. */
    public long lastWait() {
        return lastWait;
    }

    /** The seed wait {@code k} of this Run draws from, which anyone can recompute (see {@link Mix}). */
    public long seedFor(long k) {
        return Mix.mix(salt, k);
    }

    /**
     * Puts this Run's generator for wait {@code k} on top of the stack, taking the previous wait's
     * off first so the stack does not grow by one a wait.
     *
     * <p>The first call happens after the game's own initialisation has reset the stack
     * ({@code core/.../Dungeon.java:254}), which is why the driver calls this at the wait and not
     * at the Run's start: {@code Dungeon.init} would throw away anything pushed before it.
     */
    public void reseed(long k) {
        if (k < 0) {
            throw new IllegalArgumentException("a wait index counts from zero: " + k);
        }
        if (pushed) {
            Random.popGenerator();
        }
        Random.pushGenerator(seedFor(k));
        pushed = true;
        lastWait = k;
    }

    /**
     * Takes this Run's generator off the stack, leaving it as the Run found it. The driver calls
     * this when a Run ends; a Run that has never reseeded does nothing here.
     */
    public void release() {
        if (pushed) {
            Random.popGenerator();
            pushed = false;
        }
    }
}
