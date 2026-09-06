package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.EbonyMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GoldenMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real chest and a stealthy mimic at the same cell are one Observation, byte for byte
 * (ADR-0006, Heaps and Mobs): the mimic is drawn as the chest and named as the chest, so it is a
 * heap of that kind and never an actor, until it stops hiding. The chest, the locked chest and
 * the crystal chest with its category are each paired with the mimic that imitates them.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MimicDifferentialTest {

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

    @Test
    @DisplayName("a real chest and a stealthy mimic at the same cell produce byte-identical Observations")
    void a_chest_and_a_stealthy_mimic_are_one_observation() throws Exception {
        atTheFirstWait();
        List<Integer> cells = floorsInView(3);
        pair(cells.get(0), Heap.Type.CHEST, Mimic.class, HeapKind.CHEST, new Torch(), new Torch(), "");
        pair(cells.get(1), Heap.Type.LOCKED_CHEST, GoldenMimic.class, HeapKind.LOCKED_CHEST, new Torch(), new Torch(), "");
        pair(cells.get(2), Heap.Type.CRYSTAL_CHEST, CrystalMimic.class, HeapKind.CRYSTAL_CHEST, new WandOfMagicMissile(),
                new WandOfMagicMissile(), Messages.get(Heap.class, "wand"));
    }

    private void pair(int cell, Heap.Type type, Class<? extends Mimic> mimicClass, HeapKind kind, Item inChest, Item inMimic,
                      String category) throws Exception {
        Heap chest = level.drop(inChest, cell);
        chest.type = type;
        byte[] real = bytes();
        HeapView realView = heapAt(new Observer().map(), cell).orElseThrow();
        assertEquals(new HeapView(cell, kind, false, "", 0, category), realView);
        chest.destroy();
        assertTrue(level.heaps.get(cell, null) == null);

        Mimic mimic = Mimic.spawnAt(cell, mimicClass, inMimic);
        stealthy(mimic);
        GameScene.add(mimic);
        assertTrue(level.mobs.contains(mimic), "GameScene.add puts the mob on the level");
        assertTrue(Observer.hiddenMimic(mimic), "spawned neutral and passive (Mimic.java:62-64)");
        assertEquals(chestName(kind), mimic.name(), "a hidden mimic names itself as the chest (Mimic.java:112-118)");

        byte[] hidden = bytes();
        assertArrayEquals(real, hidden, kind + ": the chest and the hidden mimic are one Observation");
        assertEquals(realView, heapAt(new Observer().map(), cell).orElseThrow());
        assertTrue(actorAt(cell).isEmpty(), "never an actor while hidden");

        // stopHiding() sets the state (Mimic.java:212-222); the alignment follows on the mimic's
        // next act (:134-145), which comes before the next Input wait in play. Set as the act would.
        mimic.stopHiding();
        assertEquals(Char.Alignment.NEUTRAL, mimic.alignment);
        mimic.alignment = Char.Alignment.ENEMY;
        ActorView revealed = actorAt(cell).orElseThrow();
        assertEquals(mimic.name(), revealed.name());
        assertEquals(Alignment.ENEMY, revealed.alignment());
        assertTrue(heapAt(new Observer().map(), cell).isEmpty(), "no heap once the mimic is a mob");
        mimic.destroy();
        level.mobs.remove(mimic);
    }

    @Test
    @DisplayName("out of view, a hidden mimic is drawn only when stealthy and its cell visited, as afterObserve decides")
    void a_hidden_mimic_out_of_view() throws Exception {
        atTheFirstWait();
        int cell = floorOutOfView();
        level.visited[cell] = true;
        Mimic shy = Mimic.spawnAt(cell, Mimic.class, new Torch());
        GameScene.add(shy);
        assertTrue(!shy.stealthy(), "a mimic is not stealthy without the trinket (Mimic.java:325-327)");
        assertTrue(heapAt(new Observer().map(), cell).isEmpty(), "drawn like any mob, only in view (GameScene.java:1447)");
        assertTrue(actorAt(cell).isEmpty());

        stealthy(shy);
        assertEquals(new HeapView(cell, HeapKind.CHEST, false, "", 0, ""), heapAt(new Observer().map(), cell).orElseThrow(),
                "a stealthy mimic stays drawn once its cell is visited (GameScene.java:1443-1445)");
        shy.destroy();
        level.mobs.remove(shy);

        // An ebony mimic is always stealthy and hides at alpha 0.2, the chest only it wears, drawn faint.
        int inView = floorsInView(1).get(0);
        Mimic ebony = Mimic.spawnAt(inView, EbonyMimic.class, new Torch());
        GameScene.add(ebony);
        assertTrue(ebony.stealthy(), "EbonyMimic.java:69-71");
        assertEquals(new HeapView(inView, HeapKind.EBONY_CHEST, true, "", 0, ""), heapAt(new Observer().map(), inView).orElseThrow(),
                "MimicSprite.java:121-125; ItemSpriteSheet.java:124");
        assertTrue(actorAt(inView).isEmpty());
        ebony.destroy();
        level.mobs.remove(ebony);
    }

    private byte[] bytes() {
        Observer observer = new Observer();
        Observation observation = Skeleton.around(observer.header(), observer.map(), observer.actors(), observer.hero());
        return ObservationCodec.encode(observation);
    }

    private static void stealthy(Mimic mimic) throws Exception {
        Field field = Mimic.class.getDeclaredField("stealthy");
        field.setAccessible(true);
        field.setBoolean(mimic, true);
        assertTrue(mimic.stealthy());
    }

    private static String chestName(HeapKind kind) {
        return switch (kind) {
            case LOCKED_CHEST -> Messages.get(Heap.class, "locked_chest");
            case CRYSTAL_CHEST -> Messages.get(Heap.class, "crystal_chest");
            default -> Messages.get(Heap.class, "chest");
        };
    }

    private Optional<ActorView> actorAt(int cell) {
        return new Observer().actors().actors().stream().filter(a -> a.cell() == cell).findFirst();
    }

    private static Optional<HeapView> heapAt(MapSection map, int cell) {
        return map.heaps().stream().filter(h -> h.cell() == cell).findFirst();
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

    private int floorOutOfView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.discoverable[cell] && !level.heroFOV[cell] && !level.visited[cell] && level.map[cell] == Terrain.EMPTY
                    && level.heaps.get(cell, null) == null && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        throw new AssertionError("no floor out of view");
    }
}
