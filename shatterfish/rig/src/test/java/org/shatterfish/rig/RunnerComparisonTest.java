package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Brain compared with itself, which is how pairing is checked (story 3.6).
 *
 * <p>The random Brain's Decider is seeded from the triple, and a pair shares the salt, so the two
 * Runs of every pair are the same Run: same dungeon, same rolls, same choices. Every pair must
 * therefore tie. A pair that did not tie would be a pair whose two halves saw different salts, or a
 * Brain whose choices leaked something other than the triple -- either of which is a comparison
 * that measures noise and calls it a result.
 */
class RunnerComparisonTest {

    @Test
    @DisplayName("random against random ties every pair, because a pair shares its salt")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_brain_ties_itself(@TempDir Path out) throws IOException {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.RANDOM);
        arguments.put(Runner.AGAINST, Brains.RANDOM);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.resolve("rig").toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        arguments.put(Runner.CAP, "60");

        Path folder = Runner.run(arguments);

        String json = Files.readString(folder.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("25", LogHeader.value(json, "pairs"), json);
        assertEquals("25", LogHeader.value(json, "equal"), "every pair tied: " + json);
        assertEquals("0", LogHeader.value(json, "missing"), json);
        assertEquals("false", LogHeader.value(json, "tested"),
                "unranked, so no bounds were stated and nothing was tested: " + json);

        // The salts, read back out of the two sides' indexes: one per triple, the same on both sides.
        assertEquals(salts(folder.resolve(Comparison.CANDIDATE)), salts(folder.resolve(Comparison.BASELINE)),
                "both halves of every pair ran under one salt");
        assertEquals(25, salts(folder.resolve(Comparison.CANDIDATE)).size());
    }

    private static List<String> salts(Path side) throws IOException {
        List<String> salts = new ArrayList<>();
        for (String line : Files.readAllLines(side.resolve(RunIndex.RUNS), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                salts.add(LogHeader.string(line, "salt"));
            }
        }
        assertTrue(salts.stream().distinct().count() == salts.size(), "a fresh salt per triple");
        return salts;
    }
}
