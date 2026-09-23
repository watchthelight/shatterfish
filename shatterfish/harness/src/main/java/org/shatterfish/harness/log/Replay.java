package org.shatterfish.harness.log;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;

import java.nio.file.Path;
import java.util.ArrayList;
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

    /** What a Replay found. */
    public record Result(String runId, int waits, int verified, boolean reproduced, String chain,
                         String originalChain, String why) {

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

        Unverifiable(long at) {
            super("unverifiable from wait " + at + ": the log records an input the executor could"
                    + " not express, so nothing after it can be reproduced");
            this.at = at;
        }

        /** The wait from which nothing can be reproduced. */
        public long at() {
            return at;
        }
    }

    private Replay() {
    }

    /**
     * Whether this build can replay {@code header} at all, and what differs when it cannot.
     *
     * <p>Four things, checked before anything is played: the log's schema version, the upstream tag,
     * the Observation schema version and the Profile version. Each decides what a Run <em>is</em>,
     * so a difference in any of them makes a comparison meaningless rather than negative.
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
        RunLogVerifier.Verified verified = RunLogVerifier.of(file);
        if (!verified.ok()) {
            // The chain first, before a Run is started. A log that was edited describes a Run that
            // never happened, and replaying it would be measuring this build against a fiction.
            throw new IllegalArgumentException("the log " + file + " does not verify, so there is"
                    + " nothing to reproduce: " + verified.why());
        }
        RunLogReader.Log log = RunLogReader.of(file);
        if (!log.readable()) {
            throw new IllegalArgumentException("the log " + file + " could not be read: " + log.unreadable());
        }
        RunLog.Header header = log.header();
        Refusal refusal = refusal(header);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal.toString());
        }

        Following following = new Following(log);
        RunLoop.Logging logging = new RunLoop.Logging(out, header.commit(), header.brain(),
                header.registration(), machine, header.oracle());
        SeedSet.Entry triple = new SeedSet.Entry(header.seed(), header.heroClass(),
                header.challenges(), header.seedCode());

        // Under the original's own cap. Without it the Replay reproduced every wait and then
        // ended differently -- the original stopped by its cap, the Replay by running out of
        // Actions -- which is what put the cap in the header and the schema at version 2.
        RunOutcome outcome = new RunLoop().playTriple(triple, header.salt(), following,
                header.cap(), logging);

        String ours = RunLogVerifier.of(out.resolve(RunLog.fileName(header.runId()))).chain();
        String theirs = verified.chain();
        boolean reproduced = !ours.isEmpty() && ours.equals(theirs);
        return new Result(header.runId(), log.waits().size(), following.verified, reproduced, ours,
                theirs, reproduced ? "" : "the Replay's chain is " + ours + " and the log's is " + theirs
                        + ", so this build did not reproduce the Run the log describes (" + outcome.cause() + ")");
    }

    /**
     * The decider that is the log: at each wait it checks that this build is seeing what the log
     * recorded, and hands back the Action that was taken.
     */
    private static final class Following implements Decider {

        private final List<RunLog.Wait> waits;
        private final List<Long> unsupported = new ArrayList<>();
        private int at;
        private int verified;

        Following(RunLogReader.Log log) {
            this.waits = log.waits();
            for (RunLog record : log.records()) {
                if (record instanceof RunLog.Unsupported gap) {
                    unsupported.add(gap.k());
                }
            }
        }

        @Override
        public Action decide(Observation observation) {
            if (at >= waits.size()) {
                // The log has nothing more to say, so the Run is over as far as a reproduction
                // goes. Returning null ends the loop by its own rule rather than inventing an
                // Action nobody recorded.
                return null;
            }
            RunLog.Wait wait = waits.get(at++);
            if (unsupported.contains(wait.k())) {
                throw new Unverifiable(wait.k());
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
