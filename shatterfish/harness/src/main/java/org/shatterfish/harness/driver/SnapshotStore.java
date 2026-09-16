package org.shatterfish.harness.driver;

import org.shatterfish.api.SnapshotHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
        Objects.requireNonNull(driver, "a driver");
        // The id is the Run's and the store's, never the process's: the salt, the wait and this
        // store's count, so two processes of one tuple with one snapshot schedule name their
        // snapshots alike, and a snapshot of another Run cannot share a name.
        Snapshot snapshot = driver.snapshot("snapshot-" + Long.toHexString(driver.salt()) + "-k" + driver.waitIndex() + "-"
                + (++taken));
        SnapshotHandle handle = new SnapshotHandle(snapshot.id(), snapshot.k(), false);
        snapshots.put(snapshot.id(), snapshot);
        return handle;
    }

    /**
     * Puts the snapshot {@code handle} names back into {@code driver}, whose Run then stands at the
     * wait the snapshot was taken at, to be stepped to and observed as it was. A handle this store
     * does not know, one that says scrubbed, which no snapshot here is, or one whose wait is not
     * the snapshot's, is refused by name before the Run is touched.
     */
    public void restore(SnapshotHandle handle, HeadlessDriver driver) {
        Objects.requireNonNull(handle, "a snapshot handle");
        Objects.requireNonNull(driver, "a driver");
        Snapshot snapshot = snapshots.get(handle.id());
        if (snapshot == null) {
            throw new IllegalArgumentException("no snapshot named " + handle.id() + " in this store; it holds "
                    + snapshots.keySet());
        }
        if (handle.scrubbed()) {
            throw new IllegalArgumentException("this store holds the live Run's own snapshots, none of them scrubbed; "
                    + handle.id() + " says scrubbed, which is a claim this store cannot honour");
        }
        if (handle.k() != snapshot.k()) {
            throw new IllegalArgumentException(handle.id() + " names wait " + handle.k() + ", and the snapshot was taken at wait "
                    + snapshot.k());
        }
        driver.restore(snapshot);
    }

    /** Forgets one snapshot, so a store that takes one per wait need not grow without bound. */
    public void drop(SnapshotHandle handle) {
        Objects.requireNonNull(handle, "a snapshot handle");
        snapshots.remove(handle.id());
    }

    /** How many snapshots this store holds. */
    public int size() {
        return snapshots.size();
    }
}
