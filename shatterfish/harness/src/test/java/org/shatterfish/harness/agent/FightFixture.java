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

    /** Plays {@code seed} to wait {@code at}, which must show an enemy; the driver is the caller's to close. */
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
                if (halt.waitIndex() >= at) {
                    if (observation.actors().actors().stream().noneMatch(a -> a.alignment() == Alignment.ENEMY)) {
                        throw new AssertionError("wait " + at + " is not mid-fight");
                    }
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
