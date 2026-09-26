package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every record kind, through the reader that shares nothing with the writer (story 3.2).
 *
 * <p>ADR-0011 promises that the four kinds nothing writes yet — {@code mode}, {@code shadow},
 * {@code boundary} and {@code unsupported} — are "defined, rendered and chained now", so that the
 * story which starts writing them adds a caller and not a rule about the format. That promise was
 * only half true: a real Run exercises four kinds, and the tests exercised those four and part of a
 * fifth. {@code shadow} — the one kind whose payload is an object holding arrays of objects, and
 * therefore the only shape that exercises the reader's depth counter at all — had never been
 * rendered, chained or read by anything.
 *
 * <p>So this holds all eight, hand-built, against the property every stranger's checker depends on:
 * <b>take a line, strip the five unchained keys from its text, and what is left is the text the
 * chain was taken over.</b> That is the sentence {@code RunLogJson}'s own javadoc makes, and until
 * now nothing asserted it — the nearest test compared two string lengths, which is true of any
 * implementation including one that returned the same value from both.
 */
class RunLogKindsTest {

    private static final String ZERO = "0".repeat(64);

    private static final String ONE = "1".repeat(64);

    private static final String TWO = "2".repeat(64);

    private static RunLog.Decision decision() {
        return new RunLog.Decision("survive the floor",
                new RunLog.Choice(new Action.Step(41), 8_500, "it is away from the crab"),
                List.of(new RunLog.Choice(new Action.Attack(42), -2_000, "the crab hits harder"),
                        new RunLog.Choice(new Action.PickUp(), 100, "the gold can wait")),
                List.of("threatened", "replanned"), "greedy");
    }

    /** One of each kind, with the optional fields filled in where a kind has any. */
    private static List<RunLog> everyKind() {
        return List.of(
                new RunLog.Header(RunLog.VERSION, "v4.0.0", "abc1234", HeroClass.CLERIC, 3, 99L,
                        SeedSet.code(99L), -1L, 20_000, 3, 2, 8,
                        new RunLog.Brain("greedy", "def5678", ZERO), "H-0001-a-hypothesis@0123456789abcdef", false,
                        "a laptop", "2026-09-22T12:00:00Z"),
                new RunLog.Wait(1, 1_500, 2, 0, ZERO, Map.of("map", ONE, "hero", TWO),
                        new Action.Step(17), true, RunLog.BOT, decision(), ONE, List.of(3, 4, 5), 12),
                new RunLog.Prompt(2, PromptKind.SUBCLASS, new Action.AnswerPrompt(1)),
                new RunLog.Mode(3, "PAUSED", "fast"),
                new RunLog.Shadow(4, decision()),
                new RunLog.Boundary(5, Long.MIN_VALUE, ONE),
                new RunLog.Unsupported(6, "a two-finger swipe"),
                new RunLog.Note(6, "the crab first, {then} the \"gold\", a, b"),
                new RunLog.Shadow(6, decision(), true),
                new RunLog.End(7, new RunLog.Outcome(true, true, 12_345, 26, 640_500, "WIN", 5), true));
    }

    @Test
    @DisplayName("for every kind, a line minus the unchained keys is the text the chain was taken over")
    void stripping_a_line_gives_the_chained_text() {
        String previous = "";
        for (RunLog record : everyKind()) {
            String line = RunLogJson.line(previous, record);

            // The property, read off the bytes by the outside reader and compared with what the
            // writer says it chained. Nothing here calls the renderer to check the renderer: the
            // left side is a text transformation of the published line.
            assertEquals(RunLogJson.chained(record), LogText.chained(line),
                    record.t() + ": the line minus " + LogText.UNCHAINED + " is not the chained text");

            // And the chain on the line is the one the JDK's digest gives over that text.
            assertEquals(LogText.hex(LogText.sha256(LogText.concat(LogText.unhex(previous),
                            LogText.utf8(LogText.chained(line))))),
                    LogText.string(line, "chain"), record.t() + ": the chain on the line");
            previous = LogText.string(line, "chain");
        }
    }

    @Test
    @DisplayName("a file of one of every kind verifies end to end, and each kind survives the reader")
    void a_file_of_every_kind_verifies() {
        StringBuilder file = new StringBuilder();
        String previous = "";
        for (RunLog record : everyKind()) {
            file.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        LogText.Lines lines = LogText.lines(file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(lines.complete(), "whole lines");
        assertEquals(10, lines.whole().size(), "one of each kind, and a skipped shadow");
        // Story 5.9: the note and a skipped shadow read back as they were written, as every other kind does.
        assertEquals(everyKind(), org.shatterfish.harness.log.RunLogReader.of(file.toString()).records());
        assertEquals(0, LogText.firstBrokenLine(lines), "a file of every kind verifies");
        assertEquals(List.of("header", "wait", "prompt", "mode", "shadow", "boundary", "unsupported", "note", "shadow",
                        "end"),
                lines.whole().stream().map(l -> LogText.string(l, "t")).toList());
    }

    @Test
    @DisplayName("the wait's optional fields and the shadow's nested arrays render as the format says")
    void the_unwritten_shapes_are_the_ones_the_format_names() {
        // Hand-written, not pasted from the renderer. The shadow is the only record whose payload
        // is an object holding arrays of objects, so it is the only one that exercises the reader's
        // depth counter -- and a value holding a brace or a comma inside a string is what that
        // counter exists for.
        assertEquals("{\"decision\":{\"alternatives\":["
                        + "{\"action\":{\"cell\":42,\"kind\":\"Attack\"},\"score\":-2000,\"why\":\"the crab hits harder\"},"
                        + "{\"action\":{\"kind\":\"PickUp\"},\"score\":100,\"why\":\"the gold can wait\"}],"
                        + "\"chosen\":{\"action\":{\"cell\":41,\"kind\":\"Step\"},\"score\":8500,"
                        + "\"why\":\"it is away from the crab\"},"
                        + "\"flags\":[\"threatened\",\"replanned\"],\"goal\":\"survive the floor\","
                        + "\"policy\":\"greedy\"},\"k\":4,\"t\":\"shadow\"}",
                RunLogJson.canonical(new RunLog.Shadow(4, decision())));

        // A boundary's own chain value is spelt `chainAt`, so that stripping the envelope's `chain`
        // from a line does not take the record's payload with it. That spelling is the reason this
        // record survives the strip, and this is the test of it.
        RunLog.Boundary boundary = new RunLog.Boundary(5, Long.MIN_VALUE, ONE);
        String line = RunLogJson.line(ZERO, boundary);
        assertTrue(LogText.chained(line).contains("\"chainAt\":\"" + ONE + "\""),
                "the record's own chain value survives the strip: " + LogText.chained(line));
        assertFalse(LogText.chained(line).contains("\"chain\":"), LogText.chained(line));
        assertEquals("8000000000000000", LogText.string(line, "salt"),
                "the most negative salt there is, written the way the file name writes it");
    }

    @Test
    @DisplayName("a repeated key is refused, because two readers of one verified line would disagree")
    void a_key_written_twice_is_refused() {
        // The writer cannot produce one; a forger can. Every JSON parser downstream takes the last
        // value, so a line carrying `turn` twice would verify under a chain that covers both and
        // mean one thing to the chain and another to the Replay.
        String line = RunLogJson.line("", new RunLog.Mode(3, "PAUSED", "fast"));
        String twice = line.substring(0, line.length() - 1) + ",\"k\":9}";

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LogText.keys(twice));
        assertTrue(refused.getMessage().contains("written twice"), refused.getMessage());
        assertEquals(1, LogText.firstBrokenLine(new LogText.Lines(List.of(twice), "")),
                "and the file's first broken line is named rather than thrown out of");
    }

    @Test
    @DisplayName("the reader names a line rather than throwing, whatever is wrong with it")
    void a_malformed_line_is_named_not_thrown() {
        String good = RunLogJson.line("", new RunLog.Mode(3, "PAUSED", "fast"));

        for (String bad : List.of("", "not json at all", "{", "{\"t\":\"mode\"", "{\"t\":}",
                good.replace("\"chain\":\"", "\"chain\":\"z"), good + ",")) {
            assertEquals(1, LogText.firstBrokenLine(new LogText.Lines(List.of(bad), "")),
                    "a line the writer would never produce is line 1's problem: " + bad);
        }

        // A log with carriage returns -- fetched over HTTP, or checked out with autocrlf -- is a
        // rule violation the page states, and the reader has to say which line rather than die.
        assertEquals(1, LogText.firstBrokenLine(LogText.lines(
                        (good + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                "a carriage return is the line's problem, named");
    }

    @Test
    @DisplayName("an empty file is not a verified file, and a prefix is not a whole log")
    void empty_is_not_the_same_answer_as_fine() {
        assertEquals(LogText.NOTHING_THERE, LogText.firstBrokenLine(LogText.lines(new byte[0])),
                "a zero-length file has nothing to verify, which is not the same as verifying");
        assertFalse(LogText.whole(LogText.lines(new byte[0])));

        StringBuilder file = new StringBuilder();
        String previous = "";
        for (RunLog record : everyKind()) {
            file.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        byte[] bytes = file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(LogText.whole(LogText.lines(bytes)), "a header through an end is a whole log");

        // Every prefix of a valid log is a valid log -- which is why a chain alone cannot tell a
        // killed Run from a truncated one, and why the Rig needs this second question.
        String withoutTheEnd = file.substring(0, file.lastIndexOf("\n", file.length() - 2) + 1);
        LogText.Lines cut = LogText.lines(withoutTheEnd.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, LogText.firstBrokenLine(cut), "the prefix still verifies");
        assertFalse(LogText.whole(cut), "and it is still not a whole Run's log");
    }
}
