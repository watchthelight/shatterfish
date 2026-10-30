package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The search plan is data with a canonical text, and the closed-loop rollout keeps the scrub check (story 10.24). */
class SearchPlanTest {

    @Test
    @DisplayName("a plan parses from part of its text, the rest from the reference plan, and round-trips")
    void parse_round_trips() {
        assertEquals(SearchPlan.REFERENCE, SearchPlan.parse(null));
        assertEquals(SearchPlan.REFERENCE, SearchPlan.parse(""));
        SearchPlan wide = SearchPlan.parse("k=8,h=12");
        assertEquals(8, wide.samples());
        assertEquals(12, wide.horizon());
        assertEquals(SearchPlan.REFERENCE.candidates(), wide.candidates());
        assertEquals(wide, SearchPlan.parse(wide.canonical()));
    }

    @Test
    @DisplayName("a plan refuses an unknown key, a repeated key, a non-number and a value out of range")
    void parse_refuses() {
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.parse("z=1"));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.parse("k=1,k=2"));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.parse("k=many"));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.parse("k=0"));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.parse("c=1"));
    }

    @Test
    @DisplayName("a closed-loop rollout is refused for an unscrubbed handle before the playout is asked")
    void closed_loop_keeps_the_scrub_check() {
        AtomicInteger asked = new AtomicInteger();
        Simulator simulator = new Simulator() {
            @Override
            protected RolloutResult rollout(SnapshotHandle handle, java.util.List<Action> actions) {
                return new RolloutResult(java.util.List.of(), 0, RolloutEnd.HORIZON);
            }

            @Override
            protected RolloutResult rollout(SnapshotHandle handle, Playout playout, int horizon) {
                asked.incrementAndGet();
                return new RolloutResult(java.util.List.of(), 0, RolloutEnd.HORIZON);
            }
        };
        Playout nothing = (observation, applied) -> null;
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> simulator.simulate(new SnapshotHandle("live-9", 9, false), nothing, 4));
        assertTrue(refused.getMessage().contains("not scrubbed"), refused.getMessage());
        assertEquals(0, asked.get());
        simulator.simulate(new SnapshotHandle("live-9/s", 9, true), nothing, 4);
        assertEquals(1, asked.get());
    }
}
