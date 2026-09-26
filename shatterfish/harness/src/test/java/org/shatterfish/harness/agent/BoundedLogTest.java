package org.shatterfish.harness.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BoundedLog} (story 5.4, FR-38): oldest first, newest last, never more than its capacity,
 * each record beside the {@link ActionContext} its Action needs to be labelled in words (the review
 * round) -- held here directly, so {@link EmbeddedRun}'s own {@code HISTORY_CAPACITY} (200) does not
 * need a test that drives 200 real waits through a game to hold the trimming rule.
 */
class BoundedLogTest {

    private static RunLog.Wait record(long k) {
        return new RunLog.Wait(k, k * 1000, 1, 0, "a".repeat(64), Map.of("hero", "a".repeat(64)),
                new Action.Wait(), true, RunLog.BOT, null, "", List.of(), 0);
    }

    private static BoundedLog.Entry entry(long k) {
        return new BoundedLog.Entry(record(k), null);
    }

    @Test
    @DisplayName("within capacity: every record, oldest first")
    void within_capacity() {
        BoundedLog log = new BoundedLog(3);
        log.add(record(1), null);
        log.add(record(2), null);
        assertEquals(List.of(entry(1), entry(2)), log.asList());
    }

    @Test
    @DisplayName("past capacity: the oldest drops, so the log is never more than the capacity")
    void past_capacity_drops_the_oldest() {
        BoundedLog log = new BoundedLog(3);
        for (long k = 1; k <= 5; k++) {
            log.add(record(k), null);
        }
        assertEquals(List.of(entry(3), entry(4), entry(5)), log.asList(), "1 and 2 dropped, newest (5) last");
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
        log.add(record(1), null);
        List<BoundedLog.Entry> first = log.asList();
        log.add(record(2), null);
        assertEquals(1, first.size(), "the earlier copy is unaffected by a later add");
    }

    @Test
    @DisplayName("the context added beside a record reaches asList() unchanged, the same object")
    void context_reaches_as_list_unchanged() {
        BoundedLog log = new BoundedLog(2);
        ActionContext context = new ActionContext(5, 10, List.of(), List.of());
        log.add(record(1), context);
        assertSame(context, log.asList().get(0).context());
    }
}
