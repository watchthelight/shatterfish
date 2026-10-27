package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 7.10: the Wilson interval is right, a win rate of zero is reported as zero with its upper bound, and the
 * Results page of the measured win rate carries exactly the figures the committed outcomes give (no figure typed into
 * prose, as the E6 retrospective asked).
 */
class WinRateTest {

    static final String WRITE = "SHATTERFISH_WRITE_WINRATE";

    static final Path PAGE = SeedSetsTest.ROOT.resolve("docs/results/2026-10-27-7-10-the-measured-win-rate.md");

    /** The committed outcomes of the final Brain's Runs on the 500 `standard` triples (registration H-0008). */
    static final Path CURRENT = SeedSetsTest.ROOT.resolve("results/2026-10-27-H-0008/outcomes.jsonl");

    /** The committed outcomes of the Profile 6 baseline, played again on the same 500 triples. */
    static final Path BASELINE = SeedSetsTest.ROOT.resolve("results/2026-10-27-p6-baseline-standard/outcomes.jsonl");

    @Test
    @DisplayName("the Wilson interval matches hand-worked values: 50 of 100, 1 of 2, 0 of n, n of n")
    void the_wilson_interval_is_right() {
        double[] half = WinRate.wilson(50, 100);
        assertEquals(0.4038, half[0], 0.0005);
        assertEquals(0.5962, half[1], 0.0005);
        double[] one = WinRate.wilson(1, 2);
        assertEquals(0.0945, one[0], 0.0005);
        assertEquals(0.9055, one[1], 0.0005);
        double[] none = WinRate.wilson(0, 500);
        assertEquals(0.0, none[0], 1e-12);
        assertEquals(0.00762, none[1], 0.00001);
        double[] all = WinRate.wilson(500, 500);
        assertEquals(1.0, all[1], 1e-12);
        assertEquals(0.99238, all[0], 0.00001);
    }

    @Test
    @DisplayName("a zero in 500 Runs has an upper bound under 1%, and the column says zero wins with that bound")
    void zero_is_reported_as_zero_with_its_upper_bound() {
        assertTrue(WinRate.wilson(0, 500)[1] < 0.01);
        List<WinRate.Outcome> none = java.util.stream.IntStream.range(0, 500)
                .mapToObj(i -> new WinRate.Outcome("WARRIOR", "DEATH", 1 + i % 4, 0, false, 0, 1000))
                .toList();
        List<String> column = WinRate.column(none);
        assertEquals("0/500 (0.0%)", column.get(1));
        assertEquals("0.00% to 0.76%", column.get(2));
        assertEquals("no wins", column.get(3));
    }

    @Test
    @DisplayName("a win is counted once, its Score is reported, and a Run that is not a death is not a death by depth")
    void wins_and_scores_are_counted() {
        List<WinRate.Outcome> runs = List.of(
                new WinRate.Outcome("MAGE", "WIN", 26, 5, true, 9000, 30000),
                new WinRate.Outcome("MAGE", "DEATH", 5, 1, false, 100, 2000),
                new WinRate.Outcome("MAGE", "TURN_CAP", 3, 0, false, 50, 20000));
        List<String> column = WinRate.column(runs);
        assertEquals("1/3 (33.3%)", column.get(1));
        assertEquals("min 9000, median 9000, max 9000", column.get(3));
        assertEquals("2/3 (66.7%)", column.get(4));
        assertEquals("2/2 (100.0%)", column.get(5));
        assertTrue(column.get(6).startsWith("0/0/0/0/1/0"), column.get(6));
    }

    @Test
    @DisplayName("the committed Results page carries the block rendered from the committed outcomes and the link count rendered from the code")
    void the_page_has_not_drifted() throws IOException {
        String winBlock = WinRate.render(WinRate.read(BASELINE), WinRate.read(CURRENT));
        String linkBlock = WinRate.heuristicBlock();
        if ("1".equals(System.getenv(WRITE))) {
            write("winrate", winBlock);
            write("links", linkBlock);
        }
        assertEquals(winBlock.strip(), block("winrate"), "the page's win-rate tables are stale: run this test with " + WRITE + "=1");
        assertEquals(linkBlock.strip(), block("links"), "the page's link count is stale: run this test with " + WRITE + "=1");
    }

    @Test
    @DisplayName("both sides played the 500 standard triples, none of them holdout or goo, and the page says the holdout run came afterwards and links its page")
    void the_measurement_covers_the_set_and_the_page_says_what_it_did_not_take() throws IOException {
        assertEquals(500, WinRate.read(CURRENT).size());
        assertEquals(500, WinRate.read(BASELINE).size());
        String page = Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        assertTrue(page.contains("was run once, afterwards"), "the page says holdout was run once, afterwards");
        assertTrue(page.contains("2026-10-27-e7-holdout.md"), "the page links the holdout page");
    }

    private static String block(String name) throws IOException {
        String text = Files.readString(PAGE, StandardCharsets.UTF_8);
        String begin = "<!-- " + name + ":begin -->";
        String end = "<!-- " + name + ":end -->";
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        assertTrue(from >= 0 && to > from, "the page has no " + name + " markers");
        return text.substring(from + begin.length(), to).strip();
    }

    private static void write(String name, String body) throws IOException {
        String text = Files.readString(PAGE, StandardCharsets.UTF_8);
        String begin = "<!-- " + name + ":begin -->";
        String end = "<!-- " + name + ":end -->";
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        Files.writeString(PAGE, text.substring(0, from + begin.length()) + "\n\n" + body.strip() + "\n\n" + text.substring(to), StandardCharsets.UTF_8);
    }
}
