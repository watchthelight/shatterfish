package org.shatterfish.brain;

import java.util.Arrays;

/**
 * Big-endian bytes for a {@link Memory}, written and read by hand: {@code brain} may not reach
 * {@code java.io} or {@code java.nio}, so there is no stream or charset to lean on. A string is its
 * length and then its UTF-16 code units, two bytes each, which round-trips every Java string
 * without a charset.
 */
final class Bytes {

    /** Appends to a growing array. */
    static final class Writer {

        private byte[] bytes = new byte[64];
        private int size;

        Writer integer(int value) {
            room(4);
            for (int i = 0; i < 4; i++) {
                bytes[size++] = (byte) (value >>> (24 - 8 * i));
            }
            return this;
        }

        Writer number(long value) {
            room(8);
            for (int i = 0; i < 8; i++) {
                bytes[size++] = (byte) (value >>> (56 - 8 * i));
            }
            return this;
        }

        Writer text(String value) {
            integer(value.length());
            room(2 * value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                bytes[size++] = (byte) (c >>> 8);
                bytes[size++] = (byte) c;
            }
            return this;
        }

        byte[] bytes() {
            return Arrays.copyOf(bytes, size);
        }

        private void room(int more) {
            if (size + more > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
            }
        }
    }

    /** Reads what a {@link Writer} wrote, refusing to read past the end. */
    static final class Reader {

        private final byte[] bytes;
        private int at;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        int integer() {
            need(4);
            int value = 0;
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (bytes[at++] & 0xFF);
            }
            return value;
        }

        long number() {
            need(8);
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[at++] & 0xFF);
            }
            return value;
        }

        String text() {
            int length = integer();
            if (length < 0) {
                throw new IllegalArgumentException("a string of negative length: " + length);
            }
            need(2L * length);
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = (char) (((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF));
                at += 2;
            }
            return new String(chars);
        }

        /** Refuses bytes left over: a Belief read in full or not at all. */
        void end() {
            if (at != bytes.length) {
                throw new IllegalArgumentException((bytes.length - at) + " bytes left unread");
            }
        }

        private void need(long more) {
            if (at + more > bytes.length) {
                throw new IllegalArgumentException("the bytes end before the memory does");
            }
        }
    }

    private Bytes() {
    }
}
