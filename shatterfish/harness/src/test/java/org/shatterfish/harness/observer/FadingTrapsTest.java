package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TenguDartTrap;
import com.watabou.utils.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.TrapView;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.practice.KitProfiles;
import org.shatterfish.harness.practice.Practice;
import org.shatterfish.harness.practice.PracticeBoss;
import org.shatterfish.harness.practice.PracticeStart;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tengu's dart traps as the Observation carries them (story 10.19, issue #365; ADR-0006, Traps). The
 * prison boss level sets each {@code TenguDartTrap} hidden and covers the cell with a {@code FadingTraps}
 * tilemap that the game takes out of {@code customTiles} two turns later
 * ({@code PrisonBossLevel.java:716}, {@code :724-727}, {@code :850-874}). The section shows a hidden trap
 * of that kind only while the tilemap stands over it, and not a trap of any other kind and not a cell the
 * fog paints opaque. The Brain-side halves of the fairness question are {@code TenguTrapMemoryLeakTest}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class FadingTrapsTest {

    private static final long SEED = 27_182_818L;

    private static final long RUN_SALT = 0x5A17_5A17L;

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

    private void onTheBossFloor() {
        driver = HeadlessDriver.start(SEED, PracticeStart.gameClass(HeroClass.WARRIOR), RUN_SALT,
                new Practice(PracticeBoss.TENGU, KitProfiles.STANDARD));
        driver.stepToInputWait();
        level = Dungeon.level;
        hero = Dungeon.hero;
        assertTrue(level instanceof PrisonBossLevel);
    }

    private List<Integer> floorsInView(int count) {
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < level.length() && cells.size() < count; cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && level.traps.get(cell, null) == null && level.heaps.get(cell, null) == null
                    && Actor.findChar(cell) == null) {
                cells.add(cell);
            }
        }
        assertEquals(count, cells.size(), "enough free floor in view");
        return cells;
    }

    private static List<Integer> darts(MapSection map) {
        return map.traps().stream().filter(t -> t.kind().equals("poison dart trap")).map(TrapView::cell).sorted().toList();
    }

    /** A tilemap over the whole level, as the game's is over the cell: the section reads its presence and the cells it covers. */
    private PrisonBossLevel.FadingTraps cover() {
        PrisonBossLevel.FadingTraps tiles = new PrisonBossLevel.FadingTraps();
        tiles.setCoveringArea(new Rect(0, 0, level.width(), level.height()));
        level.customTiles.add(tiles);
        return tiles;
    }

    @Test
    @DisplayName("a hidden dart trap is in the section while the fading tilemap stands over it, and gone once the game takes the tilemap out")
    void shown_for_the_draw_and_not_after() {
        onTheBossFloor();
        List<Integer> cells = floorsInView(3);
        for (int cell : cells) {
            level.setTrap(new TenguDartTrap().hide(), cell);
            Level.set(cell, Terrain.SECRET_TRAP);
        }
        assertEquals(List.of(), darts(new Observer().map()), "nothing is drawn before the game lays the tilemap");

        PrisonBossLevel.FadingTraps tiles = cover();
        assertEquals(cells.stream().sorted().toList(), darts(new Observer().map()),
                "while the tilemap stands the screen shows the traps (PrisonBossLevel.java:791-813)");

        level.customTiles.remove(tiles);
        assertEquals(List.of(), darts(new Observer().map()),
                "the actor two turns on has taken the tilemap out (:850-874), and the traps are hidden again");
    }

    @Test
    @DisplayName("under a standing tilemap only the traps the fog lets the player see are shown, and no trap that is not a dart trap is made one")
    void fog_and_kind_are_respected() {
        onTheBossFloor();
        List<Integer> cells = floorsInView(2);
        level.setTrap(new TenguDartTrap().hide(), cells.get(0));
        Level.set(cells.get(0), Terrain.SECRET_TRAP);
        level.setTrap(new com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap().hide(), cells.get(1));
        Level.set(cells.get(1), Terrain.SECRET_TRAP);
        int far = -1;
        for (int cell = 0; cell < level.length(); cell++) {
            if (!level.visited[cell] && !level.mapped[cell] && !level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY) {
                far = cell;
                break;
            }
        }
        assertTrue(far >= 0, "an unvisited floor cell");
        level.setTrap(new TenguDartTrap().hide(), far);
        Level.set(far, Terrain.SECRET_TRAP);
        cover();
        MapSection map = new Observer().map();
        assertEquals(Fog.UNKNOWN, map.fog().get(far));
        assertEquals(List.of(cells.get(0)), darts(map), "the unseen cell is not shown even under the tilemap");
    }

    @Test
    @DisplayName("with no tilemap standing a hidden trap is invisible however many lie on the floor: the permutation leaves the section as it was")
    void a_hidden_patch_moves_nothing() {
        onTheBossFloor();
        MapSection plain = new Observer().map();
        List<Integer> cells = floorsInView(4);
        for (int cell : cells) {
            level.setTrap(new TenguDartTrap().hide(), cell);
            Level.set(cell, Terrain.SECRET_TRAP);
        }
        assertEquals(plain.traps(), new Observer().map().traps());
        assertEquals(plain.tiles(), new Observer().map().tiles());
    }
}
