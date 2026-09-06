package org.shatterfish.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A writer of canonical JSON for the Run log and the readable form of an Observation (ADR-0005,
 * option 10; ADR-0011): an object's keys come out sorted by their UTF-16 code units whatever order
 * they were given in, there is no whitespace, numbers are integers, and a string is quoted with
 * only the escapes JSON requires, so that two writers of the same values produce the same text.
 * It writes; nothing in {@code api} reads JSON back.
 *
 * <p>Usage: {@code beginObject()}, then {@code key(name)} before each {@code value(...)},
 * {@code beginObject()} or {@code beginArray()}, then {@code endObject()}; {@link #toJson()} once
 * the one root value is complete.
 */
public final class JsonWriter {

    private static final String HEX = "0123456789abcdef";

    private final Deque<Frame> frames = new ArrayDeque<>();
    private String root;

    public JsonWriter beginObject() {
        frames.push(new ObjectFrame());
        return this;
    }

    public JsonWriter endObject() {
        Frame frame = frames.poll();
        Canon.require(frame instanceof ObjectFrame, "no object is open");
        emit(frame.render());
        return this;
    }

    public JsonWriter beginArray() {
        frames.push(new ArrayFrame());
        return this;
    }

    public JsonWriter endArray() {
        Frame frame = frames.poll();
        Canon.require(frame instanceof ArrayFrame, "no array is open");
        emit(frame.render());
        return this;
    }

    /** The key of the next value in the open object. */
    public JsonWriter key(String key) {
        Canon.text(key, "key");
        Frame frame = frames.peek();
        Canon.require(frame instanceof ObjectFrame, "a key belongs in an object");
        ((ObjectFrame) frame).key(key);
        return this;
    }

    public JsonWriter value(String value) {
        emit(quote(Canon.text(value, "value")));
        return this;
    }

    public JsonWriter value(long value) {
        emit(Long.toString(value));
        return this;
    }

    public JsonWriter value(int value) {
        return value((long) value);
    }

    public JsonWriter value(boolean value) {
        emit(value ? "true" : "false");
        return this;
    }

    /** The text, once the root value is complete. */
    public String toJson() {
        Canon.require(frames.isEmpty(), "an object or array is still open");
        Canon.require(root != null, "nothing was written");
        return root;
    }

    /** {@code value} quoted, with the escapes JSON requires and no others. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xF));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private void emit(String rendered) {
        Frame frame = frames.peek();
        if (frame == null) {
            Canon.require(root == null, "the root value is already complete");
            root = rendered;
        } else {
            frame.add(rendered);
        }
    }

    private abstract static class Frame {
        abstract void add(String rendered);

        abstract String render();
    }

    private static final class ObjectFrame extends Frame {
        private final Map<String, String> members = new TreeMap<>();
        private String pending;

        void key(String key) {
            Canon.require(pending == null, "key " + pending + " has no value yet");
            Canon.require(!members.containsKey(key), "key " + key + " is already written");
            pending = key;
        }

        @Override
        void add(String rendered) {
            Canon.require(pending != null, "a value in an object needs a key first");
            members.put(pending, rendered);
            pending = null;
        }

        @Override
        String render() {
            Canon.require(pending == null, "key " + pending + " has no value");
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> member : members.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(member.getKey())).append(':').append(member.getValue());
            }
            return out.append('}').toString();
        }
    }

    private static final class ArrayFrame extends Frame {
        private final List<String> elements = new ArrayList<>();

        @Override
        void add(String rendered) {
            elements.add(rendered);
        }

        @Override
        String render() {
            return "[" + String.join(",", elements) + "]";
        }
    }
}
