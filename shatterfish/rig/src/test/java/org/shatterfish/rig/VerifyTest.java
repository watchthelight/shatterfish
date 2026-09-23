package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --verify}: every log in a folder, against its own bytes and against the index (story 3.4).
 *
 * <p>The logs here are written rather than played. Verification reads text and hashes it — it has
 * no opinion about whether a Run is plausible, and it must not, because the thing it is checking is
 * a file it did not produce. Playing real Runs to test a text check would make the test slower and
 * would test less: it could not produce the one case that matters, which is a log that was changed
 * after it was written.
 */
class VerifyTest {

    private static final long SEED = 12_345L;

    private static final String ZERO = "0".repeat(64);

    private static final String ONE = "1".repeat(64);

    private static RunLog.Header header(long salt) {
        return new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), salt, 20_000, 3, ObservationCodec.SCHEMA_VERSION, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false, "a laptop",
                "2026-09-22T12:00:00Z");
    }

    private static RunLog.Wait served(long k) {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("map", ONE);
        return new RunLog.Wait(k, 1_000 * k, 1, 0, ZERO, sections, new Action.Step(17), true,
                RunLog.BOT, null, "", List.of(), 5);
    }

    private static RunLog.End end() {
        return new RunLog.End(9, new RunLog.Outcome(false, false, 65, 1, 9_000, "DEATH", 0), true);
    }

    /** Writes a whole log, chained, and returns the chain it ends on. */
    private static String log(Path folder, long salt, List<RunLog> records) throws IOException {
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Files.writeString(folder.resolve(RunLog.fileName(header(salt).runId())), out.toString(),
                StandardCharsets.UTF_8);
        return previous;
    }

    private static String good(Path folder, long salt) throws IOException {
        return log(folder, salt, List.of(header(salt), served(1), served(2), end()));
    }

    /** A run index naming one Run and the chain it ended on. */
    private static void index(Path folder, long salt, String chain) throws IOException {
        Files.writeString(folder.resolve(RunIndex.RUNS),
                "{\"chain\":\"" + chain + "\",\"log\":\"" + RunLog.fileName(header(salt).runId())
                        + "\",\"runId\":\"" + header(salt).runId() + "\",\"state\":\"FINISHED\"}\n",
                StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------ what it finds

    @Test
    @DisplayName("a folder whose logs are what they say they are verifies, and says how many")
    void a_whole_folder_verifies(@TempDir Path folder) throws IOException {
        String chain = good(folder, 7L);
        index(folder, 7L, chain);

        Verify.Report report = Verify.of(folder);

        assertTrue(report.ok(false), report.text());
        assertEquals(1, report.ok());
        assertEquals(0, report.broken());
        assertTrue(report.checked().get(0).complete(), "the Run reached an ending");
        assertEquals(chain, report.checked().get(0).chain());
        assertTrue(report.text().contains("1 of 1 logs"), report.text());
    }

    @Test
    @DisplayName("a log changed after it was written is caught, and the line is named")
    void a_changed_byte_is_caught(@TempDir Path folder) throws IOException {
        String chain = good(folder, 7L);
        index(folder, 7L, chain);
        Path file = Verify.logs(folder).get(0);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // The second record's turn, raised by one. Nothing else in the file is touched.
        Files.writeString(file, text.replace("\"turn\":1000", "\"turn\":1001"), StandardCharsets.UTF_8);

        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        Verify.Checked one = report.checked().get(0);
        assertFalse(one.verified());
        assertEquals(2, one.brokenLine(), "the line whose chain the bytes no longer give");
        assertTrue(one.why().contains("line 2"), one.why());
    }

    @Test
    @DisplayName("an index that states a chain the log does not give is caught, which is what makes the index evidence")
    void an_index_that_disagrees_is_caught(@TempDir Path folder) throws IOException {
        good(folder, 7L);
        // The index says the Run ended on a chain of its own. Both files are internally perfect:
        // the log chains, the index parses. They are simply not about each other, which is the
        // failure a Results page citing a number from one and a log from the other would publish.
        index(folder, 7L, "f".repeat(64));

        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        Verify.Checked one = report.checked().get(0);
        assertTrue(one.verified(), "the log itself is intact");
        assertTrue(one.why().contains("the index says"), one.why());
        assertTrue(report.text().contains("the index says"), report.text());
    }

    @Test
    @DisplayName("a Run the index names and no file answers for is reported, not skipped")
    void a_missing_log_is_reported(@TempDir Path folder) throws IOException {
        String chain = good(folder, 7L);
        index(folder, 7L, chain);
        // A second Run, indexed, whose child died before a byte of it reached the disk.
        Files.writeString(folder.resolve(RunIndex.RUNS),
                Files.readString(folder.resolve(RunIndex.RUNS), StandardCharsets.UTF_8)
                        + "{\"chain\":\"\",\"log\":\"" + RunLog.fileName(header(9L).runId())
                        + "\",\"runId\":\"" + header(9L).runId() + "\",\"state\":\"INCOMPLETE\"}\n",
                StandardCharsets.UTF_8);

        Verify.Report report = Verify.of(folder);

        assertEquals(2, report.checked().size(), report.text());
        assertEquals(1, report.broken());
        assertTrue(report.text().contains("holds no log for it"), report.text());
    }

    @Test
    @DisplayName("a killed Run's log verifies as far as it goes, and that is not a failure")
    void an_incomplete_log_still_verifies(@TempDir Path folder) throws IOException {
        // Every prefix of a valid log is a valid log. The Rig already counts this Run incomplete;
        // calling it tampering would make a machine that ran out of time look like a dishonest one.
        String chain = log(folder, 7L, List.of(header(7L), served(1)));
        index(folder, 7L, chain);

        Verify.Report report = Verify.of(folder);

        assertTrue(report.ok(true), "an incomplete Run is not a changed file: " + report.text());
        assertFalse(report.ok(false), "and it is still not a finished Run");
        assertFalse(report.checked().get(0).complete());
        assertTrue(report.text().contains("incomplete"), report.text());
    }

    @Test
    @DisplayName("a log whose header claims the oracle is reported, not counted as intact")
    void an_oracle_log_is_reported(@TempDir Path folder) throws IOException {
        // FR-11 is enforced when a Run is dispatched, in the process whoever ran it controls. This
        // is the only place it is asked of the artifact, which is the half that survives a folder
        // being handed to somebody else -- and the first draft had the flag in its hand and never
        // looked at it, so a folder of disqualified Runs printed a clean pass.
        RunLog.Header h = header(7L);
        RunLog.Header oracle = new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(),
                h.challenges(), h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(), h.obsv(),
                h.codex(), h.brain(), h.registration(), true, h.machine(), h.started());
        String chain = log(folder, 7L, List.of(oracle, served(1), end()));
        index(folder, 7L, chain);

        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        assertTrue(report.checked().get(0).verified(), "the file itself is intact");
        assertTrue(report.checked().get(0).why().contains("FR-11"), report.checked().get(0).why());
    }

    @Test
    @DisplayName("a folder with no index has not been verified, it has been read")
    void a_folder_without_an_index_is_not_a_pass(@TempDir Path folder) throws IOException {
        // The cheapest way to pass a folder that was edited: rewrite a log, rechain it, and delete
        // runs.jsonl. Every log still verifies against its own bytes and not one of them is held
        // against anything published, which is the whole of what the index check is for.
        good(folder, 7L);

        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        assertEquals(1, report.unindexed());
        assertTrue(report.why().contains("cannot be verified"), report.why());
        assertTrue(report.text().contains("no published chain"), report.text());
    }

    @Test
    @DisplayName("a log whose name is not the Run its header states is reported")
    void a_misnamed_log_is_reported(@TempDir Path folder) throws IOException {
        // `Runner.finish` makes exactly this check for the Rig's own writes, and said why: without
        // it two files claiming one Run are both held against one index row, and which one is
        // believed is whichever sorts first.
        String chain = good(folder, 7L);
        index(folder, 7L, chain);
        Path was = Verify.logs(folder).get(0);
        Files.move(was, folder.resolve("v4.0.0-WARRIOR-0-AAA-AAA-AAB-000000000000000e-random.jsonl"));

        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        assertTrue(report.text().contains("its header says it is the Run"), report.text());
    }

    @Test
    @DisplayName("a folder with nothing in it is not a folder that verified")
    void an_empty_folder_is_not_a_pass(@TempDir Path folder) {
        Verify.Report report = Verify.of(folder);

        assertFalse(report.ok(true), report.text());
        assertTrue(report.why().contains("nothing to verify"), report.why());
    }

    @Test
    @DisplayName("a folder that is not there is said to be, rather than read as empty")
    void a_missing_folder_says_so(@TempDir Path folder) {
        Verify.Report report = Verify.of(folder.resolve("elsewhere"));

        assertFalse(report.ok(true));
        assertTrue(report.why().contains("no folder"), report.why());
    }

    // ------------------------------------------------------------------ what the command answers

    @Test
    @DisplayName("the command answers 0 for a folder that verifies and 1 for one that does not")
    void the_command_answers_with_a_status(@TempDir Path folder) throws IOException {
        String chain = good(folder, 7L);
        index(folder, 7L, chain);

        ByteArrayOutputStream said = new ByteArrayOutputStream();
        assertEquals(0, Runner.verify(Map.of(Runner.VERIFY, folder.toString()),
                new PrintStream(said, true, StandardCharsets.UTF_8)));
        assertTrue(said.toString(StandardCharsets.UTF_8).contains("1 of 1 logs"),
                said.toString(StandardCharsets.UTF_8));
        // The cost, printed by the command, because the methodology page publishes it as a number
        // and a number nobody measured is a number nobody should quote.
        assertTrue(said.toString(StandardCharsets.UTF_8).contains(" ms"),
                said.toString(StandardCharsets.UTF_8));

        index(folder, 7L, "f".repeat(64));
        said.reset();
        assertEquals(1, Runner.verify(Map.of(Runner.VERIFY, folder.toString()),
                new PrintStream(said, true, StandardCharsets.UTF_8)));
    }
}
