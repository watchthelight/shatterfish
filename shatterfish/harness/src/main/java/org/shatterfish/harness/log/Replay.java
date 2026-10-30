package org.shatterfish.harness.log;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A Run, played again from its own log, and checked (story 3.4, FR-24).
 *
 * <p><b>A Replay is a Run whose decider is the log.</b> {@link Decider} is an Observation in and an
 * Action out, which is exactly what a log offers — so this does not carry a second loop. It plays
 * through the same {@code RunLoop}, the same executor, the same driver and the same Profile as the
 * Run it is checking; a reproduction through a different path would prove less, and a second loop
 * would be a second set of rules about what a Run is.
 *
 * <p><b>The comparison is the two chains.</b> A chain covers everything about a Run except how long
 * the decider took and which machine it ran on, so a Replay that writes its own log and arrives at
 * the same chain has reproduced every Observation, every Action, every section hash, the turn counts
 * and the ending — in one value a person can check by eye. Comparing hashes wait by wait is what
 * names the first divergence; comparing chains is what says there was none.
 *
 * <p><b>What it proves.</b> That this build, on this machine, plays the tuple in the log the way the
 * log says it was played. It does not prove the log describes a Run anybody actually performed —
 * the rules are published, so a log can be written from scratch — and it cannot prove anything about
 * a build that means something different by a Run, which is why a mismatched schema, tag,
 * Observation version or Profile version is refused before a Run is started rather than reported as
 * a divergence afterwards.
 */
public final class Replay {

    /** Why a log cannot be replayed by this build at all. */
    public record Refusal(String field, String logSays, String buildSays) {

        @Override
        public String toString() {
            return "this build cannot replay that log: its " + field + " is " + logSays
                    + " and this build's is " + buildSays
                    + "; a reproduction between builds that mean different things by a Run proves nothing";
        }
    }

    /**
     * What a Replay found.
     *
     * @param attested the commit the log says played the original Run, which the Replay copies into
     *                 its own log so the two chains can be compared at all. It is therefore the one
     *                 header field that says nothing about the build that did the reproducing, and
     *                 a caller that reports a reproduction should say whose commit this is
     */
    public record Result(String runId, int waits, int verified, boolean reproduced, String chain,
                         String originalChain, String attested, String why) {

        /** Whether every wait matched and the two chains agree. */
        public boolean ok() {
            return reproduced && why.isEmpty();
        }
    }

    /** Raised when a wait's Observation is not the one the log recorded. */
    public static final class Diverged extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final long at;

        /** Transient: this is a value to act on here, not one to carry across a wire. */
        private final transient List<String> sections;

        Diverged(long at, List<String> sections, String message) {
            super(message);
            this.at = at;
            this.sections = List.copyOf(sections);
        }

        /** The wait at which this build and the log stopped agreeing. */
        public long at() {
            return at;
        }

        /** The sections whose hashes differ, which is what a person can act on. */
        public List<String> sections() {
            return sections;
        }
    }

    /** Raised at an {@code unsupported} record: a human input the executor could not express. */
    public static final class Unverifiable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final long at;

        private final int verified;

        Unverifiable(long at, int verified) {
            super("unverifiable from wait " + at + ": the log records an input the executor could"
                    + " not express, so nothing after it can be reproduced; the " + verified
                    + " waits before it were reproduced");
            this.at = at;
            this.verified = verified;
        }

        /** The wait from which nothing can be reproduced. */
        public long at() {
            return at;
        }

        /**
         * How many waits were reproduced before it.
         *
         * <p>A Run verified to wait 400 and unverifiable from 401 is a different thing from one
         * unverifiable from wait 1, and the first draft reported them identically. When the overlay
         * starts recording human Runs this is the number that says how much of one was checked.
         */
        public int verified() {
            return verified;
        }
    }

    /**
     * The largest turn cap a Replay will take out of a file.
     *
     * <p>The cap comes from the log, the log can say anything, and `Canon` only requires that it be
     * at least one -- so a header claiming two billion turns makes `--replay` run until the hero
     * dies of something. Ten times the Rig's own cap is past any Run this project plays and well
     * short of a machine nobody can stop.
     */
    public static final int CAP_MOST = RunLoop.TURN_CAP * 10;

    private Replay() {
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be read", e);
        }
    }

    /**
     * Whether this build can replay {@code header} at all, and what differs when it cannot.
     *
     * <p><b>The versions that decide what a Run is,</b> checked before anything is played: the log's
     * schema version, the upstream tag, the Observation schema version, the Profile version and the
     * Codex version. Every one of them is a chained header field, and that is the test for
     * membership here — a chained field this build would write differently makes the two chains
     * differ for a reason that has nothing to do with whether the Run reproduced, so it has to be a
     * refusal rather than a comparison. The Codex was missing from the first draft, which would
     * have turned the next Codex regeneration into a nightly job reporting that Windows and Linux
     * disagree about a Run.
     *
     * <p><b>And two things about the Run itself.</b> A log that claims the oracle is refused
     * outright: an oracle Run is not ranked (FR-11), and replaying one is the only way this build
     * could be asked to start a Run that may see what a player could not. A log with challenges set
     * is refused because {@code playTriple} does not apply them, so replaying it would quietly play
     * an unchallenged Run and compare it against a challenged one. A log whose header says
     * {@code driver: embedded} is refused by the Rig because its interface size is not the headless driver's
     * (issue #169; story 5.13 made the Run reproducible, {@code waitsOf} replays one); replaying one would report a Run that "did not reproduce"
     * when it was never claimed to.
     */
    public static Refusal refusal(RunLog.Header header) {
        return refusal(header, true);
    }

    private static Refusal refusal(RunLog.Header header, boolean refuseTheOverlay) {
        HeadlessBoot.ensure();
        if (header.v() != RunLog.VERSION) {
            return new Refusal("log schema version", String.valueOf(header.v()),
                    String.valueOf(RunLog.VERSION));
        }
        String tag = HeadlessBoot.pinnedTag();
        if (!header.tag().equals(tag)) {
            return new Refusal("upstream tag", header.tag(), tag);
        }
        if (header.obsv() != ObservationCodec.SCHEMA_VERSION) {
            return new Refusal("Observation schema version", String.valueOf(header.obsv()),
                    String.valueOf(ObservationCodec.SCHEMA_VERSION));
        }
        if (header.profile() != Profile.VERSION) {
            return new Refusal("Profile version", String.valueOf(header.profile()),
                    String.valueOf(Profile.VERSION));
        }
        if (header.codex() != Codex.VERSION) {
            return new Refusal("Codex version", String.valueOf(header.codex()),
                    String.valueOf(Codex.VERSION));
        }
        if (header.oracle()) {
            return new Refusal("oracle", "a Run that saw what a player could not",
                    "a build that plays fair Runs and replays fair Runs (FR-11)");
        }
        if (header.practiceRun()) {
            try {
                org.shatterfish.harness.practice.Practice.parse(header.practice(), header.heroClass());
            } catch (IllegalArgumentException unresolved) {
                return new Refusal("practice start", header.practice(),
                        "a start this build resolves to the same kit (" + unresolved.getMessage() + ")");
            }
        }
        if (header.embedded() && refuseTheOverlay) {
            return new Refusal("driver", RunLog.Header.EMBEDDED,
                    "a headless Run, because an Overlay Run is not reproducible from its tuple or"
                            + " its Action list by the Rig until the headless driver plays its interface size (issue #169, ADR-0013)");
        }
        if (header.challenges() != 0) {
            return new Refusal("challenges", String.valueOf(header.challenges()),
                    "0, because a Replay does not yet apply a challenge set and would play"
                            + " a different Run");
        }
        if (header.cap() > CAP_MOST) {
            return new Refusal("turn cap", String.valueOf(header.cap()),
                    "at most " + CAP_MOST + ", because a Replay takes its cap from the file and a"
                            + " file can say anything");
        }
        return null;
    }

    /** A log that verifies and that this build can play: the header, the records and the chain. */
    public record Opened(RunLog.Header header, RunLogReader.Log log, String chain) {
    }

    /**
     * Opens a log to play it in the Overlay (story 8.5): the chain is verified first, the file is read once, it
     * must hold one header, and this build must be one that can play it. Unlike {@link #of} it accepts an Overlay
     * log (a person's or the Brain's), since the Overlay is the driver that played it; an oracle log, a challenge
     * set, a turn cap past {@link #CAP_MOST} and every build mismatch are refused as {@link #refusal} refuses them.
     *
     * @throws IllegalArgumentException with the reason, in words the Panel shows, when the log cannot be played
     */
    public static Opened open(Path file) {
        String text = read(file);
        RunLogVerifier.Verified verified = RunLogVerifier.of(text);
        if (!verified.ok()) {
            throw new IllegalArgumentException("the log does not verify, so it is not played: " + verified.why());
        }
        RunLogReader.Log log = RunLogReader.of(text);
        if (!log.readable()) {
            throw new IllegalArgumentException("the log could not be read: " + log.unreadable());
        }
        long headers = log.records().stream().filter(RunLog.Header.class::isInstance).count();
        if (headers != 1) {
            throw new IllegalArgumentException("a Run log holds one header and this one holds " + headers
                    + "; two Runs in one file are not one Run");
        }
        Refusal refusal = refusal(log.header(), false);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal.toString());
        }
        return new Opened(log.header(), log, verified.chain());
    }

    /**
     * Replays the log at {@code file}, writing the Replay's own log under {@code out}.
     *
     * <p><b>The Replay attests what the log attests.</b> The tag, the commit, the Brain and the
     * Registration are all supplied by whoever started a Run -- the methodology page calls them
     * attested rather than verified -- and a Replay is reproducing the Run those fields describe,
     * not making a fresh claim of its own. Taking the Brain and the Registration from the log while
     * taking the commit from the caller is what the committed reference log caught: every wait
     * reproduced, and the chains differed in the one chained field no other checkout could match.
     *
     * <p>{@code machine} is the exception, and it is the exception because it is unchained: where
     * this execution happened is a true thing to record and is not part of what the Run was. A
     * Replay's own log therefore differs from the original in exactly the fields the chain excludes,
     * which is the property the round-trip test asserts line by line.
     *
     * @throws IllegalArgumentException when this build cannot replay the log at all
     * @throws Diverged                 when a wait's Observation is not the one recorded
     * @throws Unverifiable             at an {@code unsupported} record
     */
    public static Result of(Path file, Path out, String machine) {
        // The folder it is reading, before anything else. A Replay writes a log named for the Run
        // it is reproducing, so a Replay into the source folder writes over the only copy of the
        // file it is checking -- and if it then diverges, the evidence needed to look into the
        // divergence is the file it just destroyed. The story froze this as a Never and nothing
        // enforced it; `Runner` was safe only because `emptyFolder` happened to refuse.
        Path from = file.toAbsolutePath().normalize();
        if (from.getParent() != null && from.getParent().equals(out.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("a Replay writes beside the log it is checking and"
                    + " not over it, and " + out + " is the folder " + file + " is in");
        }
        // Read once. Verifying and reading used to open the file twice, so a log being written, or
        // swapped, between the two calls could verify as one thing and be replayed as another.
        String text = read(file);
        RunLogVerifier.Verified verified = RunLogVerifier.of(text);
        if (!verified.ok()) {
            // The chain first, before a Run is started. A log that was edited describes a Run that
            // never happened, and replaying it would be measuring this build against a fiction.
            throw new IllegalArgumentException("the log " + file + " does not verify, so there is"
                    + " nothing to reproduce: " + verified.why());
        }
        RunLogReader.Log log = RunLogReader.of(text);
        if (!log.readable()) {
            throw new IllegalArgumentException("the log " + file + " could not be read: " + log.unreadable());
        }
        long headers = log.records().stream().filter(RunLog.Header.class::isInstance).count();
        if (headers != 1) {
            // Two logs concatenated and rechained. Each is a valid log and the join is a valid
            // chain, so only counting catches it -- and `header()` would hand back the first while
            // the waits came from both.
            throw new IllegalArgumentException("a Run log holds one header and " + file + " holds "
                    + headers + "; two Runs in one file are not one Run");
        }
        RunLog.Header header = log.header();
        Refusal refusal = refusal(header);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal.toString());
        }

        // The waits and the unsupported marks, and nothing else. `LogFollower` is a Decider, and the
        // Log it used to be handed holds the header, which holds the salt and the seed. Nothing
        // read them -- but story 3.3's blocking finding was a Decider seeded from the salt, and
        // "the constructor happens not to keep it" is a weaker sentence than "it is never given
        // it". A Decider is handed Observations and the Actions it is reproducing.
        LogFollower following = LogFollower.strict(log);
        // `false`, in the source. The oracle flag was read out of the log here, which made this the
        // only production caller that could set it at all -- from a file. The guard that stops that
        // now is `refusal`, above, which refuses an oracle log before anything is played; the
        // battery confirmed it by surviving a mutation of this line. It stays `false` anyway,
        // because a fairness-critical flag should not be one refusal away from being data.
        RunLoop.Logging logging = new RunLoop.Logging(out, header.commit(), header.brain(),
                header.registration(), machine, false);
        if (header.practiceRun()) {
            // A practice Run is reproduced from the same start: the stamp names the boss and the profile,
            // and parse refuses one whose kit this build resolves differently (ADR-0024).
            logging = logging.practising(org.shatterfish.harness.practice.Practice.parse(header.practice(),
                    header.heroClass()));
        }
        SeedSet.Entry triple = new SeedSet.Entry(header.seed(), header.heroClass(),
                header.challenges(), header.seedCode());

        // Under the original's own cap. Without it the Replay reproduced every wait and then
        // ended differently -- the original stopped by its cap, the Replay by running out of
        // Actions -- which is what put the cap in the header and the schema at version 2.
        RunOutcome outcome = new RunLoop().playTriple(triple, header.salt(), following,
                header.cap(), logging);

        // After the Run, not only inside the decider. A mark past the last recorded wait is never
        // reached by `decide`: the Run ends at its cap, or the hero dies, before anything asks for
        // another Action -- so the Replay would finish, compare two chains that cannot match, and
        // report a failed reproduction of a Run that is simply not reproducible.
        if (following.unverifiableFrom() != Long.MAX_VALUE) {
            throw new Unverifiable(following.unverifiableFrom(), following.verified());
        }
        String ours = RunLogVerifier.of(out.resolve(RunLog.fileName(header.runId()))).chain();
        String theirs = verified.chain();
        boolean reproduced = !ours.isEmpty() && ours.equals(theirs);
        String why = "";
        if (log.end() == null) {
            // A killed Run's log. Its prefix is a valid log and every wait in it can be reproduced,
            // but it records no ending, so there is no ending to reach and the chains cannot agree
            // however faithful the reproduction was. Saying "did not reproduce" of that would be
            // reporting an incomplete measurement as a failed one.
            why = "the log records no ending, so there is no chain to reach: the " + following.verified()
                    + " waits it does record were reproduced, and the Run it describes was killed"
                    + " before it finished";
        } else if (!reproduced) {
            why = "the Replay's chain is " + ours + " and the log's is " + theirs
                    + ", so this build did not reproduce the Run the log describes ("
                    + outcome.cause() + ")";
        }
        return new Result(header.runId(), log.waits().size(), following.verified(),
                reproduced && log.end() != null, ours, theirs, header.commit(), why);
    }

    /**
     * What {@link #waitsOf} found: how many waits the log records, how many were reproduced with their
     * Observation hash matching, and the first wait an {@code unsupported} record makes unreproducible
     * (0 when there is none).
     */
    public record Waits(String runId, int waits, int verified, long unverifiableFrom) {
    }

    /**
     * Replays the waits of the log at {@code file} and checks every Observation hash, and nothing else
     * (story 5.9): for a log whose chain a Replay cannot reach by construction, a human's, whose waits
     * say {@code actor: human} and which carries {@code mode}, {@code shadow} and {@code note} records a
     * Replay, which is the executor playing the recorded Actions, never writes. It refuses what
     * {@link #refusal} refuses except the Overlay's driver, since the point is to check a log the Overlay
     * wrote; it counts nothing, scores nothing and is not a Rig path (the Rig replays through
     * {@link #of}, which still refuses such a log). An Overlay log played on the desktop is still not
     * reproducible (ADR-0013's story 5.1 amendment); one recorded where the frames are the headless
     * driver's is, and {@code HumanTurnReplayTest} holds it.
     *
     * @throws Diverged when a wait's Observation is not the one recorded
     */
    public static Waits waitsOf(Path file, String machine) {
        // The replay's own log is a scratch file, deleted before this returns: it would say a person's
        // Actions were the bot's under a headless header, which is a log the Rig would count (the
        // fairness review). What this returns is the check, and nothing else survives it.
        Path out;
        try {
            out = Files.createTempDirectory("shatterfish-human-check");
        } catch (IOException e) {
            throw new UncheckedIOException("no scratch folder for the human log's check", e);
        }
        try {
            return waitsOf(file, out, machine);
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(out)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException | RuntimeException leftBehind) {
                // A scratch folder under the system's temporary directory; the check stands either way.
            }
        }
    }

    private static Waits waitsOf(Path file, Path out, String machine) {
        String text = read(file);
        RunLogVerifier.Verified verified = RunLogVerifier.of(text);
        if (!verified.ok()) {
            throw new IllegalArgumentException("the log " + file + " does not verify: " + verified.why());
        }
        RunLogReader.Log log = RunLogReader.of(text);
        if (!log.readable()) {
            throw new IllegalArgumentException("the log " + file + " could not be read: " + log.unreadable());
        }
        RunLog.Header header = log.header();
        Refusal refusal = refusal(header, false);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal.toString());
        }
        LogFollower following = LogFollower.strict(log);
        RunLoop.Logging logging = new RunLoop.Logging(out, header.commit(), header.brain(), header.registration(),
                machine, false);
        if (header.practiceRun()) {
            logging = logging.practising(org.shatterfish.harness.practice.Practice.parse(header.practice(),
                    header.heroClass()));
        }
        SeedSet.Entry triple = new SeedSet.Entry(header.seed(), header.heroClass(), header.challenges(),
                header.seedCode());
        try {
            new RunLoop().playTriple(triple, header.salt(), following, header.cap(), logging);
        } catch (Unverifiable gap) {
            return new Waits(header.runId(), log.waits().size(), gap.verified(), gap.at());
        }
        return new Waits(header.runId(), log.waits().size(), following.verified(),
                following.unverifiableFrom() == Long.MAX_VALUE ? 0 : following.unverifiableFrom());
    }
}
