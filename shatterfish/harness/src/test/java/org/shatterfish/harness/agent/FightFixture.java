package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Observation;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.observer.Observer;

/**
 * A Run played by the real Brain to a named wait that stands in a fight, for tests of other packages that need one
 * (story 10.24). The seeds and waits are the ones the isolation tool found mid-fight on the standard set.
 */
public final class FightFixture {

    private FightFixture() {
    }

    /** Plays {@code seed} to the first wait from {@code at} on that shows an enemy; the driver is the caller's to close. */
    public static HeadlessDriver playTo(long seed, HeroClass heroClass, long salt, long at) {
        HeadlessDriver driver = HeadlessDriver.start(seed, heroClass, salt);
        BrainDecider brain = RealBrain.decider();
        ActionExecutor executor = new ActionExecutor();
        try {
            for (int i = 0; i < 3_000; i++) {
                HeadlessDriver.Halt halt = driver.stepToInputWait();
                if (halt.reason() == HeadlessDriver.Reason.SCENE_SWITCH) {
                    InterlevelScene.Mode mode = InterlevelScene.mode;
                    driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                    continue;
                }
                if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT) {
                    throw new AssertionError("the Run ended (" + halt.reason() + ") before wait " + at);
                }
                Observation observation = new Observer().observe();
                // The first wait from {@code at} on that shows an enemy: a change to the Brain moves where on
                // the Run a given wait index falls, and the cases need a fight, not that exact wait (#409).
                if (halt.waitIndex() >= at
                        && observation.actors().actors().stream().anyMatch(a -> a.alignment() == Alignment.ENEMY)) {
                    return driver;
                }
                executor.execute(observation, brain.decide(observation));
            }
            throw new AssertionError("wait " + at + " not reached");
        } catch (RuntimeException | Error failed) {
            driver.close();
            throw failed;
        }
    }
}
