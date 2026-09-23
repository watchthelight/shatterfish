package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two Runs at once are two Runs (story 3.3, AD-6).
 *
 * <p>The claim isolation makes is not about directories. It is that <b>what a Run produces does not
 * depend on what else was running</b> — a measurement taken four at a time is the same measurement
 * taken one at a time, or the Rig's parallelism is silently changing the numbers it exists to
 * collect. The game's state is static and process-wide, so in one process that claim is simply
 * false, which is why AD-6 puts one Run in one process and why this test is the one that would
 * notice if that ever stopped being true.
 *
 * <p>So one tuple is played five times under one salt — four at once and one alone — and the
 * chains are compared. A chain covers everything about a Run except how long the decider took and
 * which machine it ran on, so the four agreeing with the fifth is the whole Run agreeing, wait by
 * wait. The salt has to be held fixed for that comparison to mean anything: the Rig draws one per
 * Run on purpose (FR-22), so two invocations of it play different Runs of the same seed.
 */
class RunnerIsolationTest {

    private static final String COMMIT = "0".repeat(40);

    /**
     * Short Runs: what is under test is whether concurrency changes a Run, and a Run that reaches
     * its first floor and stops has already read the game's statics, built a level and served
     * waits. A longer Run would say the same thing more slowly.
     */
    private static final String CAP = "80";

    private static Map<String, String> arguments(Path out, int parallel) {
        Map<String, String> given = new LinkedHashMap<>();
        given.put(Runner.BRAIN, Brains.RANDOM);
        given.put(Runner.SEEDS, SeedSets.SMOKE);
        given.put(Runner.PARALLEL, Integer.toString(parallel));
        given.put(Runner.OUT, out.toString());
        given.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        given.put(Runner.COMMIT, COMMIT);
        given.put(Runner.CAP, CAP);
        return given;
    }

    @Test
    @DisplayName("one tuple played four at a time is the Run it is played alone")
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    void concurrency_does_not_change_a_run(@TempDir Path together, @TempDir Path alone) throws Exception {
        // The salt is held fixed here, which the Rig deliberately does not do: it draws a salt per
        // Run so a Brain's author cannot precompute the stream (FR-22). Two invocations of the Rig
        // therefore play different Runs of the same seed, and comparing them would be comparing
        // salts -- the first draft of this test did exactly that and failed, correctly.
        //
        // So the same tuple and the same salt are played four times at once and once alone,
        // through the same child entry point the runner uses. A chain covers everything about a
        // Run but the clock and the machine, so four chains agreeing with the fifth is the whole
        // Run agreeing, wait by wait.
        List<Path> folders = new ArrayList<>();
        List<Process> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Path folder = Files.createDirectories(together.resolve("at-once-" + i));
            folders.add(folder);
            children.add(child(folder));
        }
        for (Process child : children) {
            assertEquals(0, child.waitFor(), "a concurrent Run failed");
        }
        Path only = Files.createDirectories(alone.resolve("by-itself"));
        assertEquals(0, child(only).waitFor(), "the Run by itself failed");

        String byItself = chainOf(only);
        assertTrue(byItself.matches("[0-9a-f]{64}"), "the Run by itself ended: " + byItself);
        for (Path at : folders) {
            assertEquals(byItself, chainOf(at),
                    "a Run played beside three others is not the Run played by itself");
        }
    }

    private static final long SEED = 3343871708117L;

    private static final long SALT = 0x5A17_5A17L;

    /** One Run of the fixed tuple, in its own process, through the runner's own child. */
    private static Process child(Path out) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path exe = Path.of(java + ".exe");
        return new ProcessBuilder(Files.exists(exe) ? exe.toString() : java.toString(),
                "-cp", System.getProperty("java.class.path"), RunOne.class.getName(),
                RunOne.SEED, Long.toString(SEED),
                RunOne.CLASS, "WARRIOR",
                RunOne.CHALLENGES, "0",
                RunOne.SALT, Long.toString(SALT),
                RunOne.OUT, out.toAbsolutePath().toString(),
                RunOne.COMMIT, COMMIT,
                RunOne.BRAIN, Brains.RANDOM,
                RunOne.BRAIN_COMMIT, COMMIT,
                RunOne.MACHINE, "test",
                RunOne.CAP, CAP)
                .directory(out.toFile())
                .redirectErrorStream(true)
                .start();
    }

    /** The one log in {@code folder}, and the chain it ended on. */
    private static String chainOf(Path folder) throws IOException {
        try (Stream<Path> held = Files.list(folder)) {
            List<Path> logs = held.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList();
            assertEquals(1, logs.size(), "one Run, one log, in " + folder);
            LogHeader.Read read = LogHeader.of(logs.get(0));
            assertTrue(read.complete(), "the Run ended: " + logs.get(0));
            return read.chain();
        }
    }

    @Test
    @DisplayName("each Run ran in the working directory the Rig gave it, in its own hand")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void every_run_ran_where_it_was_put(@TempDir Path out) throws IOException {
        Runner.run(arguments(out, 4));

        // The directories were always created; nothing established that a child was ever started in
        // one. Delete the line that hands the directory over and every child inherits the parent's,
        // writing beside each other -- which is what AD-6 forbids -- and the old assertion, which
        // only listed the names of the empty directories, passed.
        List<String> index = Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        assertTrue(index.size() >= 25, "the smoke set: " + index.size());
        for (String line : index) {
            Path working = out.resolve("work").resolve(LogHeader.string(line, "runId"));
            Path said = working.resolve(RunOne.WHERE);
            assertTrue(Files.isRegularFile(said), "the Run left no word of where it ran: " + said);
            assertEquals(working.toAbsolutePath().toString(),
                    Files.readString(said, StandardCharsets.UTF_8).strip(),
                    "and it ran where the Rig put it");
        }
    }

    @Test
    @DisplayName("a second invocation of one set draws different salts, because a salt is drawn and not derived")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_salt_is_drawn_not_derived(@TempDir Path first, @TempDir Path second) throws IOException {
        // Distinctness within one invocation is true of any injective function of the tuple, so it
        // was not the property: a derived salt would let a Brain's author precompute the stream
        // their Brain will face, which is the thing FR-22 forbids. Two invocations of one set have
        // to disagree.
        Runner.run(arguments(first, 4));
        Runner.run(arguments(second, 4));

        TreeSet<String> once = saltsBySeed(first);
        TreeSet<String> twice = saltsBySeed(second);
        TreeSet<String> shared = new TreeSet<>(once);
        shared.retainAll(twice);

        assertEquals(new TreeSet<String>(), shared,
                "two invocations of one set drew the same salt for the same seed, so the salt is a"
                        + " function of the tuple and a Brain's author can compute it");
    }

    /** Each Run's seed and salt together, so a repeat across invocations is visible. */
    private static TreeSet<String> saltsBySeed(Path out) throws IOException {
        TreeSet<String> pairs = new TreeSet<>();
        for (String line : Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8)) {
            pairs.add(LogHeader.value(line, "seed") + "/" + LogHeader.string(line, "salt"));
        }
        return pairs;
    }

    @Test
    @DisplayName("a Run refuses an out folder that is not absolute, because its working directory is its own")
    void a_run_refuses_a_relative_out() {
        // The parent always passes an absolute path, so this bites only the person debugging one
        // Run by hand -- which is exactly when nobody is watching, and the child's own javadoc
        // invites it.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RunOne.play(java.util.Map.of(RunOne.SEED, "1", RunOne.CLASS, "WARRIOR",
                        RunOne.CHALLENGES, "0", RunOne.SALT, "1", RunOne.OUT, "runs",
                        RunOne.COMMIT, COMMIT, RunOne.BRAIN, Brains.RANDOM,
                        RunOne.BRAIN_COMMIT, COMMIT)));
        assertTrue(refused.getMessage().contains("absolute path"), refused.getMessage());
    }

    @Test
    @DisplayName("no two Runs of an invocation share a log, a working directory or a salt")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void every_run_has_its_own(@TempDir Path out) throws IOException {
        Runner.run(arguments(out, 4));

        List<String> index = Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        TreeSet<String> logs = new TreeSet<>();
        TreeSet<String> salts = new TreeSet<>();
        List<String> ids = new ArrayList<>();
        for (String line : index) {
            logs.add(LogHeader.string(line, "log"));
            salts.add(LogHeader.string(line, "salt"));
            ids.add(LogHeader.string(line, "runId"));
        }
        assertEquals(index.size(), logs.size(), "one log per Run");
        assertEquals(index.size(), ids.size(), "one id per Run");
        assertEquals(index.size(), salts.size(),
                "one salt per Run, drawn when that Run executed rather than derived from its tuple");

        // Each Run also had its own working directory, and the Rig made one for every Run it
        // started. A shared one is a Run writing where another Run is reading.
        Path work = out.resolve("work");
        try (Stream<Path> held = Files.list(work)) {
            TreeSet<String> working = new TreeSet<>();
            held.forEach(p -> working.add(p.getFileName().toString()));
            assertEquals(new TreeSet<>(ids), working, "a working directory per Run, named for it");
        }
    }

    @Test
    @DisplayName("a Run's Profile is its own: two Runs of one process would share the game, so there is one Run per process")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void a_profile_belongs_to_one_run(@TempDir Path out) throws IOException {
        Runner.run(arguments(out, 4));

        // The Profile is made inside the child, per Run (`HeadlessDriver.newGame` creates its own
        // temporary directory), so no two Runs of this invocation can have shared one: they were
        // in different processes, and a process makes exactly one. What this asserts is the thing
        // that makes that true -- that every Run ran somewhere of its own and came back with its
        // own log -- because the Profile itself is a temporary directory the child deletes.
        List<String> index = Files.readAllLines(out.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        for (String line : index) {
            LogHeader.Read read = LogHeader.of(out.resolve(LogHeader.string(line, "log")));
            assertTrue(read.present(), "every Run wrote its own log: " + line);
            assertEquals(LogHeader.string(line, "runId"), read.runId(),
                    "and the log's header names the Run it belongs to");
        }
        assertEquals(index.size(), index.stream().map(l -> LogHeader.string(l, "runId")).distinct().count(),
                "and no two of them are the same Run");
    }
}
