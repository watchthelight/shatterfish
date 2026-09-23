package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first Brain, in real Runs through the Rig (story 4.1): built in the child on the Codex its
 * caller read, and every wait it served is recorded with the Decision behind it and the Belief it
 * held.
 */
class BaselineRunTest {

    @Test
    @DisplayName("every wait the baseline Brain serves is logged with its Decision and Belief")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_log_says_why(@TempDir Path out) throws IOException {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.BASELINE);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        arguments.put(Runner.PARALLEL, "4");
        arguments.put(Runner.CAP, "60");

        Runner.run(arguments);

        List<Path> logs = Verify.logs(out);
        assertEquals(SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().size(), logs.size());
        int waits = 0;
        for (Path log : logs) {
            RunLogReader.Log read = RunLogReader.of(log);
            assertTrue(read.readable(), log + ": " + read.unreadable());
            assertEquals(Brains.BASELINE, read.header().brain().name());
            for (RunLog.Wait wait : read.waits()) {
                assertNotNull(wait.decision(), "a Brain's wait says why: " + log.getFileName() + " at " + wait.k());
                assertTrue(List.of("answer-prompt", "fallback").contains(wait.decision().policy()), wait.decision().policy());
                assertEquals(wait.action(), wait.decision().chosen().action(), "the Action logged is the one decided");
                assertTrue(wait.belief().matches("[0-9a-f]{64}"), wait.belief());
                waits++;
            }
        }
        assertTrue(waits > 0, "the Runs served waits");
        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals("0", LogHeader.value(summary, "runsIncomplete"), summary);
    }

    @Test
    @DisplayName("a Run of the baseline Brain states the Codex it is built on, and one that does not is refused")
    void built_on_the_codex() {
        org.shatterfish.api.SeedSet.Entry triple = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().get(0);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Brains.of(Brains.BASELINE, triple));
        assertTrue(refused.getMessage().contains(RunOne.CODEX), refused.getMessage());

        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        org.shatterfish.api.Codex.Manifest codex = CodexManifest.read(
                SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), tag);
        assertEquals(org.shatterfish.api.Codex.VERSION, codex.version());
        assertTrue(Brains.of(Brains.BASELINE, triple, codex) instanceof org.shatterfish.brain.BrainDecider);
        assertThrows(IllegalArgumentException.class, () -> CodexManifest.read(
                SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), "v0.0.1"),
                "a Codex for another tag is not this build's");
        assertFalse(Brains.version(SeedSetsTest.ROOT, Brains.BASELINE).isEmpty(),
                "the Brain's version is the commit that last changed brain/");
    }
}
