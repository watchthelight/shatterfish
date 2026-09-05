package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The hand-written SHA-256 against the standard's vectors and the JDK's digest. */
class Sha256Test {

    @Test
    @DisplayName("the FIPS 180-4 vectors")
    void the_standard_vectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Sha256.hex(Sha256.digest(new byte[0])));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.hex(Sha256.digest("abc".getBytes(StandardCharsets.US_ASCII))));
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                Sha256.hex(Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
                        .getBytes(StandardCharsets.US_ASCII))));
        byte[] million = new byte[1_000_000];
        Arrays.fill(million, (byte) 'a');
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
                Sha256.hex(Sha256.digest(million)));
    }

    @Test
    @DisplayName("every length around the block size agrees with the JDK")
    void agrees_with_the_jdk() throws Exception {
        MessageDigest jdk = MessageDigest.getInstance("SHA-256");
        Random random = new Random(256);
        for (int length = 0; length < 300; length++) {
            byte[] input = new byte[length];
            random.nextBytes(input);
            assertArrayEquals(jdk.digest(input), Sha256.digest(input), "length " + length);
        }
    }

    @Test
    void hex_is_lower_case_and_two_digits_a_byte() {
        byte[] bytes = {0, 1, (byte) 0x7f, (byte) 0x80, (byte) 0xab, (byte) 0xff};
        assertEquals(HexFormat.of().formatHex(bytes), Sha256.hex(bytes));
    }
}
