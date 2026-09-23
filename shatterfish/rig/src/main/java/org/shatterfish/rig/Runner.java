package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.rng.Salt;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * The Rig's runner (story 3.3): many seeded Runs at once, each in its own process, and an index of
 * every one of them.
 *
 * <pre>
 * ./gradlew :rig:run --args="--brain random --seeds smoke --parallel 4 --out runs/2026-09-22"
 * </pre>
 *
 * <p><b>One process, one Run</b> (AD-6). The game's state is static and process-wide, so two Runs
 * in one process share the thing that defines them. This parent plays nothing: it picks the triples
 * out of a committed Seed set, draws each Run's salt when that Run executes, starts a child JVM,
 * and waits.
 *
 * <p><b>The salt is drawn here and now.</b> It is not in any registration and not derived from the
 * tuple, so a Brain's author cannot precompute the stream their Brain will face (FR-22, ADR-0007).
 * It is written into the Run's own log, because a Run whose salt was not recorded cannot be
 * replayed and its numbers cannot be checked.
 *
 * <p><b>Nothing is lost.</b> A Run that crashes, hangs or is killed is counted as incomplete and
 * its partial log is kept — ADR-0012 scores an incomplete Run's pair as a tie, and a Run that
 * simply vanished would not be scored at all, which quietly biases whatever remains. The index line
 * is written before the child starts.
 *
 * <p><b>No oracle reaches a ranked Run.</b> There is no oracle flag on this command line in any
 * spelling, so the Rig cannot ask for one; and every finished log's header is read back, so a Run
 * that claims one fails the whole invocation (FR-11, non-negotiable 1). The second check exists
 * because the first is a property of today's code and the second is a property of the artifact.
 */
public final class Runner {

    public static final String BRAIN = "--brain";

    public static final String SEEDS = "--seeds";

    public static final String PARALLEL = "--parallel";

    public static final String OUT = "--out";

    public static final String ROOT = "--root";

    public static final String COMMIT = "--commit";

    public static final String CAP = "--cap";

    public static final String DEADLINE = "--deadline";

    /**
     * Checks a folder the Rig wrote: every log against its own bytes, and against the index.
     *
     * <p>It plays nothing, so it costs what reading the files costs and can be run on every folder
     * rather than on a sample. It answers "was this folder changed after it was written"; it does
     * not answer "does this build still do what these logs describe", which is {@link #REPLAY}.
     */
    public static final String VERIFY = "--verify";

    /**
     * Replays one log in this process and reports whether the chains agree.
     *
     * <p>One log, not a folder. A Replay is a Run, and AD-6 gives a Run its own process because the
     * game's state is static and process-wide -- so a command that replayed five hundred logs in
     * one JVM would be measuring the order they went in.
     */
    public static final String REPLAY = "--replay";

    /** Every flag the Rig knows. The list is asserted by name, so a new one is a decision. */
    static final List<String> KNOWN = List.of(BRAIN, SEEDS, PARALLEL, OUT, ROOT, COMMIT, CAP,
            DEADLINE, VERIFY, REPLAY);

    /** How long one Run may take before it is killed and counted incomplete. */
    public static final int DEADLINE_SECONDS = 900;

    /** The most processes this command will start at once, whatever it is asked for. */
    public static final int MOST = 64;

    /** The longest deadline a Run may be given: a day, past which nobody is waiting anyway. */
    public static final int DEADLINE_MOST = 86_400;

    private Runner() {
    }

    public static void main(String[] args) {
        Map<String, String> arguments = arguments(args);
        if (arguments.containsKey(VERIFY)) {
            System.exit(verify(arguments, System.out));
            return;
        }
        if (arguments.containsKey(REPLAY)) {
            System.exit(replay(arguments, System.out));
            return;
        }
        Path out = run(arguments);
        System.out.println("the Rig wrote " + out.resolve(RunIndex.RUNS) + " and "
                + out.resolve(RunIndex.SUMMARY));
    }

    /**
     * Checks every log in a folder, and answers with a status the shell can branch on.
     *
     * <p>An incomplete Run is not a failure here. A Run that was killed leaves a log whose prefix
     * verifies perfectly, the Rig already counts it as incomplete, and treating it as tampering
     * would make a busy machine look like a dishonest one.
     */
    static int verify(Map<String, String> arguments, PrintStream out) {
        Verify.Report report = Verify.of(Path.of(required(arguments, VERIFY)));
        out.println(report.text());
        return report.ok(true) ? 0 : 1;
    }

    /**
     * Replays one log and answers with a status the shell can branch on.
     *
     * <p>The Replay writes its own log beside the original, under `--out`, because the comparison
     * is between two logs and throwing one of them away would leave the answer unexaminable. What
     * it prints is the two chains, which is the whole result in two values a person can read.
     */
    static int replay(Map<String, String> arguments, PrintStream out) {
        Path log = Path.of(required(arguments, REPLAY)).toAbsolutePath().normalize();
        Path into = emptyFolder(required(arguments, OUT));
        Path root = Path.of(arguments.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        String commit = arguments.containsKey(COMMIT) ? required(arguments, COMMIT) : commitOf(root);
        long began = System.nanoTime();
        Replay.Result result;
        try {
            result = Replay.of(log, into, commit, machine());
        } catch (Replay.Diverged diverged) {
            out.println("this build and " + log + " stop agreeing at wait " + diverged.at()
                    + "; the sections that differ are " + diverged.sections());
            return 2;
        } catch (Replay.Unverifiable unverifiable) {
            out.println(unverifiable.getMessage());
            return 3;
        } catch (RuntimeException refused) {
            out.println(refused.getMessage());
            return 4;
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        out.println((result.ok() ? "reproduced " : "did not reproduce ") + result.runId() + ": "
                + result.verified() + " of " + result.waits() + " waits verified, the log chains to "
                + result.originalChain() + " and this build to " + result.chain() + ", in " + millis
                + " ms");
        return result.ok() ? 0 : 1;
    }

    /**
     * The default number of processes: one per core the machine reports, because a Run is one JVM
     * playing a game and is bound by the core it is on rather than by waiting for anything. It is
     * a default and not a claim; `--parallel` overrides it and the summary records what was used.
     */
    public static int defaultParallel() {
        return Math.max(1, Math.min(MOST, Runtime.getRuntime().availableProcessors()));
    }

    /** Runs what the arguments ask for, and returns the folder it wrote. */
    public static Path run(Map<String, String> arguments) {
        // Named, not built. The parent hosts no Run (AD-6), and a real Brain's constructor may
        // touch the game or load a model -- validating a name by building one is how the parent
        // ends up doing the thing it exists not to do.
        String brain = Brains.named(required(arguments, BRAIN));
        Path root = Path.of(arguments.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        String set = required(arguments, SEEDS);
        int parallel = parallel(arguments);
        Path out = emptyFolder(required(arguments, OUT));
        String commit = arguments.containsKey(COMMIT) ? required(arguments, COMMIT) : commitOf(root);
        int cap = bounded(arguments, CAP, 1, Integer.MAX_VALUE,
                org.shatterfish.harness.agent.RunLoop.TURN_CAP);
        int deadline = bounded(arguments, DEADLINE, 1, DEADLINE_MOST, DEADLINE_SECONDS);

        // `load` refuses the holdout set outright (story 3.1), so the runner inherits that refusal
        // rather than restating it -- a second copy of a rule is a second thing to keep true.
        SeedSet triples = SeedSets.load(root, set).set();
        RunIndex index = new RunIndex(out);
        AtomicLong waits = new AtomicLong();
        String machine = machine();
        // The tag from the pin, not from a running game: this process boots nothing (AD-6), and
        // asking the game which release it is before it exists is what story 3.2's guard refuses.
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();

        long began = System.nanoTime();
        // Every child this invocation has alive, so that a failure anywhere can stop them all. A
        // worker interrupted by `shutdownNow` used to return without destroying its child, and a
        // game JVM with a fifteen-minute deadline outlived the parent by fifteen minutes -- still
        // writing into a log the parent had already indexed, and still holding a core against the
        // next invocation's measured throughput.
        Map<String, Process> alive = new java.util.concurrent.ConcurrentHashMap<>();
        Thread hook = new Thread(() -> destroyAll(alive), "shatterfish-rig-children");
        Runtime.getRuntime().addShutdownHook(hook);
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        RuntimeException failed = null;
        try {
            List<Future<?>> started = new ArrayList<>();
            for (SeedSet.Entry triple : triples.entries()) {
                long salt = Salt.draw();
                String runId = RunLog.runId(tag,
                        triple.heroClass(), triple.challengeFlags(), triple.seedCode(), salt, brain);
                index.started(new RunIndex.Entry(runId, RunLog.fileName(runId), RunIndex.State.STARTED,
                        "", triple.seed(), triple.heroClass().name(), triple.challengeFlags(), salt,
                        "", 0, ""));
                started.add(pool.submit(() -> one(index, out, alive, triple, salt, runId, brain, commit,
                        machine, cap, deadline, waits)));
            }
            // Awaited as they finish rather than in the order they were sent. A refusal on the
            // second Run used to wait out the first Run's deadline before anyone saw it, and on a
            // five-hundred-Run set that is minutes of further Runs written into a folder the
            // refusal is about to declare void.
            failed = await(started);
        } finally {
            pool.shutdownNow();
            destroyAll(alive);
            Runtime.getRuntime().removeShutdownHook(hook);
        }
        if (failed != null) {
            throw refuse(index, failed);
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        index.summary(brain, set, parallel, cap, millis, waits.get());

        System.out.println(index.count(RunIndex.State.FINISHED) + " Runs finished and "
                + index.count(RunIndex.State.INCOMPLETE) + " were incomplete, on " + parallel
                + " processes, in " + millis + " ms ("
                + RunIndex.rate(triples.entries().size(), millis) / 1000.0 + " Runs/s, "
                + RunIndex.rate(waits.get(), millis) / 1000.0 + " waits/s)");
        return out;
    }

    /**
     * Marks the invocation refused and hands back the reason to throw.
     *
     * <p>One statement, because the mark and the throw have to travel together: the index and the
     * logs are on disk by the time a refusal fires -- they are written as Runs are dispatched, on
     * purpose -- so a refusal that threw without marking left a folder that looked like a finished
     * invocation. A battery deleted the marking call and nothing noticed, which is what this shape
     * fixes: there is no longer a call to delete on its own.
     */
    static RuntimeException refuse(RunIndex index, RuntimeException failed) {
        index.refused(failed.getMessage());
        return failed;
    }

    /**
     * Waits for every Run and returns the first failure, or null.
     *
     * <p>It unwraps. A worker's {@code IllegalStateException} used to come back out of
     * {@code Future.get} as an {@code ExecutionException} and be rewrapped as "a Run could not be
     * dispatched" -- so the oracle refusal's own message, which names the Run and FR-11, sat two
     * levels down a cause chain and the operator saw none of it. It is a seam so that a test can
     * ask what a failing Run does to the invocation without needing an invocation that fails.
     */
    static RuntimeException await(List<Future<?>> started) {
        RuntimeException failed = null;
        for (Future<?> run : started) {
            try {
                run.get();
            } catch (Exception broke) {
                Throwable cause = broke.getCause() == null ? broke : broke.getCause();
                if (failed == null) {
                    failed = cause instanceof RuntimeException already ? already
                            : new IllegalStateException("a Run could not be dispatched", cause);
                }
            }
        }
        return failed;
    }

    /** One Run, in a child, with its own Profile and working directory. */
    private static void one(RunIndex index, Path out, Map<String, Process> alive, SeedSet.Entry triple,
                            long salt, String runId, String brain, String commit, String machine,
                            int cap, int deadline, AtomicLong waits) {
        Path working = out.resolve("work").resolve(runId);
        long began = System.nanoTime();
        String why = "";
        // A StringBuffer rather than a StringBuilder: the reader thread appends while this thread
        // reads, and an unsynchronised builder can be seen with a grown array and a stale count.
        StringBuffer said = new StringBuffer();
        Process child = null;
        try {
            Files.createDirectories(working);
            child = child(out, working, triple, salt, brain, commit, machine, cap);
            alive.put(runId, child);
            Process reading = child;
            // A platform thread, not a virtual one: reading a process pipe is a blocking native
            // call that pins its carrier, and pinning `--parallel` carriers at once is how the
            // children end up blocked writing into a pipe nobody is draining.
            Thread reader = new Thread(() -> collect(reading, said), "shatterfish-rig-" + runId);
            reader.setDaemon(true);
            reader.start();
            if (!child.waitFor(deadline, TimeUnit.SECONDS)) {
                destroy(child);
                why = child.isAlive()
                        ? "the Run passed its deadline of " + deadline + "s and would not die"
                        : "the Run passed its deadline of " + deadline + "s and was killed";
            } else if (child.exitValue() != 0) {
                why = "the Run exited " + child.exitValue() + ": " + tail(said.toString());
            }
            reader.join(30_000L);
        } catch (IOException e) {
            why = "the Run could not be started: " + e;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            why = "the Rig was interrupted while this Run was in flight";
        } finally {
            if (child != null) {
                destroy(child);
                alive.remove(runId);
            }
        }
        if (!why.isEmpty()) {
            // What the child said, kept whole beside its log. A Run that failed is evidence about
            // the harness, and three lines of a stack trace in an index entry is not enough to act
            // on. Its own try: a full disk here used to replace "the Run exited 3" with "the Run
            // could not be started", which is a different and untrue story.
            try {
                Files.writeString(out.resolve(runId + ".err"), said.toString(), StandardCharsets.UTF_8);
            } catch (IOException cannot) {
                why = why + "; and what it said could not be written down (" + cannot + ")";
            }
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        finish(index, out, runId, why, millis, waits);
    }

    /** Ends a child and everything it started, which `destroyForcibly` alone does not. */
    private static void destroy(Process child) {
        if (!child.isAlive()) {
            return;
        }
        child.descendants().forEach(ProcessHandle::destroyForcibly);
        child.destroyForcibly();
        try {
            child.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void destroyAll(Map<String, Process> alive) {
        for (Process child : alive.values()) {
            destroy(child);
        }
        alive.clear();
    }

    /**
     * Reads the log back and records what it says. This is where the oracle refusal lives: a Run
     * whose own header claims it fails the invocation, whatever the command line asked for.
     *
     * <p>It is package-private so that a test can hand it a log claiming the oracle. The Rig cannot
     * produce one -- there is no flag, in any spelling -- so the refusal is unreachable from the
     * outside, and a guard nothing can reach is a guard nobody knows works: story 3.2's battery
     * deleted exactly such a guard with every test still green. The seam is the answer story 2.7
     * gave to the same shape.
     */
    static void finish(RunIndex index, Path out, String runId, String why, long millis,
                       AtomicLong waits) {
        LogHeader.Read read = LogHeader.of(out.resolve(RunLog.fileName(runId)));
        if (!read.readable() && read.present()) {
            // A log this Rig cannot read is a Run it cannot vouch for, including about the oracle.
            // It is counted incomplete rather than quietly counted fair.
            index.ended(runId, RunIndex.State.INCOMPLETE, "", "", millis,
                    (why.isEmpty() ? "" : why + "; ") + "its log could not be read: " + read.unreadable());
            return;
        }
        if (read.oracle()) {
            throw new IllegalStateException("the Run " + runId + " says in its own header that it saw"
                    + " what a player could not; an oracle Run is not ranked and this invocation"
                    + " publishes nothing (FR-11)");
        }
        if (read.present() && !read.runId().isEmpty() && !read.runId().equals(runId)) {
            // The parent predicts the file name from the tuple and the child writes it from its own
            // header. If those ever disagree the parent is reading somebody else's Run, and the
            // oracle check above was asked about the wrong log.
            throw new IllegalStateException("the log at " + RunLog.fileName(runId) + " says it is the"
                    + " Run " + read.runId() + "; the Rig is reading a log it did not mean to");
        }
        waits.addAndGet(read.waits());
        // The log decides, not the process. A child that wrote its end record and was then killed
        // a moment later -- a deadline that expired while it was exiting -- did finish its Run, and
        // calling that incomplete would discard a real measurement. ADR-0012 scores an incomplete
        // pair as a tie, so counting a finished Run as incomplete biases the set in the opposite
        // direction to the one the index exists to prevent. Whatever went wrong is kept as a note
        // beside the verdict rather than becoming it.
        boolean finished = read.complete() && read.chain().matches("[0-9a-f]{64}");
        index.ended(runId, finished ? RunIndex.State.FINISHED : RunIndex.State.INCOMPLETE,
                read.chain(), finished ? read.cause() : "", millis,
                finished ? why : (why.isEmpty() ? "the log has no end record" : why));
    }

    private static Process child(Path out, Path working, SeedSet.Entry triple, long salt,
                                 String brain, String commit, String machine, int cap) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path exe = Path.of(java + ".exe");
        List<String> command = new ArrayList<>(List.of(
                Files.exists(exe) ? exe.toString() : java.toString(),
                "-cp", System.getProperty("java.class.path"),
                RunOne.class.getName(),
                RunOne.SEED, Long.toString(triple.seed()),
                RunOne.CLASS, triple.heroClass().name(),
                RunOne.SALT, Long.toString(salt),
                RunOne.OUT, out.toAbsolutePath().toString(),
                RunOne.COMMIT, commit,
                RunOne.BRAIN, brain,
                RunOne.BRAIN_COMMIT, commit,
                RunOne.MACHINE, machine,
                RunOne.CAP, Integer.toString(cap),
                RunOne.CHALLENGES, Integer.toString(triple.challengeFlags())));
        return new ProcessBuilder(command)
                // Its own working directory, so a Run that writes beside itself writes beside
                // itself and not beside another Run (AD-6).
                .directory(working.toFile())
                .redirectErrorStream(true)
                .start();
    }

    /** The most of a child's output the parent will hold: a looping child must not fill the heap. */
    private static final int SAID_MOST = 1 << 20;

    private static void collect(Process child, StringBuffer said) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (said.length() < SAID_MOST) {
                    said.append(line).append('\n');
                }
            }
        } catch (IOException closed) {
            // The child is gone; whatever it said before that is what there is.
        }
    }

    private static String tail(String said) {
        String[] lines = said.strip().split("\n");
        int from = Math.max(0, lines.length - 3);
        return String.join(" | ", List.of(lines).subList(from, lines.length));
    }

    /**
     * The Shatterfish commit this invocation is of, from the checkout when the command line does not
     * say.
     *
     * <p>Every Run's header states which build played it, and there is no honest default for that
     * -- so `--commit` used to be required, and the command published on the methodology page, in
     * this class's own javadoc, in the Gradle task's comment and in the story's Verification
     * section did not pass it. None of them ran. A command nobody can copy is worse than a flag
     * nobody has to type, so the checkout answers when the caller does not, and the refusal names
     * the flag when the checkout cannot.
     */
    static String commitOf(Path root) {
        try {
            Process git = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            String said;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(git.getInputStream(), StandardCharsets.UTF_8))) {
                said = reader.readLine();
            }
            if (!git.waitFor(30, TimeUnit.SECONDS) || git.exitValue() != 0 || said == null
                    || !said.trim().matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("the checkout at " + root + " does not say which"
                        + " commit it is of, so state it: " + COMMIT + " <sha>");
            }
            return said.trim();
        } catch (IOException | InterruptedException cannot) {
            if (cannot instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("the commit of " + root + " could not be read (" + cannot
                    + "), so state it: " + COMMIT + " <sha>");
        }
    }

    /** What this invocation ran on. It is recorded and never chained (ADR-0011). */
    static String machine() {
        return System.getProperty("os.name") + " " + System.getProperty("os.arch") + ", "
                + Runtime.getRuntime().availableProcessors() + " cores, java "
                + System.getProperty("java.version");
    }

    // ------------------------------------------------------------------------ the command line

    static Map<String, String> arguments(String[] args) {
        Map<String, String> given = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            if (!flag.startsWith("--")) {
                throw new IllegalArgumentException("expected a flag at argument " + i + ", found " + flag);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(flag + " takes a value");
            }
            if (args[i + 1].startsWith("--")) {
                // `--commit --root` would otherwise attest the string "--root" as the commit in
                // every header of the invocation, and `--out --root` would write the whole thing
                // into a folder of that name. A flag is never a value.
                throw new IllegalArgumentException(flag + " was given the flag " + args[i + 1]
                        + " as its value; every flag takes a value of its own");
            }
            if (args[i + 1].isEmpty()) {
                throw new IllegalArgumentException(flag + " is stated, not left empty");
            }
            if (!KNOWN.contains(flag)) {
                // Named rather than ignored, and the list is printed, because the one flag this
                // command must never grow is an oracle and a silently ignored argument is how a
                // reader convinces themselves it has one.
                throw new IllegalArgumentException("the Rig does not know " + flag + "; it knows " + KNOWN);
            }
            if (given.put(flag, args[i + 1]) != null) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
        }
        return given;
    }

    /**
     * A number the command line may give, inside the bounds this command will act on.
     *
     * <p>`--parallel` was bounded and the other two were not, which is the shape of a rule that
     * holds where somebody remembered it. `--deadline 0` killed every Run before it booted and
     * reported the whole set incomplete with nothing saying the operator's typo caused it; a
     * `--cap` above two billion became a negative int on the way through.
     */
    private static int bounded(Map<String, String> arguments, String flag, int least, int most,
                               int byDefault) {
        if (!arguments.containsKey(flag)) {
            return byDefault;
        }
        long asked = number(arguments, flag);
        if (asked < least || asked > most) {
            throw new IllegalArgumentException(flag + " is " + least + " through " + most + ": " + asked);
        }
        return (int) asked;
    }

    private static int parallel(Map<String, String> arguments) {
        if (!arguments.containsKey(PARALLEL)) {
            return defaultParallel();
        }
        long asked = number(arguments, PARALLEL);
        if (asked < 1 || asked > MOST) {
            throw new IllegalArgumentException(PARALLEL + " is 1 through " + MOST + ": " + asked);
        }
        return (int) asked;
    }

    /** The folder an invocation writes into: absent, or empty. Two invocations are not one. */
    private static Path emptyFolder(String named) {
        Path out = Path.of(named).toAbsolutePath().normalize();
        if (Files.exists(out) && !Files.isDirectory(out)) {
            // The emptiness check below asks whether a *directory* holds anything, so a regular
            // file passed it and the refusal arrived much later, from inside the index writer,
            // saying something else entirely.
            throw new IllegalArgumentException(out + " is a file, and an invocation writes a folder");
        }
        if (Files.isDirectory(out)) {
            try (Stream<Path> held = Files.list(out)) {
                List<Path> anything = held.toList();
                if (!anything.isEmpty()) {
                    throw new IllegalArgumentException(out + " is not empty, and two invocations"
                            + " writing into one folder are two sets of numbers nobody can tell"
                            + " apart; it holds " + anything.size() + " entries");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("the folder " + out + " could not be read", e);
            }
        }
        return out;
    }

    private static String required(Map<String, String> arguments, String flag) {
        String value = arguments.get(flag);
        if (value == null) {
            throw new IllegalArgumentException("the Rig needs " + flag);
        }
        return value;
    }

    private static long number(Map<String, String> arguments, String flag) {
        String value = required(arguments, flag);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(flag + " is a number: " + value, notANumber);
        }
    }
}
