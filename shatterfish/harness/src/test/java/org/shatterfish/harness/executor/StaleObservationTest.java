package org.shatterfish.harness.executor;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the executor does with an Observation the world has moved past. Re-validation asks the
 * Observation's own set (ADR-0014), and the schema will not carry an Action that contradicts the
 * rest of that Observation ({@code Observation.java:120-133}), so the only way into the executor's
 * own guards is a set that was right when it was read and is wrong by the time it is used — a
 * Decision taken on a stale Observation, which is exactly what a Replay is when it has drifted.
 *
 * <p>The guards must hold there, because the state they protect is the game's: a talent past its
 * ceiling or a tier's point spent twice is a rule a person cannot break.
 */
class StaleObservationTest {

    private static final long SEED = 0xC0FFEEL;

    private HeadlessDriver driver;
    private Hero hero;
    private final ActionExecutor executor = new ActionExecutor();

    @BeforeEach
    void start() {
        driver = HeadlessDriver.start(SEED, HeroClass.WARRIOR);
        driver.stepToInputWait();
        hero = Dungeon.hero;
    }

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    @DisplayName("a talent offered by a stale set takes nothing once the tier's point is spent")
    void the_point_is_spent_between_the_reading_and_the_use() {
        while (hero.talentPointsAvailable(1) <= 0 && hero.lvl < 5) {
            hero.earnExp(hero.maxExp(), Hero.class);
        }
        assertTrue(hero.talentPointsAvailable(1) > 0, "one point, and two talents that would take it");

        // Read the screen while the point is there: the set offers every tier-one talent.
        Observation observation = new Observer().observe();
        List<Action.Talent> offered = new ArrayList<>();
        for (Action action : observation.actions().actions()) {
            if (action instanceof Action.Talent talent) {
                offered.add(talent);
            }
        }
        assertTrue(offered.size() >= 2, "the tier has more than one talent to spend on: " + offered);

        // Spend it on the first. The screen has moved; the Observation has not.
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, offered.get(0)));
        assertEquals(0, hero.talentPointsAvailable(1), "the tier's only point is gone");

        // The second is still in the stale set, and the guard the pane carries is what refuses it
        // (…/ui/TalentButton.java:114-119). Without it Hero.upgradeTalent takes a point that does
        // not exist (…/actors/hero/Hero.java:376-383).
        Action.Talent second = offered.get(1);
        Talent talent = talentNamed(second.talent());
        int before = hero.pointsInTalent(talent);
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class,
                executor.execute(observation, second), "a point already spent cannot be spent again");
        assertEquals(Reason.NOT_OFFERED, rejected.reason());
        assertTrue(rejected.detail().contains("tier"), rejected.detail());
        assertEquals(before, hero.pointsInTalent(talent), "and the talent is what it was");
    }

    @Test
    @DisplayName("a press while the hero is resting is refused at the gate, before anything is cancelled")
    void a_press_on_a_resting_hero() {
        Observation observation = new Observer().observe();
        assertInstanceOf(Outcome.Applied.class, executor.execute(observation, new Action.Rest(true)));
        assertTrue(hero.resting, "the hero is resting");

        // A person's second press would cancel the rest: the button asks GameScene.cancel() first
        // and stops there when it answers true, which it does while the hero is resting
        // (…/scenes/GameScene.java:1723-1736). The bot never gets that far, because a resting hero
        // is not at an Input wait and the gate is the first thing the executor asks. The rest is
        // still running afterwards, which is the point: a refusal changes nothing.
        Outcome.Rejected rejected = assertInstanceOf(Outcome.Rejected.class,
                executor.execute(observation, new Action.Rest(true)));
        assertEquals(Reason.NOT_AT_AN_INPUT_WAIT, rejected.reason());
        assertTrue(hero.resting, "and the hero is resting still");
    }

    private Talent talentNamed(String name) {
        for (java.util.Map<Talent, Integer> tier : hero.talents) {
            for (Talent talent : tier.keySet()) {
                if (talent.title().equals(name)) {
                    return talent;
                }
            }
        }
        throw new AssertionError("no talent called " + name);
    }
}
