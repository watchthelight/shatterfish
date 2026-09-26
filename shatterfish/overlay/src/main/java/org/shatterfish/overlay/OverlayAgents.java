package org.shatterfish.overlay;

import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RandomAgent;
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
import java.util.function.Supplier;

/**
 * Who plays an Overlay Run (story 5.1): the random agent, the Rig's Baseline, or the Brain.
 *
 * <p>The Brain here is built on the same {@code Codex.Knowledge} the Rig builds, read by the harness's
 * {@code CodexKnowledge} from the checked-in {@code codex/<tag>/} and {@code tactics/bestiary.json}: the
 * identities, rooms, guarantees, threats, gear and the bestiary's tags, so the watched game plays as the
 * Rig's Runs do (a statue is scenery, a shaman shoots). The files are read once, when the launcher
 * attaches the Brain, and a missing or mismatched one stops the launch; there is no empty fallback. Its
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
            Codex.Knowledge knowledge = knowledge(options.codex());
            return () -> new BrainDecider(new Brain(knowledge, weights, BRAIN_SEED));
        }
        return () -> new RandomAgent(options.agentSeed());
    }

    /** The name the Run log's header gives the agent. */
    static String name(LaunchOptions options) {
        return options.human() ? "human" : options.agent().equals("brain") ? "shatterfish" : "random";
    }

    /**
     * The Brain's knowledge from the Codex in {@code folder} and the bestiary beside it, read as the Rig
     * reads it ({@code CodexKnowledge}) and checked against the tag this build is pinned to. Refuses,
     * naming the file, when either is missing.
     */
    static Codex.Knowledge knowledge(Path folder) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("the Brain reads the Codex from " + folder.toAbsolutePath()
                    + ", which is not there: launch from the repository root (./gradlew :overlay:launch) or"
                    + " state --codex");
        }
        Path bestiary = CodexKnowledge.bestiaryFile(folder);
        if (!Files.isRegularFile(bestiary)) {
            throw new IllegalArgumentException("the Brain reads the bestiary from " + bestiary + ", which is not there");
        }
        return CodexKnowledge.read(folder, HeadlessBoot.pinnedTag());
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
