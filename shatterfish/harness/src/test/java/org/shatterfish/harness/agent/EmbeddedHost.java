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
        return driver.headlessBoot().pendingRunnables();
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
                driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                // The frame the loading scene takes; the Run sees the new floor from the next one,
                // as the headless loop steps before it looks again.
                return run.state();
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
     * Asks the game for the floor below, the way taking the stairs does: the transition the hero
     * takes, the loading scene's mode, and the scene change ({@code …/levels/Level.java} activating a
     * transition; {@code SPD-classes/…/noosa/Game.java:212-220}).
     */
    static void askForTheFloorBelow() {
        InterlevelScene.curTransition = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT);
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
        Game.switchScene(InterlevelScene.class);
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
