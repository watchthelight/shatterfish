package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Codex;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Run log, checked the way a stranger would check one (story 3.2, ADR-0011).
 *
 * <p>The claim the chain makes is that a published number can be checked without trusting the tool
 * that produced it. A test that recomputed each chain by calling the writer's own renderer would
 * not check that claim — it would check that the writer agrees with itself, which it does whether
 * it is right or wrong. So every chain here is recomputed by {@link LogText}: the file's bytes, the
 * unchained keys stripped by name, and the JDK's own {@code MessageDigest}.
 */
class ChainRecomputeTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    /**
     * A short cap rather than the Run's own: what is under test is the record a Run leaves, and two
     * hundred turns leave a header, a few hundred waits and an end, which is every shape the chain
     * has to hold. A Run to the amulet would take minutes to say the same thing.
     */
    private static final int CAP = 200;

    /** A Run played to its ending, with its log written beside it. */
    private static Path play(Path folder, String brain) {
        RunLog.Brain who = new RunLog.Brain(brain, "0".repeat(40), "0".repeat(64));
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L),
                CAP, new RunLoop.Logging(folder, "0".repeat(40), who, "", "test"));
        assertTrue(outcome.ordinary(), "a Run ends in death, a Win or the cap: " + outcome);
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0", org.shatterfish.api.HeroClass.WARRIOR,
                0, SeedSet.code(SEED), SALT, brain)));
    }

    @Test
    @DisplayName("every chain in a finished log is the one the file's own bytes give")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_chain_recomputes_from_the_file_alone(@TempDir Path folder) throws IOException {
        Path file = play(folder, "random");
        LogText.Lines lines = LogText.lines(file);

        assertTrue(lines.complete(), "a Run that ended wrote whole lines, not " + lines.partial());
        assertTrue(lines.whole().size() > 2, "a header, some waits and an end: " + lines.whole().size());
        assertEquals(0, LogText.firstBrokenLine(lines), "every chain is the one the bytes give");

        // The records are the ones ADR-0011 names, in the order it names them.
        assertEquals("header", LogText.string(lines.whole().get(0), "t"));
        assertEquals("end", LogText.string(lines.whole().get(lines.whole().size() - 1), "t"));
        assertNull(LogText.value(lines.whole().get(0), "prev"), "nothing comes before the header");

        // Every record but the header is keyed by its wait index, and the waits count up.
        long previous = -1;
        long waits = 0;
        for (String line : lines.whole().subList(1, lines.whole().size())) {
            String k = LogText.value(line, "k");
            assertTrue(k != null, "a record is keyed by its wait index: " + line);
            if ("wait".equals(LogText.string(line, "t"))) {
                long at = Long.parseLong(k);
                assertTrue(at > previous, "the wait indexes count up: " + at + " after " + previous);
                previous = at;
                waits++;
            }
        }
        assertTrue(waits > 0, "a Run served waits");
    }

    @Test
    @DisplayName("the header says which build, which tuple and which versions decided what the Run saw")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_header_says_what_a_replay_needs(@TempDir Path folder) throws IOException {
        String header = LogText.lines(play(folder, "random")).whole().get(0);

        assertEquals(String.valueOf(RunLog.VERSION), LogText.value(header, "v"));
        assertEquals("v4.0.0", LogText.string(header, "tag"));
        assertEquals("WARRIOR", LogText.string(header, "class"));
        assertEquals(String.valueOf(SEED), LogText.value(header, "seed"));
        assertEquals(SeedSet.code(SEED), LogText.string(header, "seedcode"),
                "the code beside the number is the one the game reads");
        assertEquals(String.valueOf(SALT), LogText.value(header, "salt"));
        assertEquals(String.valueOf(Profile.VERSION), LogText.value(header, "profile"));
        assertEquals(String.valueOf(ObservationCodec.SCHEMA_VERSION), LogText.value(header, "obsv"));
        assertEquals(String.valueOf(Codex.VERSION), LogText.value(header, "codex"),
                "the Codex version decides what a Brain knows and is not derivable from the tag");
        assertEquals("false", LogText.value(header, "oracle"),
                "a ranked Run is not an oracle Run, and the Rig refuses one that says otherwise");
    }

    @Test
    @DisplayName("one changed byte anywhere breaks the chain, naming the first record that disagrees")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void an_edited_byte_is_caught(@TempDir Path folder) throws IOException {
        Path file = play(folder, "random");
        List<String> lines = LogText.lines(file).whole();

        // A seed nobody played, in the header: the first line, so everything after it breaks too.
        assertEquals(1, broken(lines, 0, "\"seed\":" + SEED, "\"seed\":1"));

        // A depth one deeper than the Run reached, in the middle: the edit is invisible to every
        // line before it and breaks every line from it on, which is what a chain is for.
        int middle = lines.size() / 2;
        String depth = LogText.value(lines.get(middle), "depth");
        assertEquals(middle + 1, broken(lines, middle, "\"depth\":" + depth,
                "\"depth\":" + (Integer.parseInt(depth) + 1)));

        // And the end record's own claim about how the Run went.
        int last = lines.size() - 1;
        assertEquals(last + 1, broken(lines, last, "\"win\":false", "\"win\":true"));
    }

    /** The first line that disagrees once {@code from} has {@code was} replaced by {@code now}. */
    private static int broken(List<String> lines, int at, String was, String now) {
        String edited = lines.get(at);
        assertTrue(edited.contains(was), "line " + (at + 1) + " no longer holds " + was
                + ", so this edit proves nothing: " + edited);
        List<String> copy = new java.util.ArrayList<>(lines);
        copy.set(at, edited.replace(was, now));
        assertNotEquals(lines, copy, "the edit changed nothing");
        return LogText.firstBrokenLine(new LogText.Lines(List.copyOf(copy), ""));
    }

    @Test
    @DisplayName("a chain that was simply rewritten to agree with its own line is still caught")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_rewritten_chain_is_caught(@TempDir Path folder) throws IOException {
        // The obvious attack: change a field, then recompute that one line's chain so the line
        // agrees with itself. The next line's `prev` no longer matches, which is the reason `prev`
        // is written at all.
        List<String> lines = LogText.lines(play(folder, "random")).whole();
        int at = lines.size() / 2;
        String line = lines.get(at);
        String depth = LogText.value(line, "depth");
        String edited = line.replace("\"depth\":" + depth, "\"depth\":" + (Integer.parseInt(depth) + 1));
        String honest = LogText.hex(LogText.sha256(LogText.concat(
                LogText.unhex(LogText.string(edited, "prev")), LogText.utf8(LogText.chained(edited)))));
        edited = edited.replace("\"chain\":\"" + LogText.string(line, "chain") + "\"",
                "\"chain\":\"" + honest + "\"");

        List<String> copy = new java.util.ArrayList<>(lines);
        copy.set(at, edited);
        assertEquals(honest, LogText.string(edited, "chain"),
                "the edited line now agrees with itself, which is what this test is about");
        assertEquals(at + 2, LogText.firstBrokenLine(new LogText.Lines(List.copyOf(copy), "")),
                "so the line after it is where the file stops agreeing");
    }

    @Test
    @DisplayName("two Runs of one triple under one salt write two files, differing only in the Brain")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void a_pair_does_not_collide(@TempDir Path folder) throws IOException {
        Path first = play(folder, "random");
        Path second = play(folder, "other");

        assertNotEquals(first, second, "a pair's two Runs write two files (AD-14)");
        assertTrue(Files.exists(first) && Files.exists(second), "both survive");
        assertTrue(first.getFileName().toString().endsWith("-random.jsonl"), first.toString());
        assertTrue(second.getFileName().toString().endsWith("-other.jsonl"), second.toString());
        assertEquals(0, LogText.firstBrokenLine(LogText.lines(first)));
        assertEquals(0, LogText.firstBrokenLine(LogText.lines(second)));
    }

    @Test
    @DisplayName("the log is plain text a person can read without a tool")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_log_reads_as_text(@TempDir Path folder) throws IOException {
        String text = Files.readString(play(folder, "random"), StandardCharsets.UTF_8);

        assertTrue(text.endsWith("\n"), "every record is a whole line, the last one included");
        assertTrue(!text.contains("\r"), "line feeds only, so the bytes are the same on both platforms");
        assertTrue(text.contains("\"t\":\"wait\""), "a person greps for a kind and finds it");
        assertTrue(!text.contains("  "), "no whitespace inside a value: canonical means canonical");
    }
}
