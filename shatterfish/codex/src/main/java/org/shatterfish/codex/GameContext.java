package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;

import java.util.function.Supplier;

/**
 * The one door from the generator to the two Run statics a table is parameterised by (story
 * 2.2): the game's initialisers read {@code Dungeon.depth} and {@code Dungeon.challenges} to
 * scale a mob or to strengthen a boss, so a table "at depth d under challenge c" is read by
 * constructing under those values. This class sets them around one construction and restores
 * what was there, whatever it was, so that a generation inside a live Run leaves the Run as it
 * found it, which {@code CodexLeakTest} holds. No other generator class may name {@code Dungeon},
 * which the same test's gate holds by name.
 */
final class GameContext {

    private GameContext() {
    }

    /** {@code make}'s result, constructed with the game at {@code depth} under {@code challenges}. */
    static <T> T under(int depth, int challenges, Supplier<T> make) {
        int depthBefore = Dungeon.depth;
        int challengesBefore = Dungeon.challenges;
        Dungeon.depth = depth;
        Dungeon.challenges = challenges;
        try {
            return make.get();
        } finally {
            Dungeon.depth = depthBefore;
            Dungeon.challenges = challengesBefore;
        }
    }
}
