package org.shatterfish.api;

import java.util.Arrays;

/**
 * UTF-8 encoding of the exact Java string, with no normalization: a surrogate pair becomes its
 * four-byte code point, and an unpaired surrogate becomes {@code ?} as {@code String.getBytes}
 * makes it. Written here because {@code api} may reach only {@code java.lang} and
 * {@code java.util} (ADR-0003, {@code ApiBoundaryTest}), and the character sets live elsewhere.
 */
final class Utf8 {

    private Utf8() {
    }

    static byte[] encode(String text) {
        int length = text.length();
        byte[] out = new byte[length * 3];
        int size = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                out[size++] = (byte) c;
            } else if (c < 0x800) {
                out[size++] = (byte) (0xC0 | (c >> 6));
                out[size++] = (byte) (0x80 | (c & 0x3F));
            } else if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                int codePoint = Character.toCodePoint(c, text.charAt(++i));
                out[size++] = (byte) (0xF0 | (codePoint >> 18));
                out[size++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
                out[size++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
                out[size++] = (byte) (0x80 | (codePoint & 0x3F));
            } else if (Character.isSurrogate(c)) {
                out[size++] = (byte) '?';
            } else {
                out[size++] = (byte) (0xE0 | (c >> 12));
                out[size++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                out[size++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        return Arrays.copyOf(out, size);
    }
}
