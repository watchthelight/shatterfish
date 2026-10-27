package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.brain.BrainDecider;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The E4 half of FR-9 (story 4.14): the real Brain's Decisions are identical until the Observations diverge, on real
 * Runs of the {@code standard} set with the hidden identities of the unidentified items permuted.
 *
 * <p>Each seed is played twice under one salt. World A is the Run as dealt. In world B, at the first wait, two
 * unidentified classes of each family (potion, scroll, ring) trade the labels they draw under, and every item of
 * those classes already lying on the floor trades classes with its twin, so what is on screen is unchanged and what
 * the game knows is not: the potion that was one kind is now the other, under the same colour. The test walks the two
 * Runs wait by wait: while the Observation is the same bytes in both, the Action, the Decision and the Belief the
 * Brain holds must be the same, since a difference could only have come from something other than what it was
 * shown. At the first wait where the Observations part (a floor item of a swapped class comes into view under the
 * other label, or a swapped item is used and does what the other would), the comparison stops, which is the claim:
 * identical until the Observations diverge, and no earlier.
 *
 * <p>The permutation is made by this test, in the game, and never handed to the Brain: the Brain sees only the
 * Observations. Seeds are Warriors of the {@code standard} set (never {@code goo} or {@code holdout}).
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class PermutedIdentitiesDifferentialTest {

    private static final long SALT = 0x5A17_5A17L;
    private static final int TURN_CAP = 1_200;
    /** How many Warriors of the standard set, in file order. */
    private static final int SEEDS = 3;
    /** A shorter common prefix means the permutation was seen at once and the test would hold nothing. */
    private static final int MIN_PREFIX = 25;

    /** What the Brain saw and did at one wait. */
    private record Step(String observation, String action, String decision, String belief) {
    }

    @Test
    @DisplayName("on standard-set Runs with hidden identities permuted, the Brain's Decisions are identical until the Observations diverge")
    void identical_until_the_observations_diverge() throws IOException {
        boolean someRunDiverged = false;
        for (long seed : warriorSeeds()) {
            List<Step> a = play(seed, false);
            List<Step> b = play(seed, true);
            int limit = Math.min(a.size(), b.size());
            int prefix = 0;
            while (prefix < limit && a.get(prefix).observation.equals(b.get(prefix).observation)) {
                Step x = a.get(prefix);
                Step y = b.get(prefix);
                assertEquals(x.action, y.action, "seed " + seed + ": the same screen, the same Action, at wait " + (prefix + 1));
                assertEquals(x.decision, y.decision, "seed " + seed + ": the same screen, the same Decision, at wait " + (prefix + 1));
                assertEquals(x.belief, y.belief, "seed " + seed + ": the same screens, the same Belief, at wait " + (prefix + 1));
                prefix++;
            }
            assertTrue(prefix >= MIN_PREFIX, "seed " + seed + ": the worlds must look alike for a while to hold anything, but parted at wait "
                    + (prefix + 1));
            System.out.println("PERMUTED seed " + seed + ": " + prefix + " identical waits of " + a.size() + " / " + b.size());
            if (prefix < limit) {
                someRunDiverged = true;
            } else {
                assertEquals(a.size(), b.size(), "seed " + seed + ": worlds that never part are as long as each other");
            }
        }
        assertTrue(HiddenPermutation.exchanged() > 0, "some floor item must have changed class, or the walk held an unchanged floor");
        // The control: a permutation that never showed on any screen would make the walk above hold for any Brain.
        assertTrue(someRunDiverged, "the permuted identities must come to show on at least one seed, or they were never in play");
    }

    private static List<Long> warriorSeeds() throws IOException {
        String text = Files.readString(RealBrain.root().resolve("seeds").resolve("standard.json"));
        Matcher entry = Pattern.compile("\"heroClass\":\"WARRIOR\",\"seed\":(\\d+)").matcher(text);
        List<Long> seeds = new ArrayList<>();
        while (seeds.size() < SEEDS && entry.find()) {
            seeds.add(Long.parseLong(entry.group(1)));
        }
        assertEquals(SEEDS, seeds.size(), "enough Warriors in the standard set");
        return seeds;
    }

    private static List<Step> play(long seed, boolean permuted) {
        BrainDecider brain = RealBrain.decider();
        List<Step> steps = new ArrayList<>();
        new RunLoop().play(seed, HeroClass.WARRIOR, SALT, (Observation observation) -> {
            if (permuted && steps.isEmpty()) {
                HiddenPermutation.permute();
            }
            Action action = brain.decide(observation);
            steps.add(new Step(observation.hash(), String.valueOf(action), String.valueOf(brain.lastDecision()),
                    String.valueOf(brain.belief().hash())));
            return action;
        }, TURN_CAP);
        return steps;
    }
}
