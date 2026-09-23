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
                "Registration", "[committed](", "Claim", "Error rates", "Prior registered attempts", "Bounds", "natural log units",
                "The trace", "Per-Run aggregates", "Pair correlation", "Survival curve", "Boss staircase",
                "Run logs", "Fairness suite", "Oracle | off", "**Command**", "By hero class")) {
            assertTrue(page.contains(field), "the page lacks " + field);
        }
        // The prior attempts are the ledger's: the second invocation of H-0005 had one before it.
        assertTrue(page.contains("| Prior registered attempts | 1 of this Registration"), page);
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
        folder(runs, n, i -> header(i, "random", "", "v4.0.0", 20_000, i == 1 ? commit : "abc1234",
                oracle && i == 2, "2026-09-23T00:00:0" + (9 - i) + "Z"));
    }

    private static RunLog.Header header(int i, String brain, String stamp, String tag, int cap, String commit,
                                        boolean oracle, String started) {
        SeedSet.Entry triple = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().get(i);
        return new RunLog.Header(RunLog.VERSION, tag, commit, triple.heroClass(), triple.challengeFlags(),
                triple.seed(), triple.seedCode(), 100L + i, cap, 3, 2, 8, new RunLog.Brain(brain, "abc1234", ZERO),
                stamp, oracle, "a laptop", started);
    }

    /** A folder of {@code n} Runs whose headers {@code headers} makes, one per smoke triple. */
    private static void folder(Path runs, int n, java.util.function.IntFunction<RunLog.Header> headers)
            throws IOException {
        Files.createDirectories(runs);
        SeedSet set = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set();
        List<String> index = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SeedSet.Entry triple = set.entries().get(i);
            RunLog.Header header = headers.apply(i);
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
        assertEquals("2026-09-23T00:00:07Z", LogHeader.string(description, "started"),
                "the earliest start, which is the last header's, not the first's");
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
    @DisplayName("Runs that disagree on the tag, the cap or the Registration are two invocations, and refused")
    void every_disagreement(@TempDir Path tags, @TempDir Path caps, @TempDir Path stamps, @TempDir Path into)
            throws IOException {
        folder(tags, 3, i -> header(i, "random", "", i == 1 ? "v3.3.8" : "v4.0.0", 20_000, "abc1234", false,
                "2026-09-23T00:00:00Z"));
        folder(caps, 3, i -> header(i, "random", "", "v4.0.0", i == 1 ? 400 : 20_000, "abc1234", false,
                "2026-09-23T00:00:00Z"));
        folder(stamps, 3, i -> header(i, "random", i == 1 ? "H-0001-nightly-smoke@0123456789abcdef" : "",
                "v4.0.0", 20_000, "abc1234", false, "2026-09-23T00:00:00Z"));
        for (Path runs : List.of(tags, caps, stamps)) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Results.extract(runs, SeedSetsTest.ROOT, into));
            assertTrue(refused.getMessage().contains("mixes invocations"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("a registered comparison: both sides, the Registration's Brains and commit, and the prior attempts the ledger holds")
    void a_registered_comparison(@TempDir Path root, @TempDir Path runs, @TempDir Path into) throws IOException {
        org.shatterfish.api.Registration.Brain random = new org.shatterfish.api.Registration.Brain(
                "random", "aaaaaaa", ZERO);
        org.shatterfish.api.Registration.Brain worse = new org.shatterfish.api.Registration.Brain(
                "random_norest", "bbbbbbb", ZERO);
        org.shatterfish.api.Registration mine = new org.shatterfish.api.Registration("H-0300-mine", "a claim",
                random, worse, SeedSets.SMOKE, 1, 50, 50, 20, 25, 0, "a laptop", false, 500, 600, 250);
        org.shatterfish.api.Registration other = new org.shatterfish.api.Registration("H-0301-other", "another",
                random, worse, SeedSets.SMOKE, 1, 50, 50, 20, 25, 0, "a laptop", false, 500, 600, 250);
        org.shatterfish.api.Registration elsewhere = new org.shatterfish.api.Registration("H-0302-else", "else",
                worse, new org.shatterfish.api.Registration.Brain("random", "ccccccc", ZERO), SeedSets.SMOKE, 1,
                50, 50, 20, 25, 0, "a laptop", false, 500, 600, 250);
        RunnerRegistrationTest.repository(root, mine, other, elsewhere);
        Registrations.Committed committed = Registrations.read(root, "H-0300-mine");
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));
        ledger.record(committed, "random_norest", "bbbbbbb", ZERO, SeedSets.SMOKE, Ledger.Outcome.FINISHED, false, "one");
        ledger.record(committed, "random_norest", "bbbbbbb", ZERO, SeedSets.SMOKE, Ledger.Outcome.FINISHED, false, "two");
        ledger.record(Registrations.read(root, "H-0301-other"), "random_norest", "bbbbbbb", ZERO, SeedSets.SMOKE,
                Ledger.Outcome.FINISHED, false, "a sibling");
        // Another candidate Brain on the same set: not a sibling of this claim.
        ledger.record(Registrations.read(root, "H-0302-else"), "random", "ccccccc", ZERO, SeedSets.SMOKE,
                Ledger.Outcome.FINISHED, false, "not a sibling");
        for (String side : List.of(Comparison.CANDIDATE, Comparison.BASELINE)) {
            String brain = side.equals(Comparison.CANDIDATE) ? "random_norest" : "random";
            // Started long after the three ledger lines, which are therefore all prior.
            folder(runs.resolve(side), 3, i -> header(i, brain, committed.stamp(), "v4.0.0", 20_000, "abc1234",
                    false, "2099-01-01T00:00:00Z"));
        }
        Files.writeString(runs.resolve(Comparison.FILE), "{\"baseline\":\"random\",\"candidate\":\"random_norest\","
                + "\"registration\":\"" + committed.stamp() + "\",\"tested\":false}\n", StandardCharsets.UTF_8);

        Results.extract(runs, root, into);

        String d = Files.readString(into.resolve(Results.DESCRIPTION), StandardCharsets.UTF_8).strip();
        assertEquals("comparison", LogHeader.string(d, "kind"));
        assertEquals("2", LogHeader.value(d, "prior_attempts"), "this Registration's two earlier invocations");
        assertEquals("1", LogHeader.value(d, "prior_sibling_attempts"), "and the sibling's one");
        assertEquals(committed.at(), LogHeader.string(d, "registration_commit"), "the commit that added it");
        assertEquals(List.of("random@aaaaaaa/" + ZERO, "random_norest@bbbbbbb/" + ZERO),
                Results.strings(LogHeader.value(d, "registered_brains")));
        assertTrue(LogHeader.string(d, "command").contains("--brain random_norest --against random"), d);
        assertTrue(Files.isRegularFile(into.resolve(Comparison.CANDIDATE).resolve(Results.OUTCOMES)));
        assertTrue(Files.isRegularFile(into.resolve(Comparison.BASELINE).resolve(Results.OUTCOMES)));
        assertTrue(Files.isRegularFile(into.resolve(Comparison.FILE)));

        // Started before any of those ledger lines were written: none of them is prior.
        for (String side : List.of(Comparison.CANDIDATE, Comparison.BASELINE)) {
            String brain = side.equals(Comparison.CANDIDATE) ? "random_norest" : "random";
            folder(runs.resolve(side), 3, i -> header(i, brain, committed.stamp(), "v4.0.0", 20_000, "abc1234",
                    false, "2000-01-01T00:00:00Z"));
        }
        Results.extract(runs, root, into);
        String early = Files.readString(into.resolve(Results.DESCRIPTION), StandardCharsets.UTF_8).strip();
        assertEquals("0", LogHeader.value(early, "prior_attempts"), early);
        assertEquals("0", LogHeader.value(early, "prior_sibling_attempts"), early);

        // And a comparison.json naming another Registration than its Runs is refused.
        Files.writeString(runs.resolve(Comparison.FILE), "{\"baseline\":\"random\",\"candidate\":\"random_norest\","
                + "\"registration\":\"H-0301-other@0123456789abcdef\",\"tested\":false}\n", StandardCharsets.UTF_8);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Results.extract(runs, root, into));
        assertTrue(refused.getMessage().contains("names the Registration"), refused.getMessage());
    }

    @Test
    @DisplayName("an untitled data folder is an error, not a page quietly left out of the drift check")
    void untitled(@TempDir Path root) throws IOException {
        Path data = Files.createDirectories(root.resolve(Results.FOLDER).resolve("x"));
        Files.writeString(data.resolve(Results.DESCRIPTION), "{}\n", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> Results.generated(root));
    }

    @Test
    @DisplayName("a JSON array's elements, objects and strings with commas in them included")
    void strings() {
        assertEquals(List.of(), Results.strings("[]"));
        assertEquals(List.of("1", "-2", "3"), Results.strings("[1,-2,3]"));
        assertEquals(List.of("a,b", "c"), Results.strings("[\"a,b\",\"c\"]"));
        assertEquals(List.of("{\"x\":[1,2]}", "{\"y\":3}"), Results.strings("[{\"x\":[1,2]},{\"y\":3}]"));
        assertEquals(List.of("a\\", "b\",c", "d"), Results.strings("[\"a\\\\\",\"b\\\",c\",\"d\"]"),
                "an escaped backslash ends no string, and an escaped quote starts none");
    }
}
