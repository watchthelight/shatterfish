package org.shatterfish.harness.driver;

import org.shatterfish.api.Emote;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.SnapshotHandle;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an isolated rollout world is built from (E10 spike, ADR-0025): the files of a scrubbed copy and the few things a
 * load needs beside them, and nothing else. It is the one thing that crosses from the live Run's class loader into an
 * isolated one, so what it can hold is the whole answer to "can the rollout world be built from the live world's hidden
 * fields".
 *
 * <ul>
 * <li><b>One door in.</b> The constructor is private and the only factory, {@link #from}, takes a store and a handle and
 * refuses unless the store made that handle as a scrubbed copy with every row of the table applied, the check every
 * {@code Simulator} makes ({@link SnapshotStore#madeScrubbed}). No overload takes a live snapshot, a driver, a game
 * object or bytes, so there is no way to write a start from the live Run's own files. The isolated loader delegates this
 * class by name to the live one rather than defining a copy, so the isolated world cannot construct one either, and the
 * accessors are public only because the isolated world reads through them.</li>
 * <li><b>Nothing of the Run's tuple.</b> Not the seed, not the declared salt: those stay in the live {@link Snapshot}.
 * The salt carried is the sample's ({@link Snapshot#activeSalt}), which the scrub chose, and the seed the game saves in
 * the files is the sample's too (the scrub table's {@code Dungeon.seed} row).</li>
 * <li><b>Only what the screen already showed.</b> The log lines and the shown emotes are Observation content.</li>
 * </ul>
 */
public final class ScrubbedStart {

    private final String id;
    private final String heroClass;
    private final long k;
    private final int slot;
    private final long activeSalt;
    private final Map<String, byte[]> files;
    private final List<LogLine> log;
    private final Map<Integer, Emote> emotes;

    private ScrubbedStart(Snapshot copy) {
        this.id = copy.id();
        this.heroClass = copy.heroClass();
        this.k = copy.k();
        this.slot = copy.slot();
        this.activeSalt = copy.activeSalt();
        Map<String, byte[]> own = new java.util.LinkedHashMap<>();
        copy.files().forEach((name, bytes) -> own.put(name, bytes.clone()));
        this.files = java.util.Collections.unmodifiableMap(own);
        this.log = copy.log();
        this.emotes = copy.emotes();
    }

    /** The start for {@code handle}, which {@code store} must have made as a complete scrubbed copy. */
    public static ScrubbedStart from(SnapshotStore store, SnapshotHandle handle) {
        Objects.requireNonNull(store, "the store that made the scrubbed copy");
        Objects.requireNonNull(handle, "a snapshot handle");
        if (!store.madeScrubbed(handle)) {
            throw new IllegalArgumentException("snapshot " + handle.id() + " was not made as a complete scrubbed copy by this store:"
                    + " an isolated world is built from nothing else (ADR-0025)");
        }
        return new ScrubbedStart(store.find(handle.id()));
    }

    /** The name of the copy, which carries no more than the store's id. */
    public String id() {
        return id;
    }

    /** The hero class, which the screen shows. */
    public String heroClass() {
        return heroClass;
    }

    /** The wait the copy stands at. */
    public long k() {
        return k;
    }

    public int slot() {
        return slot;
    }

    /** The sample's salt, which the waits of the copy draw from. */
    public long activeSalt() {
        return activeSalt;
    }

    /** The scrubbed files of the save folder, by name. */
    public Map<String, byte[]> files() {
        Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        files.forEach((name, bytes) -> out.put(name, bytes.clone()));
        return out;
    }

    public List<LogLine> log() {
        return log;
    }

    public Map<Integer, Emote> emotes() {
        return emotes;
    }

    @Override
    public String toString() {
        return "ScrubbedStart[" + id + " at wait " + k + "]";
    }
}
