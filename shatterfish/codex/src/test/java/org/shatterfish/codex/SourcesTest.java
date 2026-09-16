package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The source reader on fixtures (story 2.2): braces inside strings, characters and comments do
 * not count; a nested or anonymous type's lines are not the class's own; a method's body is not
 * a member line; a return split over lines is one statement and one after an {@code if} on the
 * same line is another; a chance is thousandths or refused; a roll is classified by its shape.
 */
class SourcesTest {

    private static Sources.Body body(String ownName, String... lines) {
        return new Sources.Body("core/src/main/java/X.java", List.of(lines), 0, lines.length, ownName);
    }

    @Test
    @DisplayName("a block ends at its own brace, whatever braces a string, a character or a comment holds")
    void a_block_ignores_literals_and_comments() {
        Sources.Body b = body("X",
                "public class X {",
                "    String s = \"{\"; // }",
                "    char c = '}';",
                "    /* { */",
                "    int f() {",
                "        return 1; // {",
                "    }",
                "}",
                "class After {",
                "}");
        Sources.Body block = b.block(0);
        assertEquals(8, block.to(), "the class's block closes at line 8");
        assertEquals(7, b.block(4).to(), "the method's block closes at line 7");
    }

    @Test
    @DisplayName("own lines skip a nested type and an anonymous class; member lines skip a method's body")
    void own_and_member_lines() {
        Sources.Body b = body("X",
                "public class X extends Y {",
                "    {",
                "        HP = HT = 8;",
                "        loot = Gold.class;",
                "    }",
                "    public static class Inner extends X {",
                "        public int damageRoll() {",
                "            return 99;",
                "        }",
                "    }",
                "    public int damageRoll() {",
                "        loot = null;",
                "        return Random.NormalIntRange(1, 4);",
                "    }",
                "    private final Object cb = new Runnable() {",
                "        public void run() { loot = null; }",
                "    };",
                "    public X() {",
                "        form = Random.Int(3);",
                "    }",
                "}");
        List<Integer> own = b.ownLines();
        assertTrue(own.contains(2) && own.contains(12) && !own.contains(7) && !own.contains(15), own.toString());
        List<Integer> members = b.memberLines();
        assertTrue(members.contains(3) && !members.contains(11) && !members.contains(12) && !members.contains(18), members.toString());
        assertEquals(10, b.find("public int damageRoll\\s*\\("), "the class's own damageRoll, not the nested class's");
        assertEquals(5, b.declaration("Inner"));
        assertEquals(List.of(18), b.constructorLines());
    }

    @Test
    @DisplayName("returns are whole statements: joined over lines, stripped of comments, counted after an if on one line")
    void returns_are_statements() {
        Sources.Body b = body("X",
                "public int damageRoll() {",
                "    if (angry) return Random.NormalIntRange(15, 40); // when raging",
                "    return buff(Rage.class) != null ?",
                "            Random.NormalIntRange(15, 40) :",
                "            Random.NormalIntRange(5, 25);",
                "}");
        List<String> returns = Sources.returns(b.block(0));
        assertEquals(2, returns.size(), returns.toString());
        assertEquals("return Random.NormalIntRange(15, 40);", returns.get(0));
        assertEquals("return buff(Rage.class) != null ? Random.NormalIntRange(15, 40) : Random.NormalIntRange(5, 25);", returns.get(1));
        Sources.Body unfinished = body("X", "public int f() {", "    return 1 +", "}");
        assertThrows(IllegalStateException.class, () -> Sources.returns(unfinished.block(0)));
    }

    @Test
    @DisplayName("a chance is thousandths, rounded half up; a fraction is exact; a nonzero chance that rounds to zero is refused; text is -1")
    void thousandths() {
        assertEquals(500, Sources.thousandths("0.5f"));
        assertEquals(125, Sources.thousandths("1/8f"));
        assertEquals(125, Sources.thousandths("1f/8"));
        assertEquals(167, Sources.thousandths("0.1667f"));
        assertEquals(29, Sources.thousandths("0.0285f"), "half up, not the float's 28.499");
        assertEquals(1000, Sources.thousandths("1"));
        assertEquals(0, Sources.thousandths("0"));
        assertEquals(-1, Sources.thousandths("1f/(6 * (generation+1) )"));
        assertEquals(-1, Sources.thousandths("altChance"));
        assertThrows(IllegalStateException.class, () -> Sources.thousandths("0.0001f"));
    }

    @Test
    @DisplayName("a roll is classified by its one statement's shape, or is OTHER with the text")
    void rolls_are_classified() {
        Codex.Citation at = new Codex.Citation("core/src/main/java/X.java", 1);
        Codex.Roll normal = Mobs.classify(List.of("return Random.NormalIntRange( 1, 4 );"), at);
        assertEquals(Codex.RollKind.NORMAL, normal.kind());
        assertEquals(1, normal.min());
        assertEquals(4, normal.max());
        Codex.Roll range = Mobs.classify(List.of("return Random.IntRange(2, 5);"), at);
        assertEquals(Codex.RollKind.UNIFORM, range.kind());
        assertEquals(5, range.max(), "IntRange is inclusive");
        Codex.Roll exclusive = Mobs.classify(List.of("return Random.Int(2, 5);"), at);
        assertEquals(Codex.RollKind.UNIFORM, exclusive.kind());
        assertEquals(4, exclusive.max(), "Int's max is exclusive and written inclusive");
        Codex.Roll constant = Mobs.classify(List.of("return 12;"), at);
        assertEquals(Codex.RollKind.CONSTANT, constant.kind());
        assertEquals(12, constant.min());
        Codex.Roll formula = Mobs.classify(List.of("return Random.NormalIntRange(1, 4) + 2;"), at);
        assertEquals(Codex.RollKind.OTHER, formula.kind());
        assertEquals(0, formula.max());
        Codex.Roll several = Mobs.classify(List.of("return 1;", "return 2;"), at);
        assertEquals(Codex.RollKind.OTHER, several.kind());
        assertEquals("return 1; | return 2;", several.expression());
        assertEquals("(no return)", Mobs.classify(List.of(), at).expression());
    }
}
