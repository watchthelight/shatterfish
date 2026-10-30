package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Overlay's own start-up decides about a replay before the game starts (story 8.5): the same
 * {@link ReplayStart} that {@code OverlayGame.createReplay} calls, from a command line parsed by
 * {@link LaunchOptions}, with no window. The hero class and the tuple come from the options the launcher parsed;
 * the interface size from the log; and a log that changed since the launcher read it, or a window too small for
 * the size the log was played on, is refused with the reason the Panel shows (drawn by {@code RefusalNotice},
 * which {@code ReplayRefusalNoticeTest} covers).
 */
class ReplayStartTest {

    private int width;
    private int height;
    private int size;

    @AfterEach
    void restore() {
        if (width != 0) {
            Game.width = width;
            Game.height = height;
            SPDSettings.interfaceSize(size);
        }
    }

    private void remember() {
        HeadlessBoot.ensure();
        if (width == 0) {
            width = Game.width;
            height = Game.height;
            size = SPDSettings.interfaceSize();
        }
    }

    @Test
    @DisplayName("a Rig log starts on the size it was played on, with the tuple the options parsed from it")
    void a_rig_log_starts() {
        remember();
        Path log = ReplayLoadTest.smallLog();
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", log.toString()});
        SPDSettings.interfaceSize(1);
        ReplayStart start = ReplayStart.prepare(options);
        assertNull(start.refusal());
        assertNotNull(start.session());
        assertEquals(HeroClass.WARRIOR, options.heroClass(), "the class the game starts is the options', which the log named");
        assertEquals(start.session().header().heroClass().name(), options.heroClass().name());
        assertEquals(0, start.session().interfaceSize());
        assertEquals(0, SPDSettings.interfaceSize(), "the game is set to the size the log was played on");
    }

    @Test
    @DisplayName("a log that changed after the launcher read it is refused, not played with a mixed tuple")
    void a_changed_log_is_refused(@TempDir Path folder) throws IOException {
        remember();
        Path copy = folder.resolve("copy.jsonl");
        Files.copy(ReplayLoadTest.smallLog(), copy);
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", copy.toString()});

        // The same file name now holds another valid log: the cap differs, the chain holds.
        List<RunLog> records = new ArrayList<>(RunLogReader.of(copy).records());
        RunLog.Header h = (RunLog.Header) records.get(0);
        records.set(0, new RunLog.Header(h.v(), h.tag(), h.commit(), h.heroClass(), h.challenges(), h.seed(),
                h.seedCode(), h.salt(), h.cap() + 1, h.profile(), h.obsv(), h.codex(), h.brain(), h.registration(),
                h.oracle(), h.machine(), h.started(), h.driver(), h.interfaceSize(), h.controller()));
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (RunLog record : records) {
            out.append(RunLogJson.line(previous, record)).append('\n');
            previous = RunLogJson.chain(previous, record);
        }
        Files.writeString(copy, out.toString(), StandardCharsets.UTF_8);

        ReplayStart start = ReplayStart.prepare(options);
        assertNull(start.session());
        assertTrue(start.refusal().contains("changed after the game was launched"), start.refusal());
    }

    @Test
    @DisplayName("a log that does not verify is refused with the reason, for the Panel to show")
    void an_unverifiable_log_is_refused(@TempDir Path folder) throws IOException {
        remember();
        Path copy = folder.resolve("flipped.jsonl");
        String text = Files.readString(ReplayLoadTest.smallLog(), StandardCharsets.UTF_8);
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", ReplayLoadTest.smallLog().toString()});
        int at = text.indexOf("\"obs\":\"");
        char was = text.charAt(at + 7);
        Files.writeString(copy, text.substring(0, at + 7) + (was == 'a' ? 'b' : 'a') + text.substring(at + 8),
                StandardCharsets.UTF_8);
        LaunchOptions flipped = LaunchOptions.parse(new String[] {"--replay", copy.toString()});
        assertNotNull(flipped.refused(), "the launcher already knows: " + options.refused());
        ReplayStart start = ReplayStart.prepare(flipped);
        assertTrue(start.refusal().contains("does not verify"), start.refusal());
    }

    @Test
    @DisplayName("an Overlay log of the full interface is refused on a window too small for it, not mismatched")
    void a_small_window_is_refused(@TempDir Path folder) {
        remember();
        Path overlay = HostBridge.overlayLog(folder.resolve("overlay"), ReplaySessionTest.SEED, HeroClass.WARRIOR,
                ReplaySessionTest.SALT, 30);
        LaunchOptions options = LaunchOptions.parse(new String[] {"--replay", overlay.toString()});
        int stated = RunLogReader.of(overlay).header().interfaceSize();
        Game.width = 320;
        Game.height = 240;
        ReplayStart start = ReplayStart.prepare(options);
        if (stated > 0) {
            assertNotNull(start.refusal(), "size " + stated + " on a 320 by 240 window plays the compact interface");
            assertTrue(start.refusal().contains("too small for interface size " + stated), start.refusal());
        } else {
            assertNull(start.refusal(), "an Overlay log that states size 0 plays anywhere");
        }
    }
}
