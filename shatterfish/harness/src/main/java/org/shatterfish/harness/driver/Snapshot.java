package org.shatterfish.harness.driver;

import org.shatterfish.api.Emote;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.Observation;

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
    private final Observation observation;
    private final boolean[] fov;
    private final boolean scrubbed;
    private final long activeSalt;

    /** A snapshot of a Run's own, taken without the screen it showed (the tests that build one by hand). */
    Snapshot(String id, long k, long seed, String heroClass, long salt, int slot, Map<String, byte[]> files, List<LogLine> log,
             Map<Integer, Emote> emotes) {
        this(id, k, seed, heroClass, salt, slot, files, log, emotes, null, null, false, salt);
    }

    /**
     * The full form. {@code observation} and {@code fov} are what the screen showed at the wait and the cells the hero
     * saw, which the scrubber needs to rebuild a copy the screen cannot tell from the Run; {@code scrubbed} marks a
     * copy the scrubber made, whose {@code activeSalt} is the sample's, while {@code salt} stays the Run's declared
     * one so a restore into another Run is still refused.
     */
    Snapshot(String id, long k, long seed, String heroClass, long salt, int slot, Map<String, byte[]> files, List<LogLine> log,
             Map<Integer, Emote> emotes, Observation observation, boolean[] fov, boolean scrubbed, long activeSalt) {
        this.observation = observation;
        this.fov = fov == null ? null : fov.clone();
        this.scrubbed = scrubbed;
        this.activeSalt = activeSalt;
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

    /** The Observation the Run showed at the wait, or null for a snapshot built without one. */
    Observation observation() {
        return observation;
    }

    /** The cells in the hero's field of view at the wait, or null for a snapshot built without them. */
    boolean[] fov() {
        return fov == null ? null : fov.clone();
    }

    /** Whether the scrubber made this snapshot: a copy to roll out in, never the Run's own. */
    boolean scrubbed() {
        return scrubbed;
    }

    /** The salt the waits after a restore draw from: the Run's own, or a scrubbed copy's, the sample's. */
    long activeSalt() {
        return activeSalt;
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
