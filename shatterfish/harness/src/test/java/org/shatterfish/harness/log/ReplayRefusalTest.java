package org.shatterfish.harness.log;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Replay refuses, and what it reports instead (story 3.4, FR-24).
 *
 * <p>The round-trip test says a Replay reproduces a Run. This one says what happens when it cannot,
 * which is the half that decides whether the tool is worth anything: a checker that only ever
 * agrees has not been shown to disagree.
 *
 * <p>Four of these forge a log that verifies. That is deliberate and it is not cheating — the chain
 * rules are published in full, so anybody can write a log that chains perfectly, and the
 * methodology page says so. It is exactly the case a Replay exists for: the chain proves the file
 * was not edited after it was written, and only playing the Run again proves the file describes
 * something this build actually does.
 */
class ReplayRefusalTest {

    private static final long SEED = 0xC0FFEEL;

    private static final long SALT = 0x5A17_5A17L;

    private static final int CAP = 150;

    private static final String COMMIT = "0".repeat(40);

    private static final String ZERO = "0".repeat(64);

    // ------------------------------------------------------------------ the refusals, without a Run

    /** A header this build would accept, which each case below then spoils in one field. */
    private static RunLog.Header good() {
        HeadlessBoot.ensure();
        return new RunLog.Header(RunLog.VERSION, HeadlessBoot.pinnedTag(), "abc1234",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SEED, SeedSet.code(SEED), SALT, CAP,
                Profile.VERSION, ObservationCodec.SCHEMA_VERSION, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false, "a laptop",
                "2026-09-22T12:00:00Z");
    }

    @Test
    @DisplayName("a header this build agrees with is not refused")
    void a_matching_header_is_replayable() {
        assertNull(Replay.refusal(good()), "nothing in this header means something else here");
    }

    @Test
    @DisplayName("a log from a build that meant something else by a Run is refused, not compared")
    void the_four_versions_are_refused_before_anything_is_played() {
        RunLog.Header h = good();

        // The Observation schema. A Replay compares Observation hashes, so two builds that hash
        // different fields would disagree at wait 0 and say nothing about either of them.
        assertRefused(new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(), h.challenges(),
                        h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(),
                        ObservationCodec.SCHEMA_VERSION + 1, h.codex(), h.brain(), h.registration(),
                        h.oracle(), h.machine(), h.started()),
                "Observation schema version");

        // The Profile. It decides what is unlocked, and a Run with the Mage unlocked is not the
        // Run a build without her would play from the same seed.
        assertRefused(new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(), h.challenges(),
                        h.seed(), h.seedCode(), h.salt(), h.cap(), Profile.VERSION + 1, h.obsv(),
                        h.codex(), h.brain(), h.registration(), h.oracle(), h.machine(), h.started()),
                "Profile version");

        // The upstream tag. It is the game's own rules; nothing else needs saying.
        assertRefused(new RunLog.Header(h.v(), "v0.0.0", h.commit(), h.heroClass(), h.challenges(),
                        h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(), h.obsv(), h.codex(),
                        h.brain(), h.registration(), h.oracle(), h.machine(), h.started()),
                "upstream tag");
    }

    @Test
    @DisplayName("a log schema this build does not write is refused by the reader, before the Replay sees it")
    void a_later_schema_is_refused(@TempDir Path folder) throws IOException {
        // The record itself refuses a version this build does not write, so a log from a later
        // schema cannot even be constructed here -- which is the refusal, one layer further out
        // than Replay.refusal, and the reason that check is a belt to this file's braces.
        Path file = folder.resolve("later.jsonl");
        RunLog.Header h = good();
        String line = RunLogJson.line("", h);
        Files.writeString(file, line.replace(",\"v\":" + RunLog.VERSION, ",\"v\":" + (RunLog.VERSION + 9))
                + "\n", StandardCharsets.UTF_8);

        // It no longer chains, because `v` is chained -- so this is refused as a broken file rather
        // than as a version, and either refusal is honest. What must not happen is a comparison.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, folder.resolve("out"), "test"));
        assertTrue(refused.getMessage().contains("does not verify"), refused.getMessage());
    }

    @Test
    @DisplayName("a log that claims the oracle is refused, and no Run is started to find that out")
    void an_oracle_log_is_refused(@TempDir Path folder) throws IOException {
        // The blocking finding of this story's fairness review. `Replay.of` used to pass the log's
        // own oracle flag into the Run it started, which made it the only production caller in the
        // repository that could set that flag at all -- out of a file named on the Rig's command
        // line. The rules are published, so writing the file is three lines.
        RunLog.Header h = good();
        Path file = folder.resolve("oracle.jsonl");
        Path out = folder.resolve("out");
        rechain(file, List.of(new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(),
                h.challenges(), h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(), h.obsv(),
                h.codex(), h.brain(), h.registration(), true, h.machine(), h.started())));

        assertEquals("oracle", Replay.refusal(RunLogReader.of(file).header()).field());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, out, "test"));
        assertTrue(refused.getMessage().contains("FR-11"), refused.getMessage());
        assertFalse(Files.exists(out) && Files.list(out).findAny().isPresent(),
                "nothing was played, so nothing was written");
    }

    @Test
    @DisplayName("a refusal happens before a Run is started, which is the half the cost depends on")
    void a_refusal_costs_no_run(@TempDir Path folder) throws IOException {
        // Asserting this against `refusal()` alone proves nothing about ordering: that method
        // starts no Run whatever it returns. The claim is about `of`, so this goes through `of`
        // and then looks at the folder, which is empty only if nothing played.
        RunLog.Header h = good();
        Path file = folder.resolve("elsewhen.jsonl");
        Path out = folder.resolve("out");
        rechain(file, List.of(new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(),
                h.challenges(), h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(),
                h.obsv(), h.codex() + 1, h.brain(), h.registration(), h.oracle(), h.machine(),
                h.started())));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, out, "test"));
        assertTrue(refused.getMessage().contains("Codex version"), refused.getMessage());
        assertFalse(Files.exists(out) && Files.list(out).findAny().isPresent(),
                "a refused log costs no Run");
    }

    @Test
    @DisplayName("a cap a file states is bounded, because a file can say anything")
    void an_unbounded_cap_is_refused(@TempDir Path folder, @TempDir Path out) throws IOException {
        // The Replay takes its cap from the log so that it ends the way the original did. A log is
        // data, and `Canon` only asks that a cap be at least one -- so a header claiming two
        // billion turns makes the command run until the hero dies of something, with no deadline
        // and nobody watching. The rules are published, so writing that header is three lines.
        RunLog.Header h = good();
        Path file = folder.resolve("forever.jsonl");
        rechain(file, List.of(new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(),
                h.challenges(), h.seed(), h.seedCode(), h.salt(), Integer.MAX_VALUE, h.profile(),
                h.obsv(), h.codex(), h.brain(), h.registration(), h.oracle(), h.machine(),
                h.started())));

        Replay.Refusal refusal = Replay.refusal(RunLogReader.of(file).header());
        assertNotNull(refusal);
        assertEquals("turn cap", refusal.field());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, out, "test"));
        assertTrue(refused.getMessage().contains("turn cap"), refused.getMessage());
        assertFalse(Files.exists(out) && Files.list(out).findAny().isPresent(),
                "and nothing was played to find that out");
    }

    @Test
    @DisplayName("two logs concatenated and rechained are two Runs, and are refused as such")
    void two_logs_in_one_file_are_refused(@TempDir Path folder, @TempDir Path out) throws IOException {
        // Each half is a real log and the join is arithmetic anybody can do, so the chain verifies
        // perfectly and every record in the file is one this build understands. Only counting the
        // headers catches it -- and without that, `header()` hands back the first Run's header
        // while the waits come from both, which is a reproduction of a Run that never happened.
        Path file = played(folder);
        List<RunLog> once = RunLogReader.of(file).records();
        List<RunLog> twice = new ArrayList<>(once);
        twice.addAll(once);
        rechain(file, twice);

        assertTrue(RunLogVerifier.of(file).ok(), "the concatenation chains perfectly");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, out, "test"));

        assertTrue(refused.getMessage().contains("two Runs in one file"), refused.getMessage());
        assertFalse(Files.exists(out) && Files.list(out).findAny().isPresent(),
                "and nothing was played to find that out");
    }

    @Test
    @DisplayName("a Replay will not write into the folder it is reading, which the story froze as a Never")
    void a_replay_does_not_write_over_the_log_it_is_checking(@TempDir Path folder) {
        Path file = played(folder);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, folder, "test"));

        assertTrue(refused.getMessage().contains("not over it"), refused.getMessage());
        assertTrue(Files.isRegularFile(file), "the log it was asked to check is still there");
    }

    @Test
    @DisplayName("an unsupported record past the last wait still makes the Run unverifiable")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void an_unsupported_record_after_the_waits_is_not_ignored(@TempDir Path folder, @TempDir Path out)
            throws IOException {
        Path file = played(folder);
        List<RunLog> records = new ArrayList<>(RunLogReader.of(file).records());

        // The realistic overlay case, and the one the first draft ignored completely: the input the
        // executor could not express is the reason there is no wait record at that index. Matching
        // an unsupported mark against a wait the log also wrote only ever fired for the case that
        // cannot really happen.
        long past = records.stream().filter(RunLog.Wait.class::isInstance)
                .mapToLong(record -> ((RunLog.Wait) record).k()).max().orElseThrow() + 1;
        List<RunLog> with = new ArrayList<>(records);
        with.add(with.size() - 1, new RunLog.Unsupported(past, "dragged an item onto the quickslot"));
        rechain(file, with);

        Replay.Unverifiable stopped = assertThrows(Replay.Unverifiable.class,
                () -> Replay.of(file, out, "test"));

        assertEquals(past, stopped.at());
        // And it says how much of the Run was checked before it gave up, because a Run verified to
        // wait 400 and unverifiable from 401 is not the same thing as one unverifiable from wait 1.
        assertTrue(stopped.verified() > 10, stopped.getMessage());
        assertTrue(stopped.getMessage().contains("waits before it"), stopped.getMessage());
    }

    @Test
    @DisplayName("a killed Run's log replays as far as it goes and is reported incomplete, not failed")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void an_incomplete_log_is_not_a_failed_reproduction(@TempDir Path folder, @TempDir Path out)
            throws IOException {
        // The story's own frozen I/O matrix says so, and the first draft reported it as "did not
        // reproduce" -- which reads as "this build disagrees with the log" when what happened is
        // that the log records no ending for a reproduction to reach.
        Path file = played(folder);
        List<RunLog> records = new ArrayList<>(RunLogReader.of(file).records());
        rechain(file, records.subList(0, records.size() - 1));

        Replay.Result result = Replay.of(file, out, "test");

        assertFalse(result.ok());
        assertTrue(result.why().contains("no ending"), result.why());
        assertEquals(result.waits(), result.verified(),
                "every wait the log does record was reproduced");
    }

    private static void assertRefused(RunLog.Header header, String field) {
        Replay.Refusal refusal = Replay.refusal(header);
        assertNotNull(refusal, "a header whose " + field + " differs is not replayable");
        assertEquals(field, refusal.field());
        assertTrue(refusal.toString().contains("proves nothing"), refusal.toString());
    }

    // --------------------------------------------------------------- the refusals, over a real Run

    /** One Run, played and logged, which the cases below then rewrite. */
    private static Path played(Path folder) {
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, new RandomAgent(7L), CAP,
                new RunLoop.Logging(folder, COMMIT, new RunLog.Brain("random", COMMIT, ZERO), "",
                        "test"));
        return folder.resolve(RunLog.fileName(RunLog.runId("v4.0.0",
                org.shatterfish.api.HeroClass.WARRIOR, 0, SeedSet.code(SEED), SALT, "random")));
    }

    /**
     * Writes {@code records} as a whole log, chained from nothing.
     *
     * <p>This is the forger's tool, and it is three lines long. That is the point of the "what the
     * chain does not prove" paragraph on the methodology page, made executable.
     */
    private static void rechain(Path file, List<RunLog> records) throws IOException {
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a log whose bytes were changed does not verify, and no Run is played to find that out")
    void a_tampered_log_is_refused(@TempDir Path folder) throws IOException {
        Path file = played(folder);
        String text = Files.readString(file, StandardCharsets.UTF_8);

        // One digit of one Observation hash, and nothing else. The chain over that line no longer
        // holds, and neither does any chain after it.
        int at = text.indexOf("\"obs\":\"");
        assertTrue(at > 0, "the log records Observation hashes");
        char was = text.charAt(at + 7);
        Files.writeString(file, text.substring(0, at + 7) + (was == 'a' ? 'b' : 'a')
                + text.substring(at + 8), StandardCharsets.UTF_8);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Replay.of(file, folder.resolve("out"), "test"));
        assertTrue(refused.getMessage().contains("nothing to reproduce"), refused.getMessage());
    }

    @Test
    @DisplayName("a forged log that chains perfectly is caught by playing it, at the wait where it lies")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void a_moved_section_diverges(@TempDir Path folder, @TempDir Path out) throws IOException {
        Path file = played(folder);
        List<RunLog> records = new ArrayList<>(RunLogReader.of(file).records());

        // Find a wait well into the Run and claim the map looked different at it. Everything before
        // it is true, so the Replay agrees for a while and then stops -- which is the answer worth
        // having, because "wait 20" is where a person starts looking.
        long spoiled = -1;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i) instanceof RunLog.Wait wait && wait.k() == 20) {
                Map<String, String> sections = new LinkedHashMap<>(wait.sections());
                assertTrue(sections.containsKey("map"), "a wait hashes the map: " + sections.keySet());
                sections.put("map", ZERO);
                records.set(i, new RunLog.Wait(wait.k(), wait.turn(), wait.depth(), wait.branch(),
                        ZERO, sections, wait.action(), wait.applied(), wait.actor(), wait.decision(),
                        wait.belief(), wait.highlights(), wait.thinkMs()));
                spoiled = wait.k();
            }
        }
        assertEquals(20L, spoiled, "the Run ran past wait 20");
        rechain(file, records);

        // The forgery verifies. That is not a bug in the chain; it is what the chain is for.
        assertTrue(RunLogVerifier.of(file).ok(), "a rewritten log chains perfectly");

        Replay.Diverged diverged = assertThrows(Replay.Diverged.class,
                () -> Replay.of(file, out, "test"));
        assertEquals(20L, diverged.at(), "the first wait this build and the log disagree at");
        assertTrue(diverged.sections().contains("map"),
                "the section that differs is named: " + diverged.sections());
        assertFalse(diverged.sections().contains("hero"),
                "and a section that matches is not: " + diverged.sections());
    }

    @Test
    @DisplayName("an input the executor cannot express makes everything after it unverifiable, and says so")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void an_unsupported_record_stops_the_replay(@TempDir Path folder, @TempDir Path out) throws IOException {
        Path file = played(folder);
        List<RunLog> records = new ArrayList<>(RunLogReader.of(file).records());

        // A human played this Run through the overlay and did something the Action set has no word
        // for. Nothing in the harness writes this record -- only the overlay can -- so the log is
        // built here, and it is the only way this path is exercised before the overlay exists.
        //
        // The mark *replaces* the wait, and that is the whole point: the executor could not express
        // the input, so there is no Action to record and no wait record at that index. A version of
        // this test that inserted the mark beside a surviving wait 12 passed against a build that
        // only fired on an exact match, which is a build that ignores every real occurrence.
        List<RunLog> with = new ArrayList<>();
        for (RunLog record : records) {
            if (record instanceof RunLog.Wait wait && wait.k() == 12) {
                with.add(new RunLog.Unsupported(12, "dragged an item onto the quickslot"));
                continue;
            }
            with.add(record);
        }
        rechain(file, with);

        Replay.Unverifiable stopped = assertThrows(Replay.Unverifiable.class,
                () -> Replay.of(file, out, "test"));
        assertEquals(12L, stopped.at());
        assertTrue(stopped.getMessage().contains("could not express"), stopped.getMessage());
    }

    @Test
    @DisplayName("a log whose Actions are not the ones the Run took reaches a different chain, and reports it")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void a_changed_action_does_not_reproduce(@TempDir Path folder, @TempDir Path out) throws IOException {
        Path file = played(folder);
        List<RunLog> records = new ArrayList<>(RunLogReader.of(file).records());

        // The last wait's Action, changed to waiting a turn. The Observations up to it are all
        // genuine, so this does not diverge -- it reproduces every wait and then ends somewhere
        // else, which is the case the chain comparison exists to catch and a wait-by-wait check
        // would miss.
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i) instanceof RunLog.Wait wait) {
                records.set(i, new RunLog.Wait(wait.k(), wait.turn(), wait.depth(), wait.branch(),
                        wait.obs(), wait.sections(), new Action.Wait(), wait.applied(), wait.actor(),
                        wait.decision(), wait.belief(), wait.highlights(), wait.thinkMs()));
                break;
            }
        }
        rechain(file, records);

        Replay.Result result = Replay.of(file, out, "test");

        assertFalse(result.ok(), "the log does not describe what this build does");
        assertEquals(result.waits(), result.verified(), "every Observation in it was genuine");
        assertTrue(result.why().contains("did not reproduce"), result.why());
    }
}
