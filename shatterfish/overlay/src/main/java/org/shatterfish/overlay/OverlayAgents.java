package org.shatterfish.overlay;

import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RandomAgent;
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
import java.util.function.Supplier;

/**
 * Who plays an Overlay Run (story 5.1): the random agent, the Rig's Baseline, or the Brain.
 *
 * <p>The Brain here is built on an empty Codex: the Codex reader lives in the rig, which the Overlay
 * may not depend on (docs/ideas.md), so it knows no identities, rooms, guarantees, threats or gear, and
 * plays with its pessimistic defaults. It is the Brain's own code and weights and nothing else. Its
 * stream is seeded from its name and nothing about the Run, through the {@code DeciderSeeds} the rig's
 * {@code Brains.brainSeed} uses too, so a Brain never learns the seed through its own randomness
 * (story 4.1's fairness rule).
 */
final class OverlayAgents {

    /** The seed of the Overlay's Brain: the one the rig gives the Brain of the same name. */
    static final long BRAIN_SEED = DeciderSeeds.brain("shatterfish");

    private OverlayAgents() {
    }

    /** The agent the launcher was told to attach; for a human's Run, the Brain that shadows them (story 5.9). */
    static Supplier<Decider> of(LaunchOptions options) {
        if (options.agent().equals("brain") || options.human()) {
            Weights weights = weights(options.weights());
            return () -> new BrainDecider(new Brain(emptyCodex(), weights, BRAIN_SEED));
        }
        return () -> new RandomAgent(options.agentSeed());
    }

    /** The name the Run log's header gives the agent. */
    static String name(LaunchOptions options) {
        return options.human() ? "human" : options.agent().equals("brain") ? "shatterfish" : "random";
    }

    static Codex.Knowledge emptyCodex() {
        return new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                List.of(), List.of(), List.of());
    }

    /** The committed weight set, in the rig's format ({@code WeightsFile}), read the same way. */
    static Weights weights(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("the weights in " + file + " could not be read", e);
        }
        Map<String, String> held = Json.object(text);
        int format = Json.integer(Json.required(held, "format", "weights"));
        if (format != Weights.FORMAT) {
            throw new IllegalArgumentException("the weights in " + file + " are format " + format);
        }
        List<Weights.Term> terms = new ArrayList<>();
        for (Map.Entry<String, String> term : Json.object(Json.required(held, "terms", "weights")).entrySet()) {
            terms.add(new Weights.Term(term.getKey(), Json.number(term.getValue())));
        }
        return new Weights(Json.string(Json.required(held, "name", "weights")),
                Json.integer(Json.required(held, "version", "weights")), terms);
    }
}
