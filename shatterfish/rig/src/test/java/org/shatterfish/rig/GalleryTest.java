package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.HeroClass;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death gallery (story 3.12).
 *
 * <p>The logs are written by hand, as {@code ComparisonTest}'s are, so each case holds exactly the
 * endings it is about -- including the ones that are not endings: no log, a log with no ending, a
 * log nobody can read. The snapshot is taken of the committed reference Run, whose waits are real.
 */
class GalleryTest {

    private static final String ZERO = "0".repeat(64);

    /** One log in {@code folder} for seed {@code seed}, ending as {@code outcome} (none when null). */
    private static String log(Path folder, long seed, RunLog.Outcome outcome) throws IOException {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0,
                seed, SeedSet.code(seed), 100L + seed, 20_000, 3, 2, 8,
                new RunLog.Brain("random", "abc1234", ZERO), "", false, "a laptop",
                "2026-09-23T00:00:00Z");
        StringBuilder text = new StringBuilder(RunLogJson.line("", header)).append('\n');
        if (outcome != null) {
            text.append(RunLogJson.line(RunLogJson.chain("", header), new RunLog.End(0, outcome, true)))
                    .append('\n');
        }
        String file = RunLog.fileName(header.runId());
        Files.writeString(folder.resolve(file), text.toString(), StandardCharsets.UTF_8);
        return indexLine(header.runId(), file);
    }

    private static String indexLine(String runId, String file) {
        return "{\"class\":\"WARRIOR\",\"log\":\"" + file + "\",\"runId\":\"" + runId + "\"}";
    }

    private static RunLog.Outcome ended(String cause, int depth, long turns) {
        return new RunLog.Outcome(false, false, 0, depth, turns, cause, 0);
    }

    /** Seven Runs: two deaths on 1, one on 2, a lost window, no log, no ending, and garbage. */
    private static Path folder(Path folder) throws IOException {
        List<String> index = new ArrayList<>();
        index.add(log(folder, 3000, ended("DEATH", 1, 1_384_000)));
        index.add(log(folder, 1000, ended("DEATH", 1, 1_100_500)));
        index.add(log(folder, 2000, ended("DEATH", 2, 1_500_000)));
        index.add(log(folder, 4000, ended("UNKNOWN_WINDOW", 1, 900_000)));
        index.add(indexLine("v4.0.0-WARRIOR-0-NOT-HER-EEE-00000000000000aa-random",
                "v4.0.0-WARRIOR-0-NOT-HER-EEE-00000000000000aa-random.jsonl"));
        index.add(log(folder, 5000, null));
        Files.writeString(folder.resolve("garbage.jsonl"), "not json at all\n", StandardCharsets.UTF_8);
        index.add(indexLine("garbage", "garbage.jsonl"));
        Files.write(folder.resolve(RunIndex.RUNS), index, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve(RunIndex.SUMMARY), "{\"brain\":\"random\",\"seedSet\":\"smoke\"}\n",
                StandardCharsets.UTF_8);
        return folder;
    }

    @Test
    @DisplayName("Runs are grouped by ending and depth, largest first, and every Run in the index is in a group")
    void grouped(@TempDir Path out) throws IOException {
        List<Gallery.Group> groups = Gallery.of(folder(out));

        assertEquals(7, groups.stream().mapToInt(g -> g.runs().size()).sum(), "none dropped");
        Gallery.Group first = groups.get(0);
        assertEquals("DEATH", first.cause());
        assertEquals(1, first.depth());
        assertEquals(2, first.runs().size());
        assertEquals(List.of(SeedSet.code(1000), SeedSet.code(3000)),
                first.runs().stream().map(Gallery.Run::seedCode).toList(), "by seed code within a group");
        // The rest are singletons, ordered by cause then depth.
        assertEquals(List.of("DEATH", Gallery.NO_ENDING, Gallery.NO_LOG, "UNKNOWN_WINDOW", Gallery.UNREADABLE),
                groups.subList(1, groups.size()).stream().map(Gallery.Group::cause).toList());
        assertEquals(2, groups.get(1).depth());
        assertEquals(-1, groups.stream().filter(g -> g.cause().equals(Gallery.NO_LOG)).findFirst()
                .orElseThrow().depth(), "no ending, no depth");
    }

    @Test
    @DisplayName("the page is Markdown with the counts, the seeds, the turns and a link to each log")
    void the_page(@TempDir Path out) throws IOException {
        Path folder = folder(out);
        Gallery.write(folder, 0);
        String page = Files.readString(folder.resolve(Gallery.FILE), StandardCharsets.UTF_8);

        assertTrue(page.contains("# How the Runs ended: `random` on `smoke`"), page);
        assertTrue(page.contains("7 Runs in 6 groups"), page);
        assertTrue(page.contains("| `DEATH` | 1 | 2 | 28.6% |"), page);
        assertTrue(page.contains("| `" + SeedSet.code(1000) + "` | WARRIOR | 1100.5 | ["), page);
        assertTrue(page.contains("](" + RunLog.fileName(RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0,
                SeedSet.code(1000), 1100L, "random")) + ")"), "a link to the log itself");
        assertTrue(page.contains("E4's half of FR-26"), "the deferral is said on the page");
        assertFalse(page.contains("Last waits"), "no snapshot column unless asked for");
        assertFalse(Files.exists(folder.resolve(Gallery.SNAPSHOTS)));
    }

    @Test
    @DisplayName("on request, every Run with a log gets a snapshot of its last waits, and the page links it")
    void snapshots(@TempDir Path out) throws IOException {
        Path folder = folder(out);
        Gallery.write(folder, 3);
        String page = Files.readString(folder.resolve(Gallery.FILE), StandardCharsets.UTF_8);

        assertTrue(page.contains("| Seed | Class | Turns | Log | Last waits |"), page);
        assertTrue(page.contains("[snapshot](snapshots/"), page);
        try (var files = Files.list(folder.resolve(Gallery.SNAPSHOTS))) {
            assertEquals(6, files.count(), "every Run but the one with no log");
        }

        // Rewritten after the index shrank: last time's snapshots do not linger beside this time's.
        List<String> index = Files.readAllLines(folder.resolve(RunIndex.RUNS), StandardCharsets.UTF_8);
        Files.write(folder.resolve(RunIndex.RUNS), index.subList(0, 2), StandardCharsets.UTF_8);
        Gallery.write(folder, 3);
        try (var files = Files.list(folder.resolve(Gallery.SNAPSHOTS))) {
            assertEquals(2, files.count());
        }
    }

    @Test
    @DisplayName("a log with a header and then a line nobody can read is UNREADABLE, not grouped by what came before")
    void unreadable_after_a_header(@TempDir Path out) throws IOException {
        String line = log(out, 8000, null);
        Path file = out.resolve(LogHeader.string(line, "log"));
        Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8) + "{\"t\":\"nonsense\"}\n",
                StandardCharsets.UTF_8);
        Files.writeString(out.resolve(RunIndex.RUNS), line + "\n", StandardCharsets.UTF_8);

        List<Gallery.Group> groups = Gallery.of(out);

        assertEquals(Gallery.UNREADABLE, groups.get(0).cause());
    }

    @Test
    @DisplayName("a snapshot is named for its log, so an index line cannot write outside the folder")
    void snapshot_names(@TempDir Path root) throws IOException {
        // Two levels down, so that "../../" from snapshots/ lands inside this test's own folder,
        // where the assertion can see it, rather than in a temp directory other tests share.
        Path out = Files.createDirectories(root.resolve("a").resolve("b"));
        String line = log(out, 7000, ended("DEATH", 1, 1000));
        String hostile = line.replaceFirst("\"runId\":\"[^\"]*\"", "\"runId\":\"../../escaped\"");
        Files.writeString(out.resolve(RunIndex.RUNS), hostile + "\n", StandardCharsets.UTF_8);

        Gallery.write(out, 2);

        assertFalse(Files.exists(root.resolve("a").resolve("escaped.md")));
        try (var files = Files.list(out.resolve(Gallery.SNAPSHOTS))) {
            assertEquals(List.of(RunLog.fileName(RunLog.runId("v4.0.0", HeroClass.WARRIOR, 0,
                    SeedSet.code(7000), 7100L, "random")).replace(".jsonl", ".md")),
                    files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    @DisplayName("a snapshot is the last N waits of the Run's own log, and how it ended")
    void a_snapshot_of_the_reference_run() {
        Path reference = SeedSetsTest.ROOT.resolve("reference").resolve(Reference.fileName());

        String three = Gallery.snapshot(reference, 3);

        assertTrue(three.contains("The last 3 of 12 waits"), three);
        assertTrue(three.contains("it ended `TURN_CAP` at depth 1 after 446.0 turns"), three);
        assertTrue(three.contains("| 12 | 11.0 | 1 | bot | `Rest[full=true]` |"), three);
        assertEquals(3, three.lines().filter(l -> l.startsWith("| 1")).count(), three);
        assertTrue(Gallery.snapshot(reference, 100).contains("The last 12 of 12 waits"));
        assertThrows(IllegalArgumentException.class, () -> Gallery.snapshot(reference, 0));
    }

    @Test
    @DisplayName("an index line without a run id, or naming a log outside the folder, is refused")
    void refusals(@TempDir Path out) throws IOException {
        Files.writeString(out.resolve(RunIndex.RUNS), "{\"log\":\"x.jsonl\"}\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Gallery.of(out));
        Files.writeString(out.resolve(RunIndex.RUNS), "{\"log\":\"../x.jsonl\",\"runId\":\"x\"}\n",
                StandardCharsets.UTF_8);
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class, () -> Gallery.of(out));
        assertTrue(outside.getMessage().contains("outside"), outside.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Gallery.main(new String[] {"x", "--snapshots"}));
        assertThrows(IllegalArgumentException.class,
                () -> Gallery.main(new String[] {out.toString(), "--snapshots", "0"}));
    }
}
