package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole loop, driven to the end of a Run. Everything before this story was tested a wait or
 * forty at a time; a Run is thousands of turns, and the things that only appear at that length — an
 * Action that leaves the game waiting for a choice, a floor change, a set that disagrees with the
 * game — appear here or nowhere.
 *
 * <p>Nothing here arranges the world, and nothing here pins a seed to an outcome. Both rules were
 * paid for: writing {@code hero.pos} from this thread is what made issue #68 look like a harness
 * bug for a day, and a Run is not yet reproducible from its tuple — the same seed and the same
 * chooser give different Runs in the same process, which is what stories 1.15 and 1.16 exist to fix
 * (issue #70). So what is asserted is what this story owns: every Run reaches an ending, and the
 * endings are the ones the story names.
 */
class RandomAgentRunTest {

    @Test
    @DisplayName("a Run ends, and says how it ended, how deep it got and how long it took")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void a_run_ends_and_says_how() {
        RunOutcome outcome = new RunLoop().play(0xC0FFEEL, HeroClass.WARRIOR, 0xC0FFEEL);

        assertTrue(outcome.ordinary(), "a Run ends in death, a Win or the cap: " + outcome);
        // A random Warrior dies: a thousand of them did, every one, and none reached the cap or the
        // amulet. So the cause is checked and not only its kind — a Run that started reporting a
        // death as something else would otherwise pass everything here.
        assertEquals(RunOutcome.Cause.DEATH, outcome.cause(), outcome.toString());
        assertTrue(outcome.depth() >= 1, "it reached a floor: " + outcome);
        assertTrue(outcome.turns() > 0, "and time passed: " + outcome);
        assertTrue(outcome.waits() > 0, "and it was asked for input: " + outcome);
        assertTrue(outcome.applied() > 0, "and something it asked for was done: " + outcome);
        assertTrue(outcome.turns() <= RunLoop.TURN_CAP, "within the cap: " + outcome);
    }

    @Test
    @DisplayName("a Run still alive at the cap is stopped there and counted as one")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void the_cap_ends_a_run() {
        // The cap is a number and the behaviour at it is a rule, so the rule is tested at a cap a
        // test can reach, and the story's own number is held beside it.
        assertEquals(20_000, RunLoop.TURN_CAP, "the cap this epic named");

        RunOutcome outcome = new RunLoop().play(4321L, HeroClass.WARRIOR, new RandomAgent(7L), 40);

        assertEquals(RunOutcome.Cause.TURN_CAP, outcome.cause(), outcome.toString());
        assertTrue(outcome.turns() >= 40, "the cap was reached, not approached: " + outcome);
        // The cap is checked at a wait, and one Action can pass hundreds of turns — resting does —
        // so a Run overshoots by up to whatever its last Action cost. What it must not do is carry
        // on taking Actions afterwards, which is what the story's own cap being far away shows.
        assertTrue(outcome.turns() < RunLoop.TURN_CAP, "the Run stopped at its cap: " + outcome);
    }

    @Test
    @DisplayName("a Run leaves the first floor, and the loop carries it across")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_run_can_leave_the_first_floor() {
        // Nothing steers a Run down: a random Warrior finds the stairs or it does not, and about
        // one in thirty does. So Runs are played until one descends, which is the only honest way
        // to test a descent here — a test that put the hero on the stairs would be testing its own
        // arrangement, and a seed cannot be pinned while a Run is not reproducible (issue #70).
        RunOutcome descended = null;
        for (int seed = 0; seed < 150 && descended == null; seed++) {
            RunOutcome outcome = new RunLoop().play(seed, HeroClass.WARRIOR, seed * 31L + 7L);
            assertTrue(outcome.ordinary(), "seed " + seed + " ended badly: " + outcome);
            if (outcome.depth() >= 2) {
                descended = outcome;
            }
        }

        assertTrue(descended != null, "no Run in a hundred and fifty found the stairs, which is not"
                + " the rate this story measured (about one in thirty) and would mean the loop has"
                + " stopped serving the scene change");
        assertTrue(descended.depth() >= 2, "the Run left floor one: " + descended);
    }

    @Test
    @DisplayName("a thousand Runs finish unattended, and the causes are a tally")
    @Timeout(value = 90, unit = TimeUnit.MINUTES)
    void a_thousand_runs() {
        Map<RunOutcome.Cause, Integer> tally = new EnumMap<>(RunOutcome.Cause.class);
        int deepest = 0;
        long turns = 0;
        long waits = 0;
        for (int run = 0; run < 1000; run++) {
            RunOutcome outcome = new RunLoop().play(run, HeroClass.WARRIOR, run * 31L + 7L);
            tally.merge(outcome.cause(), 1, Integer::sum);
            deepest = Math.max(deepest, outcome.depth());
            turns += outcome.turns();
            waits += outcome.waits();
            assertTrue(outcome.ordinary(), "run " + run + " ended badly: " + outcome);
        }
        System.out.println("A thousand random Warriors: " + tally + ", deepest floor " + deepest
                + ", " + turns + " turns and " + waits + " waits in all");

        assertEquals(1000, tally.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(tally.getOrDefault(RunOutcome.Cause.DEATH, 0) > 0, "a random Warrior dies: " + tally);
        assertTrue(deepest >= 2, "and some of them find the stairs: deepest was " + deepest);
    }
}
