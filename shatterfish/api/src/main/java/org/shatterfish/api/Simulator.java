package org.shatterfish.api;

import java.util.List;

/**
 * The seam a tactical search rolls out through (ADR-0009, ADR-0010; FR-6): a scrubbed snapshot
 * and a list of Actions in, the Observations they lead to out. Reserved in E1 with no
 * implementation; the rollout host that implements it is E6's.
 *
 * <p>The contract is in the shape: {@link #simulate} is final and refuses a handle whose
 * {@code scrubbed} flag is false before the rollout it guards can run, so a search cannot see the
 * live Run's hidden state by asking. An implementation supplies {@link #rollout} and is never
 * called any other way.
 */
public abstract class Simulator {

    /** Rolls {@code actions} out from {@code handle}, which must be scrubbed. */
    public final RolloutResult simulate(SnapshotHandle handle, List<Action> actions) {
        if (handle == null) {
            throw new IllegalArgumentException("a snapshot handle");
        }
        if (!handle.scrubbed()) {
            throw new IllegalArgumentException("snapshot " + handle.id() + " at wait " + handle.k() + " is not scrubbed:"
                    + " no search may roll out from the live Run's hidden state (ADR-0009; FR-6, FR-13)");
        }
        if (actions == null) {
            throw new IllegalArgumentException("the Actions to roll out");
        }
        return rollout(handle, List.copyOf(actions));
    }

    /** The rollout itself, reached only through {@link #simulate}. */
    protected abstract RolloutResult rollout(SnapshotHandle handle, List<Action> actions);
}
