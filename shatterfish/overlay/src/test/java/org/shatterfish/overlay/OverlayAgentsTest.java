package org.shatterfish.overlay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.agent.RandomAgent;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        // The rig's Brains.BRAIN_STREAM (story 4.1's fairness rule), which the Overlay cannot import.
        assertEquals(0x5F15_B4A1L, OverlayAgents.BRAIN_STREAM);
    }
}
