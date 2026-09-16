package org.shatterfish.harness.driver;

import org.shatterfish.api.SnapshotHandle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Takes and restores snapshots of the Run a driver plays (ADR-0009): {@link #take} writes the
 * game's own save and keeps its files in memory under an opaque id, and {@link #restore} puts
 * them back through the game's own load, into the same driver, which reseeds at the first wait as
 * ADR-0007 already does. Every handle this store hands out is unscrubbed, the live Run's own: the
 * scrubber that turns one into a rollout's is E6's, and no {@code Simulator} accepts a handle from
 * here until then. Snapshots live in memory; the game's save in the Run's profile is the only
 * thing written to disk, and only because the game reads its state back from files.
 */
public final class SnapshotStore {

    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();
    private int taken;

    public SnapshotStore() {
    }

    /** A snapshot of {@code driver}'s Run at the Input wait it is at; the Run goes on unchanged. */
    public SnapshotHandle take(HeadlessDriver driver) {
        Snapshot snapshot = driver.snapshot("snapshot-" + (++taken));
        snapshots.put(snapshot.id(), snapshot);
        return new SnapshotHandle(snapshot.id(), snapshot.k(), false);
    }

    /**
     * Puts the snapshot {@code handle} names back into {@code driver}, whose Run then stands at the
     * wait the snapshot was taken at, to be stepped to and observed as it was.
     */
    public void restore(SnapshotHandle handle, HeadlessDriver driver) {
        Snapshot snapshot = snapshots.get(handle.id());
        if (snapshot == null) {
            throw new IllegalArgumentException("no snapshot named " + handle.id() + " in this store; it holds "
                    + snapshots.keySet());
        }
        driver.restore(snapshot);
    }

    /** How many snapshots this store holds. */
    public int size() {
        return snapshots.size();
    }
}
