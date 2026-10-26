package org.shatterfish.harness.redetermine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * What every row of a scrub shares: the two bundles as JSON, the Observation the copy must reproduce and the things
 * read from it once, the sample's streams, the rows in force and the report. Nothing here reads the true state
 * except through the two bundles, and a row reads from the Observation what the screen shows and from the bundles
 * what it is about to replace.
 */
final class Ctx {

    final JSONObject game;
    final JSONObject level;
    final Observation observation;
    final boolean[] fov;
    final int depth;
    final ScrubSample sample;
    final Streams streams;
    final Set<ScrubRow> rows;
    final Scrub.Report report = new Scrub.Report();

    final int width;
    final int length;
    final Fog[] fog;
    final Map<Integer, ActorView> actorsByCell = new HashMap<>();
    final Map<Integer, HeapView> heapsByCell = new HashMap<>();

    Ctx(JSONObject game, JSONObject level, Observation observation, boolean[] fov, int depth, ScrubSample sample,
        Set<ScrubRow> rows) {
        this.game = game;
        this.level = level;
        this.observation = observation;
        this.fov = fov;
        this.depth = depth;
        this.sample = sample;
        this.streams = new Streams(sample.draws());
        this.rows = rows.isEmpty() ? EnumSet.noneOf(ScrubRow.class) : EnumSet.copyOf(rows);
        this.width = observation.map().width();
        this.length = observation.map().cells();
        this.fog = observation.map().fog().toArray(new Fog[0]);
        for (ActorView actor : observation.actors().actors()) {
            actorsByCell.put(actor.cell(), actor);
        }
        for (HeapView heap : observation.map().heaps()) {
            heapsByCell.put(heap.cell(), heap);
        }
    }

    boolean on(ScrubRow row) {
        return rows.contains(row);
    }

    boolean known(int cell) {
        return fog[cell] != Fog.UNKNOWN;
    }

    // --- bundle files -----------------------------------------------------------------------------------------------

    static JSONObject read(byte[] bytes) {
        try {
            byte[] text = bytes;
            if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    text = in.readAllBytes();
                }
            }
            return new JSONObject(new String(text, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] write(JSONObject bundle) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                out.write(bundle.toString().getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- JSON helpers -----------------------------------------------------------------------------------------------

    static int[] ints(JSONArray array) {
        int[] out = new int[array.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.getInt(i);
        }
        return out;
    }

    static boolean[] booleans(JSONArray array) {
        boolean[] out = new boolean[array.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.getBoolean(i);
        }
        return out;
    }

    static JSONArray array(int[] values) {
        JSONArray out = new JSONArray();
        for (int value : values) {
            out.put(value);
        }
        return out;
    }

    static JSONArray array(boolean[] values) {
        JSONArray out = new JSONArray();
        for (boolean value : values) {
            out.put(value);
        }
        return out;
    }

    /** The objects of an array, in order, skipping anything that is not one. */
    static java.util.List<JSONObject> objects(JSONArray array) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    out.add(object);
                }
            }
        }
        return out;
    }

    static JSONArray arrayOf(java.util.List<JSONObject> objects) {
        JSONArray out = new JSONArray();
        for (JSONObject object : objects) {
            out.put(object);
        }
        return out;
    }

    /** A deep copy, so a row that edits a copy cannot edit the bundle it compares with. */
    static JSONObject copy(JSONObject object) {
        return new JSONObject(object.toString());
    }

    static String className(JSONObject object) {
        return object.optString("__className", "");
    }

    /** The per-row counts, for the report. */
    void held(ScrubRow row, int count) {
        report.held.merge(row, count, Integer::sum);
    }

    void changed(ScrubRow row, int count) {
        report.changed.merge(row, count, Integer::sum);
    }

    static EnumMap<ScrubRow, Integer> counts() {
        return new EnumMap<>(ScrubRow.class);
    }
}
