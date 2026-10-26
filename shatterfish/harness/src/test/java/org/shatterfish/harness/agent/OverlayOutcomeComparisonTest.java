package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Overlay's presence does not change outcomes (story 5.16, SM-6): the Runs of a Rig folder, played again as the
 * desktop game plays a Run, are the same Runs.
 *
 * <p>The Rig folder is made by {@code ./gradlew :rig:direction} (one JVM per Run, the headless driver, the Rig's
 * Brain), and this test reads each log's tuple from its header: seed, class, salt and cap. Each is then played by the
 * embedded Run under {@link EmbeddedHost} the way {@link OverlayReplayTest} plays one (frames of a different length,
 * frames drawn while the Brain thinks, the Brain deciding on its worker), with the same Brain built the way
 * {@code RunOne} builds it. Every wait of the two logs is compared: its Observation hash, Action, Decision, Belief,
 * turn, depth and whether it was applied; then the ending. The table it writes has one row for each Run.
 *
 * <p>It runs only when {@code SHATTERFISH_COMPARE_RIG} names the folder (the Runs take minutes each, and the Rig
 * numbers are made outside the test); {@code SHATTERFISH_COMPARE_OUT} names the file the table is written to:
 *
 * <pre>
 * ./gradlew :rig:direction --args="--set standard --class WARRIOR --first 16 --salt-base 1000 --jobs 2 --out build/direction/s516"
 * SHATTERFISH_COMPARE_RIG=build/direction/s516/current SHATTERFISH_COMPARE_OUT=build/overlay-comparison.md \
 *     ./gradlew :harness:test --tests '*OverlayOutcomeComparisonTest*' -Pshatterfish.testForks=1
 * </pre>
 */
class OverlayOutcomeComparisonTest {

    @Test
    @DisplayName("the Runs of a Rig folder, played in the Overlay's driver, are the same Runs wait for wait")
    @Timeout(value = 120, unit = TimeUnit.MINUTES)
    void the_overlay_does_not_change_outcomes() throws IOException {
        String given = System.getenv("SHATTERFISH_COMPARE_RIG");
        Assumptions.assumeTrue(given != null && !given.isBlank(), "SHATTERFISH_COMPARE_RIG names the Rig folder to compare with");
        Path rig = Path.of(given).toAbsolutePath();
        List<Path> logs;
        try (Stream<Path> files = Files.list(rig)) {
            logs = files.filter(path -> path.toString().endsWith(".jsonl")).sorted().toList();
        }
        assertTrue(!logs.isEmpty(), "Rig logs in " + rig);
        List<String> rows = new ArrayList<>();
        int samePlay = 0;
        int sameWaits = 0;
        for (Path log : logs) {
            String row = compare(log);
            rows.add(row);
            if (row.contains("| yes | yes |") || row.contains("| yes | NO")) {
                samePlay++;
            }
            if (row.endsWith("| yes |")) {
                sameWaits++;
            }
        }
        String table = "| Run | Rig ending | Rig depth | Rig turns | Rig waits | Overlay ending | Overlay depth | Overlay turns"
                + " | Overlay waits | same play and ending | every Observation the same |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|\n"
                + String.join("\n", rows) + "\n\n" + samePlay + " of " + logs.size()
                + " Runs played the same and ended the same; " + sameWaits + " of " + logs.size()
                + " with every Observation the same.\n";
        String out = System.getenv("SHATTERFISH_COMPARE_OUT");
        if (out != null && !out.isBlank()) {
            Files.writeString(Path.of(out), table);
        }
        System.out.println(table);
        // Every Run plays the same and ends the same in this driver, however long its frames are (issue #245 was the
        // badge banner's shine drawing from the Run's stream on a frame the wall clock picks; hook row 8 covers it now).
        List<String> unlike = rows.stream().filter(row -> !(row.contains("| yes | yes |") || row.contains("| yes | NO"))).toList();
        assertTrue(unlike.isEmpty(),
                "every Run played the same and ended the same in the Overlay's driver:\n" + table);
    }

    private static String compare(Path rigLog) throws IOException {
        RunLogReader.Log rig = RunLogReader.of(rigLog);
        RunLog.Header header = rig.header();
        Path folder = Files.createTempDirectory("overlay-comparison");
        RunLog.Brain who = new RunLog.Brain("shatterfish", "0".repeat(40), "0".repeat(64));
        HeroClass heroClass = HeroClass.valueOf(header.heroClass().name());
        try (EmbeddedHost host = new EmbeddedHost(header.seed(), heroClass, header.salt())) {
            host.stepWhileThinking = true;
            EmbeddedRun run = host.attach(RealBrain.decider(), new RunLoop.Logging(folder, "0".repeat(40), who, "",
                    "overlay"), header.cap());
            OverlayReplayTest.desktopFrames(host, header.salt() * 31 + 7);
            assertEquals(EmbeddedRun.State.ENDED, run.state());
        }
        Path played;
        try (Stream<Path> files = Files.list(folder)) {
            played = files.filter(path -> path.toString().endsWith(".jsonl")).findFirst().orElseThrow();
        }
        RunLogReader.Log overlay = RunLogReader.of(played);
        List<RunLog.Wait> a = rig.waits();
        List<RunLog.Wait> b = overlay.waits();
        String differs = "";
        int differing = 0;
        boolean playSame = true;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            RunLog.Wait x = a.get(i);
            RunLog.Wait y = b.get(i);
            if (!x.obs().equals(y.obs()) || !x.action().equals(y.action()) || x.applied() != y.applied()
                    || x.turn() != y.turn() || x.depth() != y.depth()
                    || !java.util.Objects.equals(x.decision(), y.decision())
                    || !java.util.Objects.equals(x.belief(), y.belief())) {
                java.util.List<String> fields = new ArrayList<>();
                if (!x.obs().equals(y.obs())) fields.add("obs");
                if (!x.action().equals(y.action())) fields.add("action");
                if (x.applied() != y.applied()) fields.add("applied");
                if (x.turn() != y.turn() || x.depth() != y.depth()) fields.add("turn/depth");
                if (!java.util.Objects.equals(x.decision(), y.decision())) fields.add("decision");
                if (!java.util.Objects.equals(x.belief(), y.belief())) fields.add("belief");
                differing++;
                playSame &= !fields.contains("action") && !fields.contains("applied") && !fields.contains("turn/depth");
                if (differs.isEmpty()) {
                    differs = "wait " + x.k() + ": " + String.join(", ", fields);
                }
            }
        }
        if (differs.isEmpty() && a.size() != b.size()) {
            differs = "length " + a.size() + " / " + b.size();
        }
        RunLog.End ra = rig.end();
        RunLog.End rb = overlay.end();
        boolean ends = ra.outcome().equals(rb.outcome()) && ra.k() == rb.k();
        return "| " + header.seedCode() + "/" + header.salt() + " | " + cause(ra) + " | " + depth(ra) + " | " + turns(ra)
                + " | " + a.size() + " | " + cause(rb) + " | " + depth(rb) + " | " + turns(rb) + " | " + b.size()
                + " | " + (playSame && ends && a.size() == b.size() ? "yes" : "NO") + " | " + (differs.isEmpty() && ends ? "yes" : "NO (" + (differs.isEmpty() ? "ending" : differs + ", " + differing + " waits differ") + ")")
                + " |";
    }

    private static String cause(RunLog.End end) {
        return end.outcome().cause();
    }

    private static String depth(RunLog.End end) {
        return String.valueOf(end.outcome().depth());
    }

    private static String turns(RunLog.End end) {
        return String.valueOf(end.outcome().turns() / 1000);
    }
}
