package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name reader derives the key as the game does, reads the one bundle line, falls back to the
 * superclass as the game does, and fails for a class no bundle names (story 2.3).
 */
class NamesTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;

    @Test
    @DisplayName("the key is the class name without the root package, lower-cased, a nested class's dollar kept")
    void the_key_is_the_games() {
        assertEquals("items.wands.wandofregrowth$dewcatcher$seed.name", Names.key(WandOfRegrowth.Dewcatcher.Seed.class, "name"));
        assertEquals("plants.sungrass$seed.name", Names.key(Sungrass.Seed.class, "name"));
        assertEquals("items.item.desc", Names.key(Item.class, "desc"));
        assertEquals("kaunan", Names.lower("KAUNAN"));
        assertThrows(IllegalStateException.class, () -> Names.lower("\u0130"), "a dotted capital I is not lowered by the machine's locale");
    }

    @Test
    @DisplayName("a name is the bundle's line, cited; a nested class without its own line takes its superclass's, as the game does")
    void names_are_read_and_fall_back() {
        Names.Named petal = Names.of(ROOT, DriedRose.Petal.class);
        assertEquals("dried petal", petal.value());
        assertEquals("core/src/main/assets/messages/items/items.properties", petal.citation().path());
        assertEquals(226, petal.citation().line());
        Names.Named seed = Names.of(ROOT, Sungrass.Seed.class);
        assertEquals("seed of sungrass", seed.value());
        assertEquals("core/src/main/assets/messages/plants/plants.properties", seed.citation().path());
        assertEquals(71, seed.citation().line());
        assertNull(Names.find(ROOT, Names.key(Bomb.ConjuredBomb.class, "name")), "the conjured bomb has no line of its own");
        assertEquals(Names.of(ROOT, Bomb.class), Names.of(ROOT, Bomb.ConjuredBomb.class), "so it is named as a bomb, from the bomb's line");
    }

    @Test
    @DisplayName("a key no bundle holds fails naming the key, and a class no bundle names at any level fails naming the class")
    void a_missing_name_fails() {
        IllegalStateException key = assertThrows(IllegalStateException.class, () -> Names.lookup(ROOT, "items.item.no_such_key"));
        assertTrue(key.getMessage().contains("items.item.no_such_key"), key.getMessage());
        IllegalStateException type = assertThrows(IllegalStateException.class, () -> Names.of(ROOT, Item.class));
        assertTrue(type.getMessage().contains(Item.class.getName()), type.getMessage());
        assertThrows(java.io.UncheckedIOException.class, () -> Names.lookup(ROOT, "nosuchbundle.x.name"), "a bundle that is not there");
    }

    @Test
    @DisplayName("a display name is joined on trimmed, its whitespace collapsed, and lower-cased, and is refused where it is not ASCII")
    void a_display_name_is_normalised_before_it_is_joined_on() {
        // The vocabulary diff (story 2.8) joins the two games on this, and neither pinned tree
        // spells a name in a way that exercises the rule: no display name in either game carries
        // a stray space. So the rule is stated here rather than left to a tree to demonstrate --
        // an upstream tag that adds one would otherwise split a row in two, silently, and the
        // table would report a name as belonging to one game alone.
        assertEquals("potion of healing", Names.display("Potion of Healing"));
        assertEquals("potion of healing", Names.display("  Potion of Healing  "), "trimmed");
        assertEquals("gnoll scout", Names.display("gnoll  scout"), "and its inner whitespace collapsed");
        assertEquals("gnoll scout", Names.display("gnoll\tscout"), "whatever the whitespace is");
        assertEquals("spawn of goo", Names.display("spawn of Goo"));
        IllegalStateException outside = assertThrows(IllegalStateException.class, () -> Names.display("café au lait"));
        assertTrue(outside.getMessage().contains("display name"), outside.getMessage());
        assertTrue(outside.getMessage().contains("café au lait"), "and names the name it refused: " + outside.getMessage());
        IllegalStateException blank = assertThrows(IllegalStateException.class, () -> Names.display("   "));
        assertTrue(blank.getMessage().contains("whitespace"), blank.getMessage());
    }
}
