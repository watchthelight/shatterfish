package org.shatterfish.codex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;
import org.shatterfish.harness.boot.HeadlessBoot;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hit table (story 6.1): the share of attacks that land, tabulated from the two uniform draws of
 * {@code Char.hit} (Char.java:646, :662) and the comparison at :678. The table is held to the rule
 * twice, by two routes that do not share its arithmetic: an independent closed form written here from
 * the geometry (the chance that one uniform draw on [0, a) is at least another on [0, d)), and the
 * engine's own {@code Char.hit} sampled in this process, which has booted the toolkit the generator may
 * not have.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class HitTableTest {

    /** The sampling tolerance, in thousandths: 20,000 attacks put four standard deviations under 15. */
    private static final int TOLERANCE = 25;

    private static final Codex.HitTable TABLE = Combat.hitTable(CodexSeedFreeTest.ROOT);

    /** The share by the rule's geometry, as a fraction: P(U[0,a) >= U[0,d)), a point mass at zero. */
    private static double closedForm(int a, int d) {
        if (a == 0) {
            return d == 0 ? 1 : 0;
        }
        if (d == 0) {
            return 1;
        }
        return a >= d ? 1 - d / (2.0 * a) : a / (2.0 * d);
    }

    private static int cell(int accuracy, int evasion) {
        return TABLE.cells().stream().filter(c -> c.accuracy() == accuracy && c.evasion() == evasion)
                .findFirst().orElseThrow().hitPerMille();
    }

    @Test
    @DisplayName("the table is measured, covers the declared grid and cites Char.hit")
    void the_table_covers_its_grid() {
        assertTrue(TABLE.measured());
        assertEquals("", TABLE.reason());
        assertEquals(21, TABLE.accuracy().size());
        assertEquals(21, TABLE.evasion().size());
        assertEquals(441, TABLE.cells().size());
        assertTrue(TABLE.citation().path().endsWith("actors/Char.java"), TABLE.citation().reference());
        assertEquals(619, TABLE.citation().line(), "the declaration of Char.hit(attacker, defender, accMulti, magic)");
    }

    @Test
    @DisplayName("at the grid corners and on its edges the table is the closed form, to the thousandth")
    void the_corners_are_the_closed_form() {
        assertEquals(1000, cell(0, 0), "no accuracy against no evasion: 0 >= 0");
        assertEquals(0, cell(0, 40), "no accuracy never lands on a defender with evasion");
        assertEquals(1000, cell(40, 0), "no evasion is always hit");
        assertEquals(500, cell(40, 40), "equal stats land half the time");
        assertEquals(750, cell(40, 20), "a = 2d: 1 - d/2a = 3/4");
        assertEquals(250, cell(20, 40), "a = d/2: a/2d = 1/4");
        assertEquals(975, cell(40, 2), "1 - 2/80");
        assertEquals(25, cell(2, 40), "2/80");
        assertEquals(500, cell(2, 2));
        for (Codex.HitCell c : TABLE.cells()) {
            long exact = Math.round(1000 * closedForm(c.accuracy(), c.evasion()));
            assertEquals(exact, c.hitPerMille(), "accuracy " + c.accuracy() + " evasion " + c.evasion());
        }
    }

    @Test
    @DisplayName("more accuracy never lands less, more evasion never lands more")
    void the_table_is_monotone() {
        for (int a = 0; a <= 40; a += 2) {
            for (int d = 0; d <= 40; d += 2) {
                if (a + 2 <= 40) {
                    assertTrue(cell(a + 2, d) >= cell(a, d), a + "," + d);
                }
                if (d + 2 <= 40) {
                    assertTrue(cell(a, d + 2) <= cell(a, d), a + "," + d);
                }
            }
        }
    }

    @Test
    @DisplayName("the table agrees with the engine's own Char.hit, sampled, within the stated tolerance")
    void the_table_agrees_with_the_engine() {
        HeadlessBoot.ensure();
        List<int[]> points = List.of(new int[] {0, 0}, new int[] {0, 20}, new int[] {20, 0}, new int[] {40, 0}, new int[] {0, 40},
                new int[] {40, 40}, new int[] {2, 2}, new int[] {10, 6}, new int[] {12, 4}, new int[] {20, 20}, new int[] {20, 40},
                new int[] {40, 20}, new int[] {16, 30}, new int[] {36, 8}, new int[] {4, 38}, new int[] {30, 30});
        for (int[] point : points) {
            Codex.HitCell sampled = Combat.cell(point[0], point[1]);
            int tabulated = cell(point[0], point[1]);
            assertTrue(Math.abs(sampled.hitPerMille() - tabulated) <= TOLERANCE,
                    "accuracy " + point[0] + " evasion " + point[1] + ": sampled " + sampled.hitPerMille()
                            + " against the table's " + tabulated);
        }
    }

    @Test
    @DisplayName("a lookup between cells interpolates, and past the grid it scales both stats together")
    void the_lookup_reads_the_table() {
        assertEquals(0.75, TABLE.chance(40, 20).orElseThrow(), 1e-9);
        assertEquals(0.75, TABLE.chance(80, 40).orElseThrow(), 1e-9, "the table is a function of the ratio: scaled to fit");
        assertEquals(0.5, TABLE.chance(25, 25).orElseThrow(), 0.01);
        assertEquals(1.0, TABLE.chance(13, 0).orElseThrow(), 1e-9);
        double between = TABLE.chance(13, 7).orElseThrow();
        assertTrue(Math.abs(between - closedForm(13, 7)) < 0.02, "interpolated " + between + " against " + closedForm(13, 7));
    }
}
