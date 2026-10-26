package org.shatterfish.harness.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.Launcher;

import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The measurement runs in a tiny configuration and keeps its structure (story 6.5): one snapshot, three drivers, the same
 * siblings for each, every leaf read at both horizons, the live Run put back (the measurement throws if it is not), and
 * the page prints every table. No figure is pinned: the numbers are this machine's and this commit's, and a test that
 * held them would hold the Brain's behaviour still, which the Rig is for.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class PropertiesSmokeTest {

    @Test
    @DisplayName("a tiny measurement plays every driver from the same snapshot, reads both horizons, and prints the page")
    void a_tiny_measurement_keeps_its_structure() throws IOException {
        PropertiesMeasurement.Config tiny = new PropertiesMeasurement.Config("smoke", 1, 1, 2, 0, 1_500, 20);
        PropertiesMeasurement.Report report = PropertiesMeasurement.run(tiny, new Launcher.Launch(1L, true),
                new PrintStream(new ByteArrayOutputStream()));

        assertEquals(1, report.snapshots(), "one mid-fight snapshot on the first smoke seed");
        assertEquals(PropertiesMeasurement.DRIVERS.size(), report.rows().size());
        for (Properties.Row row : report.rows()) {
            assertEquals(1, row.samples().size(), row.driver() + ": one sample");
            Properties.Sample sample = row.samples().get(0);
            assertEquals(2, row.playouts(), row.driver() + ": two siblings asked");
            assertEquals(row.playouts(), sample.leaves().size() + row.dropped(), row.driver() + ": every playout is a leaf or counted dropped");
            assertEquals(report.rows().get(0).samples().get(0).hiddenRun(), sample.hiddenRun(),
                    "the drivers share the snapshot, so they share its hidden facts");
            assertTrue(sample.hiddenFloor() <= sample.hiddenRun());
            for (Properties.Leaf leaf : sample.leaves()) {
                assertEquals(Properties.HORIZONS.length, leaf.at().length);
                assertTrue(leaf.endedAfter() >= 1 && leaf.endedAfter() <= Properties.HORIZONS[Properties.HORIZONS.length - 1]);
                for (Properties.At at : leaf.at()) {
                    assertTrue(at.revealedFloor() >= 0 && at.revealedFloor() <= sample.hiddenFloor(), "revealed within the floor's facts");
                    assertTrue(at.revealedRun() >= at.revealedFloor() && at.revealedRun() <= sample.hiddenRun());
                }
                if (leaf.end() == Properties.End.DEATH) {
                    assertFalse(leaf.at()[Properties.HORIZONS.length - 1].alive(), "a death is dead at the long horizon");
                }
            }
        }
        String page = PropertiesMeasurement.markdown(report, "test", "test");
        for (String heading : List.of("### Environment", "### The two properties, per driving Brain", "### What the leaves did", "ORACLE")) {
            assertTrue(page.contains(heading), "the page has " + heading);
        }
    }
}
