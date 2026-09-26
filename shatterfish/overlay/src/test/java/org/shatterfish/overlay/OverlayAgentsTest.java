package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Who plays an Overlay Run (story 5.1). */
class OverlayAgentsTest {

    private static final Path WEIGHTS = Path.of("../../weights/shatterfish.json");

    @Test
    @DisplayName("random by default, the Brain on its committed weights when asked")
    void the_agents() {
        LaunchOptions random = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior"});
        assertInstanceOf(RandomAgent.class, OverlayAgents.of(random).get());
        assertEquals("random", OverlayAgents.name(random));

        LaunchOptions brain = LaunchOptions.parse(new String[] {"--seed", "1", "--class", "warrior",
                "--agent", "brain", "--weights", WEIGHTS.toString()});
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
                "--agent", "brain", "--weights", WEIGHTS.toString()});
        LaunchOptions other = LaunchOptions.parse(new String[] {"--seed", "987654321", "--class", "mage",
                "--salt", "1234", "--agent", "brain", "--weights", WEIGHTS.toString()});
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
}
