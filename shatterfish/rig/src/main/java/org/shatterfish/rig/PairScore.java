package org.shatterfish.rig;

import org.shatterfish.api.RunLog;

/**
 * One pair's verdict: which of two Runs of the same triple under the same salt went better
 * (story 3.6, ADR-0012 option 1, PRD glossary "Composite outcome").
 *
 * <p><b>The order is lexicographic, and each step only speaks when every earlier one is equal.</b>
 * Win first. Then, for two winning Runs, Score. Then bosses killed, then Floor depth, then turns
 * survived. Bosses rank above depth so that diving deeper without passing a boss is not rewarded
 * (SM-C2): the game locks a boss floor's stairs until the boss is dead, so depth and bosses killed
 * move together and depth alone would pay a Brain for dying one floor further down.
 *
 * <p><b>Score only counts between two wins.</b> A losing Run's Score is gameable -- it rewards
 * picking up gold on the way to dying (SM-C3) -- so it is not consulted unless both Runs won.
 *
 * <p><b>Scored from the candidate's side.</b> One means the candidate did better, so a test that
 * accepts is a test saying the candidate is better, which is how Fishtest reads a pass.
 */
public enum PairScore {

    /** The candidate did worse. */
    WORSE(0),

    /** The two are equal at every step of the order, or one of the pair is missing. */
    EQUAL(1),

    /** The candidate did better. */
    BETTER(2);

    /** The score in halves: 0, 1 or 2, so that no float stands for one half. */
    private final int halves;

    PairScore(int halves) {
        this.halves = halves;
    }

    /** 0, 1 or 2 halves: the index into the trinomial the sequential test counts. */
    public int halves() {
        return halves;
    }

    /**
     * The pair's score from the candidate's side.
     *
     * <p>A missing outcome -- a crash, a hang, a log with no ending -- scores {@link #EQUAL}. ADR-0012
     * scores it as a tie so that a Brain cannot win by crashing on the seeds it would lose, and the
     * caller counts it separately so that a comparison full of missing pairs is visible as one.
     */
    public static PairScore of(RunLog.Outcome candidate, RunLog.Outcome baseline) {
        if (candidate == null || baseline == null) {
            return EQUAL;
        }
        int order = compare(candidate, baseline);
        return order > 0 ? BETTER : order < 0 ? WORSE : EQUAL;
    }

    /** Positive when {@code one} is better, negative when worse, zero when equal. */
    static int compare(RunLog.Outcome one, RunLog.Outcome other) {
        if (one.win() != other.win()) {
            return one.win() ? 1 : -1;
        }
        if (one.win()) {
            int score = Long.compare(one.score(), other.score());
            if (score != 0) {
                return score;
            }
        }
        int bosses = Integer.compare(one.bosses(), other.bosses());
        if (bosses != 0) {
            return bosses;
        }
        int depth = Integer.compare(one.depth(), other.depth());
        if (depth != 0) {
            return depth;
        }
        // Survived longer is better. A Run stopped by the turn cap and one that died at the same
        // depth are separated here, which is the one place turns mean anything.
        return Long.compare(one.turns(), other.turns());
    }
}
