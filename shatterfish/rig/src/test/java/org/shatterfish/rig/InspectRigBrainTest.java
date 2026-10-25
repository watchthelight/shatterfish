package org.shatterfish.rig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A debugging tool, not a check: plays the tuple of the log {@code SF_INSPECT_LOG} with the Rig's Brain on
 * the real Codex and the committed weights, and writes that Run's log under {@code SF_INSPECT_OUT}, so an
 * Overlay Run can be set beside the Brain the Rig measures (both read the Codex the same way now). It plays
 * the tuple of a log the person names, so it is a diagnosis aid and never a ranked Run. {@code SF_ROOT} is
 * the repository root. Skipped unless {@code SF_INSPECT_LOG} is set.
 */
class InspectRigBrainTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "SF_INSPECT_LOG", matches = ".+")
    void play() throws IOException {
        Path root = Path.of(required("SF_ROOT", "the repository root"));
        RunLog.Header header = RunLogReader.of(Path.of(System.getenv("SF_INSPECT_LOG"))).header();
        Path out = Files.createDirectories(Path.of(required("SF_INSPECT_OUT", "the folder the Run log goes in")));
        String tag = HeadlessBoot.pinnedTag();
        Brain brain = new Brain(CodexKnowledge.read(root.resolve("codex").resolve(tag), tag),
                WeightsFile.read(root.resolve("weights/shatterfish.json"), "shatterfish"), DeciderSeeds.brain("shatterfish"));
        new RunLoop().playTriple(new SeedSet.Entry(header.seed(), header.heroClass(), header.challenges(), header.seedCode()),
                header.salt(), new BrainDecider(brain), 5000,
                new RunLoop.Logging(Files.createTempDirectory(out, "rig"), "0".repeat(40),
                        new RunLog.Brain("inspect.rig", "0".repeat(40), "0".repeat(64)), "", "inspect"));
    }

    private static String required(String name, String what) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is " + what + ", and is not set");
        }
        return value;
    }
}
