package org.shatterfish.harness.determinism;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three ordering sites of hook row 6, held directly, because the two-JVM test cannot hold them.
 *
 * <p>The mutation battery put each site back to vanilla and {@code DeterminismTwoJvmTest} noticed
 * none of them: two JVMs started the same way on one machine give the same objects the same
 * identity hashes, so a {@code HashSet} walks in the same order in both and the reversion is
 * invisible. The hazard is real all the same — identity hashes are not a promise, and a different
 * JVM, platform or startup path gives different ones — which is why the cross-platform comparison
 * is story 3.4's nightly job and why these sites are held here by what they are rather than by what
 * they happen to do. The guidebook site, the fourth, draws from the system and the two-JVM test
 * catches its reversion at once.
 *
 * <p>Reflection is used for {@code Actor}'s private sets. The harness's own rule confines
 * reflection in main code; a test reading a field's runtime type to hold a hook in place is what
 * the rule leaves room for.
 */
class IdentityOrderTest {

    private static final long RUN_SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    @DisplayName("the actor sets walk in insertion order")
    void the_actor_sets_are_insertion_ordered() throws Exception {
        // process() walks `all` to break a tie between actors due at the same moment
        // (core/.../actors/Actor.java:251-265 at the tag); a HashSet would break it by identity.
        assertTrue(privateStatic(Actor.class, "all") instanceof LinkedHashSet,
                "Actor.all is insertion-ordered");
        assertTrue(privateStatic(Actor.class, "chars") instanceof LinkedHashSet,
                "Actor.chars is insertion-ordered");
    }

    @Test
    @DisplayName("a level's mobs and blobs walk in insertion order, at create and at restore")
    void the_level_collections_are_insertion_ordered() {
        driver = HeadlessDriver.start(4242L, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        assertTrue(Dungeon.level.mobs instanceof LinkedHashSet, "Level.mobs after create()");
        assertTrue(Dungeon.level.blobs instanceof LinkedHashMap, "Level.blobs after create()");

        // The restore site is the other place the level builds them: save the floor and load it
        // back, the way the game does on every floor change, and look again.
        try {
            Dungeon.saveAll();
            com.shatteredpixel.shatteredpixeldungeon.levels.Level loaded =
                    Dungeon.loadLevel(com.shatteredpixel.shatteredpixeldungeon.GamesInProgress.curSlot);
            assertTrue(loaded.mobs instanceof LinkedHashSet, "Level.mobs after restore()");
            assertTrue(loaded.blobs instanceof LinkedHashMap, "Level.blobs after restore()");
        } catch (java.io.IOException e) {
            throw new AssertionError("the floor could not be saved and loaded", e);
        }
    }

    @Test
    @DisplayName("a class-keyed choice is laid out by name, whatever order the map walks in")
    void chances_are_laid_out_by_name() {
        // Keys whose HashMap order differs from their name order: found by looking, because a
        // String's hash is not its alphabet. With the sort, the seeded draw lands on the same key
        // however the map happens to walk; without it, the draw lands on whichever key the map
        // walks to first, which is the map's business and not the tuple's.
        List<String> keys = keysWhoseHashOrderIsNotTheirNameOrder();
        HashMap<String, Float> oneWay = new HashMap<>();
        HashMap<String, Float> otherWay = new LinkedHashMap<>();
        for (String key : keys) {
            oneWay.put(key, 1f);
        }
        for (int i = keys.size() - 1; i >= 0; i--) {
            otherWay.put(keys.get(i), 1f);
        }

        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(null);
        for (long seed = 1; seed <= 40; seed++) {
            String expected = predictedByName(sorted, seed);
            assertEquals(expected, pick(oneWay, seed), "seed " + seed + ", the map walked one way");
            assertEquals(expected, pick(otherWay, seed), "seed " + seed + ", the map walked the other");
        }
    }

    /** What the game's own algorithm gives when the slices are laid out in name order. */
    private static String predictedByName(List<String> sortedKeys, long seed) {
        Random.pushGenerator(seed);
        try {
            float value = Random.Float(sortedKeys.size());
            return sortedKeys.get(Math.min(sortedKeys.size() - 1, (int) value));
        } finally {
            Random.popGenerator();
        }
    }

    private static String pick(HashMap<String, Float> chances, long seed) {
        Random.pushGenerator(seed);
        try {
            return Random.chances(chances);
        } finally {
            Random.popGenerator();
        }
    }

    private static List<String> keysWhoseHashOrderIsNotTheirNameOrder() {
        // Six short keys; the pair whose bucket order is not alphabetical exists in any sample of
        // this size, and the loop below proves it rather than assuming it.
        List<String> candidates = List.of("zeta", "alpha", "mu", "b", "omega", "k");
        HashMap<String, Float> map = new HashMap<>();
        for (String key : candidates) {
            map.put(key, 1f);
        }
        List<String> walked = new ArrayList<>(map.keySet());
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(null);
        assertTrue(!walked.equals(sorted), "a map that walks its keys out of name order: " + walked);
        return candidates;
    }

    private static Object privateStatic(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
