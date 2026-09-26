package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import org.shatterfish.api.Decider;
import org.shatterfish.harness.boot.HeadlessGame;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

/**
 * The owner of the loop, played by a test: what the render thread is to the Overlay, a headless
 * scene is here (story 5.1).
 *
 * <p>A test cannot open a window, so the embedded Run is attached to the headless game, and this
 * class does the render thread's part: once per frame it steps the scene, or serves the floor change
 * the game asked for the way the loading scene serves it ({@code RunLoop.crossFloor}, the mirrored
 * body of {@code InterlevelScene}), and then hands the Run its frame. The Run itself cannot tell the
 * difference, which is the point: it asks {@code Game} for the scene and the switch, the gate for the
 * wait, and this host only for the render queue and the scene asked for.
 *
 * <p>Two ways to spend the frames the Brain thinks through. {@link #stepWhileThinking} false is the
 * headless loop's timing, the decision made within its frame, which is what the determinism test
 * compares against a Rig Run; true is the desktop's, where the frames go on while the worker thinks,
 * which is what the threading test measures.
 */
final class EmbeddedHost implements EmbeddedRun.Host, AutoCloseable {

    final HeadlessDriver driver;
    final long seed;
    final HeroClass heroClass;
    EmbeddedRun run;
    boolean stepWhileThinking;
    /** Frames the scene was stepped while the Run was thinking. */
    long framesWhileThinking;
    /** Runnables the host pretends are queued, as the desktop's queue can hold some across frames. */
    int heldQueue;
    /** Loading-scene frames served: one per floor change the game asked for. */
    int loadingFrames;

    EmbeddedHost(long seed, HeroClass heroClass, long salt) {
        this.seed = seed;
        this.heroClass = heroClass;
        this.driver = HeadlessDriver.start(seed, heroClass, salt);
    }

    EmbeddedRun attach(Decider brain, RunLoop.Logging logging, int turnCap) {
        run = EmbeddedRun.attach(this, seed, heroClass, driver.rngControl(), brain, () -> new Observer().observe(),
                logging, turnCap);
        return run;
    }

    @Override
    public int pendingRunnables() {
        return driver.headlessBoot().pendingRunnables() + heldQueue;
    }

    @Override
    public Class<? extends Scene> requestedScene() {
        return driver.headlessBoot().game().requestedSceneClass();
    }

    /** One frame, as the render thread gives one: the game's frame, then the Run's. */
    EmbeddedRun.State frame() {
        HeadlessGame game = driver.headlessBoot().game();
        boolean thinking = run.state() == EmbeddedRun.State.THINKING;
        if (!thinking || stepWhileThinking) {
            if (game.sceneSwitchRequested() && game.requestedSceneClass() == InterlevelScene.class
                    && servedByTheLoadingScene(InterlevelScene.mode)) {
                InterlevelScene.Mode mode = InterlevelScene.mode;
                // The loading scene's own frame: the floor's work, and then, as its fade ends, the
                // request for the play scene made from inside the frame (…/scenes/InterlevelScene.java:
                // 509, :523), which the Run sees before the play scene is served. The play scene is
                // built here at once; the request stays standing for this frame, as it does in the
                // desktop game until the next step() serves it.
                driver.serveSceneSwitch(() -> {
                    RunLoop.crossFloor(mode);
                    Game.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.class);
                });
                loadingFrames++;
                return run.frame();
            }
            if (game.sceneSwitchRequested()
                    && game.requestedSceneClass() == com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.class) {
                // The next step() serves the play scene the loading scene asked for; it is built.
                game.clearSceneSwitchRequest();
            }
            if (!game.sceneSwitchRequested()) {
                driver.step();
                if (thinking) {
                    framesWhileThinking++;
                }
            }
        }
        return run.frame();
    }

    private static boolean servedByTheLoadingScene(InterlevelScene.Mode mode) {
        return mode == InterlevelScene.Mode.DESCEND || mode == InterlevelScene.Mode.ASCEND
                || mode == InterlevelScene.Mode.FALL;
    }

    /** Plays frames until the Run ends or {@code maxFrames} have gone by; says how it stands. */
    EmbeddedRun.State play(long maxFrames) {
        EmbeddedRun.State state = run.state();
        for (long frame = 0; frame < maxFrames && state != EmbeddedRun.State.ENDED; frame++) {
            state = frame();
            if (state == EmbeddedRun.State.THINKING && !stepWhileThinking) {
                Thread.onSpinWait();
            }
        }
        return state;
    }

    /**
     * Puts the hero beside this floor's regular exit, at a moment the actor thread is parked, so that a
     * Brain that steps onto the exit takes the stairs itself, the way the game's own transition runs
     * (…/levels/Level.java activating it from the hero's act); returns the exit's cell.
     */
    static int standBesideTheExit() {
        int exit = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT).cell();
        for (int step : com.watabou.utils.PathFinder.NEIGHBOURS8) {
            int cell = exit + step;
            if (Dungeon.level.passable[cell] && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null
                    && Dungeon.level.getTransition(cell) == null) {
                com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero hero = Dungeon.hero;
                hero.pos = cell;
                hero.sprite.place(cell);
                Dungeon.level.occupyCell(hero);
                Dungeon.observe();
                com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.updateFog();
                return exit;
            }
        }
        throw new IllegalStateException("no open cell beside the exit at " + exit);
    }

    /**
     * Rebuilds the play scene on the same floor, as the desktop game does whenever its window changes
     * size ({@code SPD-classes/…/noosa/Game.java:136-141}): the scene is destroyed and a new one
     * created, and nothing about the floor or the hero changes.
     */
    void rebuildTheScene() {
        Game.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.class);
        driver.serveSceneSwitch(() -> {
        });
    }

    @Override
    public void close() {
        try {
            if (run != null) {
                run.close();
            }
        } finally {
            driver.close();
        }
    }
}
