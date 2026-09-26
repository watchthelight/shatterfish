package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.noosa.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.agent.EmbeddedRun;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher's {@code --oracle} decides everything the oracle touches, and only it does (story 5.1's
 * fairness review): the observer the Brain sees through, the window's title, and the log's flag.
 *
 * <p>End to end, short of a window: the launcher's own observer and logging, from parsed options,
 * attached to an embedded Run the way {@link OverlayGame#create()} attaches them, and played frame by
 * frame over the headless game the way {@link OverlayGame#render()} plays them. What the Brain was
 * shown and what the log says are then read back.
 */
class OracleToggleTest {

    private static final long SEED = 12345L;

    private static LaunchOptions options(boolean oracle, Path out) {
        List<String> args = new ArrayList<>(List.of("--seed", Long.toString(SEED), "--class", "warrior",
                "--out", out.toString()));
        if (oracle) {
            args.add("--oracle");
        }
        return LaunchOptions.parse(args.toArray(String[]::new));
    }

    /** What the Brain was shown, and the log the Run wrote, for one Run of two waits. */
    private record Played(List<Observation> shown, RunLogReader.Log log) {
    }

    private static Played play(LaunchOptions options, boolean observerOracle) throws IOException {
        return play(options, observerOracle, new ArrayList<>());
    }

    private static Played play(LaunchOptions options, boolean observerOracle, List<Observation> shown)
            throws IOException {
        Files.createDirectories(options.out());
        Decider brain = observation -> {
            synchronized (shown) {
                shown.add(observation);
            }
            return new Action.Search();
        };
        try (HeadlessDriver driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, 0x5A17_5A17L)) {
            EmbeddedRun.Host host = new EmbeddedRun.Host() {
                @Override
                public int pendingRunnables() {
                    return driver.headlessBoot().pendingRunnables();
                }

                @Override
                public Class<? extends Scene> requestedScene() {
                    return driver.headlessBoot().game().requestedSceneClass();
                }
            };
            try (EmbeddedRun run = EmbeddedRun.attach(host, SEED, HeroClass.WARRIOR, driver.rngControl(), brain,
                    ShatterfishLauncher.observer(observerOracle), ShatterfishLauncher.logging(options, "test"),
                    5_000)) {
                for (int frame = 0; frame < 20_000 && run.waitIndex() < 3; frame++) {
                    if (run.state() != EmbeddedRun.State.THINKING) {
                        driver.step();
                    }
                    run.frame();
                }
                assertTrue(run.waitIndex() >= 3, "the Run reached its third wait");
            }
        }
        try (Stream<Path> logs = Files.list(options.out())) {
            Path log = logs.filter(path -> path.toString().endsWith(".jsonl")).findFirst().orElseThrow();
            return new Played(shown, RunLogReader.of(log));
        }
    }

    @Test
    @DisplayName("off: fair Observations, a plain title, a fair log; on: all three say oracle")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_flag_decides_all_three(@TempDir Path folder) throws IOException {
        LaunchOptions off = options(false, folder.resolve("off"));
        LaunchOptions on = options(true, folder.resolve("on"));

        Played fair = play(off, off.oracle());
        Played oracle = play(on, on.oracle());

        assertFalse(fair.shown().isEmpty());
        assertTrue(fair.shown().stream().noneMatch(o -> o.header().oracle()), "the fair Brain is shown fair screens");
        assertTrue(oracle.shown().stream().allMatch(o -> o.header().oracle()), "the oracle Brain's are marked");

        RunLog.Header fairHeader = fair.log().header();
        RunLog.Header oracleHeader = oracle.log().header();
        assertFalse(fairHeader.oracle());
        assertTrue(oracleHeader.oracle());
        assertTrue(fairHeader.embedded() && oracleHeader.embedded(), "both say the Overlay played them");
        assertEquals(2, fair.log().waits().size(), "the waits served are in the log");
        assertEquals("random", fairHeader.brain().name());

        assertFalse(ShatterfishLauncher.title(off).contains("ORACLE"));
        assertTrue(ShatterfishLauncher.title(on).endsWith("[ORACLE]"));
    }

    @Test
    @DisplayName("an oracle observer under a log that does not say so is refused at the first wait, before the Brain sees it")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_mismatch_is_refused(@TempDir Path folder) {
        List<Observation> shown = new ArrayList<>();
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> play(options(false, folder.resolve("mixed")), true, shown));
        // The Run's own guard, at the wait, not the log writer's after the Brain has decided on it.
        assertTrue(refused.getMessage().startsWith("the Run says oracle=false"), refused.getMessage());
        assertTrue(shown.isEmpty(), "the Brain was never shown the oracle's screen");
    }
}
