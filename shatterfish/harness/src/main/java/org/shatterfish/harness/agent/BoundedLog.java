package org.shatterfish.harness.agent;

import org.shatterfish.api.RunLog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A bounded FIFO of {@link RunLog} records (story 5.4, FR-38): {@link EmbeddedRun}'s own history
 * for {@code EmbeddedRun.Snapshot.history()} -- the Decision log's source -- kept as its own small
 * class so the trimming rule ("the oldest drops once the capacity is passed") is something a test
 * can hold without booting a game or driving hundreds of real waits through one.
 *
 * <p>FR-38: "the Decision log shows ... 200 lines on screen; the Run log holds the rest" -- this is
 * the "200 lines on screen" half; the Run log file (written by the same call that adds here,
 * {@code RunLoop.record}) is the rest.
 */
final class BoundedLog {

    private final Deque<RunLog> records = new ArrayDeque<>();
    private final int capacity;

    BoundedLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("a bounded log holds at least one record: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Appends {@code record}, dropping the oldest one if this is now over capacity. */
    void add(RunLog record) {
        if (record == null) {
            throw new IllegalArgumentException("a record to remember");
        }
        records.addLast(record);
        while (records.size() > capacity) {
            records.removeFirst();
        }
    }

    /** An immutable copy, oldest first, newest last; never more than this log's capacity. */
    List<RunLog> asList() {
        return List.copyOf(records);
    }
}
