package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Codex;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room reader on synthetic text (story 2.4, after the review): what a room puts on the floor
 * and what it draws, in the shapes the pinned source writes and the shapes it could write. The
 * pinned tree has no room that adds to the spawn list under a condition, so the refusal that
 * guards the guaranteed counts is held here rather than by the tree.
 */
class RoomsReaderTest {

    private static final String PATH = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/XRoom.java";

    /** A room's file: the imports the reader resolves through, the class, and the given body of {@code paint}. */
    private static Sources.Body room(String... paint) {
        List<String> lines = new ArrayList<>(List.of(
                "package com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special;",
                "",
                "import com.shatteredpixel.shatteredpixeldungeon.items.Honeypot;",
                "import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;",
                "import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;",
                "import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfFrost;",
                "",
                "public class XRoom extends SpecialRoom {",
                "",
                "\tpublic void paint( Level level ) {"));
        lines.addAll(List.of(paint));
        lines.addAll(List.of("\t}", "}"));
        return new Sources.Body(PATH, List.copyOf(lines), 0, lines.size(), "XRoom");
    }

    private static Codex.RoomEntry entry(Sources.Body body) {
        return Rooms.entry(body, "levels.rooms.special.XRoom", "XRoom", false);
    }

    @Test
    @DisplayName("an item added to the spawn list at the painting's own level is counted, once per statement, and resolved through the file's imports")
    void spawn_list_items_are_counted() {
        Codex.RoomEntry entry = entry(room(
                "\t\tlevel.addItemToSpawn( new IronKey( Dungeon.depth ) );",
                "\t\tlevel.addItemToSpawn(new PotionOfFrost());",
                "\t\tlevel.addItemToSpawn(new PotionOfFrost());"));
        assertEquals(List.of("items.keys.IronKey", "items.potions.PotionOfFrost"), entry.spawns().stream().map(Codex.Spawn::className).toList());
        assertEquals(1, entry.spawns().get(0).count());
        assertEquals(2, entry.spawns().get(1).count());
        assertTrue(entry.spawns().stream().noneMatch(Codex.Spawn::floorDrop));
        assertTrue(entry.spawns().stream().noneMatch(Codex.Spawn::conditional));
        assertEquals(11, entry.spawns().get(0).citation().line(), "the first line that adds it");
    }

    @Test
    @DisplayName("an item dropped on the room's own cells is marked, and one dropped under a condition or in a loop is marked conditional")
    void floor_drops_are_marked() {
        Codex.RoomEntry entry = entry(room(
                "\t\tlevel.drop( new IronKey( Dungeon.depth ), pos );",
                "\t\tif (Random.Int(2) == 0) {",
                "\t\t\tlevel.drop( new Honeypot(), pos);",
                "\t\t}",
                "\t\tfor (int i = 0; i < 3; i++) {",
                "\t\t\tlevel.drop(new Bomb.DoubleBomb(), pos);",
                "\t\t}"));
        assertEquals(List.of("items.Honeypot", "items.bombs.Bomb.DoubleBomb", "items.keys.IronKey"),
                entry.spawns().stream().map(Codex.Spawn::className).toList());
        assertTrue(entry.spawns().stream().allMatch(Codex.Spawn::floorDrop));
        assertTrue(entry.spawns().get(0).conditional(), "a drop under an if is conditional");
        assertTrue(entry.spawns().get(1).conditional(), "a drop in a loop is conditional");
        assertTrue(!entry.spawns().get(2).conditional(), "a drop at the painting's own level is not");
    }

    @Test
    @DisplayName("an item added to the spawn list under a condition, in another method, or by a shape the reader cannot name fails naming the line")
    void unreadable_shapes_fail() {
        Sources.Body conditional = room(
                "\t\tif (Random.Int(2) == 0) {",
                "\t\t\tlevel.addItemToSpawn(new PotionOfFrost());",
                "\t\t}");
        IllegalStateException under = assertThrows(IllegalStateException.class, () -> entry(conditional));
        assertTrue(under.getMessage().contains("XRoom.java:12") && under.getMessage().contains("spawn list"), under.getMessage());
        Sources.Body braceless = room(
                "\t\tif (Random.Int(2) == 0)",
                "\t\t\tlevel.addItemToSpawn(new PotionOfFrost());");
        assertThrows(IllegalStateException.class, () -> entry(braceless), "a braceless if governs the next statement");
        Sources.Body named = room("\t\tlevel.addItemToSpawn(item);");
        IllegalStateException shape = assertThrows(IllegalStateException.class, () -> entry(named));
        assertTrue(shape.getMessage().contains("shape the reader does not know"), shape.getMessage());
        Sources.Body two = room("\t\tlevel.addItemToSpawn(new PotionOfFrost()); level.addItemToSpawn(new IronKey(1));");
        IllegalStateException both = assertThrows(IllegalStateException.class, () -> entry(two));
        assertTrue(both.getMessage().contains("two items"), both.getMessage());
        List<String> helper = new ArrayList<>(List.of(
                "package com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special;",
                "import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;",
                "public class XRoom extends SpecialRoom {",
                "\tpublic void paint( Level level ) {",
                "\t\tkeys(level);",
                "\t}",
                "\tprivate void keys( Level level ) {",
                "\t\tlevel.addItemToSpawn(new IronKey(1));",
                "\t}",
                "}"));
        Sources.Body elsewhere = new Sources.Body(PATH, List.copyOf(helper), 0, helper.size(), "XRoom");
        IllegalStateException other = assertThrows(IllegalStateException.class,
                () -> Rooms.entry(elsewhere, "levels.rooms.special.XRoom", "XRoom", false));
        assertTrue(other.getMessage().contains("outside paint's own level"), other.getMessage());
        Sources.Body unimported = room("\t\tlevel.addItemToSpawn(new Ankh());");
        assertThrows(IllegalStateException.class, () -> entry(unimported), "a class the file does not import");
    }

    @Test
    @DisplayName("a statement spanning lines is read whole, and every draw is carried as its cited text")
    void statements_and_draws_are_whole() {
        Codex.RoomEntry entry = entry(room(
                "\t\tlevel.addItemToSpawn(",
                "\t\t\t\tnew PotionOfFrost());",
                "\t\tItem prize = Generator.random( Random.oneOf(",
                "\t\t\t\tGenerator.Category.POTION,",
                "\t\t\t\tGenerator.Category.SCROLL));",
                "\t\tClass<? extends Scroll> cls = Random.chances(chances);",
                "\t\tlevel.drop( Reflection.newInstance(cls), pos );"));
        assertEquals(List.of("items.potions.PotionOfFrost"), entry.spawns().stream().map(Codex.Spawn::className).toList());
        assertEquals(3, entry.draws().size());
        assertEquals("Item prize = Generator.random( Random.oneOf( Generator.Category.POTION, Generator.Category.SCROLL));",
                entry.draws().get(0).expression(), "a draw's arguments on later lines are part of it");
        assertTrue(entry.draws().get(1).expression().contains("Random.chances(chances)"));
        assertTrue(entry.draws().get(2).expression().contains("Reflection.newInstance(cls)"));
    }

    @Test
    @DisplayName("a room list is read between its own parentheses, and a class mentioned after them is not a member")
    void a_list_is_its_own_parentheses() {
        List<String> lines = List.of(
                "public abstract class SpecialRoom extends Room {",
                "\tprivate static final ArrayList<Class<? extends SpecialRoom>> EQUIP_SPECIALS = new ArrayList<>( Arrays.asList(",
                "\t\t\tWeakFloorRoom.class, CryptRoom.class",
                "\t) );",
                "\tprivate static final ArrayList<Class<? extends SpecialRoom>> OTHER = new ArrayList<>( Arrays.asList(",
                "\t\t\tPitRoom.class) ); // PoolRoom.class is not one",
                "}");
        Sources.Body body = new Sources.Body("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SpecialRoom.java",
                lines, 0, lines.size(), "SpecialRoom");
        java.util.Map<String, Class<?>> byName = new java.util.TreeMap<>();
        for (Class<?> type : Rooms.SPECIALS) {
            byName.put(type.getSimpleName(), type);
        }
        Codex.RoomList equip = Rooms.list(body, "EQUIP_SPECIALS", byName);
        assertEquals(List.of("levels.rooms.special.WeakFloorRoom", "levels.rooms.special.CryptRoom"), equip.members());
        assertEquals(2, equip.citation().line());
        Codex.RoomList other = Rooms.list(body, "OTHER", byName);
        assertEquals(List.of("levels.rooms.special.PitRoom"), other.members(), "the comment after the literal names no member");
    }
}
