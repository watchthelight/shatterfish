package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader that hands a table's own bytes back to the page that indexes it (story 2.9).
 *
 * <p>It is fed nothing but the generator's own output, so every refusal it carries is a shape the
 * pinned trees cannot reach — and a refusal nothing exercises is a refusal nobody has checked.
 * Story 2.8 recorded the same lesson: a rule the pinned tree cannot distinguish needs a seam so a
 * test can state it. The seam here is that {@link Pages#parse} takes a string.
 */
class PagesReaderTest {

    @Test
    @DisplayName("the shapes a table is written in are read back as they were written")
    void the_generator_s_own_shapes_round_trip() {
        assertEquals(List.of(), Pages.parse("[]"));
        assertEquals(Map.of(), Pages.parse("{}"));
        assertEquals(List.of(1L, 2L), Pages.parse("[1,2]"));
        assertEquals(-7L, Pages.parse("-7"));
        assertEquals(true, Pages.parse("true"));
        assertNull(Pages.parse("null"));
        assertEquals("a \" b \\ c \n d", Pages.parse("\"a \\\" b \\\\ c \\n d\""));
        assertEquals("\u00e9", Pages.parse("\"\\u00e9\""), "an escape the bundles write");
        Map<?, ?> object = assertInstanceOf(Map.class, Pages.parse("{\"b\":null,\"a\":[{\"x\":1}]}"));
        assertEquals(List.of("b", "a"), List.copyOf(object.keySet()), "the order the bytes were written in");
        assertNull(object.get("b"), "a null a table writes is a value, not a missing key");
    }

    @Test
    @DisplayName("a number that is not an integer is refused, since no table writes one and a page may not round it")
    void a_float_is_refused() {
        assertRefused("1.5", "no floats");
        assertRefused("[1e3]", "no floats");
    }

    @Test
    @DisplayName("a key written twice is refused, whatever the first value was")
    void a_key_written_twice_is_refused() {
        assertRefused("{\"a\":1,\"a\":2}", "written twice");
        // The check read what `put` returned, so a first value of null looked like no key at all
        // and the second quietly won. The tables do write nulls.
        assertRefused("{\"a\":null,\"a\":2}", "written twice");
    }

    @Test
    @DisplayName("text that is not the whole of a value, or not a value at all, is refused")
    void a_shape_the_generator_does_not_write_is_refused() {
        assertRefused("", "the JSON ends");
        assertRefused("nul", "a value was expected");
        assertRefused("[1,2] and more", "text after the JSON value");
        assertRefused("[1,2", "the JSON ends");
        assertRefused("[1 2]", "a comma or a bracket was expected");
        assertRefused("{\"a\" 1}", "expected");
        assertRefused("\"unterminated", "the JSON ends");
        assertRefused("\"\\q\"", "an escape the Codex does not write");
    }

    /** {@code text} is refused, and the message says enough to find what was wrong with it. */
    private static void assertRefused(String text, String says) {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Pages.parse(text),
                "the reader takes only what the generator writes: " + text);
        assertTrue(refused.getMessage().contains(says),
                "the refusal of " + text + " says " + says + ": " + refused.getMessage());
    }
}
