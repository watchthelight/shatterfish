package org.shatterfish.api;

/**
 * A handle to a snapshot the harness holds (ADR-0009): an opaque id, the Input wait it was taken
 * at, and whether it has been scrubbed of what the player cannot know. It carries no state: the
 * bytes of a snapshot never leave {@code harness}, so nothing a Brain can hold is inflatable into
 * hidden information. A handle whose {@code scrubbed} flag is false is the live Run's own, and no
 * {@link Simulator} accepts it (FR-6, FR-13). The flag is a claim: any caller can build a handle
 * that says scrubbed, so the harness that holds the bytes verifies the claim by id before it
 * hands anything back, and the live Run's own store refuses a handle that claims it.
 *
 * @param id the harness's name for the snapshot, opaque to a Brain
 * @param k the Input wait the snapshot was taken at, from 1
 * @param scrubbed whether a {@link Redeterminer} has replaced what the player cannot know
 */
public record SnapshotHandle(String id, long k, boolean scrubbed) {

    public SnapshotHandle {
        Canon.require(id != null && !id.isEmpty(), "a snapshot handle needs an id");
        Canon.require(k >= 1, "a snapshot is taken at an Input wait, which counts from 1: " + k);
    }
}
