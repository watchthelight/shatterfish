package org.shatterfish.harness.agent;

import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Hypothesis;
import org.shatterfish.api.Lookahead;
import org.shatterfish.api.Playout;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.SnapshotScrubber;
import org.shatterfish.harness.driver.SnapshotStore;
import org.shatterfish.harness.isolate.IsolatedHost;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link Lookahead} of the Rig (story 10.24, ADR-0025): a Brain's {@link Hypothesis} in, a scrubbed copy of the live
 * Run made from it, and a rollout of that copy in an isolated copy of the game out. It is the one place where a search
 * Brain's draws meet the harness's snapshots, and it is built so that the Brain gets nothing back but Observations.
 *
 * <p>One instance serves one Run. {@link #lend} is called at an Input wait of the Run before the Brain decides and
 * {@link #release} after, on the thread that plays the Run. The live snapshot is taken lazily, at the first
 * rollout of the wait (a wait the search declines costs nothing), and every scrubbed copy a wait made is dropped with it, so
 * nothing outlives the wait it was made for. A hypothesis asked twice in a wait is scrubbed once.
 *
 * <p>Every rollout goes through {@link IsolatedHost}, which refuses a handle this host's store did not make as a complete
 * scrubbed copy, and the host, not the Brain, names the hero class and the history the world is given. The swap host is not
 * used here: the isolated world does not snapshot or restore the live Run, so a search that runs in the Rig's Run cannot
 * leave the live Run changed.
 */
public final class HostLookahead implements Lookahead, AutoCloseable {

    /** The game's root package, which the Codex's class names leave out. */
    private static final String ROOT = "com.shatteredpixel.shatteredpixeldungeon.";

    private final HeadlessDriver driver;
    private final SnapshotStore store = new SnapshotStore();
    private final SnapshotScrubber scrubber = new SnapshotScrubber(store);
    private final IsolatedHost host = new IsolatedHost(store, IsolatedHost.Mode.POOLED);
    private SnapshotHandle live;
    private final Map<Hypothesis, SnapshotHandle> copies = new HashMap<>();
    private long rollouts;
    private long refused;
    private long nanos;

    public HostLookahead(HeadlessDriver driver) {
        this.driver = java.util.Objects.requireNonNull(driver, "the driver whose Run is searched");
    }

    /** This look-ahead, for the wait the driver stands at. */
    public Lookahead lend() {
        release();
        return this;
    }

    @Override
    public RolloutResult roll(Hypothesis hypothesis, Playout playout, int horizon) {
        java.util.Objects.requireNonNull(hypothesis, "a hypothesis");
        long t0 = System.nanoTime();
        try {
            SnapshotHandle copy;
            if (copies.containsKey(hypothesis)) {
                copy = copies.get(hypothesis);
            } else {
                copy = scrub(hypothesis);
                copies.put(hypothesis, copy);
            }
            if (copy == null) {
                return null;
            }
            rollouts++;
            return host.simulate(copy, playout, horizon);
        } finally {
            nanos += System.nanoTime() - t0;
        }
    }

    /** The scrubbed copy for {@code hypothesis}, or null when the scrub declines this Run at this wait. */
    private SnapshotHandle scrub(Hypothesis hypothesis) {
        if (live == null) {
            live = store.take(driver);
        }
        List<ScrubSample.Remembered> mobs = new ArrayList<>();
        for (Hypothesis.Remembered remembered : hypothesis.remembered()) {
            mobs.add(new ScrubSample.Remembered(ROOT + remembered.mobClass(), remembered.cell()));
        }
        BeliefSample sample = ScrubSample.of(hypothesis.dungeonSeed(), hypothesis.rolloutSalt(), hypothesis.draws())
                .withRemembered(mobs).toBelief();
        try {
            return scrubber.scrub(live, sample);
        } catch (Scrub.Refused declined) {
            // No sample: a refusal is never evidence about the hidden state (ADR-0009's amendment), and the Brain is told
            // only that there is none.
            refused++;
            return null;
        }
    }

    /** Drops the wait's snapshots. Safe at any time, and called by {@link #close}. */
    public void release() {
        for (SnapshotHandle copy : copies.values()) {
            if (copy != null) {
                store.drop(copy);
            }
        }
        copies.clear();
        if (live != null) {
            store.drop(live);
            live = null;
        }
    }

    /** How many rollouts this look-ahead has played. */
    public long rollouts() {
        return rollouts;
    }

    /** How many hypotheses the scrubber declined. */
    public long refused() {
        return refused;
    }

    /** The nanoseconds spent in {@link #roll}, scrubs included. */
    public long nanos() {
        return nanos;
    }

    @Override
    public void close() {
        release();
        host.close();
    }
}
