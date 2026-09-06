package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Belief is an opaque versioned value the harness can hash without knowing its shape
 * (ADR-0005; story 1.7): equal by content, its bytes its own, its hash over the version and the
 * bytes.
 */
class BeliefTest {

    @Test
    @DisplayName("two Beliefs of the same version and bytes are equal, and either differing makes them not")
    void equality_is_by_content() {
        byte[] bytes = {1, 2, 3};
        assertEquals(new Belief(1, bytes), new Belief(1, new byte[] {1, 2, 3}));
        assertEquals(new Belief(1, bytes).hashCode(), new Belief(1, new byte[] {1, 2, 3}).hashCode());
        assertNotEquals(new Belief(1, bytes), new Belief(2, bytes));
        assertNotEquals(new Belief(1, bytes), new Belief(1, new byte[] {1, 2, 4}));
        assertNotEquals(new Belief(1, bytes), new Belief(1, new byte[] {1, 2}));
        assertEquals(new Belief(1, new byte[0]), new Belief(1, new byte[0]));
    }

    @Test
    @DisplayName("the bytes are copied in and out, so no caller can change a Belief")
    void the_bytes_are_copied() {
        byte[] bytes = {1, 2, 3};
        Belief belief = new Belief(1, bytes);
        bytes[0] = 9;
        assertEquals(1, belief.bytes()[0]);
        belief.bytes()[1] = 9;
        assertEquals(2, belief.bytes()[1]);
        assertEquals(3, belief.size());
    }

    @Test
    @DisplayName("the hash is SHA-256 over the version as a big-endian integer followed by the bytes")
    void the_hash_is_over_the_version_and_the_bytes() throws Exception {
        byte[] bytes = {5, 6, 7, 8, 9};
        Belief belief = new Belief(3, bytes);
        ByteBuffer input = ByteBuffer.allocate(4 + bytes.length);
        input.putInt(3);
        input.put(bytes);
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.array()));
        assertEquals(expected, belief.hash());
        assertNotEquals(belief.hash(), new Belief(4, bytes).hash());
        assertNotEquals(belief.hash(), new Belief(3, new byte[] {5, 6, 7, 8}).hash());
    }

    @Test
    @DisplayName("a Belief's version starts at 1")
    void the_version_starts_at_one() {
        assertThrows(IllegalArgumentException.class, () -> new Belief(0, new byte[0]));
        assertThrows(NullPointerException.class, () -> new Belief(1, null));
    }
}
