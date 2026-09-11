package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Tile;
import org.shatterfish.api.ValidActions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of tiles a click walks onto is the game's own rule, seen through the tile sheet. It
 * lives in {@code api}, which cannot see {@code Terrain} to check itself against it; this suite is
 * in the harness, which sees both, and is what stops the table drifting from the game at an
 * upgrade or by a guess.
 *
 * <p>The rule the hero applies to the cell a click steps to is {@code passable[cell] ||
 * avoid[cell]} ({@code core/.../actors/hero/Hero.java:1832-1835}). The Observation carries a
 * visual and not a terrain, so the two directions are not symmetric: every terrain the hero can
 * enter must draw as a tile the table admits, or the bot loses a move it can see; and a tile the
 * table admits may still cover a terrain the hero cannot enter, which is a step the executor
 * refuses and a person who clicks on the blacksmith's forge also gets. The second direction is
 * therefore a named list rather than an assertion.
 */
class WalkableTableTest {

    /** The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). */
    static final List<String> ADR_0006_ROWS = List.of("Valid Actions");

    /**
     * Terrains the hero cannot enter that are drawn as a tile a click walks onto. Each is a
     * decoration that borrows a floor's or the water's visual ({@code …/levels/Terrain.java:119-120};
     * {@code …/tiles/DungeonTileSheet.java:434-442}), so the screen says walk and the game says no.
     * A new one at an upgrade lands here with its reason or the test fails.
     */
    private static final Set<Integer> DRAWN_WALKABLE_BUT_SOLID = Set.of(Terrain.CUSTOM_DECO, Terrain.CUSTOM_DECO_WTR);

    @Test
    @DisplayName("every terrain the hero can step onto draws as a tile the valid set walks onto")
    void the_table_admits_every_terrain_the_hero_can_enter() {
        List<String> missing = new ArrayList<>();
        for (int terrain : terrains()) {
            boolean enterable = (Terrain.flags[terrain] & (Terrain.PASSABLE | Terrain.AVOID)) != 0;
            if (!enterable) {
                continue;
            }
            Tile tile = Observer.tile(terrain);
            if (!walkable(tile)) {
                missing.add(name(terrain) + " draws as " + tile + " and the hero can enter it");
            }
        }
        assertTrue(missing.isEmpty(), "the valid set would lose these moves: " + missing);
    }

    @Test
    @DisplayName("a tile the set walks onto covers a terrain the hero cannot enter only where it is named")
    void the_table_over_admits_only_where_it_says() {
        TreeMap<String, TreeSet<String>> surprises = new TreeMap<>();
        for (int terrain : terrains()) {
            boolean enterable = (Terrain.flags[terrain] & (Terrain.PASSABLE | Terrain.AVOID)) != 0;
            if (enterable || DRAWN_WALKABLE_BUT_SOLID.contains(terrain)) {
                continue;
            }
            Tile tile = Observer.tile(terrain);
            if (walkable(tile)) {
                surprises.computeIfAbsent(tile.name(), t -> new TreeSet<>()).add(name(terrain));
            }
        }
        assertEquals(new TreeMap<String, TreeSet<String>>(), surprises,
                "a terrain the hero cannot enter draws as a walkable tile and is not named in"
                        + " DRAWN_WALKABLE_BUT_SOLID: add it with its reason, or the bot will keep"
                        + " walking into it");
    }

    @Test
    @DisplayName("the two the game marks avoid rather than passable are steps: the chasm and the well")
    void avoid_is_not_solid() {
        assertTrue(walkable(Observer.tile(Terrain.CHASM)), "a click on a chasm asks before the hero jumps");
        assertTrue(walkable(Observer.tile(Terrain.WELL)), "a click on a well is how a person drinks it");
        assertEquals(0, Terrain.flags[Terrain.WELL] & Terrain.PASSABLE, "the well is avoid, not passable");
        assertEquals(0, Terrain.flags[Terrain.CHASM] & Terrain.PASSABLE);
    }

    /**
     * Whether the set would offer a step onto this tile, asked of the set rather than of the table:
     * a cell of that tile beside the hero either is a step or is not.
     */
    private static boolean walkable(Tile tile) {
        return TableProbe.walkable(tile);
    }

    private static List<Integer> terrains() {
        List<Integer> terrains = new ArrayList<>();
        for (Field field : Terrain.class.getDeclaredFields()) {
            if (field.getType() == int.class && Modifier.isStatic(field.getModifiers())
                    && !field.getName().equals("PASSABLE") && !field.getName().equals("LOS_BLOCKING")
                    && !field.getName().equals("FLAMABLE") && !field.getName().equals("SECRET")
                    && !field.getName().equals("SOLID") && !field.getName().equals("AVOID")
                    && !field.getName().equals("LIQUID") && !field.getName().equals("PIT")) {
                try {
                    terrains.add(field.getInt(null));
                } catch (IllegalAccessException refused) {
                    throw new AssertionError(field.getName(), refused);
                }
            }
        }
        return terrains;
    }

    private static String name(int terrain) {
        for (Field field : Terrain.class.getDeclaredFields()) {
            try {
                if (field.getType() == int.class && Modifier.isStatic(field.getModifiers())
                        && field.getInt(null) == terrain) {
                    return field.getName();
                }
            } catch (IllegalAccessException ignored) {
                // a field the test cannot read names nothing
            }
        }
        return String.valueOf(terrain);
    }

    /**
     * Asks the valid set itself whether a tile is one a click walks onto, by building the smallest
     * Observation that can answer: a hero with that tile beside it, and a step to it or not.
     */
    private static final class TableProbe {

        static boolean walkable(Tile tile) {
            Observation observation = Fixtures.oneCellFloor(tile);
            ActionsSection valid = ValidActions.of(observation);
            return valid.actions().contains(new Action.Step(Fixtures.BESIDE));
        }
    }
}
