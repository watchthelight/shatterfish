package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-Brain comparison view of the death gallery (story 4.13, FR-26's E4 half).
 *
 * <p>The logs are written by hand, as {@code GalleryTest}'s are, each with one Brain wait whose
 * Decision is the situation the Run ended in.
 */
class GalleryComparisonTest {

    private static final String ZERO = "0".repeat(64);

    /** One log of seed {@code seed} for Brain {@code brain}; its one wait's Decision is {@code policy} and {@code flags}. */
    private static Path log(Path folder, String brain, long seed, long salt, String policy, List<String> flags,
                            RunLog.Outcome outcome) throws IOException {
        RunLog.Header header = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0,
                seed, SeedSet.code(seed), salt, 20_000, 3, 2, 8, new RunLog.Brain(brain, "abc1234", ZERO), "",
                false, "a laptop", "2026-09-25T00:00:00Z");
        List<RunLog> records = new ArrayList<>();
        records.add(header);
        RunLog.Decision decision = policy == null ? null : new RunLog.Decision("act",
                new RunLog.Choice(new Action.Wait(), 1, "why"), List.of(), flags, policy);
        records.add(new RunLog.Wait(1, 1_000, outcome == null ? 1 : outcome.depth(), 0, ZERO, Map.of("map", ZERO),
                new Action.Wait(), true, RunLog.BOT, decision, decision == null ? "" : ZERO, List.of(), 1));
        if (outcome != null) {
            records.add(new RunLog.End(1, outcome, true));
        }
        StringBuilder text = new StringBuilder();
        String chain = "";
        for (RunLog record : records) {
            text.append(RunLogJson.line(chain, record)).append('\n');
            chain = RunLogJson.chain(chain, record);
        }
        Path file = folder.resolve(RunLog.fileName(header.runId()));
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static RunLog.Outcome death(int depth, long turns) {
        return new RunLog.Outcome(false, false, 0, depth, turns, "DEATH", 0);
    }

    /** A baseline of three Runs and a candidate of three, two triples in common. */
    private static Path[] sides(Path root) throws IOException {
        Path baseline = Files.createDirectories(root.resolve("baseline"));
        Path candidate = Files.createDirectories(root.resolve("candidate"));
        log(baseline, "old", 1000, 1000, "fight", List.of("hp-low", "enemy-in-view"), death(2, 500_000));
        log(baseline, "old", 2000, 1001, "explore", List.of("starving"), death(3, 900_000));
        log(baseline, "old", 3000, 1002, "fight", List.of("enemy-in-view", "hp-low"), death(1, 100_000));
        log(candidate, "new", 1000, 1000, "fight", List.of("enemy-in-view", "hp-low"), death(4, 1_500_000));
        log(candidate, "new", 2000, 1001, "descend", List.of(), death(2, 400_000));
        log(candidate, "new", 4000, 1003, null, List.of(), new RunLog.Outcome(false, false, 0, 1, 50_000,
                "UNKNOWN_WINDOW", 0));
        return new Path[] {baseline, candidate};
    }

    @Test
    @DisplayName("a folder without a run index is every log in it, keyed by triple and salt, with the last wait's situation")
    void keyed(@TempDir Path root) throws IOException {
        Map<String, GalleryComparison.Ending> read = GalleryComparison.of(sides(root)[0]);

        assertEquals(3, read.size());
        GalleryComparison.Ending first = read.values().stream().filter(e -> e.seedCode().equals(SeedSet.code(1000)))
                .findFirst().orElseThrow();
        assertEquals("fight: enemy-in-view, hp-low", first.situation(), "the Policy, then the flags sorted");
        assertEquals(2, first.depth());
        assertEquals("DEATH", first.cause());
        assertTrue(first.key().startsWith(SeedSet.code(1000) + " WARRIOR 0 "), first.key());
    }

    @Test
    @DisplayName("the page: endings side by side, deaths by situation, every shared triple worst change first")
    void the_page(@TempDir Path root) throws IOException {
        Path[] sides = sides(root);
        String page = GalleryComparison.page("baseline", GalleryComparison.of(sides[0]), "candidate",
                GalleryComparison.of(sides[1]));

        assertTrue(page.contains("2 triples were played by both"), page);
        assertTrue(page.contains("1 only by the baseline and 1 only by the candidate"), page);
        assertTrue(page.contains("| DEATH | 2 | 1 | 1 | 0 |"), "a death on 2 on both sides: " + page);
        assertTrue(page.contains("| DEATH | 4 | 0 | 1 | +1 |"), page);
        assertTrue(page.contains("| UNKNOWN\\_WINDOW | 1 | 0 | 1 | +1 |"), "undecided endings are counted, escaped: " + page);
        assertTrue(page.contains("| fight: enemy-in-view, hp-low | 2 | 1 | -1 |"),
                "the same situation from flags in either order: " + page);
        assertTrue(page.contains("| explore: starving | 1 | 0 | -1 |"), page);
        assertTrue(page.contains("| descend | 0 | 1 | +1 |"), "a situation with no flags is its Policy: " + page);
        assertTrue(!page.contains("no decision |"), "an undecided ending is not a death by situation");
        int worse = page.indexOf("| " + SeedSet.code(2000) + " |");
        int better = page.indexOf("| " + SeedSet.code(1000) + " |");
        assertTrue(worse > 0 && better > worse, "the shallower triple first: " + page);
        assertTrue(page.contains("| -1 | -500 |"), "depth and turns changed, signed: " + page);
        assertTrue(page.contains("| +2 | +1000 |"), page);
        assertTrue(page.contains("1 ended deeper in the candidate, 1 shallower; 1 survived longer, 1 shorter"), page);
    }

    @Test
    @DisplayName("the same two folders give the same page")
    void deterministic(@TempDir Path root) throws IOException {
        Path[] sides = sides(root);
        assertEquals(GalleryComparison.page("b", GalleryComparison.of(sides[0]), "c", GalleryComparison.of(sides[1])),
                GalleryComparison.page("b", GalleryComparison.of(sides[0]), "c", GalleryComparison.of(sides[1])));
    }

    @Test
    @DisplayName("the command writes the page for any two folders; a folder with the same triple and salt twice is refused")
    void command_and_refusal(@TempDir Path root) throws IOException {
        Path[] sides = sides(root);
        Path page = root.resolve("page.md");
        Gallery.main(new String[] {"--compare", sides[0].toString(), sides[1].toString(), page.toString()});
        assertTrue(Files.readString(page, StandardCharsets.UTF_8).startsWith("<!-- Written by"));

        Path twice = Files.createDirectories(root.resolve("twice"));
        log(twice, "a", 1000, 1000, "fight", List.of(), death(1, 1000));
        log(twice, "b", 1000, 1000, "fight", List.of(), death(1, 1000));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> GalleryComparison.of(twice));
        assertTrue(refused.getMessage().contains("two Runs"), refused.getMessage());
    }

    /** A run index for {@code folder}: every log in it, and {@code more} file names besides. */
    private static void index(Path folder, String... more) throws IOException {
        List<String> lines = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (var listed = Files.list(folder)) {
            listed.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jsonl")).sorted().forEach(names::add);
        }
        names.addAll(List.of(more));
        for (String name : names) {
            lines.add("{\"class\":\"WARRIOR\",\"log\":\"" + name + "\",\"runId\":\"" + name.replace(".jsonl", "") + "\"}");
        }
        Files.write(folder.resolve(RunIndex.RUNS), lines, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("through a run index: a log it names that is missing or has no header is counted, by its file name, and never compared")
    void missing_and_unreadable(@TempDir Path root) throws IOException {
        Path[] sides = sides(root);
        Files.writeString(sides[1].resolve("junk.jsonl"), "not a log\n", StandardCharsets.UTF_8);
        index(sides[0], "gone.jsonl");
        index(sides[1]);
        Map<String, GalleryComparison.Ending> baseline = GalleryComparison.of(sides[0]);
        Map<String, GalleryComparison.Ending> candidate = GalleryComparison.of(sides[1]);
        assertEquals(4, baseline.size(), "three logs and the missing one");
        assertEquals(Gallery.NO_LOG, baseline.get(GalleryComparison.UNKEYED + "gone.jsonl").cause());
        assertEquals(Gallery.UNREADABLE, candidate.get(GalleryComparison.UNKEYED + "junk.jsonl").cause());

        String page = GalleryComparison.page("baseline", baseline, "candidate", candidate);
        assertTrue(page.contains("2 triples were played by both"), page);
        assertTrue(page.contains("1 only by the baseline and 1 only by the candidate"), "unkeyed logs are not triples: " + page);
        assertTrue(page.contains("1 baseline and 1 candidate logs are missing or have no header"), page);
        assertTrue(page.contains("| NO\\_LOG | — | 1 | 0 | -1 |"), page);
        assertTrue(page.contains("| UNREADABLE | — | 0 | 1 | +1 |"), page);

        // The same missing file name on both sides is still no triple both played.
        index(sides[1], "gone.jsonl");
        String both = GalleryComparison.page("baseline", baseline, "candidate", GalleryComparison.of(sides[1]));
        assertTrue(both.contains("2 triples were played by both"), both);
        assertTrue(!both.contains("| " + GalleryComparison.UNKEYED), "an unkeyed log is not a compared row: " + both);
    }

    @Test
    @DisplayName("the gallery command on a comparison folder writes each side's gallery and the comparison view beside them")
    void comparison_folder(@TempDir Path root) throws IOException {
        Path[] sides = sides(root);
        index(sides[0]);
        index(sides[1]);
        Files.writeString(root.resolve(Comparison.FILE), "{}\n", StandardCharsets.UTF_8);
        Gallery.main(new String[] {root.toString()});
        assertTrue(Files.isRegularFile(sides[0].resolve(Gallery.FILE)));
        assertTrue(Files.isRegularFile(sides[1].resolve(Gallery.FILE)));
        String page = Files.readString(root.resolve(GalleryComparison.FILE), StandardCharsets.UTF_8);
        assertTrue(page.contains("candidate against baseline"), "the candidate is the candidate: " + page);
        assertTrue(page.contains("2 triples were played by both"), page);
    }
}
