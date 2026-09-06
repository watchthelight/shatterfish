package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Tile;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every terrain of the tag reaches a {@link Tile} through the tile sheet's own tables, the
 * secret terrains reach their cover's tile, and every tile but {@link Tile#NONE} is reached by
 * some terrain, so the schema and the sheet agree (ADR-0006, Terrain).
 */
class TerrainTableTest {

    /** The flag constants of {@code Terrain}, which are bits, not terrains. */
    private static final Set<String> FLAGS = Set.of("PASSABLE", "LOS_BLOCKING", "FLAMABLE", "SECRET", "SOLID", "AVOID",
            "LIQUID", "PIT");

    /** Every terrain constant of the tag, by name, read from the class itself. */
    static Map<String, Integer> terrains() throws IllegalAccessException {
        Map<String, Integer> terrains = new LinkedHashMap<>();
        for (Field field : Terrain.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() == int.class && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && !FLAGS.contains(field.getName())) {
                terrains.put(field.getName(), field.getInt(null));
            }
        }
        return terrains;
    }

    @Test
    @DisplayName("every terrain constant maps to a tile, and every tile but NONE is some terrain's")
    void every_terrain_maps() throws Exception {
        Map<String, Integer> terrains = terrains();
        assertEquals(39, terrains.size(), "the terrains of v3.3.8 (Terrain.java:26-70)");
        EnumSet<Tile> reached = EnumSet.noneOf(Tile.class);
        for (Map.Entry<String, Integer> terrain : terrains.entrySet()) {
            Tile tile = Observer.tile(terrain.getValue());
            assertTrue(tile != Tile.NONE, terrain.getKey() + " is drawn as nothing");
            reached.add(tile);
        }
        EnumSet<Tile> expected = EnumSet.allOf(Tile.class);
        expected.remove(Tile.NONE);
        assertEquals(expected, reached, "every tile is some terrain's visual");
    }

    @Test
    @DisplayName("a secret door is a wall, a secret trap and every trap floor, a hero-locked door a locked door")
    void secrets_and_look_alikes_share_their_cover() {
        assertEquals(Tile.WALL, Observer.tile(Terrain.SECRET_DOOR));
        assertEquals(Observer.tile(Terrain.WALL), Observer.tile(Terrain.SECRET_DOOR));
        assertEquals(Tile.EMPTY, Observer.tile(Terrain.SECRET_TRAP));
        assertEquals(Tile.EMPTY, Observer.tile(Terrain.TRAP));
        assertEquals(Tile.EMPTY, Observer.tile(Terrain.INACTIVE_TRAP));
        assertEquals(Tile.EMPTY, Observer.tile(Terrain.CUSTOM_DECO));
        assertEquals(Tile.EMPTY, Observer.tile(Terrain.CUSTOM_DECO_EMPTY));
        assertEquals(Tile.LOCKED_DOOR, Observer.tile(Terrain.HERO_LKD_DR));
        assertEquals(Tile.WATER, Observer.tile(Terrain.WATER));
        assertEquals(Tile.CHASM, Observer.tile(Terrain.CHASM));
    }

    @Test
    @DisplayName("the mapping is the sheet's tables: a terrain both tables lack and that is neither water nor chasm fails")
    void the_tables_decide() {
        int unknown = 200;
        assertEquals(null, DungeonTileSheet.directVisuals.get(unknown, null));
        assertEquals(null, DungeonTileSheet.directFlatVisuals.get(unknown, null));
        IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Observer.tile(unknown));
        assertTrue(failure.getMessage().contains("neither table"), failure.getMessage());
        // The mine's two solids share every sprite index and are told apart by name.
        assertEquals(DungeonTileSheet.FLAT_MINE_CRYSTAL, DungeonTileSheet.FLAT_MINE_BOULDER);
        assertEquals(Tile.MINE_CRYSTAL, Observer.tile(Terrain.MINE_CRYSTAL));
        assertEquals(Tile.MINE_BOULDER, Observer.tile(Terrain.MINE_BOULDER));
    }
}
