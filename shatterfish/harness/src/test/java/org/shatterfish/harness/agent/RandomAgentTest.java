package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.observer.Observer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chooser itself, away from a Run. What matters about it is that it has no preference: a
 * chooser that liked the first Action, or any one kind, would put its taste into every number
 * measured through it, and the depth a random Run reaches would stop being a property of the game.
 * A Run cannot show this — a Run ends whatever the chooser does, which is why the mutation that
 * makes the agent always take the first Action was caught by nothing until this test existed.
 */
class RandomAgentTest {

    private HeadlessDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    @DisplayName("the agent reaches across the whole set rather than favouring a corner of it")
    void the_agent_has_no_preference() {
        driver = HeadlessDriver.start(0xC0FFEEL, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Observation observation = new Observer().observe();
        List<Action> offered = observation.actions().actions();
        assertTrue(offered.size() >= 8, "a wait with a set worth drawing from: " + offered.size());

        RandomAgent agent = new RandomAgent(1L);
        Set<Action> drawn = new HashSet<>();
        for (int draw = 0; draw < 40 * offered.size(); draw++) {
            drawn.add(agent.choose(observation));
        }

        assertEquals(new HashSet<>(offered), drawn,
                "every Action offered is one the agent will take: it drew " + drawn.size()
                        + " of " + offered.size());
    }

    @Test
    @DisplayName("two agents of one seed make the same choices, and of different seeds do not")
    void the_seed_is_the_stream() {
        driver = HeadlessDriver.start(0xC0FFEEL, HeroClass.WARRIOR);
        driver.stepToInputWait();
        Observation observation = new Observer().observe();

        RandomAgent one = new RandomAgent(7L);
        RandomAgent same = new RandomAgent(7L);
        RandomAgent other = new RandomAgent(8L);
        boolean anyDifference = false;
        for (int draw = 0; draw < 50; draw++) {
            Action a = one.choose(observation);
            assertEquals(a, same.choose(observation), "one seed, one stream, at draw " + draw);
            anyDifference |= !a.equals(other.choose(observation));
        }
        assertTrue(anyDifference, "a different seed is a different stream");
    }
}
