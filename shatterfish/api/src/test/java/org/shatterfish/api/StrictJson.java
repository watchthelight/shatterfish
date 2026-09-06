package org.shatterfish.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reader of the canonical JSON the writer promises, for the tests only: no whitespace, an
 * object's keys strictly ascending by UTF-16 code units, integers without fraction or exponent,
 * no null, and only the escapes JSON defines. Anything else fails. Objects come back as
 * {@link LinkedHashMap}, arrays as {@link ArrayList}, numbers as {@link Long}.
 */
final class StrictJson {

    private final String text;
    private int at;

    private StrictJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        StrictJson reader = new StrictJson(text);
        Object value = reader.value();
        reader.require(reader.at == text.length(), "text after the value");
        return value;
    }

    private Object value() {
        char c = peek();
        if (c == '{') {
            return object();
        } else if (c == '[') {
            return array();
        } else if (c == '"') {
            return string();
        } else if (c == 't') {
            return literal("true", Boolean.TRUE);
        } else if (c == 'f') {
            return literal("false", Boolean.FALSE);
        } else if (c == '-' || (c >= '0' && c <= '9')) {
            return number();
        }
        throw error("unexpected character " + c);
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> members = new LinkedHashMap<>();
        if (peek() == '}') {
            at++;
            return members;
        }
        String last = null;
        while (true) {
            String key = string();
            require(last == null || key.compareTo(last) > 0, "key " + key + " is not after " + last);
            last = key;
            expect(':');
            members.put(key, value());
            char c = next();
            if (c == '}') {
                return members;
            }
            require(c == ',', "expected , or } in an object");
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> elements = new ArrayList<>();
        if (peek() == ']') {
            at++;
            return elements;
        }
        while (true) {
            elements.add(value());
            char c = next();
            if (c == ']') {
                return elements;
            }
            require(c == ',', "expected , or ] in an array");
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            } else if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        require(at + 4 <= text.length(), "short unicode escape");
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw error("bad escape \\" + e);
                }
            } else {
                require(c >= 0x20, "unescaped control character");
                out.append(c);
            }
        }
    }

    private Long number() {
        int start = at;
        if (peek() == '-') {
            at++;
        }
        require(peek() >= '0' && peek() <= '9', "a number needs a digit");
        while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') {
            at++;
        }
        String digits = text.substring(start, at);
        require(!digits.matches("-?0\\d+"), "a number has no leading zero: " + digits);
        return Long.parseLong(digits);
    }

    private Object literal(String word, Object value) {
        require(text.startsWith(word, at), "expected " + word);
        at += word.length();
        return value;
    }

    private char peek() {
        require(at < text.length(), "unexpected end");
        return text.charAt(at);
    }

    private char next() {
        char c = peek();
        at++;
        return c;
    }

    private void expect(char c) {
        require(next() == c, "expected " + c);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw error(message);
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + at + " in " + text.substring(Math.max(0, at - 20),
                Math.min(text.length(), at + 20)));
    }
}
