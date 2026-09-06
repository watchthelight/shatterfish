package org.shatterfish.api;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The valid Actions at this Input wait, one entry per Action the executor would accept, computed
 * from the rest of the Observation alone (ADR-0005; ADR-0014; story 1.12). The order is canonical:
 * by kind, then by the action's own bytes, so the set an executor enumerates in any order is one
 * section with one hash.
 *
 * @param actions the valid Actions, each once
 */
public record ActionsSection(List<Action> actions) {

    /** No Action known yet: what an Observer builds before the valid set is computed. */
    public static final ActionsSection NONE = new ActionsSection(List.of());

    private static final Comparator<Action> ORDER = Comparator.comparing(Action::kind)
            .thenComparing((a, b) -> Arrays.compare(ObservationCodec.encodeValue(a), ObservationCodec.encodeValue(b)));

    public ActionsSection {
        actions = Canon.sorted(actions, ORDER, "actions");
        Canon.noRepeats(actions, "actions");
    }
}
