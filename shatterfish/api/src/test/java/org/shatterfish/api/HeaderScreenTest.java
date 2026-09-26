package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screen an Overlay Run played on, stated in its log header (story 5.2): the interface size and
 * whether a controller was connected, both of which change the log text the Observation carries. A
 * headless Run states neither and keeps its bytes.
 */
class HeaderScreenTest {

    private static final long SEED = 12345;
    private static final RunLog.Brain BRAIN = new RunLog.Brain("random", "0".repeat(40), "a".repeat(64));

    private static RunLog.Header header(String driver, int interfaceSize, int controller) {
        return new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), 7, 100, 1, 1, 1, BRAIN, "", false, "m", "t", driver, interfaceSize, controller);
    }

    @Test
    @DisplayName("an Overlay header states its interface size and controller, chained")
    void stated() {
        RunLog.Header overlay = header(RunLog.Header.EMBEDDED, 1, 0);
        String line = RunLogJson.canonical(overlay);
        assertTrue(line.contains("\"interface\":1"), line);
        assertTrue(line.contains("\"controller\":0"), line);
        assertTrue(!RunLogJson.chain("", overlay).equals(RunLogJson.chain("", header(RunLog.Header.EMBEDDED, 0, 0))),
                "the interface size is chained");
        assertTrue(!RunLogJson.chain("", overlay).equals(RunLogJson.chain("", header(RunLog.Header.EMBEDDED, 1, 1))),
                "and so is the controller");
    }

    @Test
    @DisplayName("a headless header states neither, and its line is as before")
    void headless() {
        RunLog.Header headless = header("", -1, -1);
        String line = RunLogJson.canonical(headless);
        assertFalse(line.contains("\"interface\""), line);
        assertFalse(line.contains("\"controller\""), line);
        RunLog.Header old = new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), 7, 100, 1, 1, 1, BRAIN, "", false, "m", "t");
        assertEquals(RunLogJson.canonical(old), line, "the headless header's bytes did not change");
    }

    @Test
    @DisplayName("a headless Run cannot state a screen, and the values are bounded")
    void refused() {
        assertThrows(IllegalArgumentException.class, () -> header("", 0, -1));
        assertThrows(IllegalArgumentException.class, () -> header("", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> header(RunLog.Header.EMBEDDED, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> header(RunLog.Header.EMBEDDED, 1, 2));
    }
}
