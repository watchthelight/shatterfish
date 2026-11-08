package org.shatterfish.rig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Story 10.11: the theft rows follow every direction table, the all-class one included. */
class DirectionHarmSectionTest {

    @Test
    @DisplayName("with a baseline, the section carries the theft rows whatever the class selection was")
    void theft_rows_print(@TempDir Path base, @TempDir Path now) {
        String section = Direction.harmSection(base, now);
        assertTrue(section.contains("items stolen by a thief or bandit"), section);
        assertTrue(section.contains("items a thief got away with (lost for good)"), section);
        assertTrue(section.contains("items kept or got back"), section);
    }

    @Test
    @DisplayName("without a baseline there is nothing to compare, so no rows")
    void no_baseline_no_rows(@TempDir Path now) {
        assertEquals("", Direction.harmSection(null, now));
    }
}
