package org.shatterfish.api;

import java.util.Arrays;

/**
 * What the brain remembers between Input waits, as an opaque versioned value (ADR-0005; AD-14).
 * It is not part of the Observation and nothing outside the brain reads its bytes: the harness
 * carries it from one decision to the next and hashes it for the Run log without knowing its
 * shape, which is what lets the log record a Belief without depending on the brain. The version
 * is the brain's own, bumped when the bytes' meaning changes, and part of the hash.
 */
public final class Belief {

    private final int version;
    private final byte[] bytes;

    /**
     * @param version the brain's version of the bytes' meaning, from 1
     * @param bytes the brain's state, copied
     */
    public Belief(int version, byte[] bytes) {
        Canon.require(version >= 1, "a Belief's version starts at 1: " + version);
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

    /** SHA-256 over the version, as the codec writes an integer, followed by the bytes, in hex. */
    public String hash() {
        Encoder out = new Encoder();
        out.int32(version);
        out.raw(bytes);
        return Sha256.hex(Sha256.digest(out.toByteArray()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Belief that && that.version == version && Arrays.equals(that.bytes, bytes);
    }

    @Override
    public int hashCode() {
        return 31 * version + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Belief[version=" + version + ", bytes=" + bytes.length + ", hash=" + hash().substring(0, 16) + "]";
    }
}
