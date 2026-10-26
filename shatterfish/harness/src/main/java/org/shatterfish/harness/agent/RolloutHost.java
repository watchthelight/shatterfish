package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
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
import java.util.Objects;

/**
 * The rollout host (story 6.2, ADR-0009, ADR-0010): the {@link Simulator} that plays Actions out in the live Run's own
 * process, on a scrubbed copy, and puts the live Run back.
 *
 * <p>A rollout is five steps, and the order is the contract:
 * <ol>
 *   <li>The handle is checked against the store's own record ({@link SnapshotStore#madeScrubbed}); a handle the store
 *       did not make as a scrubbed copy rolls out nothing, whatever it claims.</li>
 *   <li>The live Run is snapshotted at the wait it stands at, and the process-wide state a restore does not carry is
 *       held: {@link RunStatics.Held} for the game's statics, and a {@link ProfileGuard} for the profile's files, the
 *       journal, the badges and the rankings.</li>
 *   <li>The scrubbed copy is loaded into the same driver, which reseeds from the sample's rollout salt, and the Actions
 *       are applied through {@link ActionExecutor}, the only door an Action has into the game, with floor changes
 *       served the way {@link RunLoop} serves them. The new floor of a descent comes from the sample's seed.</li>
 *   <li>The Observation at each wait the rollout reaches is collected, with the same {@link Observer} the live Run is
 *       observed with.</li>
 *   <li>Whatever happened, the live snapshot is restored and stepped to its wait, then the statics and the profile are put back.</li>
 * </ol>
 *
 * <p>The host must be called at an Input wait of the driver it holds, from the thread that plays that driver.
 */
public final class RolloutHost extends Simulator {

    private final SnapshotStore store;
    private final HeadlessDriver driver;
    private final ActionExecutor executor = new ActionExecutor();

    public RolloutHost(SnapshotStore store, HeadlessDriver driver) {
        this.store = Objects.requireNonNull(store, "the store that made the scrubbed copies");
        this.driver = Objects.requireNonNull(driver, "the driver whose Run the rollout borrows");
    }

    @Override
    protected RolloutResult rollout(SnapshotHandle handle, List<Action> actions) {
        // The flag is a claim; the store holds the bytes and knows which copies it made (the Simulator's contract).
        if (!store.madeScrubbed(handle)) {
            throw new IllegalArgumentException("snapshot " + handle.id() + " was not made as a scrubbed copy by this host's store:"
                    + " nothing is rolled out from it (ADR-0009)");
        }
        SnapshotHandle live = store.take(driver);
        RunStatics.Held statics = RunStatics.capture();
        ProfileGuard profile = ProfileGuard.hold(driver);
        RuntimeException failure = null;
        RolloutResult result = null;
        try {
            store.restoreScrubbed(handle, driver);
            result = play(actions);
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            try {
                if (!driver.closed()) {
                    store.restore(live, driver);
                    // The Run stands at its wait again, as the caller left it: a restore leaves the game loaded and the wait
                    // still to be reached, and an Action handed over before it is reached is handed to a Run not yet there.
                    HeadlessDriver.Halt halt = driver.stepToInputWait(RunLoop.FRAME_BUDGET);
                    if (halt.reason() != HeadlessDriver.Reason.INPUT_WAIT || halt.waitIndex() != live.k()) {
                        throw new IllegalStateException("the live Run was put back and does not stand at its wait " + live.k() + ": " + halt);
                    }
                }
            } catch (RuntimeException e) {
                failure = add(failure, e);
            }
            store.drop(live);
            try {
                statics.restore();
            } catch (RuntimeException e) {
                failure = add(failure, e);
            }
            try {
                profile.restore();
            } catch (RuntimeException e) {
                failure = add(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private static RuntimeException add(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    /** Plays {@code actions} on the Run the driver now holds, which is the scrubbed copy at its wait. */
    private RolloutResult play(List<Action> actions) {
        List<Observation> observations = new ArrayList<>();
        int applied = 0;
        while (true) {
            HeadlessDriver.Halt halt = driver.stepToInputWait(RunLoop.FRAME_BUDGET);
            switch (halt.reason()) {
                case HERO_DEAD -> {
                    return new RolloutResult(observations, applied, RolloutEnd.DEATH);
                }
                case SCENE_SWITCH -> {
                    Class<?> asked = halt.requestedScene();
                    if (asked == SurfaceScene.class) {
                        return new RolloutResult(observations, applied, RolloutEnd.WIN);
                    }
                    InterlevelScene.Mode mode = InterlevelScene.mode;
                    if (asked != InterlevelScene.class || !(mode == InterlevelScene.Mode.DESCEND
                            || mode == InterlevelScene.Mode.ASCEND || mode == InterlevelScene.Mode.FALL)) {
                        // A scene the host cannot serve is the end of what it can say about this line.
                        return new RolloutResult(observations, applied, RolloutEnd.HORIZON);
                    }
                    driver.serveSceneSwitch(() -> RunLoop.crossFloor(mode));
                    continue;
                }
                default -> {
                    // An Input wait: the only case with an Action in it.
                }
            }
            Observation observation = new Observer().observe();
            observations.add(observation);
            if (applied == actions.size()) {
                return new RolloutResult(observations, applied, RolloutEnd.HORIZON);
            }
            Outcome outcome = executor.execute(observation, actions.get(applied));
            if (outcome instanceof Outcome.Rejected) {
                return new RolloutResult(observations, applied, RolloutEnd.REFUSED);
            }
            applied++;
        }
    }
}
