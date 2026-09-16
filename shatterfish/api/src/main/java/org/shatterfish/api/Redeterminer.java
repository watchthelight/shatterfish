package org.shatterfish.api;

/**
 * Turns the live Run's snapshot into one a search may roll out from (ADR-0009): everything the
 * player cannot know replaced by a {@link BeliefSample}'s assignment, and the handle marked
 * scrubbed. Reserved in E1 with no implementation; the scrubber is E6's.
 *
 * <p>The contract is in {@link #scrub}: an implementation's result must say it is scrubbed, or
 * the interface refuses it before a {@link Simulator} could be asked.
 */
@FunctionalInterface
public interface Redeterminer {

    /** The implementation's own work: a new snapshot, scrubbed, from {@code live} and {@code sample}. */
    SnapshotHandle redetermine(SnapshotHandle live, BeliefSample sample);

    /** {@link #redetermine}, with its result held to the contract. */
    default SnapshotHandle scrub(SnapshotHandle live, BeliefSample sample) {
        if (live == null || sample == null) {
            throw new IllegalArgumentException("a live snapshot handle and a sample");
        }
        SnapshotHandle result = redetermine(live, sample);
        if (result == null || !result.scrubbed()) {
            throw new IllegalStateException("a redeterminer returned " + (result == null ? "nothing" : "snapshot " + result.id()
                    + " unscrubbed") + " for snapshot " + live.id() + ": its result must be scrubbed (ADR-0009)");
        }
        return result;
    }
}
