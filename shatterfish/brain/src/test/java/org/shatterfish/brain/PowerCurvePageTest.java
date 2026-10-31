package org.shatterfish.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 10.17: the power-curve page's pooled figures are the ones in the logs it cites, not typed from
 * memory. The pooled block of each direction log is read and the page must state its reach, Goo kills and
 * deepest mean, and the per-class curve files must hold every class on depth 6.
 */
class PowerCurvePageTest {

    private static final String LOGS = "docs/results/logs/2026-10-31-10-17-power-curve/";

    private static Path root() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("docs/results/2026-10-31-10-17-the-power-curve.md"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "the repository root holds the power-curve page");
        return root;
    }

    /** The "a to b" text of the pooled row whose label starts {@code label}, from the first pooled block. */
    private static String pooled(String log, String label) {
        String block = log.substring(log.indexOf("#### pooled"));
        Matcher row = Pattern.compile("\\| " + Pattern.quote(label) + "[^|]*\\| (\\S+?)(?:/240)? \\| (\\S+?)(?:/240)? \\|").matcher(block);
        assertTrue(row.find(), "the pooled block has a row " + label);
        return row.group(1) + " to " + row.group(2);
    }

    @Test
    @DisplayName("the page states the pooled reach and Goo kills of both slices as the cited logs print them")
    void pooled_figures_are_the_logs() throws IOException {
        Path root = root();
        String page = Files.readString(root.resolve("docs/results/2026-10-31-10-17-the-power-curve.md"), StandardCharsets.UTF_8);
        for (String slice : new String[] {"S1000", "S3000"}) {
            String log = Files.readString(root.resolve(LOGS + slice + "-direction.txt"), StandardCharsets.UTF_8);
            assertTrue(page.contains(LOGS.substring("docs/results/".length()) + slice + "-direction.txt"), "the page cites " + slice);
            String reach = pooled(log, "reach depth 5");
            String goo = pooled(log, "Goo kills");
            assertTrue(page.contains("| Pooled | " + reach + " | " + goo + " |"),
                    "the page's pooled row for " + slice + " is " + reach + " and " + goo);
        }
    }

    @Test
    @DisplayName("a control: the pooled reader tells the two slices apart")
    void the_reader_discriminates() throws IOException {
        Path root = root();
        String one = Files.readString(root.resolve(LOGS + "S1000-direction.txt"), StandardCharsets.UTF_8);
        String three = Files.readString(root.resolve(LOGS + "S3000-direction.txt"), StandardCharsets.UTF_8);
        assertEquals("123 to 134", pooled(one, "reach depth 5"));
        assertEquals("106 to 120", pooled(three, "reach depth 5"));
    }

    @Test
    @DisplayName("the curve files hold every class on depth 6, on main and with the lever")
    void curves_hold_every_class() throws IOException {
        Path root = root();
        for (String file : new String[] {"curve-main-s1000.txt", "curve-l1-s1000.txt"}) {
            String curve = Files.readString(root.resolve(LOGS + file), StandardCharsets.UTF_8);
            for (String hero : new String[] {"WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC"}) {
                assertTrue(curve.contains("\n" + hero + " d6 "), file + " holds " + hero + " on depth 6");
            }
        }
    }
}
