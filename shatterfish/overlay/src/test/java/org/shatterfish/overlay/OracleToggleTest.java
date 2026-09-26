package org.shatterfish.overlay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher's {@code --oracle} decides everything the oracle touches, and only it does (story 5.1's
 * fairness review): the observer the Brain sees through, the window's title, and the log's flag.
 */
class OracleToggleTest {

    private static LaunchOptions options(boolean oracle) {
        return LaunchOptions.parse(oracle
                ? new String[] {"--seed", "12345", "--class", "warrior", "--oracle"}
                : new String[] {"--seed", "12345", "--class", "warrior"});
    }

    @Test
    @DisplayName("off: a fair Observation, a plain title, a fair log; on: all three say oracle")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_flag_decides_all_three() {
        try (HeadlessDriver driver = HeadlessDriver.start(12345L, HeroClass.WARRIOR, 0x5A17_5A17L)) {
            driver.stepToInputWait();
            Observation fair = ShatterfishLauncher.observer(false).get();
            Observation oracle = ShatterfishLauncher.observer(true).get();
            assertFalse(fair.header().oracle(), "the fair observer's Observation is fair");
            assertTrue(oracle.header().oracle(), "the oracle's is marked");
        }
        LaunchOptions off = options(false);
        LaunchOptions on = options(true);
        assertFalse(ShatterfishLauncher.title(off).contains("ORACLE"));
        assertTrue(ShatterfishLauncher.title(on).endsWith("[ORACLE]"));
        RunLoop.Logging fairLog = ShatterfishLauncher.logging(off, "test");
        RunLoop.Logging oracleLog = ShatterfishLauncher.logging(on, "test");
        assertFalse(fairLog.oracle());
        assertTrue(oracleLog.oracle());
        assertEquals("random", fairLog.brain().name());
    }
}
