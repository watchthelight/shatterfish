package org.shatterfish.harness.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reader of the one shape the Run log is written in (story 3.4).
 *
 * <p>It is not a JSON reader. It reads the canonical text {@code RunLogJson} produces — sorted
 * keys, no whitespace, whole numbers, the escape table the methodology page publishes — and refuses
 * everything else, naming what it found. A log is a generated file: every deviation from that shape
 * is a hand edit or a drift, and a parse that quietly accepted a file the writer would never produce
 * would be accepting exactly the thing the format exists to make visible.
 *
 * <p><b>Every clause of that paragraph is a check.</b> The first draft of this class promised all
 * four properties in this comment and implemented one of them, which the story's own review found:
 * unsorted keys parsed, a trailing comma parsed, an empty value parsed, and {@code {"a":[1}} parsed
 * with the value {@code [1}}. A guard that is documented and not written is worse than one that is
 * absent, because the page tells a reader to rely on it.
 *
 * <p>It is the one reader in production. After story 3.3 there were two implementations of this
 * grammar — one in the Rig, one in the harness's tests — and this story would have added a third.
 * One production reader, one deliberately independent test reader, and a test that the two agree is
 * two implementations keeping each other honest; three is none.
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

    /** The longest run of digits a {@code long} can hold, sign aside. */
    private static final int LONGEST_NUMBER = 19;

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
        String previous = null;
        while (at < text.length() - 1) {
            require(text.charAt(at) == '"', "a quoted key at " + at, text);
            int keyEnd = endOfString(text, at);
            String key = key(text, at, keyEnd);
            // Written twice, and then out of order. Every JSON reader downstream takes the last of
            // a repeated key, so a line carrying one twice means one thing to whoever reads it
            // first and another to everyone else -- and story 3.3 found a guard defeated by exactly
            // that. Order is the same argument one step further out: the writer sorts, so a line
            // that is not sorted is a line the writer did not produce.
            require(!members.containsKey(key),
                    "the key " + key + " written once and not twice, because a reader taking the"
                            + " other one would read something else", text);
            require(previous == null || key.compareTo(previous) > 0,
                    "keys in the order the writer sorts them, and " + key + " does not come after "
                            + previous, text);
            previous = key;
            require(keyEnd < text.length() && text.charAt(keyEnd) == ':', "a colon at " + keyEnd, text);
            int to = endOfValue(text, keyEnd + 1);
            members.put(key, text.substring(keyEnd + 1, to));
            at = to;
            if (at < text.length() - 1) {
                require(text.charAt(at) == ',', "a comma at " + at, text);
                at++;
                require(at < text.length() - 1, "a member after the comma at " + (at - 1), text);
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
                require(at < text.length() - 1, "an element after the comma at " + (at - 1), text);
            }
        }
        return List.copyOf(elements);
    }

    /**
     * The key between {@code at} and {@code keyEnd}, refusing one the writer would have written
     * plainly.
     *
     * <p>A key is held against its own literal text as well as its decoded value, because the
     * writer escapes nothing in a key and a checker written from the published rules strips keys by
     * matching their text. A key spelled with a unicode escape decodes to a name this reader would
     * strip from the chained text and that a stranger's script would keep — one file, two verdicts,
     * which is the one thing a published format may not have.
     */
    private static String key(String text, int at, int keyEnd) {
        String literal = text.substring(at + 1, keyEnd - 1);
        String key = string(text.substring(at, keyEnd));
        require(key.equals(literal),
                "a key written plainly, as the writer writes it, and not as " + literal, text);
        return key;
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
                    char decoded = (char) Integer.parseInt(hex, 16);
                    // The writer escapes an *unpaired* surrogate and writes a matched pair raw, so
                    // an escaped pair is a second spelling of a string the writer has one spelling
                    // for. It would verify here and re-render to other bytes, which is a log that
                    // can never be reproduced however faithfully it is replayed.
                    require(!Character.isHighSurrogate(decoded) || !lowFollows(raw, i + 5),
                            "a matched surrogate pair written raw, as the writer writes it", raw);
                    out.append(decoded);
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

    /** Whether an escaped low surrogate begins at {@code at}, which would complete a pair. */
    private static boolean lowFollows(String raw, int at) {
        if (at + 6 > raw.length() - 1 || raw.charAt(at) != '\\' || raw.charAt(at + 1) != 'u') {
            return false;
        }
        String hex = raw.substring(at + 2, at + 6);
        return hex.matches("[0-9a-f]{4}")
                && Character.isLowSurrogate((char) Integer.parseInt(hex, 16));
    }

    /** A whole number. The format writes no floats, so this refuses one rather than rounding it. */
    public static long number(String raw) {
        require(raw != null && raw.matches("-?\\d+"), "a whole number", raw);
        // The digits are counted before they are parsed, because Long.parseLong's own refusal is a
        // JDK message about a string, and a log's stated reason for being unreadable should be this
        // reader's own words about what it wanted.
        String digits = raw.charAt(0) == '-' ? raw.substring(1) : raw;
        require(digits.length() <= LONGEST_NUMBER, "a whole number that fits in a long", raw);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException tooLarge) {
            throw refuse("a whole number that fits in a long", raw);
        }
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

    /**
     * Where the value beginning at {@code at} ends.
     *
     * <p>Brackets are matched against what opened them rather than counted. Counting made
     * {@code {"a":[1}} a value, because a closing brace and a closing bracket were one token to it;
     * the object then ended before the loop noticed, and a hand-edited line chained over its own
     * malformed bytes and was declared intact — by this build alone, since every other JSON reader
     * in the world rejects it.
     */
    static int endOfValue(String text, int at) {
        require(at < text.length(), "a value at " + at, text);
        char first = text.charAt(at);
        if (first == '"') {
            return endOfString(text, at);
        }
        if (first == '{' || first == '[') {
            return endOfBrackets(text, at);
        }
        // A number, or true, or false: it runs to the next separator and may not be empty.
        int i = at;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ',' || c == '}' || c == ']') {
                break;
            }
            i++;
        }
        require(i > at, "a value after the colon, at " + at, text);
        return i;
    }

    private static int endOfBrackets(String text, int at) {
        Deque<Character> open = new ArrayDeque<>();
        int i = at;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '{' || c == '[') {
                open.push(c);
            } else if (c == '}' || c == ']') {
                require(!open.isEmpty(), "a " + c + " that closes something, at " + i, text);
                char opened = open.pop();
                require(c == '}' ? opened == '{' : opened == '[',
                        "a " + c + " that closes the " + opened + " it belongs to, at " + i, text);
                if (open.isEmpty()) {
                    return i + 1;
                }
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
        return new IllegalArgumentException("the Run log's own shape has " + expected + ", and this"
                + " is not it: " + shown(found));
    }

    /** At most two hundred characters of what was found, never cutting a surrogate pair in half. */
    private static String shown(String found) {
        if (found == null) {
            return "nothing";
        }
        if (found.length() <= 200) {
            return found;
        }
        int cut = Character.isHighSurrogate(found.charAt(199)) ? 199 : 200;
        return found.substring(0, cut) + "…";
    }
}
