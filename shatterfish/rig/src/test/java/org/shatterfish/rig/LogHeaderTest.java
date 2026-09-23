package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader the Rig guards FR-11 with (story 3.3).
 *
 * <p>Every log this reads is built by the writer that writes the real ones, through
 * {@code RunLogJson}. That is the point: the first version of these checks typed the oracle key by
 * hand on both sides, so a writer that renamed the field would have made the Rig's half of FR-11 a
 * no-op with every test still green. The key is the writer's now, and the reader has to find it.
 *
 * <p>The other thing held here is that this reader is no weaker than {@code LogText}, the harness's
 * own reader of the same format. A guard on an artifact that can be defeated by a trick an existing
 * reader already refuses is not a second check; it is a first check with a hole.
 */
class LogHeaderTest {

    private static final String ZERO = "0".repeat(64);

    private static final long SEED = 12_345L;

    private static RunLog.Header header(boolean oracle) {
        return new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), 7L, 20_000, 3, 2, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", oracle, "a laptop",
                "2026-09-22T12:00:00Z");
    }

    private static RunLog.Wait served(long k) {
        return new RunLog.Wait(k, 1_000, 1, 0, ZERO, Map.of("map", ZERO), new Action.Step(1), true,
                RunLog.BOT, null, "", List.of(), 1);
    }

    private static RunLog.End end() {
        return new RunLog.End(9, new RunLog.Outcome(false, false, 12, 3, 4_000, "DEATH", 0), true);
    }

    /** A real log, written by the real writer, of the records given. */
    private static Path log(Path folder, String name, RunLog... records) throws IOException {
        StringBuilder text = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            text.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Path file = folder.resolve(name);
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
        return file;
    }

    // -------------------------------------------------------------------------- the oracle flag

    @Test
    @DisplayName("the oracle flag the writer writes is the oracle flag this reader finds")
    void the_oracle_flag_is_the_writers(@TempDir Path folder) throws IOException {
        // Both sides from the code: the header is a real `RunLog.Header`, rendered by the real
        // renderer. Rename the field in `RunLogJson` and this fails, which is what makes the Rig's
        // half of FR-11 a check rather than a hope.
        assertTrue(LogHeader.of(log(folder, "oracle.jsonl", header(true), end())).oracle(),
                "a Run whose header claims the oracle");
        assertFalse(LogHeader.of(log(folder, "fair.jsonl", header(false), end())).oracle(),
                "and a Run whose header does not");
    }

    @Test
    @DisplayName("a header that claims the oracle twice over is still caught, and a repeated key is refused")
    void a_repeated_key_is_refused(@TempDir Path folder) throws IOException {
        // `LogText` in the harness refuses a duplicated key, saying that a reader taking the other
        // one would read a different Run. This reader used to return the first match and stop, so
        // a hand-made header with `oracle` twice read as fair — a guard on the artifact defeated
        // by a trick the reader beside it already refuses.
        Path file = folder.resolve("twice.jsonl");
        String line = RunLogJson.line("", header(false));
        Files.writeString(file, line.substring(0, line.length() - 1) + ",\"oracle\":true}\n",
                StandardCharsets.UTF_8);

        LogHeader.Read read = LogHeader.of(file);

        assertFalse(read.readable(), "a line with a key written twice is not readable");
        assertTrue(read.unreadable().contains("twice"), read.unreadable());
    }

    @Test
    @DisplayName("two Runs in one file are not one Run")
    void a_second_header_is_refused(@TempDir Path folder) throws IOException {
        // A fair Run followed by an oracle Run used to read as one fair, complete Run: the reader
        // looked at line 0 and took the last line's chain. `LogText.whole` refuses a second header.
        Path file = log(folder, "two.jsonl", header(false), end(), header(true), end());

        LogHeader.Read read = LogHeader.of(file);

        assertFalse(read.readable(), "a file holding two Runs is not a Run's log");
        assertTrue(read.unreadable().contains("one header"), read.unreadable());
    }

    @Test
    @DisplayName("a header whose last byte never arrived is still asked whether it claimed the oracle")
    void a_partial_header_still_answers(@TempDir Path folder) throws IOException {
        // What a killed writer leaves. The reader used to report "no whole lines" and, with it,
        // `oracle == false` by construction — so an oracle Run killed early was indistinguishable
        // from a fair one, and ADR-0012 would have scored its pair as a tie.
        Path file = folder.resolve("cut.jsonl");
        Files.writeString(file, RunLogJson.line("", header(true)), StandardCharsets.UTF_8);

        LogHeader.Read read = LogHeader.of(file);

        assertTrue(read.present(), "the file is there");
        assertTrue(read.oracle(), "and it says what it claimed");
        assertFalse(read.complete(), "though the Run did not finish");

        // A fair one cut the same way is reported unreadable rather than quietly counted fair.
        Path fair = folder.resolve("cut-fair.jsonl");
        Files.writeString(fair, RunLogJson.line("", header(false)), StandardCharsets.UTF_8);
        assertFalse(LogHeader.of(fair).readable(), "nothing in a headerless log can be believed");
    }

    // ---------------------------------------------------------------- what the reader reports

    @Test
    @DisplayName("a log the reader cannot make sense of is reported, never thrown")
    void a_bad_log_is_reported(@TempDir Path folder) throws IOException {
        // One corrupt byte in one Run of five hundred must not end the invocation: the parent reads
        // these on worker threads, and an exception there used to take the whole set with it.
        for (String bad : List.of("not json\n", "{}\n", "{\"t\":\"wait\"}\n", "{\n", "{\"t\":}\n")) {
            Path file = folder.resolve("bad-" + bad.hashCode() + ".jsonl");
            Files.writeString(file, bad, StandardCharsets.UTF_8);
            LogHeader.Read read = LogHeader.of(file);
            assertTrue(read.present(), bad);
            assertFalse(read.readable(), "this is not a Run log: " + bad);
        }
        assertEquals(LogHeader.Read.MISSING, LogHeader.of(folder.resolve("nothing.jsonl")),
                "a Run that left no log at all");
    }

    @Test
    @DisplayName("a log with carriage returns is read, because that is not a reason to be unable to check it")
    void carriage_returns_are_survivable(@TempDir Path folder) throws IOException {
        // Fetched over HTTP, or checked out with autocrlf. The format says line feeds only, and a
        // reader that dies on the violation cannot report the one thing it is there to report.
        Path file = folder.resolve("crlf.jsonl");
        String text = RunLogJson.line("", header(true)) + "\r\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);

        LogHeader.Read read = LogHeader.of(file);

        assertTrue(read.readable(), read.unreadable());
        assertTrue(read.oracle(), "and the flag is still found");
    }

    @Test
    @DisplayName("the reader counts waits, not records: a Prompt is not a wait")
    void waits_are_waits(@TempDir Path folder) throws IOException {
        // The published throughput is waits per second. The first draft of this counted every
        // record but the header and the end, so a Run that met Prompts reported more waits than it
        // served — and the only assertion on the number was that it was above zero.
        Path file = log(folder, "counted.jsonl", header(false), served(1),
                new RunLog.Prompt(2, org.shatterfish.api.PromptKind.SUBCLASS, new Action.AnswerPrompt(0)),
                served(2), new RunLog.Mode(3, "PAUSED", "fast"), served(3), end());

        assertEquals(3, LogHeader.of(file).waits(), "three waits among seven records");
        assertEquals(7, LogHeader.of(file).lines());
    }

    @Test
    @DisplayName("a finished log yields the Run's own cause and its final chain")
    void the_ending_is_the_runs_own(@TempDir Path folder) throws IOException {
        Path file = log(folder, "ended.jsonl", header(false), served(1), end());

        LogHeader.Read read = LogHeader.of(file);

        assertTrue(read.complete(), "it ended");
        assertEquals("DEATH", read.cause(), "and the index says how, not merely that it did");
        assertEquals(RunLogJson.chain(RunLogJson.chain(RunLogJson.chain("", header(false)), served(1)), end()),
                read.chain(), "the chain the last line carries");
        assertEquals("v4.0.0-WARRIOR-0-" + SeedSet.code(SEED) + "-0000000000000007-random", read.runId(),
                "and the id the header's own fields give");
    }
}
