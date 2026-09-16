package org.shatterfish.harness.driver;

import org.shatterfish.api.Emote;
import org.shatterfish.api.LogLine;

import java.util.List;
import java.util.Map;

/**
 * A Run's exact state at an Input wait, as the game itself saves it (ADR-0009): every file of the
 * game's own save folder, written by {@code Dungeon.saveAll} and read back by {@code loadGame} and
 * {@code loadLevel}, held in memory; the wait it was taken at, the salt, the slot the game is in,
 * and the lines the log listener held, which the load's own lines would otherwise replace. It is
 * package-private on purpose and stays so: these bytes are the hidden state of the Run, and
 * nothing outside this package, {@code api} least of all, may hold them ({@code SnapshotBoundaryTest}).
 * A Brain sees a {@link org.shatterfish.api.SnapshotHandle}, which is an id.
 */
final class Snapshot {

    private final String id;
    private final long k;
    private final long seed;
    private final String heroClass;
    private final long salt;
    private final int slot;
    private final Map<String, byte[]> files;
    private final List<LogLine> log;
    private final Map<Integer, Emote> emotes;

    Snapshot(String id, long k, long seed, String heroClass, long salt, int slot, Map<String, byte[]> files, List<LogLine> log,
             Map<Integer, Emote> emotes) {
        this.id = id;
        this.k = k;
        this.seed = seed;
        this.heroClass = heroClass;
        this.salt = salt;
        this.slot = slot;
        this.files = Map.copyOf(files);
        this.log = List.copyOf(log);
        this.emotes = Map.copyOf(emotes);
    }

    /**
     * The emote each mob's sprite showed at the wait, by actor id, for the ones that showed one:
     * the sprite's icons live on the sprite and not in the bundle, so the load would draw none.
     */
    Map<Integer, Emote> emotes() {
        return emotes;
    }

    String id() {
        return id;
    }

    long k() {
        return k;
    }

    /** The Run's seed, so that a restore into a Run of another tuple is refused. */
    long seed() {
        return seed;
    }

    /** The Run's hero class, by name, for the same refusal. */
    String heroClass() {
        return heroClass;
    }

    long salt() {
        return salt;
    }

    int slot() {
        return slot;
    }

    /** The save folder's files by name, as the game wrote them. */
    Map<String, byte[]> files() {
        return files;
    }

    /** The log listener's lines at the wait. */
    List<LogLine> log() {
        return log;
    }

    @Override
    public String toString() {
        return "Snapshot[" + id + " at wait " + k + ", " + files.size() + " file(s)]";
    }
}
