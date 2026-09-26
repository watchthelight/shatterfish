package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.shatterfish.harness.observer.GameLogListener;
import org.shatterfish.harness.rng.RngControl;

/**
 * How a Run's game begins, for both drivers: the path a player takes from typing a seed into the
 * custom-seed window to pressing start ({@code core/.../scenes/HeroSelectScene.java:157-162}), and
 * then what the loading scene does for a game with no hero yet
 * ({@code core/.../scenes/InterlevelScene.java:622-649}). Nothing is hand-placed and nothing is
 * skipped, and no scene is created: the headless driver creates its own, and the Overlay's launcher
 * asks the real game for its play scene.
 *
 * <p>This was the body of {@code HeadlessDriver.newGame} until story 5.1, which needed the Overlay to
 * begin a Run through the same statements rather than a copy of them: a Run begun two ways would be
 * two games with one tuple. What stayed behind in the headless driver is what only a headless
 * process has to check, the boot and its queue, and the headless Profile.
 *
 * <p>The caller has prepared the Run's Profile first (ADR-0007), so the files this reads and writes
 * are the Run's own, and claims the UI role, since this is game state written on the UI-role thread.
 */
public final class NewGame {

    private NewGame() {
    }

    /**
     * Begins a new game of {@code heroClass} on {@code seed}, whose generator stack {@code rng} owns
     * from {@code Dungeon.init} onward (ADR-0007), up to and including its first floor.
     *
     * <p>The game's own init ends by replacing the base generator with an unseeded one
     * ({@code core/.../Dungeon.java:254}), and anything drawn afterwards that is not inside one of the
     * game's own seeded pushes falls through to it. Level layout is pushed and seeded by the game
     * ({@code core/.../levels/Level.java:221}), but not everything the floor decides is, so without
     * {@code rng} the same tuple puts the same item on a different cell in every Run, which is what
     * story 1.15's own determinism test found.
     */
    public static void begin(long seed, HeroClass heroClass, RngControl rng) {
        if (heroClass == null) {
            throw new IllegalArgumentException("a Run needs a hero class");
        }
        if (rng == null) {
            throw new IllegalArgumentException("a Run's generator stack is its own (ADR-0007)");
        }
        String seedCode = DungeonSeed.convertToCode(seed);
        // The game clears this only when the hero falls (Chasm.java:101); a Run closed between a
        // confirmed jump and the fall would otherwise jump unasked in this one.
        Chasm.jumpConfirmed = false;

        // What the hero-select screen loads before any game starts (HeroSelectScene.java:106-107).
        // Both load once per process; story 1.15 owns what a Profile is and when it is reloaded.
        Badges.loadGlobal();
        Journal.loadGlobal();

        // HeroSelectScene.java:157-162, the start button, with the seed typed into the seed window
        // and the class and the slot chosen on the screens before it.
        SPDSettings.customSeed(seedCode);
        GamesInProgress.selectedClass = heroClass;
        GamesInProgress.curSlot = GamesInProgress.firstEmpty();
        if (GamesInProgress.curSlot < 1) {
            throw new IllegalStateException("no free save slot: every slot this process has seen is occupied");
        }
        Dungeon.hero = null;
        Dungeon.daily = Dungeon.dailyReplay = false;
        Dungeon.initSeed();
        ActionIndicator.clearAction();
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;

        // InterlevelScene.java:622-649, descend() with no hero: a new game and its first floor.
        Mob.clearHeldAllies();
        Dungeon.init();
        // The stack is the harness's from here: init has just thrown away every generator, base
        // included, and replaced the base with one seeded from the system (Dungeon.java:254). Wait
        // zero's generator goes on top before the first floor is built, so the floor is the tuple's
        // and not the moment's.
        rng.reseed(0);
        GameLog.wipe();
        GameLogListener.INSTANCE.reset();
        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, -1);
    }
}
