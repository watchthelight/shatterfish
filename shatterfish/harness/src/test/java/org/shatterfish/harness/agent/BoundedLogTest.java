package org.shatterfish.harness.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BoundedLog} (story 5.4, FR-38): oldest first, newest last, never more than its capacity --
 * held here directly, so {@link EmbeddedRun}'s own {@code HISTORY_CAPACITY} (200) does not need a
 * test that drives 200 real waits through a game to hold the trimming rule.
 */
class BoundedLogTest {

    private static RunLog.Wait record(long k) {
        return new RunLog.Wait(k, k * 1000, 1, 0, "a".repeat(64), Map.of("hero", "a".repeat(64)),
                new Action.Wait(), true, RunLog.BOT, null, "", List.of(), 0);
    }

    @Test
    @DisplayName("within capacity: every record, oldest first")
    void within_capacity() {
        BoundedLog log = new BoundedLog(3);
        log.add(record(1));
        log.add(record(2));
        assertEquals(List.of(record(1), record(2)), log.asList());
    }

    @Test
    @DisplayName("past capacity: the oldest drops, so the log is never more than the capacity")
    void past_capacity_drops_the_oldest() {
        BoundedLog log = new BoundedLog(3);
        for (long k = 1; k <= 5; k++) {
            log.add(record(k));
        }
        assertEquals(List.of(record(3), record(4), record(5)), log.asList(), "1 and 2 dropped, newest (5) last");
    }

    @Test
    @DisplayName("a capacity under one is refused")
    void capacity_under_one_is_refused() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLog(0));
    }

    @Test
    @DisplayName("asList() is a copy: mutating the returned list, if it even allowed it, would not reach the log")
    void as_list_is_a_copy() {
        BoundedLog log = new BoundedLog(2);
        log.add(record(1));
        List<RunLog> first = log.asList();
        log.add(record(2));
        assertEquals(1, first.size(), "the earlier copy is unaffected by a later add");
    }
}
