package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shatterfish's GSPRT against Fishtest's, number for number (story 3.6, ADR-0012 option 6).
 *
 * <p>The values in {@code gsprt-reference.txt} were computed by Fishtest itself, at the commit named
 * in the file's first line, by {@code tools/gsprt_reference.py}. Fishtest's code is not in this
 * repository -- it carries no licence -- so {@link Gsprt} implements the published formula on its
 * own, and this is what says the two agree: 270 cases across five pairs of hypotheses, three pairs of
 * error rates, and eighteen sets of counts chosen to reach the regularization and the clamp in both
 * directions.
 *
 * <p>An oracle rather than a second implementation written here, because a test that recomputed the
 * formula in Java would agree with any mistake the class and the test shared.
 */
class GsprtReferenceTest {

    /** Agreement to the last few bits: both sides are doubles doing the same arithmetic in a different order. */
    private static final double CLOSE = 1e-9;

    @Test
    @DisplayName("every reference case gives Fishtest's LLR, bounds and clamp")
    void the_port_agrees_with_fishtest() throws IOException {
        List<String> cases = cases();
        assertEquals(270, cases.size(), "the reference file holds every case the generator writes");
        int clamped = 0;
        for (String line : cases) {
            String[] f = line.trim().split("\s+");
            double p0 = Double.parseDouble(f[0]);
            double p1 = Double.parseDouble(f[1]);
            double alpha = Double.parseDouble(f[2]);
            double beta = Double.parseDouble(f[3]);
            Gsprt test = new Gsprt(p0, p1, alpha, beta, 1, 2);
            Gsprt.Llr llr = test.llr(Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]));

            close(Double.parseDouble(f[7]), test.lower(), "the lower bound: " + line);
            close(Double.parseDouble(f[8]), test.upper(), "the upper bound: " + line);
            close(Double.parseDouble(f[9]), llr.llr(), "the LLR: " + line);
            assertEquals(Boolean.parseBoolean(f[10]), llr.clamped(), "the clamp: " + line);
            clamped += llr.clamped() ? 1 : 0;
        }
        assertTrue(clamped > 0 && clamped < cases.size(),
                "the cases reach both sides of the clamp: " + clamped + " of " + cases.size());
    }

    @Test
    @DisplayName("the reference file names the Fishtest commit it came from")
    void the_reference_is_pinned() throws IOException {
        String first = text().lines().findFirst().orElse("");
        assertTrue(first.contains("2e540196ed8a72283a17f40793defd0f4a45d9c9"), first);
    }

    private static void close(double expected, double actual, String what) {
        assertEquals(expected, actual, CLOSE * Math.max(1, Math.abs(expected)), what);
    }

    private static List<String> cases() throws IOException {
        List<String> cases = new ArrayList<>();
        for (String line : text().split("\n")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                cases.add(line);
            }
        }
        return cases;
    }

    private static String text() throws IOException {
        try (InputStream in = GsprtReferenceTest.class.getResourceAsStream("/gsprt-reference.txt")) {
            assertNotNull(in, "the reference file is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
