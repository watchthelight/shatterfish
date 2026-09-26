package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Naming the guess window's icons reads no Run's state and moves nothing (story 4.11): the table
 * behind the names is built from classes, and building it draws nothing from the game's generator.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class GuessOptionsTest {

    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 31_415_926L;

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private long nextDraw(boolean build) {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        GuessOptions.forget();
        if (build) {
            GuessOptions.build();
        }
        long draw = Random.Long();
        driver.close();
        driver = null;
        return draw;
    }

    @Test
    @DisplayName("building the table of icon names draws nothing from the game's generator")
    void the_table_draws_nothing() {
        assertEquals(nextDraw(false), nextDraw(true),
                "the game's next draw is the same whether or not the table was built on the way");
    }
}
