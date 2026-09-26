package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RunLoop#record}'s oracle-consistency check (story 5.4's fairness review, "should fix" #4):
 * previously an early {@code if (log == null) return;} skipped it along with everything else, so a
 * mismatch between the header's own {@code oracle} and the Observation's reached only a logged Run.
 * Building and checking the {@link org.shatterfish.api.RunLog.Wait} even with no log to write to
 * (story 5.4, so {@code EmbeddedRun.history()} gets a record whether or not a file is open) made the
 * check unconditional; this holds that it still throws with {@code log == null}, the one combination
 * nothing else exercised.
 */
class RunLoopRecordTest {

    private static final long RUN_SALT = 0x5A17_5A17L;

    private HeadlessDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    private Observation observed() {
        driver = HeadlessDriver.start(0xC0FFEEL, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        return new Observer().observe();
    }

    /** A real Observation's header with {@code oracle} flipped, everything else unchanged. */
    private static Observation withOracle(Observation o, boolean oracle) {
        HeaderSection h = o.header();
        HeaderSection flipped = new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), h.depth(), h.branch(), h.sealed(), oracle, h.prompt());
        return new Observation(flipped, o.map(), o.actors(), o.hero(), o.inventory(), o.journal(), o.log(),
                o.actions(), o.prompt());
    }

    @Test
    @DisplayName("record(null, ...) still throws on an oracle/header mismatch: the check does not depend on a log being open")
    void oracle_mismatch_throws_with_no_log() {
        Observation oracleObservation = withOracle(observed(), true);
        Decider notADeliberator = observation -> new Action.Wait();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> RunLoop.record(null, 1, oracleObservation, new Action.Wait(), true, 0, false, notADeliberator));
        assertTrue(thrown.getMessage().contains("oracle"), thrown.getMessage());
    }

    @Test
    @DisplayName("record(null, ...) still returns the Wait it builds when there is no mismatch (no log to write to)")
    void matching_oracle_returns_the_wait_with_no_log() {
        Observation observation = withOracle(observed(), false);
        Decider notADeliberator = observation1 -> new Action.Wait();

        var wait = RunLoop.record(null, 1, observation, new Action.Wait(), true, 0, false, notADeliberator);
        assertTrue(wait.applied());
    }
}
