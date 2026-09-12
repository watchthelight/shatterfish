package org.shatterfish.harness.rng;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The salt is not in the Observation, and neither is anything a bot could compute it from.
 *
 * <p>Of everything the harness knows, the salt is the one number that would break the only rule of
 * play outright: it is the seed of every draw the game is about to make, so a bot that had it could
 * know the next roll, the contents of the next chest and the shape of the floor below. It belongs
 * in what a Run records, so a skeptic can recompute the stream and check the Run happened as
 * claimed (ADR-0007), and nowhere a Brain can reach.
 *
 * <p>The test looks in the two places a leak could hide: the rendered form a person reads, and the
 * canonical bytes the codec writes, which is what the hash is taken over. It searches for the salt
 * in several shapes, because a number that leaked would not announce itself as one.
 */
class SaltLeakTest {

    /** A salt whose digits are unmistakable in any rendering of anything. */
    private static final long SALT = 0x5A17_5A17_5A17_5A17L;

    /**
     * A seed long enough to be worth searching for. A short one renders as a handful of digits that
     * would match any number on the screen, so it could not be told from a coincidence — which is
     * why this test chooses its own rather than taking whatever a Run was given.
     */
    private static final long SEED = 987_654_321L;

    private HeadlessDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    @DisplayName("the salt is nowhere in an Observation, in any shape it could take")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_salt_is_nowhere_in_an_observation() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, SALT);
        assertEquals(SALT, driver.salt(), "the Run knows its own salt");

        List<String> found = new ArrayList<>();
        for (int wait = 0; wait < 6; wait++) {
            // The wait's own index, not the loop's: the driver counts waits from one and reseeds
            // for the index it has just confirmed, so searching for seedFor(loop) would look for a
            // seed that was never in force and miss the one that was. The review of this story
            // found that; it is the difference between a leak test and a test that looks like one.
            long k = driver.stepToInputWait().waitIndex();
            Observation observation = new Observer().observe();
            String json = observation.json();
            String bytes = HexFormat.of().formatHex(ObservationCodec.encode(observation));

            // The salt itself, the seed it gives this wait, and the game's seed beside it: all
            // three are numbers a Brain must not have, and all three are searched for as decimal,
            // as hexadecimal, and as the bytes the codec would write for a long.
            for (long secret : new long[]{SALT, driver.seedFor(k), SEED}) {
                for (String shape : shapes(secret)) {
                    if (json.contains(shape)) {
                        found.add("wait " + k + ": " + shape + " is in the rendered Observation");
                    }
                    if (bytes.contains(shape.toLowerCase(java.util.Locale.ROOT))) {
                        found.add("wait " + k + ": " + shape + " is in the encoded Observation");
                    }
                }
            }
            com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.rest(false);
            HeadlessDriver.actionHandedOver();
        }

        assertEquals(List.of(), found, "the salt, or the seed it gives, reached something a Brain can"
                + " hold — which is the one leak that would let a bot know the next roll:\n  "
                + String.join("\n  ", found));
    }

    @Test
    @DisplayName("two salts give one first floor, which is the claim the page makes")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_salt_is_not_the_seed() {
        // The first floor is generated inside newGame, before any wait and therefore before any
        // reseed, so this holds by construction and is worth saying only because the methodology
        // page says it. Whether it holds for the floors below — which are generated with the
        // salted generator on top of the stack, even though the layout re-pushes a seed derived
        // from the game's own (core/.../levels/Level.java:221, core/.../Dungeon.java:418-429) — is
        // not established here, and the page has been narrowed to say so. The review of this story
        // asked for exactly that distinction.
        MapSection underOne = firstMap(4242L, 0x1111L);
        MapSection underAnother = firstMap(4242L, 0x2222L);
        assertEquals(underOne.tiles(), underAnother.tiles(), "one seed, one first floor");
        assertEquals(underOne.width(), underAnother.width());
    }

    private MapSection firstMap(long seed, long salt) {
        HeadlessDriver run = HeadlessDriver.start(seed, HeroClass.WARRIOR, salt);
        try {
            run.stepToInputWait();
            return new Observer().observe().map();
        } finally {
            run.close();
        }
    }

    /** The shapes a 64-bit secret could take in text a person or a codec writes. */
    private static List<String> shapes(long secret) {
        List<String> shapes = new ArrayList<>();
        shapes.add(Long.toString(secret));
        shapes.add(Long.toHexString(secret));
        shapes.add(Long.toHexString(secret).toUpperCase(java.util.Locale.ROOT));
        shapes.add(Long.toString(Math.abs(secret)));
        // Trivially short renderings would match anything; a secret that reduces to one is not a
        // secret this test can speak about, and none of the ones it is given do.
        shapes.removeIf(shape -> shape.length() < 6);
        assertTrue(!shapes.isEmpty(), "a secret worth searching for: " + secret);
        return shapes;
    }
}
