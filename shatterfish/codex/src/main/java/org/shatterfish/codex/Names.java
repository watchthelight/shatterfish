package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Display names from the English bundle (story 2.3), read as a citation is: the game derives a
 * class's key from its name without the root package, lower-cased, the {@code $} of a nested
 * class kept ({@code Messages.java:125-133}), and looks it up in the bundle of the key's first
 * segment. The generator cannot call {@code Messages}, which reads the bundles through the
 * toolkit's files, so it reads the one file, and the line is the citation.
 */
final class Names {

    private static final String BUNDLES = "core/src/main/assets/messages/";

    private Names() {
    }

    /** The key the game derives for {@code type} and {@code suffix} ({@code "name"} for the display name). */
    static String key(Class<?> type, String suffix) {
        return lower(type.getName().replace(Sources.ROOT_PACKAGE_PREFIX, "")) + "." + suffix;
    }

    /**
     * {@code text} lower-cased as the game does under its fixed English locale, which for the
     * ASCII names a class or a label key is made of is the ASCII mapping; the machine's locale is
     * never consulted, and a character outside ASCII is refused rather than guessed at.
     */
    static String lower(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 128) {
                throw new IllegalStateException("a key with a character outside ASCII: " + text);
            }
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return out.toString();
    }

    /**
     * A display name as the vocabulary diff joins on it (story 2.8): trimmed, its runs of
     * whitespace collapsed to one space, and lower-cased so that one game's "Potion of Healing"
     * meets the other's "potion of healing".
     *
     * <p>This is a name a player reads, not a key a class derives, so it is normalised on its own
     * terms. A character outside ASCII is still refused — the two pinned games name everything in
     * ASCII and a guess at a mapping would silently split a row in two — but the refusal names
     * the display name rather than speaking of a key that does not exist.
     */
    static String display(String text) {
        String collapsed = text.trim().replaceAll("\\s+", " ");
        for (int i = 0; i < collapsed.length(); i++) {
            if (collapsed.charAt(i) >= 128) {
                throw new IllegalStateException("a display name with a character outside ASCII: " + text);
            }
        }
        if (collapsed.isEmpty()) {
            throw new IllegalStateException("a display name that is nothing but whitespace");
        }
        return lower(collapsed);
    }

    /** The bundle line for {@code key}: its value and the citation of the line; no line, or two, fails naming the key. */
    record Named(String value, Codex.Citation citation) {
    }

    static Named lookup(Path root, String key) {
        Named named = find(root, key);
        if (named == null) {
            throw new IllegalStateException("no bundle line for " + key);
        }
        return named;
    }

    /**
     * The lines of one bundle file of the pinned game. This class is one of the few the gate lets
     * open a file, so every reader of the game's text comes through here rather than opening one
     * of its own.
     */
    static List<String> lines(Path root, String bundle) {
        if (!bundle.startsWith(BUNDLES) || !bundle.endsWith(".properties")) {
            throw new IllegalStateException("not a bundle of the pinned game: " + bundle);
        }
        try {
            List<String> lines = Files.readAllLines(root.resolve(bundle), StandardCharsets.UTF_8);
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("the bundle could not be read at " + bundle, e);
        }
    }

    /** The bundle line for {@code key}, or null when the bundle has none; two lines fail. */
    static Named find(Path root, String key) {
        String bundle = BUNDLES + key.substring(0, key.indexOf('.')) + "/" + key.substring(0, key.indexOf('.')) + ".properties";
        List<String> lines = lines(root, bundle);
        String value = null;
        int at = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(key + "=")) {
                if (value != null) {
                    throw new IllegalStateException(bundle + " lines " + (at + 1) + " and " + (i + 1) + " both give " + key);
                }
                value = line.substring(key.length() + 1).trim();
                at = i;
            }
        }
        if (value == null) {
            return null;
        }
        return new Named(value, new Codex.Citation(bundle, at + 1));
    }

    /**
     * The display name of {@code type}: its own bundle line, or the nearest superclass's, as the
     * game falls back ({@code Messages.java:122-139}); a class the bundles do not name at any
     * level fails.
     */
    static Named of(Path root, Class<?> type) {
        return sourced(root, type).named();
    }

    /** A bundle name with the class whose own key carried it, which is not always the class asked for. */
    record Sourced(Class<?> owner, Named named) {
    }

    /**
     * The bundle name for a class and the class it was found on. The game falls back to a
     * superclass's name when a subclass has no key of its own (the tengu's darts are named as the
     * poison dart trap), so a table that cites the line must be able to say the name is not this
     * class's own; otherwise the citation names a key for a different class with nothing to mark it.
     */
    static Sourced sourced(Path root, Class<?> type) {
        for (Class<?> c = type; c != null && c.getName().startsWith(Sources.ROOT_PACKAGE_PREFIX); c = c.getSuperclass()) {
            Named named = find(root, key(c, "name"));
            if (named != null) {
                return new Sourced(c, named);
            }
        }
        throw new IllegalStateException("no bundle names " + type.getName() + " or a superclass of it");
    }
}
