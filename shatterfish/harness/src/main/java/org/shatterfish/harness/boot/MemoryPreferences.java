package org.shatterfish.harness.boot;

import com.badlogic.gdx.Preferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The game's settings, held in memory and never written anywhere.
 *
 * <p>{@code SPDSettings} reads and writes through {@code GameSettings}, which takes its
 * {@code Preferences} from {@code Gdx.app.getPreferences} unless one is installed with
 * {@code GameSettings.set} ({@code SPD-classes/.../utils/GameSettings.java:34-43}). The headless
 * backend's own preferences write an XML file under the user's home directory, which a test
 * must not do and a Run must not depend on. This one starts empty, so every setting has the
 * game's own default until the harness pins it, and it is discarded with the process.
 *
 * <p>Values are stored as strings and parsed on the way out, which is what libGDX's file-backed
 * implementations do, so a setting written as one type and read as another behaves the same
 * way here as in the game.
 */
public final class MemoryPreferences implements Preferences {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Preferences putBoolean(String key, boolean val) {
        values.put(key, Boolean.toString(val));
        return this;
    }

    @Override
    public Preferences putInteger(String key, int val) {
        values.put(key, Integer.toString(val));
        return this;
    }

    @Override
    public Preferences putLong(String key, long val) {
        values.put(key, Long.toString(val));
        return this;
    }

    @Override
    public Preferences putFloat(String key, float val) {
        values.put(key, Float.toString(val));
        return this;
    }

    @Override
    public Preferences putString(String key, String val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences put(Map<String, ?> vals) {
        for (Map.Entry<String, ?> entry : vals.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean b) {
                putBoolean(entry.getKey(), b);
            } else if (value instanceof Integer i) {
                putInteger(entry.getKey(), i);
            } else if (value instanceof Long l) {
                putLong(entry.getKey(), l);
            } else if (value instanceof Float f) {
                putFloat(entry.getKey(), f);
            } else if (value instanceof String s) {
                putString(entry.getKey(), s);
            } else {
                throw new IllegalArgumentException("unsupported preference type for " + entry.getKey()
                        + ": " + (value == null ? "null" : value.getClass().getName()));
            }
        }
        return this;
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public int getInteger(String key) {
        return getInteger(key, 0);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    @Override
    public float getFloat(String key) {
        return getFloat(key, 0f);
    }

    @Override
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String value = values.get(key);
        return value == null ? defValue : Boolean.parseBoolean(value);
    }

    @Override
    public int getInteger(String key, int defValue) {
        String value = values.get(key);
        return value == null ? defValue : Integer.parseInt(value);
    }

    @Override
    public long getLong(String key, long defValue) {
        String value = values.get(key);
        return value == null ? defValue : Long.parseLong(value);
    }

    @Override
    public float getFloat(String key, float defValue) {
        String value = values.get(key);
        return value == null ? defValue : Float.parseFloat(value);
    }

    @Override
    public String getString(String key, String defValue) {
        String value = values.get(key);
        return value == null ? defValue : value;
    }

    @Override
    public Map<String, ?> get() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    /** Nothing to flush: the values live here and nowhere else. */
    @Override
    public void flush() {
    }
}
