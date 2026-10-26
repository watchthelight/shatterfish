package org.shatterfish.harness.agent;

import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.Simulator;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.ProfileGuard;
import org.shatterfish.harness.driver.RunStatics;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * A rollout host without the host's refusals (story 6.3's negative controls): the planted leak of "a search handed the
 * live snapshot's handle with the scrubbed flag forged", and of a scrubber that left a row out, whose copy the real
 * {@link RolloutHost} refuses as incomplete. It believes the flag. A handle that names a scrubbed copy of the store's is
 * restored as one, whatever rows it ran; any other handle is restored as the live Run's own snapshot, which is the
 * forged claim the real host exists to refuse.
 *
 * <p>It stops at a floor change, which the controls' short rollouts do not reach. It exists only in the test source
 * set, so that no code a search could call can do what it does.
 */
final class LeakyHost extends Simulator {

    private final SnapshotStore store;
    private final HeadlessDriver driver;
    private final ActionExecutor executor = new ActionExecutor();

    LeakyHost(SnapshotStore store, HeadlessDriver driver) {
        this.store = store;
        this.driver = driver;
    }

    @Override
    protected RolloutResult rollout(SnapshotHandle handle, List<Action> actions) {
        SnapshotHandle live = store.take(driver);
        RunStatics.Held statics = RunStatics.capture();
        ProfileGuard profile = ProfileGuard.hold(driver);
        try {
            try {
                store.restoreScrubbed(handle, driver);
            } catch (IllegalArgumentException notScrubbed) {
                // The forged claim: the live Run's own snapshot, believed to be a scrubbed one.
                store.restore(new SnapshotHandle(handle.id(), handle.k(), false), driver);
            }
            return play(actions);
        } finally {
            store.restore(live, driver);
            HeadlessDriver.Halt halt = driver.stepToInputWait(RunLoop.FRAME_BUDGET);
            if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT || halt.waitIndex() != live.k()) {
                throw new IllegalStateException("the live Run was put back and does not stand at its wait " + live.k() + ": " + halt);
            }
            store.drop(live);
            statics.restore();
            profile.restore();
        }
    }

    private RolloutResult play(List<Action> actions) {
        List<Observation> observations = new ArrayList<>();
        int applied = 0;
        while (true) {
            HeadlessDriver.Halt halt = driver.stepToInputWait(RunLoop.FRAME_BUDGET);
            switch (halt.reason()) {
                case HERO_DEAD -> {
                    return new RolloutResult(observations, applied, RolloutEnd.DEATH);
                }
                case INPUT_WAIT -> {
                    // fall through to the Action below
                }
                default -> {
                    return new RolloutResult(observations, applied, RolloutEnd.HORIZON);
                }
            }
            Observation observation = new Observer().observe();
            observations.add(observation);
            if (applied == actions.size()) {
                return new RolloutResult(observations, applied, RolloutEnd.HORIZON);
            }
            if (executor.execute(observation, actions.get(applied)) instanceof Outcome.Rejected) {
                return new RolloutResult(observations, applied, RolloutEnd.REFUSED);
            }
            applied++;
        }
    }
}
