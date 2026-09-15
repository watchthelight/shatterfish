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
    @DisplayName("a class-keyed choice is laid out by name, and only that one")
    void chances_are_laid_out_by_name_only_where_vanilla_had_no_order() {
        // Classes whose HashMap walk differs from their name order, found by looking: identity
        // hashes are what they are. With the hook, the seeded draw lands on the same class however
        // the map happens to walk; without it, on whichever class the map walks to first, which is
        // the process's business and not the tuple's.
        List<Class<?>> keys = classesWhoseHashOrderIsNotTheirNameOrder();
        HashMap<Class<?>, Float> byHash = new HashMap<>();
        for (Class<?> key : keys) {
            byHash.put(key, 1f);
        }
        List<Class<?>> sorted = new ArrayList<>(keys);
        sorted.sort(java.util.Comparator.comparing(String::valueOf));
        for (long seed = 1; seed <= 40; seed++) {
            assertEquals(predicted(sorted, seed), pick(byHash, seed), "seed " + seed + ", class keys");
        }

        // A map whose walk order vanilla defines is left exactly as vanilla lays it out: the
        // generator's category maps are LinkedHashMaps filled in declaration order and drawn from
        // inside the floor's seeded push, so sorting them would make this fork's seed generate a
        // different floor from upstream's. The review of story 1.16 caught the first draft doing
        // exactly that.
        List<Class<?>> reversed = new ArrayList<>(keys);
        java.util.Collections.reverse(reversed);
        HashMap<Class<?>, Float> defined = new LinkedHashMap<>();
        for (Class<?> key : reversed) {
            defined.put(key, 1f);
        }
        for (long seed = 1; seed <= 40; seed++) {
            assertEquals(predicted(reversed, seed), pick(defined, seed),
                    "seed " + seed + ", a map whose order vanilla defines keeps it");
        }

        // And a map keyed by something other than a class is left alone too: its keys' own hashes
        // fix its walk, the same in every process.
        HashMap<String, Float> strings = new HashMap<>();
        for (String key : List.of("zeta", "alpha", "mu", "b", "omega", "k")) {
            strings.put(key, 1f);
        }
        List<String> walked = new ArrayList<>(strings.keySet());
        for (long seed = 1; seed <= 40; seed++) {
            assertEquals(predicted(walked, seed), pickStrings(strings, seed),
                    "seed " + seed + ", string keys walk as the map walks them");
        }
    }

    /** What the game's own algorithm gives when the slices sit in {@code order}. */
    private static <K> K predicted(List<K> order, long seed) {
        Random.pushGenerator(seed);
        try {
            float value = Random.Float(order.size());
            return order.get(Math.min(order.size() - 1, (int) value));
        } finally {
            Random.popGenerator();
        }
    }

    private static Class<?> pick(HashMap<Class<?>, Float> chances, long seed) {
        Random.pushGenerator(seed);
        try {
            return Random.chances(chances);
        } finally {
            Random.popGenerator();
        }
    }

    private static String pickStrings(HashMap<String, Float> chances, long seed) {
        Random.pushGenerator(seed);
        try {
            return Random.chances(chances);
        } finally {
            Random.popGenerator();
        }
    }

    private static List<Class<?>> classesWhoseHashOrderIsNotTheirNameOrder() {
        // A dozen classes; some pair walks out of name order in any process, and the loop proves
        // it for this one rather than assuming it.
        List<Class<?>> candidates = List.of(String.class, Integer.class, Long.class, Double.class,
                Boolean.class, Character.class, Object.class, Number.class, Thread.class,
                Runnable.class, Iterable.class, Comparable.class);
        HashMap<Class<?>, Float> map = new HashMap<>();
        for (Class<?> key : candidates) {
            map.put(key, 1f);
        }
        List<Class<?>> walked = new ArrayList<>(map.keySet());
        List<Class<?>> sorted = new ArrayList<>(candidates);
        sorted.sort(java.util.Comparator.comparing(String::valueOf));
        assertTrue(!walked.equals(sorted), "a map that walks its classes out of name order: " + walked);
        return candidates;
    }

    private static Object privateStatic(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
