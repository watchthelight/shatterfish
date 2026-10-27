package org.shatterfish.rig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.harness.agent.RunOutcome;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Issue 328: the Mage Run XMK-SUI-KRX ended UNSERVED_SCENE on depth 2 after a ranged throw. */
class UnservedSceneAfterThrowTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_mage_run_does_not_end_on_a_scene_nobody_serves(@TempDir Path out) {
        Path root = SeedSetsTest.ROOT;
        Map<String, String> a = new LinkedHashMap<>();
        a.put(RunOne.SEED, "4902716517241");
        a.put(RunOne.CLASS, "MAGE");
        a.put(RunOne.SALT, Long.toString(Long.parseUnsignedLong("93f06482b25d3201", 16)));
        a.put(RunOne.OUT, out.toAbsolutePath().toString());
        a.put(RunOne.COMMIT, "repro");
        a.put(RunOne.BRAIN, Brains.SHATTERFISH);
        a.put(RunOne.BRAIN_COMMIT, "repro");
        a.put(RunOne.CHALLENGES, "0");
        a.put(RunOne.CODEX, root.resolve("codex/v4.0.2").toAbsolutePath().toString());
        a.put(RunOne.WEIGHTS, root.resolve("weights/shatterfish.json").toAbsolutePath().toString());
        a.put(RunOne.PLAYBOOK, root.resolve("playbooks/shatterfish.json").toAbsolutePath().toString());
        RunOutcome outcome = RunOne.play(a);
        
        assertNotEquals(RunOutcome.Cause.UNSERVED_SCENE, outcome.cause(), outcome.toString());
    }
}
