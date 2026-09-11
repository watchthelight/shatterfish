package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ConfusionGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.SkeletonKey;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BlobCell;
import org.shatterfish.api.Fog;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Skeleton.Serialized;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The environment the map carries is what the screen draws of it and no more (ADR-0006, Blobs,
 * Danger count, Seed and turn): a blob is the kinds drawn on a cell the player can see, never an
 * amount; a blob the fog hides or the scene never drew is absent; the danger the indicator counts
 * is the enemies among the actors; and neither the seed nor the clock reaches the bytes. Paths
 * abbreviate {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/},
 * at the tag.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class EnvironmentLeakTest {

    /** The rows of ADR-0006's whitelist this suite holds ({@link VisibilityChecklistTest}). */
    static final List<String> ADR_0006_ROWS = List.of("Blobs", "Danger count", "Seed and turn");

    private static final long SEED = 31_415_926L;

    /** A volume no cell count, health or coordinate of this floor could be. */
    private static final int LOUD_VOLUME = 4242;

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
    @DisplayName("a blob in view is its kinds on the cell, never its volume")
    void a_blob_in_view_is_its_kinds() {
        atTheFirstWait();
        int cell = floorInView();
        assertTrue(new Observer().map().blobs().isEmpty(), "the first floor of the sewers has no blob");

        // The idiom every caller uses: the seed puts the blob on the level, and the scene gives it
        // the emitter that draws it (…/scenes/GameScene.java:1131-1136; …/items/potions/PotionOfToxicGas.java:49).
        GameScene.add(Blob.seed(cell, 20, ToxicGas.class));
        MapSection map = new Observer().map();
        BlobCell blob = blobAt(map, cell).orElseThrow();
        assertEquals(List.of("ToxicGas"), blob.kinds());
        assertEquals(1, map.blobs().size(), "one cell was seeded");
        Serialized.of(new Observer().observe()).assertPresent("ToxicGas");

        // A second kind on the same cell: two of the game's blobs, one cell, the names in order,
        // whatever order the level's HashMap hands them over in (…/levels/Level.java:184).
        GameScene.add(Blob.seed(cell, 20, ConfusionGas.class));
        assertEquals(List.of("ConfusionGas", "ToxicGas"), blobAt(new Observer().map(), cell).orElseThrow().kinds());
        Observation twice = new Observer().observe();
        assertEquals(twice.hash(), new Observer().observe().hash(), "two reads of one wait are the same Observation");

        // The differential the emitter's rule promises: one particle is drawn per cell however
        // much is there (…/effects/BlobEmitter.java:47-70), so the amount cannot change the bytes.
        String before = twice.hash();
        Blob gas = level.blobs.get(ToxicGas.class);
        gas.volume += LOUD_VOLUME - gas.cur[cell];
        gas.cur[cell] = LOUD_VOLUME;
        Serialized louder = Serialized.of(new Observer().observe());
        assertEquals(before, new Observer().observe().hash(), "the volume is not carried");
        louder.assertAbsent(String.valueOf(LOUD_VOLUME));
    }

    @Test
    @DisplayName("a blob on a cell the player cannot see is absent, and present once seen")
    void a_blob_out_of_view_is_absent() {
        atTheFirstWait();
        int remembered = floorOutOfView();
        level.visited[remembered] = true;
        assertEquals(Fog.VISITED, new Observer().map().fog().get(remembered), "remembered, not seen");

        GameScene.add(Blob.seed(remembered, 20, ToxicGas.class));
        MapSection map = new Observer().map();
        assertTrue(blobAt(map, remembered).isEmpty(), "the emitter draws no particle out of view"
                + " (BlobEmitter.java:62-64), and the fog is drawn over the gases (GameScene.java:343-353)");
        assertTrue(map.blobs().isEmpty());
        Serialized.of(new Observer().observe()).assertAbsent("ToxicGas");

        // The control: the same gas, the same volume, on a cell the hero sees.
        int seen = floorInView();
        GameScene.add(Blob.seed(seen, 20, ToxicGas.class));
        assertEquals(List.of("ToxicGas"), blobAt(new Observer().map(), seen).orElseThrow().kinds());
        assertTrue(blobAt(new Observer().map(), remembered).isEmpty(), "the remembered cell still shows nothing");
    }

    @Test
    @DisplayName("a blob the scene never gave an emitter draws nothing and is absent")
    void a_blob_with_no_emitter_is_absent() {
        atTheFirstWait();
        int cell = floorInView();

        // Blob.seed alone puts the blob on the level and no sprite in the scene
        // (…/actors/blobs/Blob.java:254-272); nothing is drawn until a scene gives it an emitter.
        Fire fire = Blob.seed(cell, 20, Fire.class, level);
        assertNotNull(fire);
        assertNull(fire.emitter, "no emitter, so Emitter.update emits nothing (Emitter.java:116-128)");
        assertTrue(new Observer().map().blobs().isEmpty(), "nothing drawn, nothing carried");
        Serialized.of(new Observer().observe()).assertAbsent("Fire");

        GameScene.add(fire);
        assertTrue(fire.emitter.on, "the emitter was given a factory (Fire.java, use(BlobEmitter))");
        assertEquals(List.of("Fire"), blobAt(new Observer().map(), cell).orElseThrow().kinds());
    }

    @Test
    @DisplayName("a blob the game marks always visible draws through its own cells and is not carried")
    void an_always_visible_blob_is_absent() {
        atTheFirstWait();
        int remembered = floorOutOfView();
        level.visited[remembered] = true;

        // The emitter draws a blob marked always visible wherever it is, through the fog of a
        // remembered cell (…/effects/BlobEmitter.java:62-64; …/items/artifacts/SkeletonKey.java:472-476,
        // :548-553). The fog paints that cell remembered and not seen, the record carries a blob
        // only on a seen cell, and the particles the player would see there are the loss ADR-0006
        // records.
        SkeletonKey.KeyWall wall = Blob.seed(remembered, 5, SkeletonKey.KeyWall.class, level);
        GameScene.add(wall);
        assertTrue(wall.alwaysVisible, "the flag the emitter's other gate reads");
        assertTrue(wall.emitter.on, "it pours particles like any other blob");
        assertTrue(new Observer().map().blobs().isEmpty(), "drawn to a player, not carried: the loss");

        // In view it is a blob like any other.
        int seen = floorInView();
        wall.seed(level, seen, 5);
        assertEquals(List.of("KeyWall"), blobAt(new Observer().map(), seen).orElseThrow().kinds());
        assertTrue(blobAt(new Observer().map(), remembered).isEmpty());
    }

    @Test
    @DisplayName("the danger count is the indicator's number: the enemies in view, invisible ones included")
    void the_danger_count_is_the_enemies_among_the_actors() {
        atTheFirstWait();
        assertEquals(hero.visibleEnemies(), enemies(), "the indicator's number at the first wait"
                + " (…/ui/DangerIndicator.java:87-104; …/actors/hero/Hero.java:1691-1694)");

        Mob mob = mobOutOfView();
        mob.pos = floorInView();
        if (mob.sprite != null) {
            mob.sprite.place(mob.pos);
        }
        // The game refreshes the list at the top of every hero act (…/actors/hero/Hero.java:859),
        // so at a wait it is what the indicator shows; the test moved the mob, so it refreshes too.
        hero.checkVisibleMobs();
        assertTrue(hero.visibleEnemies() > 0, "an enemy is in view now");
        assertEquals(hero.visibleEnemies(), enemies());

        // An invisible enemy is drawn faint and counted (…/sprites/CharSprite.java:401-407).
        Buff.affect(mob, Invisibility.class, 10f);
        hero.checkVisibleMobs();
        assertTrue(mob.invisible > 0);
        assertEquals(hero.visibleEnemies(), enemies(), "an invisible enemy in view is still counted");
        assertTrue(new Observer().actors().actors().stream().anyMatch(actor -> actor.cell() == mob.pos && actor.invisible()));
    }

    @Test
    @DisplayName("neither the seed nor the clock reaches the Observation")
    void the_seed_and_the_turn_are_absent() throws Exception {
        atTheFirstWait();
        Observation before = new Observer().observe();
        Serialized serialized = Serialized.of(before);
        serialized.assertAbsent(String.valueOf(Dungeon.seed));
        serialized.assertAbsent(DungeonSeed.convertToCode(Dungeon.seed));

        // The same screen, a different seed and a different clock: the bytes are the same, so a
        // Brain cannot fingerprint a published seed (ADR-0006, Seed and turn; FR-9).
        Dungeon.seed = 123_456_789L;
        Statistics.duration = 12_345f;
        Field now = Actor.class.getDeclaredField("now");
        now.setAccessible(true);
        now.setFloat(null, 4_242f);
        assertEquals(4_242f, Actor.now(), "the clock moved");
        assertEquals(before.hash(), new Observer().observe().hash(), "the seed and the clock are not carried");
    }

    private int enemies() {
        return (int) new Observer().actors().actors().stream()
                .filter(actor -> actor.alignment() == Alignment.ENEMY)
                .count();
    }

    private static Optional<BlobCell> blobAt(MapSection map, int cell) {
        return map.blobs().stream().filter(blob -> blob.cell() == cell).findFirst();
    }

    private int floorInView() {
        List<Fog> fog = new Observer().map().fog();
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.heroFOV[cell] && cell != hero.pos && level.map[cell] == Terrain.EMPTY
                    && Actor.findChar(cell) == null && level.heaps.get(cell, null) == null
                    && fog.get(cell) == Fog.VISIBLE) {
                return cell;
            }
        }
        throw new AssertionError("no free floor in view");
    }

    private int floorOutOfView() {
        for (int cell = 0; cell < level.length(); cell++) {
            if (!level.heroFOV[cell] && level.map[cell] == Terrain.EMPTY && level.discoverable[cell]) {
                return cell;
            }
        }
        throw new AssertionError("no floor out of view");
    }

    private Mob mobOutOfView() {
        for (Mob mob : level.mobs) {
            if (!level.heroFOV[mob.pos] && mob.alignment == Char.Alignment.ENEMY
                    && !Observer.hiddenMimic(mob) && mob.sprite != null) {
                return mob;
            }
        }
        throw new AssertionError("no enemy out of view");
    }

}
