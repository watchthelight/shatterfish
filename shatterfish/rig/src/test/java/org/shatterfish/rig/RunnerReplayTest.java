package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --replay}: one log, played again, and the two chains compared (story 3.4).
 *
 * <p>This runs the command. Story 3.3 published a rig invocation that could not have worked — a
 * required flag was missing from every example — and the story's own Verification section said it
 * had been run. A command nobody executed is a claim, and this file is here so that this one is
 * not.
 *
 * <p>One log rather than a folder, because a Replay is a Run and AD-6 gives a Run its own process:
 * the game's state is static and process-wide, so a command that replayed a folder in one JVM would
 * be measuring the order the logs went in.
 */
class RunnerReplayTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final int CAP = 120;

    private static final String COMMIT = "0".repeat(40);

    /**
     * A Run, played and logged, through the triple rather than the game's own hero class.
     *
     * <p>The Rig cannot see {@code core}: the game's {@code HeroClass} is not on this module's
     * compile classpath, which is the boundary that keeps the Rig from knowing anything about the
     * game it starts. A triple is the api's own value and is what a Run is identified by anyway.
     */
    private static Path play(Path folder) {
        SeedSet.Entry triple = new SeedSet.Entry(SEED, org.shatterfish.api.HeroClass.WARRIOR, 0,
                SeedSet.code(SEED));
        new RunLoop().playTriple(triple, SALT, new RandomAgent(11L), CAP,
                new RunLoop.Logging(folder, COMMIT,
                        new RunLog.Brain("random", COMMIT, "0".repeat(64)), "", "test"));
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SeedSet.code(SEED), SALT, "random")));
    }

    private static String said(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the command replays a log, answers 0, and prints the two chains it compared")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_command_replays_a_run(@TempDir Path folder, @TempDir Path into) {
        Path log = play(folder);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.REPLAY, log.toString());
        arguments.put(Runner.OUT, into.resolve("replay").toString());
        int status = Runner.replay(arguments, new PrintStream(out, true, StandardCharsets.UTF_8));

        assertEquals(0, status, said(out));
        assertTrue(said(out).startsWith("reproduced "), said(out));
        assertTrue(said(out).contains("waits verified"), said(out));
        // Both chains, printed, because "reproduced" on its own is a word and these are the values
        // a reader can check against the file and against a Results page.
        String chain = RunIndexes.chainOf(log);
        assertEquals(2, count(said(out), chain), "the log's chain and this build's are the same value: "
                + said(out));
        // And the Replay's own log is on disk beside the original, because the comparison is
        // between two files and throwing one away would leave the answer unexaminable.
        assertTrue(Files.isRegularFile(into.resolve("replay").resolve(log.getFileName())),
                "the Replay wrote its own log");
    }

    @Test
    @DisplayName("the command answers 4 for a log this build will not replay, and says what differs")
    void the_command_refuses_a_log_it_cannot_replay(@TempDir Path folder, @TempDir Path into)
            throws IOException {
        // A log whose bytes were changed. No Run is played to find this out, which is why this
        // case costs nothing and the one above costs a Run.
        Path log = folder.resolve("edited.jsonl");
        Files.writeString(log, "{\"chain\":\"" + "0".repeat(64) + "\",\"t\":\"header\"}\n",
                StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.REPLAY, log.toString());
        arguments.put(Runner.OUT, into.resolve("replay").toString());
        int status = Runner.replay(arguments, new PrintStream(out, true, StandardCharsets.UTF_8));

        assertEquals(4, status, said(out));
        assertNotEquals("", said(out).strip(), "a refusal says what is wrong with the file");
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    /** The chain a log ends on, read the way the Rig reads it. */
    private static final class RunIndexes {

        private RunIndexes() {
        }

        static String chainOf(Path log) {
            LogHeader.Read read = LogHeader.of(log);
            assertTrue(read.readable(), read.unreadable());
            return read.chain();
        }
    }
}
