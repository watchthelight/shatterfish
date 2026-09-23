package org.shatterfish.rig;

import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.rng.Salt;

import java.io.IOException;
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

    private static final List<String> KNOWN = List.of(BRAIN, SEEDS, PARALLEL, OUT, ROOT, COMMIT, CAP, DEADLINE);

    /** How long one Run may take before it is killed and counted incomplete. */
    public static final int DEADLINE_SECONDS = 900;

    /** The most processes this command will start at once, whatever it is asked for. */
    public static final int MOST = 64;

    private Runner() {
    }

    public static void main(String[] args) {
        Path out = run(arguments(args));
        System.out.println("the Rig wrote " + out.resolve(RunIndex.RUNS) + " and "
                + out.resolve(RunIndex.SUMMARY));
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
        String brain = required(arguments, BRAIN);
        Brains.of(brain, 0L);
        Path root = Path.of(arguments.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        String set = required(arguments, SEEDS);
        int parallel = parallel(arguments);
        Path out = emptyFolder(required(arguments, OUT));
        String commit = required(arguments, COMMIT);
        int cap = arguments.containsKey(CAP) ? (int) number(arguments, CAP) : org.shatterfish.harness.agent.RunLoop.TURN_CAP;
        long deadline = arguments.containsKey(DEADLINE) ? number(arguments, DEADLINE) : DEADLINE_SECONDS;

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
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            List<Future<?>> started = new ArrayList<>();
            for (SeedSet.Entry triple : triples.entries()) {
                long salt = Salt.draw();
                String runId = RunLog.runId(tag,
                        triple.heroClass(), triple.challengeFlags(), triple.seedCode(), salt, brain);
                index.started(new RunIndex.Entry(runId, RunLog.fileName(runId), RunIndex.State.STARTED,
                        "", triple.seed(), triple.heroClass().name(), triple.challengeFlags(), salt,
                        "", 0, ""));
                started.add(pool.submit(() -> one(index, out, root, triple, salt, runId, brain, commit,
                        machine, cap, deadline, waits)));
            }
            for (Future<?> run : started) {
                try {
                    run.get();
                } catch (Exception failed) {
                    throw new IllegalStateException("a Run could not be dispatched", failed);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        index.summary(brain, set, parallel, millis, waits.get());

        System.out.println(index.count(RunIndex.State.FINISHED) + " Runs finished and "
                + index.count(RunIndex.State.INCOMPLETE) + " were incomplete, on " + parallel
                + " processes, in " + millis + " ms ("
                + RunIndex.rate(triples.entries().size(), millis) / 1000.0 + " Runs/s, "
                + RunIndex.rate(waits.get(), millis) / 1000.0 + " waits/s)");
        return out;
    }

    /** One Run, in a child, with its own Profile and working directory. */
    private static void one(RunIndex index, Path out, Path root, SeedSet.Entry triple, long salt,
                            String runId, String brain, String commit, String machine, int cap,
                            long deadline, AtomicLong waits) {
        Path working = out.resolve("work").resolve(runId);
        long began = System.nanoTime();
        String why = "";
        try {
            Files.createDirectories(working);
            Process child = child(out, root, working, triple, salt, brain, commit, machine, cap);
            StringBuilder said = new StringBuilder();
            Thread reading = Thread.ofVirtual().start(() -> collect(child, said));
            if (!child.waitFor(deadline, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                child.waitFor(30, TimeUnit.SECONDS);
                why = "the Run passed its deadline of " + deadline + "s and was killed";
            } else if (child.exitValue() != 0) {
                why = "the Run exited " + child.exitValue() + ": " + tail(said.toString());
            }
            reading.join(java.time.Duration.ofSeconds(30));
            if (!why.isEmpty()) {
                // What the child said, kept whole beside its log. A Run that failed is evidence
                // about the harness, and three lines of a stack trace in an index entry is not
                // enough to act on -- the index says that it failed, this says what it said.
                Files.writeString(out.resolve(runId + ".err"), said.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            why = "the Run could not be started: " + e;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            why = "the Rig was interrupted while this Run was in flight";
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        finish(index, out, runId, why, millis, waits);
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
        if (read.oracle()) {
            throw new IllegalStateException("the Run " + runId + " says in its own header that it saw"
                    + " what a player could not; an oracle Run is not ranked and this invocation"
                    + " publishes nothing (FR-11)");
        }
        waits.addAndGet(read.waits());
        boolean finished = read.complete() && why.isEmpty();
        index.ended(runId, finished ? RunIndex.State.FINISHED : RunIndex.State.INCOMPLETE,
                read.chain(), finished ? "ended" : "", millis,
                finished ? "" : (why.isEmpty() ? "the log has no end record" : why));
    }

    private static Process child(Path out, Path root, Path working, SeedSet.Entry triple, long salt,
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

    private static void collect(Process child, StringBuilder said) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (said) {
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
