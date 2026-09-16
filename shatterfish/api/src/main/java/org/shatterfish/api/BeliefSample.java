package org.shatterfish.api;

import java.util.Arrays;

/**
 * One draw from a Brain's belief over what the player cannot know (ADR-0009): the assignment a
 * {@link Redeterminer} writes into a snapshot's hidden state before a rollout. Reserved in E1 as
 * an opaque versioned value, the shape {@link Belief} has: the bytes' meaning is the Brain's and
 * the scrubber's (E6), and nothing else reads them.
 */
public final class BeliefSample {

    private final int version;
    private final byte[] bytes;

    /**
     * @param version the version of the bytes' meaning, from 1
     * @param bytes the sample, copied
     */
    public BeliefSample(int version, byte[] bytes) {
        Canon.require(version >= 1, "a BeliefSample's version starts at 1: " + version);
        this.version = version;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public int version() {
        return version;
    }

    /** A copy of the bytes. */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public int size() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BeliefSample that && that.version == version && Arrays.equals(that.bytes, bytes);
    }

    @Override
    public int hashCode() {
        return 31 * version + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "BeliefSample[version=" + version + ", " + bytes.length + " bytes]";
    }
}
