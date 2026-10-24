package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Who plays an Overlay Run (story 5.1). */
class OverlayAgentsTest {

    private static final Path WEIGHTS = Path.of("../../weights/shatterfish.json");

    /** The checked-in Codex, from this module's directory, where Gradle runs its tests. */
    private static final Path CODEX = Path.of("../../codex/v4.0.0");

    @Test
    @DisplayName("random by default, the Brain on its committed weights when asked")
    void the_agents() {
        LaunchOptions random = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertInstanceOf(RandomAgent.class, OverlayAgents.of(random).get());
        assertEquals("random", OverlayAgents.name(random));

        LaunchOptions brain = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "brain", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        assertInstanceOf(BrainDecider.class, OverlayAgents.of(brain).get());
        assertEquals("shatterfish", OverlayAgents.name(brain));
        Weights weights = OverlayAgents.weights(WEIGHTS);
        assertEquals("shatterfish", weights.name());

        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(
                new String[] {"--seed", "1", "--class", "warrior", "--agent", "oracle"}));
    }

    @Test
    @DisplayName("the Brain's stream is seeded as the rig seeds it, from its name and nothing about the Run")
    void the_brain_seed() {
        LaunchOptions brain = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "brain", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        LaunchOptions other = LaunchOptions.parse(new String[] {"--seed", "987654321", "--class", "mage",
                "--salt", "1234", "--agent", "brain", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        long seeded = ((BrainDecider) OverlayAgents.of(brain).get()).brain().seed();
        // The seed the rig's Brains.brainSeed gives the Brain of this name, through the one DeciderSeeds
        // both use; and nothing about the Run moves it.
        assertEquals(DeciderSeeds.brain(OverlayAgents.name(brain)), seeded);
        assertEquals(seeded, ((BrainDecider) OverlayAgents.of(other).get()).brain().seed(),
                "another tuple and salt, the same stream");
    }

    @Test
    @DisplayName("the random agent is seeded from the triple, as the Rig seeds it, and never from the salt")
    void the_random_agent_seed() {
        LaunchOptions one = LaunchOptions.parse(new String[] {"--seed", "987654321", "--class", "mage"});
        LaunchOptions salted = LaunchOptions.parse(new String[] {"--seed", "987654321", "--class", "mage",
                "--salt", "1234"});
        LaunchOptions other = LaunchOptions.parse(new String[] {"--seed", "987654321", "--class", "rogue"});
        assertEquals(DeciderSeeds.agent(987654321L, org.shatterfish.api.HeroClass.MAGE, 0), one.agentSeed());
        assertEquals(one.agentSeed(), salted.agentSeed(), "the salt moves nothing");
        assertNotEquals(one.agentSeed(), other.agentSeed(), "the hero class does");
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(
                new String[] {"--seed", "1", "--class", "warrior", "--agent-seed", "7"}), "no second seed to state");
    }

    @Test
    @DisplayName("the embedded Brain is built on the Rig's knowledge: the Codex tables and the bestiary, not an empty Codex")
    void the_brain_knows_the_codex_and_the_bestiary() {
        LaunchOptions brain = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "brain", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        Codex.Knowledge knowledge = ((BrainDecider) OverlayAgents.of(brain).get()).brain().knowledge();
        assertFalse(knowledge.families().isEmpty(), "the identifiable families");
        assertFalse(knowledge.threats().isEmpty(), "the enemies' figures");
        assertFalse(knowledge.weapons().isEmpty(), "the measured gear");
        assertEquals(HeadlessBoot.pinnedTag(), knowledge.manifest().upstreamTag());
        assertFalse(knowledge.bestiary().isEmpty(), "the bestiary");
        assertEquals("passive", knowledge.tactics("animated statue", 3).ai(), "a statue is scenery to it");
        assertEquals(CodexKnowledge.read(CODEX, HeadlessBoot.pinnedTag()), knowledge, "exactly what the Rig's Brain gets");
    }

    @Test
    @DisplayName("with no Codex or no bestiary where it looks, the Brain is not attached with an empty one: the launch fails")
    void no_codex_no_launch(@TempDir Path empty) throws java.io.IOException {
        String[] missing = {"--seed", "1", "--class", "warrior", "--agent", "brain", "--weights", WEIGHTS.toString(),
                "--codex", empty.resolve("codex/v4.0.0").toString()};
        assertThrows(IllegalArgumentException.class, () -> OverlayAgents.of(LaunchOptions.parse(missing)), "no Codex");
        // A Codex folder with no bestiary two folders up.
        Path codex = java.nio.file.Files.createDirectories(empty.resolve("codex/v4.0.0"));
        for (java.io.File table : CODEX.toFile().listFiles()) {
            java.nio.file.Files.copy(table.toPath(), codex.resolve(table.getName()));
        }
        assertThrows(IllegalArgumentException.class, () -> OverlayAgents.of(LaunchOptions.parse(missing)),
                "no tactics/bestiary.json");
        String[] human = missing.clone();
        human[5] = "human";
        assertThrows(IllegalArgumentException.class, () -> OverlayAgents.of(LaunchOptions.parse(human)),
                "the human's shadow Brain too");
    }

    @Test
    @DisplayName("the log header names the Brain's configuration and the knowledge it was built on; the others state none")
    void the_config_hash() {
        LaunchOptions brain = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "brain", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        String hash = OverlayAgents.configHash(brain);
        assertEquals(org.shatterfish.harness.knowledge.ConfigHash.of(org.shatterfish.brain.Brain.configuration(
                OverlayAgents.weights(WEIGHTS)), OverlayAgents.BRAIN_SEED, CodexKnowledge.digest(CODEX)), hash,
                "the Rig's formula, on the same weights, seed and knowledge");
        assertNotEquals("0".repeat(64), hash);
        assertEquals(hash, ShatterfishLauncher.logging(brain, "test").brain().configHash(), "and the header carries it");
        LaunchOptions random = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertEquals("0".repeat(64), OverlayAgents.configHash(random));
        LaunchOptions human = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "human", "--weights", WEIGHTS.toString(), "--codex", CODEX.toString()});
        assertEquals(hash, OverlayAgents.configHash(human), "a human's Run names the shadow Brain's configuration");
    }

    @Test
    @DisplayName("the Codex is looked for under the repository root by default, where :overlay:launch runs")
    void the_default_codex() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertEquals(Path.of("codex", HeadlessBoot.pinnedTag()), options.codex());
    }
}
