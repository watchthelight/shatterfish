package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Action kind the executor claims to apply is applied at least once, in a real Run, by the
 * executor itself. The review of story 1.13 asked for this: the talent branch called an unguarded
 * method and let the bot push a talent past its ceiling, and it survived the whole story because
 * nothing had ever driven that branch. A branch no test drives is a branch nobody has checked.
 *
 * <p>The walk is greedy for coverage rather than random: at each wait it takes an offered Action of
 * a kind it has not applied yet, and otherwise a random one. The seed is fixed, so the set of kinds
 * a Warrior meets on the way down is fixed too; {@link #REACHED} is what this seed actually reaches
 * and is asserted, while {@link #OUT_OF_REACH} names what it does not and why, so a kind that stops
 * being applied fails here rather than going quiet.
 */
class ActionKindCoverageTest {

    /** The salt these Runs declare. There is no default: see ADR-0007 and {@code Salt}. */
    private static final long RUN_SALT = 0x5A17_5A17L;

    private static final long SEED = 0xC0FFEEL;

    /** How far the walk goes. A Warrior on this seed meets everything in REACHED well inside it. */
    private static final int WAITS = 1200;

    /** The kinds this seed's Run reaches, each applied by the executor and not by a test's hand. */
    private static final Set<String> REACHED = new TreeSet<>(List.of(
            "Step", "Wait", "Rest", "Search", "Attack", "PickUp", "UseItem", "UseItemAt"));

    /**
     * The kinds a Warrior's walk down does not reach on this seed, with the reason. None is a gap
     * in the executor: each needs a floor feature or an item the walk did not meet, and each is
     * covered by a mutation in the story's battery instead.
     */
    private static final Set<String> OUT_OF_REACH = new TreeSet<>(List.of(
            "MoveTo", "Ability", "AbilityAt", "Interact", "OpenChest", "Buy", "Unlock",
            "UseItemOn", "AnswerPrompt", "DismissPrompt",
            // A walk that prefers novelty wanders and does not seek the stairs, so the two
            // transitions and the talent pane are driven by tests that arrange the state instead:
            // ActionExecutorTest's descent and its talent ceiling.
            "Descend", "Ascend", "Talent"));

    private HeadlessDriver driver;
    private final ActionExecutor executor = new ActionExecutor();

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    @DisplayName("every Action kind this Run can reach is applied through the executor at least once")
    void every_reachable_kind_is_applied() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR, RUN_SALT);
        driver.stepToInputWait();
        Random choices = new Random(SEED);
        Set<String> applied = new LinkedHashSet<>();

        for (int wait = 0; wait < WAITS && Dungeon.hero.isAlive(); wait++) {
            Observation observation = new Observer().observe();
            List<Action> offered = observation.actions().actions();
            if (offered.isEmpty()) {
                break;
            }
            List<Action> fresh = new ArrayList<>();
            for (Action action : offered) {
                if (!applied.contains(action.kind())) {
                    fresh.add(action);
                }
            }
            Action chosen = fresh.isEmpty()
                    ? offered.get(choices.nextInt(offered.size()))
                    : fresh.get(choices.nextInt(fresh.size()));

            Outcome outcome = executor.execute(observation, chosen);
            assertInstanceOf(Outcome.Applied.class, outcome, chosen + " at wait " + wait + ": " + outcome);
            applied.add(chosen.kind());
            try {
                driver.stepToInputWait();
            } catch (HeadlessDriver.Stalled stalled) {
                break;
            }
            if (applied.containsAll(REACHED)) {
                break;
            }
        }

        Set<String> missing = new TreeSet<>(REACHED);
        missing.removeAll(applied);
        assertTrue(missing.isEmpty(), "a kind this Run used to apply is no longer applied: " + missing
                + "; applied " + new TreeSet<>(applied));

        Set<String> surprise = new TreeSet<>(applied);
        surprise.removeAll(REACHED);
        assertTrue(OUT_OF_REACH.containsAll(surprise),
                "a kind was applied that neither list names, so the lists have drifted: " + surprise);
    }
}
