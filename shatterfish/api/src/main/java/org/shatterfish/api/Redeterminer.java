package org.shatterfish.api;

/**
 * Turns the live Run's snapshot into one a search may roll out from (ADR-0009): everything the
 * player cannot know replaced by a {@link BeliefSample}'s assignment, and the handle marked
 * scrubbed. Reserved in E1 with no implementation; the scrubber is E6's.
 *
 * <p>The contract is in the shape, as {@link Simulator}'s is: {@link #scrub} is final and holds
 * the result of {@link #redetermine} to being a new snapshot, scrubbed, at the same wait, before a
 * {@link Simulator} could be asked. The flag on a handle is a claim; the harness that holds the
 * bytes is what verifies it, by id, and this class is where an implementation's claim is made.
 */
public abstract class Redeterminer {

    /** {@link #redetermine}, with its result held to the contract. */
    public final SnapshotHandle scrub(SnapshotHandle live, BeliefSample sample) {
        if (live == null || sample == null) {
            throw new IllegalArgumentException("a live snapshot handle and a sample");
        }
        SnapshotHandle result = redetermine(live, sample);
        if (result == null) {
            throw new IllegalStateException("a redeterminer returned nothing for snapshot " + live.id());
        }
        if (!result.scrubbed()) {
            throw new IllegalStateException("a redeterminer returned snapshot " + result.id() + " unscrubbed for snapshot "
                    + live.id() + ": its result must be scrubbed (ADR-0009)");
        }
        if (result.id().equals(live.id())) {
            throw new IllegalStateException("a redeterminer returned the live snapshot's own id " + live.id()
                    + " marked scrubbed: a scrubbed snapshot is a new one, never the live one relabelled");
        }
        if (result.k() != live.k()) {
            throw new IllegalStateException("a redeterminer returned snapshot " + result.id() + " at wait " + result.k()
                    + " for snapshot " + live.id() + " at wait " + live.k() + ": the wait does not move");
        }
        return result;
    }

    /** The implementation's own work: a new snapshot, scrubbed, from {@code live} and {@code sample}. */
    protected abstract SnapshotHandle redetermine(SnapshotHandle live, BeliefSample sample);
}
