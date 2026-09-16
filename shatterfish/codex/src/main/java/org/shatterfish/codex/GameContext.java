package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
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
 */
final class GameContext {

    /** The generator a construction draws from: fixed, the Codex's, never a Run's. */
    static final long CODEX_GENERATOR_SEED = 0x5EEDL;

    /**
     * The seed in use: the constant, except that {@code CodexSeedFreeTest} moves it between two
     * generations, so that a drawn value that reached a table would differ and be seen.
     */
    static long generatorSeed = CODEX_GENERATOR_SEED;

    private GameContext() {
    }

    /** {@code make}'s result, constructed with the game at {@code depth} under {@code challenges}. */
    static <T> T under(int depth, int challenges, Supplier<T> make) {
        int depthBefore = Dungeon.depth;
        int challengesBefore = Dungeon.challenges;
        Level levelBefore = Dungeon.level;
        Dungeon.depth = depth;
        Dungeon.challenges = challenges;
        Dungeon.level = null;
        Random.pushGenerator(generatorSeed);
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
