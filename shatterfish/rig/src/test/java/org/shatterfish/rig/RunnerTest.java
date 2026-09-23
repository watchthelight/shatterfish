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

        // There is no oracle flag, in any spelling. This is the list the *code* checks against --
        // not a copy built from the same constants, which is what this used to compare and which
        // would have let a ninth flag in unnoticed.
        assertEquals(List.of("--brain", "--seeds", "--parallel", "--out", "--root", "--commit",
                        "--cap", "--deadline", "--verify", "--replay", "--finished"), Runner.KNOWN,
                "a flag added to the Rig is a decision, and this is where it is made");
    }

    @Test
    @DisplayName("a flag is never a value, and a value is never empty")
    void a_flag_is_not_a_value() {
        // `--commit --root` used to attest the string "--root" as the Shatterfish commit in every
        // header of the invocation, and `--out --root` wrote the whole thing into a folder of that
        // name.
        IllegalArgumentException asValue = assertThrows(IllegalArgumentException.class,
                () -> Runner.arguments(new String[] {Runner.COMMIT, Runner.ROOT}));
        assertTrue(asValue.getMessage().contains("as its value"), asValue.getMessage());
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> Runner.arguments(new String[] {Runner.COMMIT, ""}));
        assertTrue(empty.getMessage().contains("not left empty"), empty.getMessage());
    }

    @Test
    @DisplayName("the command the methodology page publishes is a command the Rig accepts")
    void the_published_command_is_accepted() throws IOException {
        // It was not. `--commit` was required, and the page, the runner's own javadoc, the Gradle
        // task's comment and this story's Verification section all omitted it -- so none of them
        // ran, and the Verification section said one of them had.
        String page = Files.readString(SeedSetsTest.ROOT.resolve("docs/methodology.md"),
                StandardCharsets.UTF_8);
        int at = page.indexOf("--args=");
        assertTrue(at > 0, "the page publishes the command");
        int opens = page.indexOf('"', at);
        String published = page.substring(opens + 1, page.indexOf('"', opens + 1));

        Map<String, String> parsed = Runner.arguments(published.trim().split("\\s+"));

        assertEquals(Brains.RANDOM, parsed.get(Runner.BRAIN), published);
        assertTrue(parsed.containsKey(Runner.SEEDS) && parsed.containsKey(Runner.OUT), published);
        for (String flag : parsed.keySet()) {
            assertTrue(Runner.KNOWN.contains(flag), flag + " is not a flag the Rig knows: " + published);
        }
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
    @DisplayName("an invocation not told how many processes to use takes the default, and records it")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_default_is_used_and_recorded(@TempDir Path out) throws IOException {
        // Every test used to pass `--parallel`, so the fallback was dead code in the suite: change
        // it to `return 1` and nothing failed, while the page's claim became false and every
        // default invocation ran one core at a time.
        Map<String, String> given = arguments(out, Runner.CAP, "40");
        given.remove(Runner.PARALLEL);

        Runner.run(given);

        // The expectation is the machine's own core count, not `defaultParallel()` -- a mutant
        // that made the default 1 made both sides 1, and this test, written in this story to fix
        // exactly that shape, reproduced it. A battery found it; four reviews had not.
        int cores = Math.min(Runner.MOST, Runtime.getRuntime().availableProcessors());
        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals(String.valueOf(cores), LogHeader.value(summary, "processes"),
                "an invocation with no --parallel uses one process per core");
        assertEquals(cores, Runner.defaultParallel(), "which is what the default says it is");

        // And the cap the invocation ran under, because two invocations at different caps are not
        // the same measurement and the run ids and headers are identical either way.
        assertEquals("40", LogHeader.value(summary, "turnCap"), summary);
    }

    @Test
    @DisplayName("the rate the summary publishes is thousandths of a per-second rate, and the page quotes it so")
    void the_published_rate_is_thousandths() throws IOException {
        // The only assertion on this used to be "above zero". Drop the factor of a thousand and the
        // summary would report 0.00185 Runs/s as `1` while the page's table stayed unreproducible.
        assertEquals(3_102, RunIndex.rate(500, 161_199), "500 Runs in 161.199 s");
        assertEquals(1_852, RunIndex.rate(25, 13_502), "25 Runs in 13.502 s");
        assertEquals(1_000, RunIndex.rate(1, 1_000), "one Run a second");
        assertEquals(0, RunIndex.rate(7, 0), "no time has passed, so no rate has been measured");

        // And the page's table quotes that unit: each row's Runs/s is what its own Runs and wall
        // clock give, to within the hundredth the wall clock itself is rounded to. A row nobody can
        // recompute from the row is a number nobody can check.
        String page = Files.readString(SeedSetsTest.ROOT.resolve("docs/methodology.md"),
                StandardCharsets.UTF_8);
        for (String[] row : new String[][] {{"25", "13.5", "1.85"}, {"500", "161", "3.10"}}) {
            double fromTheRow = RunIndex.rate(Long.parseLong(row[0]),
                    Math.round(Double.parseDouble(row[1]) * 1000)) / 1000.0;
            double quoted = Double.parseDouble(row[2]);
            assertTrue(page.contains("| " + row[2] + " |"),
                    "the page quotes " + row[2] + " Runs/s for " + row[0] + " Runs in " + row[1] + " s");
            assertTrue(Math.abs(fromTheRow - quoted) <= 0.02,
                    row[0] + " Runs in " + row[1] + " s is " + fromTheRow + " Runs/s, and the page says "
                            + quoted);
        }
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
            assertEquals("", LogHeader.string(line, "cause"),
                    "a Run that did not finish has no ending to report: " + line);
            Path log = out.resolve(LogHeader.string(line, "log"));
            Path said = out.resolve(LogHeader.string(line, "runId") + ".err");
            // Whatever the Run left is kept. A child killed one second in may not have reached the
            // point of creating its log, so the evidence is the log or what it printed -- asserting
            // only the log made this test fail whenever a JVM start ran long.
            assertTrue(Files.isRegularFile(log) || Files.isRegularFile(said),
                    "a killed Run left neither a log nor a word: " + log);
            if (Files.isRegularFile(log)) {
                assertFalse(LogHeader.of(log).complete(), "and its log has no end record: " + log);
            }
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
    @DisplayName("a log the Rig cannot read is not a Run it can vouch for")
    void an_unreadable_log_is_not_a_fair_run(@TempDir Path out) throws IOException {
        // Including about the oracle: a log nobody can parse says nothing, and counting it as a
        // fair Run is the one reading of it that cannot be justified.
        String runId = "v4.0.0-WARRIOR-0-AAA-AAA-AAB-0000000000000007-random";
        Files.writeString(out.resolve(runId + ".jsonl"), "this is not a Run log\n", StandardCharsets.UTF_8);
        RunIndex index = new RunIndex(out);
        index.started(new RunIndex.Entry(runId, runId + ".jsonl", RunIndex.State.STARTED, "", 1,
                "WARRIOR", 0, 7, "", 0, ""));

        Runner.finish(index, out, runId, "", 1, new AtomicLong());

        assertEquals(RunIndex.State.INCOMPLETE, index.entries().get(0).state());
        assertTrue(index.entries().get(0).why().contains("could not be read"),
                index.entries().get(0).why());
    }

    @Test
    @DisplayName("a Brain that has not said what it is configured as cannot have that written down for it")
    void a_brain_states_its_own_configuration() {
        // The Baseline has no configuration, and says so with a digest of zeros. Anything else
        // would be publishing an unfalsifiable claim in every header it wrote, and the field is
        // what a Registration pins.
        assertEquals("0".repeat(64), Brains.configHash(Brains.RANDOM));
        assertThrows(IllegalArgumentException.class, () -> Brains.configHash("greedy"),
                "a Brain the Rig does not have");
        assertEquals(List.of(Brains.RANDOM), Brains.names(),
                "when a real Brain is added here, `configHash` refuses until it states its own");
    }

    @Test
    @DisplayName("a refusal comes back out of the invocation in its own words, and marks the folder")
    void a_refusal_reaches_the_caller(@TempDir Path out) {
        // Through `Future.get` a worker's exception arrives wrapped, and the runner used to rewrap
        // it again as "a Run could not be dispatched" -- so the refusal's own message, which names
        // the Run and FR-11, sat two levels down a cause chain where the operator never saw it.
        IllegalStateException refusal = new IllegalStateException("the Run r saw what a player could not (FR-11)");
        java.util.concurrent.FutureTask<Void> failing = new java.util.concurrent.FutureTask<>(() -> {
            throw refusal;
        });
        failing.run();

        assertEquals(refusal, Runner.await(List.of(failing)), "the refusal itself, not a wrapper");

        // And the folder says the invocation was refused rather than looking like a finished one.
        RunIndex index = new RunIndex(out);
        index.started(new RunIndex.Entry("r", "r.jsonl", RunIndex.State.STARTED, "", 1, "WARRIOR",
                0, 7, "", 0, ""));
        // Through `Runner.refuse`, which is the one statement the invocation uses: the marking and
        // the throwing travel together there, so neither can go missing on its own. Calling
        // `index.refused` here instead is what let a battery delete the marking with this test
        // still green.
        assertEquals(refusal, Runner.refuse(index, refusal), "the reason, to be thrown");
        assertEquals(RunIndex.State.REFUSED, index.entries().get(0).state());
        assertTrue(Files.isRegularFile(out.resolve(RunIndex.REFUSED)),
                "a reader who picks this folder up finds the refusal beside the numbers");
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
