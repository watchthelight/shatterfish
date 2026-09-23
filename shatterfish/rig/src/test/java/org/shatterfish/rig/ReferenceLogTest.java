package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.RunLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed reference log, and the job that replays it (story 3.4).
 *
 * <p>A Run is meant to be a property of its tuple and nothing else, and the one difference no test
 * on one machine can find is a difference between machines. The nightly {@code replay} workflow
 * spends a Run on each platform to look for one; this test is what stops that job from being
 * quietly pointed at nothing — the shape of failure where a workflow goes green because the file it
 * was checking is gone, which is worse than no job at all.
 *
 * <p>It does not replay. Replaying costs a Run and the workflow is where that is spent; what this
 * holds is that the file is there, that it verifies against its own bytes, that it is a whole Run
 * this build can still read, and that the workflow actually runs the commands it claims to.
 */
class ReferenceLogTest {

    private static final Path REFERENCE = SeedSetsTest.ROOT.resolve("reference");

    @Test
    @DisplayName("the reference log is committed, whole, and verifies against its own bytes")
    void the_reference_log_is_real() {
        Verify.Report report = Verify.of(REFERENCE);

        assertTrue(report.ok(false), report.text());
        assertEquals(1, report.checked().size(),
                "one reference Run, so `--replay <the log>` is unambiguous: " + report.text());

        Verify.Checked one = report.checked().get(0);
        assertTrue(one.complete(), "the reference Run reached an ending, so a Replay has one to reach");
        assertTrue(one.chain().matches("[0-9a-f]{64}"), one.chain());
    }

    @Test
    @DisplayName("this build can still read the reference log, which is the first thing a Replay needs")
    void this_build_can_read_it() {
        Path log = Verify.logs(REFERENCE).get(0);
        LogHeader.Read read = LogHeader.of(log);

        assertTrue(read.readable(), read.unreadable());
        assertFalse(read.oracle(), "the reference Run saw what a player could see");
        assertEquals(RunLog.fileName(read.runId()), log.getFileName().toString(),
                "the log's own header names the file it is in");
        assertTrue(read.waits() > 0, "it served waits, so replaying it checks something");
        // The commit is forty zeros on purpose, and the Gradle task that writes this file says why:
        // a Replay attests what the log attests, so a real commit here would be a claim about a
        // checkout that has nothing to do with the machine replaying it.
        assertEquals("TURN_CAP", read.cause(), "the reference Run ends the same way on every machine");
    }

    @Test
    @DisplayName("the nightly workflow runs both commands, against the folder that holds the log")
    void the_workflow_runs_what_it_says() throws IOException {
        // Story 3.3 published a rig command that could not have worked, in four places at once. A
        // workflow is the one place where that mistake is invisible until a night nobody is
        // watching, so the commands it contains are read here and held against the Rig's own flags.
        String workflow = Files.readString(
                SeedSetsTest.ROOT.resolve(".github/workflows/replay.yml"), StandardCharsets.UTF_8);

        assertTrue(workflow.contains("--verify reference"),
                "the job checks the log's bytes before playing anything");
        assertTrue(workflow.contains("--replay $log"), "the job replays it");
        assertTrue(workflow.contains("ls reference/*.jsonl"),
                "the job finds the log in the folder this test verifies");
        for (String os : List.of("ubuntu-latest", "windows-latest")) {
            assertTrue(workflow.contains(os), "the job runs on " + os);
        }
        // Every `--args=` in the file, parsed by the Rig's own parser. A flag the Rig does not know
        // fails the night rather than the build, and a value nobody typed correctly fails it twice.
        int checked = 0;
        for (int at = workflow.indexOf("--args="); at >= 0; at = workflow.indexOf("--args=", at + 1)) {
            int opens = workflow.indexOf('"', at);
            String published = workflow.substring(opens + 1, workflow.indexOf('"', opens + 1));
            // The log's path is a shell variable at this point, so it stands in as a path here;
            // what is being held is the flags, which is the part a person gets wrong.
            for (String flag : Runner.arguments(published.replace("$log", "a.jsonl")
                    .trim().split("\\s+")).keySet()) {
                assertTrue(Runner.KNOWN.contains(flag), flag + " is not a flag the Rig knows: " + published);
            }
            checked++;
        }
        assertEquals(2, checked, "both commands were parsed");
    }
}
