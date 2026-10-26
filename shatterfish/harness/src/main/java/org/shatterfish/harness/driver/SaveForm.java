package org.shatterfish.harness.driver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * The form two saves of the same Run take, for comparing them ({@link SnapshotStore#verifyRestored}). A save is the game's
 * bundle, gzipped JSON, and two saves of one state are not byte for byte equal, for reasons the game itself puts in
 * and that say nothing about the Run (each is a key this class leaves out or orders):
 * <ul>
 *   <li>the wall-clock time of the save ({@code core/.../Dungeon.java:634}, key {@code last_played});</li>
 *   <li>the next actor id ({@code core/.../actors/Actor.java:232-240}, key {@code nextid}): a buff an item owns is not
 *       saved and is made again by the item as the hero's pack is activated on every load (a wand's charger,
 *       {@code core/.../items/wands/Wand.java:195-197}), and {@code Actor.add} gives it the next id
 *       ({@code Actor.java:354-360}), so a load moves the counter on by the number of such buffs. An id orders nothing in
 *       the game ({@code Actor.process} walks the insertion-ordered set, shatterfish-hook 6) and no saved reference
 *       names one of those buffs;</li>
 *   <li>the order of a set the game writes in the order of a hash set of classes, which follows the order the entries were
 *       put in and not the entries ({@code core/.../Statistics.java:154}, key {@code item_types_discovered});</li>
 *   <li>the order of the level's heaps, plants, traps and targeted cells, which the level keeps in maps by cell and writes in
 *       the map's order ({@code core/.../levels/Level.java:504-507}, {@code :513-514}); the order follows the map's
 *       capacity, which follows how many entries it once held, and a level read back holds only the ones it has.</li>
 * </ul>
 * Everything else is compared as the game wrote it, with an object's keys in sorted order. The form is a string held only
 * for the length of the comparison; it is the Run's hidden state and never leaves this package.
 */
final class SaveForm {

    /** Keys whose value is the clock's or the load's and not the Run's. */
    private static final Set<String> CLOCK = Set.of("last_played", "nextid");
    /** Keys whose array is a set written in an order that does not matter. */
    private static final Set<String> SETS = Set.of("item_types_discovered", "heaps", "plants", "traps", "targeted_cells");

    private SaveForm() {
    }

    /** The file's compared form: the parsed, ordered text for a bundle, and the bytes themselves for anything else. */
    static String of(byte[] file) {
        if (file == null) {
            return null;
        }
        try {
            byte[] raw = file;
            if (file.length > 2 && (file[0] & 0xFF) == 0x1F && (file[1] & 0xFF) == 0x8B) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(file))) {
                    raw = in.readAllBytes();
                }
            }
            Object parsed = new JSONTokener(new String(raw, StandardCharsets.UTF_8)).nextValue();
            StringBuilder out = new StringBuilder(raw.length);
            write(parsed, null, out);
            return out.toString();
        } catch (JSONException | IOException notABundle) {
            return "bytes:" + HexFormat.of().formatHex(file);
        }
    }

    private static void write(Object value, String key, StringBuilder out) {
        if (value instanceof JSONObject object) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (String name : object.keySet()) {
                if (!CLOCK.contains(name)) {
                    sorted.put(name, object.get(name));
                }
            }
            out.append('{');
            for (var entry : sorted.entrySet()) {
                out.append(JSONObject.quote(entry.getKey())).append(':');
                write(entry.getValue(), entry.getKey(), out);
                out.append(',');
            }
            out.append('}');
        } else if (value instanceof JSONArray array) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                StringBuilder item = new StringBuilder();
                write(array.get(i), null, item);
                items.add(item.toString());
            }
            if (key != null && SETS.contains(key)) {
                Collections.sort(items);
            }
            out.append('[').append(String.join(",", items)).append(']');
        } else {
            out.append(JSONObject.valueToString(value));
        }
    }
}
