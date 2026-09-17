package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.Random;

import java.util.function.Supplier;

/**
 * The one door from the generator to the two Run statics a table is parameterised by (story
 * 2.2): the game's initialisers read {@code Dungeon.depth} and {@code Dungeon.challenges} to
 * scale a mob or to strengthen a boss, so a table "at depth d under challenge c" is read by
 * constructing under those values. This class sets them around one construction and restores
 * what was there, whatever it was, so that a generation inside a live Run leaves the Run as it
 * found it, which {@code CodexLeakTest} holds. Some constructors draw (a loot picked by
 * {@code Random.oneOf}, an element picked by {@code Random.Int}); the construction runs under a
 * generator of the Codex's own, pushed and popped here, so that nothing is taken from and
 * nothing is left in the Run's, and the drawn facets are named and not dumped. No other
 * generator class may name {@code Dungeon} or the game's {@code Random}, and this one may reach
 * exactly the three fields and the two generator calls, which the same test's gate holds by
 * name. The third field is {@code Dungeon.level} (story 2.3): an item's actions may ask what
 * level the hero stands on (a pickaxe on the mining level cannot be dropped), so the level is
 * set to none around a construction, as a cold generation has it, and restored.
 *
 * <p>The fourth is {@code Dungeon.hero} (story 2.5), which only {@link #measure} sets: the
 * engine's own combat methods dereference it for a talent check ({@code Char.java:648-652},
 * {@code :668-672}) and throw without one, so a measurement runs with a hero fresh from the
 * game's own constructor. That hero has no class at all ({@code heroClass} is null until a Run
 * chooses one), no talent, no item, no ring and no buff, so every modifier those methods look up
 * through it is the identity; what it is not is a played hero, and a measurement that wanted one
 * would be measuring a Run. It is restored with the rest.
 *
 * <p>Setting an item's level to measure it writes one more Run static, the flag that tells a
 * scene to redraw its item displays ({@code Item.java:576-578}), so the door snapshots and
 * restores that too. The gate admits exactly these: the four {@code Dungeon} fields, the level's
 * and the hero's types, the hero's constructor, the redraw flag, and the two generator calls.
 */
final class GameContext {

    /** The generator a construction draws from: fixed, the Codex's, never a Run's. */
    static final long CODEX_GENERATOR_SEED = 0x5EEDL;

    /**
     * The generator a measurement draws from (story 2.5): a constant of its own, never
     * {@link #generatorSeed}, so that moving the Codex's construction seed cannot move a measured
     * number and the seed-free guarantee still holds over the measured tables.
     */
    static final long MEASUREMENT_SEED = 0xC0FFEEL;

    /**
     * The seed in use: the constant, except that {@code CodexSeedFreeTest} moves it between two
     * generations, so that a drawn value that reached a table would differ and be seen.
     */
    static long generatorSeed = CODEX_GENERATOR_SEED;

    private GameContext() {
    }

    /**
     * {@code work}'s result, measured at depth one under no challenge with a bare hero in place,
     * under a generator seeded from the Codex's seed and {@code key}, so that a measurement is
     * reproducible on its own and does not depend on the order the table was filled in.
     */
    static <T> T measure(long key, java.util.function.Function<Hero, T> work) {
        Hero hero = new Hero();
        Hero heroBefore = Dungeon.hero;
        boolean redrawBefore = GameScene.updateItemDisplays;
        Dungeon.hero = hero;
        try {
            return under(1, 0, MEASUREMENT_SEED + key * 0x9E3779B97F4A7C15L, () -> work.apply(hero));
        } finally {
            Dungeon.hero = heroBefore;
            GameScene.updateItemDisplays = redrawBefore;
        }
    }

    /** {@code make}'s result, constructed with the game at {@code depth} under {@code challenges}. */
    static <T> T under(int depth, int challenges, Supplier<T> make) {
        return under(depth, challenges, generatorSeed, make);
    }

    /** As {@link #under(int, int, Supplier)}, under a generator seeded by {@code seed}. */
    static <T> T under(int depth, int challenges, long seed, Supplier<T> make) {
        int depthBefore = Dungeon.depth;
        int challengesBefore = Dungeon.challenges;
        Level levelBefore = Dungeon.level;
        Dungeon.depth = depth;
        Dungeon.challenges = challenges;
        Dungeon.level = null;
        Random.pushGenerator(seed);
        try {
            return make.get();
        } finally {
            Random.popGenerator();
            Dungeon.depth = depthBefore;
            Dungeon.challenges = challengesBefore;
            Dungeon.level = levelBefore;
        }
    }
}
