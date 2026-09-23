package org.shatterfish.harness.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reader of the one shape the Run log is written in (story 3.4).
 *
 * <p>It is not a JSON reader. It reads the canonical text {@code RunLogJson} produces — sorted
 * keys, no whitespace, integers, the escape table the methodology page publishes — and refuses
 * everything else, naming what it found. A log is a generated file: every deviation from that shape
 * is a hand edit or a drift, and a parse that quietly accepted a file the writer would never produce
 * would be accepting exactly the thing the format exists to make visible.
 *
 * <p>It is the one reader in production. After story 3.3 there were two implementations of this
 * grammar — one in the Rig, one in the harness's tests — and this story would have added a third.
 * One production reader, one deliberately independent test reader, and a test that the two agree is
 * two implementations keeping each other honest; three is none.
 *
 * <p>It refuses a key written twice. Every JSON reader downstream takes the last one, so a line
 * carrying a key twice means one thing to whoever reads it first and another to everyone else —
 * and story 3.3 found a guard defeated by exactly that.
 */
public final class Json {

    /**
     * One backslash, built from its code point.
     *
     * <p>A backslash followed by the letter u is a unicode escape in Java source -- in a
     * string, in a comment, anywhere -- and the compiler processes it before it lexes
     * anything. Writing that pair in this file, even to talk about it, does not compile.
     */
    private static final String BACKSLASH = String.valueOf((char) 92);

    private Json() {
    }

    /**
     * The members of one object, by name, each as the raw text of its value.
     *
     * <p>Raw rather than parsed: the caller knows which of its keys are numbers and which are
     * strings, and a reader that guessed would be inventing a type system the format does not have.
     */
    public static Map<String, String> object(String text) {
        require(text != null && text.length() >= 2 && text.charAt(0) == '{'
                && text.charAt(text.length() - 1) == '}', "an object", text);
        Map<String, String> members = new LinkedHashMap<>();
        int at = 1;
        while (at < text.length() - 1) {
            require(text.charAt(at) == '"', "a quoted key at " + at, text);
            int keyEnd = endOfString(text, at);
            String key = string(text.substring(at, keyEnd));
            require(keyEnd < text.length() && text.charAt(keyEnd) == ':', "a colon at " + keyEnd, text);
            int to = endOfValue(text, keyEnd + 1);
            require(members.put(key, text.substring(keyEnd + 1, to)) == null,
                    "the key " + key + " written once, and a reader taking the other one would read"
                            + " something else", text);
            at = to;
            if (at < text.length() - 1) {
                require(text.charAt(at) == ',', "a comma at " + at, text);
                at++;
            }
        }
        return members;
    }

    /** The elements of one array, each as the raw text of its value. */
    public static List<String> array(String text) {
        require(text != null && text.length() >= 2 && text.charAt(0) == '['
                && text.charAt(text.length() - 1) == ']', "an array", text);
        List<String> elements = new ArrayList<>();
        int at = 1;
        while (at < text.length() - 1) {
            int to = endOfValue(text, at);
            elements.add(text.substring(at, to));
            at = to;
            if (at < text.length() - 1) {
                require(text.charAt(at) == ',', "a comma at " + at, text);
                at++;
            }
        }
        return List.copyOf(elements);
    }

    /** A string value, unquoted and unescaped by the table the methodology page publishes. */
    public static String string(String raw) {
        require(raw != null && raw.length() >= 2 && raw.charAt(0) == '"'
                && raw.charAt(raw.length() - 1) == '"', "a string", raw);
        StringBuilder out = new StringBuilder(raw.length() - 2);
        for (int i = 1; i < raw.length() - 1; i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            require(i + 1 < raw.length() - 1, "an escape with something after it", raw);
            char escaped = raw.charAt(++i);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    require(i + 4 < raw.length() - 1, "four hex digits after a " + BACKSLASH + "u", raw);
                    String hex = raw.substring(i + 1, i + 5);
                    require(hex.matches("[0-9a-f]{4}"),
                            "four lower-case hex digits, as the format writes them", raw);
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                }
                // The writer produces no other escape, so neither does this reader accept one.
                // An escaped solidus, and a unicode escape written in upper case, are both
                // legal JSON and both a sign that something other than this project wrote
                // the file.
                default -> throw refuse("an escape the writer produces, not " + BACKSLASH + escaped, raw);
            }
        }
        return out.toString();
    }

    /** A whole number. The format writes no floats, so this refuses one rather than rounding it. */
    public static long number(String raw) {
        require(raw != null && raw.matches("-?\\d+"), "a whole number", raw);
        return Long.parseLong(raw);
    }

    public static int integer(String raw) {
        long value = number(raw);
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE, "a number that fits", raw);
        return (int) value;
    }

    public static boolean bool(String raw) {
        require("true".equals(raw) || "false".equals(raw), "true or false", raw);
        return "true".equals(raw);
    }

    /** The raw text of {@code key}, refusing a member the caller requires and the object lacks. */
    public static String required(Map<String, String> members, String key, String what) {
        String raw = members.get(key);
        require(raw != null, "a " + what + " with a " + key, members.toString());
        return raw;
    }

    // --------------------------------------------------------------------- walking the characters

    static int endOfString(String text, int at) {
        for (int i = at + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i + 1;
            }
        }
        throw refuse("a string that closes, from " + at, text);
    }

    static int endOfValue(String text, int at) {
        int depth = 0;
        int i = at;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                i = endOfString(text, i);
                if (depth == 0) {
                    return i;
                }
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (depth == 0) {
                    return i;
                }
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            } else if (c == ',' && depth == 0) {
                return i;
            }
            i++;
        }
        throw refuse("a value that closes, from " + at, text);
    }

    private static void require(boolean held, String expected, String found) {
        if (!held) {
            throw refuse(expected, found);
        }
    }

    private static IllegalArgumentException refuse(String expected, String found) {
        String shown = found == null ? "nothing" : found.length() > 200 ? found.substring(0, 200) + "…" : found;
        return new IllegalArgumentException("the Run log's own shape has " + expected + ", and this"
                + " is not it: " + shown);
    }
}
