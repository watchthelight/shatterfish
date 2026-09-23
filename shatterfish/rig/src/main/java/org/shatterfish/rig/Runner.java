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

    /**
     * Requires every log in a {@code --verify} folder to be a Run that finished.
     *
     * <p>Without it an incomplete log passes, which is right for a folder of five hundred Runs
     * where a machine ran out of time. It is wrong for the nightly job's reference folder, where a
     * log truncated to its header would otherwise pre-flight clean and then be "replayed" as a
     * one-line Run that reports success.
     */
    public static final String FINISHED = "--finished";

    /**
     * The Registration this invocation runs under, by id.
     *
     * <p>Without it an invocation is not ranked: it runs, it writes logs, and nothing it produces
     * may be published as a measurement of anything. With it, the Registration must be committed to
     * git before the Runs -- which is the whole of FR-22, and is why this takes an id and reads a
     * file rather than taking the numbers on the command line.
     */
    public static final String REGISTRATION = "--registration";

    /**
     * The Brain {@code --brain} is compared against (story 3.6).
     *
     * <p>Both play every triple under one salt, drawn once per triple, into their own folders; the
     * pairs are scored and, under a Registration that fixes a comparison, tested.
     */
    public static final String AGAINST = "--against";

    /** Every flag the Rig knows. The list is asserted by name, so a new one is a decision. */
    static final List<String> KNOWN = List.of(BRAIN, SEEDS, PARALLEL, OUT, ROOT, COMMIT, CAP,
            DEADLINE, VERIFY, REPLAY, FINISHED, REGISTRATION, AGAINST);

    /** The two things this command can be asked to do, of which it does exactly one. */
    static final List<String> MODES = List.of(VERIFY, REPLAY);

    /** The flags that are their own answer and take no value after them. */
    static final List<String> SWITCHES = List.of(FINISHED);

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
        // One mode. `arguments` refuses a flag twice and a flag with no value, and this is the same
        // rule one level up: a command line asking to verify and to replay used to do one of them
        // and say nothing about the other, which is a flag silently ignored.
        List<String> asked = MODES.stream().filter(arguments::containsKey).toList();
        if (asked.size() > 1) {
            throw new IllegalArgumentException("the Rig does one thing per invocation and this asks"
                    + " for " + asked + "; verifying a folder and replaying a log are different"
                    + " questions with different costs");
        }
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
        long began = System.nanoTime();
        Verify.Report report = Verify.of(Path.of(required(arguments, VERIFY)));
        boolean finished = arguments.containsKey(FINISHED);
        out.println(report.text() + ", in " + (System.nanoTime() - began) / 1_000_000L + " ms");
        return report.ok(!finished) ? 0 : 1;
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
        // No commit is taken here. A Replay attests what the log attests, because it is reproducing
        // the Run the log describes rather than making a claim of its own -- and a Replay that
        // signed its own checkout's commit could never reach the log's chain from any other one.
        long began = System.nanoTime();
        Replay.Result result;
        try {
            result = Replay.of(log, into, machine());
        } catch (Replay.Diverged diverged) {
            out.println("this build and " + log + " stop agreeing at wait " + diverged.at()
                    + "; the sections that differ are " + diverged.sections());
            return 2;
        } catch (Replay.Unverifiable unverifiable) {
            out.println(unverifiable.getMessage());
            return 3;
        } catch (java.io.UncheckedIOException broken) {
            // A file that could not be read is not a log this build refuses; it is a machine
            // problem, and reporting it as a refusal would send whoever reads the nightly job
            // looking for a schema difference that is not there.
            out.println("the log could not be read: " + broken.getMessage());
            return 5;
        } catch (RuntimeException refused) {
            out.println(refused.getMessage());
            return 4;
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        out.println((result.ok() ? "reproduced " : "did not reproduce ") + result.runId() + ": "
                + result.verified() + " of " + result.waits() + " waits verified, the log chains to "
                + result.originalChain() + " and this build to " + result.chain() + ", in " + millis
                + " ms");
        if (!result.ok()) {
            out.println(result.why());
        }
        // What the log attests, beside what is actually running. A Replay attests the log's commit
        // so that the two chains can be compared at all, which means the chain says nothing about
        // which build did the reproducing -- so the command says it, every time, in the one place
        // a person is looking.
        out.println("replayed by this build, at commit " + commitOf(Path.of(".").toAbsolutePath()
                .normalize()) + ", against a log attesting " + result.attested());
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
        String against = arguments.containsKey(AGAINST) ? Brains.named(required(arguments, AGAINST)) : null;
        Path root = Path.of(arguments.getOrDefault(ROOT, ".")).toAbsolutePath().normalize();
        String set = required(arguments, SEEDS);
        // One commit, resolved once, used everywhere. It used to be resolved twice: `commitOf(root)`
        // for the holdout budget check and `--commit` for what the ledger recorded -- so a run with
        // `--commit <any sha>` was checked against one identity and recorded under another, and the
        // held-out set could be spent without limit, every line reading as a lawful first use. It is
        // also resolved lazily, because `commitOf` refuses a checkout with no git in it and
        // `--commit` is the documented way to run the Rig from a source export.
        String commit = arguments.containsKey(COMMIT) ? required(arguments, COMMIT) : commitOf(root);
        // The Registration first, before a folder is made or a Run is dispatched. A refusal that
        // arrives after five hundred Runs is a refusal that cost what it was preventing.
        long checking = System.nanoTime();
        Registered registered = registration(arguments, root, set, brain, against, commit);
        Registrations.Committed registration = registered.committed();
        Ledger ledger = registered.ledger();
        String stamp = registration == null ? "" : registration.stamp();
        // What the pre-flight cost. It asks git a few questions and reads a file, and the number
        // matters because story 3.11 schedules this nightly: a check that costs a measurable
        // fraction of the measurement is a fact worth having before it is run every night.
        long checked = (System.nanoTime() - checking) / 1_000_000L;
        int parallel = parallel(arguments);
        Path out = emptyFolder(required(arguments, OUT));
        int cap = bounded(arguments, CAP, 1, Integer.MAX_VALUE,
                org.shatterfish.harness.agent.RunLoop.TURN_CAP);
        int deadline = bounded(arguments, DEADLINE, 1, DEADLINE_MOST, DEADLINE_SECONDS);

        // `load` refuses the holdout set outright (story 3.1), so the runner inherits that refusal
        // rather than restating it -- a second copy of a rule is a second thing to keep true. The
        // one way past that door is the one FR-20 allows: a Registration that claims a release-level
        // result, which is what `publish` demands a reason for, and the reason is the hypothesis.
        // Claimed before the set is opened, not after the Runs finish. `publish` is the act that
        // spends a held-out set: once those triples have been read, they have been seen, whatever
        // happens next. Recording the use afterwards meant Ctrl-C left the set played and the
        // ledger silent, and the next invocation's check passed -- which is the exact move FR-20
        // caps, defeated by something that is not an attack, it is Tuesday.
        if (published(registration, set)) {
            ledger.record(registration, brain, registered.brainVersion(),
                    registered.brainConfig(), set, Ledger.Outcome.CLAIMED,
                    Registrations.spendsTheBudget(set), "the held-out set is about to be read");
        }
        SeedSets.Read read = published(registration, set)
                ? SeedSets.publish(root, set, registration.registration().id() + ": "
                        + registration.registration().claim())
                : SeedSets.load(root, set);
        SeedSet triples = read.set();
        // The Seed set's version, against the one the hypothesis fixed. A Registration names a set
        // *and a version* so that "standard" means the same thing later; without this the field is
        // recorded and enforces nothing.
        if (registration != null && registration.registration().seedVersion() != triples.version()) {
            throw new IllegalArgumentException("the Registration " + registration.registration().id()
                    + " fixes " + set + " at version " + registration.registration().seedVersion()
                    + " and this build's " + set + " is version " + triples.version()
                    + "; a hypothesis names the Runs it is about");
        }
        // One side per Brain. A comparison puts each in its own folder, because a Brain compared with
        // itself -- which is how pairing is checked -- would otherwise write both Runs of a pair to
        // one file name: the run id names the Brain, and the Brain is the same.
        List<Side> sides = against == null
                ? List.of(new Side(brain, out, new RunIndex(out)))
                : List.of(side(brain, out.resolve(Comparison.CANDIDATE)),
                        side(against, out.resolve(Comparison.BASELINE)));
        RunIndex index = sides.get(0).index();
        List<Long> salts = new ArrayList<>();
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
                // One salt per triple, whatever the number of Brains. That is the pair: whatever
                // the dungeon does at a given moment, it does to both.
                long salt = Salt.draw();
                salts.add(salt);
                for (Side side : sides) {
                    String runId = RunLog.runId(tag, triple.heroClass(), triple.challengeFlags(),
                            triple.seedCode(), salt, side.brain());
                    side.index().started(new RunIndex.Entry(runId, RunLog.fileName(runId),
                            RunIndex.State.STARTED, "", triple.seed(), triple.heroClass().name(),
                            triple.challengeFlags(), salt, "", 0, ""));
                    started.add(pool.submit(() -> one(side.index(), side.out(), alive, triple, salt,
                            runId, side.brain(), commit, machine, cap, deadline, waits, stamp)));
                }
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
            // The refusal is built first and thrown last. Recording used to come first and could
            // throw on its own -- a null message reaches `Canon.text` as a null -- which replaced
            // the refusal with a NullPointerException and left the folder unmarked: the one path
            // that voids a folder of numbers was the one that crashed.
            RuntimeException reason = refuse(index, failed);
            for (Side side : sides.subList(1, sides.size())) {
                refuse(side.index(), failed);
            }
            if (registration != null) {
                try {
                    // Recorded even though nothing is published. A refused invocation is an
                    // attempt, and a ledger that counted only the invocations somebody was happy
                    // with would be the opposite of the count FR-25 asks for.
                    ledger.record(registration, brain, registered.brainVersion(),
                    registered.brainConfig(), set,
                            Ledger.Outcome.REFUSED, published(registration, set), note(failed));
                } catch (RuntimeException | Error couldNotRecord) {
                    // A read-only tree, a full disk. The refusal is what the operator needs and it
                    // is what gets thrown; failing to write the line is attached to it rather than
                    // replacing it, which is what used to happen when a null message reached the
                    // writer and a NullPointerException arrived instead of the reason.
                    reason.addSuppressed(couldNotRecord);
                }
            }
            throw reason;
        }
        long millis = (System.nanoTime() - began) / 1_000_000L;
        for (Side side : sides) {
            side.index().summary(side.brain(), set, parallel, cap, millis, waits.get(), stamp,
                    read.reason());
        }
        if (against != null) {
            // Tested only under a Registration that fixes a comparison: the bounds are the
            // hypothesis, and a test whose bounds were chosen after the pairs were seen is the thing
            // FR-22 exists to prevent. An unranked comparison still scores and counts its pairs.
            Gsprt test = registration != null ? Gsprt.of(registration.registration()) : null;
            Comparison.Report report = Comparison.of(triples, salts, tag, out, brain, against, test);
            Comparison.write(out, report, stamp);
            System.out.println(brain + " against " + against + ": " + report.scores().size()
                    + " pairs, " + report.missing() + " missing"
                    + (report.result() == null ? ", not tested (no Registration states the bounds)"
                            : ", " + report.result().verdict() + " after " + report.result().pairs()));
        }
        if (registration != null) {
            ledger.record(registration, brain, registered.brainVersion(),
                    registered.brainConfig(), set,
                    Ledger.Outcome.FINISHED, published(registration, set), "");
        }

        System.out.println(registration == null
                ? "this invocation is not ranked: it ran under no Registration, so nothing it"
                        + " produced may be published as a measurement (FR-22)"
                : "ranked under " + stamp + ", checked against git in " + checked + " ms and"
                        + " recorded in " + root.resolve(Registrations.FOLDER).resolve(Ledger.FILE));
        // Every side's Runs. A comparison plays two per triple, and counting one side's made the
        // first paired smoke run report half the Runs it played and half the rate.
        long finished = sides.stream().mapToLong(side -> side.index().count(RunIndex.State.FINISHED)).sum();
        long incomplete = sides.stream().mapToLong(side -> side.index().count(RunIndex.State.INCOMPLETE)).sum();
        System.out.println(finished + " Runs finished and "
                + incomplete + " were incomplete, on " + parallel
                + " processes, in " + millis + " ms ("
                + RunIndex.rate((long) triples.entries().size() * sides.size(), millis) / 1000.0 + " Runs/s, "
                + RunIndex.rate(waits.get(), millis) / 1000.0 + " waits/s)");
        return out;
    }

    /**
     * Whether this invocation is the one use of a held-out set that FR-20 permits.
     *
     * <p>Both halves have to hold: a Registration that claims a release-level result, and the set
     * it claims it about. Everything else goes through `load`, which refuses the holdout set and
     * says why.
     */
    private static boolean published(Registrations.Committed registration, String set) {
        return registration != null && registration.registration().releaseLevel()
                && Registrations.spendsTheBudget(set);
    }

    /**
     * What a refusal is called in a ledger line when the exception did not say.
     *
     * <p>Package-private so that it can be held directly. Reaching it through {@code run} needs a
     * child that fails with a message-less exception, which is a thing to arrange in production and
     * not in a test -- and the guard matters: `JsonWriter` refuses a null, so without this the
     * ledger write threw and replaced the refusal that voids the folder.
     */
    static String note(RuntimeException failed) {
        String said = failed.getMessage();
        return said == null || said.isEmpty() ? failed.getClass().getName() : said;
    }

    /**
     * The Registration an invocation runs under, the ledger it is recorded in, and the Brain's
     * configuration hash -- or nulls and an empty hash when the invocation is unranked.
     *
     * <p>Three things travel together because two of them must not be computed for an unranked
     * invocation. `Brains.configHash` refuses a Brain that has not stated its configuration, which
     * is every Brain except `random`, so asking for it unconditionally would have made the first
     * real Brain unrunnable by the Rig at all. And a ledger with one unreadable line refuses to be
     * read, which is right when a count is about to be relied on and wrong when nothing is going to
     * look at it.
     */
    record Registered(Registrations.Committed committed, Ledger ledger, String brainConfig,
                      String brainVersion) {
    }

    /** One Brain's half of an invocation: its name, its folder and its index. */
    record Side(String brain, Path out, RunIndex index) {
    }

    private static Side side(String brain, Path out) {
        try {
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new UncheckedIOException("could not make " + out, e);
        }
        return new Side(brain, out, new RunIndex(out));
    }

    /**
     * The Registration this invocation runs under, or null when it is unranked.
     *
     * <p>Every refusal happens here, before a folder exists: a Registration that is not committed,
     * one that is about a different Seed set, a holdout Run with no release-level claim, and a
     * second holdout Run for a Brain version that has already had its one. The last two are FR-20,
     * and they are written once -- {@link Registrations#refusal} is the only place that rule lives,
     * which is what story 3.1 left an issue open for (#116).
     *
     * <p>An unranked invocation is allowed and is the normal case during development. What is not
     * allowed is an unranked invocation of the holdout set: the set exists to be spent once on a
     * claim, and spending it on nothing at all is still spending it.
     */
    static Registered registration(Map<String, String> arguments, Path root, String set,
                                   String brain, String commit) {
        return registration(arguments, root, set, brain, null, commit);
    }

    static Registered registration(Map<String, String> arguments, Path root, String set,
                                   String brain, String against, String commit) {
        if (!arguments.containsKey(REGISTRATION)) {
            // An unranked invocation of the holdout set is refused too -- by `SeedSets.load`, which
            // states the whole of FR-20 and is the door story 3.1 built. Restating it here was a
            // second place for the rule to live, and the Rig's own test caught it.
            //
            // Nothing else is computed here. An unranked invocation reads no ledger and asks no
            // Brain for a configuration hash, so a corrupt ledger and an unconfigured Brain both
            // stop being reasons a development run cannot happen.
            return new Registered(null, null, "", "");
        }
        Registrations.Committed committed = Registrations.read(root, required(arguments, REGISTRATION));
        String brainConfig = Brains.configHash(brain);
        // The Brain's own version, not the repository's. FR-20 counts one held-out use per Brain
        // version, and a key that moved when a README did would have handed an unchanged Brain a
        // fresh allowance every time somebody fixed a comment.
        String brainVersion = Brains.version(root, brain);
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER), root);
        Registrations.Refusal refusal =
                Registrations.refusal(committed, set, brainVersion, brainConfig, ledger);
        if (refusal == null) {
            // The Brain being measured has to be the Brain being run. A Registration about one
            // Brain and an invocation of another produces logs that cite a hypothesis they are not
            // about.
            String measured = committed.registration().brainB().name();
            org.shatterfish.api.Registration.Brain baseline = committed.registration().brainA();
            if (committed.registration().comparison() != (against != null)) {
                // A comparison's Registration run as a baseline would never test anything, and a
                // baseline's run as a comparison would test with bounds nobody stated.
                refusal = new Registrations.Refusal("the Registration "
                        + committed.registration().id() + " fixes "
                        + (committed.registration().comparison() ? "a comparison" : "a baseline")
                        + " and this invocation runs "
                        + (against != null ? "a comparison" : "one Brain"));
            } else if (baseline != null && !baseline.name().equals(against)) {
                refusal = new Registrations.Refusal("the Registration "
                        + committed.registration().id() + " compares against the Brain "
                        + baseline.name() + " and this invocation compares against " + against);
            } else if (!measured.equals(brain)) {
                refusal = new Registrations.Refusal("the Registration "
                        + committed.registration().id() + " measures the Brain " + measured
                        + " and this invocation runs " + brain);
            } else if (committed.registration().releaseLevel()
                    && !brainVersion.startsWith(committed.registration().brainB().commit())) {
                // A release-level claim names the build it is about, and this is the one kind of
                // Registration where that has to bind: the held-out set is spent once per Brain
                // version, so a Registration that governed an unlimited sequence of Brains would
                // hand each of them its own allowance. A development Registration is deliberately
                // looser -- it names a Brain, not a build, and a baseline outlives a commit.
                //
                // Against the Brain's version, not the invocation's commit: committing the
                // Registration is itself a commit, so a rule comparing against HEAD could never be
                // satisfied by any Registration that named a real value.
                refusal = new Registrations.Refusal("the Registration "
                        + committed.registration().id() + " claims a release-level result for the"
                        + " Brain at " + committed.registration().brainB().commit()
                        + " and the " + brain + " Brain in this checkout was last changed at "
                        + (brainVersion.isEmpty() ? "a commit git could not name" : brainVersion)
                        + "; a release-level hypothesis names the Brain it is about (FR-20)");
            }
        }
        if (refusal != null) {
            // Every refusal under a committed Registration is an attempt, and every one of them is
            // counted -- the Brain-name mismatch used to throw without recording, which is a use
            // the count FR-25 publishes would not have shown. It spends no holdout allowance: a
            // refusal reads no seeds, and burning the one use on a typo would be a rule punishing
            // the wrong thing.
            ledger.record(committed, brain, brainVersion, brainConfig, set,
                    Ledger.Outcome.FORBIDDEN, false, refusal.why());
            throw new IllegalArgumentException(refusal.why());
        }
        return new Registered(committed, ledger, brainConfig, brainVersion);
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
                            int cap, int deadline, AtomicLong waits, String registration) {
        Path working = out.resolve("work").resolve(runId);
        long began = System.nanoTime();
        String why = "";
        // A StringBuffer rather than a StringBuilder: the reader thread appends while this thread
        // reads, and an unsynchronised builder can be seen with a grown array and a stale count.
        StringBuffer said = new StringBuffer();
        Process child = null;
        try {
            Files.createDirectories(working);
            child = child(out, working, triple, salt, brain, commit, machine, cap, registration);
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
        // The oracle first, before readability. "I cannot read this file, and it says it saw what a
        // player could not" is a refusal, not an incomplete Run: a log that makes the claim makes
        // it whether or not the rest of it parses, and the weaker ordering would let a Run escape
        // the guard by being malformed as well as unfair.
        if (read.oracle()) {
            throw new IllegalStateException("the Run " + runId + " says in its own header that it saw"
                    + " what a player could not; an oracle Run is not ranked and this invocation"
                    + " publishes nothing (FR-11)");
        }
        if (!read.readable() && read.present()) {
            // A log this Rig cannot read is a Run it cannot vouch for. It is counted incomplete
            // rather than quietly counted fair.
            index.ended(runId, RunIndex.State.INCOMPLETE, "", "", millis,
                    (why.isEmpty() ? "" : why + "; ") + "its log could not be read: " + read.unreadable());
            return;
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
                                 String brain, String commit, String machine, int cap,
                                 String registration) throws IOException {
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
        if (!registration.isEmpty()) {
            // Only when there is one. The child's own parser refuses an empty value -- a flag is
            // stated or it is absent -- and an unranked Run's header says so by carrying nothing.
            command.add(RunOne.REGISTRATION);
            command.add(registration);
        }
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
        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            if (!flag.startsWith("--")) {
                throw new IllegalArgumentException("expected a flag at argument " + i + ", found " + flag);
            }
            if (!KNOWN.contains(flag)) {
                // Named rather than ignored, and the list is printed, because the one flag this
                // command must never grow is an oracle and a silently ignored argument is how a
                // reader convinces themselves it has one.
                throw new IllegalArgumentException("the Rig does not know " + flag + "; it knows " + KNOWN);
            }
            String value;
            if (SWITCHES.contains(flag)) {
                // A switch is its own answer. It is written down as the word so that everything
                // downstream reads the map the same way, and so that a switch given twice is
                // caught by the same line that catches a flag given twice.
                value = "yes";
            } else {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(flag + " takes a value");
                }
                value = args[i + 1];
                if (value.startsWith("--")) {
                    // `--commit --root` would otherwise attest the string "--root" as the commit in
                    // every header of the invocation, and `--out --root` would write the whole
                    // thing into a folder of that name. A flag is never a value.
                    throw new IllegalArgumentException(flag + " was given the flag " + value
                            + " as its value; every flag takes a value of its own");
                }
                if (value.isEmpty()) {
                    throw new IllegalArgumentException(flag + " is stated, not left empty");
                }
                i++;
            }
            if (given.put(flag, value) != null) {
                throw new IllegalArgumentException(flag + " is given twice");
            }
            i++;
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
