package org.shatterfish.harness.agent;

import org.shatterfish.api.RunLog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A bounded FIFO of {@link RunLog} records, each beside the {@link ActionContext} its own Action
 * needs to be labelled in words (story 5.4, FR-38, review round): {@link EmbeddedRun}'s own history
 * for {@code EmbeddedRun.Snapshot.history()} -- the Decision log's source -- kept as its own small
 * class so the trimming rule ("the oldest drops once the capacity is passed") is something a test
 * can hold without booting a game or driving hundreds of real waits through one.
 *
 * <p>FR-38: "the Decision log shows ... 200 lines on screen; the Run log holds the rest" -- this is
 * the "200 lines on screen" half; the Run log file (written by the same call that adds here,
 * {@code RunLoop.record}) is the rest.
 *
 * <p>Public (with {@link Entry}) because the Overlay, a different module, reads
 * {@code EmbeddedRun.Snapshot.history()}: {@code List<BoundedLog.Entry>}.
 */
public final class BoundedLog {

    /**
     * One record and the context its Action needs to be labelled the way the Decision card labels
     * one (review round: the log used to read every Action with no Observation, so its rows showed a
     * raw cell where the card showed a compass direction). {@code context} is null for a record kind
     * {@code ActionText} does not read one for, or when none was captured.
     */
    public record Entry(RunLog record, ActionContext context) {

        public Entry {
            if (record == null) {
                throw new IllegalArgumentException("an entry names the record it is for");
            }
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    BoundedLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("a bounded log holds at least one record: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Appends {@code record} with {@code context} (may be null), dropping the oldest entry if now over capacity. */
    void add(RunLog record, ActionContext context) {
        entries.addLast(new Entry(record, context));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /** An immutable copy, oldest first, newest last; never more than this log's capacity. */
    List<Entry> asList() {
        return List.copyOf(entries);
    }
}
