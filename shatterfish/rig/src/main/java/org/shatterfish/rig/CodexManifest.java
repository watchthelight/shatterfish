package org.shatterfish.rig;

import org.shatterfish.api.Codex;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The Codex manifest, read from disk for a Brain (story 4.1).
 *
 * <p>The Brain may not open a file, so its caller reads the Codex and hands it over as an {@code api}
 * value at construction. The manifest is refused unless it is the Codex this build writes, for the
 * tag this build is pinned to: a Brain built on another Codex would be deciding from tables whose
 * meaning this build does not share.
 */
public final class CodexManifest {

    /** Where the Codex lives, under the repository root: one folder per upstream tag. */
    public static final String FOLDER = "codex";

    public static final String FILE = "manifest.json";

    private CodexManifest() {
    }

    /** The manifest in {@code folder} ({@code codex/<tag>}), checked against this build. */
    public static Codex.Manifest read(Path folder, String tag) {
        String text;
        try {
            text = Files.readString(folder.resolve(FILE), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the Codex manifest in " + folder + " could not be read", e);
        }
        Map<String, String> held = Json.object(text);
        Codex.Manifest manifest = new Codex.Manifest(
                Json.integer(Json.required(held, "codexVersion", "manifest")),
                Json.string(Json.required(held, "upstreamTag", "manifest")),
                Json.array(Json.required(held, "tables", "manifest")).stream().map(Json::string).toList());
        if (manifest.version() != Codex.VERSION || !manifest.upstreamTag().equals(tag)) {
            throw new IllegalArgumentException("the Codex in " + folder + " is version " + manifest.version()
                    + " for " + manifest.upstreamTag() + ", and this build writes version " + Codex.VERSION
                    + " for " + tag + "; regenerate it with ./gradlew :codex:generate");
        }
        return manifest;
    }
}
