package org.shatterfish.brain;

import org.shatterfish.api.Belief;

/**
 * What the Brain carries from one Input wait to the next, and nothing else (story 4.1).
 *
 * <p>It is written into a {@link Belief}'s bytes, so the harness can hash it for the Run log without
 * knowing its shape, and it holds only what the Brain has <em>seen</em>: how many waits it has
 * served and the deepest floor it has observed. Deliberately absent is anything the Brain
 * <em>did</em> -- its last Action, its plan -- because a human may take any turn, and a Brain that
 * remembered its intention would act on an intention the game never carried out (FR-27, FR-28).
 *
 * @param waits   the Input waits served so far
 * @param deepest the deepest floor any Observation has shown
 */
record Memory(long waits, int deepest) {

    /** The meaning of the bytes; bumped when it changes. */
    static final int VERSION = 1;

    static final Memory START = new Memory(0, 0);

    Memory {
        if (waits < 0 || deepest < 0) {
            throw new IllegalArgumentException("a memory counts from zero: " + waits + ", " + deepest);
        }
    }

    /** Big-endian: eight bytes of waits, four of depth. */
    Belief belief() {
        byte[] bytes = new byte[12];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (waits >>> (56 - 8 * i));
        }
        for (int i = 0; i < 4; i++) {
            bytes[8 + i] = (byte) (deepest >>> (24 - 8 * i));
        }
        return new Belief(VERSION, bytes);
    }

    /** The memory a Belief holds; the start for none. Refuses a Belief of another version or shape. */
    static Memory of(Belief belief) {
        if (belief == null) {
            return START;
        }
        byte[] bytes = belief.bytes();
        if (belief.version() != VERSION || bytes.length != 12) {
            throw new IllegalArgumentException("not a Belief this Brain wrote: " + belief);
        }
        long waits = 0;
        for (int i = 0; i < 8; i++) {
            waits = (waits << 8) | (bytes[i] & 0xFF);
        }
        int deepest = 0;
        for (int i = 0; i < 4; i++) {
            deepest = (deepest << 8) | (bytes[8 + i] & 0xFF);
        }
        return new Memory(waits, deepest);
    }
}
