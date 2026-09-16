package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reserved interfaces of ADR-0009 hold their contracts with no implementation behind them: a
 * simulator refuses a handle that is not scrubbed before its rollout runs, a redeterminer's result
 * must be scrubbed, and a handle and a sample are values with no state to inflate.
 */
class ReservedInterfacesTest {

    @Test
    @DisplayName("a simulator refuses an unscrubbed handle before any rollout runs")
    void a_simulator_refuses_an_unscrubbed_handle() {
        AtomicInteger rollouts = new AtomicInteger();
        Simulator simulator = new Simulator() {
            @Override
            protected RolloutResult rollout(SnapshotHandle handle, List<Action> actions) {
                rollouts.incrementAndGet();
                return new RolloutResult(List.of(), RolloutEnd.HORIZON);
            }
        };
        SnapshotHandle live = new SnapshotHandle("live-7", 7, false);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> simulator.simulate(live, List.of(new Action.Wait())));
        assertTrue(refused.getMessage().contains("live-7") && refused.getMessage().contains("not scrubbed"), refused.getMessage());
        assertEquals(0, rollouts.get(), "the rollout never ran");

        SnapshotHandle scrubbed = new SnapshotHandle("live-7/sample-1", 7, true);
        assertEquals(RolloutEnd.HORIZON, simulator.simulate(scrubbed, List.of()).end());
        assertEquals(1, rollouts.get());
    }

    @Test
    @DisplayName("a redeterminer's result must be scrubbed, or the interface refuses it")
    void a_redeterminer_must_scrub() {
        SnapshotHandle live = new SnapshotHandle("live-3", 3, false);
        BeliefSample sample = new BeliefSample(1, new byte[] {1, 2, 3});
        Redeterminer forgetful = (handle, s) -> new SnapshotHandle(handle.id() + "/copy", handle.k(), false);
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> forgetful.scrub(live, sample));
        assertTrue(refused.getMessage().contains("unscrubbed"), refused.getMessage());
        Redeterminer empty = (handle, s) -> null;
        assertThrows(IllegalStateException.class, () -> empty.scrub(live, sample));
        Redeterminer proper = (handle, s) -> new SnapshotHandle(handle.id() + "/sample", handle.k(), true);
        assertTrue(proper.scrub(live, sample).scrubbed());
    }

    @Test
    @DisplayName("a handle is an id, a wait and a flag; a sample is a version and bytes; nothing else")
    void the_values_hold_nothing_to_inflate() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotHandle("", 1, false));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotHandle("x", 0, false));
        assertEquals(3, SnapshotHandle.class.getRecordComponents().length);
        BeliefSample a = new BeliefSample(2, new byte[] {9});
        BeliefSample b = new BeliefSample(2, new byte[] {9});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new BeliefSample(3, new byte[] {9}));
        byte[] bytes = a.bytes();
        bytes[0] = 0;
        assertEquals(9, a.bytes()[0], "the bytes are copied out");
        assertThrows(IllegalArgumentException.class, () -> new BeliefSample(0, new byte[0]));
        RolloutResult result = new RolloutResult(List.of(), RolloutEnd.DEATH);
        assertEquals(0, result.observations().size());
        assertThrows(UnsupportedOperationException.class, () -> result.observations().add(null));
    }
}
