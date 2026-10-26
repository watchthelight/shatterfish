package org.shatterfish.harness.driver;

import org.shatterfish.api.SnapshotHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

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
        // The id is a count and nothing else. The handle reaches the Brain, and so do the messages that name it; the
        // Run's salt predicts every roll of the Run (ADR-0007), so no id, handle or message carries it.
        Snapshot snapshot = driver.snapshot("snapshot-" + (++taken));
        SnapshotHandle handle = new SnapshotHandle(snapshot.id(), snapshot.k(), false);
        snapshots.put(snapshot.id(), snapshot);
        return handle;
    }

    /**
     * Puts the snapshot {@code handle} names back into {@code driver}, whose Run then stands at the
     * wait the snapshot was taken at, to be stepped to and observed as it was. A handle this store
     * does not know, one that says scrubbed, which no snapshot here is, or one whose wait is not
     * the snapshot's, is refused by name before the Run is touched.
     *
     * <p>The restore carries the game's save and nothing else: the profile (the journal, the badges, the rankings) and the
     * statics {@link RunStatics} names are the process's, and the game reads some of them as it loads a floor (a guide
     * page the journal has found is dropped from the floor, {@code core/.../items/Heap.java:439-449}). A caller that plays
     * on after a snapshot and restores it holds a {@link ProfileGuard} and puts it back before this call, as the rollout
     * host does, and may check the result with {@link #verifyRestored} (issue #271).
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

    /**
     * Checks that the Run {@code driver} stands in is the one the live snapshot {@code handle} names: the Run is saved
     * again, as {@link #take} saves it, and every file of that save is compared with the snapshot's, and the screen it
     * shows with the screen the snapshot showed. It does not matter which state a restore missed (a static, a field the
     * game does not save, a file read from the profile as a floor loads, issue #271): the save and the screen either match
     * or they do not. The saves are compared as {@link SaveForm} writes them, which leaves out the save's wall-clock time and
     * the actor-id counter and puts the game's hash-ordered sets in order, and nothing else. Call it at the wait the
     * restore was stepped to, after the profile is as it was when the snapshot was taken.
     *
     * <p>The Run goes on unchanged (a save is what the game does at every floor change). The exception names the files
     * that differ and never their bytes: the bytes are the Run's hidden state and stay in this package.
     *
     * @throws IllegalStateException if anything differs
     */
    public void verifyRestored(SnapshotHandle handle, HeadlessDriver driver) {
        Objects.requireNonNull(handle, "a snapshot handle");
        Objects.requireNonNull(driver, "a driver");
        Snapshot was = snapshots.get(handle.id());
        if (was == null || was.scrubbed() || handle.scrubbed()) {
            throw new IllegalArgumentException("verifyRestored compares with the live Run's own snapshot; " + handle.id()
                    + " is not one this store holds");
        }
        Snapshot now = driver.snapshot("verify-" + handle.id());
        if (now.k() != was.k()) {
            throw new IllegalStateException("the Run stands at wait " + now.k() + " and the snapshot was taken at wait " + was.k());
        }
        TreeSet<String> names = new TreeSet<>(was.files().keySet());
        names.addAll(now.files().keySet());
        List<String> differ = new ArrayList<>();
        for (String name : names) {
            byte[] one = was.files().get(name);
            byte[] other = now.files().get(name);
            // Most files are equal byte for byte; the parse is for the ones that are not.
            if (!Arrays.equals(one, other) && !Objects.equals(SaveForm.of(one), SaveForm.of(other))) {
                differ.add(name);
            }
        }
        if (was.observation() != null && !was.observation().equals(now.observation())) {
            differ.add("(the screen)");
        }
        if (!differ.isEmpty()) {
            throw new IllegalStateException("the live Run was put back and is not the Run the snapshot " + handle.id()
                    + " was taken of; it differs in " + differ + " (issue #271)");
        }
    }

    /**
     * Whether this store made {@code handle} as a scrubbed copy with every row of the table applied: the check a Simulator
     * makes before it rolls out. A copy made with fewer rows (the differential test's) restores, and is never rolled out.
     */
    public boolean madeScrubbed(SnapshotHandle handle) {
        Objects.requireNonNull(handle, "a snapshot handle");
        Snapshot snapshot = snapshots.get(handle.id());
        return handle.scrubbed() && snapshot != null && snapshot.scrubbed() && snapshot.complete() && snapshot.k() == handle.k();
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
