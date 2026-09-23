package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The weights of an Evaluation as an {@code api} value (story 4.5). */
class WeightsTest {

    @Test
    @DisplayName("terms are sorted by feature, named once, and written canonically")
    void canonical() {
        Weights weights = new Weights("shatterfish", 3,
                List.of(new Weights.Term("hp", 2), new Weights.Term("depth", -5)));
        assertEquals(List.of("depth", "hp"), weights.features());
        assertEquals(-5, weights.weight("depth"));
        assertEquals("{\"format\":1,\"name\":\"shatterfish\",\"terms\":{\"depth\":-5,\"hp\":2},\"version\":3}",
                weights.canonical());
        assertThrows(IllegalArgumentException.class, () -> weights.weight("luck"));
    }

    @Test
    @DisplayName("a repeated feature, a bad name or a version below one is refused")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> new Weights("shatterfish", 1,
                List.of(new Weights.Term("hp", 1), new Weights.Term("hp", 2))));
        assertThrows(IllegalArgumentException.class, () -> new Weights("shatterfish", 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Weights("Shatter Fish", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Weights.Term("Hit Points", 1));
        new Weights.Term("hp", Weights.MAX_WEIGHT);
        new Weights.Term("hp", -Weights.MAX_WEIGHT);
        assertThrows(IllegalArgumentException.class, () -> new Weights.Term("hp", Weights.MAX_WEIGHT + 1),
                "a weight is bounded, so no score overflows");
        assertThrows(IllegalArgumentException.class, () -> new Weights.Term("hp", -Weights.MAX_WEIGHT - 1));
    }
}
