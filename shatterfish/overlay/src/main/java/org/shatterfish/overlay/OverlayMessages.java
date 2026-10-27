package org.shatterfish.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Set;

/**
 * The Overlay's own strings (story 8.4, UX-DR15): one properties file beside this class, so none of the
 * settings section's words is written in Java and none is added to the game's message files, which are
 * upstream's. English only, like the Overlay's other text.
 *
 * <p>The game's own {@code Messages} reads a fixed list of bundles and cannot be given another
 * ({@code core/.../messages/Messages.java:56-66}), so the file is read here with the JDK's properties
 * reader. A key that is missing reads as {@link #NO_TEXT_FOUND} and the key, as the game's does, and
 * {@code PauseOnWindowTest} holds that every key the window asks for exists.
 */
final class OverlayMessages {

    static final String NO_TEXT_FOUND = "!!!NO TEXT FOUND!!!";
    private static final String FILE = "overlay.properties";

    private static final Properties STRINGS = read();

    private OverlayMessages() {
    }

    private static Properties read() {
        Properties strings = new Properties();
        try (InputStream in = OverlayMessages.class.getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is missing from the Overlay's resources");
            }
            strings.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + FILE, e);
        }
        return strings;
    }

    /** The string for {@code key}, with {@code {0}}, {@code {1}} filled from {@code args}. */
    static String get(String key, Object... args) {
        String value = STRINGS.getProperty(key);
        if (value == null) {
            return NO_TEXT_FOUND + " " + key;
        }
        return args.length == 0 ? value : MessageFormat.format(value, args);
    }

    /** Every key in the file, for the test that holds that none is missing. */
    static Set<String> keys() {
        return Set.copyOf(STRINGS.stringPropertyNames());
    }
}
