package org.shatterfish.harness;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.SeedSet;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The seed code {@code api} writes is the seed code the game reads (story 3.1).
 *
 * <p>A seed set commits a {@code @@@-@@@-@@@} code beside every seed, because that is what a
 * person types into the game's own seed box to run one — the whole point of publishing a set is
 * that a stranger can. {@code api} depends on nothing (AD-13, ADR-0003), so it cannot call the
 * game, and the encoding is written there a second time.
 *
 * <p>The first draft of that reasoning ended "a divergence would refuse the Run rather than play
 * the wrong one", and that was false. {@code HeadlessDriver.newGame} takes the number and makes
 * its own code ({@code shatterfish/…/driver/HeadlessDriver.java:306-310}), so nothing a Run does
 * ever compares the two. A divergence would play the right dungeons and publish codes that open
 * different ones, silently, in every committed set at once.
 *
 * <p>Nothing else can hold it: {@code api} may not see the game and {@code rig} does not see it
 * either. {@code harness} sees both, so the check lives here. It is a differential test — two
 * implementations of one mapping, run against each other — and it is the reason the mirror is
 * tolerable rather than a second implementation of a game rule nobody checks.
 */
class SeedCodeParityTest {

    @Test
    @DisplayName("the bounds api mirrors are the game's own compiled values, not a copy that has drifted")
    void the_bounds_are_the_games() {
        assertEquals(DungeonSeed.TOTAL_SEEDS, SeedSet.TOTAL_SEEDS,
                "api's seed bound and the game's; a tag that moved one has to move the other");
        assertEquals(Challenges.MAX_VALUE, SeedSet.MAX_CHALLENGE_VALUE,
                "api's challenge bound and the game's");
    }

    @Test
    @DisplayName("every seed api encodes, the game encodes the same way, and every code the game reads, api reads alike")
    void the_encoding_is_the_games() {
        for (long seed : interesting()) {
            String ours = SeedSet.code(seed);
            assertEquals(DungeonSeed.convertToCode(seed), ours, "the code for seed " + seed);
            assertEquals(seed, SeedSet.seed(ours), "api reads back what it wrote for " + seed);
            assertEquals(DungeonSeed.convertFromCode(ours), SeedSet.seed(ours),
                    "the game and api read the code " + ours + " as the same seed");
        }
    }

    /**
     * The seeds worth trying: both ends, every digit boundary, and a fixed spread. The spread is
     * arithmetic rather than random so that a failure names the same seed on every machine.
     */
    private static List<Long> interesting() {
        List<Long> seeds = new ArrayList<>(List.of(0L, 1L, 25L, 26L, 27L, 675L, 676L,
                SeedSet.TOTAL_SEEDS - 2, SeedSet.TOTAL_SEEDS - 1));
        for (long power = 1; power < SeedSet.TOTAL_SEEDS; power *= 26) {
            seeds.add(power);
            seeds.add(power * 25);
        }
        for (long step = 0; step < 512; step++) {
            seeds.add(Math.floorMod(step * 10_368_231_641L, SeedSet.TOTAL_SEEDS));
        }
        return seeds;
    }
}
