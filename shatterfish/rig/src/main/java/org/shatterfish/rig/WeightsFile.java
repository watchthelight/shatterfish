package org.shatterfish.rig;

import org.shatterfish.api.Weights;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Brain's Evaluation weights, read from disk for it (story 4.5).
 *
 * <p>The Brain may not open a file, so its caller reads the weights and hands them over as an
 * {@code api} value at construction, as it does the Codex ({@link CodexManifest}). The file is
 * refused unless it is in the shape this build reads, names the Brain it is for, and is written in
 * its canonical form: one spelling per weight set, so the configuration hash the Rig puts in every
 * log header is a function of the weights and not of how somebody spaced them.
 */
public final class WeightsFile {

    /** Where the weight sets live, under the repository root: one file per Brain. */
    public static final String FOLDER = "weights";

    private WeightsFile() {
    }

    /** The committed weight set of the Brain named {@code brain}, under {@code root}. */
    public static Path of(Path root, String brain) {
        return root.resolve(FOLDER).resolve(brain + ".json");
    }

    /** The weights in {@code file}, which must be the set for {@code brain}. */
    public static Weights read(Path file, String brain) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the weights in " + file + " could not be read", e);
        }
        Map<String, String> held = Json.object(text);
        int format = Json.integer(Json.required(held, "format", "weights"));
        if (format != Weights.FORMAT) {
            throw new IllegalArgumentException("the weights in " + file + " are format " + format
                    + ", and this build reads format " + Weights.FORMAT);
        }
        List<Weights.Term> terms = new ArrayList<>();
        for (Map.Entry<String, String> term : Json.object(Json.required(held, "terms", "weights")).entrySet()) {
            terms.add(new Weights.Term(term.getKey(), Json.number(term.getValue())));
        }
        Weights weights = new Weights(Json.string(Json.required(held, "name", "weights")),
                Json.integer(Json.required(held, "version", "weights")), terms);
        if (!weights.name().equals(brain)) {
            throw new IllegalArgumentException("the weights in " + file + " are for " + weights.name()
                    + ", not for " + brain);
        }
        if (!weights.canonical().equals(text)) {
            throw new IllegalArgumentException("the weights in " + file + " are not written canonically;"
                    + " the file must read exactly " + weights.canonical());
        }
        return weights;
    }
}
