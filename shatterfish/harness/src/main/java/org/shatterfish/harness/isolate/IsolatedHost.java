package org.shatterfish.harness.isolate;

import org.shatterfish.api.Action;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.Simulator;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.ScrubbedStart;
import org.shatterfish.harness.driver.SnapshotStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * The rollout host that does not swap (E10 spike, ADR-0024): a {@link Simulator} that plays a scrubbed copy out in an
 * isolated copy of the game, in the live Run's own process, and leaves the live Run exactly where it stood. It is the
 * swap-in-place {@code RolloutHost}'s sibling and obeys the same contract: the handle is checked against the store's own
 * record ({@link SnapshotStore#madeScrubbed}), so a handle the store did not make as a complete scrubbed copy rolls out
 * nothing whatever it claims, and what comes back is Observations already made.
 *
 * <p>Two modes, because the spike measures both. {@link Mode#POOLED} keeps one world per hero class and reuses it, so the
 * cost of opening a world (a class loader, the classes a Run touches, the boot, a first floor) is paid once and the JIT
 * warms once. {@link Mode#FRESH} opens a world per rollout and drops it, the cleanest isolation and the dearest.
 *
 * <p>Called from the thread that plays the live Run, at an Input wait of it, and from no other: the worlds claim that thread
 * as their UI-role thread and share the libGDX queue with the live Run (see {@code IsolatedWorld}).
 */
public final class IsolatedHost extends Simulator implements AutoCloseable {

    /** How worlds are kept. */
    public enum Mode { POOLED, FRESH }

    /** Where the host's costs are reported when somebody is measuring: durations only, handed nothing from the game. */
    public interface Phases {
        /** A world opened: its class loader, boot, Run start, and first floor. */
        void opened(long nanos);

        /** One rollout: holding the profile, loading the copy, playing, and putting the baseline back. */
        void rolled(long hold, long load, long play, long restore);

        /** The parts of the last rollout's load, in nanoseconds: end the scene, write files, load game and floor, build the scene, step to the wait. */
        default void loadParts(long endScene, long files, long game, long scene, long step) {
        }
    }

    private final SnapshotStore store;
    private final Mode mode;
    private final Map<String, Opened> pool = new HashMap<>();
    private Phases phases;

    /** A world and the loader that defines it. */
    private record Opened(IsolationLoader loader, RolloutWorld world) {
    }

    public IsolatedHost(SnapshotStore store, Mode mode) {
        this.store = Objects.requireNonNull(store, "the store that made the scrubbed copies");
        this.mode = Objects.requireNonNull(mode, "a mode");
    }

    public IsolatedHost timedBy(Phases listener) {
        this.phases = listener;
        return this;
    }

    @Override
    protected RolloutResult rollout(SnapshotHandle handle, List<Action> actions) {
        // The flag is a claim; the store holds the bytes and knows which copies it made (the Simulator's contract).
        if (!store.madeScrubbed(handle)) {
            throw new IllegalArgumentException("snapshot " + handle.id() + " was not made as a scrubbed copy by this host's store:"
                    + " nothing is rolled out from it (ADR-0009, ADR-0024)");
        }
        ScrubbedStart start = ScrubbedStart.from(store, handle);
        Opened opened = mode == Mode.POOLED ? pool.get(start.heroClass()) : null;
        if (opened == null) {
            opened = open(start.heroClass());
            if (mode == Mode.POOLED) {
                pool.put(start.heroClass(), opened);
            }
        }
        boolean keep = mode == Mode.POOLED;
        try {
            RolloutResult result = opened.world().rollout(start, actions);
            Phases timer = phases;
            if (timer != null) {
                long[] c = opened.world().lastCostsNanos();
                timer.rolled(c[0], c[1], c[2], c[3]);
                timer.loadParts(c[4], c[5], c[6], c[7], c[8]);
            }
            return result;
        } catch (RuntimeException | Error failed) {
            // A world that failed mid-rollout is not trusted with the next one.
            keep = false;
            pool.remove(start.heroClass(), opened);
            throw failed;
        } finally {
            if (!keep) {
                opened.world().close();
            }
        }
    }

    private Opened open(String heroClass) {
        long t0 = System.nanoTime();
        IsolationLoader loader = new IsolationLoader(IsolatedHost.class.getClassLoader());
        WorldFactory factory = ServiceLoader.load(WorldFactory.class, loader).findFirst()
                .orElseThrow(() -> new IllegalStateException("no WorldFactory is registered for the isolated loader"));
        RolloutWorld world = factory.open(heroClass);
        Phases timer = phases;
        if (timer != null) {
            timer.opened(System.nanoTime() - t0);
        }
        return new Opened(loader, world);
    }

    /** How many worlds this host keeps open. */
    public int openWorlds() {
        return pool.size();
    }

    /** The defined-class count of the pooled world for {@code heroClass}, or 0. */
    public long definedClasses(String heroClass) {
        Opened opened = pool.get(heroClass);
        return opened == null ? 0 : opened.loader().definedClasses();
    }

    @Override
    public void close() {
        for (Opened opened : pool.values()) {
            opened.world().close();
        }
        pool.clear();
    }
}
