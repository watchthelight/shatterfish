package org.shatterfish.harness.log;

import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Rewindable;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.Halting;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The decider that is the log: at each wait it checks that this build is seeing what the log recorded, and hands
 * back the Action that was taken (story 3.4; moved out of {@link Replay} for story 8.5).
 *
 * <p>Two ways to meet a wait the build does not see as the log did. <b>Strict</b> (the Rig's {@link Replay}) throws
 * {@link Replay.Diverged}, which ends the replay with the first divergence named. <b>Stopping</b> (the Overlay's
 * replay session) records a {@link Halting.Halt}, answers with a wait that is never executed, and keeps answering so;
 * the Run ends at the halt instead of executing anything (a thrown error would be answered, in the Overlay, with a
 * wait that is executed, which would play on past the point where the replay stopped agreeing with the log).
 *
 * <p>It holds waits and the marks that make a log unverifiable, and nothing else: no header, so no seed and no salt
 * (story 3.3's finding). It is {@link Rewindable}, so the Overlay can put it back when a stale answer is dropped.
 */
public final class LogFollower implements Deliberator, Rewindable, Halting {

    /** The cause an Overlay Run closed mid-play ends with ({@code RunOutcome.Cause.CLOSED}). */
    static final String CLOSED = "CLOSED";

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

    /**
     * The Brain's own message, when the log says the Run ended because the Brain could not decide
     * (story 4.11), or null. The original threw at the wait after its last recorded one and wrote
     * no record for it; the follower, out of waits there, says the same thing the same way, so the
     * Run loop writes the same ending.
     */
    private final String brainError;

    /** Whether a wait that differs stops the replay quietly instead of throwing. */
    private final boolean stopping;

    private int at;
    private int verified;

    /**
     * The wait last followed. A Brain's log says why at every wait (story 4.1); the replay
     * reproduces the Action and states the same Decision and Belief hash the log recorded, so
     * the record it writes is the recorded one and the chains compare.
     */
    private RunLog.Wait followed;

    /** Where a stopping follower halted; written on the Brain's worker, read on the render thread after its Future. */
    private volatile Halting.Halt halt;

    private LogFollower(RunLogReader.Log log, boolean stopping) {
        this.waits = List.copyOf(log.waits());
        this.brainError = brainError(log);
        // The log's own end record is believed when it says the Run was not verifiable, even if
        // no surviving `unsupported` mark says which wait: a writer that marked its Run
        // unverifiable knows something this reader does not.
        long gap = firstGap(log);
        this.unverifiableFrom = saidUnverifiable(log) && gap == Long.MAX_VALUE ? 0 : gap;
        this.stopping = stopping;
    }

    /** The follower the Rig's Replay uses: a wait that differs throws {@link Replay.Diverged}. */
    static LogFollower strict(RunLogReader.Log log) {
        return new LogFollower(log, false);
    }

    /** The follower a replay session uses: a wait that differs halts it, and nothing is thrown. */
    public static LogFollower stopping(RunLogReader.Log log) {
        return new LogFollower(log, true);
    }

    /** The waits the log holds. */
    public int total() {
        return waits.size();
    }

    /** The waits followed so far whose Observation matched. */
    public int verified() {
        return verified;
    }

    /** The first wait from which nothing can be reproduced, or {@code Long.MAX_VALUE}. */
    long unverifiableFrom() {
        return unverifiableFrom;
    }

    @Override
    public Halting.Halt halted() {
        return halt;
    }

    @Override
    public Action decide(Observation observation) {
        if (halt != null) {
            return new Action.Wait();
        }
        if (at >= waits.size()) {
            if (unverifiableFrom != Long.MAX_VALUE) {
                // The waits ran out and the log says something past them could not be
                // expressed. That is not a Run that ended; it is a Run nothing can check.
                if (stopping) {
                    return stop(unverifiableFrom, Halting.Kind.UNVERIFIABLE, unverifiable(), unverifiable());
                }
                throw new Replay.Unverifiable(unverifiableFrom, verified);
            }
            if (stopping) {
                String end = "the log has no wait " + nextIndex() + "; its " + verified + " waits were applied and matched";
                return stop(nextIndex(), Halting.Kind.END, "the log ends here", end);
            }
            if (brainError != null) {
                throw new Decider.CannotDecide(brainError);
            }
            // The log has nothing more to say, so the Run is over as far as a reproduction
            // goes. Returning null ends the loop by its own rule rather than inventing an
            // Action nobody recorded.
            return null;
        }
        RunLog.Wait wait = waits.get(at++);
        if (wait.k() >= unverifiableFrom) {
            if (stopping) {
                return stop(unverifiableFrom, Halting.Kind.UNVERIFIABLE, unverifiable(), unverifiable());
            }
            throw new Replay.Unverifiable(unverifiableFrom, verified);
        }
        if (!observation.hash().equals(wait.obs())) {
            List<String> sections = moved(observation, wait);
            String message = "at wait " + wait.k() + " this build sees " + observation.hash()
                    + " and the log recorded " + wait.obs() + "; the sections that differ are " + sections;
            if (stopping) {
                return stop(wait.k(), Halting.Kind.MISMATCH,
                        "the Observation differs at wait " + wait.k() + " in " + String.join(", ", sections), message);
            }
            throw new Replay.Diverged(wait.k(), sections, message);
        }
        verified++;
        followed = wait;
        return wait.action();
    }

    private Action stop(long wait, Halting.Kind kind, String why, String detail) {
        halt = new Halting.Halt(wait, kind, why, detail);
        return new Action.Wait();
    }

    private long nextIndex() {
        return waits.isEmpty() ? 1 : waits.get(waits.size() - 1).k() + 1;
    }

    private String unverifiable() {
        return "the log records an input the executor could not express, so nothing from wait "
                + unverifiableFrom + " can be reproduced; the " + verified + " waits before it matched";
    }

    @Override
    public RunLog.Decision lastDecision() {
        return followed == null ? null : followed.decision();
    }

    /** The replay holds no Belief, only the hash the log recorded; see {@link #beliefHash}. */
    @Override
    public org.shatterfish.api.Belief belief() {
        return null;
    }

    @Override
    public String beliefHash() {
        return followed == null ? "" : followed.belief();
    }

    /** The cells the log recorded for the wait followed (story 4.4), so the replayed record is the recorded one. */
    @Override
    public List<Integer> lastHighlights() {
        return followed == null ? List.of() : followed.highlights();
    }

    /** Where the follower stood, for a Run that drops a stale answer and asks again (story 5.1). */
    private record Mark(int at, int verified, RunLog.Wait followed, Halting.Halt halt) {
    }

    @Override
    public Object mark() {
        return new Mark(at, verified, followed, halt);
    }

    @Override
    public void rewind(Object mark) {
        Mark was = (Mark) mark;
        at = was.at();
        verified = was.verified();
        followed = was.followed();
        halt = was.halt();
    }

    /**
     * Whether the log's end record says the Run cannot be reproduced. An Overlay Run a person closed
     * mid-play ends as {@code CLOSED} (issue #175), unverifiable because its ending was nobody's to
     * reach; the waits before it are what they were, so such an end is read as a log that stops
     * there, the way a killed Run's log is, and its waits are still checked.
     */
    static boolean saidUnverifiable(RunLogReader.Log log) {
        RunLog.End end = log.end();
        return end != null && !end.verifiable() && !CLOSED.equals(end.outcome().cause());
    }

    /**
     * The message a Run that ended as a Brain error ended with, as the loop wrote it after its
     * "at wait k: " prefix, or null for any other ending.
     */
    static String brainError(RunLogReader.Log log) {
        RunLog.End end = log.end();
        if (end == null || !"BRAIN_ERROR".equals(end.outcome().cause())) {
            return null;
        }
        String detail = end.detail();
        int colon = detail.indexOf(": ");
        return colon < 0 ? detail : detail.substring(colon + 2);
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
    static List<String> moved(Observation observation, RunLog.Wait wait) {
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
