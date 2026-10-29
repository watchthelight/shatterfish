package org.shatterfish.harness.agent;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Action;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.SharedBackend;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.ProfileGuard;
import org.shatterfish.harness.driver.RunStatics;
import org.shatterfish.harness.driver.ScrubbedStart;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.isolate.RolloutWorld;

import java.util.List;
import java.util.Objects;

/**
 * One isolated copy of the game playing scrubbed rollouts (E10 spike, ADR-0025). It runs inside an
 * {@code IsolationLoader}, so the driver, the game's statics and the profile are this copy's, and the live Run, which
 * stands at its Input wait in another loader, is neither saved nor loaded nor touched: that is what the swap host spends
 * 87% of a rollout on.
 *
 * <p>A rollout here is the swap host's with the live half removed: the profile's files are held, the scrubbed copy is
 * loaded into the world's driver and stepped to its wait, {@link RolloutHost#play} plays the Actions through the
 * executor and the Observer of this copy, and the game's statics and the profile are put back to their baseline, the
 * values the world had when it was opened, so that a rollout is a function of its copy and its Actions and not of the
 * rollouts the world played before it. The world's tuple is a placeholder of its own; nothing of the live Run's tuple
 * reaches it ({@link ScrubbedStart}).
 *
 * <p>The libGDX queue of posted runnables is the one thing both loaders share. A rollout puts the live Run's queued
 * runnables aside, runs on an empty queue and drains what the world posted, and puts the live Run's back at the head, so
 * the live Run's next frame runs none of the world's and all of its own. That makes a world safe for one thread at a time,
 * taking turns with the live Run on the thread that plays it (the Rig's shape), and not for a render thread that keeps
 * drawing the live Run while a search thinks (the Overlay's); ADR-0025 records what the Overlay would need.
 */
final class IsolatedWorld implements RolloutWorld {

    /** The world's own tuple: any seed a player could type, and a salt nobody else uses. */
    private static final long PLACEHOLDER_SEED = 1_000_003L;
    private static final long PLACEHOLDER_SALT = 0x150_1A7EDL;

    private final HeadlessDriver driver;
    private final ActionExecutor executor = new ActionExecutor();
    private final RunStatics.Held baseline;
    private final long[] costs = new long[9];

    IsolatedWorld(String heroClass) {
        Objects.requireNonNull(heroClass, "a hero class");
        List<Runnable> held = stash();
        try {
            this.driver = HeadlessDriver.start(PLACEHOLDER_SEED, HeroClass.valueOf(heroClass), PLACEHOLDER_SALT);
            this.baseline = RunStatics.capture();
        } finally {
            unstash(held);
        }
    }

    @Override
    public RolloutResult rollout(ScrubbedStart start, List<Action> actions) {
        Objects.requireNonNull(start, "a scrubbed start");
        Objects.requireNonNull(actions, "the Actions to roll out");
        HeadlessBoot boot = HeadlessBoot.ensure();
        // What the live Run has queued is put aside for the rollout and back after it (see the class comment).
        List<Runnable> held = stash();
        long t0 = System.nanoTime();
        ProfileGuard profile = ProfileGuard.hold(driver);
        RuntimeException failure = null;
        RolloutResult result = null;
        long t1 = 0;
        long t2 = 0;
        long t3 = 0;
        long firstStep = 0;
        try {
            t1 = System.nanoTime();
            driver.loadScrubbed(start);
            long s0 = System.nanoTime();
            HeadlessDriver.Halt first = driver.stepToInputWait(RunLoop.FRAME_BUDGET);
            t2 = System.nanoTime();
            firstStep = t2 - s0;
            result = RolloutHost.play(driver, executor, actions, first);
            t3 = System.nanoTime();
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            if (t3 == 0) {
                t3 = System.nanoTime();
            }
            try {
                if (!driver.closed()) {
                    // The profile first, for the reason the swap host gives: the game reads it as it loads a floor.
                    profile.restore();
                    baseline.restore();
                }
                boot.drainPostedRunnables();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            unstash(held);
            long t4 = System.nanoTime();
            costs[0] = t1 - t0;
            costs[1] = t2 - t1;
            costs[2] = t3 - t2;
            costs[3] = t4 - t3;
            long[] parts = driver.lastRestoreCostsNanos();
            System.arraycopy(parts, 0, costs, 4, 4);
            costs[8] = firstStep;
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private static List<Runnable> stash() {
        return Gdx.app instanceof SharedBackend backend ? backend.takeQueued() : List.of();
    }

    private static void unstash(List<Runnable> held) {
        if (!held.isEmpty() && Gdx.app instanceof SharedBackend backend) {
            backend.putBack(held);
        }
    }

    @Override
    public long[] lastCostsNanos() {
        return costs.clone();
    }

    @Override
    public void close() {
        if (!driver.closed()) {
            driver.close();
        }
    }
}
