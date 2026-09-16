package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actions reader on synthetic text (story 2.3, after the review): the shapes the pinned
 * source writes and the shapes it could write, each read as a fresh instance would be offered
 * or refused by line.
 */
class ItemsReaderTest {

    private static final UnaryOperator<String> RESOLVE = name -> name.substring("AC_".length());

    private static Sources.Body method(String... lines) {
        List<String> all = List.of(lines);
        return new Sources.Body("core/src/main/java/X.java", all, 0, all.size(), "X");
    }

    @Test
    @DisplayName("an add at the body's own level counts; one under an if with braces, a braceless if on one line or two, a loop or a switch does not")
    void conditional_adds_are_not_offered() {
        Sources.Body body = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = super.actions(hero);",
                "        actions.add(AC_ONE);",
                "        if (isEquipped(hero)) {",
                "            actions.add(AC_TWO);",
                "        }",
                "        if (seal != null) actions.add(AC_THREE);",
                "        if (hero.buff(Blindness.class) == null)",
                "            actions.add(AC_FOUR);",
                "        for (Item i : hero.belongings) { actions.add(AC_FIVE); }",
                "        switch (level()) { case 1: actions.add(AC_SIX); break; default: break; }",
                "        actions.add( AC_SEVEN );",
                "        return actions;",
                "    }");
        assertEquals(List.of("DROP", "THROW", "ONE", "SEVEN"), Items.readActions(body, List.of("DROP", "THROW"), RESOLVE));
    }

    @Test
    @DisplayName("a remove, a clear, an add with a space before the parenthesis, an Allman brace and a brace in a literal are read")
    void removes_clears_and_braces() {
        Sources.Body body = method(
                "    public ArrayList<String> actions(Hero hero)",
                "    {",
                "        ArrayList<String> actions = super.actions( hero );",
                "        actions.remove( AC_THROW );",
                "        actions.add ( AC_LIGHTTHROW );",
                "        String brace = \"{\"; char open = '{';",
                "        /* { */ actions.add(AC_EIGHT);",
                "        return actions;",
                "    }");
        assertEquals(List.of("DROP", "LIGHTTHROW", "EIGHT"), Items.readActions(body, List.of("DROP", "THROW"), RESOLVE));
        Sources.Body cleared = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = super.actions(hero);",
                "        actions.clear();",
                "        actions.add(AC_ONLY);",
                "        return actions;",
                "    }");
        assertEquals(List.of("ONLY"), Items.readActions(cleared, List.of("DROP", "THROW"), RESOLVE));
        Sources.Body fresh = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = new ArrayList<>();",
                "        actions.add(AC_DROP);",
                "        return actions;",
                "    }");
        assertEquals(List.of("DROP"), Items.readActions(fresh, List.of(), RESOLVE), "the base declares its own list");
    }

    @Test
    @DisplayName("a ternary on being equipped takes the unequipped branch, whichever side it is on")
    void the_ternary_takes_the_unequipped_branch() {
        Sources.Body plain = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = super.actions( hero );",
                "        actions.add( isEquipped( hero ) ? AC_UNEQUIP : AC_EQUIP );",
                "        return actions;",
                "    }");
        assertEquals(List.of("EQUIP"), Items.readActions(plain, List.of(), RESOLVE));
        Sources.Body reversed = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = super.actions( hero );",
                "        actions.add( !isEquipped(hero) ? AC_EQUIP : AC_UNEQUIP );",
                "        return actions;",
                "    }");
        assertEquals(List.of("EQUIP"), Items.readActions(reversed, List.of(), RESOLVE));
    }

    @Test
    @DisplayName("a shape the reader does not know fails naming the line: another condition's ternary, a nested ternary, an addAll, a reassignment, a change under a condition, a super call it cannot see")
    void unknown_shapes_fail() {
        String[][] bodies = {
                {"        actions.add( hero.isAlive() ? AC_A : AC_B );"},
                {"        actions.add( isEquipped(hero) ? (cursed ? AC_A : AC_B) : AC_C );"},
                {"        actions.addAll(List.of(AC_A));"},
                {"        actions = new ArrayList<>();"},
                {"        if (cursed) actions.addAll(others);"},
                {"        actions.removeIf(a -> true);"},
        };
        for (String[] middle : bodies) {
            Sources.Body body = method(
                    "    public ArrayList<String> actions(Hero hero) {",
                    "        ArrayList<String> actions = super.actions(hero);",
                    middle[0],
                    "        return actions;",
                    "    }");
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Items.readActions(body, List.of("DROP"), RESOLVE), middle[0]);
            assertTrue(failure.getMessage().contains("X.java:3"), failure.getMessage());
        }
        Sources.Body hidden = method(
                "    public ArrayList<String> actions(Hero hero) {",
                "        ArrayList<String> actions = new ArrayList<>();",
                "        if (cursed) { actions = super.actions(hero); }",
                "        return actions;",
                "    }");
        assertThrows(IllegalStateException.class, () -> Items.readActions(hidden, List.of("DROP"), RESOLVE), "super under an if");
    }

    @Test
    @DisplayName("braces are counted outside literals and comments")
    void braces_outside_literals() {
        assertEquals(1, Sources.braces("if (x) {"));
        assertEquals(0, Sources.braces("String s = \"{{\"; // {"));
        assertEquals(-1, Sources.braces("} /* { */ // }"));
        assertEquals(0, Sources.braces("char c = '{'; char d = '\\\\'; char e = '\\'';"));
        assertEquals(1, Sources.braces("{ /* unclosed"));
    }
}
