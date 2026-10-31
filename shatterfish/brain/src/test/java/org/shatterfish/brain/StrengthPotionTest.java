package org.shatterfish.brain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 10.17 lever 1 (issue #363): a known potion of strength is drunk as soon as the screen is calm. A
 * potion of strength is a permanent point (PotionOfStrength.java:40-47) and the pack held one unused on 71
 * percent of heroes by depth 7 (docs/results/2026-10-30-10-17-the-power-curve.md). The first test of the
 * story: it fails on main, where the shipped Playbook states {@code known_gains} 0.
 */
class StrengthPotionTest {

    @Test
    @DisplayName("the shipped Playbook drinks a known potion of strength; the reference Playbook still holds it")
    void shipped_playbook_drinks_known_gains() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("playbooks/shatterfish.json"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "the repository root holds playbooks/shatterfish.json");
        String shipped = Files.readString(root.resolve("playbooks/shatterfish.json"), StandardCharsets.UTF_8);
        assertFalse(shipped.contains("\"known_gains\":0"), "the shipped Playbook never holds a known potion of strength");
        assertTrue(shipped.contains("\"known_gains\":1"), "the shipped Playbook states known_gains 1");
        assertTrue(Files.readString(root.resolve("playbooks/oneply.json"), StandardCharsets.UTF_8)
                .contains("\"known_gains\":0"), "the reference Playbook keeps the old hold");
    }
}
