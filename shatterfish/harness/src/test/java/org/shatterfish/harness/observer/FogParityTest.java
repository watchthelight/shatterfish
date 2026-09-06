package org.shatterfish.harness.observer;

import com.badlogic.gdx.graphics.Pixmap;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MindVision;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTileSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.FogOfWar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.Tile;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Observer's fog is the fog the scene painted, cell for cell, read back from the fog of war's
 * own texture rather than recomputed from the arrays (ADR-0006, Cell visibility): at the first
 * wait, where a room's far wall is painted opaque while in view; blinded, with mind vision, and
 * after a scroll of magic mapping is read, each through the game's own effect and observe; and
 * with a cell of rock marked visited and mapped by hand, which the fog still paints opaque. The
 * one step past the paint is the examine window: a wall painted opaque that is visited or mapped
 * is emitted at that level, since the window opens on it and draws its tile.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class FogParityTest {

    private static final long SEED = 14_142_135L;

    private HeadlessDriver driver;
    private Level level;
    private Hero hero;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void atTheFirstWait() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
    }

    /**
     * Paints the whole fog anew. The headless frame updates the scene but draws nothing
     * ({@code SceneStepper}), so the fog of war's texture keeps its creation fill until its own
     * painter runs; this calls that painter with the level's arrays, as the fog's {@code draw()}
     * would on a drawn frame ({@code FogOfWar.java:321-329}).
     */
    private void repaint() throws Exception {
        GameScene.updateFog();
        driver.step();
        Method paint = FogOfWar.class.getDeclaredMethod("updateTexture", boolean[].class, boolean[].class, boolean[].class);
        paint.setAccessible(true);
        paint.invoke(fog(), level.heroFOV, level.visited, level.mapped);
    }

    private FogOfWar fog() throws Exception {
        Field field = GameScene.class.getDeclaredField("fog");
        field.setAccessible(true);
        return (FogOfWar) field.get(driver.scene());
    }

    @Test
    @DisplayName("at the first wait every cell reads as the fog painted it, and a far wall in view is not in view")
    void the_first_wait() throws Exception {
        atTheFirstWait();
        repaint();
        MapSection map = new Observer().map();
        int opaqueWallsInView = 0;
        for (int cell = 0; cell < level.length(); cell++) {
            Fog expected = expected(cell);
            assertEquals(expected, map.fog().get(cell), "cell " + cell + " (in view " + level.heroFOV[cell] + ", visited "
                    + level.visited[cell] + ", wall " + DungeonTileSheet.wallStitcheable(level.map[cell]) + ")");
            assertEquals(expected == Fog.UNKNOWN, map.tiles().get(cell) == Tile.NONE, "cell " + cell);
            if (level.heroFOV[cell] && painted(cell) == Fog.UNKNOWN) {
                opaqueWallsInView++;
                assertEquals(Fog.VISITED, map.fog().get(cell), "a visited wall painted opaque is emitted at examine level");
            }
        }
        assertTrue(opaqueWallsInView > 0, "the first room has a far wall the fog paints opaque while it is in view (FogOfWar.java:263-267)");

        // The same wait twice is the same section, byte for byte.
        MapSection again = new Observer().map();
        assertEquals(map, again);
        assertArrayEquals(ObservationCodec.encode(Skeleton.around(new Observer().header(), map)),
                ObservationCodec.encode(Skeleton.around(new Observer().header(), again)));
    }

    @Test
    @DisplayName("blinded, the fog closes to the cells around the hero, and the Observer follows the paint")
    void blinded() throws Exception {
        atTheFirstWait();
        Buff.affect(hero, Blindness.class, 10f);
        Dungeon.observe();
        repaint();
        MapSection map = new Observer().map();
        int visible = 0;
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(expected(cell), map.fog().get(cell), "cell " + cell);
            visible += map.fog().get(cell) == Fog.VISIBLE ? 1 : 0;
        }
        assertTrue(visible <= 9 && visible > 0, "a blinded hero sees the cells around it (Level.java:1342-1378): " + visible);
        hero.buff(Blindness.class).detach();
        Dungeon.observe();
        repaint();
        MapSection after = new Observer().map();
        int seeing = 0;
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(expected(cell), after.fog().get(cell), "cell " + cell);
            seeing += after.fog().get(cell) == Fog.VISIBLE ? 1 : 0;
        }
        assertTrue(seeing > visible, "sight returns");
    }

    @Test
    @DisplayName("with mind vision every mob's cell is in view, and the Observer follows the paint")
    void mind_vision() throws Exception {
        atTheFirstWait();
        assertTrue(!level.mobs.isEmpty(), "the floor has mobs to see");
        Buff.affect(hero, MindVision.class, 5f);
        Dungeon.observe();
        repaint();
        MapSection map = new Observer().map();
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(expected(cell), map.fog().get(cell), "cell " + cell);
        }
        for (Mob mob : level.mobs) {
            assertEquals(Fog.VISIBLE, map.fog().get(mob.pos), "mind vision shows the cells around every mob (Level.java:1346-1378)");
        }
    }

    @Test
    @DisplayName("a scroll of magic mapping read the game's way maps the floor, and the Observer follows the paint")
    void magic_mapping() throws Exception {
        atTheFirstWait();
        ScrollOfMagicMapping scroll = new ScrollOfMagicMapping();
        assertTrue(scroll.collect(hero.belongings.backpack));
        scroll.execute(hero, Scroll.AC_READ);
        driver.stepToInputWait();
        repaint();
        MapSection map = new Observer().map();
        int mapped = 0;
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(expected(cell), map.fog().get(cell), "cell " + cell);
            mapped += map.fog().get(cell) == Fog.MAPPED ? 1 : 0;
            if (level.discoverable[cell]) {
                assertTrue(map.fog().get(cell) != Fog.UNKNOWN, "every discoverable cell is mapped (ScrollOfMagicMapping.java:53-59): " + cell);
            }
        }
        assertTrue(mapped > 0, "cells beyond the room are mapped, not visited");
    }

    @Test
    @DisplayName("a cell of rock marked visited and mapped by hand is still painted opaque, and reads unknown")
    void rock_stays_opaque() throws Exception {
        atTheFirstWait();
        int rock = -1;
        for (int cell = 0; cell < level.length(); cell++) {
            if (!level.discoverable[cell]) {
                rock = cell;
                break;
            }
        }
        assertTrue(rock >= 0, "the floor has solid rock");
        level.visited[rock] = true;
        level.mapped[rock] = true;
        repaint();
        MapSection map = new Observer().map();
        assertEquals(Fog.UNKNOWN, painted(rock), "the fog paints a cell that cannot be discovered opaque (FogOfWar.java:200-205)");
        assertEquals(Fog.UNKNOWN, map.fog().get(rock));
        assertEquals(Tile.NONE, map.tiles().get(rock));
        for (int cell = 0; cell < level.length(); cell++) {
            assertEquals(expected(cell), map.fog().get(cell), "cell " + cell);
        }
    }

    /** What the Observer must emit: the painted level, or the examine level for a wall painted opaque. */
    private Fog expected(int cell) throws Exception {
        Fog painted = painted(cell);
        if (painted == Fog.UNKNOWN && level.discoverable[cell] && DungeonTileSheet.wallStitcheable(level.map[cell])
                && (level.visited[cell] || level.mapped[cell])) {
            return level.visited[cell] ? Fog.VISITED : Fog.MAPPED;
        }
        return painted;
    }

    /**
     * The level the fog of war painted on a cell, read from its texture: the lighter of the cell's
     * two halves, which an internal wall paints apart ({@code FogOfWar.java:301-314}).
     */
    private Fog painted(int cell) throws Exception {
        Pixmap bitmap = fog().texture.bitmap;
        Field colors = FogOfWar.class.getDeclaredField("FOG_COLORS");
        colors.setAccessible(true);
        int[][] table = (int[][]) colors.get(null);
        int brightness = SPDSettings.brightness() + 1;
        int x = cell % level.width() * 2;
        int y = cell / level.width() * 2;
        Fog left = level(bitmap.getPixel(x, y), table, brightness);
        Fog right = level(bitmap.getPixel(x + 1, y), table, brightness);
        return left.ordinal() <= right.ordinal() ? left : right;
    }

    private static Fog level(int pixel, int[][] table, int brightness) {
        for (int index = 0; index < table.length; index++) {
            int argb = table[index][brightness];
            if (pixel == ((argb << 8) | (argb >>> 24))) {
                return Fog.values()[index];
            }
        }
        return fail("a fog pixel of no level: " + Integer.toHexString(pixel));
    }
}
