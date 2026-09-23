package org.shatterfish.harness.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grammar reader, held against the shape it says it reads (story 3.4).
 *
 * <p>It had no test. It was exercised only through whole logs the canonical writer had just
 * produced — every line sorted, escape-free, duplicate-free and bracket-balanced — so the entire
 * space in which a reader can be wrong was never entered, and the class's own javadoc promised four
 * properties of which one was implemented. A reader whose job is to refuse needs tests that hand it
 * things to refuse.
 *
 * <p>Every line below is one a hand editor or a different writer would plausibly produce. None of
 * them can come out of {@code RunLogJson}, which is the point: this is the boundary between "a file
 * this project wrote" and "a file somebody made", and the format exists to put that boundary
 * somewhere a reader can stand.
 */
class JsonTest {

    private static final String BACKSLASH = String.valueOf((char) 92);

    // ------------------------------------------------------------------------------ what it reads

    @Test
    @DisplayName("the shape the writer writes reads back, member by member")
    void the_canonical_shape_reads() {
        Map<String, String> held = Json.object("{\"a\":1,\"b\":\"two\",\"c\":[3,4],\"d\":{\"e\":false}}");

        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(held.keySet()));
        assertEquals(1L, Json.number(held.get("a")));
        assertEquals("two", Json.string(held.get("b")));
        assertEquals(List.of("3", "4"), Json.array(held.get("c")));
        assertEquals(false, Json.bool(Json.object(held.get("d")).get("e")));
    }

    @Test
    @DisplayName("an empty object and an empty array are both values")
    void the_empty_shapes_read() {
        assertEquals(Map.of(), Json.object("{}"));
        assertEquals(List.of(), Json.array("[]"));
        assertEquals("[]", Json.object("{\"a\":[]}").get("a"));
    }

    @Test
    @DisplayName("the escape table reads exactly the escapes the writer produces")
    void the_escapes_read() {
        assertEquals("\"", Json.string("\"" + BACKSLASH + "\"\""));
        assertEquals("\\", Json.string("\"" + BACKSLASH + BACKSLASH + "\""));
        assertEquals("\b\f\n\r\t", Json.string("\"" + BACKSLASH + "b" + BACKSLASH + "f"
                + BACKSLASH + "n" + BACKSLASH + "r" + BACKSLASH + "t\""));
        assertEquals("", Json.string("\"" + BACKSLASH + "u0001\""));
        // A matched pair, raw, as the writer writes it.
        assertEquals("😀", Json.string("\"😀\""));
        // And a lone high surrogate, escaped, as the writer writes that.
        assertEquals("\uD83D", Json.string("\"" + BACKSLASH + "ud83d\""));
    }

    // -------------------------------------------------------------------------- what it refuses

    @Test
    @DisplayName("a line whose keys are not in the order the writer sorts them is refused")
    void unsorted_keys_are_refused() {
        // The javadoc promised this from the first draft and the code did not do it, so a
        // hand-made line chained to its own unsorted text and read as intact.
        refuses("{\"b\":1,\"a\":2}", "in the order the writer sorts them");
        refuses("{\"t\":\"wait\",\"k\":1}", "in the order the writer sorts them");
    }

    @Test
    @DisplayName("a key written twice is refused, because a reader taking the other one reads something else")
    void a_repeated_key_is_refused() {
        refuses("{\"a\":1,\"a\":2}", "twice");
    }

    @Test
    @DisplayName("a key spelled with an escape is refused, because a checker matching text would keep it")
    void an_escaped_key_is_refused() {
        // `\\u0070rev` decodes to `prev`. A decoding checker strips it from the chained text and a
        // checker written from the published rules keeps it: one file, two verdicts, which is the
        // one thing a published format may not have.
        refuses("{\"" + BACKSLASH + "u0070rev\":\"x\"}", "written plainly");
    }

    @Test
    @DisplayName("a bracket that closes something else is refused, not counted")
    void mismatched_brackets_are_refused() {
        // Counting depth made this a value: a closing brace and a closing bracket were one token,
        // so `{"a":[1}` parsed with the value `[1}` and the object ended before anyone noticed.
        refuses("{\"a\":[1}", "closes the");
        refuses("{\"a\":{\"b\":1]}", "closes the");
        assertThrows(IllegalArgumentException.class, () -> Json.array("[{\"a\":1]"));
    }

    @Test
    @DisplayName("a member with no value, and a comma with no member after it, are refused")
    void empty_members_are_refused() {
        refuses("{\"a\":,\"b\":1}", "a value after the colon");
        refuses("{\"a\":1,}", "a member after the comma");
        assertThrows(IllegalArgumentException.class, () -> Json.array("[1,]"));
    }

    @Test
    @DisplayName("an escape the writer does not produce is refused, including a legal one")
    void a_foreign_escape_is_refused() {
        // In the value, which means at the moment a value is read as a string rather than when the
        // line is split into members: `object` hands back raw text on purpose, so a key is checked
        // where it is read and a value where it is decoded. Everything that turns a log line into a
        // record decodes every string in it, which is where this bites.
        refusesAsString("\"" + BACKSLASH + "/\"", "an escape the writer produces");
        refusesAsString("\"" + BACKSLASH + "u00FF\"", "lower-case hex");
        // A matched pair written as two escapes is legal JSON and a second spelling of a string the
        // writer has one spelling for: it would verify and then re-render to other bytes.
        refusesAsString("\"" + BACKSLASH + "ud83d" + BACKSLASH + "ude00\"", "written raw");
        refusesAsString("\"" + BACKSLASH + "\"", "an escape with something after it");
    }

    @Test
    @DisplayName("a number no long holds is refused in this reader's own words")
    void a_number_too_large_is_refused() {
        assertEquals(Long.MAX_VALUE, Json.number("9223372036854775807"));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Json.number("9223372036854775808"));
        assertTrue(refused.getMessage().contains("fits in a long"), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Json.number("1.5"), "no floats");
        assertThrows(IllegalArgumentException.class, () -> Json.number("1e3"), "no exponents");
        assertThrows(IllegalArgumentException.class, () -> Json.integer("2147483648"));
    }

    @Test
    @DisplayName("what is not an object at all is refused before anything is read out of it")
    void the_shapes_that_are_not_objects_are_refused() {
        for (String line : List.of("", "{", "}", "[]", "null", "{\"a\"}", "{a:1}", "{\"a\" :1}")) {
            assertThrows(IllegalArgumentException.class, () -> Json.object(line),
                    "this is not a log line: " + line);
        }
    }

    @Test
    @DisplayName("a byte order mark makes a line something this reader will not read")
    void a_byte_order_mark_is_refused() {
        // It is invisible, it is what a text editor adds, and the format says UTF-8 without one.
        // What matters most is that the Rig's oracle guard does not read past it as though the
        // line were fine -- which is `LogHeader`'s problem, and this is why it has one.
        assertThrows(IllegalArgumentException.class, () -> Json.object("﻿{\"a\":1}"));
    }

    @Test
    @DisplayName("what it refuses, it names, and it does not print a whole log to do it")
    void a_refusal_names_what_it_wanted() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Json.object("{\"b\":1,\"a\":" + "9".repeat(400) + "}"));
        assertTrue(refused.getMessage().contains("the Run log's own shape has"), refused.getMessage());
        assertTrue(refused.getMessage().length() < 400, "the whole line is not the message");
        assertTrue(refused.getMessage().endsWith("…"), refused.getMessage());
    }

    @Test
    @DisplayName("a required member that is absent is named, rather than read as a default")
    void a_missing_member_is_named() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Json.required(Json.object("{\"a\":1}"), "b", "wait"));
        assertTrue(refused.getMessage().contains("a wait with a b"), refused.getMessage());
    }

    private static void refusesAsString(String raw, String saying) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Json.string(raw), "this value should be refused: " + raw);
        assertTrue(refused.getMessage().contains(saying),
                "the refusal of " + raw + " should say " + saying + ", and says: "
                        + refused.getMessage());
    }

    private static void refuses(String line, String saying) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Json.object(line), "this line should be refused: " + line);
        assertTrue(refused.getMessage().contains(saying),
                "the refusal of " + line + " should say " + saying + ", and says: "
                        + refused.getMessage());
    }
}
