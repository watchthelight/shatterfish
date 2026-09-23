package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Results pages (story 3.10).
 *
 * <p>The committed pages are the claim, so the first test regenerates every one from its committed
 * data folder and requires the same bytes. The rest hold what a fresh render would agree with
 * whatever it did: the extraction from a folder the Rig wrote, what it refuses, and the FR-25 fields a
 * page has to carry.
 */
class ResultsTest {

    private static final String ZERO = "0".repeat(64);

    @Test
    @DisplayName("every committed Results page is a fresh render of its committed data folder")
    void the_pages_are_generated() throws IOException {
        List<Path> folders = Results.generated(SeedSetsTest.ROOT);
        assertTrue(folders.size() >= 6, "story 3.9's five and H-0007 at least: " + folders);
        for (Path data : folders) {
            String title = Files.readString(data.resolve("title.txt"), StandardCharsets.UTF_8).strip();
            String page = Files.readString(SeedSetsTest.ROOT.resolve(Results.PAGES)
                    .resolve(data.getFileName() + ".md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(page, Results.page(data, title), "regenerate with " + Results.COMMAND + ": " + data);
        }
    }

    @Test
    @DisplayName("a page carries every field FR-25 names")
    void every_field() throws IOException {
        Path data = SeedSetsTest.ROOT.resolve(Results.FOLDER).resolve("2026-09-23-H-0005-2");
        String page = Results.page(data, "x");
        for (String field : List.of("Upstream tag", "Shatterfish commit", "Seed set", "Brains",
                "Registration", "committed at", "Prior registered attempts", "Bounds", "natural log units",
                "The trace", "Per-Run aggregates", "Pair correlation", "Survival curve", "Boss staircase",
                "Run logs", "Fairness suite", "Oracle | off", "**Command**", "By hero class")) {
            assertTrue(page.contains(field), "the page lacks " + field);
        }
        // The prior attempts are the ledger's: the second invocation of H-0005 had one before it.
        assertTrue(page.contains("| Prior registered attempts | 1 "), page);
    }

    @Test
    @DisplayName("an undecided result is published on the same terms")
    void undecided_is_published() throws IOException {
        String page = Files.readString(SeedSetsTest.ROOT.resolve(Results.PAGES).resolve("2026-09-23-H-0007.md"),
                StandardCharsets.UTF_8);
        assertTrue(page.contains("**UNDECIDED**"), page);
        assertTrue(page.contains("Registration | `H-0007-twin-undecided@"), page);
    }

    // --------------------------------------------------------------------------- extraction

    /** A Baseline folder for the first {@code n} triples of smoke; returns nothing, writes the logs. */
    private static void folder(Path runs, int n, String commit, boolean oracle) throws IOException {
        SeedSet set = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set();
        List<String> index = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SeedSet.Entry triple = set.entries().get(i);
            RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", i == 1 ? commit : "abc1234",
                    triple.heroClass(), triple.challengeFlags(), triple.seed(), triple.seedCode(), 100L + i,
                    20_000, 3, 2, 8, new RunLog.Brain("random", "abc1234", ZERO), "", oracle && i == 2,
                    "a laptop", "2026-09-23T00:00:0" + i + "Z");
            RunLog.End end = new RunLog.End(0, new RunLog.Outcome(false, false, 5, 1 + i % 2,
                    1000L * (100 + i), "DEATH", i == 0 ? 1 : 0), true);
            String text = RunLogJson.line("", header) + "\n"
                    + RunLogJson.line(RunLogJson.chain("", header), end) + "\n";
            String file = RunLog.fileName(header.runId());
            Files.writeString(runs.resolve(file), text, StandardCharsets.UTF_8);
            index.add("{\"chain\":\"" + LogHeader.of(runs.resolve(file)).chain() + "\",\"class\":\""
                    + triple.heroClass().name() + "\",\"log\":\"" + file + "\",\"runId\":\"" + header.runId()
                    + "\",\"seed\":" + triple.seed() + "}");
        }
        Files.write(runs.resolve(RunIndex.RUNS), index, StandardCharsets.UTF_8);
        Files.writeString(runs.resolve(RunIndex.SUMMARY), "{\"seedSet\":\"smoke\"}\n", StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a Baseline folder becomes a data folder and a page, from each Run's own log")
    void a_baseline(@TempDir Path runs, @TempDir Path into) throws IOException {
        folder(runs, 3, "abc1234", false);

        Results.extract(runs, SeedSetsTest.ROOT, into);
        String page = Results.page(into, "A test Baseline");

        String description = Files.readString(into.resolve(Results.DESCRIPTION), StandardCharsets.UTF_8).strip();
        assertEquals("baseline", LogHeader.string(description, "kind"));
        assertEquals("abc1234", LogHeader.string(description, "commit"));
        assertEquals("3", LogHeader.value(description, "runs"));
        assertEquals("2026-09-23T00:00:00Z", LogHeader.string(description, "started"), "the earliest start");
        List<String> outcomes = Files.readAllLines(into.resolve(Results.OUTCOMES), StandardCharsets.UTF_8);
        assertEquals(3, outcomes.size());
        assertEquals("100000", LogHeader.value(outcomes.get(0), "turns"), "from the log, not the index");
        assertTrue(page.contains("| Ended `DEATH` | 3 |"), page);
        assertTrue(page.contains("| Deepest floor 2 | 1 |"), page);
        assertTrue(page.contains("| 1 | 1 |"), "one Run killed a boss: " + page);
        assertTrue(page.contains("Registration | none: not a measurement"), page);
        assertTrue(page.contains("| 100 | 100.0% |"), page);
    }

    @Test
    @DisplayName("Runs of two invocations, and a Run with the Oracle on, are refused")
    void refusals(@TempDir Path mixed, @TempDir Path oracle, @TempDir Path into) throws IOException {
        folder(mixed, 3, "def5678", false);
        IllegalArgumentException twoCommits = assertThrows(IllegalArgumentException.class,
                () -> Results.extract(mixed, SeedSetsTest.ROOT, into));
        assertTrue(twoCommits.getMessage().contains("mixes invocations"), twoCommits.getMessage());

        folder(oracle, 3, "abc1234", true);
        IllegalArgumentException withOracle = assertThrows(IllegalArgumentException.class,
                () -> Results.extract(oracle, SeedSetsTest.ROOT, into));
        assertTrue(withOracle.getMessage().contains("Oracle"), withOracle.getMessage());
    }

    @Test
    @DisplayName("a JSON array's elements, objects and strings with commas in them included")
    void strings() {
        assertEquals(List.of(), Results.strings("[]"));
        assertEquals(List.of("1", "-2", "3"), Results.strings("[1,-2,3]"));
        assertEquals(List.of("a,b", "c"), Results.strings("[\"a,b\",\"c\"]"));
        assertEquals(List.of("{\"x\":[1,2]}", "{\"y\":3}"), Results.strings("[{\"x\":[1,2]},{\"y\":3}]"));
    }
}
