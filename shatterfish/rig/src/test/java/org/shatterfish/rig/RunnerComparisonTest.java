package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Brain compared with itself, which is how pairing is checked (story 3.6).
 *
 * <p>The random Brain's Decider is seeded from the triple, and a pair shares the salt, so the two
 * Runs of every pair are the same Run: same dungeon, same rolls, same choices -- and therefore the
 * same chain. That is what this checks, per pair, from what each child actually wrote.
 *
 * <p>The first version of this test asserted that every pair tied and that the two indexes agreed on
 * the salts. Both passed for the wrong reason: at a cap of sixty turns nearly every random Run ends
 * at the cap with the same outcome whatever its salt, and the indexes' salts are written by the
 * parent from one variable. The chain is what a child cannot agree with by accident.
 */
class RunnerComparisonTest {

    @Test
    @DisplayName("random against random plays the same Run twice per pair: one salt, one chain, a tie")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_brain_is_the_same_run_twice(@TempDir Path out) throws IOException {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.RANDOM);
        arguments.put(Runner.AGAINST, Brains.RANDOM);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.resolve("rig").toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());

        Path folder = Runner.run(arguments);

        String json = Files.readString(folder.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("false", LogHeader.value(json, "tested"),
                "unranked, so no bounds were stated and nothing was tested: " + json);

        SeedSet set = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set();
        int died = 0;
        for (SeedSet.Entry triple : set.entries()) {
            Path mine = only(folder.resolve(Comparison.CANDIDATE), triple);
            Path theirs = only(folder.resolve(Comparison.BASELINE), triple);
            RunLogReader.Log a = RunLogReader.of(mine);
            RunLogReader.Log b = RunLogReader.of(theirs);
            // The salt each child ran under, from its own header -- not from the parent's index.
            assertEquals(a.header().salt(), b.header().salt(), "one salt per pair: " + triple);
            // The same Run: the chain covers every Observation, Action and ending.
            assertEquals(LogHeader.of(mine).chain(), LogHeader.of(theirs).chain(),
                    "both halves of the pair are the same Run: " + triple);
            died += "DEATH".equals(a.end().outcome().cause()) ? 1 : 0;
        }
        assertTrue(died > 0, "at least some pairs reached an ending the game decided, so the ties"
                + " below are scored ties and not missing ones");
        assertEquals(String.valueOf(set.entries().size() - died), LogHeader.value(json, "missing"),
                "a Run the game did not end is counted as missing");

        // Each side's summary counts its own waits. The two sides played the same Runs, so their
        // counts are equal; one counter shared by both gave one side all the waits and the other none.
        String mineSummary = Files.readString(folder.resolve(Comparison.CANDIDATE)
                .resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        String theirSummary = Files.readString(folder.resolve(Comparison.BASELINE)
                .resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertTrue(Long.parseLong(LogHeader.value(mineSummary, "waits")) > 0, mineSummary);
        assertEquals(LogHeader.value(mineSummary, "waits"), LogHeader.value(theirSummary, "waits"));

        // The Runner writes each side's death gallery when the invocation completes (story 3.12),
        // and every Run of the side is in it.
        for (String side : List.of(Comparison.CANDIDATE, Comparison.BASELINE)) {
            List<Gallery.Group> groups = Gallery.of(folder.resolve(side));
            assertEquals(set.entries().size(), groups.stream().mapToInt(g -> g.runs().size()).sum(), side);
            assertTrue(Files.isRegularFile(folder.resolve(side).resolve(Gallery.FILE)), side);
        }

        // And the folder verifies as a whole: both sides' logs, and the comparison's record of each
        // side's index against the index there now.
        Verify.Report verified = Verify.of(folder);
        assertTrue(verified.ok(true), verified.text());
        assertEquals(2 * set.entries().size(), verified.checked().size());
        Path index = folder.resolve(Comparison.BASELINE).resolve(RunIndex.RUNS);
        Files.writeString(index, Files.readString(index, StandardCharsets.UTF_8) + "\n",
                StandardCharsets.UTF_8);
        Verify.Report tampered = Verify.of(folder);
        assertTrue(!tampered.ok(true) && tampered.why().contains("baseline index"), tampered.text());
    }

    @Test
    @DisplayName("--against with --verify or --replay is refused, not ignored")
    void against_is_a_run_flag() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.main(new String[] {Runner.VERIFY, "x", Runner.AGAINST, Brains.RANDOM}));
        assertTrue(refused.getMessage().contains("refused, not ignored"), refused.getMessage());
    }

    /** The one log in {@code side} for {@code triple}. */
    private static Path only(Path side, SeedSet.Entry triple) throws IOException {
        try (var files = Files.list(side)) {
            var found = files.filter(f -> f.getFileName().toString().endsWith(".jsonl"))
                    .filter(f -> !f.getFileName().toString().equals(RunIndex.RUNS))
                    .filter(f -> f.getFileName().toString().contains("-" + triple.seedCode() + "-"))
                    .filter(f -> f.getFileName().toString().contains("-" + triple.heroClass().name() + "-"))
                    .toList();
            assertEquals(1, found.size(), side + " holds one log for " + triple + ": " + found);
            RunLog.Header header = RunLogReader.of(found.get(0)).header();
            assertEquals(triple.seed(), header.seed());
            return found.get(0);
        }
    }
}
