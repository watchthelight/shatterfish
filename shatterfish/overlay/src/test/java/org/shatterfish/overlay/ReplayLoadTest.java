package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.harness.agent.HostBridge;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What loading a Run log for a replay accepts and refuses (story 8.5, FR-46): the chain is verified before anything
 * is played, and a log whose chain, upstream tag, Codex version or oracle flag does not match this build is refused
 * with the reason, which the Panel shows. The cases that spoil a header chain the log again, so the refusal is the
 * header's and not the chain's: the forger's tool is three lines long, which is why a replay also compares hashes.
 */
class ReplayLoadTest {

    private static Path good;

    /** A short Rig log of the {@code standard} seed, written once for the tests that want a small real log. */
    static synchronized Path smallLog() {
        if (good == null) {
            try {
                HeadlessBoot.ensure();
                good = HostBridge.rigLog(Files.createTempDirectory("replay-small"), ReplaySessionTest.SEED,
                        HeroClass.WARRIOR, ReplaySessionTest.SALT, 60);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return good;
    }

    /** The log with its header rewritten by {@code spoil} and the chain written again. */
    private static Path spoiled(Path folder, java.util.function.UnaryOperator<RunLog.Header> spoil) throws IOException {
        List<RunLog> records = new ArrayList<>(RunLogReader.of(smallLog()).records());
        records.set(0, spoil.apply((RunLog.Header) records.get(0)));
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Path file = folder.resolve("spoiled.jsonl");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static RunLog.Header with(RunLog.Header h, String tag, int codex, boolean oracle) {
        return new RunLog.Header(h.v(), tag, h.commit(), h.heroClass(), h.challenges(), h.seed(), h.seedCode(),
                h.salt(), h.cap(), h.profile(), h.obsv(), codex, h.brain(), h.registration(), oracle, h.machine(),
                h.started(), h.driver(), h.interfaceSize(), h.controller());
    }

    @Test
    @DisplayName("a log of this build loads: its header, its waits, and the follower that will apply them")
    void a_good_log_loads() {
        ReplaySession session = ReplaySession.load(smallLog());
        assertEquals(ReplaySessionTest.SEED, session.header().seed());
        assertTrue(session.waits() > 5, "the waits: " + session.waits());
        assertNotNull(session.follower());
        assertEquals(session.waits(), session.follower().total());
        assertEquals(0, session.interfaceSize(), "a Rig log states no screen, and is the headless driver's size 0");
    }

    @Test
    @DisplayName("a log with one byte changed does not verify and is refused with that reason")
    void a_flipped_byte_is_refused(@TempDir Path folder) throws IOException {
        String text = Files.readString(smallLog(), StandardCharsets.UTF_8);
        int at = text.indexOf("\"obs\":\"");
        char was = text.charAt(at + 7);
        Path flipped = folder.resolve("flipped.jsonl");
        Files.writeString(flipped, text.substring(0, at + 7) + (was == 'a' ? 'b' : 'a') + text.substring(at + 8),
                StandardCharsets.UTF_8);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> ReplaySession.load(flipped));
        assertTrue(refused.getMessage().contains("does not verify"), refused.getMessage());
    }

    @Test
    @DisplayName("a log from another upstream tag or another Codex version is refused, though its chain holds")
    void another_tag_or_codex_is_refused(@TempDir Path folder) throws IOException {
        Path tag = spoiled(folder, h -> with(h, "v0.0.0", h.codex(), h.oracle()));
        IllegalArgumentException wrongTag = assertThrows(IllegalArgumentException.class, () -> ReplaySession.load(tag));
        assertTrue(wrongTag.getMessage().contains("upstream tag"), wrongTag.getMessage());

        Path codex = spoiled(folder, h -> with(h, h.tag(), h.codex() + 1, h.oracle()));
        IllegalArgumentException wrongCodex = assertThrows(IllegalArgumentException.class, () -> ReplaySession.load(codex));
        assertTrue(wrongCodex.getMessage().toLowerCase().contains("codex"), wrongCodex.getMessage());
    }

    @Test
    @DisplayName("a log that claims the oracle is refused, so a replay can never be an oracle run")
    void an_oracle_log_is_refused(@TempDir Path folder) throws IOException {
        Path oracle = spoiled(folder, h -> with(h, h.tag(), h.codex(), true));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> ReplaySession.load(oracle));
        assertTrue(refused.getMessage().toLowerCase().contains("oracle"), refused.getMessage());
    }

    @Test
    @DisplayName("a log the Overlay wrote is accepted, and plays on the interface size it states")
    void an_overlay_log_is_accepted(@TempDir Path folder) throws IOException {
        Path overlay = spoiled(folder, h -> new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(),
                h.challenges(), h.seed(), h.seedCode(), h.salt(), h.cap(), h.profile(), h.obsv(), h.codex(),
                h.brain(), h.registration(), h.oracle(), h.machine(), h.started(), "embedded", 1, 0));
        ReplaySession session = ReplaySession.load(overlay);
        assertEquals(1, session.interfaceSize());
    }
}
