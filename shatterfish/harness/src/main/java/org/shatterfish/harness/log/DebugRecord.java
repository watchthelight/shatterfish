package org.shatterfish.harness.log;

import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.BuffView;
import org.shatterfish.api.EquipSlot;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.JsonWriter;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The debug record: a sidecar file the Rig keeps beside a Run's log so that a fight or a death can be
 * attributed from the files instead of inferred (issue #219, the ADR-0011 update of 2026-10-25).
 *
 * <p><b>Why this is not a parity leak.</b> It is written after each wait has been served and read by
 * nobody while a Run is played: the loop hands the Observation the Brain already decided on, and the
 * Action it chose, to this class and takes nothing back. No method here returns a value the loop
 * uses, the Brain's module cannot name this class (it sees {@code api} only), and no class of the
 * Observer or the api package depends on it; {@code DebugRecordIsolationTest} holds all three. The
 * wait lines are built from the Observation alone, so they hold nothing a player at that screen could
 * not see (health as drawn pips, the water a character is drawn standing in, the log, the pack). The
 * one read of the game is the killer in the end line, taken after the Run is over.
 *
 * <p><b>Why it is a file of its own.</b> The Run log holds hashes only and its hash chain is what a
 * Replay and the determinism tests compare, so a field added there would change every published
 * chain. This file is outside the chain, outside {@code *.jsonl} (so nothing that lists a folder of
 * Run logs picks it up), is never read back by the harness, and is absent from a Run played without a
 * log. It is named for its Run: {@code <run-id>.debug.ndjson}.
 *
 * <p><b>Which waits.</b> A wait is written when a hostile character is in view, when the log gained a
 * line since the last wait written, or when the floor is a boss floor (depth a multiple of five);
 * other waits are walking and nothing in them is a fight or a death. Each line is complete on disk
 * before the next is built, like the Run log, so a killed Run leaves a usable file.
 */
public final class DebugRecord implements AutoCloseable {

    /** The file's suffix, after the Run log's stem. */
    public static final String SUFFIX = ".debug.ndjson";

    /** Null once the file could not be opened or written: the record is best effort, a Run is not. */
    private OutputStream out;

    private List<String> previousLog = List.of();

    private Observation last;

    private DebugRecord(OutputStream out) {
        this.out = out;
    }

    /** The debug file that goes beside {@code runLog}, a {@code <run-id>.jsonl} file. */
    public static Path beside(Path runLog) {
        String name = runLog.getFileName().toString();
        String stem = name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        return runLog.resolveSibling(stem + SUFFIX);
    }

    /** The system property that turns the record on. */
    public static final String PROPERTY = "shatterfish.debugRecord";

    /** The environment variable that turns it on, which a forked Rig JVM inherits. */
    public static final String ENVIRONMENT = "SHATTERFISH_DEBUG_RECORD";

    /**
     * Whether Runs write a debug record. Off unless the property is {@code true} or the environment
     * variable is {@code 1}: a record is about 1.8 MB a Run, which the nightly, ranked and goo-set
     * Runs have no use for.
     */
    public static boolean enabled() {
        return "true".equals(System.getProperty(PROPERTY)) || "1".equals(System.getenv(ENVIRONMENT));
    }

    /** Makes a child process the Rig starts write the record when this process does. */
    public static void inherit(ProcessBuilder child) {
        if (enabled()) {
            child.environment().put(ENVIRONMENT, "1");
        }
    }

    /**
     * Opens the file beside {@code runLog}, or returns null when the record is off ({@link #enabled}).
     * It replaces a file a former Run left. It is best effort: a
     * record that cannot be opened or written is dropped with a note on standard error, because a Run
     * must end the way it would have ended without it (issue #219) -- unlike the log, which is the
     * evidence and refuses to overwrite.
     */
    public static DebugRecord open(Path runLog) {
        if (!enabled()) {
            return null;
        }
        Path file = beside(runLog);
        try {
            return new DebugRecord(Files.newOutputStream(file, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
        } catch (IOException | RuntimeException e) {
            System.err.println("the debug record " + file + " could not be opened, and this Run goes without: " + e);
            return new DebugRecord(null);
        }
    }

    /**
     * One served wait. {@code observation} is what the Brain was handed and {@code wait} what the
     * loop recorded of it; nothing is returned.
     */
    public void wait(Observation observation, RunLog.Wait wait) {
        if (out == null) {
            return;
        }
        try {
            line(observation, wait);
        } catch (RuntimeException failed) {
            drop(failed);
        }
    }

    private void line(Observation observation, RunLog.Wait wait) {
        last = observation;
        List<String> now = new ArrayList<>();
        for (LogLine line : observation.log().lines()) {
            now.add(line.text());
        }
        List<String> fresh = fresh(previousLog, now);
        previousLog = now;
        int hero = observation.hero().cell();
        int width = observation.map().width();
        List<Tile> tiles = observation.map().tiles();
        List<ActorView> enemies = new ArrayList<>();
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                enemies.add(actor);
            }
        }
        if (enemies.isEmpty() && fresh.isEmpty() && observation.header().depth() % 5 != 0) {
            return;
        }
        JsonWriter json = new JsonWriter().beginObject();
        json.key("t").value("wait");
        json.key("k").value(wait.k());
        json.key("turn").value(wait.turn());
        json.key("depth").value(observation.header().depth());
        json.key("hp").value(observation.hero().hp());
        json.key("ht").value(observation.hero().ht());
        json.key("shield").value(observation.hero().shield());
        json.key("hunger").value(observation.hero().hunger().name());
        json.key("hero_cell").value(hero);
        json.key("hero_water").value(tiles.get(hero) == Tile.WATER);
        // What the practice table reads (story 10.18): whether the floor is sealed, and the keys the journal shows held here.
        json.key("sealed").value(observation.header().sealed());
        // And the dart traps Tengu's floor draws as it lays them (story 10.19), from the same screen.
        long darts = observation.map().traps().stream().filter(trap -> trap.kind().equals("poison dart trap")).count();
        if (observation.header().sealed() && darts > 0) {
            json.key("darts").value((int) darts);
        }
        json.key("keys").beginArray();
        for (var note : observation.journal().notes()) {
            if (note.kind() == org.shatterfish.api.NoteKind.KEY && note.depth() == observation.header().depth()) {
                json.value(note.title());
            }
        }
        json.endArray();
        json.key("hero_buffs");
        buffs(json, observation.hero().buffs());
        json.key("enemies").beginArray();
        for (ActorView enemy : enemies) {
            json.beginObject();
            json.key("name").value(enemy.name());
            json.key("cell").value(enemy.cell());
            json.key("pips").value(enemy.healthPips());
            json.key("water").value(tiles.get(enemy.cell()) == Tile.WATER);
            json.key("dist").value(Math.max(Math.abs(enemy.cell() % width - hero % width),
                    Math.abs(enemy.cell() / width - hero / width)));
            json.key("buffs");
            buffs(json, enemy.buffs());
            json.endObject();
        }
        json.endArray();
        json.key("log").beginArray();
        for (String line : fresh) {
            json.value(line);
        }
        json.endArray();
        json.key("potions").beginArray();
        for (ItemView item : observation.inventory().items()) {
            if (item.kind() == ItemKind.POTION) {
                json.beginObject().key("name").value(item.name()).key("q").value(item.quantity()).endObject();
            }
        }
        json.endArray();
        json.key("pack").beginArray();
        for (ItemView item : observation.inventory().items()) {
            if (item.kind() != ItemKind.POTION) {
                json.beginObject().key("kind").value(item.kind().name()).key("name").value(item.name())
                        .key("q").value(item.quantity()).key("worn").value(item.slot() != EquipSlot.NONE)
                        .key("lvl").value(item.levelKnown() ? item.visiblyUpgraded() : 0).endObject();
            }
        }
        json.endArray();
        json.key("action").value(String.valueOf(wait.action()));
        json.key("applied").value(wait.applied());
        if (wait.decision() != null) {
            json.key("goal").value(wait.decision().goal());
            json.key("why").value(wait.decision().chosen().why());
        }
        json.endObject();
        write(json.toJson());
    }

    /**
     * How the Run ended, with the last Observation the Brain saw (none for a Run that ended before it
     * saw any); {@code killer} is {@link #killerSince}, or empty for an ending that is not a death.
     */
    public void end(String cause, int depth, long turns, String killer) {
        if (out == null) {
            return;
        }
        try {
            ending(cause, depth, turns, killer);
        } catch (RuntimeException failed) {
            drop(failed);
        }
    }

    /** The appearances of the last belief line written, so a line is written only when they change. */
    private List<org.shatterfish.api.BeliefView.Appearance> lastBeliefs = List.of();

    /**
     * What the Brain believed about the unidentified potions and scrolls at a wait, written when it
     * changed (issue #219, potion inference). The view is the Brain's own output, the same one the
     * Overlay shows; the record takes it and returns nothing. Together with the {@code truth} line at
     * the Run's end it measures calibration offline, and nothing in a Run reads either.
     */
    public void beliefs(int depth, long k, org.shatterfish.api.BeliefView view) {
        if (out == null || view == null || view.appearances().equals(lastBeliefs)) {
            return;
        }
        try {
            lastBeliefs = view.appearances();
            JsonWriter json = new JsonWriter().beginObject();
            json.key("t").value("bel");
            json.key("k").value(k);
            json.key("depth").value(depth);
            json.key("floor");
            json.beginArray();
            for (String fact : view.floor()) {
                json.value(fact);
            }
            json.endArray();
            json.key("ap");
            json.beginArray();
            for (org.shatterfish.api.BeliefView.Appearance appearance : view.appearances()) {
                json.beginObject().key("l").value(appearance.label());
                json.key("c");
                json.beginArray();
                for (org.shatterfish.api.BeliefView.Candidate candidate : appearance.candidates()) {
                    json.beginObject().key("n").value(candidate.name()).key("p").value(Math.round(candidate.probability() * 100000)).endObject();
                }
                json.endArray().endObject();
            }
            json.endArray().endObject();
            write(json.toJson());
        } catch (RuntimeException failed) {
            drop(failed);
        }
    }

    /**
     * The true appearance of every potion and scroll class, read from the game after the Run is over
     * (diagnosis only: it is the one place the record reads hidden identities, and no Run reads it back).
     */
    private void truth() {
        JsonWriter json = new JsonWriter().beginObject();
        json.key("t").value("truth");
        for (String family : List.of("potion", "scroll")) {
            json.key(family);
            json.beginArray();
            Class<?>[] classes = family.equals("potion")
                    ? com.shatteredpixel.shatteredpixeldungeon.items.Generator.Category.POTION.classes
                    : com.shatteredpixel.shatteredpixeldungeon.items.Generator.Category.SCROLL.classes;
            for (Class<?> one : classes) {
                try {
                    // A fresh item wears its Run's appearance: its name while unknown, its sprite always.
                    com.shatteredpixel.shatteredpixeldungeon.items.Item item =
                            (com.shatteredpixel.shatteredpixeldungeon.items.Item) com.watabou.utils.Reflection.newInstance(one);
                    boolean known = item instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion potion
                            ? potion.isKnown() : ((com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll) item).isKnown();
                    json.beginObject().key("class").value(one.getSimpleName()).key("name").value(item.name())
                            .key("image").value((long) item.image).key("known").value(known).endObject();
                } catch (RuntimeException e) {
                    // A Run with no handler (never started) has no truth to write.
                }
            }
            json.endArray();
        }
        json.endObject();
        write(json.toJson());
    }

    private void drop(RuntimeException failed) {
        System.err.println("the debug record stops here and this Run goes on without it: " + failed);
        try {
            close();
        } catch (RuntimeException ignored) {
            // The file is being given up on; a second failure to close it says nothing more.
        }
        out = null;
    }

    private void ending(String cause, int depth, long turns, String killer) {
        truth();
        JsonWriter json = new JsonWriter().beginObject();
        json.key("t").value("end");
        json.key("cause").value(cause);
        json.key("depth").value(depth);
        json.key("turns").value(turns);
        json.key("killer").value(killer);
        if (last != null) {
            json.key("last_hp").value(last.hero().hp());
            json.key("ht").value(last.hero().ht());
            json.key("hunger").value(last.hero().hunger().name());
            json.key("hero_buffs");
            buffs(json, last.hero().buffs());
        }
        json.endObject();
        write(json.toJson());
    }

    private static void buffs(JsonWriter json, List<BuffView> buffs) {
        json.beginArray();
        for (BuffView buff : buffs) {
            json.beginObject().key("name").value(buff.name())
                    .key("turns").value(buff.timed() ? buff.turnsHundredths() / 100 : -1).endObject();
        }
        json.endArray();
    }

    /**
     * The lines of {@code now} that {@code before} did not have. The log is a window of the last
     * sixty-four lines that only grows at its end, so the new lines are the ones after the longest
     * run at the front of {@code now} that is a tail of {@code before}. A line repeated verbatim at
     * the seam can be taken for an old one when the window is full; the file says so rather than
     * guessing, because the record is a debugging aid and not a measurement.
     */
    static List<String> fresh(List<String> before, List<String> now) {
        for (int overlap = Math.min(before.size(), now.size()); overlap > 0; overlap--) {
            if (before.subList(before.size() - overlap, before.size()).equals(now.subList(0, overlap))) {
                return now.subList(overlap, now.size());
            }
        }
        return now;
    }

    /** The games recorded in the game's rankings now, so that a death can be told from an old one. */
    public static Set<String> rankingsNow() {
        Set<String> ids = new HashSet<>();
        try {
            if (Rankings.INSTANCE.records != null) {
                for (Rankings.Record record : Rankings.INSTANCE.records) {
                    ids.add(record.gameID);
                }
            }
        } catch (RuntimeException unreadable) {
            // The rankings are only read for the killer; without them the killer is "unknown".
        }
        return ids;
    }

    /**
     * What the game says killed the hero: the cause class of the ranking record the death added, read
     * after the Run is over ({@code core/.../Dungeon.java:872-877}; {@code core/.../Rankings.java:110}).
     * "unknown" when the death left no new record.
     */
    public static String killerSince(Set<String> before) {
        try {
            if (Rankings.INSTANCE.records != null) {
                for (Rankings.Record record : Rankings.INSTANCE.records) {
                    if (!before.contains(record.gameID) && record.cause != null) {
                        return record.cause.getName().substring(record.cause.getName().lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            // Falls through to unknown.
        }
        return "unknown";
    }

    private void write(String line) {
        try {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("the debug record could not be written", e);
        }
    }

    @Override
    public void close() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException("the debug record could not be closed", e);
        }
    }
}
