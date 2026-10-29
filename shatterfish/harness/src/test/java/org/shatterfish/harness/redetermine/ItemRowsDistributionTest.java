package org.shatterfish.harness.redetermine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unidentified-item row must not tell a rollout anything about the truth (fairness review of story 10.24, ADR-0025).
 * The game assigns labels to the classes of a family uniformly at random, so given what the hero has seen (which labels
 * go with the classes still unidentified) every bijection between them is equally likely. A sample that draws the
 * permutation uniformly, identity included, gives the scrubbed Run an assignment with the same distribution whatever the
 * truth is; a sample that excludes the identity gives one that is biased away from it. Fixed counts over many draws, so
 * the test does not wobble.
 */
class ItemRowsDistributionTest {

    private static final int DRAWS = 12_000;

    private static Map<String, Integer> counts(int n) {
        Map<String, Integer> counts = new HashMap<>();
        for (long draw = 1; draw <= DRAWS; draw++) {
            int[] perm = ItemRows.permutation(n, new Streams(draw * 0x9E3779B97F4A7C15L), "potion");
            counts.merge(Arrays.toString(perm), 1, Integer::sum);
        }
        return counts;
    }

    private static double chiSquare(Map<String, Integer> counts, int cells) {
        double expected = (double) DRAWS / cells;
        double sum = 0;
        for (int c : counts.values()) {
            sum += (c - expected) * (c - expected) / expected;
        }
        // Cells never drawn count their whole expectation.
        sum += (cells - counts.size()) * expected;
        return sum;
    }

    @Test
    @DisplayName("three unidentified classes: all six assignments, identity included, are equally likely")
    void three_classes_uniform() {
        Map<String, Integer> counts = counts(3);
        assertTrue(counts.containsKey("[0, 1, 2]"), "the identity is a possible draw: " + counts);
        // df = 5; 20.5 is the 0.001 point of the chi-square distribution.
        assertTrue(chiSquare(counts, 6) < 20.5, "chi-square over six assignments: " + chiSquare(counts, 6) + " " + counts);
    }

    @Test
    @DisplayName("two unidentified classes: the swap and the identity are equally likely")
    void two_classes_uniform() {
        Map<String, Integer> counts = counts(2);
        assertEquals(2, counts.size(), String.valueOf(counts));
        // df = 1; 10.83 is the 0.001 point.
        assertTrue(chiSquare(counts, 2) < 10.83, "chi-square over two assignments: " + chiSquare(counts, 2) + " " + counts);
    }
}
