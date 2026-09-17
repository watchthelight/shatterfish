package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusals story 2.6's readers carry that the pinned tree cannot reach, held against source
 * written here instead. A refusal nobody can trigger is a claim nobody checks: the reader would be
 * free to lose it, and the shape it guards against would then be read as something else. This is
 * the same reason story 2.4 holds the rooms reader's refusal against a room the game does not have.
 */
class ReadersRefuseTest {

    /** A body of made-up source, as the readers see a file. */
    private static Sources.Body body(String... lines) {
        List<String> all = List.of(lines);
        return new Sources.Body("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Made.java", all, 0, all.size(), "Made");
    }

    @Test
    @DisplayName("an array literal is closed by its matching brace, so a literal inside one does not truncate the pool")
    void a_literal_closes_where_it_closes() {
        assertEquals(List.of("A.class, B.class"), Traps.literals("return new Class<?>[]{A.class, B.class};", "new Class"));
        assertEquals(List.of("new int[]{1, 2}, C.class"),
                Traps.literals("return new Class<?>[]{new int[]{1, 2}, C.class};", "new Class<?>"),
                "a brace inside a literal does not end it");
        assertEquals(List.of("A.class", "B.class"),
                Traps.literals("return x ? new Class<?>[]{A.class} : new Class<?>[]{B.class};", "new Class"),
                "two arms are two literals");
        assertThrows(IllegalStateException.class, () -> Traps.literals("return new Class<?>[]{A.class;", "new Class"),
                "a literal that does not close");
        assertThrows(IllegalStateException.class, () -> Traps.literals("return null;", "new Class"),
                "a method with no literal at all");
    }

    @Test
    @DisplayName("the condition of a two-armed method is the one before its literal, not the question mark of its own type")
    void a_condition_is_read_before_the_literal() {
        assertEquals("", Traps.condition("return new Class<?>[]{A.class};", "new Class"),
                "a method with one arm states no condition, and its own Class<?> is not one");
        assertEquals("Dungeon.depth == 1",
                Traps.condition("return Dungeon.depth == 1 ? new Class<?>[]{A.class} : new Class<?>[]{B.class};", "new Class"));
        assertEquals("x > 2", Traps.condition("return x > 2 ? new float[]{1} : new float[]{2};", "new float"));
    }

    @Test
    @DisplayName("the feelings refuse an enum the reader cannot read, an arm that sets one twice, and a feeling no arm sets")
    void the_feelings_refuse_what_they_cannot_read() {
        assertThrows(IllegalStateException.class, () -> Structure.feelings(null, body(
                "class Made {",
                "\tpublic enum Feeling {",
                "\t\tNONE, CHASM;",
                "\t}",
                "}")), "the feelings no longer read one plain name to a line");
        assertThrows(IllegalStateException.class, () -> Structure.feelings(null, body(
                "class Made {",
                "\tpublic enum Feeling {",
                "\t\tNONE,",
                "\t\tCHASM;",
                "\t}",
                "\tpublic void create() {",
                "\t\tif (!Dungeon.bossLevel() && Dungeon.branch == 0) {",
                "\t\t\tif (Dungeon.depth > 1) {",
                "\t\t\t\tswitch (Random.Int( 2 )) {",
                "\t\t\t\t\tcase 0:",
                "\t\t\t\t\t\tfeeling = Feeling.CHASM;",
                "\t\t\t\t\t\tbreak;",
                "\t\t\t\t\tcase 1:",
                "\t\t\t\t\t\tfeeling = Feeling.CHASM;",
                "\t\t\t\t\t\tbreak;",
                "\t\t\t\t}",
                "\t\t\t}",
                "\t\t}",
                "\t}",
                "}")), "a feeling set in two arms has no one chance");
        assertThrows(IllegalStateException.class, () -> Structure.feelings(null, body(
                "class Made {",
                "\tpublic enum Feeling {",
                "\t\tNONE,",
                "\t\tCHASM;",
                "\t}",
                "\tpublic void create() {",
                "\t\tif (!Dungeon.bossLevel() && Dungeon.branch == 0) {",
                "\t\t\tif (Dungeon.depth > 1) {",
                "\t\t\t\tswitch (Random.Int( 2 )) {",
                "\t\t\t\t\tcase 0:",
                "\t\t\t\t\t\tfeeling = Feeling.NONE;",
                "\t\t\t\t\t\tbreak;",
                "\t\t\t\t}",
                "\t\t\t}",
                "\t\t}",
                "\t}",
                "}")), "a feeling no arm of the roll sets");
        assertThrows(IllegalStateException.class, () -> Structure.feelings(null, body(
                "class Made {",
                "\tpublic enum Feeling {",
                "\t\tNONE;",
                "\t}",
                "\tpublic void create() {",
                "\t\tif (!Dungeon.bossLevel() && Dungeon.branch == 0) {",
                "\t\t\tif (Dungeon.depth > 1) {",
                "\t\t\t\tfeeling = Feeling.NONE;",
                "\t\t\t}",
                "\t\t}",
                "\t}",
                "}")), "a creation that no longer rolls at all");
    }

    @Test
    @DisplayName("a commented-out statement is not read as a live one")
    void a_comment_is_not_a_statement() {
        Sources.Body made = body(
                "class Made {",
                "\t/* seal();",
                "\t*/",
                "\tvoid go() {",
                "\t\tint x = 1; // seal();",
                "\t}",
                "}");
        List<String> stripped = Sources.stripped(made);
        assertTrue(stripped.stream().noneMatch(line -> line.contains("seal();")),
                "neither a block comment nor a trailing one leaves a statement behind: " + stripped);
    }
}
