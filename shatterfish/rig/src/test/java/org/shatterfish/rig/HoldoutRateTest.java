package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one `holdout` confirmation run of the E7 Brain (Registration H-0009): the Results page carries exactly the figures
 * the committed outcomes give, beside the same Brain's `standard` figures of story 7.10, and the 7.10 page links to it.
 */
class HoldoutRateTest {

    static final Path PAGE = SeedSetsTest.ROOT.resolve("docs/results/2026-10-27-e7-holdout.md");

    static final Path HOLDOUT = SeedSetsTest.ROOT.resolve("results/2026-10-27-H-0009/outcomes.jsonl");

    @Test
    @DisplayName("the exact binomial upper limit matches hand-worked values: 0 of 500 and 0 of 83 are 1 - 0.025^(1/n), 1 of 2 is sqrt(0.975)")
    void the_exact_upper_limit_is_right() {
        assertEquals(1 - Math.pow(0.025, 1.0 / 500), WinRate.exactUpper(0, 500), 1e-9);
        assertEquals(1 - Math.pow(0.025, 1.0 / 83), WinRate.exactUpper(0, 83), 1e-9);
        assertEquals(Math.sqrt(0.975), WinRate.exactUpper(1, 2), 1e-9);
        assertEquals(1.0, WinRate.exactUpper(5, 5), 0.0);
    }

    @Test
    @DisplayName("the committed holdout outcomes are the 500 holdout triples, none of them a standard triple")
    void the_run_covers_the_holdout_set() throws IOException {
        List<WinRate.Outcome> held = WinRate.read(HOLDOUT);
        assertEquals(500, held.size());
        assertTrue(held.stream().noneMatch(WinRate.Outcome::win), "the page says no holdout Run won");
        Set<String> seeds = Files.readAllLines(HOLDOUT, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank())
                .map(l -> org.shatterfish.harness.log.Json.object(l))
                .map(m -> m.get("class") + ":" + m.get("seed"))
                .collect(Collectors.toSet());
        Set<String> standard = Files.readAllLines(WinRateTest.CURRENT, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank())
                .map(l -> org.shatterfish.harness.log.Json.object(l))
                .map(m -> m.get("class") + ":" + m.get("seed"))
                .collect(Collectors.toSet());
        assertEquals(500, seeds.size(), "every holdout triple appears once");
        assertTrue(seeds.stream().noneMatch(standard::contains), "no holdout triple was a standard triple");
    }

    @Test
    @DisplayName("the holdout page carries the block rendered from the committed outcomes, and the 7.10 page links to it")
    void the_page_has_not_drifted() throws IOException {
        String block = WinRate.renderHoldout(WinRate.read(WinRateTest.CURRENT), WinRate.read(HOLDOUT));
        if ("1".equals(System.getenv(WinRateTest.WRITE))) {
            String text = Files.readString(PAGE, StandardCharsets.UTF_8);
            String begin = "<!-- holdout:begin -->";
            int from = text.indexOf(begin);
            int to = text.indexOf("<!-- holdout:end -->");
            Files.writeString(PAGE, text.substring(0, from + begin.length()) + "\n\n" + block.strip() + "\n\n" + text.substring(to),
                    StandardCharsets.UTF_8);
        }
        String text = Files.readString(PAGE, StandardCharsets.UTF_8);
        int from = text.indexOf("<!-- holdout:begin -->");
        int to = text.indexOf("<!-- holdout:end -->");
        assertTrue(from >= 0 && to > from, "the page has no holdout markers");
        assertEquals(block.strip(), text.substring(from + "<!-- holdout:begin -->".length(), to).strip(),
                "the page's tables are stale: run this test with " + WinRateTest.WRITE + "=1");
        String measured = Files.readString(WinRateTest.PAGE, StandardCharsets.UTF_8);
        assertTrue(measured.contains("2026-10-27-e7-holdout.md"), "the 7.10 page links to the holdout page");
    }
}
