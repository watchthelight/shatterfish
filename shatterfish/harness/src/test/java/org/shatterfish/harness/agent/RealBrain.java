package org.shatterfish.harness.agent;

import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Brain the Rig plays, built the way {@code RunOne} builds it (story 5.16): the committed Codex of the pinned
 * tag, the committed weights, and the Brain's constant stream. The other agent tests use a Brain on an empty
 * Codex; the sewers run and the outcome comparison need the real one, since it is the one that reaches Goo.
 */
final class RealBrain {

    static final String NAME = "shatterfish";

    private RealBrain() {
    }

    /** The repository root: the nearest folder above the test's working directory that holds {@code vanilla.pin}. */
    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        for (Path at = here; at != null; at = at.getParent()) {
            if (Files.exists(at.resolve("vanilla.pin"))) {
                return at;
            }
        }
        throw new IllegalStateException("no repository root above " + here);
    }

    static BrainDecider decider() {
        Path root = root();
        String tag = HeadlessBoot.pinnedTag();
        Codex.Knowledge codex = CodexKnowledge.read(root.resolve("codex").resolve(tag), tag);
        return new BrainDecider(new Brain(codex, weights(root), DeciderSeeds.brain(NAME)));
    }

    /** {@code weights/shatterfish.json}, as {@code WeightsFile.read} reads it (the Rig's reader is not on the harness's path). */
    static Weights weights(Path root) {
        try {
            String text = Files.readString(root.resolve("weights").resolve(NAME + ".json"), StandardCharsets.UTF_8).strip();
            Map<String, String> held = Json.object(text);
            List<Weights.Term> terms = new ArrayList<>();
            for (Map.Entry<String, String> term : Json.object(Json.required(held, "terms", "weights")).entrySet()) {
                terms.add(new Weights.Term(term.getKey(), Json.number(term.getValue())));
            }
            Weights weights = new Weights(Json.string(Json.required(held, "name", "weights")),
                    Json.integer(Json.required(held, "version", "weights")), terms);
            if (!weights.canonical().equals(text)) {
                throw new IllegalStateException("the committed weights are not canonical");
            }
            return weights;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
