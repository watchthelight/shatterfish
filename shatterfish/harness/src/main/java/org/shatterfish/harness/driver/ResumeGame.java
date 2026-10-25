package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import org.shatterfish.harness.observer.GameLogListener;
import org.shatterfish.harness.rng.RngControl;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * How a suspended Run's game comes back, for both drivers (story 5.14, ADR-0013): the path a player takes
 * from the title screen's saved game to the play scene, through the game's own restore
 * ({@code core/.../scenes/InterlevelScene.java:733-747}, mode {@code CONTINUE}), and nothing else. It is
 * {@link NewGame}'s sibling: the same preparation of the process, then a load where that begins.
 *
 * <p>The caller has prepared the Run's Profile first, with the suspended save in it (ADR-0007), and no
 * scene exists yet: the Overlay asks the real game for its play scene afterwards, and a headless host
 * creates its own.
 *
 * <p>What the load does not bring back is what the game itself does not save, and a person who reloads
 * the save loses it too: the game log pane starts empty (the Observation's log section is the pane, so
 * the first Observation after a resume holds the game's "return to this depth" line and nothing older),
 * the sprites' emotes are drawn afresh, and a Brain starts with no Belief. The Run's wait index, salt
 * and log chain are the Run's, not the game's, and come back from the log's boundary record.
 */
public final class ResumeGame {

    private ResumeGame() {
    }

    /**
     * Loads the game saved in {@code slot} and its current floor, with {@code rng}'s generator for wait
     * {@code k} (the last wait the log's boundary names) in force while the load draws, as
     * {@code HeadlessDriver}'s restore of a snapshot does ({@code core/.../Dungeon.java:822},
     * {@code core/.../items/Generator.java:625-636}); the next wait's own reseed replaces it.
     */
    public static void load(int slot, RngControl rng, long k) {
        if (rng == null) {
            throw new IllegalArgumentException("a Run's generator stack is its own (ADR-0007)");
        }
        // The same sweep a new game begins with: statics an earlier Run in this process left behind.
        RunStatics.reset();
        java.util.Objects.requireNonNull(com.shatteredpixel.shatteredpixeldungeon.effects.particles.WindParticle.FACTORY);
        Badges.loadGlobal();
        Journal.loadGlobal();

        GamesInProgress.curSlot = slot;
        Dungeon.hero = null;
        Dungeon.daily = Dungeon.dailyReplay = false;
        ActionIndicator.clearAction();
        InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;
        rng.reseed(k);
        // InterlevelScene.restore(), line for line.
        Mob.clearHeldAllies();
        GameLog.wipe();
        GameLogListener.INSTANCE.reset();
        try {
            Dungeon.loadGame(slot);
            if (Dungeon.hero == null) {
                throw new IllegalStateException("the save in slot " + slot + " loaded no hero");
            }
            if (Dungeon.depth == -1) {
                Dungeon.depth = Statistics.deepestFloor;
                Dungeon.switchLevel(Dungeon.loadLevel(slot), -1);
            } else {
                Level level = Dungeon.loadLevel(slot);
                Dungeon.switchLevel(level, Dungeon.hero.pos);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the game saved in slot " + slot + " could not be loaded", e);
        }
    }
}
