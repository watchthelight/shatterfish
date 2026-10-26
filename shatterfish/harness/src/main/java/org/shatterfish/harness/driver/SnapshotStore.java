package org.shatterfish.harness.driver;

import org.shatterfish.api.SnapshotHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Takes and restores snapshots of the Run a driver plays (ADR-0009): {@link #take} writes the
 * game's own save and keeps its files in memory under an opaque id, and {@link #restore} puts
 * them back through the game's own load, into the same driver, which reseeds at the first wait as
 * ADR-0007 already does. {@link #take} hands out the live Run's own, unscrubbed; the one scrubbed handle
 * this store holds are the ones {@link SnapshotScrubber} {@link #adopt}s, and {@link #restoreScrubbed}
 * accepts only those, by id and by the store's own record, never by the flag a handle carries. Snapshots live in memory; the game's save in the Run's profile is the only
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
        if (handle.scrubbed() || snapshot.scrubbed()) {
            throw new IllegalArgumentException("restore puts back the live Run's own snapshots, none of them scrubbed; "
                    + handle.id() + " is a scrubbed copy or says scrubbed, which restore does not take (restoreScrubbed does)");
        }
        if (handle.k() != snapshot.k()) {
            throw new IllegalArgumentException(handle.id() + " names wait " + handle.k() + ", and the snapshot was taken at wait "
                    + snapshot.k());
        }
        driver.restore(snapshot);
    }

    /**
     * Puts the scrubbed copy {@code handle} names into {@code driver}, as a rollout's start. Only a copy this store
     * made is accepted: the handle's flag is a claim, and the store checks it against its own record, so a handle that
     * says scrubbed over a live snapshot, or over an id the store never held, is refused before the Run is touched.
     */
    public void restoreScrubbed(SnapshotHandle handle, HeadlessDriver driver) {
        Objects.requireNonNull(handle, "a snapshot handle");
        Objects.requireNonNull(driver, "a driver");
        Snapshot snapshot = snapshots.get(handle.id());
        if (snapshot == null || !snapshot.scrubbed()) {
            throw new IllegalArgumentException("no scrubbed snapshot named " + handle.id() + " was made by this store; it made "
                    + snapshots.values().stream().filter(Snapshot::scrubbed).map(Snapshot::id).toList());
        }
        if (!handle.scrubbed()) {
            throw new IllegalArgumentException(handle.id() + " is a scrubbed copy and the handle does not say so");
        }
        if (handle.k() != snapshot.k()) {
            throw new IllegalArgumentException(handle.id() + " names wait " + handle.k() + ", and the snapshot was taken at wait "
                    + snapshot.k());
        }
        driver.restore(snapshot);
    }

    /** Whether this store made {@code handle} as a scrubbed copy: the check a Simulator makes before it rolls out. */
    public boolean madeScrubbed(SnapshotHandle handle) {
        Objects.requireNonNull(handle, "a snapshot handle");
        Snapshot snapshot = snapshots.get(handle.id());
        return handle.scrubbed() && snapshot != null && snapshot.scrubbed() && snapshot.k() == handle.k();
    }

    /** The snapshot named {@code id}, or null. */
    Snapshot find(String id) {
        return snapshots.get(id);
    }

    /** Keeps a scrubbed copy the scrubber made, and returns its handle, which is the only way a handle comes to say scrubbed. */
    SnapshotHandle adopt(Snapshot copy) {
        if (!copy.scrubbed()) {
            throw new IllegalArgumentException("only a scrubbed copy is adopted: " + copy.id());
        }
        snapshots.put(copy.id(), copy);
        return new SnapshotHandle(copy.id(), copy.k(), true);
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
