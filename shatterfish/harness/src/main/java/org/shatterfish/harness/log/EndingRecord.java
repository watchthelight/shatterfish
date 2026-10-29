package org.shatterfish.harness.log;

import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import org.shatterfish.api.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a Run ended, as the Rig needs to tell deaths apart: who killed the hero, on which floor, from
 * how much health, and which bosses it had killed by name. A sidecar file beside the Run's log,
 * {@code <run-id>.ending.json}, written once after the Run is over (E10 W1, the ADR-0011 amendment
 * of 2026-10-28).
 *
 * <p><b>Why a file of its own.</b> The {@code end} record of the Run log is chained, so a member
 * added there would change every chain ever published. This file is outside the chain and outside
 * {@code *.jsonl}, like {@link DebugRecord}, and a log with no such file stays a log with no killer:
 * every reader treats absence as "not recorded", never as an error.
 *
 * <p><b>Why it is not a parity leak.</b> It is written after the Run has ended and read by nothing
 * while a Run is played; no class of the Brain's module can name it. The one value taken during the
 * Run, the hero's health at the last wait, is read from the Observation the Brain was already
 * handed. {@code EndingRecordIsolationTest} holds the isolation.
 */
public final class EndingRecord {

    /** The file's suffix, after the Run log's stem. */
    public static final String SUFFIX = ".ending.json";

    /**
     * The boss behind each index of {@code Statistics.bossScores}, by the code that adds to it on a
     * kill: Goo {@code Goo.java:306}, Tengu {@code Tengu.java:225}, DM-300 {@code DM300.java:594},
     * the Dwarf King {@code DwarfKing.java:572}, Yog-Dzewa {@code YogDzewa.java:542} (all under
     * {@code core/.../actors/mobs/}). The array's length is {@code Statistics.java:51}.
     */
    public static final List<String> BOSSES = List.of("Goo", "Tengu", "DM-300", "Dwarf King", "Yog-Dzewa");

    /**
     * What a Run's ending says.
     *
     * @param killer    the class name the game's rankings give as the cause of death, or empty when the
     *                  Run did not die ({@code "unknown"} when it died and left no record)
     * @param depth     the floor the hero was on at the end
     * @param lastHp    the hero's health at the last wait the Brain saw, or -1 when it saw none
     * @param ht        the hero's maximum health then, or -1
     * @param bossKills the bosses killed, by {@link #BOSSES} name, in index order
     */
    public record Ending(String killer, int depth, int lastHp, int ht, List<String> bossKills) {

        public Ending {
            killer = killer == null ? "" : killer;
            bossKills = List.copyOf(bossKills);
        }
    }

    private EndingRecord() {
    }

    /** The file that goes beside {@code runLog}, a {@code <run-id>.jsonl} file. */
    public static Path beside(Path runLog) {
        String name = runLog.getFileName().toString();
        String stem = name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        return runLog.resolveSibling(stem + SUFFIX);
    }

    /** The bosses whose entry of {@code Statistics.bossScores} is above zero now, as the game's score counts them. */
    public static List<String> bossKillsNow() {
        List<String> killed = new ArrayList<>();
        int[] scores = Statistics.bossScores;
        for (int i = 0; i < scores.length && i < BOSSES.size(); i++) {
            if (scores[i] > 0) {
                killed.add(BOSSES.get(i));
            }
        }
        return killed;
    }

    /** Writes the file beside {@code runLog}; best effort, because a Run ends the way it would have without it. */
    public static void write(Path runLog, Ending ending) {
        JsonWriter json = new JsonWriter().beginObject();
        json.key("killer").value(ending.killer());
        json.key("depth").value(ending.depth());
        json.key("last_hp").value(ending.lastHp());
        json.key("ht").value(ending.ht());
        json.key("boss_kills").beginArray();
        for (String boss : ending.bossKills()) {
            json.value(boss);
        }
        json.endArray();
        json.endObject();
        try {
            Files.writeString(beside(runLog), json.toJson() + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("the ending record beside " + runLog + " could not be written: " + e);
        }
    }

    /** The ending beside {@code runLog}, or null when there is none or it cannot be read. */
    public static Ending read(Path runLog) {
        Path file = beside(runLog);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            Map<String, String> held = Json.object(Files.readString(file, StandardCharsets.UTF_8).strip());
            List<String> bosses = new ArrayList<>();
            for (String raw : Json.array(Json.required(held, "boss_kills", "ending"))) {
                bosses.add(Json.string(raw));
            }
            return new Ending(Json.string(Json.required(held, "killer", "ending")),
                    Json.integer(Json.required(held, "depth", "ending")),
                    Json.integer(Json.required(held, "last_hp", "ending")),
                    Json.integer(Json.required(held, "ht", "ending")), bosses);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
