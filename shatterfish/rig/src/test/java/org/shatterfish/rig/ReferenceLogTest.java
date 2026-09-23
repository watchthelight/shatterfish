package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed reference Run, and the job that replays it (story 3.4).
 *
 * <p>A Run is meant to be a property of its tuple and nothing else, and the one difference no test
 * on one machine can find is a difference between machines. The nightly {@code replay} workflow
 * spends a Run on each platform to look for one; this test is what stops that job from being
 * quietly pointed at nothing — the shape of failure where a workflow goes green because the file it
 * was checking is gone, or is no longer the Run anybody meant.
 *
 * <p><b>It pins which Run.</b> The first draft checked that the folder held <em>a</em> log which
 * verified, read, and ended at the cap, and never which one — so any valid log swapped in passed
 * every assertion. The same module already solves this for {@code seeds/}, where the derivation is
 * re-run and a difference fails, and the reference Run gets the same treatment: the tuple is held
 * against the constants that generate it, and the chain is a committed value, so regenerating the
 * reference is a visible decision rather than a silent swap.
 *
 * <p>It does not replay. Replaying costs a Run and the workflow is where that is spent; what this
 * holds is that the file is there, that it is the Run it should be, that it verifies against its own
 * bytes, that this build would not refuse it, and that the workflow runs the commands it claims to.
 */
class ReferenceLogTest {

    private static final Path REFERENCE = SeedSetsTest.ROOT.resolve("reference");

    /**
     * The chain the committed reference Run ends on.
     *
     * <p>Pinned here, in the repository, because a chain a file states about itself proves only
     * that the file is consistent with itself. This value and {@code reference/runs.jsonl} are the
     * two places it is recorded outside the log, and the nightly job's whole claim is that two
     * platforms reach it.
     */
    private static final String CHAIN =
            "6e17a0bc28da285dade1787a907f017e28777facaa90d0edd80f2645b5b6929e";

    /**
     * What {@code ls reference/*.jsonl | grep -v '/runs\.jsonl$' | head -1} selects.
     *
     * <p>Worked out here rather than asserted about the workflow's text, because the text was
     * right and the selection was wrong. {@code ls} sorts by name in the C collation, which for
     * these names is the same as sorting the strings.
     */
    private static String selected() {
        return Verify.logs(REFERENCE).stream()
                .map(file -> file.getFileName().toString())
                .filter(name -> !name.equals(RunIndex.RUNS))
                .sorted()
                .findFirst()
                .orElse("");
    }

    @Test
    @DisplayName("the reference folder holds the Run the generator names, and no other")
    void the_reference_log_is_the_one_it_should_be() {
        List<Path> logs = Verify.logs(REFERENCE);

        assertEquals(1, logs.size(), "one reference Run, so `--replay <the log>` is unambiguous: " + logs);
        assertEquals(Reference.fileName(), logs.get(0).getFileName().toString(),
                "the committed log is the Run `:rig:reference` writes");

        RunLog.Header header = RunLogReader.of(logs.get(0)).header();
        assertEquals(Reference.SEED, header.seed());
        assertEquals(SeedSet.code(Reference.SEED), header.seedCode());
        assertEquals(Reference.SALT, header.salt());
        assertEquals(Reference.HERO, header.heroClass());
        assertEquals(Reference.CHALLENGES, header.challenges());
        assertEquals(Reference.CAP, header.cap());
        assertEquals(Reference.BRAIN, header.brain().name());
        assertEquals(Reference.COMMIT, header.commit(),
                "nobody's build, because a Replay attests what the log attests");
        assertFalse(header.oracle(), "the reference Run saw what a player could see");
    }

    @Test
    @DisplayName("it verifies against its own bytes, and against the chain committed beside it")
    void the_reference_log_verifies() {
        Verify.Report report = Verify.of(REFERENCE);

        // `ok(false)`: a reference Run that was truncated to its header would otherwise pass this
        // and then be "replayed" as a one-line Run that reports success on both platforms.
        assertTrue(report.ok(false), report.text());
        assertEquals(1, report.checked().size(), report.text());

        Verify.Checked one = report.checked().get(0);
        assertTrue(one.complete(), "the reference Run reached an ending, so a Replay has one to reach");
        assertTrue(one.indexed(), "its chain is published in the index beside it, not only in itself");
        assertEquals(CHAIN, one.chain(),
                "the reference Run is the one this repository committed; regenerating it is a"
                        + " change to this constant and to reference/runs.jsonl, not a silent swap");
    }

    @Test
    @DisplayName("this build would replay it rather than refuse it, which the nightly job assumes")
    void this_build_would_replay_it() {
        // The refusal check, without the Run. A Profile or Observation schema bump makes the
        // reference log permanently unreplayable, and the job that would find that out runs at
        // night: this says so at the moment the bump is made.
        RunLog.Header header = RunLogReader.of(Verify.logs(REFERENCE).get(0)).header();

        assertNull(Replay.refusal(header), "the committed reference Run is one this build can replay");
    }

    @Test
    @DisplayName("this build can still read the reference log, which is the first thing a Replay needs")
    void this_build_can_read_it() {
        Path log = Verify.logs(REFERENCE).get(0);
        LogHeader.Read read = LogHeader.of(log);

        assertTrue(read.readable(), read.unreadable());
        assertFalse(read.oracle());
        assertEquals(RunLog.fileName(read.runId()), log.getFileName().toString(),
                "the log's own header names the file it is in");
        assertTrue(read.waits() > 0, "it served waits, so replaying it checks something");
        assertEquals("TURN_CAP", read.cause(), "the reference Run ends the same way on every machine");
        assertEquals(CHAIN, read.chain());
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
        assertTrue(workflow.contains("--finished"),
                "and requires the reference Run to be one that finished, or a log truncated to its"
                        + " header would pre-flight clean and be replayed as a one-line Run");
        assertTrue(workflow.contains("--replay $log"), "the job replays it");
        assertTrue(workflow.contains("ls reference/*.jsonl"),
                "the job finds the log in the folder this test verifies");
        // And it finds the *log*, not the index beside it. The first version of this assertion
        // held that the workflow contained the glob, which it did, while the glob selected
        // runs.jsonl -- `r` sorts before `v` -- and the job replayed a file that is not a Run log.
        // Checking that a command was written is not checking that it does anything.
        assertTrue(workflow.contains("grep -v '/runs\\.jsonl$'"),
                "the job excludes the run index from what it replays");
        assertEquals(Reference.fileName(), selected(),
                "the shell's own selection is the reference Run, not the index beside it");
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

        // And the job re-runs when the code that decides its answer changes. `Runner` holds the
        // exit codes the workflow branches on and was missing from the list.
        for (String watched : List.of("reference/**", "shatterfish/harness/src/main/java/org/shatterfish/harness/log/**",
                "shatterfish/rig/src/main/java/org/shatterfish/rig/Runner.java",
                "shatterfish/rig/src/main/java/org/shatterfish/rig/Verify.java")) {
            assertTrue(workflow.contains(watched), "a push touching " + watched + " re-runs the job");
        }
    }
}
