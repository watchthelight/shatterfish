package org.shatterfish.codex;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The upstream tag the Codex folder is named by: {@code v} and the version the root build script
 * declares ({@code build.gradle: appVersionName}), stamped into a resource by
 * {@code :codex:processResources} as the harness does for its own boot. {@code docs/UPSTREAM.md}
 * pins the tag, and {@code CodexSeedFreeTest} holds that the two agree.
 */
final class Upstream {

    private Upstream() {
    }

    /** The tag, {@code v4.0.0} at the pin this story was written against. */
    static String tag() {
        Properties properties = new Properties();
        try (InputStream in = Upstream.class.getResourceAsStream("upstream.properties")) {
            if (in == null) {
                throw new IllegalStateException("upstream.properties is not on the classpath; :codex:processResources stamps it");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("upstream.properties could not be read", e);
        }
        String version = properties.getProperty("version.name");
        if (version == null || version.isBlank() || version.contains("$")) {
            throw new IllegalStateException("upstream.properties carries no stamped version: " + version);
        }
        return "v" + version.trim();
    }
}
