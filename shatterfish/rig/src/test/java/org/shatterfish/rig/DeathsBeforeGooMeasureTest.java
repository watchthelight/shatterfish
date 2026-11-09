package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 10.42, the second half of the page: the throw rule measured on two slices and kept on the epic's keep rule. Every verdict row is
 * recomputed from the committed {@code runs.tsv} of each slice with the E3 Sequential test and compared with the page, and
 * the claim that the rule is not kept is recomputed with {@link TuneConfirm#kept}.
 */
class DeathsBeforeGooMeasureTest {

    static Path runs(String slice) {
        return DeathsBeforeGooPageTest.FOLDER.resolve("fix-" + slice).resolve("runs.tsv");
    }

    @Test
    @DisplayName("story 10.42: the verdict rows on the page are the E3 test over the committed pairs of each slice")
    void rows_match_the_page() throws IOException {
        String page = TuneRunPageTest.read(DeathsBeforeGooPageTest.PAGE);
        for (String slice : List.of("1000", "3000")) {
            for (String row : TuneRunPageTest.verdictRows(runs(slice))) {
                assertTrue(page.contains(row), "slice " + slice + ": the page lacks " + row);
            }
        }
    }

    @Test
    @DisplayName("story 10.42: the keep rule holds: pooled not worse, no class vetoed, and E3 did not accept H1")
    void kept_on_the_keep_rule() {
        int better = 0;
        int worse = 0;
        int equal = 0;
        for (String slice : List.of("1000", "3000")) {
            List<TuneConfirm.Verdict> v = TuneConfirm.verdicts(TuneConfirm.fromTable(runs(slice), "candidate"),
                    TuneConfirm.fromTable(runs(slice), "current"));
            assertFalse(TuneConfirm.kept(v), "slice " + slice + ": E3 does not accept H1, which the keep rule does not need");
            assertTrue(v.stream().skip(1).noneMatch(TuneConfirm::vetoed), "slice " + slice);
            better += v.get(0).better();
            worse += v.get(0).worse();
            equal += v.get(0).equal();
        }
        assertTrue(better >= worse, "pooled over both slices not worse");
        assertEquals(52, better);
        assertEquals(39, worse);
        assertEquals(389, equal);
    }
}
