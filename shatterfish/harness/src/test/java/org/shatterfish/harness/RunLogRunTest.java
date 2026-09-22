package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.agent.RunOutcome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a real Run's log says about the Run (story 3.2).
 *
 * <p>{@link ChainRecomputeTest} holds the chain; this holds the contents. The distinction matters
 * because a log whose every hash is a constant chains perfectly, verifies perfectly, and is
 * worthless — the reviews found that nothing compared a wait record's Observation hash with the
 * Observation the decider was handed, so a stubbed value would have passed the whole suite.
 *
 * <p>It also holds the two properties the artifact exists for and nothing asserted: that the same
 * tuple twice gives the same chain, and that a logged Run is the same Run as an unlogged one.
 */
class RunLogRunTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final int CAP = 150;

    private static final String ZERO = "0".repeat(40);

    private static RunLoop.Logging logging(Path folder, String brain) {
        return new RunLoop.Logging(folder, ZERO, new RunLog.Brain(brain, ZERO, "0".repeat(64)), "", "test");
    }

    private static Path file(Path folder, String brain, long seed, long salt) {
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0", org.shatterfish.api.HeroClass.WARRIOR,
                0, SeedSet.code(seed), salt, brain)));
    }

    private static List<String> play(Path folder, String brain, Decider agent) throws IOException {
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, agent, CAP, logging(folder, brain));
        return LogText.lines(file(folder, brain, SEED, SALT)).whole();
    }

    // ------------------------------------------------------------------ the artifact's own point

    @Test
    @DisplayName("the same tuple played twice writes the same chain, which is what a published chain means")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void one_tuple_one_chain(@TempDir Path first, @TempDir Path second) throws IOException {
        List<String> once = play(first, "random", new RandomAgent(7L));
        List<String> twice = play(second, "random", new RandomAgent(7L));

        assertEquals(chain(once), chain(twice),
                "two Runs of one tuple chained differently, so a published chain says nothing");
        assertEquals(once.size(), twice.size(), "and the same number of records");
        // The bytes differ only where the chain says they may: the clock and the machine.
        for (int i = 0; i < once.size(); i++) {
            assertEquals(LogText.chained(once.get(i)), LogText.chained(twice.get(i)),
                    "line " + (i + 1) + " differs in a field the chain covers");
        }
    }

    private static String chain(List<String> lines) {
        return LogText.string(lines.get(lines.size() - 1), "chain");
    }

    @Test
    @DisplayName("a logged Run is the same Run as an unlogged one, which is why the rig may publish logged numbers")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void logging_changes_nothing(@TempDir Path folder) {
        RunOutcome unlogged = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), CAP);
        RunOutcome logged = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), CAP,
                logging(folder, "random"));

        assertEquals(unlogged.cause(), logged.cause(), "the same ending");
        assertEquals(unlogged.depth(), logged.depth(), "the same floor");
        assertEquals(unlogged.turns(), logged.turns(), "the same turns");
        assertEquals(unlogged.waits(), logged.waits(), "the same waits");
        assertEquals(unlogged.applied(), logged.applied(), "the same Actions applied");
        assertEquals(unlogged.refused(), logged.refused(), "and the same refused");
    }

    // ------------------------------------------------------------- what a wait record is made of

    @Test
    @DisplayName("a wait's hashes are the Observation's own, not a value that merely looks like one")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_hashes_are_the_observations(@TempDir Path folder) throws IOException {
        List<String> lines = play(folder, "random", new RandomAgent(7L));
        TreeSet<String> hashes = new TreeSet<>();
        int waits = 0;
        for (String line : lines) {
            if (!"wait".equals(LogText.string(line, "t"))) {
                continue;
            }
            waits++;
            hashes.add(LogText.string(line, "obs"));
            // Every section the codec encodes, and no other: a stub would have one key or none.
            assertEquals(new TreeSet<>(ObservationCodec.SECTIONS), sections(line),
                    "a wait records every section's hash");
        }
        assertTrue(waits > 10, "a Run of " + CAP + " turns serves waits: " + waits);
        assertTrue(hashes.size() > 1,
                "every wait recorded the same Observation hash, so it is a constant and not an Observation");
    }

    private static TreeSet<String> sections(String line) {
        String object = LogText.value(line, "sections");
        TreeSet<String> keys = new TreeSet<>();
        for (String pair : object.substring(1, object.length() - 1).split(",")) {
            keys.add(pair.substring(1, pair.indexOf('"', 1)));
        }
        return keys;
    }

    @Test
    @DisplayName("a wait says whether the Action was applied, and a refused one is not logged as taken")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_refused_action_says_so(@TempDir Path folder) throws IOException {
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), CAP,
                logging(folder, "random"));
        List<String> lines = LogText.lines(file(folder, "random", SEED, SALT)).whole();

        long applied = 0;
        long refused = 0;
        for (String line : lines) {
            if ("wait".equals(LogText.string(line, "t"))) {
                if ("true".equals(LogText.value(line, "applied"))) {
                    applied++;
                } else {
                    refused++;
                }
            }
        }
        assertEquals(outcome.applied(), applied, "the log counts the applied Actions the Run did");
        assertEquals(outcome.refused(), refused, "and the refused ones, which used to look identical");
        assertTrue(refused > 0, "a random agent is refused sometimes, or this test proves nothing");
    }

    @Test
    @DisplayName("every wait says the bot took it, and the end record says what the Run did")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_end_record_is_the_outcome(@TempDir Path folder) throws IOException {
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), CAP,
                logging(folder, "random"));
        List<String> lines = LogText.lines(file(folder, "random", SEED, SALT)).whole();
        String end = lines.get(lines.size() - 1);

        for (String line : lines) {
            if ("wait".equals(LogText.string(line, "t"))) {
                assertEquals("bot", LogText.string(line, "actor"), "a rig Run is the bot's");
            }
        }
        assertEquals("end", LogText.string(end, "t"));
        String held = LogText.value(end, "outcome");
        assertTrue(held.contains("\"cause\":\"" + outcome.cause().name() + "\""), held);
        assertTrue(held.contains("\"depth\":" + outcome.depth()), held);
        // The turn is thousandths of the same quantity the outcome counts in whole turns.
        long turns = Long.parseLong(held.replaceAll(".*\"turns\":(\\d+).*", "$1"));
        assertEquals(outcome.turns(), turns / 1000, "the end record's turns, in thousandths");
        assertEquals("true", LogText.value(end, "verifiable"),
                "a Run that reached the cap can be replayed");
    }

    @Test
    @DisplayName("the last wait's index is the end record's, so `k` means one thing in one file")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_end_is_keyed_by_the_drivers_index(@TempDir Path folder) throws IOException {
        List<String> lines = play(folder, "random", new RandomAgent(7L));
        long last = 0;
        for (String line : lines) {
            if ("wait".equals(LogText.string(line, "t"))) {
                last = Long.parseLong(LogText.value(line, "k"));
            }
        }
        long end = Long.parseLong(LogText.value(lines.get(lines.size() - 1), "k"));
        assertTrue(end >= last, "the end is keyed at or after the last wait, not before it: "
                + end + " after " + last);
    }

    // ---------------------------------------------------------------- an ending that is not tidy

    @Test
    @DisplayName("a Run that ended badly writes its ending too, so it is a loss and not a killed Run")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void an_ending_that_is_not_ordinary_still_ends(@TempDir Path folder) throws IOException {
        // A decider that asks for something the screen never offers. The executor refuses it, the
        // game is never handed anything, and the driver stalls -- an ending that is real, is not
        // ordinary, and used to be one of the paths that could have skipped the end record.
        //
        // The loop has one exit now, so no ending can skip it; this is that property observed on
        // the one non-ordinary ending a test can reach. `REFUSED` needs sixty-four consecutive
        // refusals that each hand the wait over, which only `NO_SELECTOR` does, so it is not
        // reachable from a decider -- which is exactly why it was the return that got missed.
        Decider stubborn = observation -> new Action.OpenChest(0);
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, stubborn, CAP,
                logging(folder, "stubborn"));

        assertFalse(outcome.ordinary(), "this Run did not end the way a Run is meant to: " + outcome);
        LogText.Lines lines = LogText.lines(file(folder, "stubborn", SEED, SALT));
        assertEquals(0, LogText.firstBrokenLine(lines), "and its chain verifies");
        assertTrue(LogText.whole(lines), "a Run that ended for a known reason is a whole log");
        String end = lines.whole().get(lines.whole().size() - 1);
        assertTrue(LogText.value(end, "outcome").contains("\"cause\":\"" + outcome.cause().name() + "\""),
                "and the log says which reason: " + LogText.value(end, "outcome"));
        assertEquals("false", LogText.value(end, "verifiable"),
                "the harness could not follow the game, so a Replay cannot reproduce it");
        for (String line : lines.whole()) {
            if ("wait".equals(LogText.string(line, "t"))) {
                assertEquals("false", LogText.value(line, "applied"), "every Action was refused");
            }
        }
    }

    @Test
    @DisplayName("a line is on disk before the next record exists, which is what a killed Run relies on")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void each_line_is_flushed_before_the_next(@TempDir Path folder) throws IOException {
        // Read the file from inside the Run. Nothing else can observe the per-line flush without
        // killing a JVM, and the flush is the whole mechanism behind "a killed Run leaves whole
        // lines" -- reintroducing a buffer would have passed every other test in this story.
        Path file = file(folder, "random", SEED, SALT);
        List<Integer> seen = new ArrayList<>();
        RandomAgent inner = new RandomAgent(7L);
        Decider watching = observation -> {
            if (seen.size() < 3) {
                try {
                    seen.add(LogText.lines(Files.readAllBytes(file)).whole().size());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            return inner.decide(observation);
        };
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, watching, CAP, logging(folder, "random"));

        assertEquals(3, seen.size(), "the decider looked three times");
        assertTrue(seen.get(0) >= 1, "the header was on disk before the first decision: " + seen);
        assertTrue(seen.get(2) > seen.get(0), "and records kept arriving while the Run ran: " + seen);
    }

    @Test
    @DisplayName("a prompt in front of the Run is recorded beside the wait that answered it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_prompt_is_recorded(@TempDir Path folder) throws IOException {
        // The Prompt is put up deliberately rather than waited for. A random Warrior dies without
        // meeting one, so an end-to-end test that hoped for a Prompt would have been a test that
        // passed by not looking -- and deleting the line that writes a prompt record failed no test
        // at all before this one.
        RandomAgent inner = new RandomAgent(13L);
        int[] raised = {0};
        Decider answers = observation -> {
            if (observation.header().prompt() != org.shatterfish.api.PromptKind.NONE) {
                return new Action.AnswerPrompt(0);
            }
            if (raised[0]++ == 2) {
                com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.show(
                        new com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions(
                                "A question", "Which way?", "Left", "Right") {
                            @Override
                            protected void onSelect(int index) {
                            }
                        });
            }
            return inner.decide(observation);
        };
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, answers, CAP, logging(folder, "answerer"));
        List<String> lines = LogText.lines(file(folder, "answerer", SEED, SALT)).whole();

        List<String> prompts = lines.stream().filter(l -> "prompt".equals(LogText.string(l, "t"))).toList();
        assertFalse(prompts.isEmpty(), "the Run was shown a window and answered it");
        for (String prompt : prompts) {
            String k = LogText.value(prompt, "k");
            String beside = lines.stream()
                    .filter(l -> "wait".equals(LogText.string(l, "t")) && k.equals(LogText.value(l, "k")))
                    .findFirst().orElseThrow(() -> new AssertionError("no wait beside prompt " + k));
            assertEquals(LogText.value(beside, "action"), LogText.value(prompt, "answer"),
                    "the prompt record carries the option the wait took");
            assertNotEquals("NONE", LogText.string(prompt, "prompt"), "and names the Prompt's kind");
        }
    }
}
