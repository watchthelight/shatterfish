package org.shatterfish.harness.driver;

import org.shatterfish.api.BeliefSample;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Redeterminer;
import org.shatterfish.api.SnapshotHandle;
import org.shatterfish.harness.redetermine.Scrub;
import org.shatterfish.harness.redetermine.ScrubRow;
import org.shatterfish.harness.redetermine.ScrubSample;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Turns the live Run's snapshot into one a rollout may start from (ADR-0009): every row of the scrub table applied by
 * {@link Scrub}, and the result kept in the store as a new snapshot that only this class can mark scrubbed.
 *
 * <p>It lives in the driver package because a {@link Snapshot} does, and the bytes of one never leave it
 * ({@code SnapshotBoundaryTest}). The scrub itself is in {@code redetermine} and sees only the bundles, the
 * Observation at the wait and the sample.
 */
public final class SnapshotScrubber extends Redeterminer {

    private final SnapshotStore store;
    private final Set<ScrubRow> rows;
    private Scrub.Report lastReport;

    /** A scrubber with every row of the table. */
    public SnapshotScrubber(SnapshotStore store) {
        this(store, EnumSet.allOf(ScrubRow.class));
    }

    /**
     * A scrubber with only {@code rows}. The differential test uses this to check one row at a time, and to check that
     * a scrubber missing a row fails naming it; a search never does.
     */
    public SnapshotScrubber(SnapshotStore store, Set<ScrubRow> rows) {
        this.store = Objects.requireNonNull(store, "a snapshot store");
        this.rows = EnumSet.copyOf(rows);
    }

    /** What the last scrub found and changed, by row. */
    public Scrub.Report lastReport() {
        return lastReport;
    }

    @Override
    protected SnapshotHandle redetermine(SnapshotHandle live, BeliefSample sample) {
        UiRole.require("SnapshotScrubber.redetermine()");
        Snapshot from = store.find(live.id());
        if (from == null || from.scrubbed() || live.scrubbed()) {
            throw new IllegalArgumentException("only the live Run's own snapshots are scrubbed, and this store holds none named "
                    + live.id() + " that is one");
        }
        if (from.k() != live.k()) {
            throw new IllegalArgumentException(live.id() + " names wait " + live.k() + ", and the snapshot was taken at wait "
                    + from.k());
        }
        Observation shown = from.observation();
        boolean[] fov = from.fov();
        if (shown == null || fov == null) {
            throw new IllegalArgumentException("snapshot " + live.id() + " was built without the screen it showed, which the"
                    + " scrub reproduces");
        }
        ScrubSample parsed = ScrubSample.parse(sample);
        int depth = shown.header().depth();
        int branch = shown.header().branch();
        String levelFile = "depth" + depth + ".dat";
        Scrub.Input in = new Scrub.Input(from.files(), "game.dat", levelFile, shown, fov, depth, branch);
        Scrub.Output out = Scrub.scrub(in, parsed, rows);
        lastReport = out.report();
        long active = rows.contains(ScrubRow.ROLLOUT_SALT) ? out.salt() : from.salt();
        int mask = 0;
        for (ScrubRow row : rows) {
            mask |= 1 << row.ordinal();
        }
        String name = "scrubbed-" + from.id() + "-" + sample.hash() + "-r"
                + Integer.toHexString(mask);
        Snapshot copy = new Snapshot(name, from.k(),
                from.seed(), from.heroClass(), from.salt(), from.slot(), out.files(), from.log(), from.emotes(), shown, fov, true,
                active);
        return store.adopt(copy);
    }
}
