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
     * an unchallenged Run and compare it against a challenged one.
     */
    public static Refusal refusal(RunLog.Header header) {
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

        // The waits and the unsupported marks, and nothing else. `Following` is a Decider, and the
        // Log it used to be handed holds the header, which holds the salt and the seed. Nothing
        // read them -- but story 3.3's blocking finding was a Decider seeded from the salt, and
        // "the constructor happens not to keep it" is a weaker sentence than "it is never given
        // it". A Decider is handed Observations and the Actions it is reproducing.
        Following following = new Following(log.waits(), Following.firstGap(log),
                log.end() != null && !log.end().verifiable());
        // `false`, in the source. The oracle flag was read out of the log here, which made this the
        // only production caller that could set it at all -- from a file. `refusal` refuses such a
        // log above; this is the second lock on the same door.
        RunLoop.Logging logging = new RunLoop.Logging(out, header.commit(), header.brain(),
                header.registration(), machine, false);
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
        if (following.unverifiableFrom != Long.MAX_VALUE) {
            throw new Unverifiable(following.unverifiableFrom, following.verified);
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
            why = "the log records no ending, so there is no chain to reach: the " + following.verified
                    + " waits it does record were reproduced, and the Run it describes was killed"
                    + " before it finished";
        } else if (!reproduced) {
            why = "the Replay's chain is " + ours + " and the log's is " + theirs
                    + ", so this build did not reproduce the Run the log describes ("
                    + outcome.cause() + ")";
        }
        return new Result(header.runId(), log.waits().size(), following.verified,
                reproduced && log.end() != null, ours, theirs, header.commit(), why);
    }

    /**
     * The decider that is the log: at each wait it checks that this build is seeing what the log
     * recorded, and hands back the Action that was taken.
     */
    private static final class Following implements Decider {

        private final List<RunLog.Wait> waits;

        /**
         * The first wait from which nothing can be reproduced, or {@code Long.MAX_VALUE}.
         *
         * <p>One number rather than a list, because an {@code unsupported} record makes everything
         * <em>from</em> the wait it names unverifiable -- which is what {@code End.verifiable}'s own
         * javadoc says -- and the first draft only fired when the mark named a wait the log also
         * recorded. The realistic overlay case is the other one: the input the executor could not
         * express is the reason there is no wait record there, and a mark after the last recorded
         * wait was ignored entirely.
         */
        private final long unverifiableFrom;

        private int at;
        private int verified;

        Following(List<RunLog.Wait> waits, long unverifiableFrom, boolean saidUnverifiable) {
            this.waits = List.copyOf(waits);
            // The log's own end record is believed when it says the Run was not verifiable, even if
            // no surviving `unsupported` mark says which wait: a writer that marked its Run
            // unverifiable knows something this reader does not.
            this.unverifiableFrom = saidUnverifiable && unverifiableFrom == Long.MAX_VALUE
                    ? 0 : unverifiableFrom;
        }

        @Override
        public Action decide(Observation observation) {
            if (at >= waits.size()) {
                if (unverifiableFrom != Long.MAX_VALUE) {
                    // The waits ran out and the log says something past them could not be
                    // expressed. That is not a Run that ended; it is a Run nothing can check.
                    throw new Unverifiable(unverifiableFrom, verified);
                }
                // The log has nothing more to say, so the Run is over as far as a reproduction
                // goes. Returning null ends the loop by its own rule rather than inventing an
                // Action nobody recorded.
                return null;
            }
            RunLog.Wait wait = waits.get(at++);
            if (wait.k() >= unverifiableFrom) {
                throw new Unverifiable(unverifiableFrom, verified);
            }
            if (!observation.hash().equals(wait.obs())) {
                throw new Diverged(wait.k(), moved(observation, wait),
                        "at wait " + wait.k() + " this build sees " + observation.hash()
                                + " and the log recorded " + wait.obs() + "; the sections that differ are "
                                + moved(observation, wait));
            }
            verified++;
            return wait.action();
        }

        /** The first wait an {@code unsupported} record makes unreproducible, if there is one. */
        static long firstGap(RunLogReader.Log log) {
            long first = Long.MAX_VALUE;
            for (RunLog record : log.records()) {
                if (record instanceof RunLog.Unsupported gap) {
                    first = Math.min(first, gap.k());
                }
            }
            return first;
        }

        /** Which sections differ, because "wait 412 differs" is not something anyone can act on. */
        private static List<String> moved(Observation observation, RunLog.Wait wait) {
            Map<String, String> fresh = observation.sectionHashes();
            TreeSet<String> differing = new TreeSet<>();
            for (Map.Entry<String, String> section : wait.sections().entrySet()) {
                if (!section.getValue().equals(fresh.get(section.getKey()))) {
                    differing.add(section.getKey());
                }
            }
            for (String section : fresh.keySet()) {
                if (!wait.sections().containsKey(section)) {
                    differing.add(section);
                }
            }
            return List.copyOf(differing);
        }
    }
}
