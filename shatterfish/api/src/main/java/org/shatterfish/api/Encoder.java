package org.shatterfish.api;

import java.util.Arrays;

/**
 * The canonical encoding's primitives (ADR-0005, option 8): big-endian fixed-width integers, one
 * byte per boolean, strings as a four-byte length and the UTF-8 code units of the exact Java
 * string, enums by name, lists as a four-byte count followed by their elements. Nothing else, and
 * no floats: a schema that needs a fraction stores integers over a stated denominator.
 */
final class Encoder {

    private byte[] bytes = new byte[256];
    private int size;

    void int32(int value) {
        ensure(4);
        bytes[size++] = (byte) (value >>> 24);
        bytes[size++] = (byte) (value >>> 16);
        bytes[size++] = (byte) (value >>> 8);
        bytes[size++] = (byte) value;
    }

    void bool(boolean value) {
        ensure(1);
        bytes[size++] = (byte) (value ? 1 : 0);
    }

    void string(String value) {
        byte[] utf8 = Utf8.encode(value);
        int32(utf8.length);
        raw(utf8);
    }

    void name(Enum<?> value) {
        string(value.name());
    }

    void count(int elements) {
        int32(elements);
    }

    void raw(byte[] value) {
        ensure(value.length);
        // A loop rather than System.arraycopy: java.lang.System is a door the api module keeps shut.
        for (byte b : value) {
            bytes[size++] = b;
        }
    }

    byte[] toByteArray() {
        return Arrays.copyOf(bytes, size);
    }

    private void ensure(int more) {
        if (size + more > bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
        }
    }
}
