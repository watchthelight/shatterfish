package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rig's runner: what it refuses, what it writes down, and what it does with a Run that does not
 * come back (story 3.3).
 */
class RunnerTest {

    private static final String COMMIT = "0".repeat(40);

    private static Map<String, String> arguments(Path out, String... extra) {
        Map<String, String> given = new LinkedHashMap<>();
        given.put(Runner.BRAIN, Brains.RANDOM);
        given.put(Runner.SEEDS, SeedSets.SMOKE);
        given.put(Runner.PARALLEL, "2");
        given.put(Runner.OUT, out.toString());
        given.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        given.put(Runner.COMMIT, COMMIT);
        for (int i = 0; i < extra.length; i += 2) {
            given.put(extra[i], extra[i + 1]);
        }
        return given;
    }

    // ------------------------------------------------------------------------ the command line

    @Test
    @DisplayName("an argument the Rig does not know is refused by name, never ignored")
    void the_command_line_refuses_what_it_does_not_know() {
        // Named rather than dropped, and the known flags are printed, for one reason: the flag this
        // command must never grow is an oracle, and a silently ignored argument is how someone
        // convinces themselves it has one (FR-11, non-negotiable 1).
        for (String[] bad : new String[][] {{"--oracle", "true"}, {"--Oracle", "1"}, {"--parralel", "2"}}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Runner.arguments(bad));
            assertTrue(refused.getMessage().contains("does not know"), refused.getMessage());
            assertTrue(refused.getMessage().contains(bad[0]), refused.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> Runner.arguments(new String[] {"--brain"}),
                "a flag with no value");
        assertThrows(IllegalArgumentException.class,
                () -> Runner.arguments(new String[] {"random", "--brain"}), "a value with no flag");
        assertThrows(IllegalArgumentException.class,
                () -> Runner.arguments(new String[] {"--brain", "a", "--brain", "b"}), "a flag twice");

        // There is no oracle flag, in any spelling. This is the list, and it does not hold one.
        assertEquals(List.of("--brain", "--seeds", "--parallel", "--out", "--root", "--commit",
                "--cap", "--deadline"), knownFlags());
    }

    private static List<String> knownFlags() {
        return List.of(Runner.BRAIN, Runner.SEEDS, Runner.PARALLEL, Runner.OUT, Runner.ROOT,
                Runner.COMMIT, Runner.CAP, Runner.DEADLINE);
    }

    @Test
    @DisplayName("the Rig refuses a Brain it does not have, a parallelism it will not use, and a folder that is not empty")
    void the_rig_refuses_what_it_cannot_run(@TempDir Path out) throws IOException {
        IllegalArgumentException brain = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(out, Runner.BRAIN, "clairvoyant")));
        assertTrue(brain.getMessage().contains("clairvoyant") && brain.getMessage().contains("random"),
                brain.getMessage());

        for (String asked : new String[] {"0", "-1", "65", "many"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Runner.run(arguments(out, Runner.PARALLEL, asked)),
                    Runner.PARALLEL + " " + asked);
        }

        // Two invocations writing into one folder are two sets of numbers nobody can tell apart.
        Files.writeString(out.resolve("something.txt"), "already here\n", StandardCharsets.UTF_8);
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(out)));
        assertTrue(taken.getMessage().contains("not empty"), taken.getMessage());
    }

    @Test
    @DisplayName("the held-out set is refused at the door the Rig inherits, not at one it restates")
    void the_holdout_is_refused(@TempDir Path out) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(out, Runner.SEEDS, SeedSets.HOLDOUT)));
        assertTrue(refused.getMessage().contains("holdout"), refused.getMessage());
        assertTrue(refused.getMessage().contains("per Brain version"), refused.getMessage());
    }

    @Test
    @DisplayName("the default parallelism is one process per core, stated rather than assumed")
    void the_default_is_what_the_machine_says() {
        assertEquals(Math.max(1, Math.min(Runner.MOST, Runtime.getRuntime().availableProcessors())),
                Runner.defaultParallel());
        assertTrue(Runner.defaultParallel() >= 1 && Runner.defaultParallel() <= Runner.MOST);
    }

    // ------------------------------------------------------------------ what an invocation writes

    @Test
    @DisplayName("every Run is indexed, every log is written, and the summary says how fast it went")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void an_invocation_writes_its_index_and_its_summary(@TempDir Path out) throws IOException {
        Runner.run(arguments(out, Runner.CAP, "60"));

        List<String> index = Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        int expected = SeedSets.definition(SeedSets.SMOKE).size();
        assertEquals(expected, index.size(), "a line per Run the Rig started");
        for (String line : index) {
            assertEquals("FINISHED", LogHeader.string(line, "state"), line);
            assertTrue(LogHeader.string(line, "chain").matches("[0-9a-f]{64}"), line);
            Path log = out.resolve(LogHeader.string(line, "log"));
            assertTrue(Files.isRegularFile(log), log + " is the log its index line names");
            assertEquals(LogHeader.string(line, "runId"), LogHeader.of(log).runId(),
                    "the id the index gives and the id the log's own header gives");
        }

        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals(String.valueOf(expected), LogHeader.value(summary, "runsStarted"), summary);
        assertEquals(String.valueOf(expected), LogHeader.value(summary, "runsFinished"), summary);
        assertEquals("0", LogHeader.value(summary, "runsIncomplete"), summary);
        assertEquals("2", LogHeader.value(summary, "processes"), summary);
        assertTrue(Long.parseLong(LogHeader.value(summary, "waits")) > 0, summary);
        assertTrue(Long.parseLong(LogHeader.value(summary, "runsPerSecondThousandths")) > 0,
                "a throughput this invocation measured: " + summary);
    }

    @Test
    @DisplayName("a Run that does not come back in time is killed, counted, and its partial log kept")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void a_run_past_its_deadline_is_incomplete(@TempDir Path out) throws IOException {
        // A deadline of one second against a Run that takes longer: the child is destroyed, which
        // is the same thing that happens to a Run on a machine that goes away. What matters is that
        // it is counted and that whatever it wrote is still there -- ADR-0012 scores an incomplete
        // Run's pair as a tie, and a Run with no line at all is not scored, which removes it from
        // the set and biases what is left.
        Runner.run(arguments(out, Runner.CAP, "20000", Runner.DEADLINE, "1"));

        List<String> index = Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        assertEquals(SeedSets.definition(SeedSets.SMOKE).size(), index.size(),
                "every Run the Rig started is in the index, finished or not");
        long incomplete = index.stream().filter(l -> "INCOMPLETE".equals(LogHeader.string(l, "state"))).count();
        assertTrue(incomplete > 0, "a one-second deadline stopped some Runs: " + index.size());

        for (String line : index) {
            if (!"INCOMPLETE".equals(LogHeader.string(line, "state"))) {
                continue;
            }
            assertFalse(LogHeader.string(line, "why").isEmpty(), "an incomplete Run says why: " + line);
            Path log = out.resolve(LogHeader.string(line, "log"));
            // The partial log is kept. A killed Run's evidence is the thing the Rig must not lose.
            assertTrue(Files.isRegularFile(log), "the partial log is still there: " + log);
            assertFalse(LogHeader.of(log).complete(), "and it has no end record: " + log);
        }

        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals(String.valueOf(incomplete), LogHeader.value(summary, "runsIncomplete"), summary);
    }

    // ----------------------------------------------------------------------- the oracle refusal

    @Test
    @DisplayName("a Run whose own header claims the oracle fails the invocation, and nothing is published")
    void an_oracle_run_fails_the_invocation(@TempDir Path out) throws IOException {
        // The Rig cannot produce this log: there is no oracle flag on its command line in any
        // spelling, which the test above holds. So the refusal is handed one directly -- a guard
        // nothing can reach is a guard nobody knows works, which is what story 3.2's battery found
        // by deleting one with every test still green.
        String runId = "v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-random";
        Files.writeString(out.resolve(runId + ".jsonl"),
                "{\"oracle\":true,\"t\":\"header\",\"tag\":\"v4.0.0\"}\n", StandardCharsets.UTF_8);
        RunIndex index = new RunIndex(out);
        index.started(new RunIndex.Entry(runId, runId + ".jsonl", RunIndex.State.STARTED, "", 1,
                "WARRIOR", 0, 7, "", 0, ""));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> Runner.finish(index, out, runId, "", 1, new AtomicLong()));

        assertTrue(refused.getMessage().contains(runId), refused.getMessage());
        assertTrue(refused.getMessage().contains("what a player could not"), refused.getMessage());
        assertTrue(refused.getMessage().contains("FR-11"), refused.getMessage());
    }

    @Test
    @DisplayName("a Run that left no log at all is still counted, because a Run nobody counted is a Run nobody misses")
    void a_run_with_no_log_is_incomplete(@TempDir Path out) {
        String runId = "v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-random";
        RunIndex index = new RunIndex(out);
        index.started(new RunIndex.Entry(runId, runId + ".jsonl", RunIndex.State.STARTED, "", 1,
                "WARRIOR", 0, 7, "", 0, ""));

        Runner.finish(index, out, runId, "the Run could not be started", 5, new AtomicLong());

        assertEquals(1, index.count(RunIndex.State.INCOMPLETE));
        assertEquals(RunIndex.State.INCOMPLETE, index.entries().get(0).state());
        assertEquals("the Run could not be started", index.entries().get(0).why());
    }

    @Test
    @DisplayName("the index refuses to record the end of a Run it never wrote down as started")
    void an_ending_without_a_beginning_is_refused(@TempDir Path out) {
        RunIndex index = new RunIndex(out);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> index.ended("never-started", RunIndex.State.FINISHED, "", "ended", 1, ""));
        assertTrue(refused.getMessage().contains("before a Run is dispatched"), refused.getMessage());
    }
}
